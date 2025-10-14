package org.stypox.dicio.io.net

import kotlinx.coroutines.flow.StateFlow

/**
 * 通信协议接口 - 定义与服务端通信的基础协议
 * 参考 py-xiaozhi 的 Protocol 实现
 */
interface Protocol {
    /**
     * 连接状态流
     */
    val connectionState: StateFlow<ConnectionState>
    
    /**
     * 连接到服务器
     * @return 是否连接成功
     */
    suspend fun connect(): Boolean
    
    /**
     * 断开连接
     */
    suspend fun disconnect()
    
    /**
     * 发送文本消息
     * @param message JSON 格式的文本消息
     */
    suspend fun sendText(message: String)
    
    /**
     * 发送音频数据
     * @param audioData 音频字节数据
     */
    suspend fun sendAudio(audioData: ByteArray)
    
    /**
     * 设置文本消息接收回调
     */
    fun onTextMessage(callback: (String) -> Unit)
    
    /**
     * 设置音频数据接收回调
     */
    fun onAudioMessage(callback: (ByteArray) -> Unit)
    
    /**
     * 设置网络错误回调
     */
    fun onNetworkError(callback: (String) -> Unit)
    
    /**
     * 设置连接状态变化回调
     */
    fun onConnectionStateChanged(callback: (Boolean, String) -> Unit)
}

/**
 * 连接状态
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * 协议消息类型
 */
object MessageType {
    const val HELLO = "hello"
    const val TTS = "tts"
    const val STT = "stt"
    const val LLM = "llm"
    const val MCP = "mcp"
    const val IOT = "iot"
    const val SYSTEM = "system"
    const val ALERT = "alert"
    const val AUDIO_CONFIG = "audio_config"  // 音频配置协商
}

/**
 * 音频编解码器类型
 */
object AudioCodec {
    const val PCM = "pcm"
}

/**
 * 音频配置
 */
data class AudioConfig(
    val codec: String = AudioCodec.PCM,
    val sampleRate: Int = 16000,
    val channels: Int = 1
)

/**
 * TTS 状态
 */
object TtsState {
    const val START = "start"
    const val STOP = "stop"
    const val SENTENCE_START = "sentence_start"
}

/**
 * 监听模式
 */
enum class ListeningMode {
    AUTO_STOP,      // 自动停止模式（检测到语音结束后自动停止）
    MANUAL_STOP,    // 手动停止模式（需要用户手动停止）
    REALTIME        // 实时模式（持续监听）
}

/**
 * 中断原因
 */
enum class AbortReason {
    NONE,
    WAKE_WORD_DETECTED,
    USER_INTERRUPT
}

