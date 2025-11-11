package com.ai.voice.io.wake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ai.voice.util.DebugLogger

/**
 * 唤醒词触发广播接收器
 * 
 * 临时调试功能：接收广播触发唤醒处理流程
 * 静态注册在 AndroidManifest.xml 中
 */
class WakeWordTriggerBroadcastReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "WakeWordTriggerReceiver"
        const val ACTION_TRIGGER_WAKE_WORD =
            "com.ai.voice.WakeService.ACTION_TRIGGER_WAKE_WORD"
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            return
        }
        
        if (intent.action != ACTION_TRIGGER_WAKE_WORD) {
            return
        }
        
        DebugLogger.logWakeWord(TAG, "📡 收到唤醒广播，触发唤醒处理")
        
        // 简单直接：通过WakeWordCallbackManager通知所有注册的回调
        WakeWordCallbackManager.notifyWakeWordDetected()
    }
}

