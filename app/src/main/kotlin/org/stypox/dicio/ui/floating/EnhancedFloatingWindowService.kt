package org.stypox.dicio.ui.floating

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
        
        // 运行配置测试
        FloatingOrbConfigTest.runAllTests(applicationContext)
        
        // 启动WakeService（现在由悬浮球服务管理）
        startWakeService()
        
        // 监听VoiceAssistantStateProvider的状态变化
        serviceScope.launch {
            voiceAssistantStateProvider.addListener { state ->
                handleVoiceAssistantStateChange(state)
            }
        }
        
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
        
        // TODO: 启动语音识别和文本显示
    }
    
    /**
     * 处理收缩到悬浮球
     */
    private fun handleContractToOrb() {
        DebugLogger.logUI(TAG, "📉 Contracting to orb")
        
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
    
    /**
     * 处理VoiceAssistantStateProvider的状态变化
     */
    private fun handleVoiceAssistantStateChange(state: VoiceAssistantFullState) {
        DebugLogger.logUI(TAG, "🔄 Voice assistant state changed: ${state.uiState}, display: '${state.displayText}'")
        
        // 更新当前状态（映射到本地状态枚举）
        currentVoiceState = when (state.uiState) {
            VoiceAssistantUIState.IDLE -> VoiceAssistantState.IDLE
            VoiceAssistantUIState.WAKE_DETECTED -> VoiceAssistantState.WAKE_DETECTED
            VoiceAssistantUIState.LISTENING -> VoiceAssistantState.LISTENING
            VoiceAssistantUIState.THINKING -> VoiceAssistantState.THINKING
            VoiceAssistantUIState.SPEAKING -> VoiceAssistantState.SPEAKING
            VoiceAssistantUIState.ERROR -> VoiceAssistantState.ERROR
        }
        
        // 由于DraggableFloatingOrb已经直接订阅了VoiceAssistantStateProvider，
        // 这里不需要再手动更新动画状态，避免重复处理
        
        // 记录状态变化用于调试
        DebugLogger.logUI(TAG, "📊 Service state updated to: $currentVoiceState")
        
        // 如果有ASR实时文本，记录日志
        if (state.asrText.isNotBlank()) {
            DebugLogger.logUI(TAG, "📝 ASR text: ${state.asrText}")
        }
        
        // 如果有TTS文本，记录日志
        if (state.ttsText.isNotBlank()) {
            DebugLogger.logUI(TAG, "🎵 TTS text: ${state.ttsText}")
        }
        
        // 如果有技能结果，记录日志
        state.result?.let { result ->
            DebugLogger.logUI(TAG, "🎯 Skill result: ${result.title} - ${result.content}")
        }
    }
    
    /**
     * 处理来自SkillEvaluator的InputEvent (已废弃，现在通过VoiceAssistantStateProvider处理)
     */
    @Deprecated("Use VoiceAssistantStateProvider instead")
    private fun handleInputEvent(inputEvent: InputEvent) {
        when (inputEvent) {
            is InputEvent.Partial -> {
                DebugLogger.logUI(TAG, "📝 Partial result: ${inputEvent.utterance}")
                currentVoiceState = VoiceAssistantState.LISTENING
                
                // 动画内部显示LISTENING状态
                floatingOrb?.getAnimationStateManager()?.setActive("LISTENING")
                // 不再使用下方的文本显示
            }
            
            is InputEvent.Final -> {
                val bestResult = inputEvent.utterances.firstOrNull()?.first ?: ""
                DebugLogger.logUI(TAG, "✅ Final result: $bestResult")
                
                if (bestResult.isNotBlank()) {
                    currentVoiceState = VoiceAssistantState.THINKING
                    
                    // 动画内部显示THINKING状态
                    floatingOrb?.getAnimationStateManager()?.setLoading()
                    // 可以在加载动画中显示THINKING文本，但通常加载动画本身就表示思考状态
                    
                    // SkillEvaluator会自动处理后续的技能匹配和回复
                } else {
                    currentVoiceState = VoiceAssistantState.IDLE
                    floatingOrb?.getAnimationStateManager()?.setIdle()
                    // 待机状态不显示文本
                }
            }
            
            is InputEvent.Error -> {
                DebugLogger.logUI(TAG, "❌ STT error: ${inputEvent.throwable.message}")
                currentVoiceState = VoiceAssistantState.ERROR
                
                // 动画内部显示ERROR状态
                floatingOrb?.getAnimationStateManager()?.setActive("ERROR")
                // 不再使用下方的文本显示
            }
            
            InputEvent.None -> {
                DebugLogger.logUI(TAG, "🔇 No speech detected")
                currentVoiceState = VoiceAssistantState.IDLE
                
                // 待机状态不显示文本
                floatingOrb?.getAnimationStateManager()?.setIdle()
                // 不再使用下方的文本显示
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
    
    /**
     * 处理SkillEvaluator状态变化
     */
    private fun handleSkillEvaluatorState(interactionLog: InteractionLog) {
        val pendingQuestion = interactionLog.pendingQuestion
        val lastInteraction = interactionLog.interactions.lastOrNull()
        val lastAnswer = lastInteraction?.questionsAnswers?.lastOrNull()?.answer
        
        when {
            // 有待处理的问题且正在评估技能
            pendingQuestion?.skillBeingEvaluated != null -> {
                DebugLogger.logUI(TAG, "🔄 Skill being evaluated: ${pendingQuestion.skillBeingEvaluated.id}")
                currentVoiceState = VoiceAssistantState.PROCESSING
                
                // 显示处理状态
                floatingOrb?.getAnimationStateManager()?.setLoading()
                // 加载动画本身就表示处理状态，不需要额外文本
            }
            
            // 有新的回复生成
            lastAnswer != null -> {
                DebugLogger.logUI(TAG, "💬 New skill output generated")
                currentVoiceState = VoiceAssistantState.SPEAKING
                
                // 获取回复文本
                val speechOutput = try {
                    lastAnswer.getSpeechOutput(skillEvaluator as SkillContext)
                } catch (e: Exception) {
                    DebugLogger.logUI(TAG, "❌ Error getting speech output: ${e.message}")
                    "回复生成错误"
                }
                
                if (speechOutput.isNotBlank()) {
                    // 显示回复文本在动画内部
                    floatingOrb?.getAnimationStateManager()?.setActive("SPEAKING")
                    
                    // 设置一个定时器，在TTS播放完成后回到待机状态
                    serviceScope.launch {
                        // 等待TTS播放完成（估算时间：每个字符100ms）
                        val estimatedDuration = (speechOutput.length * 100).coerceAtLeast(2000)
                        kotlinx.coroutines.delay(estimatedDuration.toLong())
                        
                        // 回到待机状态
                        currentVoiceState = VoiceAssistantState.IDLE
                        floatingOrb?.getAnimationStateManager()?.setIdle()
                    }
                } else {
                    // 没有语音输出，直接回到待机状态
                    currentVoiceState = VoiceAssistantState.IDLE
                    floatingOrb?.getAnimationStateManager()?.setIdle()
                }
            }
            
            // 没有待处理问题，回到待机状态
            pendingQuestion == null -> {
                if (currentVoiceState != VoiceAssistantState.IDLE) {
                    DebugLogger.logUI(TAG, "🏠 Returning to idle state")
                    currentVoiceState = VoiceAssistantState.IDLE
                    floatingOrb?.getAnimationStateManager()?.setIdle()
                }
            }
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
