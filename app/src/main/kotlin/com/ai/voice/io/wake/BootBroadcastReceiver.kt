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

        // 开机或快速重启时：尝试启动悬浮球服务（不涉及麦克风类型）
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            if (Settings.canDrawOverlays(context)) {
                Log.d(TAG, "Starting Floating Window Service on boot/locked boot")
                EnhancedFloatingWindowService.start(context)
            } else {
                Log.d(TAG, "Overlay permission not granted, cannot start floating service")
            }
        }

        // 用户解锁后：如果唤醒词已启用，则启动麦克风前台服务
        if (intent.action == Intent.ACTION_USER_PRESENT) {
            if (ContextCompat.checkSelfPermission(context, RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED) {
                when (wakeDevice.state.value) {
                    WakeState.NotLoaded,
                    WakeState.Loading,
                    WakeState.Loaded -> {
                        Log.d(TAG, "User present: starting WakeService")
                        WakeService.start(context)
                    }
                    else -> Log.d(TAG, "User present: wake device state ${wakeDevice.state.value}")
                }
            } else {
                Log.d(TAG, "User present but RECORD_AUDIO not granted")
            }
        }
    }

    companion object {
        val TAG = BootBroadcastReceiver::class.simpleName
    }
}
