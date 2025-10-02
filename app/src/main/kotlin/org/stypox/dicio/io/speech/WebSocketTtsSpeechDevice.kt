package org.stypox.dicio.io.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import org.dicio.skill.context.SpeechOutputDevice
import org.json.JSONObject
import org.stypox.dicio.io.net.MessageType
import org.stypox.dicio.io.net.TtsState
import org.stypox.dicio.io.net.WebSocketProtocol
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WebSocket 在线 TTS 语音输出设备
 * 参考 py-xiaozhi 实现，通过 WebSocket 接收服务器的 TTS 音频流
 */
class WebSocketTtsSpeechDevice(
    private val context: Context,
    private val protocol: WebSocketProtocol
) : SpeechOutputDevice {

    companion object {
        private const val TAG = "WebSocketTtsSpeechDevice"
        private const val SAMPLE_RATE = 24000 // 服务器 TTS 采样率
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // 播放状态
    private val isSpeakingFlag = AtomicBoolean(false)
    override val isSpeaking: Boolean
        get() = isSpeakingFlag.get()
    
    // 音频播放
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val audioBuffer = mutableListOf<ByteArray>()
    private val audioBufferLock = Any()
    
    // 播放完成回调列表
    private val finishCallbacks = mutableListOf<Runnable>()
    
    // 当前正在播放的句子
    private var currentSentence: String? = null

    init {
        Log.d(TAG, "🚀 初始化 WebSocketTtsSpeechDevice")
        setupAudioMessageCallback()
        setupTextMessageCallback()
    }

    /**
     * 设置音频消息回调
     */
    private fun setupAudioMessageCallback() {
        protocol.onAudioMessage { audioData ->
            scope.launch(Dispatchers.IO) {
                handleIncomingAudio(audioData)
            }
        }
    }

    /**
     * 设置文本消息回调 - 处理 TTS 状态消息
     */
    private fun setupTextMessageCallback() {
        protocol.onTextMessage { message ->
            try {
                val json = JSONObject(message)
                val type = json.optString("type")
                
                if (type == MessageType.TTS) {
                    val state = json.optString("state")
                    when (state) {
                        TtsState.START -> {
                            Log.d(TAG, "🔊 TTS 开始")
                            handleTtsStart()
                        }
                        TtsState.STOP -> {
                            Log.d(TAG, "⏹️ TTS 停止")
                            handleTtsStop()
                        }
                        TtsState.SENTENCE_START -> {
                            val text = json.optString("text")
                            Log.d(TAG, "📝 TTS 句子开始: '$text'")
                            currentSentence = text
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理文本消息失败: ${e.message}", e)
            }
        }
    }

    override fun speak(speechOutput: String) {
        if (speechOutput.isBlank()) {
            return
        }

        Log.d(TAG, "🗣️ 请求 TTS 合成: '$speechOutput'")
        
        // 停止当前播放
        stopSpeaking()
        
        // 发送 TTS 请求到服务器
        scope.launch {
            val message = JSONObject().apply {
                put("type", "tts_request")
                put("text", speechOutput)
            }
            protocol.sendText(message.toString())
        }
    }

    override fun stopSpeaking() {
        if (!isSpeaking) {
            return
        }

        Log.d(TAG, "⏹️ 停止 TTS 播放")
        
        isSpeakingFlag.set(false)
        playbackJob?.cancel()
        playbackJob = null
        
        audioTrack?.apply {
            if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                stop()
            }
            flush()
            release()
        }
        audioTrack = null
        
        synchronized(audioBufferLock) {
            audioBuffer.clear()
        }
        
        currentSentence = null
        
        // 发送停止 TTS 命令到服务器
        scope.launch {
            val message = JSONObject().apply {
                put("type", "command")
                put("action", "stop_tts")
            }
            protocol.sendText(message.toString())
        }
    }

    override fun runWhenFinishedSpeaking(runnable: Runnable) {
        synchronized(finishCallbacks) {
            if (isSpeaking) {
                finishCallbacks.add(runnable)
            } else {
                // 如果已经播放完成，立即执行
                runnable.run()
            }
        }
    }

    /**
     * 处理 TTS 开始事件
     */
    private fun handleTtsStart() {
        isSpeakingFlag.set(true)
        initializeAudioTrack()
        startPlayback()
    }

    /**
     * 处理 TTS 停止事件
     */
    private fun handleTtsStop() {
        // 等待剩余音频播放完成
        scope.launch(Dispatchers.IO) {
            // 等待音频缓冲区清空
            while (synchronized(audioBufferLock) { audioBuffer.isNotEmpty() }) {
                delay(50)
            }
            
            // 停止播放
            withContext(Dispatchers.Main) {
                onSpeakingFinished()
            }
        }
    }

    /**
     * 处理接收到的音频数据
     */
    private fun handleIncomingAudio(audioData: ByteArray) {
        if (!isSpeaking) {
            return
        }

        synchronized(audioBufferLock) {
            audioBuffer.add(audioData)
        }
    }

    /**
     * 初始化 AudioTrack
     */
    private fun initializeAudioTrack() {
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
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
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
        Log.d(TAG, "✅ AudioTrack 已初始化并开始播放")
    }

    /**
     * 开始播放音频
     */
    private fun startPlayback() {
        playbackJob = scope.launch(Dispatchers.IO) {
            try {
                while (isSpeaking && isActive) {
                    val audioData = synchronized(audioBufferLock) {
                        if (audioBuffer.isNotEmpty()) {
                            audioBuffer.removeAt(0)
                        } else {
                            null
                        }
                    }

                    if (audioData != null) {
                        // 转换为 short 数组
                        val shortArray = ShortArray(audioData.size / 2)
                        for (i in shortArray.indices) {
                            val byte1 = audioData[i * 2].toInt() and 0xFF
                            val byte2 = audioData[i * 2 + 1].toInt() and 0xFF
                            shortArray[i] = ((byte2 shl 8) or byte1).toShort()
                        }

                        // 写入音频数据
                        audioTrack?.write(shortArray, 0, shortArray.size)
                    } else {
                        // 缓冲区为空，等待新数据
                        delay(10)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "音频播放失败: ${e.message}", e)
            }
        }
    }

    /**
     * 播放完成回调
     */
    private fun onSpeakingFinished() {
        isSpeakingFlag.set(false)
        
        audioTrack?.apply {
            if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                stop()
            }
            release()
        }
        audioTrack = null
        
        Log.d(TAG, "✅ TTS 播放完成")
        
        // 执行所有完成回调
        synchronized(finishCallbacks) {
            finishCallbacks.forEach { it.run() }
            finishCallbacks.clear()
        }
    }

    override fun cleanup() {
        Log.d(TAG, "🧹 清理 WebSocketTtsSpeechDevice 资源")
        stopSpeaking()
        scope.cancel()
    }
}

