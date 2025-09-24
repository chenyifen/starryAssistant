package org.stypox.dicio.ui.floating

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.stypox.dicio.di.SttInputDeviceWrapper
import org.stypox.dicio.di.SkillContextInternal
import org.stypox.dicio.eval.SkillEvaluator
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.io.input.SttState
import org.stypox.dicio.io.wake.WakeWordCallback
import org.stypox.dicio.io.wake.WakeWordCallbackManager
import org.stypox.dicio.ui.home.InteractionLog
import org.stypox.dicio.util.DebugLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 语音助手状态协调器
 * 
 * 职责：
 * 1. 统一管理所有语音相关服务的状态
 * 2. 将复杂的多服务状态转换为简单的UI状态
 * 3. 解耦UI层与具体服务实现
 * 4. 提供统一的状态流给UI层消费
 */
@Singleton
class VoiceAssistantStateCoordinator @Inject constructor(
    private val sttInputDeviceWrapper: SttInputDeviceWrapper,
    private val skillEvaluator: SkillEvaluator,
    private val skillContext: SkillContextInternal
) : WakeWordCallback {
    
    companion object {
        private const val TAG = "VoiceAssistantStateCoordinator"
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // 统一的UI状态
    private val _uiState = MutableStateFlow(VoiceAssistantUIState.IDLE)
    val uiState: StateFlow<VoiceAssistantUIState> = _uiState.asStateFlow()
    
    // 当前显示文本
    private val _displayText = MutableStateFlow("")
    val displayText: StateFlow<String> = _displayText.asStateFlow()
    
    // TTS完成回调
    private var ttsCompletionCallback: (() -> Unit)? = null
    
    init {
        // 注册唤醒词回调
        WakeWordCallbackManager.registerCallback(this)
        
        // 监听多个服务状态并协调
        startStateCoordination()
    }
    
    /**
     * 开始状态协调 - 监听所有相关服务状态
     */
    private fun startStateCoordination() {
        // 分别监听不同的状态流，而不是组合它们
        
        // 监听STT设备状态
        scope.launch {
            sttInputDeviceWrapper.uiState.collect { sttState ->
                handleSttStateChange(sttState)
            }
        }
        
        // 监听SkillEvaluator的输入事件
        scope.launch {
            skillEvaluator.inputEvents.collect { inputEvent ->
                handleInputEvent(inputEvent)
            }
        }
        
        // 监听SkillEvaluator的状态变化
        scope.launch {
            skillEvaluator.state.collect { interactionLog ->
                handleSkillEvaluatorState(interactionLog)
            }
        }
    }
    
    /**
     * 处理STT设备状态变化
     */
    private fun handleSttStateChange(sttState: SttState?) {
        when (sttState) {
            is SttState.Loaded -> {
                DebugLogger.logUI(TAG, "🎤 STT device loaded and ready")
                // STT设备已加载，保持当前状态
            }
            
            is SttState.Listening -> {
                DebugLogger.logUI(TAG, "🎧 STT device listening")
                updateUIState(VoiceAssistantUIState.LISTENING, "LISTENING")
            }
            
            is SttState.Loading -> {
                DebugLogger.logUI(TAG, "⏳ STT device loading")
                updateUIState(VoiceAssistantUIState.THINKING, "")
            }
            
            is SttState.NotAvailable -> {
                DebugLogger.logUI(TAG, "❌ STT device not available")
                updateUIState(VoiceAssistantUIState.ERROR, "ERROR")
            }
            
            is SttState.ErrorLoading -> {
                DebugLogger.logUI(TAG, "❌ STT device loading error: ${sttState.throwable.message}")
                updateUIState(VoiceAssistantUIState.ERROR, "ERROR")
            }
            
            is SttState.ErrorDownloading -> {
                DebugLogger.logUI(TAG, "❌ STT device download error: ${sttState.throwable.message}")
                updateUIState(VoiceAssistantUIState.ERROR, "ERROR")
            }
            
            is SttState.ErrorUnzipping -> {
                DebugLogger.logUI(TAG, "❌ STT device unzip error: ${sttState.throwable.message}")
                updateUIState(VoiceAssistantUIState.ERROR, "ERROR")
            }
            
            is SttState.WaitingForResult -> {
                DebugLogger.logUI(TAG, "⏳ STT waiting for external result")
                updateUIState(VoiceAssistantUIState.LISTENING, "LISTENING")
            }
            
            null -> {
                DebugLogger.logUI(TAG, "🚫 STT device disabled")
                // STT设备被禁用，保持当前状态
            }
            
            else -> {
                DebugLogger.logUI(TAG, "🔄 STT device state: $sttState")
                // 其他状态暂时不处理
            }
        }
    }
    
    /**
     * 处理输入事件
     */
    private fun handleInputEvent(inputEvent: InputEvent) {
        when (inputEvent) {
            is InputEvent.Partial -> {
                DebugLogger.logUI(TAG, "📝 Partial result: ${inputEvent.utterance}")
                updateUIState(VoiceAssistantUIState.LISTENING, "LISTENING")
            }
            
            is InputEvent.Final -> {
                val bestResult = inputEvent.utterances.firstOrNull()?.first ?: ""
                DebugLogger.logUI(TAG, "✅ Final result: $bestResult")
                
                if (bestResult.isNotBlank()) {
                    updateUIState(VoiceAssistantUIState.THINKING, "")
                } else {
                    updateUIState(VoiceAssistantUIState.IDLE, "")
                }
            }
            
            is InputEvent.Error -> {
                DebugLogger.logUI(TAG, "❌ STT error: ${inputEvent.throwable.message}")
                updateUIState(VoiceAssistantUIState.ERROR, "ERROR")
            }
            
            InputEvent.None -> {
                DebugLogger.logUI(TAG, "🔇 No speech detected")
                updateUIState(VoiceAssistantUIState.IDLE, "")
            }
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
                updateUIState(VoiceAssistantUIState.THINKING, "")
            }
            
            // 有新的回复生成
            lastAnswer != null -> {
                DebugLogger.logUI(TAG, "💬 New skill output generated")
                
                val speechOutput = try {
                    lastAnswer.getSpeechOutput(skillContext)
                } catch (e: Exception) {
                    DebugLogger.logUI(TAG, "❌ Error getting speech output: ${e.message}")
                    "回复生成错误"
                }
                
                if (speechOutput.isNotBlank()) {
                    updateUIState(VoiceAssistantUIState.SPEAKING, "SPEAKING")
                    setupTTSCallback(speechOutput)
                } else {
                    updateUIState(VoiceAssistantUIState.IDLE, "")
                }
            }
            
            // 没有待处理问题，回到待机状态
            pendingQuestion == null -> {
                if (_uiState.value != VoiceAssistantUIState.IDLE) {
                    DebugLogger.logUI(TAG, "🏠 Returning to idle state")
                    updateUIState(VoiceAssistantUIState.IDLE, "")
                }
            }
        }
    }
    
    /**
     * 更新UI状态和显示文本
     */
    private fun updateUIState(newState: VoiceAssistantUIState, displayText: String) {
        if (_uiState.value != newState) {
            DebugLogger.logUI(TAG, "🔄 UI state changed: ${_uiState.value} → $newState")
            _uiState.value = newState
        }
        _displayText.value = displayText
    }
    
    /**
     * 设置TTS完成回调
     */
    private fun setupTTSCallback(speechOutput: String) {
        if (speechOutput.isNotBlank()) {
            try {
                val speechOutputDevice = skillContext.speechOutputDevice
                speechOutputDevice.runWhenFinishedSpeaking {
                    DebugLogger.logUI(TAG, "🏁 TTS playback finished")
                    updateUIState(VoiceAssistantUIState.IDLE, "")
                }
            } catch (e: Exception) {
                DebugLogger.logUI(TAG, "❌ Error setting TTS callback: ${e.message}")
                // 回退到定时器
                scope.launch {
                    kotlinx.coroutines.delay(3000)
                    updateUIState(VoiceAssistantUIState.IDLE, "")
                }
            }
        }
    }
    
    // ========================================
    // WakeWordCallback 接口实现
    // ========================================
    
    override fun onWakeWordDetected(confidence: Float, wakeWord: String) {
        DebugLogger.logUI(TAG, "🎯 Wake word detected: $wakeWord")
        updateUIState(VoiceAssistantUIState.WAKE_DETECTED, "LISTENING")
    }
    
    override fun onWakeWordListeningStarted() {
        DebugLogger.logUI(TAG, "👂 Wake word listening started")
        updateUIState(VoiceAssistantUIState.IDLE, "")
    }
    
    override fun onWakeWordListeningStopped() {
        DebugLogger.logUI(TAG, "🔇 Wake word listening stopped")
        updateUIState(VoiceAssistantUIState.IDLE, "")
    }
    
    override fun onWakeWordError(error: Throwable) {
        DebugLogger.logUI(TAG, "❌ Wake word error: ${error.message}")
        updateUIState(VoiceAssistantUIState.ERROR, "ERROR")
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        WakeWordCallbackManager.unregisterCallback(this)
    }
}

/**
 * 统一的语音助手UI状态
 * 
 * 简化的状态枚举，隐藏底层服务的复杂性
 */
enum class VoiceAssistantUIState {
    /** 待机状态 - 等待唤醒 */
    IDLE,
    
    /** 唤醒状态 - 检测到唤醒词 */
    WAKE_DETECTED,
    
    /** 监听状态 - 正在录音识别 */
    LISTENING,
    
    /** 思考状态 - 正在处理 */
    THINKING,
    
    /** 说话状态 - 正在播放回复 */
    SPEAKING,
    
    /** 错误状态 */
    ERROR
}
