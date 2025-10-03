package org.stypox.dicio

import android.Manifest
import android.app.Application
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.HiltAndroidApp
import org.stypox.dicio.activation.ActivationManager
import org.stypox.dicio.activation.ActivationCodeGenerator
import org.stypox.dicio.util.checkPermissions
import org.stypox.dicio.util.WebSocketConfig

// IMPORTANT NOTE: beware of this nasty bug related to allowBackup=true
// https://medium.com/p/924c91bafcac
@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 初始化激活模块 (独立的功能模块)
        // ⚠️ 注意: 这是一个可选的模块,如果不需要可以删除整个 activation package
        ActivationManager.initialize(this)
        
        // 打印配置信息（用于调试）
        WebSocketConfig.printConfigInfo(this)
        
        // 检查设备授权状态
        checkDeviceAuthorization()
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkPermissions(this, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            initNotificationChannels()
        }
    }

    /**
     * 检查设备授权状态
     * 如果设备未激活，生成并打印激活码
     */
    private fun checkDeviceAuthorization() {
        val isActivated = ActivationManager.isActivated(this)
        
        Log.i(TAG, "")
        Log.i(TAG, "═══════════════════════════════════════════════════════════════")
        Log.i(TAG, "🔐 设备授权检查")
        Log.i(TAG, "═══════════════════════════════════════════════════════════════")
        
        if (isActivated) {
            Log.i(TAG, "✅ 设备已激活，可以正常使用所有功能")
            ActivationManager.printDeviceInfo(this)
        } else {
            Log.w(TAG, "⚠️ 设备未激活，需要进行设备激活")
            
            // 打印设备信息
            ActivationManager.printDeviceInfo(this)
            
            // 生成激活码 (模拟服务器响应)
            generateActivationCode()
        }
        
        Log.i(TAG, "═══════════════════════════════════════════════════════════════")
        Log.i(TAG, "")
    }
    
    /**
     * 生成激活码 (模拟服务器响应)
     * 在实际使用中，这些信息应该从服务器获取
     */
    private fun generateActivationCode() {
        try {
            // 生成模拟的激活码和 challenge
            val activationCode = generateRandomCode()
            val challenge = generateRandomChallenge()
            
            Log.i(TAG, "")
            Log.i(TAG, "🔑 正在生成设备激活码...")
            
            // 打印激活码
            ActivationCodeGenerator.printActivationCode(
                code = activationCode,
                message = "请在服务器控制面板输入此验证码完成设备激活"
            )
            
            // 获取激活 URL
            val activationUrl = WebSocketConfig.getActivationUrl(this)
            
            // 打印激活请求详情
            ActivationCodeGenerator.printActivationRequestInfo(
                context = this,
                activationUrl = activationUrl,
                challenge = challenge,
                code = activationCode
            )
            
            // 构建激活请求 Payload
            val payload = ActivationManager.buildActivationRequest(this, challenge)
            if (payload != null) {
                Log.i(TAG, "")
                Log.i(TAG, "📋 激活请求 Payload (可用于手动激活):")
                Log.i(TAG, payload)
                Log.i(TAG, "")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 生成激活码失败: ${e.message}", e)
        }
    }
    
    /**
     * 生成随机激活码 (6位数字)
     */
    private fun generateRandomCode(): String {
        return (100000..999999).random().toString()
    }
    
    /**
     * 生成随机 challenge 字符串
     */
    private fun generateRandomChallenge(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..32).map { chars.random() }.joinToString("")
    }

    private fun initNotificationChannels() {
        NotificationManagerCompat.from(this).createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(
                    getString(R.string.error_report_channel_id),
                    NotificationManagerCompat.IMPORTANCE_LOW
                )
                    .setName(getString(R.string.error_report_channel_name))
                    .setDescription(getString(R.string.error_report_channel_description))
                    .build()
            )
        )
    }
    
    companion object {
        private val TAG = App::class.simpleName
    }
}
