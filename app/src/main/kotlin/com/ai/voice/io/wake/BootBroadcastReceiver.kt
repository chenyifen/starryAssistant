package com.ai.voice.io.wake

import android.Manifest.permission.RECORD_AUDIO
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.KeyguardManager
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
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            if (Settings.canDrawOverlays(context)) {
                EnhancedFloatingWindowService.start(context)
            }
            
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                tryStartWakeService(context, "BOOT_COMPLETED")
            }
        }

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
        val hasPermission = ContextCompat.checkSelfPermission(context, RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            return
        }
        
        if (source == "BOOT_COMPLETED") {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (keyguardManager?.isKeyguardLocked == true) {
                return
            }
        }
        
        val wakeState = wakeDevice.state.value
        when (wakeState) {
            WakeState.NotLoaded,
            WakeState.Loading,
            WakeState.Loaded -> {
                try {
                    WakeService.start(context)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to start WakeService", e)
                }
            }
            else -> {}
        }
    }

    companion object {
        val TAG = BootBroadcastReceiver::class.simpleName ?: "BootBroadcastReceiver"
    }
}
