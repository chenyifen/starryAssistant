package org.stypox.dicio.activation

import android.content.Context
import android.util.Log
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 激活码生成器
 * 
 * 基于 HMAC-SHA256 算法生成设备激活签名
 * 
 * ⚠️ 注意: 这是一个独立的模块,用于设备激活功能
 * 如果不需要激活功能,可以直接删除整个 activation package
 * 
 * 参考: py-xiaozhi-main/src/utils/device_activator.py
 */
object ActivationCodeGenerator {
    private const val TAG = "🔐[Activation]"
    private const val HMAC_ALGORITHM = "HmacSHA256"
    
    /**
     * 生成 HMAC-SHA256 签名
     * 
     * @param context Android Context
     * @param challenge 服务器发送的 challenge 字符串
     * @return HMAC 签名 (十六进制字符串),如果失败返回 null
     */
    fun generateHmacSignature(context: Context, challenge: String): String? {
        if (challenge.isEmpty()) {
            Log.e(TAG, "❌ Challenge 字符串不能为空")
            return null
        }
        
        val hmacKey = DeviceFingerprint.getHmacKey(context)
        
        if (hmacKey == null) {
            Log.e(TAG, "❌ 未找到 HMAC 密钥,无法生成签名")
            return null
        }
        
        return try {
            // 创建 HMAC-SHA256 实例
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            val secretKey = SecretKeySpec(hmacKey.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM)
            mac.init(secretKey)
            
            // 计算签名
            val signatureBytes = mac.doFinal(challenge.toByteArray(Charsets.UTF_8))
            
            // 转换为十六进制字符串
            val signature = signatureBytes.joinToString("") { "%02x".format(it) }
            
            Log.d(TAG, "✅ HMAC 签名生成成功")
            Log.d(TAG, "   Challenge: ${challenge.take(32)}...")
            Log.d(TAG, "   Signature: ${signature.take(32)}...")
            
            signature
        } catch (e: Exception) {
            Log.e(TAG, "❌ 生成 HMAC 签名失败: ${e.message}", e)
            null
        }
    }
    
    /**
     * 构建激活请求 Payload
     * 
     * @param context Android Context  
     * @param challenge 服务器发送的 challenge 字符串
     * @return 激活请求 Payload (JSON 字符串),如果失败返回 null
     */
    fun buildActivationPayload(context: Context, challenge: String): String? {
        val serialNumber = DeviceFingerprint.getSerialNumber(context)
        
        if (serialNumber == null) {
            Log.e(TAG, "❌ 设备没有序列号,无法构建激活请求")
            return null
        }
        
        val hmacSignature = generateHmacSignature(context, challenge)
        
        if (hmacSignature == null) {
            Log.e(TAG, "❌ 无法生成 HMAC 签名,激活请求构建失败")
            return null
        }
        
        // 构建激活请求 JSON
        val payload = org.json.JSONObject().apply {
            put("Payload", org.json.JSONObject().apply {
                put("algorithm", "hmac-sha256")
                put("serial_number", serialNumber)
                put("challenge", challenge)
                put("hmac", hmacSignature)
            })
        }
        
        Log.d(TAG, "✅ 激活请求 Payload 已构建")
        Log.d(TAG, payload.toString(2))
        
        return payload.toString()
    }
    
    /**
     * 打印激活验证码信息
     * 
     * @param code 6位数字验证码
     * @param message 激活提示信息
     */
    fun printActivationCode(code: String, message: String = "请在控制面板输入验证码") {
        Log.i(TAG, "╔════════════════════════════════════════════════╗")
        Log.i(TAG, "║         🔐 设备激活 - 验证码                    ║")
        Log.i(TAG, "╠════════════════════════════════════════════════╣")
        Log.i(TAG, "║                                                ║")
        Log.i(TAG, "║   验证码: ${code.chunked(1).joinToString(" ")}                              ║")
        Log.i(TAG, "║                                                ║")
        Log.i(TAG, "║   $message                                     ║")
        Log.i(TAG, "║                                                ║")
        Log.i(TAG, "╚════════════════════════════════════════════════╝")
    }
    
    /**
     * 打印激活请求信息
     * 
     * @param context Android Context
     * @param activationUrl 激活请求 URL
     * @param challenge 服务器 challenge
     * @param code 验证码
     */
    fun printActivationRequestInfo(
        context: Context,
        activationUrl: String,
        challenge: String,
        code: String
    ) {
        val serialNumber = DeviceFingerprint.getSerialNumber(context)
        val hmacKey = DeviceFingerprint.getHmacKey(context)
        val hmacSignature = generateHmacSignature(context, challenge)
        
        Log.i(TAG, "")
        Log.i(TAG, "═══════════════════════════════════════════════════════════════")
        Log.i(TAG, "🔐 设备激活请求详情")
        Log.i(TAG, "═══════════════════════════════════════════════════════════════")
        Log.i(TAG, "")
        Log.i(TAG, "📋 设备信息:")
        Log.i(TAG, "   序列号: $serialNumber")
        Log.i(TAG, "   HMAC密钥: ${hmacKey?.take(16)}...")
        Log.i(TAG, "")
        Log.i(TAG, "🌐 服务器信息:")
        Log.i(TAG, "   激活 URL: $activationUrl")
        Log.i(TAG, "   Challenge: ${challenge.take(32)}...")
        Log.i(TAG, "")
        Log.i(TAG, "🔑 验证信息:")
        Log.i(TAG, "   验证码: $code")
        Log.i(TAG, "   HMAC 签名: ${hmacSignature?.take(32)}...")
        Log.i(TAG, "")
        Log.i(TAG, "═══════════════════════════════════════════════════════════════")
        Log.i(TAG, "")
    }
    
    /**
     * 验证 HMAC 签名 (用于调试)
     * 
     * @param context Android Context
     * @param challenge 服务器 challenge
     * @param expectedSignature 期望的签名
     * @return 签名是否匹配
     */
    fun verifyHmacSignature(
        context: Context,
        challenge: String,
        expectedSignature: String
    ): Boolean {
        val actualSignature = generateHmacSignature(context, challenge)
        
        if (actualSignature == null) {
            Log.e(TAG, "❌ 无法生成签名进行验证")
            return false
        }
        
        val isMatch = actualSignature.equals(expectedSignature, ignoreCase = true)
        
        if (isMatch) {
            Log.i(TAG, "✅ HMAC 签名验证通过")
        } else {
            Log.e(TAG, "❌ HMAC 签名验证失败")
            Log.e(TAG, "   期望: $expectedSignature")
            Log.e(TAG, "   实际: $actualSignature")
        }
        
        return isMatch
    }
}


