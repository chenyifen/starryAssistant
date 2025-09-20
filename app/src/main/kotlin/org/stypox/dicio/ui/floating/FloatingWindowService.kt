package org.stypox.dicio.ui.floating

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import org.stypox.dicio.di.SkillContextInternal
import org.stypox.dicio.di.SttInputDeviceWrapper
import org.stypox.dicio.di.WakeDeviceWrapper
import org.stypox.dicio.eval.SkillEvaluator
import org.stypox.dicio.ui.floating.components.FloatingAssistantUI
import javax.inject.Inject

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
        } else {
            Log.w(TAG, "没有悬浮窗权限，无法创建悬浮窗")
            stopSelf()
        }
    }

    private fun createFloatingWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.CENTER
        params.x = 0
        params.y = 0

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
        
        Box(modifier = Modifier.fillMaxSize()) {
            FloatingAssistantUI(
                uiState = uiState,
                onEnergyOrbClick = { floatingViewModel.onEnergyOrbClick() },
                onSettingsClick = { floatingViewModel.onSettingsClick() },
                onCommandClick = { command -> floatingViewModel.onCommandClick(command) },
                onDismiss = { floatingViewModel.onDismiss() }
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
     * 发送权限错误广播
     */
    private fun sendPermissionErrorBroadcast() {
        val intent = Intent("org.stypox.dicio.FLOATING_WINDOW_PERMISSION_ERROR")
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        
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
        
        fun start(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java)
            context.startService(intent)
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java)
            context.stopService(intent)
        }
    }
}