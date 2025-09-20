package org.stypox.dicio.ui.floating

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.stypox.dicio.di.SkillContextInternal
import org.stypox.dicio.di.SttInputDeviceWrapper
import org.stypox.dicio.di.WakeDeviceWrapper
import org.stypox.dicio.eval.SkillEvaluator
import org.stypox.dicio.io.wake.WakeService
import org.stypox.dicio.io.wake.WakeState
import org.stypox.dicio.ui.floating.components.FloatingAssistantUI
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@AndroidEntryPoint
class FloatingWindowService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    @Inject
    lateinit var skillEvaluator: SkillEvaluator
    
    @Inject
    lateinit var sttInputDevice: SttInputDeviceWrapper
    
    @Inject
    lateinit var wakeDevice: WakeDeviceWrapper
    
    @Inject
    lateinit var skillContext: SkillContextInternal

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private lateinit var floatingViewModel: FloatingWindowViewModel
    private var isFullScreen = false
    
    // 服务管理相关
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isWakeServiceStarted = false
    
    // LifecycleOwner implementation
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
    
    // ViewModelStore implementation
    private val _viewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore
        get() = _viewModelStore
    
    // SavedStateRegistry implementation
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        
        // 正确的初始化顺序：
        // 1. 首先初始化生命周期到INITIALIZED状态
        lifecycleRegistry.currentState = Lifecycle.State.INITIALIZED
        
        // 2. 然后attach SavedStateRegistry
        savedStateRegistryController.performAttach()
        
        // 3. 恢复保存的状态
        savedStateRegistryController.performRestore(null)
        
        // 4. 最后设置到CREATED状态
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        
        // 手动创建ViewModel，传入注入的依赖
        try {
            floatingViewModel = FloatingWindowViewModel(
                context = applicationContext,
                skillEvaluator = skillEvaluator,
                sttInputDevice = sttInputDevice,
                wakeDevice = wakeDevice,
                skillContext = skillContext
            )
        } catch (e: Exception) {
            Log.e(TAG, "创建FloatingWindowViewModel失败", e)
            stopSelf()
            return
        }
        
        if (canDrawOverlays()) {
            createFloatingWindow()
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
            
            // 启动唤醒服务和ASR服务
            startWakeServiceIfNeeded()
        } else {
            Log.w(TAG, "没有悬浮窗权限，无法创建悬浮窗")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 检查是否是满屏模式
        isFullScreen = intent?.getBooleanExtra(EXTRA_FULLSCREEN, false) ?: false
        Log.d(TAG, "启动悬浮窗服务，满屏模式: $isFullScreen")
        
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createFloatingWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 根据模式设置窗口大小和位置
        val displayMetrics = resources.displayMetrics
        val params = if (isFullScreen) {
            // 满屏模式
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }
        } else {
            // 小窗模式
            val windowWidth = (300 * displayMetrics.density).toInt()
            val windowHeight = (600 * displayMetrics.density).toInt() // 增加高度以容纳VoiceTextDisplay组件
            
            WindowManager.LayoutParams(
                windowWidth,
                windowHeight,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                x = 20
                y = 0
            }
        }

        // 创建 ComposeView
        val composeView = ComposeView(this)
        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeViewModelStoreOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(this)
        
        composeView.setContent {
            FloatingWindowTheme {
                FloatingAssistantContent()
            }
        }

        floatingView = composeView

        try {
            windowManager?.addView(floatingView, params)
            Log.d(TAG, "悬浮窗创建成功")
        } catch (e: SecurityException) {
            Log.e(TAG, "悬浮窗权限不足，无法创建悬浮窗", e)
            // 发送广播通知MainActivity权限问题
            sendPermissionErrorBroadcast()
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "创建悬浮窗失败", e)
            stopSelf()
        }
    }

    @Composable
    private fun FloatingAssistantContent() {
        val uiState by floatingViewModel.uiState.collectAsState()
        
        // 调试日志 - 检查Compose中的状态
        LaunchedEffect(uiState.asrText, uiState.ttsText, uiState.assistantState) {
            Log.d(TAG, "🎨 Compose状态更新: asrText='${uiState.asrText}', ttsText='${uiState.ttsText}', state=${uiState.assistantState}")
        }
        
                Box(modifier = Modifier.fillMaxSize()) {
                    FloatingAssistantUI(
                        uiState = uiState,
                        onEnergyOrbClick = { floatingViewModel.onEnergyOrbClick() },
                        onSettingsClick = { floatingViewModel.onSettingsClick() },
                        onCommandClick = { command -> floatingViewModel.onCommandClick(command) },
                        onDismiss = { floatingViewModel.onDismiss() },
                        isFullScreen = isFullScreen
                    )
                }
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }
    
    /**
     * 启动唤醒服务（如果需要）
     */
    private fun startWakeServiceIfNeeded() {
        // 检查麦克风权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "缺少麦克风权限，无法启动唤醒服务")
            return
        }

        // 检查唤醒设备状态并启动服务
        serviceScope.launch {
            wakeDevice.state.collect { state ->
                when (state) {
                    WakeState.NotLoaded, WakeState.Loading, WakeState.Loaded -> {
                        if (!isWakeServiceStarted && !WakeService.isRunning()) {
                            Log.d(TAG, "启动唤醒服务，当前状态: $state")
                            try {
                                WakeService.start(this@FloatingWindowService)
                                isWakeServiceStarted = true
                                Log.d(TAG, "✅ 唤醒服务启动成功")
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ 启动唤醒服务失败", e)
                            }
                        } else if (WakeService.isRunning()) {
                            isWakeServiceStarted = true
                            Log.d(TAG, "唤醒服务已在运行")
                        }
                    }
                    else -> {
                        Log.w(TAG, "唤醒设备状态不适合启动服务: $state")
                    }
                }
            }
        }
    }

    /**
     * 停止唤醒服务（如果已启动）
     */
    private fun stopWakeServiceIfStarted() {
        if (isWakeServiceStarted) {
            try {
                Log.d(TAG, "停止唤醒服务")
                WakeService.stop(this)
                isWakeServiceStarted = false
                Log.d(TAG, "✅ 唤醒服务已停止")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 停止唤醒服务失败", e)
            }
        }
    }

    /**
     * 发送权限错误广播
     */
    private fun sendPermissionErrorBroadcast() {
        val intent = Intent("org.stypox.dicio.FLOATING_WINDOW_PERMISSION_ERROR")
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // 停止唤醒服务和ASR服务
        stopWakeServiceIfStarted()
        
        // 取消服务协程作用域
        serviceScope.cancel()
        
        // 清理生命周期
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        
        floatingView?.let { view ->
            windowManager?.removeView(view)
        }
        floatingView = null
        windowManager = null
        _viewModelStore.clear()
        Log.d(TAG, "悬浮窗服务已销毁")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "FloatingWindowService"
        private const val EXTRA_FULLSCREEN = "fullscreen"
        
        fun start(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java)
            context.startService(intent)
        }
        
        fun startFullScreen(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java)
            intent.putExtra(EXTRA_FULLSCREEN, true)
            context.startService(intent)
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java)
            context.stopService(intent)
        }
    }
}