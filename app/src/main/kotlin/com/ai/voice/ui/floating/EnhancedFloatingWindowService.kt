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
import kotlinx.coroutines.flow.collect
import com.ai.voice.io.input.InputEvent
import com.ai.voice.eval.SkillEvaluator
import com.ai.voice.eval.InteractionLog
import com.ai.voice.eval.SkillHandler
import com.ai.voice.di.SkillContextInternal
import com.ai.voice.io.wake.WakeService
import com.ai.voice.io.wake.WakeWordCallback
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.ai.voice.io.wake.WakeWordCallbackManager
import com.ai.voice.di.WakeDeviceWrapper
import com.ai.voice.io.wake.WakeState
import com.ai.voice.ui.floating.components.DraggableFloatingOrb
import com.ai.voice.ui.floating.components.LottieAnimationTexts
import com.ai.voice.ui.floating.VoiceAssistantUIState
import com.ai.voice.ui.floating.state.VoiceAssistantStateProvider
import com.ai.voice.di.SpeechOutputDeviceWrapper
import com.ai.voice.R
import com.ai.voice.util.DebugLogger
import com.ai.voice.util.AsrHandler
import com.ai.voice.util.ActivationChecker
import com.ai.voice.util.SkillOutputConverter
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
    @Inject lateinit var wakeDevice: WakeDeviceWrapper
    @Inject lateinit var skillHandler: com.ai.voice.eval.SkillHandler
    @Inject lateinit var skillContext: com.ai.voice.di.SkillContextInternal
    
    // 生命周期管理
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    
    // 协程作用域
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 悬浮球组件
    private var floatingOrb: DraggableFloatingOrb? = null
    private var permissionCheckJob: kotlinx.coroutines.Job? = null
    
    
    override fun onCreate() {
        super.onCreate()
        DebugLogger.logUI(TAG, "🚀 EnhancedFloatingWindowService created")
        Log.i(TAG, "🚀 EnhancedFloatingWindowService onCreate")
        
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
        
        // 监听状态变化并更新UI
        observeStatusChanges()
        
        // 监听UI状态变化，统一更新悬浮球动画
        observeUIStateChanges()
        
        // 监听技能评估结果，匹配到技能后停止ASR并设置orb为idle
        observeSkillEvaluation()
        
        // 设置 AsrHandler Final识别结果回调，用于触发技能识别
        AsrHandler.setFinalResultCallback { finalText ->
            if (finalText.isNotBlank()) {
                DebugLogger.logUI(TAG, "🔍 Final识别完成，触发技能识别: $finalText")
                Log.i(TAG, "🎯 [Final ASR] 识别文本: \"$finalText\"")
                
                serviceScope.launch {
                    try {
                        val cleanedText = finalText.trim()
                            .replace(Regex("\\s+"), " ")
                            .replace(Regex("[.,。，!！?？;；:：]"), "")
                            .trim()
                        
                        Log.i(TAG, "🔍 [Final ASR] 清理后文本: \"$cleanedText\"")
                        
                        val skillRanker = skillHandler.skillRanker.value
                        
                        Log.i(TAG, "🔍 [Final ASR] 开始技能评分，清理后文本: \"$cleanedText\"")
                        val result = skillRanker.getBest(skillContext, cleanedText)
                        
                        if (result != null) {
                            val score = result.score.scoreIn01Range()
                            val skillId = result.skill.correspondingSkillInfo.id
                            Log.i(TAG, "✅ [Final ASR] 技能评分结果: skillId=$skillId, score=$score (raw=${result.score})")
                            DebugLogger.logUI(TAG, "✅ [Final ASR] 技能评分: $skillId = $score")
                        } else {
                            Log.i(TAG, "❌ [Final ASR] 无匹配技能")
                            DebugLogger.logUI(TAG, "❌ [Final ASR] 无匹配技能")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ [Final ASR] 技能评分异常", e)
                    }
                }
                
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
        
        permissionCheckJob?.cancel()
        permissionCheckJob = null
        
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
        
        // 更新状态显示
        updateStatusDisplay()
    }
    
    /**
     * 监听UI状态变化，统一更新悬浮球动画
     */
    private fun observeUIStateChanges() {
        voiceAssistantStateProvider.addListener { state ->
            Log.i(TAG, "🔄 [UI状态变化] UI状态=${state.uiState}")
            updateStatusDisplay()
        }
    }
    
    /**
     * 监听状态变化（权限、模型、激活状态）
     */
    private fun observeStatusChanges() {
        serviceScope.launch {
            wakeDevice.state.collect { wakeState ->
                updateStatusDisplay()
            }
        }
        
        serviceScope.launch {
            while (true) {
                updateStatusDisplay()
                kotlinx.coroutines.delay(5000)
            }
        }
    }
    
    /**
     * 更新状态显示
     */
    private fun updateStatusDisplay() {
        if (floatingOrb == null) return
        
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val wakeState = wakeDevice.state.value
        val activationStatus = ActivationChecker.getActivationStatus(this)
        val currentUIState = voiceAssistantStateProvider.getCurrentUIState()
        
        DebugLogger.logUI(TAG, "📊 状态更新: 权限=$hasPermission, 模型状态=$wakeState, 激活状态=$activationStatus, UI状态=$currentUIState")
        
        val statusText = when {
            !hasPermission -> {
                DebugLogger.logUI(TAG, "❌ 无权限状态")
                getString(R.string.status_no_permission)
            }
            wakeState is WakeState.ErrorLoading -> {
                DebugLogger.logUI(TAG, "❌ 模型加载失败: ${wakeState.throwable.message}")
                getString(R.string.status_no_permission)
            }
            wakeState == WakeState.NotLoaded -> {
                DebugLogger.logUI(TAG, "❌ 模型未加载")
                getString(R.string.status_no_permission)
            }
            activationStatus == ActivationChecker.ActivationStatus.NOT_ACTIVATED -> {
                DebugLogger.logUI(TAG, "⚠️ 未激活状态")
                getString(R.string.activation_status_not_activated)
            }
            activationStatus == ActivationChecker.ActivationStatus.TRIAL -> {
                val remainingDays = ActivationChecker.getRemainingDays(this)
                DebugLogger.logUI(TAG, "⏳ 试用模式: 剩余 $remainingDays 天")
                getString(R.string.activation_status_trial_with_days, remainingDays)
            }
            activationStatus == ActivationChecker.ActivationStatus.ACTIVATED -> {
                DebugLogger.logUI(TAG, "✅ 已激活状态")
                getString(R.string.activation_status_activated)
            }
            else -> LottieAnimationTexts.DEFAULT
        }
        
        Log.i(TAG, "📊 [状态显示] UI状态=$currentUIState, ASR状态=${AsrHandler.isStarted()}, 状态文本=\"$statusText\"")
        
        val stateManager = floatingOrb?.getAnimationStateManager()
        val isAsrStarted = AsrHandler.isStarted()
        
        when {
            isAsrStarted -> {
                stateManager?.setListening()
            }
            else -> {
                stateManager?.setIdle()
            }
        }
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
        serviceScope.launch {
            voiceAssistantStateProvider.transitionToState(
                VoiceAssistantUIState.LISTENING,
                reason = "用户点击悬浮球"
            )
        }
    }

    
    private fun startVoiceRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        AsrHandler.setSilenceTimeoutCallback {
            Log.d(TAG, "🔔 [SILENCE_TIMEOUT] AsrHandler静音超时回调触发")
            
            voiceAssistantStateProvider.setASRText("")
            voiceAssistantStateProvider.setTTSText("")
            
            serviceScope.launch {
                voiceAssistantStateProvider.transitionToState(
                    VoiceAssistantUIState.IDLE,
                    reason = "静音超时（10秒无语音）"
                )
            }
            
            updateStatusDisplay()
            
            Log.d(TAG, "✅ [SILENCE_TIMEOUT] 完整状态恢复完成")
        }
        
        serviceScope.launch {
            voiceAssistantStateProvider.transitionToState(
                VoiceAssistantUIState.LISTENING,
                reason = "启动语音识别"
            )
        }
    }
    
    override fun onWakeWordDetected(confidence: Float, wakeWord: String) {
        DebugLogger.logUI(TAG, "🎯 唤醒词检测成功: confidence=$confidence, wakeWord=$wakeWord")
        Log.i(TAG, "🎯 唤醒词检测成功: confidence=$confidence, wakeWord=$wakeWord")
        showFloatingOrb()
        
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            updateStatusDisplay()
            return
        }
        
        when (ActivationChecker.getActivationStatus(this)) {
            ActivationChecker.ActivationStatus.NOT_ACTIVATED -> {
                updateStatusDisplay()
                return
            }
            ActivationChecker.ActivationStatus.TRIAL,
            ActivationChecker.ActivationStatus.ACTIVATED -> {
                serviceScope.launch {
                    voiceAssistantStateProvider.transitionToState(
                        VoiceAssistantUIState.LISTENING,
                        reason = "唤醒词检测: $wakeWord"
                    )
                }
            }
        }

        startVoiceRecognition()
        serviceScope.launch {
            voiceAssistantStateProvider.transitionToState(
                VoiceAssistantUIState.LISTENING,
                reason = "唤醒词检测: $wakeWord"
            )
        }
    }
    
    override fun onWakeWordListeningStarted() {}
    override fun onWakeWordListeningStopped() {}
    override fun onWakeWordError(error: Throwable) {}
    
    private fun startWakeService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionCheckJob?.cancel()
            permissionCheckJob = serviceScope.launch {
                while (ContextCompat.checkSelfPermission(this@EnhancedFloatingWindowService, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    kotlinx.coroutines.delay(5000)
                }
                WakeService.start(this@EnhancedFloatingWindowService)
            }
            return
        }
        WakeService.start(this)
    }
    
    private fun observeSkillEvaluation() {
        serviceScope.launch {
            skillEvaluator.state.collect { interactionLog ->
                val lastInteraction = interactionLog.interactions.lastOrNull()
                val lastAnswer = lastInteraction?.questionsAnswers?.lastOrNull()?.answer
                
                if (lastAnswer != null) {
                    DebugLogger.logUI(TAG, "🎯 New skill result available")
                    
                    val skillInfo = lastInteraction?.skill
                    val isFallbackSkill = skillInfo?.id == "text"
                    
                    if (isFallbackSkill) {
                        DebugLogger.logUI(TAG, "⏭️ 识别不出具体命令（fallback），显示TTS文本但不播放")
                        return@collect
                    }
                    
                    serviceScope.launch {
                        voiceAssistantStateProvider.transitionToState(
                            VoiceAssistantUIState.IDLE,
                            reason = "技能执行完成"
                        )
                    }


                    try {
                        val speechOutput = lastAnswer.getSpeechOutput(skillContext)
                        DebugLogger.logUI(TAG, "🗣️ [DEBUG] getSpeechOutput() 返回: '$speechOutput'")
                        
                        if (speechOutput.isNotBlank()) {
                            voiceAssistantStateProvider.setTTSText(speechOutput)
                            setupTTSCompletionCallback()
                            DebugLogger.logUI(TAG, "🗣️ [DEBUG] TTS 文本已设置")
                        } else {
                            DebugLogger.logUI(TAG, "⚠️ [DEBUG] speechOutput 为空，跳过TTS和回调设置")
                        }
                    } catch (e: Exception) {
                        DebugLogger.logUI(TAG, "❌ Error getting speech output: ${e.message}")
                    }
                }
            }
        }
    }
    
    private fun setupTTSCompletionCallback() {
        speechOutputDevice.runWhenFinishedSpeaking {
        }
    }
    
    private fun cleanTextForSkillMatching(text: String): String {
        var cleaned = text.trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[.,。，!！?？;；:：]"), "")
            .trim()
        
        val sentences = cleaned.split(Regex("[。！？\\.!?]"))
        if (sentences.size > 1) {
            val firstSentence = sentences[0].trim()
            val hasCommandKeywords = firstSentence.contains(Regex("연결|실행|켜|꺼|변경|지워|녹화|캡쳐|보여|열"))
            if (hasCommandKeywords && firstSentence.length >= 3) {
                cleaned = firstSentence
            }
        }
        
        return cleaned
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
