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
        Log.d(TAG, "初始化TTS: $locale")
        initializeTts()
    }

    private fun initializeTts() {
        try {
            val modelConfig = TtsModelManager.getTtsModelConfig(context, locale)
            if (modelConfig != null) {
                Log.d(TAG, "加载TTS模型: ${if (modelConfig.useAssets) "Assets" else "外部存储"}")
                
                // 处理dataDir和dictDir，需要复制到外部存储（参考demo代码）
                var processedDataDir = modelConfig.dataDir
                var processedDictDir = modelConfig.dictDir
                var processedRuleFsts = modelConfig.ruleFsts
                
                if (modelConfig.useAssets) {
                    // 从Assets加载，TtsModelManager已经返回了完整的assets路径，但某些文件仍需要复制到外部存储
                    if (modelConfig.dataDir.isNotEmpty()) {
                        val externalFilesDir = context.getExternalFilesDir(null)!!.absolutePath
                        val externalDataPath = "$externalFilesDir/${modelConfig.dataDir}"
                        
                        if (!File(externalDataPath).exists()) {
                            copyAssetsToExternal(modelConfig.dataDir)
                        }
                        processedDataDir = externalDataPath
                    }
                    
                    if (modelConfig.dictDir.isNotEmpty()) {
                        val externalFilesDir = context.getExternalFilesDir(null)!!.absolutePath
                        val externalDictPath = "$externalFilesDir/${modelConfig.dictDir}"
                        
                        if (!File(externalDictPath).exists()) {
                            copyAssetsToExternal(modelConfig.dictDir)
                        }
                        processedDictDir = externalDictPath
                        
                        // 根据demo代码，当有dictDir时自动设置ruleFsts
                        if (modelConfig.ruleFsts.isEmpty()) {
                            val ttsBasePath = TtsModelManager.getExternalTtsModelsPath(context)
                            val modelDirName = modelConfig.modelDir.substringAfterLast("/")
                            processedRuleFsts = "$ttsBasePath/$modelDirName/phone.fst,$ttsBasePath/$modelDirName/date.fst,$ttsBasePath/$modelDirName/number.fst"
                        }
                    }
                }
                
                // 确保使用正确的modelDir路径
                val processedModelDir = if (modelConfig.useAssets) {
                    modelConfig.modelDir
                } else {
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
                    acousticModelName = "",
                    vocoder = "",
                    voices = ""
                )
                
                // 根据模型来源选择初始化方式
                try {
                    // 验证关键文件是否存在
                    if (!modelConfig.useAssets) {
                        val modelFile = File(processedModelDir, modelConfig.modelName)
                        if (!modelFile.exists()) {
                            Log.e(TAG, "模型文件不存在: ${modelFile.absolutePath}")
                            throw Exception("模型文件不存在: ${modelFile.absolutePath}")
                        }
                    }
                    
                    tts = if (modelConfig.useAssets) {
                        OfflineTts(assetManager = context.assets, config = config)
                    } else {
                        OfflineTts(assetManager = null, config = config)
                    }
                    
                    initializedCorrectly = true
                    Log.d(TAG, "TTS初始化成功 (采样率: ${tts?.sampleRate()})")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "TTS创建失败: ${e.message}", e)
                    
                    // 尝试不使用ruleFsts重新初始化
                    if (processedRuleFsts.isNotEmpty()) {
                        Log.w(TAG, "尝试不使用ruleFsts重新初始化TTS")
                        try {
                            val fallbackConfig = getOfflineTtsConfig(
                                modelDir = processedModelDir,
                                modelName = modelConfig.modelName,
                                lexicon = modelConfig.lexicon,
                                dataDir = processedDataDir,
                                dictDir = processedDictDir,
                                ruleFsts = "",
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
                            Log.w(TAG, "TTS初始化成功（已禁用ruleFsts）")
                            return
                            
                        } catch (fallbackException: Exception) {
                            Log.e(TAG, "回退初始化失败: ${fallbackException.message}", fallbackException)
                        }
                    }
                    
                    tts = null
                    initializedCorrectly = false
                    handleInitializationError(R.string.android_tts_error)
                    return
                }
                
            } else {
                Log.e(TAG, "未找到TTS模型: $locale")
                handleInitializationError(R.string.android_tts_unsupported_language)
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS初始化失败: ${e.message}", e)
            handleInitializationError(R.string.android_tts_error)
        }
    }

    override fun speak(speechOutput: String) {
        if (!initializedCorrectly || tts == null) {
            Log.w(TAG, "TTS未初始化，使用Toast显示: $speechOutput")
            Toast.makeText(context, speechOutput, Toast.LENGTH_LONG).show()
            return
        }

        stopSpeaking()
        isSpeakingFlag.set(true)
        
        currentJob = scope.launch {
            try {
                val audio = tts?.generate(
                    text = speechOutput,
                    sid = 0,
                    speed = 1.0f
                )
                
                if (audio != null && audio.samples.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        playAudio(audio)
                    }
                } else {
                    Log.e(TAG, "音频生成失败")
                    withContext(Dispatchers.Main) {
                        onSpeakingFinished()
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException && 
                    e.cause !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "TTS合成失败: ${e.message}", e)
                }
                withContext(Dispatchers.Main) {
                    onSpeakingFinished()
                }
            }
        }
    }

    private fun playAudio(audio: GeneratedAudio) {
        scope.launch {
            try {
                AudioResourceManager.notifyTtsStart()
            } catch (e: Exception) {
                Log.e(TAG, "通知TTS开始失败", e)
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
            audioTrack?.write(shortArray, 0, shortArray.size)
            
            // 等待播放完成
            audioTrack?.setNotificationMarkerPosition(shortArray.size)
            audioTrack?.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack?) {
                    onSpeakingFinished()
                }
                
                override fun onPeriodicNotification(track: AudioTrack?) {}
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
        
        scope.launch {
            try {
                AudioResourceManager.notifyTtsEnd()
            } catch (e: Exception) {
                Log.e(TAG, "通知TTS结束失败", e)
            }
        }
        
        for (runnable in runnablesWhenFinished) {
            runnable.run()
        }
        runnablesWhenFinished.clear()
    }

    override fun stopSpeaking() {
        val wasSpeaking = isSpeakingFlag.getAndSet(false)
        
        currentJob?.cancel()
        currentJob = null
        
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        
        if (wasSpeaking) {
            scope.launch {
                try {
                    AudioResourceManager.notifyTtsEnd()
                } catch (e: Exception) {
                    Log.e(TAG, "通知TTS停止失败", e)
                }
            }
        }
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
     * 复制单个Asset文件到外部存储
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
        } catch (e: Exception) {
            Log.e(TAG, "文件复制失败: $assetPath", e)
        }
    }

    /**
     * 将输入的Locale映射为SherpaOnnx兼容的Locale
     */
    private fun mapToSherpaCompatibleLocale(inputLocale: Locale): Locale {
        return when (inputLocale.language) {
            "cn" -> Locale.CHINESE
            "ko" -> Locale.KOREAN
            else -> inputLocale
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
