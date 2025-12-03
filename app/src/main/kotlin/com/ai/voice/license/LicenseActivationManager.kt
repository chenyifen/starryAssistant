package com.ai.voice.license

import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.*

/**
 * 授权激活管理器
 * 实现基于MAC地址和激活码的授权验证功能
 */
class LicenseActivationManager private constructor(
    private val context: Context,
    private val appId: String,
    private val apiBaseUrl: String
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("license_activation_prefs", Context.MODE_PRIVATE)
    private val STORAGE_KEY = "license_activation_status"
    private val secureStorage = SecureStorage(context)
    private val VERIFICATION_INTERVAL = 24 * 60 * 60 * 1000L  // 24小时
    
    companion object {
        private const val TAG = "LicenseActivation"
        
        // 默认配置（可通过initialize方法覆盖）
        private var defaultAppId: String = "20e6f2ea2769f8e5e8e77fe7eceed485" // 固定应用ID
        private var defaultApiBaseUrl: String = "https://namingyou.com"
        
        @Volatile
        private var instance: LicenseActivationManager? = null
        
        /**
         * 初始化激活管理器
         * @param context Android Context
         * @param appId 应用ID（可选，默认使用包名）
         * @param apiBaseUrl API基础地址（可选，默认使用文档中的地址）
         */
        fun initialize(
            context: Context,
            appId: String = defaultAppId,
            apiBaseUrl: String = defaultApiBaseUrl
        ): LicenseActivationManager {
            return instance ?: synchronized(this) {
                instance ?: LicenseActivationManager(context, appId, apiBaseUrl).also {
                    instance = it
                }
            }
        }
        
        /**
         * 获取单例实例（必须先调用initialize）
         */
        fun getInstance(): LicenseActivationManager {
            return instance ?: throw IllegalStateException("LicenseActivationManager未初始化，请先调用initialize()")
        }
    }
    
    /**
     * 获取MAC地址
     * Android 6.0+ 无法直接获取WiFi MAC地址，需要其他方式
     */
    fun getMacAddress(): String? {
        Log.d(TAG, "开始获取MAC地址...")
        return try {
            // 方法1: 通过NetworkInterface获取（推荐）
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            Log.d(TAG, "通过NetworkInterface获取，找到 ${interfaces.size} 个网络接口")
            for (networkInterface in interfaces) {
                val macBytes = networkInterface.hardwareAddress ?: continue
                val sb = StringBuilder()
                for (byte in macBytes) {
                    sb.append(String.format("%02X:", byte))
                }
                if (sb.isNotEmpty()) {
                    sb.deleteCharAt(sb.length - 1)
                    val mac = sb.toString()
                    // 排除回环接口和无效MAC
                    if (!mac.equals("02:00:00:00:00:00", ignoreCase = true) && 
                        mac.isNotEmpty()) {
                        Log.d(TAG, "✅ 通过NetworkInterface获取MAC地址成功: $mac (接口: ${networkInterface.name})")
                        return mac
                    }
                }
            }

            // 方法1.1: 读取sysfs网卡地址
            val sysfsCandidates = listOf(
                "/sys/class/net/wlan0/address",
                "/sys/class/net/eth0/address",
                "/sys/class/net/en0/address"
            )
            for (path in sysfsCandidates) {
                try {
                    val f = File(path)
                    if (f.exists() && f.canRead()) {
                        val mac = f.readText().trim()
                        if (mac.isNotEmpty() && !mac.equals("02:00:00:00:00:00", true) && mac.matches(Regex("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$"))) {
                            Log.d(TAG, "✅ 通过sysfs读取MAC地址成功: $mac ($path)")
                            return mac
                        }
                    }
                } catch (_: Exception) {}
            }
            
            // 方法2: 通过WifiManager获取（Android 6.0+需要位置权限）
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                Log.d(TAG, "尝试通过WifiManager获取MAC地址 (Android版本: ${Build.VERSION.SDK_INT})")
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                val wifiInfo = wifiManager?.connectionInfo
                @Suppress("DEPRECATION")
                val mac = wifiInfo?.macAddress
                if (mac != null && mac.isNotEmpty() && mac != "02:00:00:00:00:00") {
                    Log.d(TAG, "✅ 通过WifiManager获取MAC地址成功: $mac")
                    return mac
                }
            } else {
                Log.d(TAG, "Android 6.0+，跳过WifiManager方式（需要位置权限）")
            }
            
            // 方法3: 使用Android ID作为备选（如果无法获取MAC地址）
            // 注意：这不是真正的MAC地址，但可以作为设备唯一标识
            Log.w(TAG, "⚠️ 无法获取真实MAC地址，使用Android ID作为备选")
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            val fallbackId = androidId ?: "UNKNOWN"
            Log.d(TAG, "使用Android ID作为设备标识: $fallbackId")
            fallbackId
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取MAC地址失败", e)
            null
        }
    }
    
    /**
     * 通过MAC地址验证授权状态
     */
    suspend fun verifyByMac(
        mac: String,
        name: String? = null,
        deviceId: String? = null
    ): Result<JSONObject> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始MAC地址验证...")
                Log.d(TAG, "  - API地址: $apiBaseUrl/api/licenses/verify-by-mac")
                Log.d(TAG, "  - 应用ID: $appId")
                Log.d(TAG, "  - MAC地址: $mac")
                name?.let { Log.d(TAG, "  - 设备名称: $it") }
                deviceId?.let { Log.d(TAG, "  - 设备ID: $it") }
                
                val json = JSONObject().apply {
                    put("appId", appId)
                    put("mac", mac)
                    name?.let { put("name", it) }
                    deviceId?.let { put("deviceId", it) }
                }
                
                val requestBody = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$apiBaseUrl/api/licenses/verify-by-mac")
                    .post(requestBody)
                    .build()
                
                Log.d(TAG, "发送请求: ${json.toString()}")
                
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d(TAG, "✅ MAC地址验证请求成功")
                    Log.d(TAG, "响应内容: $responseBody")
                    Result.success(JSONObject(responseBody ?: "{}"))
                } else {
                    val errorBody = response.body?.string() ?: "请求失败: ${response.code}"
                    Log.e(TAG, "❌ MAC地址验证请求失败: HTTP ${response.code}, $errorBody")
                    Result.failure(IOException(errorBody))
                }
            } catch (e: Exception) {
                val errorMessage = when {
                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true -> {
                        "无法解析服务器域名，请检查网络连接和DNS设置"
                    }
                    e.message?.contains("No address associated with hostname", ignoreCase = true) == true -> {
                        "DNS解析失败，服务器域名可能不存在或无法访问"
                    }
                    e.message?.contains("timeout", ignoreCase = true) == true -> {
                        "连接超时，请检查网络连接"
                    }
                    e.message?.contains("SSL", ignoreCase = true) == true -> {
                        "SSL证书验证失败，请检查服务器证书配置"
                    }
                    else -> e.message ?: "未知网络错误"
                }
                Log.e(TAG, "❌ MAC地址验证异常: $errorMessage", e)
                Log.e(TAG, "   详细错误: ${e.javaClass.simpleName}: ${e.message}")
                Result.failure(IOException(errorMessage, e))
            }
        }
    }
    
    /**
     * 通过激活码激活授权
     */
    suspend fun activateByLicense(
        licenseKey: String,
        mac: String,
        userInfo: Map<String, String>? = null
    ): Result<JSONObject> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始激活码激活...")
                Log.d(TAG, "  - API地址: $apiBaseUrl/api/licenses/apply")
                Log.d(TAG, "  - 应用ID: $appId")
                Log.d(TAG, "  - MAC地址: $mac")
                Log.d(TAG, "  - 激活码: ${licenseKey.take(8)}...") // 只显示前8位，保护隐私
                
                val userInfoJson = JSONObject().apply {
                    put("macAddress", mac)  // MAC地址必须传入
                    userInfo?.forEach { (key, value) -> put(key, value) }
                }
                
                val json = JSONObject().apply {
                    put("licenseKey", licenseKey)
                    put("appId", appId)
                    put("userInfo", userInfoJson)
                }
                
                val requestBody = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$apiBaseUrl/api/licenses/apply")
                    .post(requestBody)
                    .build()
                
                Log.d(TAG, "发送激活请求...")
                
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d(TAG, "✅ 激活码激活请求成功")
                    Log.d(TAG, "响应内容: $responseBody")
                    Result.success(JSONObject(responseBody ?: "{}"))
                } else {
                    val errorBody = response.body?.string() ?: "请求失败: ${response.code}"
                    Log.e(TAG, "❌ 激活码激活请求失败: HTTP ${response.code}, $errorBody")
                    Result.failure(IOException(errorBody))
                }
            } catch (e: Exception) {
                val errorMessage = when {
                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true -> {
                        "无法解析服务器域名，请检查网络连接和DNS设置"
                    }
                    e.message?.contains("No address associated with hostname", ignoreCase = true) == true -> {
                        "DNS解析失败，服务器域名可能不存在或无法访问"
                    }
                    e.message?.contains("timeout", ignoreCase = true) == true -> {
                        "连接超时，请检查网络连接"
                    }
                    e.message?.contains("SSL", ignoreCase = true) == true -> {
                        "SSL证书验证失败，请检查服务器证书配置"
                    }
                    else -> e.message ?: "未知网络错误"
                }
                Log.e(TAG, "❌ 激活码激活异常: $errorMessage", e)
                Log.e(TAG, "   详细错误: ${e.javaClass.simpleName}: ${e.message}")
                Result.failure(IOException(errorMessage, e))
            }
        }
    }
    
    /**
     * 保存激活状态（加密存储）
     */
    fun saveActivationStatus(activated: Boolean, data: Map<String, String?> = emptyMap()) {
        val mac = data["mac"] ?: getMacAddress() ?: ""
        val status = JSONObject().apply {
            put("activated", activated)
            put("mac", mac)
            put("licenseKey", data["licenseKey"] ?: JSONObject.NULL)
            put("activatedAt", data["activatedAt"] ?: SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date()))
            put("expiresAt", data["expiresAt"] ?: JSONObject.NULL)
            put("lastVerifiedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date()))
        }
        
        try {
            // 使用加密存储
            val encrypted = secureStorage.encrypt(status.toString())
            prefs.edit().putString(STORAGE_KEY, encrypted).apply()
            Log.d(TAG, "✅ 激活状态已加密保存到本地")
            Log.d(TAG, "  - 激活状态: $activated")
            Log.d(TAG, "  - MAC地址: $mac")
            Log.d(TAG, "  - 激活时间: ${status.optString("activatedAt")}")
            Log.d(TAG, "  - 过期时间: ${status.optString("expiresAt")}")
            Log.d(TAG, "  - 最后验证: ${status.optString("lastVerifiedAt")}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 保存激活状态失败（加密错误）", e)
            // 降级：明文存储（向后兼容）
            prefs.edit().putString(STORAGE_KEY, status.toString()).apply()
            Log.w(TAG, "⚠️ 降级为明文存储")
        }
    }
    
    /**
     * 获取激活状态（支持加密存储）
     */
    fun getActivationStatus(): JSONObject? {
        val stored = prefs.getString(STORAGE_KEY, null) ?: return null
        return try {
            // 尝试解密读取
            try {
                val decrypted = secureStorage.decrypt(stored)
                JSONObject(decrypted)
            } catch (e: Exception) {
                // 可能是旧版本的明文数据，尝试直接解析
                Log.d(TAG, "尝试作为明文数据读取...")
                JSONObject(stored)
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取激活状态失败", e)
            null
        }
    }
    
    /**
     * 检查是否已激活
     */
    fun isActivated(): Boolean {
        Log.d(TAG, "检查本地激活状态...")
        val status = getActivationStatus()
        if (status == null) {
            Log.d(TAG, "  - 未找到本地激活状态")
            return false
        }
        
        val activated = status.optBoolean("activated", false)
        Log.d(TAG, "  - 激活标志: $activated")
        
        if (!activated) {
            Log.d(TAG, "  - 设备未激活")
            return false
        }
        
        // 检查是否过期
        val expiresAtStr = status.optString("expiresAt", "").takeIf { it.isNotEmpty() && it != "null" }
        if (expiresAtStr != null) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                val expiresAt = sdf.parse(expiresAtStr)
                if (expiresAt != null && Date() > expiresAt) {
                    Log.w(TAG, "  - ⚠️ 授权已过期 (过期时间: $expiresAtStr)")
                    return false
                } else {
                    Log.d(TAG, "  - 授权未过期 (过期时间: $expiresAtStr)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "  - ❌ 解析过期时间失败", e)
            }
        } else {
            Log.d(TAG, "  - 无过期时间限制")
        }
        
        Log.d(TAG, "✅ 设备已激活（本地验证）")
        return true
    }
    
    /**
     * 应用启动时验证
     */
    suspend fun checkOnStartup(): ActivationResult {
        Log.d(TAG, "═══════════════════════════════════════════════════════════════")
        Log.d(TAG, "开始应用启动时激活验证")
        Log.d(TAG, "═══════════════════════════════════════════════════════════════")
        
        // 1. 检查本地激活状态
        Log.d(TAG, "[步骤1] 检查本地激活状态...")
        if (isActivated()) {
            Log.d(TAG, "✅ 应用已激活（从本地缓存），跳过网络验证")
            Log.d(TAG, "═══════════════════════════════════════════════════════════════")
            return ActivationResult(activated = true, fromCache = true)
        }
        
        // 2. 获取MAC地址
        Log.d(TAG, "[步骤2] 获取MAC地址...")
        val mac = getMacAddress()
        if (mac.isNullOrEmpty()) {
            Log.e(TAG, "❌ 无法获取MAC地址，验证终止")
            Log.d(TAG, "═══════════════════════════════════════════════════════════════")
            return ActivationResult(activated = false, error = "无法获取MAC地址")
        }
        
        // 3. 通过MAC地址验证
        Log.d(TAG, "[步骤3] 通过MAC地址验证...")
        return verifyByMac(mac).fold(
            onSuccess = { data ->
                val valid = data.optBoolean("valid", false)
                if (valid) {
                    Log.d(TAG, "✅ MAC地址验证成功")
                    val licenseKey = data.optString("licenseKey", "").takeIf { it.isNotEmpty() && it != "null" }
                    val activatedAt = data.optString("activatedAt", "").takeIf { it.isNotEmpty() && it != "null" }
                    val expiresAt = data.optString("expiresAt", "").takeIf { it.isNotEmpty() && it != "null" }
                    val fromWhitelist = data.optBoolean("fromWhitelist", false)
                    
                    Log.d(TAG, "  - 授权码: ${licenseKey?.take(8) ?: "无"}...")
                    Log.d(TAG, "  - 激活时间: $activatedAt")
                    Log.d(TAG, "  - 过期时间: $expiresAt")
                    Log.d(TAG, "  - 来源: ${if (fromWhitelist) "白名单" else "激活码"}")
                    
                    saveActivationStatus(true, mapOf(
                        "mac" to mac,
                        "licenseKey" to licenseKey,
                        "activatedAt" to activatedAt,
                        "expiresAt" to expiresAt
                    ))
                    Log.d(TAG, "═══════════════════════════════════════════════════════════════")
                    ActivationResult(activated = true, fromMac = true)
                } else {
                    val message = data.optString("message", "未知原因")
                    Log.w(TAG, "⚠️ MAC地址未在激活名单中: $message")
                    Log.d(TAG, "  - 需要用户输入激活码")
                    Log.d(TAG, "═══════════════════════════════════════════════════════════════")
                    ActivationResult(activated = false, needLicense = true, message = message)
                }
            },
            onFailure = { error ->
                Log.e(TAG, "❌ MAC地址验证失败: ${error.message}", error)
                Log.d(TAG, "═══════════════════════════════════════════════════════════════")
                ActivationResult(activated = false, error = error.message)
            }
        )
    }
    
    /**
     * 使用激活码激活
     */
    suspend fun activate(licenseKey: String, userInfo: Map<String, String>? = null): ActivationResult {
        Log.d(TAG, "═══════════════════════════════════════════════════════════════")
        Log.d(TAG, "开始激活码激活流程")
        Log.d(TAG, "═══════════════════════════════════════════════════════════════")
        
        Log.d(TAG, "[步骤1] 获取MAC地址...")
        val mac = getMacAddress()
        if (mac.isNullOrEmpty()) {
            Log.e(TAG, "❌ 无法获取MAC地址，激活终止")
            Log.d(TAG, "═══════════════════════════════════════════════════════════════")
            return ActivationResult(success = false, message = "无法获取MAC地址")
        }
        
        Log.d(TAG, "[步骤2] 调用激活API...")
        return activateByLicense(licenseKey, mac, userInfo).fold(
            onSuccess = { data ->
                val success = data.optBoolean("success", false)
                if (success) {
                    Log.d(TAG, "✅ 激活码激活成功")
                    val licenseKeyFromResponse = data.optJSONObject("license")?.optString("licenseKey")
                    val activatedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
                    
                    Log.d(TAG, "  - 授权码: ${licenseKeyFromResponse?.take(8) ?: "无"}...")
                    Log.d(TAG, "  - 激活时间: $activatedAt")
                    
                    saveActivationStatus(true, mapOf(
                        "mac" to mac,
                        "licenseKey" to licenseKeyFromResponse,
                        "activatedAt" to activatedAt
                    ))
                    Log.d(TAG, "═══════════════════════════════════════════════════════════════")
                    ActivationResult(success = true)
                } else {
                    val message = data.optString("message", "激活失败")
                    Log.e(TAG, "❌ 激活失败: $message")
                    Log.d(TAG, "═══════════════════════════════════════════════════════════════")
                    ActivationResult(success = false, message = message)
                }
            },
            onFailure = { error ->
                Log.e(TAG, "❌ 激活异常: ${error.message}", error)
                Log.d(TAG, "═══════════════════════════════════════════════════════════════")
                ActivationResult(success = false, message = error.message)
            }
        )
    }
    
    /**
     * 定期验证（24小时一次）
     */
    suspend fun verifyPeriodically(): ActivationResult {
        val now = System.currentTimeMillis()
        val lastVerified = prefs.getLong("last_verification_time", 0)
        
        // 如果距离上次验证不足24小时，跳过
        if (now - lastVerified < VERIFICATION_INTERVAL) {
            val remainingHours = (VERIFICATION_INTERVAL - (now - lastVerified)) / (60 * 60 * 1000)
            Log.d(TAG, "距离上次验证不足24小时（剩余${remainingHours}小时），跳过定期验证")
            return ActivationResult(activated = true, fromCache = true)
        }
        
        Log.d(TAG, "开始定期验证（距上次验证已超过24小时）...")
        val result = checkOnStartup()
        
        if (result.activated) {
            prefs.edit().putLong("last_verification_time", now).apply()
            Log.d(TAG, "✅ 定期验证成功，更新验证时间")
        } else {
            Log.w(TAG, "⚠️ 定期验证失败")
        }
        
        return result
    }
    
    /**
     * 获取设备指纹
     */
    fun getDeviceFingerprint(): DeviceFingerprint {
        return DeviceFingerprint.generate(context, getMacAddress())
    }
    
    /**
     * 清除激活状态（用于测试或重置）
     */
    fun clearActivation() {
        prefs.edit().clear().apply()
        Log.d(TAG, "✅ 激活状态已清除")
    }
    
    /**
     * 获取激活信息摘要（用于UI显示）
     */
    fun getActivationSummary(): ActivationSummary {
        val status = getActivationStatus()
        if (status == null) {
            return ActivationSummary(
                isActivated = false,
                message = "设备未激活"
            )
        }
        
        val activated = status.optBoolean("activated", false)
        val mac = status.optString("mac", "未知")
        val licenseKey = status.optString("licenseKey", "").takeIf { it.isNotEmpty() && it != "null" }
        val activatedAt = status.optString("activatedAt", "").takeIf { it.isNotEmpty() && it != "null" }
        val expiresAt = status.optString("expiresAt", "").takeIf { it.isNotEmpty() && it != "null" }
        val lastVerifiedAt = status.optString("lastVerifiedAt", "").takeIf { it.isNotEmpty() && it != "null" }
        
        return ActivationSummary(
            isActivated = activated,
            macAddress = mac,
            licenseKey = licenseKey,
            activatedAt = activatedAt,
            expiresAt = expiresAt,
            lastVerifiedAt = lastVerifiedAt,
            message = if (activated) "设备已激活" else "设备未激活"
        )
    }
}

/**
 * 激活结果数据类
 */
data class ActivationResult(
    val activated: Boolean = false,
    val success: Boolean = false,
    val fromCache: Boolean = false,
    val fromMac: Boolean = false,
    val needLicense: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

/**
 * 激活信息摘要（用于UI显示）
 */
data class ActivationSummary(
    val isActivated: Boolean,
    val macAddress: String? = null,
    val licenseKey: String? = null,
    val activatedAt: String? = null,
    val expiresAt: String? = null,
    val lastVerifiedAt: String? = null,
    val message: String
)
