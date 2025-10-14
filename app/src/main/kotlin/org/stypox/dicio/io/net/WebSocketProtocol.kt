package org.stypox.dicio.io.net

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okhttp3.WebSocket
import okio.ByteString
import org.json.JSONObject
import org.stypox.dicio.activation.ActivationManager
import org.stypox.dicio.io.audio.AdaptiveAudioProcessor
import org.stypox.dicio.io.audio.AudioCodecType
import java.util.concurrent.TimeUnit

/**
 * WebSocket 协议实现
 * 参考 py-xiaozhi 的 WebsocketProtocol 实现
 * 支持与服务端进行 ASR、TTS、MCP 等通信
 * 使用PCM音频编解码
 */
class WebSocketProtocol(
    private val context: Context,
    private val serverUrl: String,
    private val accessToken: String,
    private val deviceId: String,
    private val clientId: String
) : Protocol {

    companion object {
        private const val PING_INTERVAL = 20L // 心跳间隔（秒），匹配服务器配置
        private const val CONNECT_TIMEOUT = 10L // 连接超时（秒）
        private const val HELLO_TIMEOUT = 10L // 等待 hello 响应超时（秒）
        
        private var instanceCounter = 0
    }
    
    private val TAG = "WebSocketProtocol#${++instanceCounter}"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 音频处理器
    private val audioProcessor = AdaptiveAudioProcessor(context)
    
    // 协商后的音频配置
    private var negotiatedAudioConfig: AudioConfig = AudioConfig()
    
    // 连接状态
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    // WebSocket 连接
    private var webSocket: WebSocket? = null
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // 不设置读超时，保持长连接
            .writeTimeout(0, TimeUnit.SECONDS)
            .pingInterval(PING_INTERVAL, TimeUnit.SECONDS)
            .build()
    }
    
    // 回调
    private var onTextMessageCallback: ((String) -> Unit)? = null
    private var onAudioMessageCallback: ((ByteArray) -> Unit)? = null
    private var onNetworkErrorCallback: ((String) -> Unit)? = null
    private var onConnectionStateChangedCallback: ((Boolean, String) -> Unit)? = null
    
    // Hello 响应等待
    private var helloReceived = CompletableDeferred<Boolean>()
    
    // 连接标志
    private var isConnected = false
    private var isClosing = false

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        if (isClosing) {
            Log.w(TAG, "连接正在关闭中，取消新的连接尝试")
            return@withContext false
        }

        if (isConnected) {
            Log.d(TAG, "已经连接到服务器")
            return@withContext true
        }

        try {
            _connectionState.value = ConnectionState.Connecting
            helloReceived = CompletableDeferred()

            // 初始化音频处理器
            if (!audioProcessor.initialize()) {
                Log.w(TAG, "⚠️ 音频处理器初始化失败，使用PCM模式")
            }

            // 构建请求
            val request = Request.Builder()
                .url(serverUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Protocol-Version", "1")
                .addHeader("Device-Id", deviceId)
                .addHeader("Client-Id", clientId)
                .build()

            Log.d(TAG, "正在连接到 WebSocket 服务器: $serverUrl")

            // 建立 WebSocket 连接
            webSocket = okHttpClient.newWebSocket(request, InternalWebSocketListener())

            // 发送 hello 消息，包含音频配置
            val audioFormat = AudioCodec.PCM
            
            val helloMessage = JSONObject().apply {
                put("type", MessageType.HELLO)
                put("version", 1)
                put("features", JSONObject().apply {
                    put("mcp", true)
                })
                put("transport", "websocket")
                put("audio_params", JSONObject().apply {
                    put("format", audioFormat)
                    put("sample_rate", 16000)
                    put("channels", 1)
                })
            }
            sendText(helloMessage.toString())

            // 等待 hello 响应
            withTimeout(HELLO_TIMEOUT * 1000) {
                helloReceived.await()
            }

            isConnected = true
            _connectionState.value = ConnectionState.Connected
            Log.i(TAG, "✅ 已连接到 WebSocket 服务器，音频格式: $audioFormat")
            onConnectionStateChangedCallback?.invoke(true, "连接成功")
            
            return@withContext true

        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "❌ 等待服务器 hello 响应超时")
            cleanup()
            _connectionState.value = ConnectionState.Error("等待响应超时")
            onNetworkErrorCallback?.invoke("等待响应超时")
            return@withContext false

        } catch (e: Exception) {
            Log.e(TAG, "❌ WebSocket 连接失败: ${e.message}", e)
            cleanup()
            _connectionState.value = ConnectionState.Error("连接失败: ${e.message}")
            onNetworkErrorCallback?.invoke("无法连接服务: ${e.message}")
            return@withContext false
        }
    }

    override suspend fun disconnect() {
        isClosing = true
        cleanup()
    }

    override suspend fun sendText(message: String) {
        webSocket?.send(message)?.also {
            Log.d(TAG, "📤 发送文本消息: ${message.take(200)}${if (message.length > 200) "..." else ""}")
        } ?: run {
            Log.w(TAG, "WebSocket 未连接，无法发送文本消息")
        }
    }

    override suspend fun sendAudio(audioData: ByteArray) {
        // 如果是PCM数据，需要先转换为ShortArray然后编码
        val pcmShorts = audioProcessor.decodeAudio(audioData) ?: run {
            Log.e(TAG, "❌ 无法解析PCM数据")
            return
        }
        
        // 使用音频处理器编码音频数据
        val encodedAudio = audioProcessor.encodeAudio(pcmShorts) ?: run {
            Log.e(TAG, "❌ 音频编码失败")
            return
        }
        
        webSocket?.send(ByteString.of(*encodedAudio))?.also {
            val codecInfo = audioProcessor.getCodecInfo()
            Log.v(TAG, "📤 发送音频数据: ${audioData.size} -> ${encodedAudio.size} 字节 ($codecInfo)")
        } ?: run {
            Log.w(TAG, "WebSocket 未连接，无法发送音频数据")
        }
    }
    
    /**
     * 直接发送已编码的音频数据（用于WebSocketInputDevice）
     */
    suspend fun sendEncodedAudio(encodedAudio: ByteArray) {
        webSocket?.send(ByteString.of(*encodedAudio))?.also {
            Log.v(TAG, "📤 发送已编码音频数据: ${encodedAudio.size} 字节")
        } ?: run {
            Log.w(TAG, "WebSocket 未连接，无法发送音频数据")
        }
    }

    override fun onTextMessage(callback: (String) -> Unit) {
        onTextMessageCallback = callback
    }

    override fun onAudioMessage(callback: (ByteArray) -> Unit) {
        onAudioMessageCallback = callback
    }

    override fun onNetworkError(callback: (String) -> Unit) {
        onNetworkErrorCallback = callback
    }

    override fun onConnectionStateChanged(callback: (Boolean, String) -> Unit) {
        onConnectionStateChangedCallback = callback
    }

    private fun cleanup() {
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        isConnected = false
        isClosing = false
        audioProcessor.cleanup()
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * WebSocket 监听器
     */
    private inner class InternalWebSocketListener : okhttp3.WebSocketListener() {
        
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket 连接已建立")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "📥 收到文本消息: ${text.take(200)}${if (text.length > 200) "..." else ""}")
            scope.launch {
                handleTextMessage(text)
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            Log.d(TAG, "📥 收到音频数据: ${bytes.size} 字节")
            scope.launch {
                handleAudioMessage(bytes.toByteArray())
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket 正在关闭: code=$code, reason=$reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "WebSocket 已关闭: code=$code, reason=$reason")
            isConnected = false
            _connectionState.value = ConnectionState.Disconnected
            onConnectionStateChangedCallback?.invoke(false, "连接已关闭")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket 连接失败: ${t.message}", t)
            isConnected = false
            _connectionState.value = ConnectionState.Error("连接失败: ${t.message}")
            onNetworkErrorCallback?.invoke("连接失败: ${t.message}")
            onConnectionStateChangedCallback?.invoke(false, "连接失败")
        }
    }

    /**
     * 处理文本消息
     */
    private fun handleTextMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.optString("type")

            when (type) {
                MessageType.HELLO -> {
                    Log.d(TAG, "收到服务器 hello 响应")
                    
                    // 处理音频配置协商
                    if (json.has("audio_params")) {
                        scope.launch {
                            handleAudioConfigNegotiation(json.getJSONObject("audio_params"))
                        }
                    }
                    
                    // 检查是否需要激活
                    if (json.has("activation")) {
                        val activationData = json.getJSONObject("activation")
                        handleActivationRequired(activationData)
                    }
                    
                    helloReceived.complete(true)
                }
                MessageType.AUDIO_CONFIG -> {
                    Log.d(TAG, "收到音频配置更新")
                    if (json.has("audio_params")) {
                        scope.launch {
                            handleAudioConfigNegotiation(json.getJSONObject("audio_params"))
                        }
                    }
                }
                MessageType.STT -> {
                    val text = json.optString("text", "")
                    val isFinal = json.optBoolean("is_final", false)
                    Log.d(TAG, "📝 STT 识别: \"$text\" (${if (isFinal) "最终" else "部分"})")
                    onTextMessageCallback?.invoke(message)
                }
                MessageType.TTS -> {
                    Log.d(TAG, "🔊 TTS 消息")
                    onTextMessageCallback?.invoke(message)
                }
                MessageType.LLM -> {
                    val content = json.optString("content", "")
                    Log.d(TAG, "🤖 LLM 响应: ${content.take(50)}${if (content.length > 50) "..." else ""}")
                    onTextMessageCallback?.invoke(message)
                }
                "activation" -> {
                    // 处理激活相关消息
                    handleActivationMessage(json)
                }
                else -> {
                    Log.d(TAG, "📨 其他消息类型: $type")
                    // 转发给应用层处理
                    onTextMessageCallback?.invoke(message)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析文本消息失败: ${e.message}", e)
        }
    }

    /**
     * 处理音频消息
     */
    private fun handleAudioMessage(audioData: ByteArray) {
        onAudioMessageCallback?.invoke(audioData)
    }

    /**
     * 处理音频配置协商
     */
    private suspend fun handleAudioConfigNegotiation(audioParams: JSONObject) = withContext(Dispatchers.IO) {
        try {
            val serverFormat = audioParams.optString("format", AudioCodec.PCM)
            val serverSampleRate = audioParams.optInt("sample_rate", 16000)
            val serverChannels = audioParams.optInt("channels", 1)
            
            Log.d(TAG, "🎵 服务器音频配置: format=$serverFormat, rate=${serverSampleRate}Hz, " +
                      "channels=$serverChannels")
            
            // 更新协商后的音频配置
            negotiatedAudioConfig = AudioConfig(
                codec = serverFormat,
                sampleRate = serverSampleRate,
                channels = serverChannels
            )
            
            // 根据服务器配置调整本地音频处理器
            if (serverFormat == AudioCodec.PCM) {
                Log.d(TAG, "📡 服务器使用PCM，保持PCM模式")
                audioProcessor.setAudioQuality(org.stypox.dicio.io.audio.AudioQuality.HIGH_QUALITY)
            } else {
                Log.w(TAG, "⚠️ 未知音频格式: $serverFormat，使用默认PCM配置")
                audioProcessor.setAudioQuality(org.stypox.dicio.io.audio.AudioQuality.HIGH_QUALITY)
            }
            
            Log.i(TAG, "🔧 音频配置协商完成: ${audioProcessor.getCodecInfo()}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 处理音频配置协商失败: ${e.message}", e)
        }
    }

    /**
     * 获取当前协商的音频配置
     */
    fun getNegotiatedAudioConfig(): AudioConfig = negotiatedAudioConfig

    /**
     * 获取音频处理器
     */
    fun getAudioProcessor(): AdaptiveAudioProcessor = audioProcessor
    
    /**
     * 处理激活需求
     * 
     * 当服务器返回激活数据时调用
     */
    private fun handleActivationRequired(activationData: JSONObject) {
        try {
            val code = activationData.optString("code", "")
            val challenge = activationData.optString("challenge", "")
            val message = activationData.optString("message", "请在控制面板输入验证码")
            
            if (code.isEmpty() || challenge.isEmpty()) {
                Log.w(TAG, "⚠️ 激活数据不完整")
                return
            }
            
            Log.i(TAG, "")
            Log.i(TAG, "═══════════════════════════════════════════════════════════════")
            Log.i(TAG, "🔐 服务器要求设备激活")
            Log.i(TAG, "═══════════════════════════════════════════════════════════════")
            
            // 使用激活管理器处理激活响应
            ActivationManager.handleActivationResponse(context, code, challenge, message)
            
            // 构建并打印激活请求 payload
            val payload = ActivationManager.buildActivationRequest(context, challenge)
            if (payload != null) {
                Log.i(TAG, "")
                Log.i(TAG, "📋 激活请求 Payload (可用于手动激活):")
                Log.i(TAG, payload)
                Log.i(TAG, "")
            }
            
            Log.i(TAG, "═══════════════════════════════════════════════════════════════")
            Log.i(TAG, "")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 处理激活需求失败: ${e.message}", e)
        }
    }
    
    /**
     * 处理激活相关消息
     */
    private fun handleActivationMessage(json: JSONObject) {
        try {
            val status = json.optString("status", "")
            
            when (status) {
                "success" -> {
                    Log.i(TAG, "🎉 设备激活成功!")
                    ActivationManager.markAsActivated(context)
                }
                "pending" -> {
                    Log.i(TAG, "⏳ 等待用户输入验证码...")
                }
                "failed" -> {
                    val error = json.optString("error", "未知错误")
                    Log.e(TAG, "❌ 设备激活失败: $error")
                }
                else -> {
                    Log.d(TAG, "收到激活消息: $status")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 处理激活消息失败: ${e.message}", e)
        }
    }

    /**
     * 清理资源
     */
    fun destroy() {
        scope.cancel()
        cleanup()
    }
}

