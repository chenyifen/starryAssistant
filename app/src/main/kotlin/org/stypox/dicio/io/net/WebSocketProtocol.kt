package org.stypox.dicio.io.net

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okhttp3.WebSocket
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WebSocket 协议实现
 * 参考 py-xiaozhi 的 WebsocketProtocol 实现
 * 支持与服务端进行 ASR、TTS、MCP 等通信
 */
class WebSocketProtocol(
    private val serverUrl: String,
    private val accessToken: String,
    private val deviceId: String,
    private val clientId: String
) : Protocol {

    companion object {
        private const val TAG = "WebSocketProtocol"
        private const val PING_INTERVAL = 30L // 心跳间隔（秒）
        private const val CONNECT_TIMEOUT = 10L // 连接超时（秒）
        private const val HELLO_TIMEOUT = 10L // 等待 hello 响应超时（秒）
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
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

            // 发送 hello 消息
            val helloMessage = JSONObject().apply {
                put("type", MessageType.HELLO)
                put("version", 1)
                put("features", JSONObject().apply {
                    put("mcp", true)
                })
                put("transport", "websocket")
                put("audio_params", JSONObject().apply {
                    put("format", "opus")
                    put("sample_rate", 16000)
                    put("channels", 1)
                    put("frame_duration", 20)
                })
            }
            sendText(helloMessage.toString())

            // 等待 hello 响应
            withTimeout(HELLO_TIMEOUT * 1000) {
                helloReceived.await()
            }

            isConnected = true
            _connectionState.value = ConnectionState.Connected
            Log.i(TAG, "✅ 已连接到 WebSocket 服务器")
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
        webSocket?.send(message) ?: run {
            Log.w(TAG, "WebSocket 未连接，无法发送文本消息")
        }
    }

    override suspend fun sendAudio(audioData: ByteArray) {
        webSocket?.send(ByteString.of(*audioData)) ?: run {
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
            scope.launch {
                handleTextMessage(text)
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
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
                    helloReceived.complete(true)
                }
                else -> {
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
     * 清理资源
     */
    fun destroy() {
        scope.cancel()
        cleanup()
    }
}

