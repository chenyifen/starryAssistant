package com.ai.voice.io.wake

import android.Manifest.permission.RECORD_AUDIO
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.KeyguardManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import com.ai.voice.di.WakeDeviceWrapper
import com.ai.voice.ui.floating.EnhancedFloatingWindowService
import com.ai.voice.util.DebugLogger
import com.ai.voice.BuildConfig
import javax.inject.Inject

@AndroidEntryPoint
class BootBroadcastReceiver : BroadcastReceiver() {
    @Inject lateinit var wakeDevice: WakeDeviceWrapper

    override fun onReceive(context: Context, intent: Intent) {
        if (BuildConfig.DEBUG) {
            DebugLogger.logIfDebug(TAG, "Got intent ${intent.action}")
        }

        // 开机或快速重启时：尝试启动悬浮球服务（不涉及麦克风类型）
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            if (Settings.canDrawOverlays(context)) {
                if (BuildConfig.DEBUG) {
                    DebugLogger.logIfDebug(TAG, "Starting Floating Window Service on boot/locked boot")
                }
                EnhancedFloatingWindowService.start(context)
            } else {
                if (BuildConfig.DEBUG) {
                    DebugLogger.logIfDebug(TAG, "Overlay permission not granted, cannot start floating service")
                }
            }
            
            // 🔧 修复：在BOOT_COMPLETED时也尝试启动WakeService（如果设备已解锁且权限允许）
            // 因为单用户设备可能不会发送USER_PRESENT广播，或者已经发送过了
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                tryStartWakeService(context, "BOOT_COMPLETED")
            }
        }

        // 用户解锁后：如果唤醒词已启用，则启动麦克风前台服务
        if (intent.action == Intent.ACTION_USER_PRESENT) {
            tryStartWakeService(context, "USER_PRESENT")
        }
    }
    
    /**
     * 尝试启动WakeService
     * @param context 上下文
     * @param source 启动来源（用于日志）
     */
    private fun tryStartWakeService(context: Context, source: String) {
        // 检查录音权限
        if (ContextCompat.checkSelfPermission(context, RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) {
            if (BuildConfig.DEBUG) {
                DebugLogger.logIfDebug(TAG, "$source: RECORD_AUDIO permission not granted")
            }
            return
        }
        
        // 检查设备是否已解锁（对于BOOT_COMPLETED）
        if (source == "BOOT_COMPLETED") {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (keyguardManager?.isKeyguardLocked == true) {
                if (BuildConfig.DEBUG) {
                    DebugLogger.logIfDebug(TAG, "$source: Device is locked, waiting for USER_PRESENT")
                }
                return
            }
        }
        
        // 检查唤醒设备状态
        when (wakeDevice.state.value) {
            WakeState.NotLoaded,
            WakeState.Loading,
            WakeState.Loaded -> {
                if (BuildConfig.DEBUG) {
                    DebugLogger.logIfDebug(TAG, "$source: Starting WakeService")
                }
                WakeService.start(context)
            }
            else -> {
                if (BuildConfig.DEBUG) {
                    DebugLogger.logIfDebug(TAG, "$source: Wake device state ${wakeDevice.state.value}, skipping WakeService start")
                }
            }
        }
    }

    companion object {
        val TAG = BootBroadcastReceiver::class.simpleName ?: "BootBroadcastReceiver"
    }
}
