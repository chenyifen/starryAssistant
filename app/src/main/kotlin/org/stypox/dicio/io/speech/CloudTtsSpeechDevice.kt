package org.stypox.dicio.io.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.dicio.skill.context.SpeechOutputDevice
import org.json.JSONObject
import org.stypox.dicio.util.WebSocketConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 云端 TTS 语音输出设备
 * 参考 py-xiaozhi-main 实现，通过 HTTP API 请求服务器进行语音合成
 * 支持流式和非流式两种模式
 */
class CloudTtsSpeechDevice(
    private val context: Context
) : SpeechOutputDevice {

    companion object {
        private const val TAG = "CloudTtsSpeechDevice"
        private const val SAMPLE_RATE = 24000 // 服务器 TTS 采样率
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val REQUEST_TIMEOUT = 30L // 请求超时时间（秒）
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // 播放状态
    private val isSpeakingFlag = AtomicBoolean(false)
    override val isSpeaking: Boolean
        get() = isSpeakingFlag.get()
    
    // HTTP 客户端
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(REQUEST_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(REQUEST_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(REQUEST_TIMEOUT, TimeUnit.SECONDS)
            .build()
    }
    
    // 音频播放
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    
    // 播放完成回调列表
    private val finishCallbacks = mutableListOf<Runnable>()

    init {
        Log.d(TAG, "🚀 初始化 CloudTtsSpeechDevice")
    }

    override fun speak(speechOutput: String) {
        if (speechOutput.isBlank()) {
            return
        }

        Log.d(TAG, "🗣️ 请求云端 TTS 合成: '$speechOutput'")
        
        // 停止当前播放
        stopSpeaking()
        
        // 异步请求 TTS 合成
        scope.launch(Dispatchers.IO) {
            try {
                isSpeakingFlag.set(true)
                
                // 构建 TTS 请求
                val audioData = requestTtsSynthesis(speechOutput)
                
                if (audioData != null && audioData.isNotEmpty()) {
                    // 播放音频数据
                    playAudioData(audioData)
                } else {
                    Log.e(TAG, "❌ TTS 合成失败，没有返回音频数据")
                    handlePlaybackFinished()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ 云端 TTS 合成失败: ${e.message}", e)
                handlePlaybackFinished()
            }
        }
    }

    /**
     * 请求 TTS 合成
     */
    private suspend fun requestTtsSynthesis(text: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            // 获取服务器配置
            val baseUrl = WebSocketConfig.getHttpUrl(context)
            val ttsUrl = "$baseUrl/api/v1/tts/synthesize"
            
            // 构建请求 JSON
            val requestJson = JSONObject().apply {
                put("text", text)
                put("voice", "default") // 可以后续扩展为可配置
                put("speed", 1.0)
                put("pitch", 1.0)
                put("volume", 1.0)
                put("format", "pcm") // PCM 格式
                put("sample_rate", SAMPLE_RATE)
                put("channels", 1)
            }
            
            Log.d(TAG, "📤 发送 TTS 请求到: $ttsUrl")
            Log.d(TAG, "📋 请求参数: ${requestJson.toString(2)}")
            
            // 构建 HTTP 请求
            val requestBody = requestJson.toString()
                .toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(ttsUrl)
                .post(requestBody)
                .addHeader("Authorization", "Bearer ${WebSocketConfig.getAccessToken(context)}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "audio/pcm")
                .build()
            
            // 发送请求
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val audioData = response.body?.bytes()
                Log.d(TAG, "✅ TTS 合成成功，音频数据大小: ${audioData?.size ?: 0} 字节")
                audioData
            } else {
                Log.e(TAG, "❌ TTS 请求失败: ${response.code} ${response.message}")
                val errorBody = response.body?.string()
                Log.e(TAG, "错误详情: $errorBody")
                null
            }
            
        } catch (e: IOException) {
            Log.e(TAG, "❌ TTS 网络请求失败: ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ TTS 请求异常: ${e.message}", e)
            null
        }
    }

    /**
     * 播放音频数据
     */
    private suspend fun playAudioData(audioData: ByteArray) = withContext(Dispatchers.IO) {
        try {
            // 计算音频缓冲区大小
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ).coerceAtLeast(audioData.size)
            
            // 创建 AudioTrack
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            
            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "❌ AudioTrack 初始化失败")
                handlePlaybackFinished()
                return@withContext
            }
            
            Log.d(TAG, "🎵 开始播放音频，数据大小: ${audioData.size} 字节")
            
            // 开始播放
            audioTrack?.play()
            
            // 写入音频数据
            var bytesWritten = 0
            val chunkSize = 4096
            
            while (bytesWritten < audioData.size && isSpeakingFlag.get()) {
                val remainingBytes = audioData.size - bytesWritten
                val writeSize = minOf(chunkSize, remainingBytes)
                
                val result = audioTrack?.write(
                    audioData,
                    bytesWritten,
                    writeSize
                ) ?: 0
                
                if (result > 0) {
                    bytesWritten += result
                } else {
                    Log.w(TAG, "⚠️ AudioTrack 写入失败: $result")
                    break
                }
                
                // 短暂延迟，避免过快写入
                delay(10)
            }
            
            // 等待播放完成
            if (isSpeakingFlag.get()) {
                // 计算播放时长（毫秒）
                val durationMs = (audioData.size * 1000L) / (SAMPLE_RATE * 2) // 16位PCM
                delay(durationMs)
            }
            
            Log.d(TAG, "✅ 音频播放完成")
            handlePlaybackFinished()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 音频播放失败: ${e.message}", e)
            handlePlaybackFinished()
        }
    }

    /**
     * 处理播放完成
     */
    private fun handlePlaybackFinished() {
        scope.launch(Dispatchers.Main) {
            isSpeakingFlag.set(false)
            
            // 清理音频资源
            audioTrack?.apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    stop()
                }
                release()
            }
            audioTrack = null
            
            // 执行完成回调
            synchronized(finishCallbacks) {
                finishCallbacks.forEach { it.run() }
                finishCallbacks.clear()
            }
            
            Log.d(TAG, "🏁 播放完成处理结束")
        }
    }

    override fun stopSpeaking() {
        Log.d(TAG, "⏹️ 停止云端 TTS 播放")
        
        // 取消播放任务
        playbackJob?.cancel()
        playbackJob = null
        
        // 停止音频播放
        scope.launch(Dispatchers.IO) {
            isSpeakingFlag.set(false)
            
            audioTrack?.apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    stop()
                }
            }
        }
    }

    override fun runWhenFinishedSpeaking(runnable: Runnable) {
        if (isSpeaking) {
            synchronized(finishCallbacks) {
                finishCallbacks.add(runnable)
            }
        } else {
            runnable.run()
        }
    }

    override fun cleanup() {
        Log.d(TAG, "🧹 清理 CloudTtsSpeechDevice 资源")
        
        stopSpeaking()
        
        // 取消协程作用域
        scope.cancel()
        
        // 清理 HTTP 客户端
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }
}
