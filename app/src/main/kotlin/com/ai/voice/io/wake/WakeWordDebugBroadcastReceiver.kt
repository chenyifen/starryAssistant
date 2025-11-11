package com.ai.voice.io.wake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ai.voice.util.DebugLogger

/**
 * 唤醒词调试广播接收器
 * 
 * 静态注册在 AndroidManifest.xml 中，用于接收调试唤醒广播
 * 发送广播：adb shell am broadcast -a com.ai.voice.WakeService.ACTION_DEBUG_WAKE_WORD
 */
class WakeWordDebugBroadcastReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "WakeWordDebugReceiver"
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            return
        }
        
        if (intent.action != WakeService.ACTION_DEBUG_WAKE_WORD) {
            return
        }
        
        DebugLogger.logWakeWord(TAG, "🔧 [DEBUG] 收到调试唤醒广播，模拟唤醒词检测")
        
        // 通知所有注册的回调（这会触发EnhancedFloatingWindowService的onWakeWordDetected）
        WakeWordCallbackManager.notifyWakeWordDetected()
        
        // 同时启动MainActivity（如果需要）
        val mainIntent = Intent(context, com.ai.voice.MainActivity::class.java)
        mainIntent.setAction(com.ai.voice.MainActivity.ACTION_WAKE_WORD)
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(mainIntent)
            DebugLogger.logWakeWord(TAG, "📱 已启动MainActivity")
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ 启动MainActivity失败", e)
        }
    }
}

