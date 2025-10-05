package org.stypox.dicio.util

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * WebSocket 配置管理器
 * 统一管理所有服务器相关配置，从 assets/websocket_config.json 读取配置
 * 
 * 支持的配置项：
 * - WebSocket 服务器 URL
 * - HTTP 服务器 URL  
 * - 激活服务器 URL
 * - 访问令牌
 * - 设备和客户端 ID
 * - 服务器端口配置
 */
object WebSocketConfig {
    private const val TAG = "WebSocketConfig"
    private const val CONFIG_FILE = "websocket_config.json"
    
    // 默认配置常量
    private const val DEFAULT_SERVER_HOST = "192.168.0.107"
    private const val DEFAULT_WEBSOCKET_PORT = 8000
    private const val DEFAULT_HTTP_PORT = 8003
    private const val DEFAULT_WEBSOCKET_PATH = "/xiaozhi/v1/"
    private const val DEFAULT_HTTP_PATH = "/api/v1/"
    private const val DEFAULT_ACTIVATION_PATH = "/api/v1/activation"
    private const val DEFAULT_ACCESS_TOKEN = "test-token"
    
    private var config: JSONObject? = null
    
    /**
     * 初始化配置
     */
    fun initialize(context: Context) {
        if (config != null) {
            return
        }
        
        try {
            val inputStream = context.assets.open(CONFIG_FILE)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.use { it.readText() }
            config = JSONObject(jsonString)
            Log.d(TAG, "✅ WebSocket 配置加载成功")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 加载 WebSocket 配置失败: ${e.message}", e)
            // 使用默认配置
            config = createDefaultConfig()
        }
    }
    
    /**
     * 获取 WebSocket URL
     */
    fun getWebSocketUrl(context: Context): String {
        initialize(context)
        return try {
            config?.getJSONObject("SYSTEM_OPTIONS")
                ?.getJSONObject("NETWORK")
                ?.getString("WEBSOCKET_URL")
                ?: getDefaultWebSocketUrl()
        } catch (e: Exception) {
            Log.e(TAG, "获取 WebSocket URL 失败: ${e.message}")
            getDefaultWebSocketUrl()
        }
    }
    
    /**
     * 获取访问令牌
     */
    fun getAccessToken(context: Context): String {
        initialize(context)
        return try {
            config?.getJSONObject("SYSTEM_OPTIONS")
                ?.getJSONObject("NETWORK")
                ?.getString("WEBSOCKET_ACCESS_TOKEN")
                ?: DEFAULT_ACCESS_TOKEN
        } catch (e: Exception) {
            Log.e(TAG, "获取访问令牌失败: ${e.message}")
            DEFAULT_ACCESS_TOKEN
        }
    }
    
    /**
     * 获取设备 ID
     */
    fun getDeviceId(context: Context): String {
        initialize(context)
        return try {
            config?.getJSONObject("SYSTEM_OPTIONS")
                ?.getString("DEVICE_ID")
                ?: android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )
        } catch (e: Exception) {
            Log.e(TAG, "获取设备 ID 失败: ${e.message}")
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
        }
    }
    
    /**
     * 获取客户端 ID
     */
    fun getClientId(context: Context): String {
        initialize(context)
        return try {
            config?.getJSONObject("SYSTEM_OPTIONS")
                ?.getString("CLIENT_ID")
                ?: java.util.UUID.randomUUID().toString()
        } catch (e: Exception) {
            Log.e(TAG, "获取客户端 ID 失败: ${e.message}")
            java.util.UUID.randomUUID().toString()
        }
    }
    
    /**
     * 获取激活 URL
     */
    fun getActivationUrl(context: Context): String {
        initialize(context)
        return try {
            config?.getJSONObject("SYSTEM_OPTIONS")
                ?.getJSONObject("NETWORK")
                ?.getString("ACTIVATION_URL")
                ?: getDefaultActivationUrl(context)
        } catch (e: Exception) {
            Log.e(TAG, "获取激活 URL 失败: ${e.message}")
            getDefaultActivationUrl(context)
        }
    }
    
    /**
     * 生成默认激活 URL
     */
    private fun getDefaultActivationUrl(context: Context): String {
        val webSocketUrl = getWebSocketUrl(context)
        return if (webSocketUrl.isNotEmpty()) {
            // 将 WebSocket URL 转换为 HTTP URL
            val httpUrl = webSocketUrl
                .replace("ws://", "http://")
                .replace("wss://", "https://")
                .replace(DEFAULT_WEBSOCKET_PATH, DEFAULT_ACTIVATION_PATH)
            httpUrl
        } else {
            getDefaultHttpUrl() + DEFAULT_ACTIVATION_PATH
        }
    }
    
    /**
     * 检查 WebSocket 是否可用
     */
    fun isWebSocketAvailable(context: Context): Boolean {
        val url = getWebSocketUrl(context)
        return url.isNotEmpty() && (url.startsWith("ws://") || url.startsWith("wss://"))
    }
    
    /**
     * 获取服务器主机地址
     */
    fun getServerHost(context: Context): String {
        initialize(context)
        return try {
            config?.getJSONObject("SYSTEM_OPTIONS")
                ?.getJSONObject("NETWORK")
                ?.getString("SERVER_HOST")
                ?: DEFAULT_SERVER_HOST
        } catch (e: Exception) {
            Log.e(TAG, "获取服务器主机地址失败: ${e.message}")
            DEFAULT_SERVER_HOST
        }
    }
    
    /**
     * 获取 WebSocket 端口
     */
    fun getWebSocketPort(context: Context): Int {
        initialize(context)
        return try {
            config?.getJSONObject("SYSTEM_OPTIONS")
                ?.getJSONObject("NETWORK")
                ?.getInt("WEBSOCKET_PORT")
                ?: DEFAULT_WEBSOCKET_PORT
        } catch (e: Exception) {
            Log.e(TAG, "获取 WebSocket 端口失败: ${e.message}")
            DEFAULT_WEBSOCKET_PORT
        }
    }
    
    /**
     * 获取 HTTP 端口
     */
    fun getHttpPort(context: Context): Int {
        initialize(context)
        return try {
            config?.getJSONObject("SYSTEM_OPTIONS")
                ?.getJSONObject("NETWORK")
                ?.getInt("HTTP_PORT")
                ?: DEFAULT_HTTP_PORT
        } catch (e: Exception) {
            Log.e(TAG, "获取 HTTP 端口失败: ${e.message}")
            DEFAULT_HTTP_PORT
        }
    }
    
    /**
     * 获取 HTTP 服务器 URL
     */
    fun getHttpUrl(context: Context): String {
        initialize(context)
        return try {
            config?.getJSONObject("SYSTEM_OPTIONS")
                ?.getJSONObject("NETWORK")
                ?.getString("HTTP_URL")
                ?: getDefaultHttpUrl()
        } catch (e: Exception) {
            Log.e(TAG, "获取 HTTP URL 失败: ${e.message}")
            getDefaultHttpUrl()
        }
    }
    
    // ==================== 私有辅助方法 ====================
    
    /**
     * 创建默认配置
     */
    private fun createDefaultConfig(): JSONObject {
        return JSONObject().apply {
            put("SYSTEM_OPTIONS", JSONObject().apply {
                put("CLIENT_ID", "default-client")
                put("DEVICE_ID", "default-device")
                put("NETWORK", JSONObject().apply {
                    put("SERVER_HOST", DEFAULT_SERVER_HOST)
                    put("WEBSOCKET_PORT", DEFAULT_WEBSOCKET_PORT)
                    put("HTTP_PORT", DEFAULT_HTTP_PORT)
                    put("WEBSOCKET_URL", getDefaultWebSocketUrl())
                    put("HTTP_URL", getDefaultHttpUrl())
                    put("ACTIVATION_URL", getDefaultHttpUrl() + DEFAULT_ACTIVATION_PATH)
                    put("WEBSOCKET_ACCESS_TOKEN", DEFAULT_ACCESS_TOKEN)
                })
            })
        }
    }
    
    /**
     * 获取默认 WebSocket URL
     */
    private fun getDefaultWebSocketUrl(): String {
        return "ws://$DEFAULT_SERVER_HOST:$DEFAULT_WEBSOCKET_PORT$DEFAULT_WEBSOCKET_PATH"
    }
    
    /**
     * 获取默认 HTTP URL
     */
    private fun getDefaultHttpUrl(): String {
        return "http://$DEFAULT_SERVER_HOST:$DEFAULT_HTTP_PORT"
    }
    
    /**
     * 验证 URL 格式
     */
    fun validateUrl(url: String, type: String = "WebSocket"): Boolean {
        return when (type.lowercase()) {
            "websocket" -> url.startsWith("ws://") || url.startsWith("wss://")
            "http" -> url.startsWith("http://") || url.startsWith("https://")
            else -> url.isNotEmpty()
        }
    }
    
    /**
     * 打印当前配置信息（用于调试）
     */
    fun printConfigInfo(context: Context) {
        initialize(context)
        Log.i(TAG, "")
        Log.i(TAG, "═══════════════════════════════════════")
        Log.i(TAG, "🌐 WebSocket 配置信息")
        Log.i(TAG, "═══════════════════════════════════════")
        Log.i(TAG, "服务器主机: ${getServerHost(context)}")
        Log.i(TAG, "WebSocket端口: ${getWebSocketPort(context)}")
        Log.i(TAG, "HTTP端口: ${getHttpPort(context)}")
        Log.i(TAG, "WebSocket URL: ${getWebSocketUrl(context)}")
        Log.i(TAG, "HTTP URL: ${getHttpUrl(context)}")
        Log.i(TAG, "激活 URL: ${getActivationUrl(context)}")
        Log.i(TAG, "访问令牌: ${getAccessToken(context)}")
        Log.i(TAG, "设备 ID: ${getDeviceId(context)}")
        Log.i(TAG, "客户端 ID: ${getClientId(context)}")
        Log.i(TAG, "═══════════════════════════════════════")
        Log.i(TAG, "")
    }
}

