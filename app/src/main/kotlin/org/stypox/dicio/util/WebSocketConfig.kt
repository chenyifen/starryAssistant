package org.stypox.dicio.util

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * WebSocket 配置管理器
 * 从 assets/websocket_config.json 读取配置
 */
object WebSocketConfig {
    private const val TAG = "WebSocketConfig"
    private const val CONFIG_FILE = "websocket_config.json"
    
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
            config = JSONObject().apply {
                put("SYSTEM_OPTIONS", JSONObject().apply {
                    put("CLIENT_ID", "default-client")
                    put("DEVICE_ID", "default-device")
                    put("NETWORK", JSONObject().apply {
                        put("WEBSOCKET_URL", "ws://192.168.0.102:8000/xiaozhi/v1/")
                        put("WEBSOCKET_ACCESS_TOKEN", "test-token")
                    })
                })
            }
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
                ?: "ws://192.168.0.108:8000/xiaozhi/v1/"
        } catch (e: Exception) {
            Log.e(TAG, "获取 WebSocket URL 失败: ${e.message}")
            "ws://192.168.0.108:8000/xiaozhi/v1/"
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
                ?: "test-token"
        } catch (e: Exception) {
            Log.e(TAG, "获取访问令牌失败: ${e.message}")
            "test-token"
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
                .replace("/xiaozhi/v1/", "/api/v1/activation")
            httpUrl
        } else {
            "http://192.168.0.108:8000/api/v1/activation"
        }
    }
    
    /**
     * 检查 WebSocket 是否可用
     */
    fun isWebSocketAvailable(context: Context): Boolean {
        val url = getWebSocketUrl(context)
        return url.isNotEmpty() && (url.startsWith("ws://") || url.startsWith("wss://"))
    }
}

