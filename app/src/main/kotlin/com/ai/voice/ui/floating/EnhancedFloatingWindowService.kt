package com.ai.voice.ui.floating

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.pm.ServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
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
import com.ai.voice.io.input.InputEvent
import com.ai.voice.ui.home.InteractionLog
import org.dicio.skill.context.SkillContext
import com.ai.voice.di.WakeDeviceWrapper
import com.ai.voice.eval.SkillEvaluator
import com.ai.voice.io.wake.WakeService
import com.ai.voice.io.wake.WakeWordCallback
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.ai.voice.io.wake.WakeWordCallbackManager
import com.ai.voice.ui.floating.components.DraggableFloatingOrb
import com.ai.voice.ui.floating.components.LottieAnimationState
import com.ai.voice.ui.floating.components.LottieAnimationTexts
import com.ai.voice.ui.floating.state.VoiceAssistantFullState
import com.ai.voice.ui.floating.VoiceAssistantUIState
import com.ai.voice.ui.floating.state.VoiceAssistantStateProvider
import com.ai.voice.di.SpeechOutputDeviceWrapper
import com.ai.voice.settings.datastore.UserSettings
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.collectLatest
import com.ai.voice.BuildConfig
import com.ai.voice.MainActivity
import com.ai.voice.R
import com.ai.voice.util.DebugLogger
import com.ai.voice.util.AsrHandler
import com.ai.voice.util.ActivationChecker
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
    SavedStateRegistryOwner,
    WakeWordCallback {
    
    private val TAG = "EnhancedFloatingWindowService"
    
    // 依赖注入
    // @Inject lateinit var sttInputDeviceWrapper: SttInputDeviceWrapper // 🔧 已禁用：不再使用，改用 AsrHandler
    @Inject lateinit var wakeDeviceWrapper: WakeDeviceWrapper
    @Inject lateinit var skillEvaluator: SkillEvaluator
    @Inject lateinit var voiceAssistantStateProvider: VoiceAssistantStateProvider
    @Inject lateinit var dataStore: DataStore<UserSettings>
    @Inject lateinit var speechOutputDevice: SpeechOutputDeviceWrapper
    
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
    
    // 自动化测试相关
    private var autoTestReceiver: BroadcastReceiver? = null
    
    
    override fun onCreate() {
        super.onCreate()
        DebugLogger.logUI(TAG, "🚀 EnhancedFloatingWindowService created")
        
        // 创建前台服务通知 (Android 8.0+ 要求在 startForegroundService() 后 5 秒内调用)
        createForegroundNotification()

        // 🔧 修复：移除Android版本限制，所有版本都尝试启动WakeService
        // 因为单用户设备可能不会发送USER_PRESENT广播，或者已经发送过了
        // EnhancedFloatingWindowService作为前台服务，可以启动其他前台服务
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
        
        // 注册 WakeWordCallback
        WakeWordCallbackManager.registerCallback(this)
        
        // 显示悬浮球
        showFloatingOrb()
        
        // 检查并运行唤醒词测试
        checkAndRunWakeWordTest()
        
        // 监听设置变化
        observeSettings()
        
        // 监听技能评估结果，匹配到技能后停止ASR并设置orb为idle
        observeSkillEvaluation()
        
        // 设置 AsrHandler Final识别结果回调，用于触发技能识别
        AsrHandler.setFinalResultCallback { finalText ->
            if (finalText.isNotBlank()) {
                DebugLogger.logUI(TAG, "🔍 Final识别完成，触发技能识别: $finalText")
                // 创建 Final 事件进行技能匹配
                val finalEvent = InputEvent.Final(listOf(Pair(finalText, 1.0f)))
                skillEvaluator.processInputEvent(finalEvent)
            }
        }
        
        // 注册自动化测试接收器
        registerAutoTestReceiver()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DebugLogger.logUI(TAG, "📥 Service start command received")
        return START_STICKY
    }
    
    override fun onDestroy() {
        DebugLogger.logUI(TAG, "🛑 EnhancedFloatingWindowService destroyed")
        
        // 取消注册 WakeWordCallback
        WakeWordCallbackManager.unregisterCallback(this)
        
        // 停止 AsrHandler
        DebugLogger.logUI(TAG, "⏹️ [CALLER] onDestroy() 调用 AsrHandler.stop()")
        AsrHandler.stop(this)
        
        // 清除所有回调
        AsrHandler.setSilenceTimeoutCallback(null)
        AsrHandler.setFinalResultCallback(null)
        
        // 取消注册自动化测试接收器
        unregisterAutoTestReceiver()
        
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
            // 悬浮球已设置为不可点击，无需设置回调
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
        Log.d(TAG, "🎯 [CLICK] handleOrbClick 开始执行")
        DebugLogger.logUI(TAG, "👆 Orb clicked - expanding to half screen")
        
        Log.d(TAG, "🔄 [CLICK] 设置Loading状态")
        // 设置加载状态
        floatingOrb?.getAnimationStateManager()?.setLoading()
        
        Log.d(TAG, "📈 [CLICK] 调用expandToHalfScreen")
        // 展开到半屏
        assistantUIController?.expandToHalfScreen()
        Log.d(TAG, "✅ [CLICK] handleOrbClick 执行完成")
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
     * 启动语音识别（已禁用，改用 AsrHandler）
     */
    private fun startVoiceRecognition() {
        DebugLogger.logUI(TAG, "🎤 Starting voice recognition with AsrHandler...")
        
        // 检查麦克风权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            DebugLogger.logUI(TAG, "❌ Microphone permission not granted")
            floatingOrb?.getAnimationStateManager()?.setActive(LottieAnimationTexts.ERROR)
            return
        }
        
        // 使用 AsrHandler 启动识别
        try {
            // 设置静音超时回调（仅用于更新 Orb 状态，不用于停止 AsrHandler）
            AsrHandler.setSilenceTimeoutCallback {
                DebugLogger.logUI(TAG, "⏰ AsrHandler 检测到连续静音10秒，重置 Orb 状态")
                floatingOrb?.getAnimationStateManager()?.setIdle()
            }
            
            val started = AsrHandler.start(this)
            if (started) {
                DebugLogger.logUI(TAG, "✅ AsrHandler started successfully")
                floatingOrb?.getAnimationStateManager()?.setActive(LottieAnimationTexts.LISTENING)
            } else {
                DebugLogger.logUI(TAG, "❌ AsrHandler failed to start")
                floatingOrb?.getAnimationStateManager()?.setActive(LottieAnimationTexts.ERROR)
            }
        } catch (e: Exception) {
            DebugLogger.logUI(TAG, "❌ Error starting AsrHandler: ${e.message}")
            floatingOrb?.getAnimationStateManager()?.setActive(LottieAnimationTexts.ERROR)
        }
    }
    
    /**
     * 处理收缩到悬浮球（已禁用 sttInputDeviceWrapper）
     */
    private fun handleContractToOrb() {
        DebugLogger.logUI(TAG, "📉 Contracting to orb")
        
        // 停止 AsrHandler
        DebugLogger.logUI(TAG, "⏹️ [CALLER] handleContractToOrb() 调用 AsrHandler.stop()")
        AsrHandler.stop(this)
        DebugLogger.logUI(TAG, "⏹️ AsrHandler stopped")
        
        // 重新显示悬浮球
        floatingOrb?.show()
        
        // 设置待机状态
        floatingOrb?.getAnimationStateManager()?.setIdle()
    }
    
    /**
     * 处理语音唤醒 - WakeWordCallback 实现
     */
    override fun onWakeWordDetected(confidence: Float, wakeWord: String) {
        DebugLogger.logUI(TAG, "🎤 Wake word detected: $wakeWord (confidence: $confidence)")
        
        // 🔒 检查激活状态
        val isActivated = ActivationChecker.isActivated(this, dataStore)
        if (!isActivated) {
            DebugLogger.logUI(TAG, "❌ 应用未激活，无法使用")
            // 播放"Not Activated"提示
            try {
                serviceScope.launch {
                    speechOutputDevice.speak("Not Activated")
                    DebugLogger.logUI(TAG, "🔊 已播放: Not Activated")
                }
            } catch (e: Exception) {
                DebugLogger.logUI(TAG, "❌ 播放TTS失败: ${e.message}")
            }
            return
        }
        
        // 显示悬浮球
        showFloatingOrb()
        
        // 触发唤醒词动画
        floatingOrb?.getAnimationStateManager()?.triggerWakeWord(LottieAnimationTexts.WAKE_WORD_DETECTED)
        
        // 启动 AsrHandler
        startVoiceRecognition()
    }
    
    override fun onWakeWordListeningStarted() {
        DebugLogger.logUI(TAG, "👂 Wake word listening started")
    }
    
    override fun onWakeWordListeningStopped() {
        DebugLogger.logUI(TAG, "👂 Wake word listening stopped")
    }
    
    override fun onWakeWordError(error: Throwable) {
        DebugLogger.logUI(TAG, "❌ Wake word error: ${error.message}")
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
            // 使用 WakeService.start() 方法，它会正确使用 startForegroundService
            WakeService.start(this)
            DebugLogger.logUI(TAG, "✅ WakeService started by EnhancedFloatingWindowService")
        } catch (e: Exception) {
            DebugLogger.logUI(TAG, "❌ Failed to start WakeService: ${e.message}")
        }
    }
    
    // handleSkillEvaluatorState 方法已移除，现在完全由VoiceAssistantStateProvider统一处理
    
    /**
     * 监听技能评估结果，匹配到技能后停止ASR并设置orb为idle
     */
    private fun observeSkillEvaluation() {
        serviceScope.launch {
            try {
                skillEvaluator.state.collect { interactionLog ->
                    val lastInteraction = interactionLog.interactions.lastOrNull()
                    val lastAnswer = lastInteraction?.questionsAnswers?.lastOrNull()?.answer
                    
                    if (lastAnswer != null) {
                        val skillInfo = lastInteraction?.skill
                        val isFallbackSkill = skillInfo?.id == "text"
                        
                        // 如果匹配到具体技能（不是fallback），停止ASR并设置orb为idle
                        if (!isFallbackSkill) {
                            DebugLogger.logUI(TAG, "✅ 匹配到技能 (${skillInfo?.id})，停止ASR并设置orb为idle")
                            
                            // 停止 AsrHandler
                            DebugLogger.logUI(TAG, "⏹️ [CALLER] 技能匹配后调用 AsrHandler.stop()，skillId=${skillInfo?.id}")
                            AsrHandler.stop(this@EnhancedFloatingWindowService)
                            
                            // 更新UI状态为IDLE并清空ASR和TTS文本
                            voiceAssistantStateProvider.updateUIState(VoiceAssistantUIState.IDLE)
                            voiceAssistantStateProvider.setASRText("")
                            voiceAssistantStateProvider.setTTSText("")
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLogger.logUI(TAG, "❌ Skill evaluation observation failed: ${e.message}")
            }
        }
    }
    
    /**
     * 检查并运行唤醒词测试
     */
    private fun checkAndRunWakeWordTest() {
        serviceScope.launch {
            try {
                val wakeDevice = com.ai.voice.io.wake.onnx.HiNudgeOnnxV8WakeDevice(applicationContext)
                com.ai.voice.io.wake.onnx.WakeWordTestRunner.checkAndRunTests(applicationContext, wakeDevice)
            } catch (e: Exception) {
                Log.w(TAG, "唤醒词测试失败: ${e.message}")
            }
        }
    }
    
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
        // Android 14+ (API 34+) 需要指定前台服务类型
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
        startForeground(NOTIFICATION_ID, notification)
        }
        DebugLogger.logUI(TAG, "✅ Foreground service notification created")
    }
    
    /**
     * 注册自动化测试广播接收器
     */
    private fun registerAutoTestReceiver() {
        autoTestReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_AUTO_TEST_START) {
                    Log.i(AUTO_TEST_TAG, "收到自动化测试启动指令")
                    handleAutoTestStart()
                }
            }
        }
        
        val filter = IntentFilter(ACTION_AUTO_TEST_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(autoTestReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(autoTestReceiver, filter)
        }
        Log.d(TAG, "✅ 自动化测试接收器已注册")
    }
    
    /**
     * 注销自动化测试广播接收器
     */
    private fun unregisterAutoTestReceiver() {
        autoTestReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d(TAG, "✅ 自动化测试接收器已注销")
            } catch (e: Exception) {
                Log.w(TAG, "注销接收器失败: ${e.message}")
            }
        }
        autoTestReceiver = null
    }
    
    /**
     * 处理自动化测试启动
     * 模拟点击悬浮球的效果
     */
    private fun handleAutoTestStart() {
        Log.i(AUTO_TEST_TAG, "开始自动化测试 - 模拟点击悬浮球")
        com.ai.voice.util.AutoTestLogger.logTestStarted()
        com.ai.voice.util.AutoTestLogger.logOrbClicked()
        
        // 模拟点击悬浮球，触发语音识别
        handleOrbClick()
    }
    
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "floating_assistant_channel"
        private const val NOTIFICATION_ID = 1001
        
        // 自动化测试常量
        const val ACTION_AUTO_TEST_START = "com.ai.voice.AUTO_TEST_START"
        private const val AUTO_TEST_TAG = "AutoTest"
        
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
