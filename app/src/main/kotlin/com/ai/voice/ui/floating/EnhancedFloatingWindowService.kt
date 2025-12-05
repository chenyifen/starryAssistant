package com.ai.voice.ui.floating

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.pm.ServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.os.IBinder
import android.provider.Settings
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
import com.ai.voice.eval.SkillEvaluator
import com.ai.voice.eval.InteractionLog
import com.ai.voice.io.wake.WakeService
import com.ai.voice.io.wake.WakeWordCallback
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.ai.voice.io.wake.WakeWordCallbackManager
import com.ai.voice.ui.floating.components.DraggableFloatingOrb
import com.ai.voice.ui.floating.components.LottieAnimationTexts
import com.ai.voice.ui.floating.VoiceAssistantUIState
import com.ai.voice.ui.floating.state.VoiceAssistantStateProvider
import com.ai.voice.di.SpeechOutputDeviceWrapper
import com.ai.voice.R
import com.ai.voice.util.DebugLogger
import com.ai.voice.util.AsrHandler
import com.ai.voice.util.ActivationChecker
import javax.inject.Inject

@AndroidEntryPoint
class EnhancedFloatingWindowService : Service(), 
    LifecycleOwner, 
    ViewModelStoreOwner, 
    SavedStateRegistryOwner,
    WakeWordCallback {
    
    private val TAG = "EnhancedFloatingWindowService"
    
    @Inject lateinit var skillEvaluator: SkillEvaluator
    @Inject lateinit var voiceAssistantStateProvider: VoiceAssistantStateProvider
    @Inject lateinit var speechOutputDevice: SpeechOutputDeviceWrapper
    
    // 生命周期管理
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    
    // 协程作用域
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 悬浮球组件
    private var floatingOrb: DraggableFloatingOrb? = null
    
    
    override fun onCreate() {
        super.onCreate()
        DebugLogger.logUI(TAG, "🚀 EnhancedFloatingWindowService created")
        
        createForegroundNotification()
        startWakeService()
        
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
        
        // 监听技能评估结果，匹配到技能后停止ASR并设置orb为idle
        observeSkillEvaluation()
        
        // 设置 AsrHandler Final识别结果回调，用于触发技能识别
        AsrHandler.setFinalResultCallback { finalText ->
            if (finalText.isNotBlank()) {
                DebugLogger.logUI(TAG, "🔍 Final识别完成，触发技能识别: $finalText")
                val finalEvent = InputEvent.Final(listOf(Pair(finalText, 1.0f)))
                skillEvaluator.processInputEvent(finalEvent)
            }
        }
        
        // 启动自动化测试HTTP服务器（仅home渠道）
        startAutoTestServer()
    }
    
    private var autoTestServer: Any? = null
    
    private fun startAutoTestServer() {
        try {
            Log.i(TAG, "Trying to start AutoTest HTTP Server...")
            val serverClass = Class.forName("com.ai.voice.test.AutoTestHttpServer")
            val constructor = serverClass.getConstructor(Context::class.java, Int::class.java)
            autoTestServer = constructor.newInstance(this, 8765)
            serverClass.getMethod("start").invoke(autoTestServer)
            Log.i(TAG, "AutoTest HTTP Server started on port 8765")
        } catch (e: ClassNotFoundException) {
            Log.d(TAG, "AutoTestHttpServer not available (non-home build)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AutoTest server: ${e.message}", e)
        }
    }
    
    private fun stopAutoTestServer() {
        try {
            autoTestServer?.let {
                it.javaClass.getMethod("stop").invoke(it)
            }
            autoTestServer = null
        } catch (e: Exception) { }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DebugLogger.logUI(TAG, "📥 Service start command received")
        return START_STICKY
    }
    
    override fun onDestroy() {
        DebugLogger.logUI(TAG, "🛑 EnhancedFloatingWindowService destroyed")
        
        stopAutoTestServer()
        WakeWordCallbackManager.unregisterCallback(this)
        
        // 停止 AsrHandler
        Log.d(TAG, "🛑 [DESTROY] onDestroy() 调用 AsrHandler.stop()")
        AsrHandler.stop(this)
        
        // 清除所有回调
        AsrHandler.setSilenceTimeoutCallback(null)
        AsrHandler.setFinalResultCallback(null)
        
        // 隐藏悬浮球
        hideFloatingOrb()
        
        // 清理资源
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
    
    private fun initializeComponents() {
        floatingOrb = DraggableFloatingOrb(
            context = this,
            lifecycleOwner = this,
            viewModelStoreOwner = this,
            savedStateRegistryOwner = this
        )
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
        DebugLogger.logUI(TAG, "👆 Orb clicked")
        floatingOrb?.getAnimationStateManager()?.setLoading()
        handleTextDisplayMode()
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
    
    private fun startVoiceRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            floatingOrb?.getAnimationStateManager()?.setActive(LottieAnimationTexts.ERROR)
            return
        }
        
        // ✅ 设置静音超时回调：使用统一的状态转换方法
        AsrHandler.setSilenceTimeoutCallback {
            Log.d(TAG, "🔔 [SILENCE_TIMEOUT] AsrHandler静音超时回调触发")
            
            // 更新悬浮球动画
            floatingOrb?.getAnimationStateManager()?.setIdle()
            
            // 🔥 使用统一的状态转换方法，确保功能状态同步
            serviceScope.launch {
                voiceAssistantStateProvider.transitionToState(
                    VoiceAssistantUIState.IDLE,
                    reason = "静音超时（10秒无语音）"
                )
            }
            
            // 清空ASR文本
            voiceAssistantStateProvider.setASRText("")
            voiceAssistantStateProvider.setTTSText("")
            
            Log.d(TAG, "✅ [SILENCE_TIMEOUT] 完整状态恢复完成")
        }
        
        if (AsrHandler.start(this)) {
            floatingOrb?.getAnimationStateManager()?.setActive(LottieAnimationTexts.LISTENING)
        } else {
            floatingOrb?.getAnimationStateManager()?.setActive(LottieAnimationTexts.ERROR)
        }
    }
    
    override fun onWakeWordDetected(confidence: Float, wakeWord: String) {
        showFloatingOrb()
        
        when (ActivationChecker.getActivationStatus(this)) {
            ActivationChecker.ActivationStatus.NOT_ACTIVATED -> {
                floatingOrb?.getAnimationStateManager()?.setActive(getString(R.string.activation_status_not_activated))
                return
            }
            ActivationChecker.ActivationStatus.TRIAL -> {
                val remainingDays = ActivationChecker.getRemainingDays(this)
                val statusText = getString(R.string.activation_status_trial_with_days, remainingDays)
                floatingOrb?.getAnimationStateManager()?.triggerWakeWord(statusText)
            }
            ActivationChecker.ActivationStatus.ACTIVATED -> { }
        }
        startVoiceRecognition()
    }
    
    override fun onWakeWordListeningStarted() {}
    override fun onWakeWordListeningStopped() {}
    override fun onWakeWordError(error: Throwable) {}
    
    private fun startWakeService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        WakeService.start(this)
    }
    
    private fun observeSkillEvaluation() {
        serviceScope.launch {
            skillEvaluator.state.collect { interactionLog ->
                val lastInteraction = interactionLog.interactions.lastOrNull()
                val lastAnswer = lastInteraction?.questionsAnswers?.lastOrNull()?.answer
                val skillId = lastInteraction?.skill?.id
                Log.d(TAG, "🔍 [SKILL] observeSkillEvaluation: lastAnswer=${lastAnswer != null}, skillId=$skillId")
                
                // 🔥 简化：所有技能执行完成后都转换到 IDLE（包括 Fallback）
                if (lastAnswer != null) {
                    Log.d(TAG, "🔍 [SKILL] 技能执行完成 (skillId=$skillId)")
                    
                    // 使用统一的状态转换方法
                    serviceScope.launch {
                        voiceAssistantStateProvider.transitionToState(
                            VoiceAssistantUIState.IDLE,
                            reason = "技能执行完成: $skillId"
                        )
                    }
                    
                    voiceAssistantStateProvider.setASRText("")
                    voiceAssistantStateProvider.setTTSText("")
                } else {
                    Log.d(TAG, "🔍 [SKILL] 无技能结果")
                }
            }
        }
    }
    
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
        
        // 构建通知（无点击行为，悬浮球已在屏幕上）
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hearing_white)
            .setContentTitle("语音助手运行中")
            .setContentText("点击悬浮球开始对话")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "floating_assistant_channel"
        private const val NOTIFICATION_ID = 1001
        
        fun start(context: android.content.Context) {
            val intent = Intent(context, EnhancedFloatingWindowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: android.content.Context) {
            val intent = Intent(context, EnhancedFloatingWindowService::class.java)
            context.stopService(intent)
        }
    }
}
