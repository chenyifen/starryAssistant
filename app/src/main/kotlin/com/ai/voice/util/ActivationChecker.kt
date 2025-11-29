package com.ai.voice.util

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import com.ai.voice.settings.datastore.UserSettings
import com.ai.voice.activation.ActivationManager
import com.ai.voice.license.LicenseActivationManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 激活检查工具类
 * 检查应用是否已通过真实激活（不再使用试用期逻辑）
 * 
 * 支持两种激活方式：
 * 1. ActivationManager - 基于设备指纹的激活
 * 2. LicenseActivationManager - 基于MAC地址和授权码的激活
 */
@Singleton
class ActivationChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<UserSettings>
) {
    companion object {
        private const val TAG = "ActivationChecker"
        
        /**
         * 静态方法：检查应用是否已激活（不依赖注入）
         * 
         * @return true=已激活，false=未激活
         */
        fun isActivated(context: Context, dataStore: DataStore<UserSettings>? = null): Boolean {
            return try {
                // 优先检查 LicenseActivationManager（基于MAC地址激活）
                val licenseActivated = try {
                    LicenseActivationManager.getInstance().isActivated()
                } catch (e: Exception) {
                    Log.d(TAG, "LicenseActivationManager未初始化或检查失败: ${e.message}")
                    false
                }
                
                if (licenseActivated) {
                    Log.i(TAG, "✅ 应用已激活（通过授权码/MAC白名单）")
                    return true
                }
                
                // 其次检查 ActivationManager（基于设备指纹激活）
                val deviceActivated = ActivationManager.isActivated(context)
                
                if (deviceActivated) {
                    Log.i(TAG, "✅ 应用已激活（通过设备指纹）")
                    return true
                }
                
                // 两种方式都未激活
                Log.w(TAG, "❌ 应用未激活，请进行设备激活")
                false
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ 检查激活状态失败: ${e.message}", e)
                // 出错时返回false，确保未激活无法使用
                false
            }
        }
    }
    
    /**
     * 检查应用是否已激活
     * @return true=已激活，false=未激活
     */
    fun isActivated(): Boolean {
        return isActivated(context, dataStore)
    }
}

