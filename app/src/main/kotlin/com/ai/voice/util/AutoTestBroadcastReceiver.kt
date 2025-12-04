package com.ai.voice.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ai.voice.io.wake.WakeWordCallbackManager

class AutoTestBroadcastReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        
        when (action) {
            ACTION_SIMULATE_WAKE -> {
                Log.i(TAG, "AutoTest: 模拟唤醒词检测")
                AutoTestLogger.logTestStarted()
                AutoTestLogger.logWakeupDetected()
                WakeWordCallbackManager.notifyWakeWordDetected(0.95f, "auto_test")
            }
            ACTION_SIMULATE_ASR_START -> {
                Log.i(TAG, "AutoTest: 模拟ASR启动")
                context?.let { AsrHandler.start(it) }
            }
            ACTION_SIMULATE_ASR_STOP -> {
                Log.i(TAG, "AutoTest: 模拟ASR停止")
                context?.let { AsrHandler.stop(it) }
            }
        }
    }
    
    companion object {
        private const val TAG = "AutoTest"
        const val ACTION_SIMULATE_WAKE = "com.ai.voice.AUTO_TEST_WAKE"
        const val ACTION_SIMULATE_ASR_START = "com.ai.voice.AUTO_TEST_ASR_START"
        const val ACTION_SIMULATE_ASR_STOP = "com.ai.voice.AUTO_TEST_ASR_STOP"
    }
}

