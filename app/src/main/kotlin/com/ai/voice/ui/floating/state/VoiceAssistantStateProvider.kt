package com.ai.voice.ui.floating.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.dicio.skill.skill.SkillOutput
import com.ai.voice.di.SpeechOutputDeviceWrapper
// import com.ai.voice.di.SttInputDeviceWrapper // 🔧 已禁用：不再使用，改用 AsrHandler
import com.ai.voice.di.SkillContextInternal
import com.ai.voice.eval.SkillEvaluator
import com.ai.voice.io.input.InputEvent
import com.ai.voice.io.input.SttState
import com.ai.voice.io.wake.WakeWordCallback
import com.ai.voice.io.wake.WakeWordCallbackManager
import com.ai.voice.ui.floating.VoiceAssistantUIState
import com.ai.voice.ui.home.InteractionLog
import com.ai.voice.util.DebugLogger
import com.ai.voice.util.AsrHandler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 语音助手状态提供者 - 统一状态管理中心
 * 
 * 核心职责：
 * 1. 统一管理语音助手的完整状态
 * 2. 提供全局访问点，任何UI组件都可以获取当前状态
 * 3. 支持状态监听，UI组件可以响应状态变化
 * 4. 直接监听底层服务（STT、SkillEvaluator、WakeWord）
 */
@Singleton
class VoiceAssistantStateProvider @Inject constructor(
    // private val sttInputDeviceWrapper: SttInputDeviceWrapper, // 🔧 已禁用：不再使用，改用 AsrHandler
    private val skillEvaluator: SkillEvaluator,
    private val speechOutputDeviceWrapper: SpeechOutputDeviceWrapper,
    private val skillContext: SkillContextInternal
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
    
    // 性能优化：ASR文本去重
    private var lastAsrText = ""
    private var lastTtsText = ""
    
    // 性能优化：状态变化类型
    enum class StateChangeType {
        ASR_TEXT_ONLY,      // 仅ASR文本变化 - 轻量更新
        TTS_TEXT_ONLY,      // 仅TTS文本变化 - 轻量更新  
        UI_STATE_CHANGE,    // UI状态变化 - 完整更新
        MIXED_CHANGE        // 混合变化 - 完整更新
    }
    
    init {
        // 初始化全局实例
        initialize(this)
        
        // 注册唤醒词回调
        WakeWordCallbackManager.registerCallback(this)
        
        // 直接监听底层服务
        observeServices()
    }
    
    /**
     * 直接监听底层服务
     */
    private fun observeServices() {
        // 🔧 已禁用：不再监听 sttInputDeviceWrapper，改用 AsrHandler
        // 1. 监听STT状态变化
        // scope.launch {
        //     sttInputDeviceWrapper.uiState.collect { sttState ->
        //         handleSttStateChange(sttState)
        //     }
        // }
        
        // 🔧 改为监听 AsrHandler 状态变化
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(200) // 每200ms检查一次
                val isAsrStarted = AsrHandler.isStarted()
                if (isAsrStarted) {
                    // AsrHandler 正在运行，设置为 LISTENING 状态
                    if (_currentState.uiState != VoiceAssistantUIState.LISTENING) {
                        updateState(uiState = VoiceAssistantUIState.LISTENING, displayText = "LISTENING")
                    }
                } else {
                    // AsrHandler 已停止，如果没有其他活动，设置为 IDLE
                    if (_currentState.uiState == VoiceAssistantUIState.LISTENING) {
                        updateState(uiState = VoiceAssistantUIState.IDLE, displayText = "")
                    }
                }
            }
        }
        
        // 2. 监听SkillEvaluator的InputEvent来获取ASR实时文本
        scope.launch {
            skillEvaluator.inputEvents.collect { inputEvent ->
                handleInputEvent(inputEvent)
            }
        }
        
        // 3. 监听SkillEvaluator的状态变化来获取技能结果
        scope.launch {
            skillEvaluator.state.collect { interactionLog ->
                handleSkillEvaluatorState(interactionLog)
            }
        }
    }
    
    /**
     * 处理STT状态变化
     */
    private fun handleSttStateChange(sttState: SttState?) {
        when (sttState) {
            is SttState.Loaded -> {
                DebugLogger.logUI(TAG, "😴 STT device loaded and ready")
                // 🆕 修复：如果ASR正在监听，不应该强制设置为IDLE
                // 只有在确实没有监听时才设置为IDLE（10秒静音超时的情况）
                // SttState.Loaded只是表示设备准备就绪，不代表停止监听
                if (_currentState.uiState == VoiceAssistantUIState.IDLE || 
                    _currentState.uiState == VoiceAssistantUIState.ERROR) {
                    // 只有在IDLE或ERROR状态时才更新，保持LISTENING状态不变
                    updateState(uiState = VoiceAssistantUIState.IDLE, displayText = "")
                } else {
                    DebugLogger.logUI(TAG, "⏭️ STT设备已就绪，但ASR仍在监听，保持LISTENING状态")
                }
            }
            
            is SttState.Listening -> {
                DebugLogger.logUI(TAG, "🎧 STT device listening")
                // 🆕 确保设置为LISTENING状态，无论当前是什么状态
                updateState(uiState = VoiceAssistantUIState.LISTENING, displayText = "LISTENING")
            }
            
            is SttState.Loading -> {
                DebugLogger.logUI(TAG, "⏳ STT device loading")
                // 🆕 THINKING状态不影响ASR监听，只是UI显示
                updateState(uiState = VoiceAssistantUIState.THINKING, displayText = "")
            }
            
            is SttState.NotAvailable -> {
                DebugLogger.logUI(TAG, "❌ STT device not available")
                updateState(uiState = VoiceAssistantUIState.ERROR, displayText = "ERROR")
            }
            
            is SttState.ErrorLoading -> {
                val errorMessage = sttState.throwable.message ?: ""
                if (errorMessage.contains("was cancelled", ignoreCase = true)) {
                    DebugLogger.logUI(TAG, "⚠️ STT device loading cancelled (normal), returning to IDLE")
                    updateState(uiState = VoiceAssistantUIState.IDLE, displayText = "")
                } else {
                    DebugLogger.logUI(TAG, "❌ STT device loading error: $errorMessage")
                    updateState(uiState = VoiceAssistantUIState.ERROR, displayText = "ERROR")
                }
            }
            
            is SttState.ErrorDownloading -> {
                DebugLogger.logUI(TAG, "❌ STT device download error: ${sttState.throwable.message}")
                updateState(uiState = VoiceAssistantUIState.ERROR, displayText = "ERROR")
            }
            
            is SttState.ErrorUnzipping -> {
                DebugLogger.logUI(TAG, "❌ STT device unzip error: ${sttState.throwable.message}")
                updateState(uiState = VoiceAssistantUIState.ERROR, displayText = "ERROR")
            }
            
            is SttState.WaitingForResult -> {
                DebugLogger.logUI(TAG, "⏳ STT waiting for external result")
                // 🆕 保持LISTENING状态
                updateState(uiState = VoiceAssistantUIState.LISTENING, displayText = "LISTENING")
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
     * 处理InputEvent - 主要用于ASR实时文本更新
     */
    private fun handleInputEvent(inputEvent: InputEvent) {
        when (inputEvent) {
            is InputEvent.Partial -> {
                val receiveTime = System.currentTimeMillis()
                
                // 性能优化：ASR文本去重，相同文本不触发更新
                if (inputEvent.utterance != lastAsrText) {
                    lastAsrText = inputEvent.utterance
                    val textLength = inputEvent.utterance.length
                    
                    DebugLogger.logRecognition(TAG, "📨 收到Partial事件 - 时间戳: $receiveTime, 文本长度: $textLength")
                    DebugLogger.logRecognition(TAG, "   内容: '${inputEvent.utterance}'")
                    
                    val updateStartTime = System.currentTimeMillis()
                    updateState(asrText = inputEvent.utterance)
                    val updateDuration = System.currentTimeMillis() - updateStartTime
                    
                    DebugLogger.logRecognition(TAG, "✅ State更新完成 - 耗时: ${updateDuration}ms")
                } else {
                    DebugLogger.logRecognition(TAG, "⏭️ ASR文本未变化，跳过更新: ${inputEvent.utterance}")
                }
            }
            
            is InputEvent.Final -> {
                val bestResult = inputEvent.utterances.firstOrNull()?.first ?: ""
                val confidence = inputEvent.utterances.firstOrNull()?.second ?: 0f
                DebugLogger.logUI(TAG, "✅ ASR final result: $bestResult (confidence: $confidence)")
                
                // 🆕 立即更新UI显示Final识别结果，不延迟
                updateState(asrText = bestResult, confidence = confidence)
                
                // 🆕 检测ASR识别的语言并设置TTS语言
                if (bestResult.isNotBlank()) {
                    val asrLocale = com.ai.voice.util.LanguageDetector.detectLocale(bestResult, java.util.Locale.KOREAN)
                    DebugLogger.logUI(TAG, "🎤 ASR识别语言: ${com.ai.voice.util.LanguageDetector.getLocaleName(asrLocale)}")
                    speechOutputDeviceWrapper.setAsrLocale(asrLocale)
                    // 🆕 设置到SkillContext，让技能可以获取ASR语言
                    skillContext.asrLocale = asrLocale
                } else {
                    // 清空ASR语言设置
                    speechOutputDeviceWrapper.setAsrLocale(null)
                    skillContext.asrLocale = null
                }
                
                // 添加用户消息到会话历史
                if (bestResult.isNotBlank()) {
                    addUserMessage(bestResult, confidence)
                }
                
                // 🆕 不清空ASR文本，保留显示，直到用户继续说话时新的Partial结果覆盖它
                // 或者等待10秒静音超时再清空
                // 这样用户可以连续说话时看到所有识别结果
            }
            
            is InputEvent.Error -> {
                DebugLogger.logUI(TAG, "❌ ASR error: ${inputEvent.throwable.message}")
                updateState(asrText = "")
            }
            
            InputEvent.None -> {
                DebugLogger.logUI(TAG, "🔇 No speech detected (10秒静音超时，停止监听)")
                // 🆕 只有10秒静音超时时才会收到None事件，此时停止STT并回到待唤醒
                updateState(asrText = "")
                // 🆕 清空ASR语言设置
                speechOutputDeviceWrapper.setAsrLocale(null)
                skillContext.asrLocale = null
                
                // 🔥 关键修复：确保状态切换到IDLE
                updateState(
                    uiState = VoiceAssistantUIState.IDLE,
                    displayText = "",
                    ttsText = ""
                )
                
                try {
                    // 🔧 已禁用：不再使用 sttInputDeviceWrapper，改用 AsrHandler
                    // sttInputDeviceWrapper.stopListening()
                    DebugLogger.logUI(TAG, "⏭️ 跳过停止STT监听（已改用 AsrHandler）")
                } catch (e: Exception) {
                    DebugLogger.logUI(TAG, "⚠️ 停止STT失败: ${e.message}")
                }
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
            
            // 🆕 检查是否是fallback技能（识别不出具体命令）
            val skillInfo = lastInteraction?.skill
            val isFallbackSkill = skillInfo?.id == "text"
            
            if (isFallbackSkill) {
                DebugLogger.logUI(TAG, "⏭️ 识别不出具体命令（fallback），不设置SPEAKING状态，不播放TTS")
                // fallback时不设置SPEAKING状态，不播放TTS，保持LISTENING状态
                return
            }
            
            // 将技能输出转换为SimpleResult
            val simpleResult = convertSkillOutputToSimpleResult(lastAnswer)
            updateState(result = simpleResult)
            
            // 获取TTS文本并添加AI回复到会话历史
            try {
                val speechOutput = lastAnswer.getSpeechOutput(skillContext)
                DebugLogger.logUI(TAG, "🗣️ [DEBUG] getSpeechOutput() 返回: '$speechOutput'")
                
                if (speechOutput.isNotBlank()) {
                    // 🆕 设置SPEAKING状态，但不影响ASR监听（ASR继续在后台监听）
                    updateState(
                        uiState = VoiceAssistantUIState.SPEAKING,
                        ttsText = speechOutput,
                        displayText = "SPEAKING"
                    )
                    addAIMessage(speechOutput)
                    
                    // ⚠️ 注意：这里不需要再次调用 speak()，因为 SkillEvaluator 已经调用了
                    // 🆕 关键：TTS播放不影响ASR监听，ASR会继续监听直到10秒静音
                    setupTTSCompletionCallback()
                    
                    DebugLogger.logUI(TAG, "🗣️ [DEBUG] TTS 文本已设置，ASR继续监听...")
                } else {
                    DebugLogger.logUI(TAG, "⚠️ [DEBUG] speechOutput 为空，跳过TTS")
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
            val speechText = skillOutput.getSpeechOutput(skillContext)
            
            DebugLogger.logUI(TAG, "🔄 Converting skill output: $skillClassName")
            
            // 根据具体的技能输出类型创建相应的SimpleResult
            // 首先尝试精确匹配已知的技能输出类
            when (skillOutput) {
                // 天气技能 - 精确匹配
                is com.ai.voice.skills.weather.WeatherOutput.Success -> {
                    SimpleResultBuilder.weather(
                        location = skillOutput.city,
                        temperature = skillOutput.temp.toInt(),
                        condition = skillOutput.description
                    )
                }
                is com.ai.voice.skills.weather.WeatherOutput.Failed -> {
                    SimpleResultBuilder.error("无法获取${skillOutput.city}的天气信息")
                }
                
                // 时间技能 - 精确匹配
                is com.ai.voice.skills.current_time.CurrentTimeOutput -> {
                    SimpleResultBuilder.time(speechText)
                }
                
                // 计算器技能 - 精确匹配
                is com.ai.voice.skills.calculator.CalculatorOutput -> {
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
                is com.ai.voice.skills.navigation.NavigationOutput -> {
                    SimpleResultBuilder.appAction("导航", "地图导航", speechText.contains("导航"))
                }
                
                // 媒体控制技能 - 精确匹配
                is com.ai.voice.skills.media.MediaOutput -> {
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
                is com.ai.voice.skills.open.OpenOutput -> {
                    val success = !speechText.contains("无法") && !speechText.contains("未知")
                    SimpleResultBuilder.appAction("应用", "打开应用", success)
                }
                
                // 电话技能已移除
                
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
        
        // 🔥 修复：防止在SPEAKING状态时被意外覆盖成IDLE
        // 如果当前是SPEAKING状态，且新状态想要切换到IDLE，但TTS文本还在，则忽略此次更新
        // 但是如果明确要清空ttsText（ttsText参数为""），则允许更新（这是TTS播放完成的正常流程）
        if (_currentState.uiState == VoiceAssistantUIState.SPEAKING && 
            uiState == VoiceAssistantUIState.IDLE &&
            _currentState.ttsText.isNotBlank() &&
            ttsText != "") {  // 新增：如果明确要清空ttsText，则允许更新
            DebugLogger.logUI(TAG, "🛡️ 防止状态覆盖：当前SPEAKING状态且TTS文本存在，忽略转到IDLE的请求")
            return
        }
        
        // 修复：状态一致性检查
        val finalUiState = uiState ?: _currentState.uiState
        val finalDisplayText = when {
            displayText != null -> displayText
            // IDLE状态强制清空displayText
            finalUiState == VoiceAssistantUIState.IDLE && _currentState.displayText.isNotEmpty() -> ""
            else -> _currentState.displayText
        }
        
        _currentState = _currentState.copy(
            uiState = finalUiState,
            displayText = finalDisplayText,
            confidence = confidence ?: _currentState.confidence,
            asrText = asrText ?: _currentState.asrText,
            ttsText = ttsText ?: _currentState.ttsText,
            result = result ?: _currentState.result,
            conversationHistory = conversationHistory ?: _currentState.conversationHistory,
            timestamp = System.currentTimeMillis()
        )
        
        // 修复：一致性验证
        if (_currentState.uiState == VoiceAssistantUIState.IDLE && 
            _currentState.displayText.isNotEmpty()) {
            DebugLogger.logUI(TAG, "⚠️ 状态不一致：IDLE 但 displayText='${_currentState.displayText}'，已自动修正")
            _currentState = _currentState.copy(displayText = "")
        }
        
        // 只有状态真正改变时才通知（忽略timestamp字段）
        if (hasSignificantChange(previousState, _currentState)) {
            // 性能优化：分析变化类型，选择通知策略
            val changeType = analyzeStateChange(previousState, _currentState)
            DebugLogger.logUI(TAG, "🔄 State updated: ${_currentState.uiState}, text: '${_currentState.displayText}', changeType: $changeType")
            
            when (changeType) {
                StateChangeType.ASR_TEXT_ONLY, StateChangeType.TTS_TEXT_ONLY -> {
                    // 轻量级通知：仅文本变化，直接在主线程调用
                    notifyListenersLight()
                }
                else -> {
                    // 完整通知：UI状态变化，使用协程
                    notifyListeners()
                }
            }
        } else {
            DebugLogger.logUI(TAG, "⏭️ No significant state change, skipping notification")
        }
    }
    
    /**
     * 检查是否有重要的状态变化（忽略timestamp）
     */
    private fun hasSignificantChange(oldState: VoiceAssistantFullState, newState: VoiceAssistantFullState): Boolean {
        return oldState.uiState != newState.uiState ||
                oldState.displayText != newState.displayText ||
                oldState.confidence != newState.confidence ||
                oldState.asrText != newState.asrText ||
                oldState.ttsText != newState.ttsText ||
                oldState.result != newState.result ||
                oldState.conversationHistory != newState.conversationHistory
    }
    
    /**
     * 分析状态变化类型
     */
    private fun analyzeStateChange(oldState: VoiceAssistantFullState, newState: VoiceAssistantFullState): StateChangeType {
        val uiStateChanged = oldState.uiState != newState.uiState
        val displayTextChanged = oldState.displayText != newState.displayText
        val asrTextChanged = oldState.asrText != newState.asrText
        val ttsTextChanged = oldState.ttsText != newState.ttsText
        val resultChanged = oldState.result != newState.result
        val historyChanged = oldState.conversationHistory != newState.conversationHistory
        
        return when {
            uiStateChanged || displayTextChanged || resultChanged || historyChanged -> StateChangeType.UI_STATE_CHANGE
            asrTextChanged && !ttsTextChanged -> StateChangeType.ASR_TEXT_ONLY
            ttsTextChanged && !asrTextChanged -> StateChangeType.TTS_TEXT_ONLY
            asrTextChanged && ttsTextChanged -> StateChangeType.MIXED_CHANGE
            else -> StateChangeType.UI_STATE_CHANGE // 默认完整更新
        }
    }
    
    /**
     * 轻量级通知：直接在主线程调用，避免协程开销
     */
    private fun notifyListenersLight() {
        listeners.forEach { listener ->
            try {
                listener(_currentState)
            } catch (e: Exception) {
                DebugLogger.logUI(TAG, "❌ Error notifying listener (light): ${e.message}")
            }
        }
    }
    
    /**
     * 完整通知：使用协程处理复杂状态变化
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
     * 🔥 关键修复：TTS播放完成不影响ASR监听，ASR继续监听直到10秒静音超时
     */
    private fun setupTTSCompletionCallback() {
        try {
            speechOutputDeviceWrapper.runWhenFinishedSpeaking {
                DebugLogger.logUI(TAG, "🎵 TTS playback completed")
                
                // 🔥 关键修复：TTS播放完成不影响ASR监听状态
                // ASR会继续监听直到10秒静音超时（InputEvent.None）才会停止
                scope.launch {
                    delay(1000) // 延迟1秒后清空TTS文本
                    
                // 🆕 检查ASR是否仍在监听（使用 AsrHandler）
                val isAsrStarted = AsrHandler.isStarted()
                
                if (isAsrStarted) {
                    // ASR仍在监听，只清空TTS文本，保持LISTENING状态
                    updateState(
                        uiState = VoiceAssistantUIState.LISTENING,
                        ttsText = "",
                        displayText = "LISTENING"
                    )
                    DebugLogger.logUI(TAG, "🔄 TTS播放完成，ASR仍在监听，保持LISTENING状态")
                } else {
                    // ASR已停止（10秒静音超时），切换到IDLE
                    updateState(
                        uiState = VoiceAssistantUIState.IDLE,
                        ttsText = "",
                        displayText = ""
                    )
                    DebugLogger.logUI(TAG, "🧹 TTS播放完成，ASR已停止，切换回IDLE状态")
                }
                }
            }
        } catch (e: Exception) {
            DebugLogger.logUI(TAG, "⚠️ 设置TTS完成回调失败: ${e.message}")
            // 如果设置失败，使用延迟清理作为备用方案
            scope.launch {
                delay(2000)
                val isAsrStarted = AsrHandler.isStarted()
                if (isAsrStarted) {
                    updateState(
                        uiState = VoiceAssistantUIState.LISTENING,
                        ttsText = "",
                        displayText = "LISTENING"
                    )
                } else {
                    updateState(
                        uiState = VoiceAssistantUIState.IDLE,
                        ttsText = "",
                        displayText = ""
                    )
                }
            }
        }
    }
    
    // ========================================
    // WakeWordCallback 接口实现
    // ========================================
    
    override fun onWakeWordDetected(confidence: Float, wakeWord: String) {
        DebugLogger.logUI(TAG, "🎯 Wake word detected: '$wakeWord' (confidence: $confidence)")
        
        // 修复：直接设置为LISTENING，不需要延迟
        // WAKE_DETECTED状态太短暂，容易导致状态不一致
        updateState(
            uiState = VoiceAssistantUIState.LISTENING,
            displayText = "LISTENING",
            confidence = confidence
        )
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
