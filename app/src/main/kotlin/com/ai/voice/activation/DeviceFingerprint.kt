package com.ai.voice.activation

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * 设备指纹收集器
 * 
 * 用于生成唯一的设备标识,支持激活码生成流程
 * 
 * ⚠️ 注意: 这是一个独立的模块,用于设备激活功能
 * 如果不需要激活功能,可以直接删除整个 activation package
 * 
 * 参考: py-xiaozhi-main/src/utils/device_fingerprint.py
 */
object DeviceFingerprint {
    private const val TAG = "🔐[Activation]"
    private const val EFUSE_FILE_NAME = "activation_efuse.json"
    
    // 缓存的设备信息
    @Volatile
    private var cachedEfuseData: JSONObject? = null
    
    /**
     * 获取设备指纹文件路径
     */
    private fun getEfuseFile(context: Context): File {
        return File(context.filesDir, EFUSE_FILE_NAME)
    }
    
    /**
     * 初始化设备身份信息
     * 确保 efuse 文件存在且完整
     */
    fun initialize(context: Context) {
        Log.d(TAG, "初始化设备指纹...")
        
        val efuseFile = getEfuseFile(context)
        
        if (!efuseFile.exists()) {
            Log.i(TAG, "efuse 文件不存在,创建新文件")
            createNewEfuseFile(context)
        } else {
            Log.i(TAG, "efuse 文件已存在,验证完整性")
            validateAndFixEfuseFile(context)
        }
        
        // 打印设备信息
        val (serialNumber, hmacKey, isActivated) = getDeviceIdentity(context)
        Log.i(TAG, "═══════════════════════════════════════")
        Log.i(TAG, "📱 设备身份信息:")
        Log.i(TAG, "   序列号: $serialNumber")
        Log.i(TAG, "   HMAC密钥: ${hmacKey?.take(16)}...") // 只显示前16位
        Log.i(TAG, "   激活状态: ${if (isActivated) "已激活" else "未激活"}")
        Log.i(TAG, "   MAC地址: ${getMacAddress(context)}")
        Log.i(TAG, "   Android ID: ${getAndroidId(context)}")
        Log.i(TAG, "═══════════════════════════════════════")
    }
    
    /**
     * 创建新的 efuse 文件
     */
    private fun createNewEfuseFile(context: Context) {
        val macAddress = getMacAddress(context)
        val serialNumber = generateSerialNumber(context)
        val hmacKey = generateHardwareHash(context)
        
        val efuseData = JSONObject().apply {
            put("mac_address", macAddress)
            put("serial_number", serialNumber)
            put("hmac_key", hmacKey)
            put("activation_status", false)
            put("device_fingerprint", generateDeviceFingerprint(context))
        }
        
        saveEfuseData(context, efuseData)
        Log.i(TAG, "✅ 已创建 efuse 配置文件")
    }
    
    /**
     * 验证并修复 efuse 文件
     */
    private fun validateAndFixEfuseFile(context: Context) {
        try {
            val efuseData = loadEfuseData(context)
            
            val requiredFields = listOf(
                "mac_address",
                "serial_number", 
                "hmac_key",
                "activation_status",
                "device_fingerprint"
            )
            
            val missingFields = requiredFields.filter { !efuseData.has(it) }
            
            if (missingFields.isNotEmpty()) {
                Log.w(TAG, "efuse 配置文件缺少字段: $missingFields")
                fixMissingFields(context, efuseData, missingFields)
            } else {
                Log.d(TAG, "✅ efuse 配置文件完整性检查通过")
            }
        } catch (e: Exception) {
            Log.e(TAG, "验证 efuse 配置文件失败,重新创建: ${e.message}")
            createNewEfuseFile(context)
        }
    }
    
    /**
     * 修复缺失的字段
     */
    private fun fixMissingFields(
        context: Context,
        efuseData: JSONObject,
        missingFields: List<String>
    ) {
        missingFields.forEach { field ->
            when (field) {
                "mac_address" -> efuseData.put(field, getMacAddress(context))
                "serial_number" -> efuseData.put(field, generateSerialNumber(context))
                "hmac_key" -> efuseData.put(field, generateHardwareHash(context))
                "activation_status" -> efuseData.put(field, false)
                "device_fingerprint" -> efuseData.put(field, generateDeviceFingerprint(context))
            }
        }
        
        saveEfuseData(context, efuseData)
        Log.i(TAG, "✅ 已修复 efuse 配置文件")
    }
    
    /**
     * 生成设备指纹
     */
    private fun generateDeviceFingerprint(context: Context): JSONObject {
        return JSONObject().apply {
            put("system", "Android")
            put("version", Build.VERSION.RELEASE)
            put("sdk_int", Build.VERSION.SDK_INT)
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("device", Build.DEVICE)
            put("hostname", Build.HOST)
            put("mac_address", getMacAddress(context))
            put("android_id", getAndroidId(context))
        }
    }
    
    /**
     * 获取 MAC 地址
     * 
     * 注意: Android 6.0+ 无法获取真实 MAC 地址,返回 Android ID 的变体
     */
    private fun getMacAddress(context: Context): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6.0+ 使用 Android ID 生成伪 MAC 地址
                val androidId = getAndroidId(context)
                generateMacFromAndroidId(androidId)
            } else {
                // Android 6.0 以下可以获取真实 MAC 地址
                @Suppress("DEPRECATION")
                val wifiManager = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val wifiInfo = wifiManager.connectionInfo
                @Suppress("DEPRECATION")
                wifiInfo.macAddress ?: generateMacFromAndroidId(getAndroidId(context))
            }
        } catch (e: Exception) {
            Log.w(TAG, "获取 MAC 地址失败: ${e.message}")
            generateMacFromAndroidId(getAndroidId(context))
        }
    }
    
    /**
     * 从 Android ID 生成伪 MAC 地址
     */
    private fun generateMacFromAndroidId(androidId: String): String {
        val md5 = MessageDigest.getInstance("MD5")
        val hash = md5.digest(androidId.toByteArray())
        
        // 取前6个字节生成 MAC 地址格式
        return hash.take(6).joinToString(":") { 
            "%02x".format(it) 
        }
    }
    
    /**
     * 获取 Android ID
     */
    private fun getAndroidId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: UUID.randomUUID().toString()
    }
    
    /**
     * 生成序列号
     */
    private fun generateSerialNumber(context: Context): String {
        val macAddress = getMacAddress(context)
        val macClean = macAddress.replace(":", "")
        
        val md5 = MessageDigest.getInstance("MD5")
        val hash = md5.digest(macClean.toByteArray())
        val shortHash = hash.take(4).joinToString("") { 
            "%02X".format(it) 
        }
        
        return "SN-$shortHash-$macClean"
    }
    
    /**
     * 生成硬件哈希 (用作 HMAC 密钥)
     */
    private fun generateHardwareHash(context: Context): String {
        val fingerprint = generateDeviceFingerprint(context)
        
        val identifiers = mutableListOf<String>()
        
        // 收集硬件标识符
        fingerprint.optString("hostname").takeIf { it.isNotEmpty() }?.let { identifiers.add(it) }
        fingerprint.optString("mac_address").takeIf { it.isNotEmpty() }?.let { identifiers.add(it) }
        fingerprint.optString("android_id").takeIf { it.isNotEmpty() }?.let { identifiers.add(it) }
        fingerprint.optString("model").takeIf { it.isNotEmpty() }?.let { identifiers.add(it) }
        
        // 如果没有任何标识符,使用 Android ID
        if (identifiers.isEmpty()) {
            identifiers.add(getAndroidId(context))
        }
        
        val fingerprintStr = identifiers.joinToString("||")
        
        val sha256 = MessageDigest.getInstance("SHA-256")
        val hash = sha256.digest(fingerprintStr.toByteArray())
        
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * 加载 efuse 数据
     */
    private fun loadEfuseData(context: Context): JSONObject {
        // 如果有缓存,直接返回
        cachedEfuseData?.let { return it }
        
        val efuseFile = getEfuseFile(context)
        
        if (!efuseFile.exists()) {
            return JSONObject()
        }
        
        return try {
            val data = JSONObject(efuseFile.readText())
            cachedEfuseData = data
            data
        } catch (e: Exception) {
            Log.e(TAG, "加载 efuse 数据失败: ${e.message}")
            JSONObject()
        }
    }
    
    /**
     * 保存 efuse 数据
     */
    private fun saveEfuseData(context: Context, data: JSONObject): Boolean {
        return try {
            val efuseFile = getEfuseFile(context)
            efuseFile.writeText(data.toString(2)) // 格式化 JSON
            cachedEfuseData = data
            Log.d(TAG, "✅ efuse 数据已保存")
            true
        } catch (e: Exception) {
            Log.e(TAG, "保存 efuse 数据失败: ${e.message}")
            false
        }
    }
    
    /**
     * 获取设备身份信息
     * 
     * @return Triple(序列号, HMAC密钥, 激活状态)
     */
    fun getDeviceIdentity(context: Context): Triple<String?, String?, Boolean> {
        val efuseData = loadEfuseData(context)
        
        val serialNumber = if (efuseData.has("serial_number")) {
            efuseData.getString("serial_number")
        } else null
        
        val hmacKey = if (efuseData.has("hmac_key")) {
            efuseData.getString("hmac_key")
        } else null
        
        return Triple(
            serialNumber,
            hmacKey,
            efuseData.optBoolean("activation_status", false)
        )
    }
    
    /**
     * 获取序列号
     */
    fun getSerialNumber(context: Context): String? {
        val efuseData = loadEfuseData(context)
        return if (efuseData.has("serial_number")) {
            efuseData.getString("serial_number")
        } else null
    }
    
    /**
     * 获取 HMAC 密钥
     */
    fun getHmacKey(context: Context): String? {
        val efuseData = loadEfuseData(context)
        return if (efuseData.has("hmac_key")) {
            efuseData.getString("hmac_key")
        } else null
    }
    
    /**
     * 设置激活状态
     */
    fun setActivationStatus(context: Context, status: Boolean): Boolean {
        val efuseData = loadEfuseData(context)
        efuseData.put("activation_status", status)
        
        return if (saveEfuseData(context, efuseData)) {
            Log.i(TAG, "✅ 激活状态已更新: ${if (status) "已激活" else "未激活"}")
            true
        } else {
            false
        }
    }
    
    /**
     * 检查设备是否已激活
     */
    fun isActivated(context: Context): Boolean {
        return loadEfuseData(context).optBoolean("activation_status", false)
    }
    
    /**
     * 清除激活数据 (用于调试)
     */
    fun clearActivationData(context: Context): Boolean {
        val efuseFile = getEfuseFile(context)
        return try {
            if (efuseFile.exists()) {
                efuseFile.delete()
                cachedEfuseData = null
                Log.i(TAG, "✅ 激活数据已清除")
                true
            } else {
                Log.w(TAG, "激活数据文件不存在")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "清除激活数据失败: ${e.message}")
            false
        }
    }
}

