package com.ai.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_ASSIST
import android.content.Intent.ACTION_VOICE_COMMAND
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.shreyaspatil.permissionFlow.PermissionFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.ai.voice.io.wake.WakeService
import com.ai.voice.ui.floating.EnhancedFloatingWindowService
import com.ai.voice.ui.home.wakeWordPermissions
import com.ai.voice.ui.nav.Navigation
import com.ai.voice.ui.theme.AppTheme
import com.ai.voice.util.BaseActivity
import com.ai.voice.util.DebugLogger
import com.ai.voice.util.PermissionHelper
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity() {
    // 移除唤醒服务相关依赖，这些将由FloatingWindowService管理

    private var sttPermissionJob: Job? = null
    private var wakeServiceJob: Job? = null

    private var nextAssistAllowed = Instant.MIN
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        DebugLogger.logUI("MainActivity", "🚀 MainActivity created")
        
        // 处理唤醒词意图
        handleWakeWordTurnOnScreen(intent)
        
        // 处理助手意图
        if (isAssistIntent(intent)) {
            onAssistIntentReceived()
        }
        
        // 增加创建计数
        isCreated += 1
        
        // 检查是否需要导航到特定页面
        val navigateTo = intent.getStringExtra("navigate_to")
        
        if (navigateTo == "settings") {
            // 如果是从悬浮窗点击设置按钮进入，显示完整的Navigation界面
            composeSetContent {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Navigation()
                }
            }
        } else {
            // 自动启动悬浮助手（悬浮球）
            startFloatingAssistant()
            
            // 设置Compose内容
            composeSetContent {
                AppTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .safeDrawingPadding()
                        ) {
                            Navigation()
                        }
                    }
                }
            }
        }
    }
    
    
    companion object {
        private const val INTENT_BACKOFF_MILLIS = 100L
        private val TAG = MainActivity::class.simpleName
        const val ACTION_WAKE_WORD = "org.stypox.dicio.MainActivity.ACTION_WAKE_WORD"
        private const val REQUEST_OVERLAY_PERMISSION = 1001

        var isInForeground: Int = 0
            private set
        var isCreated: Int = 0
            private set

        private fun isAssistIntent(intent: Intent?): Boolean {
            return when (intent?.action) {
                ACTION_ASSIST, ACTION_VOICE_COMMAND -> true
                else -> false
            }
        }
    }

    /**
     * 处理助手意图 - 由悬浮球处理语音助手功能
     */
    private fun onAssistIntentReceived() {
        val now = Instant.now()
        if (nextAssistAllowed < now) {
            nextAssistAllowed = now.plusMillis(INTENT_BACKOFF_MILLIS)
            Log.d(TAG, "Assist intent handled by floating orb")
        } else {
            Log.w(TAG, "Ignoring duplicate assist intent")
        }
    }

    private fun handleWakeWordTurnOnScreen(intent: Intent?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
            intent?.action == ACTION_WAKE_WORD
        ) {
            // Dicio was started anew based on a wake word,
            // turn on the screen to let the user see what is happening
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        // the wake word triggered notification is not needed anymore
        WakeService.cancelTriggeredNotification(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // 更新当前intent

        handleWakeWordTurnOnScreen(intent)
        if (isAssistIntent(intent)) {
            onAssistIntentReceived()
        }
        
        // 检查是否需要导航到设置页面
        val navigateTo = intent.getStringExtra("navigate_to")
        if (navigateTo == "settings") {
            // 重新创建UI以显示设置页面
            composeSetContent {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Navigation()
                }
            }
        }
    }

    override fun onStart() {
        isInForeground += 1
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
        isInForeground -= 1

        // once the activity is swiped away from the lock screen (or put in the background in any
        // other way), we don't want to show it on the lock screen anymore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        }
    }

    /**
     * 启动悬浮球助手
     */
    private fun startFloatingAssistant() {
        if (Settings.canDrawOverlays(this)) {
            Log.d(TAG, "Starting floating assistant service")
            EnhancedFloatingWindowService.start(this)
        } else {
            Log.w(TAG, "Cannot start floating assistant - no overlay permission")
            // 可以在这里引导用户申请权限
            requestOverlayPermission()
        }
    }
    
    /**
     * 请求悬浮窗权限
     */
    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
        }
    }

    /**
     * 检查并请求必要的权限
     */
    private fun checkAndRequestPermissions() {
        // 检查基础权限
        if (!PermissionHelper.hasAllBasicPermissions(this)) {
            Log.d(TAG, "缺少基础权限，主动请求")
            PermissionHelper.requestBasicPermissions(this)
        }
        
        // 检查外部存储权限并主动请求
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Log.d(TAG, "缺少MANAGE_EXTERNAL_STORAGE权限，主动请求")
                PermissionHelper.requestManageExternalStoragePermission(this)
            }
        } else {
            if (!PermissionHelper.hasExternalStoragePermission(this)) {
                Log.d(TAG, "缺少READ_EXTERNAL_STORAGE权限，主动请求")
                PermissionHelper.requestExternalStoragePermission(this)
            }
        }
        
        // 记录权限状态
        val hasModelAccess = PermissionHelper.hasModelAccessPermissions(this)
        Log.d(TAG, "模型访问权限状态: ${if (hasModelAccess) "已授予" else "缺失"}")
    }
    
    
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        val result = PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (result.allGranted) {
            Log.d(TAG, "所有权限已授予: ${result.grantedPermissions}")
        } else {
            Log.w(TAG, "部分权限被拒绝: ${result.deniedPermissions}")
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            PermissionHelper.REQUEST_MANAGE_EXTERNAL_STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        Log.d(TAG, "MANAGE_EXTERNAL_STORAGE权限已授予")
                    } else {
                        Log.w(TAG, "MANAGE_EXTERNAL_STORAGE权限被拒绝")
                    }
                }
            }
            REQUEST_OVERLAY_PERMISSION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.canDrawOverlays(this)) {
                        Log.d(TAG, "悬浮窗权限已授予")
                        startFloatingAssistant()
                    } else {
                        Log.w(TAG, "悬浮窗权限被拒绝")
                        android.widget.Toast.makeText(
                            this,
                            "悬浮窗权限被拒绝，请到设置中手动开启以使用悬浮助手功能",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        // 取消协程作业
        sttPermissionJob?.cancel()
        wakeServiceJob?.cancel()
        
        // 注意：不要在这里停止WakeService，因为它应该在后台持续运行
        // 只有在用户明确关闭应用或系统资源不足时才停止
        
        isCreated -= 1
        super.onDestroy()
    }

}

