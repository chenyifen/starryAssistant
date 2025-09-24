package org.stypox.dicio.ui.floating.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.di.SpeechOutputDeviceWrapper
import org.stypox.dicio.di.SttInputDeviceWrapper
import org.stypox.dicio.eval.SkillEvaluator
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.io.wake.WakeWordCallback
import org.stypox.dicio.io.wake.WakeWordCallbackManager
import org.stypox.dicio.ui.floating.VoiceAssistantStateCoordinator
import org.stypox.dicio.ui.floating.VoiceAssistantUIState
import org.stypox.dicio.ui.home.InteractionLog
import org.stypox.dicio.util.DebugLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 语音助手状态提供者 - 极简版
 * 
 * 核心职责：
 * 1. 统一管理语音助手的完整状态
 * 2. 提供全局访问点，任何UI组件都可以获取当前状态
 * 3. 支持状态监听，UI组件可以响应状态变化
 * 4. 与现有的VoiceAssistantStateCoordinator集成
 */
@Singleton
class VoiceAssistantStateProvider @Inject constructor(
    private val stateCoordinator: VoiceAssistantStateCoordinator,
    private val sttInputDeviceWrapper: SttInputDeviceWrapper,
    private val skillEvaluator: SkillEvaluator,
    private val speechOutputDeviceWrapper: SpeechOutputDeviceWrapper
) : WakeWordCallback {
    
    companion object {
        private const val TAG = "VoiceAssistantStateProvider"
        
        @Volatile
        private var INSTANCE: VoiceAssistantStateProvider? = null
        
        /**
         * 获取全局实例 - 任何地方都可以调用
         */
        fun getInstance(): VoiceAssistantStateProvider {
            return INSTANCE ?: throw IllegalStateException("VoiceAssistantStateProvider not initialized")
        }
        
        /**
         * 初始化全局实例 - 由Hilt在创建时调用
         */
        internal fun initialize(instance: VoiceAssistantStateProvider) {
            INSTANCE = instance
            DebugLogger.logUI(TAG, "🌟 VoiceAssistantStateProvider initialized")
        }
    }
    
    // 当前状态
    private var _currentState = VoiceAssistantFullState.IDLE
    
    // 状态监听器
    private val listeners = mutableSetOf<(VoiceAssistantFullState) -> Unit>()
    
    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.Main)
    
    // 会话历史管理
    private val conversationHistory = mutableListOf<ConversationMessage>()
    private val maxHistorySize = 50 // 最多保留50条对话记录
    
    init {
        // 初始化全局实例
        initialize(this)
        
        // 注册唤醒词回调
        WakeWordCallbackManager.registerCallback(this)
        
        // 监听状态协调器的状态变化
        observeStateCoordinator()
    }
    
    /**
     * 监听状态协调器的状态变化
     */
    private fun observeStateCoordinator() {
        // 监听UI状态变化
        scope.launch {
            stateCoordinator.uiState.collect { uiState ->
                updateState(uiState = uiState)
            }
        }
        
        // 监听显示文本变化
        scope.launch {
            stateCoordinator.displayText.collect { displayText ->
                updateState(displayText = displayText)
            }
        }
        
        // 监听SkillEvaluator的InputEvent来获取ASR实时文本
        scope.launch {
            skillEvaluator.inputEvents.collect { inputEvent ->
                handleInputEvent(inputEvent)
            }
        }
        
        // 监听SkillEvaluator的状态变化来获取技能结果
        scope.launch {
            skillEvaluator.state.collect { interactionLog ->
                handleSkillEvaluatorState(interactionLog)
            }
        }
    }
    
    /**
     * 处理InputEvent - 主要用于ASR实时文本更新
     */
    private fun handleInputEvent(inputEvent: InputEvent) {
        when (inputEvent) {
            is InputEvent.Partial -> {
                DebugLogger.logUI(TAG, "📝 ASR partial result: ${inputEvent.utterance}")
                updateState(asrText = inputEvent.utterance)
            }
            
            is InputEvent.Final -> {
                val bestResult = inputEvent.utterances.firstOrNull()?.first ?: ""
                val confidence = inputEvent.utterances.firstOrNull()?.second ?: 0f
                DebugLogger.logUI(TAG, "✅ ASR final result: $bestResult (confidence: $confidence)")
                
                updateState(asrText = bestResult, confidence = confidence)
                
                // 添加用户消息到会话历史
                if (bestResult.isNotBlank()) {
                    addUserMessage(bestResult, confidence)
                }
                
                // 清空ASR文本，因为现在进入技能处理阶段
                scope.launch {
                    kotlinx.coroutines.delay(1000) // 延迟1秒后清空，让用户看到最终结果
                    updateState(asrText = "")
                }
            }
            
            is InputEvent.Error -> {
                DebugLogger.logUI(TAG, "❌ ASR error: ${inputEvent.throwable.message}")
                updateState(asrText = "")
            }
            
            InputEvent.None -> {
                DebugLogger.logUI(TAG, "🔇 No speech detected")
                updateState(asrText = "")
            }
        }
    }
    
    /**
     * 处理SkillEvaluator状态变化 - 主要用于技能结果处理
     */
    private fun handleSkillEvaluatorState(interactionLog: InteractionLog) {
        val lastInteraction = interactionLog.interactions.lastOrNull()
        val lastAnswer = lastInteraction?.questionsAnswers?.lastOrNull()?.answer
        
        if (lastAnswer != null) {
            DebugLogger.logUI(TAG, "🎯 New skill result available")
            
            // 将技能输出转换为SimpleResult
            val simpleResult = convertSkillOutputToSimpleResult(lastAnswer)
            updateState(result = simpleResult)
            
            // 获取TTS文本并添加AI回复到会话历史
            try {
                val speechOutput = lastAnswer.getSpeechOutput(skillEvaluator as org.dicio.skill.context.SkillContext)
                if (speechOutput.isNotBlank()) {
                    updateState(
                        uiState = VoiceAssistantUIState.SPEAKING,
                        ttsText = speechOutput,
                        displayText = "SPEAKING"
                    )
                    addAIMessage(speechOutput)
                    
                    // 监听TTS播放完成
                    setupTTSCompletionCallback()
                }
            } catch (e: Exception) {
                DebugLogger.logUI(TAG, "❌ Error getting speech output: ${e.message}")
            }
        }
    }
    
    /**
     * 将技能输出转换为SimpleResult
     */
    private fun convertSkillOutputToSimpleResult(skillOutput: SkillOutput): SimpleResult {
        return try {
            // 获取技能的基本信息
            val skillClassName = skillOutput::class.java.simpleName
            val speechText = skillOutput.getSpeechOutput(skillEvaluator as org.dicio.skill.context.SkillContext)
            
            DebugLogger.logUI(TAG, "🔄 Converting skill output: $skillClassName")
            
            // 根据具体的技能输出类型创建相应的SimpleResult
            // 首先尝试精确匹配已知的技能输出类
            when (skillOutput) {
                // 天气技能 - 精确匹配
                is org.stypox.dicio.skills.weather.WeatherOutput.Success -> {
                    SimpleResultBuilder.weather(
                        location = skillOutput.city,
                        temperature = skillOutput.temp.toInt(),
                        condition = skillOutput.description
                    )
                }
                is org.stypox.dicio.skills.weather.WeatherOutput.Failed -> {
                    SimpleResultBuilder.error("无法获取${skillOutput.city}的天气信息")
                }
                
                // 时间技能 - 精确匹配
                is org.stypox.dicio.skills.current_time.CurrentTimeOutput -> {
                    SimpleResultBuilder.time(speechText)
                }
                
                // 计算器技能 - 精确匹配
                is org.stypox.dicio.skills.calculator.CalculatorOutput -> {
                    // 尝试从语音输出中提取计算表达式和结果
                    val parts = speechText.split("等于", "是", "=", "equals")
                    if (parts.size >= 2) {
                        val expression = parts[0].trim()
                        val result = parts[1].trim()
                        SimpleResultBuilder.calculation(expression, result)
                    } else {
                        SimpleResultBuilder.calculation("计算", speechText)
                    }
                }
                
                // 导航技能 - 精确匹配
                is org.stypox.dicio.skills.navigation.NavigationOutput -> {
                    SimpleResultBuilder.appAction("导航", "地图导航", speechText.contains("导航"))
                }
                
                // 媒体控制技能 - 精确匹配
                is org.stypox.dicio.skills.media.MediaOutput -> {
                    val action = when {
                        speechText.contains("播放", ignoreCase = true) -> "播放"
                        speechText.contains("暂停", ignoreCase = true) -> "暂停"
                        speechText.contains("上一", ignoreCase = true) -> "上一首"
                        speechText.contains("下一", ignoreCase = true) -> "下一首"
                        else -> "媒体控制"
                    }
                    SimpleResultBuilder.appAction("媒体", action, !speechText.contains("没有"))
                }
                
                // 应用启动技能 - 精确匹配
                is org.stypox.dicio.skills.open.OpenOutput -> {
                    val success = !speechText.contains("无法") && !speechText.contains("未知")
                    SimpleResultBuilder.appAction("应用", "打开应用", success)
                }
                
                // 电话技能 - 精确匹配
                is org.stypox.dicio.skills.telephone.TelephoneOutput -> {
                    val success = !speechText.contains("没有") && !speechText.contains("找不到")
                    SimpleResultBuilder.appAction("电话", "拨打电话", success)
                }
                
                // 其他技能使用模糊匹配
                else -> {
                    // 基于类名进行模糊匹配作为备用方案
                    when {
                        skillClassName.contains("Weather", ignoreCase = true) -> {
                            SimpleResultBuilder.info("天气信息", speechText)
                        }
                        skillClassName.contains("Time", ignoreCase = true) -> {
                            SimpleResultBuilder.time(speechText)
                        }
                        skillClassName.contains("Calculator", ignoreCase = true) || skillClassName.contains("Math", ignoreCase = true) -> {
                            SimpleResultBuilder.calculation("计算", speechText)
                        }
                        skillClassName.contains("Timer", ignoreCase = true) -> {
                            SimpleResultBuilder.info("定时器", speechText)
                        }
                        skillClassName.contains("Open", ignoreCase = true) || skillClassName.contains("App", ignoreCase = true) -> {
                            SimpleResultBuilder.appAction("应用", "打开", true)
                        }
                        skillClassName.contains("Search", ignoreCase = true) -> {
                            SimpleResultBuilder.info("搜索", speechText)
                        }
                        skillClassName.contains("Navigation", ignoreCase = true) -> {
                            SimpleResultBuilder.info("导航", speechText)
                        }
                        skillClassName.contains("Telephone", ignoreCase = true) || skillClassName.contains("Phone", ignoreCase = true) -> {
                            SimpleResultBuilder.appAction("电话", "拨打", true)
                        }
                        skillClassName.contains("Media", ignoreCase = true) -> {
                            SimpleResultBuilder.appAction("媒体", "控制", true)
                        }
                        skillClassName.contains("Lyrics", ignoreCase = true) -> {
                            SimpleResultBuilder.info("歌词", speechText)
                        }
                        skillClassName.contains("Listening", ignoreCase = true) -> {
                            SimpleResultBuilder.info("监听控制", speechText)
                        }
                        skillClassName.contains("Fallback", ignoreCase = true) -> {
                            SimpleResultBuilder.error("未能理解您的请求")
                        }
                        else -> {
                            SimpleResultBuilder.fromSkillOutput(skillClassName, speechText, true)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogger.logUI(TAG, "❌ Error converting skill output: ${e.message}")
            SimpleResultBuilder.error("技能处理错误: ${e.message}")
        }
    }
    
    /**
     * 获取当前状态
     */
    fun getCurrentState(): VoiceAssistantFullState = _currentState
    
    /**
     * 添加状态监听器
     */
    fun addListener(listener: (VoiceAssistantFullState) -> Unit) {
        listeners.add(listener)
        DebugLogger.logUI(TAG, "📡 Added listener, total: ${listeners.size}")
        
        // 立即通知当前状态
        listener(_currentState)
    }
    
    /**
     * 移除状态监听器
     */
    fun removeListener(listener: (VoiceAssistantFullState) -> Unit) {
        listeners.remove(listener)
        DebugLogger.logUI(TAG, "📡 Removed listener, total: ${listeners.size}")
    }
    
    /**
     * 更新UI状态
     */
    fun updateUIState(uiState: VoiceAssistantUIState) {
        updateState(uiState = uiState)
    }
    
    /**
     * 更新显示文本
     */
    fun updateDisplayText(displayText: String) {
        updateState(displayText = displayText)
    }
    
    /**
     * 更新置信度
     */
    fun updateConfidence(confidence: Float) {
        updateState(confidence = confidence)
    }
    
    /**
     * 设置ASR文本
     */
    fun setASRText(text: String) {
        updateState(asrText = text)
    }
    
    /**
     * 设置TTS文本
     */
    fun setTTSText(text: String) {
        updateState(ttsText = text)
    }
    
    /**
     * 设置技能结果
     */
    fun setResult(result: SimpleResult) {
        DebugLogger.logUI(TAG, "🎯 Setting skill result: ${result.title} (${result.type})")
        updateState(result = result)
    }
    
    /**
     * 清除技能结果
     */
    fun clearResult() {
        updateState(result = null)
    }
    
    /**
     * 重置到空闲状态
     */
    fun resetToIdle() {
        DebugLogger.logUI(TAG, "🏠 Resetting to idle state")
        _currentState = VoiceAssistantFullState.IDLE
        notifyListeners()
    }
    
    /**
     * 添加用户消息到会话历史
     */
    fun addUserMessage(text: String, confidence: Float) {
        val message = ConversationMessage(
            text = text,
            isUser = true,
            timestamp = System.currentTimeMillis(),
            confidence = confidence
        )
        
        synchronized(conversationHistory) {
            conversationHistory.add(message)
            // 保持历史记录在限制范围内
            while (conversationHistory.size > maxHistorySize) {
                conversationHistory.removeAt(0)
            }
        }
        
        DebugLogger.logUI(TAG, "👤 User message added: $text (confidence: $confidence)")
        updateState(conversationHistory = conversationHistory.toList())
    }
    
    /**
     * 添加AI回复到会话历史
     */
    fun addAIMessage(text: String) {
        val message = ConversationMessage(
            text = text,
            isUser = false,
            timestamp = System.currentTimeMillis()
        )
        
        synchronized(conversationHistory) {
            conversationHistory.add(message)
            // 保持历史记录在限制范围内
            while (conversationHistory.size > maxHistorySize) {
                conversationHistory.removeAt(0)
            }
        }
        
        DebugLogger.logUI(TAG, "🤖 AI message added: $text")
        updateState(conversationHistory = conversationHistory.toList())
    }
    
    /**
     * 清空会话历史
     */
    fun clearConversationHistory() {
        synchronized(conversationHistory) {
            conversationHistory.clear()
        }
        DebugLogger.logUI(TAG, "🧹 Conversation history cleared")
        updateState(conversationHistory = emptyList())
    }
    
    /**
     * 获取会话历史
     */
    fun getConversationHistory(): List<ConversationMessage> {
        return synchronized(conversationHistory) {
            conversationHistory.toList()
        }
    }

    /**
     * 内部状态更新方法
     */
    private fun updateState(
        uiState: VoiceAssistantUIState? = null,
        displayText: String? = null,
        confidence: Float? = null,
        asrText: String? = null,
        ttsText: String? = null,
        result: SimpleResult? = null,
        conversationHistory: List<ConversationMessage>? = null
    ) {
        val previousState = _currentState
        
        _currentState = _currentState.copy(
            uiState = uiState ?: _currentState.uiState,
            displayText = displayText ?: _currentState.displayText,
            confidence = confidence ?: _currentState.confidence,
            asrText = asrText ?: _currentState.asrText,
            ttsText = ttsText ?: _currentState.ttsText,
            result = result ?: _currentState.result,
            conversationHistory = conversationHistory ?: _currentState.conversationHistory,
            timestamp = System.currentTimeMillis()
        )
        
        // 只有状态真正改变时才通知
        if (_currentState != previousState) {
            DebugLogger.logUI(TAG, "🔄 State updated: ${_currentState.uiState}, text: '${_currentState.displayText}'")
            notifyListeners()
        }
    }
    
    /**
     * 通知所有监听器
     */
    private fun notifyListeners() {
        scope.launch {
            listeners.forEach { listener ->
                try {
                    listener(_currentState)
                } catch (e: Exception) {
                    DebugLogger.logUI(TAG, "❌ Error notifying listener: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 便捷方法：获取当前UI状态
     */
    fun getCurrentUIState(): VoiceAssistantUIState = _currentState.uiState
    
    /**
     * 便捷方法：获取当前显示文本
     */
    fun getCurrentDisplayText(): String = _currentState.displayText
    
    /**
     * 便捷方法：获取当前ASR文本
     */
    fun getCurrentASRText(): String = _currentState.asrText
    
    /**
     * 便捷方法：获取当前TTS文本
     */
    fun getCurrentTTSText(): String = _currentState.ttsText
    
    /**
     * 便捷方法：获取当前技能结果
     */
    fun getCurrentResult(): SimpleResult? = _currentState.result
    
    /**
     * 便捷方法：获取当前置信度
     */
    fun getCurrentConfidence(): Float = _currentState.confidence
    
    /**
     * 设置TTS播放完成回调
     */
    private fun setupTTSCompletionCallback() {
        try {
            speechOutputDeviceWrapper.runWhenFinishedSpeaking {
                DebugLogger.logUI(TAG, "🎵 TTS playback completed")
                
                // TTS播放完成，清空TTS文本并回到空闲状态
                updateState(
                    uiState = VoiceAssistantUIState.IDLE,
                    ttsText = "",
                    displayText = ""
                )
            }
        } catch (e: Exception) {
            DebugLogger.logUI(TAG, "❌ Error setting up TTS completion callback: ${e.message}")
            
            // 如果设置回调失败，使用延迟作为备用方案
            scope.launch {
                kotlinx.coroutines.delay(3000) // 3秒后自动恢复到空闲状态
                updateState(
                    uiState = VoiceAssistantUIState.IDLE,
                    ttsText = "",
                    displayText = ""
                )
            }
        }
    }
    
    // ========================================
    // WakeWordCallback 接口实现
    // ========================================
    
    override fun onWakeWordDetected(confidence: Float, wakeWord: String) {
        DebugLogger.logUI(TAG, "🎯 Wake word detected: '$wakeWord' (confidence: $confidence)")
        
        // 更新状态为唤醒检测
        updateState(
            uiState = VoiceAssistantUIState.WAKE_DETECTED,
            displayText = "LISTENING",
            confidence = confidence
        )
        
        // 短暂延迟后转为监听状态
        scope.launch {
            kotlinx.coroutines.delay(500) // 0.5秒后转为监听状态
            updateState(uiState = VoiceAssistantUIState.LISTENING)
        }
    }
    
    override fun onWakeWordListeningStarted() {
        DebugLogger.logUI(TAG, "👂 Wake word listening started")
        updateState(uiState = VoiceAssistantUIState.IDLE, displayText = "")
    }
    
    override fun onWakeWordListeningStopped() {
        DebugLogger.logUI(TAG, "🔇 Wake word listening stopped")
        updateState(uiState = VoiceAssistantUIState.IDLE, displayText = "")
    }
    
    override fun onWakeWordError(error: Throwable) {
        DebugLogger.logUI(TAG, "❌ Wake word error: ${error.message}")
        updateState(
            uiState = VoiceAssistantUIState.ERROR,
            displayText = "ERROR"
        )
        
        // 3秒后自动恢复到空闲状态
        scope.launch {
            kotlinx.coroutines.delay(3000)
            updateState(uiState = VoiceAssistantUIState.IDLE, displayText = "")
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        DebugLogger.logUI(TAG, "🧹 Cleaning up VoiceAssistantStateProvider")
        
        // 取消注册唤醒词回调
        WakeWordCallbackManager.unregisterCallback(this)
        
        // 清空监听器
        listeners.clear()
    }
}
