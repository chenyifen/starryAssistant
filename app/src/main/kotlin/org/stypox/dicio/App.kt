package org.stypox.dicio

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.HiltAndroidApp
import org.stypox.dicio.activation.ActivationManager
import org.stypox.dicio.util.checkPermissions

// IMPORTANT NOTE: beware of this nasty bug related to allowBackup=true
// https://medium.com/p/924c91bafcac
@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 初始化激活模块 (独立的功能模块)
        // ⚠️ 注意: 这是一个可选的模块,如果不需要可以删除整个 activation package
        ActivationManager.initialize(this)
        
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
}
