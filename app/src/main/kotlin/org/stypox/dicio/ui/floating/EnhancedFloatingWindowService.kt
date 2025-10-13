package org.stypox.dicio.ui.floating

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.di.SttInputDeviceWrapper
import org.stypox.dicio.ui.home.InteractionLog
import org.dicio.skill.context.SkillContext
import org.stypox.dicio.di.WakeDeviceWrapper
import org.stypox.dicio.eval.SkillEvaluator
import org.stypox.dicio.io.wake.WakeService
import org.stypox.dicio.io.wake.WakeWordCallback
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.stypox.dicio.io.wake.WakeWordCallbackManager
import org.stypox.dicio.ui.floating.components.DraggableFloatingOrb
import org.stypox.dicio.ui.floating.components.LottieAnimationState
import org.stypox.dicio.ui.floating.components.LottieAnimationTexts
import org.stypox.dicio.ui.floating.state.VoiceAssistantFullState
import org.stypox.dicio.ui.floating.state.VoiceAssistantStateProvider
import org.stypox.dicio.util.DebugLogger
import org.stypox.dicio.settings.datastore.UserSettings
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.collectLatest
import org.stypox.dicio.BuildConfig
import org.stypox.dicio.MainActivity
import org.stypox.dicio.R
import javax.inject.Inject

/**
 * 语音助手状态枚举
 */
enum class VoiceAssistantState {
    IDLE,           // 空闲状态，等待唤醒
    WAKE_DETECTED,  // 检测到唤醒词
    LISTENING,      // 正在听取用户语音
    PROCESSING,     // 正在处理语音识别结果
    THINKING,       // 正在进行技能评估和处理
    SPEAKING,       // 正在播放TTS回复
    ERROR           // 错误状态
}

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
    @Inject lateinit var voiceAssistantStateProvider: VoiceAssistantStateProvider
    @Inject lateinit var dataStore: DataStore<UserSettings>
    
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
    
    // 当前语音助手状态
    private var currentVoiceState = VoiceAssistantState.IDLE
    
    override fun onCreate() {
        super.onCreate()
        DebugLogger.logUI(TAG, "🚀 EnhancedFloatingWindowService created")
        
        // 创建前台服务通知 (Android 8.0+ 要求在 startForegroundService() 后 5 秒内调用)
        createForegroundNotification()
        
        // 运行配置测试
        FloatingOrbConfigTest.runAllTests(applicationContext)
        
        // 启动WakeService（现在由悬浮球服务管理）
        startWakeService()
        
        // 注意：不在Service层监听状态变化，让DraggableFloatingOrb自己处理
        // 避免重复监听导致的状态更新循环
        
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
        
        // 监听设置变化
        observeSettings()
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
        
        // 创建UI控制器 (已屏蔽半屏功能)
        assistantUIController = AssistantUIController(this).apply {
            // 屏蔽半屏相关回调，改为文本显示模式
            onExpandToHalfScreen = { handleTextDisplayMode() }
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
     * 处理文本显示模式 (替代半屏展开)
     */
    private fun handleTextDisplayMode() {
        DebugLogger.logUI(TAG, "📝 Switching to text display mode")
        
        // 设置激活状态但不隐藏悬浮球
        floatingOrb?.getAnimationStateManager()?.setActive(LottieAnimationTexts.READY)
        
        // 启动语音识别
        startVoiceRecognition()
    }
    
    /**
     * 启动语音识别
     */
    private fun startVoiceRecognition() {
        DebugLogger.logUI(TAG, "🎤 Starting voice recognition...")
        DebugLogger.logUI(TAG, "📡 Current STT device: ${sttInputDeviceWrapper.javaClass.simpleName}")
        
        // 检查麦克风权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            DebugLogger.logUI(TAG, "❌ Microphone permission not granted")
            floatingOrb?.getAnimationStateManager()?.setActive(LottieAnimationTexts.ERROR)
            return
        }
        
        // 启动 STT 输入设备
        try {
            DebugLogger.logUI(TAG, "🔌 Attempting to start STT input device...")
            val sttStarted = sttInputDeviceWrapper.tryLoad(skillEvaluator::processInputEvent)
            
            if (sttStarted) {
                DebugLogger.logUI(TAG, "✅ STT input device started successfully")
                floatingOrb?.getAnimationStateManager()?.setActive(LottieAnimationTexts.LISTENING)
            } else {
                DebugLogger.logUI(TAG, "❌ STT input device failed to start")
                floatingOrb?.getAnimationStateManager()?.setActive(LottieAnimationTexts.ERROR)
            }
        } catch (e: Exception) {
            DebugLogger.logUI(TAG, "❌ Error starting STT: ${e.message}")
            floatingOrb?.getAnimationStateManager()?.setActive(LottieAnimationTexts.ERROR)
        }
    }
    
    /**
     * 处理收缩到悬浮球
     */
    private fun handleContractToOrb() {
        DebugLogger.logUI(TAG, "📉 Contracting to orb")
        
        // 停止 STT 录音
        sttInputDeviceWrapper.stopListening()
        DebugLogger.logUI(TAG, "⏹️ STT recording stopped")
        
        // 重新显示悬浮球
        floatingOrb?.show()
        
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
    
    // ========================================
    // VoiceAssistantStateProvider 状态处理
    // ========================================
    
    // 注意：状态处理现在完全由DraggableFloatingOrb处理，避免重复监听
    
    // 废弃的方法已移除，现在完全由DraggableFloatingOrb处理状态变化
    
    /**
     * 启动WakeService
     */
    private fun startWakeService() {
        // 检查录音权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            DebugLogger.logUI(TAG, "❌ No RECORD_AUDIO permission, cannot start WakeService")
            return
        }
        
        try {
            val intent = Intent(this, WakeService::class.java)
            startService(intent)
            DebugLogger.logUI(TAG, "✅ WakeService started by EnhancedFloatingWindowService")
        } catch (e: Exception) {
            DebugLogger.logUI(TAG, "❌ Failed to start WakeService: ${e.message}")
        }
    }
    
    // handleSkillEvaluatorState 方法已移除，现在完全由VoiceAssistantStateProvider统一处理
    
    /**
     * 监听设置变化
     */
    private fun observeSettings() {
        serviceScope.launch {
            try {
                dataStore.data.collectLatest { settings ->
                    // Settings observation placeholder
                    // 可以在这里添加其他设置的监听
                }
            } catch (e: Exception) {
                // 错误隔离：设置观察失败不应影响服务运行
                DebugLogger.logUI(TAG, "❌ Settings observation failed: ${e.message}")
            }
        }
    }
    
    /**
     * 创建前台服务通知
     * Android 8.0+ 要求使用 startForegroundService() 启动的服务必须在 5 秒内调用 startForeground()
     */
    private fun createForegroundNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // 创建通知渠道 (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.floating_window_service_label),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "语音助手悬浮球服务"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // 创建点击通知打开主界面的 Intent
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // 构建通知
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hearing_white)
            .setContentTitle("语音助手运行中")
            .setContentText("点击悬浮球开始对话")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, notification)
        DebugLogger.logUI(TAG, "✅ Foreground service notification created")
    }
    
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "floating_assistant_channel"
        private const val NOTIFICATION_ID = 1001
        /**
         * 启动服务
         */
        fun start(context: android.content.Context) {
            val intent = Intent(context, EnhancedFloatingWindowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
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
