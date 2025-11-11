package com.ai.voice.util

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import com.ai.voice.settings.datastore.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 激活检查工具类
 * 用于检查应用是否在15天试用期内
 */
@Singleton
class ActivationChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<UserSettings>
) {
    companion object {
        private const val TAG = "ActivationChecker"
        private const val TRIAL_PERIOD_DAYS = 15L // 15天试用期
        private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
        
        /**
         * 静态方法：检查应用是否已激活（不依赖注入）
         */
        fun isActivated(context: Context, dataStore: DataStore<UserSettings>? = null): Boolean {
            return runBlocking {
                try {
                    val firstInstallTime = getFirstInstallTimestamp(context, dataStore)
                    if (firstInstallTime == 0L) {
                        // 首次启动，保存当前时间
                        val currentTime = System.currentTimeMillis()
                        saveFirstInstallTimestamp(context, currentTime, dataStore)
                        Log.i(TAG, "✅ 首次启动，已保存安装时间: ${currentTime}")
                        return@runBlocking true
                    }
                    
                    val currentTime = System.currentTimeMillis()
                    val elapsedDays = (currentTime - firstInstallTime) / MILLIS_PER_DAY
                    val isActivated = elapsedDays < TRIAL_PERIOD_DAYS
                    
                    if (isActivated) {
                        val remainingDays = TRIAL_PERIOD_DAYS - elapsedDays
                        Log.i(TAG, "✅ 应用已激活，剩余试用期: ${remainingDays}天")
                    } else {
                        Log.w(TAG, "❌ 应用试用期已过期（已使用${elapsedDays}天，试用期${TRIAL_PERIOD_DAYS}天）")
                    }
                    
                    return@runBlocking isActivated
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 检查激活状态失败: ${e.message}", e)
                    // 出错时默认返回true，避免影响正常使用
                    return@runBlocking true
                }
            }
        }
        
        /**
         * 获取首次安装时间戳（静态方法）
         */
        private suspend fun getFirstInstallTimestamp(context: Context, dataStore: DataStore<UserSettings>?): Long {
            return try {
                // 优先从UserSettings获取
                if (dataStore != null) {
                    val settings = dataStore.data.first()
                    // proto3中int64字段默认值为0，直接检查是否大于0
                    if (settings.firstInstallTimestamp > 0) {
                        return settings.firstInstallTimestamp
                    }
                }
                
                // 如果UserSettings中没有，返回0（首次启动）
                0L
            } catch (e: Exception) {
                Log.e(TAG, "获取首次安装时间失败: ${e.message}", e)
                0L
            }
        }
        
        /**
         * 保存首次安装时间戳（静态方法）
         */
        private suspend fun saveFirstInstallTimestamp(context: Context, timestamp: Long, dataStore: DataStore<UserSettings>?) {
            try {
                // 保存到UserSettings
                if (dataStore != null) {
                    dataStore.updateData { settings ->
                        settings.toBuilder()
                            .setFirstInstallTimestamp(timestamp)
                            .build()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "保存首次安装时间失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 检查应用是否已激活（在试用期内）
     * @return true=已激活（在试用期内），false=已过期
     */
    fun isActivated(): Boolean {
        return isActivated(context, dataStore)
    }
    
    /**
     * 获取剩余试用天数
     */
    fun getRemainingDays(): Long {
        return runBlocking {
            try {
                val firstInstallTime = Companion.getFirstInstallTimestamp(context, dataStore)
                if (firstInstallTime == 0L) {
                    return@runBlocking TRIAL_PERIOD_DAYS
                }
                
                val currentTime = System.currentTimeMillis()
                val elapsedDays = (currentTime - firstInstallTime) / MILLIS_PER_DAY
                val remainingDays = TRIAL_PERIOD_DAYS - elapsedDays
                return@runBlocking maxOf(0, remainingDays)
            } catch (e: Exception) {
                Log.e(TAG, "获取剩余天数失败: ${e.message}", e)
                return@runBlocking 0L
            }
        }
    }
    
    private suspend fun getFirstInstallTimestamp(): Long {
        return Companion.getFirstInstallTimestamp(context, dataStore)
    }
    
    private suspend fun saveFirstInstallTimestamp(timestamp: Long) {
        Companion.saveFirstInstallTimestamp(context, timestamp, dataStore)
    }
}

