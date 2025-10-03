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
                _uiState.emit(SttState.ErrorLoading(Exception(error)))
            }
        }
        
        // 设置连接状态变化回调
        protocol?.onConnectionStateChanged { connected, message ->
            Log.d(TAG, "连接状态变化: connected=$connected, message=$message")
            scope.launch {
                if (connected) {
                    _uiState.emit(SttState.Loaded)
                } else {
                    _uiState.emit(SttState.NotAvailable)
                }
            }
        }
    }

    override fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean {
        Log.d(TAG, "📥 ==================== tryLoad 被调用 ====================")
        Log.d(TAG, "📥 thenStartListeningEventListener: ${if (thenStartListeningEventListener != null) "有回调" else "null"}")
        Log.d(TAG, "📥 Protocol 实例: ${if (protocol != null) "已初始化" else "null"}")
        
        scope.launch {
            _uiState.emit(SttState.Loading(thenStartListeningEventListener != null))
            Log.d(TAG, "📥 状态已更新为: Loading")
            
            // 连接到服务器
            Log.d(TAG, "🔌 准备连接到 WebSocket 服务器...")
            val connected = protocol?.connect() ?: false
            Log.d(TAG, "🔌 连接结果: $connected")
            
            if (connected) {
                Log.d(TAG, "✅ WebSocket 连接成功!")
                _uiState.emit(SttState.Loaded)
                
                thenStartListeningEventListener?.let {
                    Log.d(TAG, "🎤 设置事件监听器并开始录音...")
                    this@WebSocketInputDevice.eventListener = it
                    _uiState.emit(SttState.Listening)
                    startAudioRecording()
                }
            } else {
                Log.e(TAG, "❌ WebSocket 连接失败!")
                _uiState.emit(SttState.ErrorLoading(Exception("无法连接到服务器")))
            }
            
            Log.d(TAG, "📥 ==================== tryLoad 完成 ====================")
        }
        return true
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
                _uiState.emit(SttState.ErrorLoading(Exception("AudioRecord 初始化失败")))
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
                val byteBuffer = java.nio.ByteBuffer.allocate(FRAME_SIZE * 2)
                byteBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                
                var frameCount = 0
                while (isRecording.get() && isActive) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (readSize > 0) {
                        // 转换为字节数组 (Little-Endian PCM16)
                        byteBuffer.clear()
                        for (i in 0 until readSize) {
                            byteBuffer.putShort(buffer[i])
                        }
                        
                        val audioData = byteBuffer.array().copyOf(readSize * 2)
                        
                        // 每100帧打印一次详细信息用于调试
                        if (frameCount % 100 == 0) {
                            val first8Bytes = audioData.take(8).joinToString(" ") { "%02X".format(it) }
                            Log.d(TAG, "🎵 Frame $frameCount: size=${audioData.size}, first 8 bytes: $first8Bytes")
                        }
                        frameCount++
                        
                        // 发送音频数据到服务器
                        protocol?.sendAudio(audioData)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 音频录制失败: ${e.message}", e)
            _uiState.emit(SttState.ErrorLoading(e))
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
            put("type", "listen")
            put("state", "start")
            put("mode", "auto")
        }
        protocol?.sendText(message.toString())
    }

    /**
     * 发送停止监听命令
     */
    private suspend fun sendStopListening() {
        val message = JSONObject().apply {
            put("type", "listen")
            put("state", "stop")
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

    override fun stopListening() {
        Log.d(TAG, "⏹️ 停止监听")
        scope.launch {
            stopAudioRecording()
            _uiState.emit(SttState.Loaded)
        }
    }

    override fun onClick(eventListener: (InputEvent) -> Unit) {
        val currentState = uiState.value
        if (currentState == SttState.Listening) {
            stopListening()
        } else if (currentState == SttState.Loaded) {
            this.eventListener = eventListener
            scope.launch {
                _uiState.emit(SttState.Listening)
                startAudioRecording()
            }
        }
    }

    override suspend fun destroy() {
        Log.d(TAG, "🧹 销毁 WebSocketInputDevice")
        stopAudioRecording()
        protocol?.disconnect()
        scope.cancel()
    }
}

