package com.ai.voice.activation

import android.content.Context
import android.util.Log

/**
 * 激活管理器 - 统一的激活流程入口
 * 
 * ⚠️ 注意: 这是一个独立的模块,用于设备激活功能
 * 如果不需要激活功能,可以直接删除整个 activation package
 * 
 * 使用示例:
 * ```kotlin
 * // 1. 初始化设备身份
 * ActivationManager.initialize(context)
 * 
 * // 2. 处理激活响应 (当服务器返回需要激活时)
 * if (serverResponse.has("activation")) {
 *     val activationData = serverResponse.getJSONObject("activation")
 *     val code = activationData.getString("code")
 *     val challenge = activationData.getString("challenge")
 *     
 *     ActivationManager.handleActivationResponse(context, code, challenge)
 * }
 * 
 * // 3. 构建激活请求
 * val payload = ActivationManager.buildActivationRequest(context, challenge)
 * 
 * // 4. 标记为已激活
 * ActivationManager.markAsActivated(context)
 * ```
 */
object ActivationManager {
    private const val TAG = "🔐[Activation]"
    
    /**
     * 初始化激活模块
     * 
     * 应在应用启动时调用,用于:
     * - 生成或加载设备身份信息
     * - 打印设备信息到 Log
     */
    fun initialize(context: Context) {
        Log.d(TAG, "初始化激活模块...")
        DeviceFingerprint.initialize(context)
    }
    
    /**
     * 处理激活响应
     * 
     * 当服务器返回激活数据时调用,自动打印激活码和设备信息
     * 
     * @param context Android Context
     * @param code 6位数字验证码
     * @param challenge 服务器 challenge 字符串
     * @param message 激活提示信息 (可选)
     */
    fun handleActivationResponse(
        context: Context,
        code: String,
        challenge: String,
        message: String = "请在控制面板输入验证码"
    ) {
        Log.i(TAG, "收到激活响应,开始处理...")
        
        // 打印激活验证码
        ActivationCodeGenerator.printActivationCode(code, message)
        
        // 打印完整的激活请求信息
        // 注意: activationUrl 需要从 WebSocket 配置中获取
        val activationUrl = "服务器激活 URL" // 占位符,实际使用时需要从配置获取
        ActivationCodeGenerator.printActivationRequestInfo(
            context,
            activationUrl,
            challenge,
            code
        )
    }
    
    /**
     * 构建激活请求 Payload
     * 
     * @param context Android Context
     * @param challenge 服务器 challenge 字符串
     * @return 激活请求 JSON Payload,如果失败返回 null
     */
    fun buildActivationRequest(context: Context, challenge: String): String? {
        Log.d(TAG, "构建激活请求...")
        return ActivationCodeGenerator.buildActivationPayload(context, challenge)
    }
    
    /**
     * 生成 HMAC 签名
     * 
     * @param context Android Context
     * @param challenge 服务器 challenge 字符串
     * @return HMAC 签名,如果失败返回 null
     */
    fun generateHmacSignature(context: Context, challenge: String): String? {
        return ActivationCodeGenerator.generateHmacSignature(context, challenge)
    }
    
    /**
     * 获取设备序列号
     */
    fun getSerialNumber(context: Context): String? {
        return DeviceFingerprint.getSerialNumber(context)
    }
    
    /**
     * 检查设备是否已激活
     */
    fun isActivated(context: Context): Boolean {
        return DeviceFingerprint.isActivated(context)
    }
    
    /**
     * 标记设备为已激活
     * 
     * 当收到服务器激活成功响应 (HTTP 200) 时调用
     */
    fun markAsActivated(context: Context): Boolean {
        Log.i(TAG, "🎉 设备激活成功!")
        return DeviceFingerprint.setActivationStatus(context, true)
    }
    
    /**
     * 重置激活状态 (用于调试/测试)
     * 
     * ⚠️ 警告: 这会清除所有激活数据,仅用于开发/测试
     */
    fun resetActivation(context: Context): Boolean {
        Log.w(TAG, "⚠️ 重置激活状态...")
        return DeviceFingerprint.clearActivationData(context)
    }
    
    /**
     * 打印设备身份信息 (用于调试)
     */
    fun printDeviceInfo(context: Context) {
        val (serialNumber, hmacKey, isActivated) = DeviceFingerprint.getDeviceIdentity(context)
        
        Log.i(TAG, "")
        Log.i(TAG, "═══════════════════════════════════════")
        Log.i(TAG, "📱 设备身份信息")
        Log.i(TAG, "═══════════════════════════════════════")
        Log.i(TAG, "序列号: $serialNumber")
        Log.i(TAG, "HMAC密钥: ${hmacKey?.take(16)}...")
        Log.i(TAG, "激活状态: ${if (isActivated) "✅ 已激活" else "❌ 未激活"}")
        Log.i(TAG, "═══════════════════════════════════════")
        Log.i(TAG, "")
    }
}


