package org.stypox.dicio.ui.floating

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.stypox.dicio.di.SttInputDeviceWrapper
import org.stypox.dicio.di.WakeDeviceWrapper
import org.stypox.dicio.eval.SkillEvaluator
import org.stypox.dicio.ui.floating.components.DraggableFloatingOrb
import org.stypox.dicio.ui.floating.components.LottieAnimationState
import org.stypox.dicio.ui.floating.components.LottieAnimationTexts
import org.stypox.dicio.util.DebugLogger
import javax.inject.Inject

/**
 * 增强版悬浮窗服务
 * 
 * 特性：
 * - 管理可拖动的悬浮球
 * - 集成Lottie动画状态
 * - 支持语音唤醒触发
 * - 处理权限检查
 * - 生命周期管理
 */
@AndroidEntryPoint
class EnhancedFloatingWindowService : Service(), 
    LifecycleOwner, 
    ViewModelStoreOwner, 
    SavedStateRegistryOwner {
    
    private val TAG = "EnhancedFloatingWindowService"
    
    // 依赖注入
    @Inject lateinit var sttInputDeviceWrapper: SttInputDeviceWrapper
    @Inject lateinit var wakeDeviceWrapper: WakeDeviceWrapper
    @Inject lateinit var skillEvaluator: SkillEvaluator
    
    // 生命周期管理
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    
    // 协程作用域
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 悬浮球组件
    private var floatingOrb: DraggableFloatingOrb? = null
    
    // UI控制器
    private var assistantUIController: AssistantUIController? = null
    
    override fun onCreate() {
        super.onCreate()
        DebugLogger.logUI(TAG, "🚀 EnhancedFloatingWindowService created")
        
        // 运行配置测试
        FloatingOrbConfigTest.runAllTests(applicationContext)
        
        // 初始化生命周期
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        
        // 检查权限
        if (!checkOverlayPermission()) {
            DebugLogger.logUI(TAG, "❌ Overlay permission not granted")
            stopSelf()
            return
        }
        
        // 初始化组件
        initializeComponents()
        
        // 显示悬浮球
        showFloatingOrb()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DebugLogger.logUI(TAG, "📥 Service start command received")
        return START_STICKY
    }
    
    override fun onDestroy() {
        DebugLogger.logUI(TAG, "🛑 EnhancedFloatingWindowService destroyed")
        
        // 隐藏悬浮球
        hideFloatingOrb()
        
        // 清理资源
        assistantUIController?.cleanup()
        serviceScope.cancel()
        
        // 生命周期结束
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    // LifecycleOwner实现
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    
    // ViewModelStoreOwner实现
    override val viewModelStore: ViewModelStore get() = store
    
    // SavedStateRegistryOwner实现
    override val savedStateRegistry: SavedStateRegistry 
        get() = savedStateRegistryController.savedStateRegistry
    
    /**
     * 检查悬浮窗权限
     */
    private fun checkOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }
    
    /**
     * 初始化组件
     */
    private fun initializeComponents() {
        DebugLogger.logUI(TAG, "🔧 Initializing components")
        
        // 创建UI控制器
        assistantUIController = AssistantUIController(this).apply {
            // 设置回调
            onExpandToHalfScreen = { handleExpandToHalfScreen() }
            onContractToOrb = { handleContractToOrb() }
        }
        
        // 创建悬浮球
        floatingOrb = DraggableFloatingOrb(
            context = this,
            lifecycleOwner = this,
            viewModelStoreOwner = this,
            savedStateRegistryOwner = this
        ).apply {
            // 设置点击回调
            onOrbClick = { handleOrbClick() }
            onOrbLongPress = { handleOrbLongPress() }
        }
    }
    
    /**
     * 显示悬浮球
     */
    private fun showFloatingOrb() {
        DebugLogger.logUI(TAG, "🎈 Showing floating orb")
        floatingOrb?.show()
        
        // 设置为待机状态
        floatingOrb?.getAnimationStateManager()?.setIdle()
    }
    
    /**
     * 隐藏悬浮球
     */
    private fun hideFloatingOrb() {
        DebugLogger.logUI(TAG, "🎈 Hiding floating orb")
        floatingOrb?.hide()
    }
    
    /**
     * 处理悬浮球点击
     */
    private fun handleOrbClick() {
        DebugLogger.logUI(TAG, "👆 Orb clicked - expanding to half screen")
        
        // 设置加载状态
        floatingOrb?.getAnimationStateManager()?.setLoading()
        
        // 展开到半屏
        assistantUIController?.expandToHalfScreen()
    }
    
    /**
     * 处理悬浮球长按
     */
    private fun handleOrbLongPress() {
        DebugLogger.logUI(TAG, "👆 Orb long pressed - showing settings")
        
        // TODO: 显示设置菜单或开始拖动
        floatingOrb?.getAnimationStateManager()?.setActive(LottieAnimationTexts.READY)
    }
    
    /**
     * 处理展开到半屏
     */
    private fun handleExpandToHalfScreen() {
        DebugLogger.logUI(TAG, "📈 Expanding to half screen")
        
        // 设置激活状态
        floatingOrb?.getAnimationStateManager()?.setActive(LottieAnimationTexts.READY)
        
        // TODO: 启动半屏Activity
    }
    
    /**
     * 处理收缩到悬浮球
     */
    private fun handleContractToOrb() {
        DebugLogger.logUI(TAG, "📉 Contracting to orb")
        
        // 设置待机状态
        floatingOrb?.getAnimationStateManager()?.setIdle()
    }
    
    /**
     * 处理语音唤醒
     */
    fun handleVoiceWakeUp() {
        DebugLogger.logUI(TAG, "🎤 Voice wake up detected")
        
        // 触发唤醒词动画
        floatingOrb?.getAnimationStateManager()?.triggerWakeWord(LottieAnimationTexts.WAKE_WORD_DETECTED)
        
        // 自动展开到半屏
        assistantUIController?.expandToHalfScreen()
    }
    
    /**
     * 更新动画状态
     */
    fun updateAnimationState(state: LottieAnimationState, text: String? = null) {
        DebugLogger.logUI(TAG, "🎭 Updating animation state to: $state")
        
        val animationManager = floatingOrb?.getAnimationStateManager()
        when (state) {
            LottieAnimationState.IDLE -> animationManager?.setIdle()
            LottieAnimationState.LOADING -> animationManager?.setLoading()
            LottieAnimationState.ACTIVE -> animationManager?.setActive(text ?: LottieAnimationTexts.DEFAULT)
            LottieAnimationState.WAKE_WORD -> animationManager?.triggerWakeWord(text ?: LottieAnimationTexts.WAKE_WORD_DETECTED)
        }
    }
    
    companion object {
        /**
         * 启动服务
         */
        fun start(context: android.content.Context) {
            val intent = Intent(context, EnhancedFloatingWindowService::class.java)
            context.startService(intent)
        }
        
        /**
         * 停止服务
         */
        fun stop(context: android.content.Context) {
            val intent = Intent(context, EnhancedFloatingWindowService::class.java)
            context.stopService(intent)
        }
    }
}
