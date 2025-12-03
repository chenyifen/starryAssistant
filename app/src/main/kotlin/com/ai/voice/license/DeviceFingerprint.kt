package com.ai.voice.license

import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.security.MessageDigest

/**
 * 设备指纹 - 多重设备标识，提高设备识别可靠性
 */
data class DeviceFingerprint(
    val mac: String?,
    val androidId: String,
    val serialNumber: String?,
    val buildFingerprint: String,
    val deviceModel: String,
    val deviceManufacturer: String,
    val sdkVersion: Int,
    val deviceHash: String  // 组合哈希值
) {
    
    companion object {
        private const val TAG = "DeviceFingerprint"
        
        /**
         * 生成设备指纹
         */
        fun generate(context: Context, macAddress: String?): DeviceFingerprint {
            Log.d(TAG, "开始生成设备指纹...")
            
            // Android ID
            val androidId = try {
                android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ) ?: "UNKNOWN"
            } catch (e: Exception) {
                Log.w(TAG, "获取Android ID失败: ${e.message}")
                "UNKNOWN"
            }
            
            // 序列号
            val serialNumber = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Build.getSerial()
                } else {
                    @Suppress("DEPRECATION")
                    Build.SERIAL
                }
            } catch (e: Exception) {
                Log.d(TAG, "获取序列号失败: ${e.message}")
                null
            }
            
            // 构建指纹
            val buildFingerprint = Build.FINGERPRINT
            val deviceModel = Build.MODEL
            val deviceManufacturer = Build.MANUFACTURER
            val sdkVersion = Build.VERSION.SDK_INT
            
            // 生成组合哈希
            val deviceHash = generateHash(
                macAddress,
                androidId,
                serialNumber,
                buildFingerprint,
                deviceModel,
                deviceManufacturer
            )
            
            val fingerprint = DeviceFingerprint(
                mac = macAddress,
                androidId = androidId,
                serialNumber = serialNumber,
                buildFingerprint = buildFingerprint,
                deviceModel = deviceModel,
                deviceManufacturer = deviceManufacturer,
                sdkVersion = sdkVersion,
                deviceHash = deviceHash
            )
            
            Log.d(TAG, "✅ 设备指纹生成完成")
            Log.d(TAG, "  - MAC: ${macAddress?.take(17)}")
            Log.d(TAG, "  - Android ID: ${androidId.take(8)}...")
            Log.d(TAG, "  - 序列号: ${serialNumber?.take(8) ?: "无"}...")
            Log.d(TAG, "  - 设备型号: $deviceModel")
            Log.d(TAG, "  - 制造商: $deviceManufacturer")
            Log.d(TAG, "  - SDK版本: $sdkVersion")
            Log.d(TAG, "  - 设备哈希: ${deviceHash.take(16)}...")
            
            return fingerprint
        }
        
        /**
         * 生成设备组合哈希（SHA-256）
         */
        private fun generateHash(vararg components: String?): String {
            val combined = components.filterNotNull().joinToString("|")
            return sha256(combined)
        }
        
        /**
         * SHA-256哈希
         */
        private fun sha256(input: String): String {
            return try {
                val digest = MessageDigest.getInstance("SHA-256")
                val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
                hash.joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                Log.w(TAG, "SHA-256哈希失败: ${e.message}")
                "HASH_ERROR"
            }
        }
    }
    
    /**
     * 转换为JSON对象（用于API请求）
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("mac", mac ?: JSONObject.NULL)
            put("androidId", androidId)
            put("serialNumber", serialNumber ?: JSONObject.NULL)
            put("buildFingerprint", buildFingerprint)
            put("deviceModel", deviceModel)
            put("deviceManufacturer", deviceManufacturer)
            put("sdkVersion", sdkVersion)
            put("deviceHash", deviceHash)
        }
    }
    
    /**
     * 获取简要信息（用于日志）
     */
    fun toSummary(): String {
        return "$deviceManufacturer $deviceModel (Android $sdkVersion)"
    }
}

