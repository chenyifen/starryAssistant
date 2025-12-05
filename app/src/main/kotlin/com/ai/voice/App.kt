package com.ai.voice

import android.Manifest
import android.app.Application
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.HiltAndroidApp
import com.ai.voice.util.checkPermissions
import com.ai.voice.util.AsrHandler
import com.ai.voice.util.ActivationChecker

@HiltAndroidApp
class App : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化激活检查
        ActivationChecker.isActivated(this, null)
            Log.i(TAG, "✅ 激活检查初始化完成")
        
        // 初始化 AsrHandler
        Log.i(TAG, "🚀 开始初始化 AsrHandler...")
        if (!AsrHandler.initialize(this)) {
            Log.e(TAG, "❌ AsrHandler初始化失败")
        }
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkPermissions(this, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            initNotificationChannels()
        }
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
