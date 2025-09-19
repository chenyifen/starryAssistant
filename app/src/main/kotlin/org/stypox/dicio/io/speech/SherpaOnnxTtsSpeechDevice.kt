package org.stypox.dicio.io.speech

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
import org.stypox.dicio.R
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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
        try {
            val modelConfig = TtsModelManager.getTtsModelConfig(context, locale)
            if (modelConfig != null) {
                Log.d(TAG, "  📦 加载TTS模型: ${modelConfig.modelDir}")
                Log.d(TAG, "  🔧 使用模式: ${if (modelConfig.useAssets) "Assets" else "外部存储"}")
                
                // 处理dataDir和dictDir，需要复制到外部存储（参考demo代码）
                var processedDataDir = modelConfig.dataDir
                var processedDictDir = modelConfig.dictDir
                var processedRuleFsts = modelConfig.ruleFsts
                
                if (modelConfig.useAssets) {
                    // 从Assets加载，需要复制数据目录到外部存储
                    if (modelConfig.dataDir.isNotEmpty()) {
                        val copiedDataDir = copyDataDirFromAssets(modelConfig.dataDir)
                        processedDataDir = "$copiedDataDir/${modelConfig.dataDir}"
                        Log.d(TAG, "  📁 数据目录已复制: $processedDataDir")
                    }
                    
                    if (modelConfig.dictDir.isNotEmpty()) {
                        val copiedDictDir = copyDataDirFromAssets("${modelConfig.modelDir}/${modelConfig.dictDir}")
                        processedDictDir = "$copiedDictDir/${modelConfig.modelDir}/${modelConfig.dictDir}"
                        
                        // 根据demo代码，当有dictDir时自动设置ruleFsts
                        if (modelConfig.ruleFsts.isEmpty()) {
                            processedRuleFsts = "${modelConfig.modelDir}/phone.fst,${modelConfig.modelDir}/date.fst,${modelConfig.modelDir}/number.fst"
                        }
                        Log.d(TAG, "  📚 字典目录已复制: $processedDictDir")
                        Log.d(TAG, "  📝 规则FSTs: $processedRuleFsts")
                    }
                }
                
                val config = getOfflineTtsConfig(
                    modelDir = modelConfig.modelDir,
                    modelName = modelConfig.modelName,
                    lexicon = modelConfig.lexicon,
                    dataDir = processedDataDir,
                    dictDir = processedDictDir,
                    ruleFsts = processedRuleFsts,
                    ruleFars = modelConfig.ruleFars
                )
                
                // 根据模型来源选择初始化方式（参考SherpaOnnxWakeDevice的实现）
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
                Log.e(TAG, "TTS合成失败: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onSpeakingFinished()
                }
            }
        }
    }

    private fun playAudio(audio: GeneratedAudio) {
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
        
        // 执行完成回调
        for (runnable in runnablesWhenFinished) {
            runnable.run()
        }
        runnablesWhenFinished.clear()
        
        Log.d(TAG, "  🏁 TTS播放完成")
    }

    override fun stopSpeaking() {
        currentJob?.cancel()
        currentJob = null
        
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        
        isSpeakingFlag.set(false)
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
     * 从Assets复制数据目录到外部存储（参考demo代码）
     */
    private fun copyDataDirFromAssets(assetPath: String): String {
        Log.d(TAG, "复制数据目录: $assetPath")
        copyAssetsToExternal(assetPath)
        
        val externalFilesDir = context.getExternalFilesDir(null)!!.absolutePath
        Log.d(TAG, "外部文件目录: $externalFilesDir")
        return externalFilesDir
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

    /**
     * 根据语言获取对应的TTS模型配置
     */
    private fun getTtsModelConfig(locale: Locale): TtsModelConfig? {
        return when (locale.language) {
            "zh" -> TtsModelConfig(
                modelDir = "vits-zh-hf-fanchen-C",
                modelName = "vits-zh-hf-fanchen-C.onnx",
                lexicon = "lexicon.txt",
                dictDir = "dict"
            )
            "ko" -> TtsModelConfig(
                modelDir = "vits-mimic3-ko_KO-kss_low",
                modelName = "ko_KO-kss_low.onnx", 
                lexicon = "tokens.txt",
                dataDir = "espeak-ng-data"
            )
            "en" -> TtsModelConfig(
                modelDir = "vits-piper-en_US-amy-low",
                modelName = "en_US-amy-low.onnx",
                lexicon = "tokens.txt",
                dataDir = "espeak-ng-data"
            )
            else -> null
        }
    }

    companion object {
        private val TAG = SherpaOnnxTtsSpeechDevice::class.simpleName
    }
}
