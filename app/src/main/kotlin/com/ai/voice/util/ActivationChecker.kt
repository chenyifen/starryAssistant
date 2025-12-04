package com.ai.voice.util

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 激活检查工具类
 * 用于检查应用是否在15天试用期内
 */
@Singleton
class ActivationChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ActivationChecker"
        private const val TRIAL_PERIOD_DAYS = 15L
        private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
        private const val PREFS_NAME = "activation_prefs"
        private const val KEY_FIRST_INSTALL = "first_install_timestamp"
        
        fun isActivated(context: Context, unused: Any? = null): Boolean {
            return try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                var firstInstallTime = prefs.getLong(KEY_FIRST_INSTALL, 0L)
                
                if (firstInstallTime == 0L) {
                    firstInstallTime = System.currentTimeMillis()
                    prefs.edit().putLong(KEY_FIRST_INSTALL, firstInstallTime).apply()
                    Log.i(TAG, "✅ 首次启动，已保存安装时间")
                    return true
                }
                
                val elapsedDays = (System.currentTimeMillis() - firstInstallTime) / MILLIS_PER_DAY
                val isActivated = elapsedDays < TRIAL_PERIOD_DAYS
                
                if (isActivated) {
                    Log.i(TAG, "✅ 应用已激活，剩余试用期: ${TRIAL_PERIOD_DAYS - elapsedDays}天")
                } else {
                    Log.w(TAG, "❌ 应用试用期已过期")
                }
                
                isActivated
            } catch (e: Exception) {
                Log.e(TAG, "❌ 检查激活状态失败: ${e.message}", e)
                true
            }
        }
    }
    
    fun isActivated(): Boolean = isActivated(context)
    
    fun getRemainingDays(): Long {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val firstInstallTime = prefs.getLong(KEY_FIRST_INSTALL, 0L)
            if (firstInstallTime == 0L) return TRIAL_PERIOD_DAYS
            
            val elapsedDays = (System.currentTimeMillis() - firstInstallTime) / MILLIS_PER_DAY
            maxOf(0, TRIAL_PERIOD_DAYS - elapsedDays)
        } catch (e: Exception) {
            0L
        }
    }
}
