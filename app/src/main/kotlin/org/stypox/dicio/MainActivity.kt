package org.stypox.dicio

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
import org.stypox.dicio.io.wake.WakeService
import org.stypox.dicio.ui.floating.FloatingWindowService
import org.stypox.dicio.ui.floating.EnhancedFloatingWindowService
import org.stypox.dicio.ui.home.wakeWordPermissions
import org.stypox.dicio.ui.nav.Navigation
import org.stypox.dicio.ui.theme.AppTheme
import org.stypox.dicio.util.BaseActivity
import org.stypox.dicio.util.DebugLogger
import org.stypox.dicio.util.PermissionHelper
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
            // 🚫 暂时停用满屏悬浮窗
            // startFullScreenFloatingWindow()
            
            // 自动启动悬浮助手（悬浮球）
            startFloatingAssistant()
            
            // 自动启动WakeService
            startWakeService()
            
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
    
    /**
     * 启动WakeService
     */
    private fun startWakeService() {
        // 检查录音权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            DebugLogger.logUI("MainActivity", "❌ No RECORD_AUDIO permission, cannot start WakeService")
            return
        }
        
        try {
            val intent = Intent(this, WakeService::class.java)
            startService(intent)
            DebugLogger.logUI("MainActivity", "✅ WakeService started successfully")
        } catch (e: Exception) {
            DebugLogger.logUI("MainActivity", "❌ Failed to start WakeService: ${e.message}")
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
     * 处理助手意图 - 简化版本，只启动悬浮窗
     */
    private fun onAssistIntentReceived() {
        val now = Instant.now()
        if (nextAssistAllowed < now) {
            nextAssistAllowed = now.plusMillis(INTENT_BACKOFF_MILLIS)
            Log.d(TAG, "Received assist intent, but floating window is disabled")
            // 🚫 暂时停用满屏悬浮窗
            // startFullScreenFloatingWindow()
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
    

    private fun startFullScreenFloatingWindow() {
        // 🚫 满屏悬浮窗已停用
        Log.d(TAG, "🚫 满屏悬浮窗功能已停用，只使用悬浮球")
        return
        
        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                Log.d(TAG, "悬浮窗权限已授予，启动满屏悬浮窗")
                FloatingWindowService.startFullScreen(this)
            } else {
                Log.d(TAG, "请求悬浮窗权限")
                requestOverlayPermission()
            }
        } else {
            // Android 6.0以下版本不需要悬浮窗权限
            Log.d(TAG, "Android版本低于6.0，直接启动满屏悬浮窗")
            FloatingWindowService.startFullScreen(this)
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
    
    /**
     * 启动悬浮窗服务
     */
    private fun startFloatingWindowService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Log.d(TAG, "请求悬浮窗权限")
                requestOverlayPermission()
            } else {
                Log.d(TAG, "🚫 悬浮窗服务已停用")
                // 🚫 暂时停用满屏悬浮窗
                // FloatingWindowService.start(this)
            }
        } else {
            // Android 6.0以下直接启动
            // 🚫 暂时停用满屏悬浮窗
            // FloatingWindowService.start(this)
        }
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
            // 🚫 暂时停用满屏悬浮窗
            // startFullScreenFloatingWindow()
        } else {
            Log.w(TAG, "部分权限被拒绝: ${result.deniedPermissions}")
            // 可以显示权限说明对话框
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            PermissionHelper.REQUEST_MANAGE_EXTERNAL_STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        Log.d(TAG, "MANAGE_EXTERNAL_STORAGE权限已授予")
                        // 🚫 暂时停用满屏悬浮窗
                        // startFullScreenFloatingWindow()
                    } else {
                        Log.w(TAG, "MANAGE_EXTERNAL_STORAGE权限被拒绝")
                    }
                }
            }
            REQUEST_OVERLAY_PERMISSION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.canDrawOverlays(this)) {
                        Log.d(TAG, "悬浮窗权限已授予，但悬浮窗服务已停用")
                        // 🚫 暂时停用满屏悬浮窗
                        // FloatingWindowService.start(this)
                        // 同时启动悬浮球助手
                        startFloatingAssistant()
                    } else {
                        Log.w(TAG, "悬浮窗权限被拒绝")
                        showOverlayPermissionDeniedDialog()
                    }
                }
            }
        }
    }

    /**
     * 显示悬浮窗权限被拒绝的对话框
     */
    private fun showOverlayPermissionDeniedDialog() {
        // 这里可以显示一个对话框引导用户手动开启权限
        // 由于这是Compose项目，可以考虑使用Compose Dialog
        // 或者使用传统的AlertDialog
        Log.i(TAG, "悬浮窗权限被拒绝，建议用户手动到设置中开启")
        
        // 可以显示一个Toast提示用户
        android.widget.Toast.makeText(
            this,
            "悬浮窗权限被拒绝，请到设置中手动开启以使用悬浮助手功能",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
    
    /**
     * 检查悬浮窗服务是否正在运行
     */
    private fun isFloatingWindowServiceRunning(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (FloatingWindowService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun onDestroy() {
        // 取消协程作业
        sttPermissionJob?.cancel()
        wakeServiceJob?.cancel()
        
        // 🚫 不停止FloatingWindowService，因为已经停用
        // FloatingWindowService.stop(this)
        
        // 注意：不要在这里停止WakeService，因为它应该在后台持续运行
        // 只有在用户明确关闭应用或系统资源不足时才停止
        
        isCreated -= 1
        super.onDestroy()
    }

}

