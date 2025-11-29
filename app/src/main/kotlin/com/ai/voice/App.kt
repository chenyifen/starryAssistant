package com.ai.voice

import android.Manifest
import android.app.Application
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.HiltAndroidApp
import com.ai.voice.activation.ActivationManager
import com.ai.voice.activation.ActivationCodeGenerator
import com.ai.voice.util.checkPermissions
import com.ai.voice.util.AsrHandler
import com.ai.voice.util.ActivationChecker
import com.ai.voice.license.LicenseActivationManager
import com.ai.voice.settings.datastore.UserSettingsEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.datastore.core.DataStore
import dagger.hilt.android.EntryPointAccessors
import com.ai.voice.settings.datastore.UserSettings
import android.os.Handler
import android.os.Looper

// IMPORTANT NOTE: beware of this nasty bug related to allowBackup=true
// https://medium.com/p/924c91bafcac
@HiltAndroidApp
class App : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化授权激活管理器（基于MAC地址和激活码）
        try {
            LicenseActivationManager.initialize(
                context = this,
                appId = packageName, // 使用包名作为应用ID
                apiBaseUrl = "https://namingyou.com"
            )
            Log.i(TAG, "✅ 授权激活管理器初始化完成")
            
            // 后台验证激活状态（不阻塞启动）
            applicationScope.launch {
                try {
                    val activationManager = LicenseActivationManager.getInstance()
                    val result = activationManager.checkOnStartup()
                    if (result.activated) {
                        Log.i(TAG, "✅ 设备已激活: fromCache=${result.fromCache}, fromMac=${result.fromMac}")
                    } else {
                        Log.w(TAG, "⚠️ 设备未激活: needLicense=${result.needLicense}, error=${result.error}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 激活验证失败: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 授权激活管理器初始化失败: ${e.message}", e)
        }
        
        // 初始化激活模块 (独立的功能模块)
        // ⚠️ 注意: 这是一个可选的模块,如果不需要可以删除整个 activation package
        ActivationManager.initialize(this)
        
        // 🔒 关键：检查激活状态（必须激活才能使用）
        val isActivated = try {
            val dataStore = EntryPointAccessors.fromApplication(
                this,
                UserSettingsEntryPoint::class.java
            ).userSettings()
            val activated = ActivationChecker.isActivated(this, dataStore)
            
            if (!activated) {
                Log.e(TAG, "❌❌❌ 应用未激活，无法使用 ❌❌❌")
                Log.e(TAG, "请通过以下方式之一激活应用：")
                Log.e(TAG, "1. 使用授权码激活（LicenseActivationManager）")
                Log.e(TAG, "2. 使用设备指纹激活（ActivationManager）")
                
                // 在主线程显示Toast提示
                mainHandler.post {
                    Toast.makeText(
                        this,
                        "❌ 应用未激活，请先激活后再使用",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Log.i(TAG, "✅ 激活检查完成，应用已激活")
            }
            
            activated
        } catch (e: Exception) {
            Log.e(TAG, "❌ 激活检查失败: ${e.message}", e)
            // 出错时认为未激活
            mainHandler.post {
                Toast.makeText(
                    this,
                    "❌ 激活检查失败，请联系技术支持",
                    Toast.LENGTH_LONG
                ).show()
            }
            false
        }
        
        // 只有激活后才初始化 AsrHandler
        if (isActivated) {
            Log.i(TAG, "🚀 应用已激活，开始初始化 AsrHandler...")
            if (!AsrHandler.initialize(this)) {
                Log.e(TAG, "❌ AsrHandler初始化失败")
            }
        } else {
            Log.w(TAG, "⚠️ 应用未激活，跳过 AsrHandler 初始化")
        }
        
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
            
            // 打印激活请求详情
            ActivationCodeGenerator.printActivationRequestInfo(
                context = this,
                activationUrl = "服务器激活 URL",
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
