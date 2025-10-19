package com.ai.voice.ui.floating

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ai.voice.util.AutoTestLogger

/**
 * 自动化测试广播接收器
 * 
 * 静态注册在 AndroidManifest.xml 中，即使应用未运行也能接收广播
 * 接收到广播后会启动 EnhancedFloatingWindowService 并触发测试
 */
class AutoTestBroadcastReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "AutoTestReceiver"
        const val ACTION_AUTO_TEST_START = "com.ai.voice.AUTO_TEST_START"
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.w(TAG, "⚠️ Context或Intent为空")
            return
        }
        
        when (intent.action) {
            ACTION_AUTO_TEST_START -> {
                Log.i(TAG, "📡 收到自动化测试广播")
                AutoTestLogger.logTestStarted()
                
                try {
                    // 启动悬浮球服务（如果尚未启动）
                    EnhancedFloatingWindowService.start(context)
                    Log.d(TAG, "✅ 已启动EnhancedFloatingWindowService")
                    
                    // 发送内部广播给服务，触发点击事件
                    val serviceIntent = Intent(ACTION_AUTO_TEST_START)
                    serviceIntent.setPackage(context.packageName)
                    context.sendBroadcast(serviceIntent)
                    Log.d(TAG, "✅ 已发送内部广播给服务")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 处理自动化测试广播失败", e)
                    AutoTestLogger.logTestError("广播处理失败: ${e.message}")
                }
            }
            else -> {
                Log.w(TAG, "⚠️ 未知的广播Action: ${intent.action}")
            }
        }
    }
}

