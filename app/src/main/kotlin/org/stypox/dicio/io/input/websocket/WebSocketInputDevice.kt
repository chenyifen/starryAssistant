package org.stypox.dicio.io.input.websocket

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.io.input.SttInputDevice
import org.stypox.dicio.io.input.SttState
import org.stypox.dicio.io.net.MessageType
import org.stypox.dicio.io.net.WebSocketProtocol
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WebSocket 在线 ASR 输入设备
 * 参考 py-xiaozhi 实现，通过 WebSocket 连接服务器进行实时语音识别
 */
class WebSocketInputDevice(
    @ApplicationContext private val appContext: Context,
    private val serverUrl: String,
    private val accessToken: String,
    private val deviceId: String,
    private val clientId: String,
    private val sharedProtocol: WebSocketProtocol? = null
) : SttInputDevice {

    companion object {
        private const val TAG = "WebSocketInputDevice"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_SIZE = 640 // 20ms @ 16kHz
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 状态
    private val _uiState = MutableStateFlow<SttState>(SttState.NotAvailable)
    override val uiState: StateFlow<SttState> = _uiState.asStateFlow()
    
    // WebSocket 协议
    private var protocol: WebSocketProtocol? = null
    
    // 音频录制
    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private var recordingJob: Job? = null
    
    // 事件监听器
    private var eventListener: ((InputEvent) -> Unit)? = null
    
    init {
        Log.d(TAG, "🚀 初始化 WebSocketInputDevice")
        initializeProtocol()
    }

    /**
     * 初始化 WebSocket 协议
     */
    private fun initializeProtocol() {
        // 使用共享的协议实例或创建新的
        protocol = sharedProtocol ?: WebSocketProtocol(
            serverUrl = serverUrl,
            accessToken = accessToken,
            deviceId = deviceId,
            clientId = clientId
        )
        
        // 设置文本消息回调 - 处理 STT 识别结果
        protocol?.onTextMessage { message ->
            handleServerMessage(message)
        }
        
        // 设置网络错误回调
        protocol?.onNetworkError { error ->
            Log.e(TAG, "❌ 网络错误: $error")
            scope.launch {
                _uiState.emit(SttState.Error(Exception(error)))
            }
        }
        
        // 设置连接状态变化回调
        protocol?.onConnectionStateChanged { connected, message ->
            Log.d(TAG, "连接状态变化: connected=$connected, message=$message")
            scope.launch {
                if (connected) {
                    _uiState.emit(SttState.Available)
                } else {
                    _uiState.emit(SttState.NotAvailable)
                }
            }
        }
    }

    override fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?) {
        Log.d(TAG, "📥 tryLoad 被调用")
        scope.launch {
            _uiState.emit(SttState.Loading(null))
            
            // 连接到服务器
            val connected = protocol?.connect() ?: false
            
            if (connected) {
                _uiState.emit(SttState.Available)
                thenStartListeningEventListener?.let {
                    startListening(it)
                }
            } else {
                _uiState.emit(SttState.Error(Exception("无法连接到服务器")))
            }
        }
    }

    override fun startListening(eventListener: (InputEvent) -> Unit) {
        Log.d(TAG, "🎙️ 开始监听")
        this.eventListener = eventListener
        
        scope.launch {
            _uiState.emit(SttState.Listening)
            startAudioRecording()
        }
    }

    override fun stopListening() {
        Log.d(TAG, "⏹️ 停止监听")
        scope.launch {
            stopAudioRecording()
            _uiState.emit(SttState.Available)
        }
    }

    override fun onClick(eventListener: (InputEvent) -> Unit) {
        if (uiState.value == SttState.Listening) {
            stopListening()
        } else if (uiState.value == SttState.Available) {
            startListening(eventListener)
        }
    }

    override fun reinitializeToReleaseResources() {
        Log.d(TAG, "🔄 重新初始化以释放资源")
        destroy()
        initializeProtocol()
    }

    /**
     * 开始音频录制
     */
    private suspend fun startAudioRecording() = withContext(Dispatchers.IO) {
        if (isRecording.get()) {
            Log.w(TAG, "⚠️ 已经在录音中")
            return@withContext
        }

        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ).coerceAtLeast(FRAME_SIZE * 2)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "❌ AudioRecord 初始化失败")
                _uiState.emit(SttState.Error(Exception("AudioRecord 初始化失败")))
                return@withContext
            }

            audioRecord?.startRecording()
            isRecording.set(true)
            
            Log.d(TAG, "✅ 音频录制已开始")
            
            // 通知服务器开始监听
            sendStartListening()

            // 启动音频数据发送任务
            recordingJob = scope.launch {
                val buffer = ShortArray(FRAME_SIZE)
                
                while (isRecording.get() && isActive) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (readSize > 0) {
                        // 转换为字节数组
                        val byteBuffer = ByteArray(readSize * 2)
                        for (i in 0 until readSize) {
                            val value = buffer[i].toInt()
                            byteBuffer[i * 2] = (value and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
                        }
                        
                        // 发送音频数据到服务器
                        protocol?.sendAudio(byteBuffer)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 音频录制失败: ${e.message}", e)
            _uiState.emit(SttState.Error(e))
        }
    }

    /**
     * 停止音频录制
     */
    private suspend fun stopAudioRecording() = withContext(Dispatchers.IO) {
        if (!isRecording.get()) {
            return@withContext
        }

        isRecording.set(false)
        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.apply {
            if (state == AudioRecord.STATE_INITIALIZED) {
                stop()
            }
            release()
        }
        audioRecord = null
        
        // 通知服务器停止监听
        sendStopListening()

        Log.d(TAG, "✅ 音频录制已停止")
    }

    /**
     * 发送开始监听命令
     */
    private suspend fun sendStartListening() {
        val message = JSONObject().apply {
            put("type", "command")
            put("action", "start_listening")
            put("mode", "auto_stop")
        }
        protocol?.sendText(message.toString())
    }

    /**
     * 发送停止监听命令
     */
    private suspend fun sendStopListening() {
        val message = JSONObject().apply {
            put("type", "command")
            put("action", "stop_listening")
        }
        protocol?.sendText(message.toString())
    }

    /**
     * 处理服务器消息
     */
    private fun handleServerMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.optString("type")

            when (type) {
                MessageType.STT -> {
                    val text = json.optString("text")
                    val isFinal = json.optBoolean("is_final", false)
                    
                    Log.d(TAG, "📝 收到 STT 结果: text='$text', isFinal=$isFinal")
                    
                    scope.launch {
                        val event = if (isFinal) {
                            InputEvent.Final(listOf(Pair(text, 1.0f)))
                        } else {
                            InputEvent.Partial(text)
                        }
                        eventListener?.invoke(event)
                    }
                }
                MessageType.LLM -> {
                    // 处理 LLM 响应（意图识别结果）
                    val emotion = json.optString("emotion", "")
                    Log.d(TAG, "🤖 收到 LLM 响应: emotion='$emotion'")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 处理服务器消息失败: ${e.message}", e)
        }
    }

    override fun destroy() {
        Log.d(TAG, "🧹 销毁 WebSocketInputDevice")
        scope.launch {
            stopAudioRecording()
            protocol?.disconnect()
        }
        scope.cancel()
    }
}

