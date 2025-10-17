package com.ai.voice.io.wake

import android.Manifest.permission.RECORD_AUDIO
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import com.ai.voice.di.WakeDeviceWrapper
import com.ai.voice.ui.floating.EnhancedFloatingWindowService
import javax.inject.Inject

@AndroidEntryPoint
class BootBroadcastReceiver : BroadcastReceiver() {
    @Inject lateinit var wakeDevice: WakeDeviceWrapper

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Got intent ${intent.action}")

        if (ContextCompat.checkSelfPermission(context, RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Audio permission not granted")
            return
        }

        // 检查悬浮窗权限，如果有权限就启动悬浮球服务
        if (Settings.canDrawOverlays(context)) {
            Log.d(TAG, "Starting Floating Window Service on boot")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ 不能直接从BOOT_COMPLETED启动需要麦克风的前台服务
                // 悬浮球服务会在启动时处理这个情况
                Log.d(TAG, "Creating notification to start floating service later")
                // 由于悬浮球服务内部会启动WakeService，所以这里创建一个通知
                WakeService.createNotificationToStartLater(context)
            } else {
                // Android 10及以下，可以直接启动
                EnhancedFloatingWindowService.start(context)
            }
        } else {
            Log.d(TAG, "Overlay permission not granted, cannot start floating service")
        }

        // 保留原有的WakeService启动逻辑作为备用
        when (wakeDevice.state.value) {
            WakeState.NotLoaded,
            WakeState.Loading,
            WakeState.Loaded -> {
                // any of these three states indicates that wake word recognition is enabled, and
                // that the model has already been downloaded

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Starting from Android 11, it is not possible to start a foreground service
                    // that accesses the microphone from a BOOT_COMPLETED broadcast. So we show a
                    // notification instead, which starts the foreground service when clicked.
                    // https://developer.android.com/about/versions/15/behavior-changes-15#fgs-boot-completed
                    Log.d(TAG, "Creating notification")
                    WakeService.createNotificationToStartLater(context)
                } else {
                    Log.d(TAG, "Starting service")
                    WakeService.start(context)
                }
            }
            else -> {
                Log.d(TAG, "Wrong wake device state: ${wakeDevice.state.value}")
            }
        }
    }

    companion object {
        val TAG = BootBroadcastReceiver::class.simpleName
    }
}
