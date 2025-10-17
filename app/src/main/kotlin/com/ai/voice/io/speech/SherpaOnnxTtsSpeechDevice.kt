package com.ai.voice.io.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import org.dicio.skill.context.SpeechOutputDevice
import com.ai.voice.R
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.ai.voice.io.AudioResourceManager

/**
 * SherpaOnnx TTS语音输出设备
 * 支持中文、韩语、英文的离线TTS合成
 */
class SherpaOnnxTtsSpeechDevice(
    private val context: Context,
    private val inputLocale: Locale
) : SpeechOutputDevice {

    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private var initializedCorrectly = false
    private val isSpeakingFlag = AtomicBoolean(false)
    private val runnablesWhenFinished: MutableList<Runnable> = ArrayList()
    private var currentJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // 处理语言映射，确保TTS能正确识别
    private val locale: Locale = mapToSherpaCompatibleLocale(inputLocale)
    
    init {
        Log.d(TAG, "🔊 SherpaOnnxTtsSpeechDevice - 初始化:")
        Log.d(TAG, "  📥 输入语言: $inputLocale (language=${inputLocale.language}, country=${inputLocale.country})")
        Log.d(TAG, "  🔄 映射后语言: $locale (language=${locale.language}, country=${locale.country})")
        
        initializeTts()
    }

    private fun initializeTts() {
        // 添加详细的路径检查
        Log.d(TAG, "  🔍 开始检查TTS模型可用性...")
        
        // 检查外部存储路径
        val externalTtsPath = TtsModelManager.getExternalTtsModelsPath(context)
        Log.d(TAG, "  📁 外部存储TTS路径: $externalTtsPath")
        
        val externalDir = java.io.File(externalTtsPath)
        Log.d(TAG, "  📂 外部存储目录状态:")
        Log.d(TAG, "    - 存在: ${externalDir.exists()}")
        Log.d(TAG, "    - 可读: ${externalDir.canRead()}")
        Log.d(TAG, "    - 是目录: ${externalDir.isDirectory}")
        
        if (externalDir.exists()) {
            val subDirs = externalDir.listFiles()
            Log.d(TAG, "    - 子目录数量: ${subDirs?.size ?: 0}")
            subDirs?.forEach { subDir ->
                Log.d(TAG, "      * ${subDir.name} (${if (subDir.isDirectory) "目录" else "文件"})")
            }
        }
        
        try {
            val modelConfig = TtsModelManager.getTtsModelConfig(context, locale)
            if (modelConfig != null) {
                Log.d(TAG, "  📦 加载TTS模型: ${modelConfig.modelDir}")
                Log.d(TAG, "  🔧 使用模式: ${if (modelConfig.useAssets) "Assets" else "外部存储"}")
                
                // 验证模型文件是否真实存在
                val modelFile = java.io.File(modelConfig.modelDir, modelConfig.modelName)
                Log.d(TAG, "  📄 模型文件路径: ${modelFile.absolutePath}")
                Log.d(TAG, "  📄 模型文件状态:")
                Log.d(TAG, "    - 存在: ${modelFile.exists()}")
                Log.d(TAG, "    - 可读: ${modelFile.canRead()}")
                Log.d(TAG, "    - 大小: ${if (modelFile.exists()) "${modelFile.length() / 1024 / 1024}MB" else "N/A"}")
                
                // 处理dataDir和dictDir，需要复制到外部存储（参考demo代码）
                var processedDataDir = modelConfig.dataDir
                var processedDictDir = modelConfig.dictDir
                var processedRuleFsts = modelConfig.ruleFsts
                
                if (modelConfig.useAssets) {
                    // 从Assets加载，TtsModelManager已经返回了完整的assets路径，但某些文件仍需要复制到外部存储
                    if (modelConfig.dataDir.isNotEmpty()) {
                        // dataDir已经是完整的assets路径，如: "models/tts/vits-zh-hf-fanchen-C/espeak-ng-data"
                        val externalFilesDir = context.getExternalFilesDir(null)!!.absolutePath
                        val externalDataPath = "$externalFilesDir/${modelConfig.dataDir}"
                        
                        if (File(externalDataPath).exists()) {
                            Log.d(TAG, "  📁 数据目录已存在，直接使用: $externalDataPath")
                            processedDataDir = externalDataPath
                        } else {
                            Log.d(TAG, "  📁 数据目录不存在，开始复制: ${modelConfig.dataDir}")
                            // 直接使用TtsModelManager返回的完整assets路径
                            copyAssetsToExternal(modelConfig.dataDir)
                            processedDataDir = "$externalFilesDir/${modelConfig.dataDir}"
                            Log.d(TAG, "  📁 数据目录已复制: $processedDataDir")
                        }
                    }
                    
                    if (modelConfig.dictDir.isNotEmpty()) {
                        // dictDir已经是完整的assets路径，如: "models/tts/vits-zh-hf-fanchen-C/dict"
                        val externalFilesDir = context.getExternalFilesDir(null)!!.absolutePath
                        val externalDictPath = "$externalFilesDir/${modelConfig.dictDir}"
                        
                        if (File(externalDictPath).exists()) {
                            Log.d(TAG, "  📚 字典目录已存在，直接使用: $externalDictPath")
                            processedDictDir = externalDictPath
                            
                            // 根据demo代码，当有dictDir时自动设置ruleFsts
                            if (modelConfig.ruleFsts.isEmpty()) {
                                // 使用 ModelPathManager 获取正确的 TTS 路径
                                val ttsBasePath = TtsModelManager.getExternalTtsModelsPath(context)
                                val modelDirName = modelConfig.modelDir.substringAfterLast("/")
                                processedRuleFsts = "$ttsBasePath/$modelDirName/phone.fst,$ttsBasePath/$modelDirName/date.fst,$ttsBasePath/$modelDirName/number.fst"
                            }
                        } else {
                            Log.d(TAG, "  📚 字典目录不存在，开始复制: ${modelConfig.dictDir}")
                            // 直接使用TtsModelManager返回的完整assets路径
                            copyAssetsToExternal(modelConfig.dictDir)
                            processedDictDir = "$externalFilesDir/${modelConfig.dictDir}"
                            
                            // 根据demo代码，当有dictDir时自动设置ruleFsts
                            if (modelConfig.ruleFsts.isEmpty()) {
                                // 使用 ModelPathManager 获取正确的 TTS 路径
                                val ttsBasePath = TtsModelManager.getExternalTtsModelsPath(context)
                                val modelDirName = modelConfig.modelDir.substringAfterLast("/")
                                processedRuleFsts = "$ttsBasePath/$modelDirName/phone.fst,$ttsBasePath/$modelDirName/date.fst,$ttsBasePath/$modelDirName/number.fst"
                            }
                            Log.d(TAG, "  📚 字典目录已复制: $processedDictDir")
                        }
                        Log.d(TAG, "  📝 规则FSTs: $processedRuleFsts")
                    }
                }
                
                // TODO: 修复AAR版本的API差异
                // 确保使用正确的modelDir路径
                val processedModelDir = if (modelConfig.useAssets) {
                    modelConfig.modelDir // assets路径保持不变
                } else {
                    // 外部存储：使用 ModelPathManager 获取正确的 TTS 路径
                    val ttsBasePath = TtsModelManager.getExternalTtsModelsPath(context)
                    val modelDirName = modelConfig.modelDir.substringAfterLast("/")
                    "$ttsBasePath/$modelDirName"
                }
                val config = getOfflineTtsConfig(
                    modelDir = processedModelDir,
                    modelName = modelConfig.modelName,
                    lexicon = modelConfig.lexicon,
                    dataDir = processedDataDir,
                    dictDir = processedDictDir,
                    ruleFsts = processedRuleFsts,
                    ruleFars = modelConfig.ruleFars,
                    acousticModelName = "", // AAR版本新增参数
                    vocoder = "", // AAR版本新增参数
                    voices = "" // AAR版本新增参数
                )
                
                // 根据模型来源选择初始化方式（参考SherpaOnnxWakeDevice的实现）
                try {
                    // 验证关键文件是否存在
                    if (!modelConfig.useAssets) {
                        val modelFile = File(processedModelDir, modelConfig.modelName)
                        if (!modelFile.exists()) {
                            Log.e(TAG, "  ❌ 模型文件不存在: ${modelFile.absolutePath}")
                            throw Exception("模型文件不存在: ${modelFile.absolutePath}")
                        }
                        Log.d(TAG, "  ✅ 模型文件验证通过: ${modelFile.absolutePath}")
                    }
                    
                    Log.d(TAG, "  🔧 TTS配置详情:")
                    Log.d(TAG, "    - modelDir: $processedModelDir")
                    Log.d(TAG, "    - modelName: ${modelConfig.modelName}")
                    Log.d(TAG, "    - dataDir: $processedDataDir")
                    Log.d(TAG, "    - dictDir: $processedDictDir")
                    Log.d(TAG, "    - ruleFsts: $processedRuleFsts")
                    Log.d(TAG, "    - useAssets: ${modelConfig.useAssets}")
                    
                    tts = if (modelConfig.useAssets) {
                        Log.d(TAG, "  📱 从Assets加载TTS模型")
                        OfflineTts(assetManager = context.assets, config = config)
                    } else {
                        Log.d(TAG, "  💾 从外部存储加载TTS模型")
                        OfflineTts(assetManager = null, config = config)
                    }
                    
                    initializedCorrectly = true
                    
                    Log.d(TAG, "  ✅ SherpaOnnx TTS初始化成功")
                    Log.d(TAG, "  🎵 采样率: ${tts?.sampleRate()}")
                    Log.d(TAG, "  🎤 说话人数量: ${tts?.numSpeakers()}")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "  ❌ SherpaOnnx OfflineTts创建失败: ${e.message}", e)
                    Log.e(TAG, "  📋 配置信息: modelDir=$processedModelDir, useAssets=${modelConfig.useAssets}")
                    Log.e(TAG, "  📋 处理后路径: dataDir=$processedDataDir, dictDir=$processedDictDir, ruleFsts=$processedRuleFsts")
                    
                    // 尝试不使用ruleFsts重新初始化（可能是ruleFsts导致的崩溃）
                    if (processedRuleFsts.isNotEmpty()) {
                        Log.w(TAG, "  🔄 尝试不使用ruleFsts重新初始化TTS...")
                        try {
                            val fallbackConfig = getOfflineTtsConfig(
                                modelDir = processedModelDir,
                                modelName = modelConfig.modelName,
                                lexicon = modelConfig.lexicon,
                                dataDir = processedDataDir,
                                dictDir = processedDictDir,
                                ruleFsts = "", // 清空ruleFsts
                                ruleFars = modelConfig.ruleFars,
                                acousticModelName = "",
                                vocoder = "",
                                voices = ""
                            )
                            
                            tts = if (modelConfig.useAssets) {
                                OfflineTts(assetManager = context.assets, config = fallbackConfig)
                            } else {
                                OfflineTts(assetManager = null, config = fallbackConfig)
                            }
                            
                            initializedCorrectly = true
                            Log.w(TAG, "  ⚠️ TTS初始化成功（已禁用ruleFsts）")
                            Log.d(TAG, "  🎵 采样率: ${tts?.sampleRate()}")
                            Log.d(TAG, "  🎤 说话人数量: ${tts?.numSpeakers()}")
                            return
                            
                        } catch (fallbackException: Exception) {
                            Log.e(TAG, "  ❌ 回退初始化也失败: ${fallbackException.message}", fallbackException)
                        }
                    }
                    
                    tts = null
                    initializedCorrectly = false
                    handleInitializationError(R.string.android_tts_error)
                    return
                }
                
            } else {
                Log.e(TAG, "  ❌ 未找到TTS模型: $locale")
                Log.e(TAG, "  💡 请确保已下载对应语言的TTS模型")
                handleInitializationError(R.string.android_tts_unsupported_language)
            }
        } catch (e: Exception) {
            Log.e(TAG, "  ❌ SherpaOnnx TTS初始化失败: ${e.message}", e)
            handleInitializationError(R.string.android_tts_error)
        }
    }

    override fun speak(speechOutput: String) {
        if (!initializedCorrectly || tts == null) {
            Log.w(TAG, "TTS未初始化，使用Toast显示: $speechOutput")
            Toast.makeText(context, speechOutput, Toast.LENGTH_LONG).show()
            return
        }

        // 取消当前播放
        stopSpeaking()
        
        Log.d(TAG, "🗣️ 开始TTS合成: '$speechOutput'")
        isSpeakingFlag.set(true)
        
        currentJob = scope.launch {
            try {
                // 生成音频
                val audio = tts?.generate(
                    text = speechOutput,
                    sid = 0, // 使用默认说话人
                    speed = 1.0f
                )
                
                if (audio != null && audio.samples.isNotEmpty()) {
                    Log.d(TAG, "  ✅ 音频生成成功，样本数: ${audio.samples.size}")
                    
                    // 播放音频
                    withContext(Dispatchers.Main) {
                        playAudio(audio)
                    }
                } else {
                    Log.e(TAG, "  ❌ 音频生成失败")
                    withContext(Dispatchers.Main) {
                        onSpeakingFinished()
                    }
                }
            } catch (e: Exception) {
                // 区分取消异常和真正的错误
                if (e is kotlinx.coroutines.CancellationException || 
                    e.cause is kotlinx.coroutines.CancellationException ||
                    e.message?.contains("was cancelled", ignoreCase = true) == true) {
                    // 协程被取消是正常的（新的TTS请求到来时会取消旧的）
                    Log.d(TAG, "⚠️ TTS合成被取消（正常）: 新的TTS请求到达")
                } else {
                    // 真正的错误才记录ERROR
                    Log.e(TAG, "❌ TTS合成失败: ${e.message}", e)
                }
                withContext(Dispatchers.Main) {
                    onSpeakingFinished()
                }
            }
        }
    }

    private fun playAudio(audio: GeneratedAudio) {
        // 通知AudioResourceManager: TTS播放开始
        scope.launch {
            try {
                AudioResourceManager.notifyTtsStart()
                Log.d(TAG, "  📢 已通知AudioResourceManager: TTS播放开始")
            } catch (e: Exception) {
                Log.e(TAG, "  ❌ 通知TTS开始失败", e)
            }
        }
        
        try {
            val sampleRate = audio.sampleRate
            val samples = audio.samples
            
            // 转换为16位PCM
            val pcmData = FloatArray(samples.size)
            for (i in samples.indices) {
                pcmData[i] = samples[i] * 32767.0f
            }
            val shortArray = ShortArray(pcmData.size)
            for (i in pcmData.indices) {
                shortArray[i] = pcmData[i].toInt().coerceIn(-32768, 32767).toShort()
            }
            
            // 创建AudioTrack
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize.coerceAtLeast(shortArray.size * 2))
                .build()
            
            audioTrack?.play()
            
            // 写入音频数据
            val bytesWritten = audioTrack?.write(shortArray, 0, shortArray.size)
            Log.d(TAG, "  🎵 音频播放中，写入字节数: $bytesWritten")
            
            // 等待播放完成
            audioTrack?.setNotificationMarkerPosition(shortArray.size)
            audioTrack?.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack?) {
                    Log.d(TAG, "  ✅ 音频播放完成")
                    onSpeakingFinished()
                }
                
                override fun onPeriodicNotification(track: AudioTrack?) {
                    // 不需要处理
                }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "音频播放失败: ${e.message}", e)
            onSpeakingFinished()
        }
    }

    private fun onSpeakingFinished() {
        isSpeakingFlag.set(false)
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        
        // 通知AudioResourceManager: TTS播放结束
        scope.launch {
            try {
                AudioResourceManager.notifyTtsEnd()
                Log.d(TAG, "  📢 已通知AudioResourceManager: TTS播放结束")
            } catch (e: Exception) {
                Log.e(TAG, "  ❌ 通知TTS结束失败", e)
            }
        }
        
        // 执行完成回调
        for (runnable in runnablesWhenFinished) {
            runnable.run()
        }
        runnablesWhenFinished.clear()
        
        Log.d(TAG, "  🏁 TTS播放完成")
    }

    override fun stopSpeaking() {
        val wasSpeaking = isSpeakingFlag.getAndSet(false)
        
        currentJob?.cancel()
        currentJob = null
        
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        
        // 如果之前正在播放，通知AudioResourceManager
        if (wasSpeaking) {
            scope.launch {
                try {
                    AudioResourceManager.notifyTtsEnd()
                    Log.d(TAG, "  📢 已通知AudioResourceManager: TTS停止")
                } catch (e: Exception) {
                    Log.e(TAG, "  ❌ 通知TTS停止失败", e)
                }
            }
        }
        
        Log.d(TAG, "  ⏹️ TTS播放停止")
    }

    override val isSpeaking: Boolean
        get() = isSpeakingFlag.get()

    override fun runWhenFinishedSpeaking(runnable: Runnable) {
        if (isSpeaking) {
            runnablesWhenFinished.add(runnable)
        } else {
            runnable.run()
        }
    }

    override fun cleanup() {
        stopSpeaking()
        tts?.release()
        tts = null
        Log.d(TAG, "  🧹 SherpaOnnx TTS清理完成")
    }

    private fun handleInitializationError(@StringRes errorString: Int) {
        Toast.makeText(context, errorString, Toast.LENGTH_SHORT).show()
        cleanup()
    }

    
    /**
     * 递归复制Assets目录到外部存储（参考demo代码）
     */
    private fun copyAssetsToExternal(assetPath: String) {
        try {
            val assetFiles = context.assets.list(assetPath)
            
            if (assetFiles.isNullOrEmpty()) {
                // 这是一个文件，直接复制
                copyAssetFile(assetPath)
            } else {
                // 这是一个目录，创建目录并递归复制内容
                val externalPath = "${context.getExternalFilesDir(null)}/$assetPath"
                val dir = File(externalPath)
                dir.mkdirs()
                
                for (fileName in assetFiles) {
                    val subPath = if (assetPath.isEmpty()) fileName else "$assetPath/$fileName"
                    copyAssetsToExternal(subPath)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "复制Assets失败: $assetPath", e)
        }
    }
    
    /**
     * 复制单个Asset文件到外部存储（参考demo代码）
     */
    private fun copyAssetFile(assetPath: String) {
        try {
            context.assets.open(assetPath).use { inputStream ->
                val outputPath = "${context.getExternalFilesDir(null)}/$assetPath"
                File(outputPath).parentFile?.mkdirs()
                
                FileOutputStream(outputPath).use { outputStream ->
                    val buffer = ByteArray(1024)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
            }
            Log.d(TAG, "文件复制成功: $assetPath")
        } catch (e: Exception) {
            Log.e(TAG, "文件复制失败: $assetPath", e)
        }
    }

    /**
     * 将输入的Locale映射为SherpaOnnx兼容的Locale
     */
    private fun mapToSherpaCompatibleLocale(inputLocale: Locale): Locale {
        return when (inputLocale.language) {
            "cn" -> {
                Log.d(TAG, "  🔄 映射cn -> zh (中文)")
                Locale.CHINESE
            }
            "ko" -> {
                Log.d(TAG, "  🔄 映射ko -> ko (韩语)")
                Locale.KOREAN
            }
            else -> {
                Log.d(TAG, "  ✅ 保持原始Locale: $inputLocale")
                inputLocale
            }
        }
    }

    /**
     * TTS模型配置数据类
     */
    data class TtsModelConfig(
        val modelDir: String,
        val modelName: String,
        val lexicon: String = "",
        val dataDir: String = "",
        val dictDir: String = "",
        val ruleFsts: String = "",
        val ruleFars: String = ""
    )

    // 删除重复的TTS模型配置，统一使用TtsModelManager

    companion object {
        private val TAG = SherpaOnnxTtsSpeechDevice::class.simpleName
    }
}
