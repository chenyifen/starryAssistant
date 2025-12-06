package com.ai.voice.ui.floating.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.dicio.skill.skill.SkillOutput
import com.ai.voice.di.SpeechOutputDeviceWrapper
import com.ai.voice.di.SkillContextInternal
import com.ai.voice.eval.SkillEvaluator
import com.ai.voice.eval.InteractionLog

import com.ai.voice.io.wake.WakeWordCallback
import com.ai.voice.io.wake.WakeWordCallbackManager
import com.ai.voice.ui.floating.VoiceAssistantUIState
import com.ai.voice.util.DebugLogger
import com.ai.voice.util.AsrHandler
import com.ai.voice.util.AutoTestLogger
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
    private val skillEvaluator: SkillEvaluator,
    private val speechOutputDeviceWrapper: SpeechOutputDeviceWrapper,
    private val skillContext: SkillContextInternal
) : WakeWordCallback {
    
    companion object {
        private const val TAG = "VoiceAssistantStateProvider"
        private const val NOTIFY_SERVER_URL = "http://192.168.2.8:8080/api/notify_state"
        
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
    
    private fun notifyStateChange(event: String, data: Map<String, Any> = emptyMap()) {
        scope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("event", event)
                    put("timestamp", System.currentTimeMillis())
                    data.forEach { (key, value) ->
                        when (value) {
                            is String -> put(key, value)
                            is Int -> put(key, value)
                            is Long -> put(key, value)
                            is Boolean -> put(key, value)
                            is Float -> put(key, value.toDouble())
                            is Double -> put(key, value)
                            else -> put(key, value.toString())
                        }
                    }
                }
                
                val url = URL(NOTIFY_SERVER_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.doOutput = true
                connection.connectTimeout = 2000
                connection.readTimeout = 2000
                
                connection.outputStream.use { output ->
                    output.write(json.toString().toByteArray(Charsets.UTF_8))
                }
                
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    DebugLogger.logUI(TAG, "[NOTIFY] State change notified: $event")
                } else {
                    DebugLogger.logUI(TAG, "[NOTIFY] Failed to notify state change: $event, response code: $responseCode")
                }
                connection.disconnect()
            } catch (e: Exception) {
                DebugLogger.logUI(TAG, "[NOTIFY] Error notifying state change: ${e.message}")
            }
        }
    }
    
    // 当前状态
    private var _currentState = VoiceAssistantFullState.IDLE
    
    // 🔒 状态转换互斥锁 - 保证线程安全
    private val stateTransitionMutex = Mutex()
    
    // 状态监听器
    private val listeners = mutableSetOf<(VoiceAssistantFullState) -> Unit>()
    
    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.Main)
    
    // 会话历史管理
    private val conversationHistory = mutableListOf<ConversationMessage>()
    private val maxHistorySize = 50 // 最多保留50条对话记录
    
    private var listeningStartTime: Long = 0
    private var lastAsrFinalTime: Long = 0
    private var stateMonitorJob: kotlinx.coroutines.Job? = null
    
    init {
        // 初始化全局实例
        initialize(this)
        
        // 注册唤醒词回调
        WakeWordCallbackManager.registerCallback(this)
        
        // 初始化时启动WakeService
        scope.launch {
            transitionToState(VoiceAssistantUIState.IDLE, reason = "初始化")
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
     * 🔥 统一的状态转换方法 - 确保UI状态和功能状态完全同步
     * 
     * 🎯 简化状态机：只有两种状态
     * - IDLE: Wake运行 + ASR停止 + 待唤醒
     * - LISTENING: Wake暂停 + ASR运行 + 语音识别中
     * 
     * 🔒 线程安全：使用 Mutex 保证状态转换的原子性，避免竞态条件
     * 
     * @param targetState 目标UI状态（只接受 IDLE 或 LISTENING）
     * @param reason 状态转换原因（用于日志）
     */
    suspend fun transitionToState(targetState: VoiceAssistantUIState, reason: String = "") {
        // 🔒 使用 Mutex 确保同一时间只有一个状态转换在执行
        stateTransitionMutex.withLock {
            val currentUIState = _currentState.uiState
            
            val normalizedTargetState = targetState

//            if (currentUIState == normalizedTargetState) {
//                DebugLogger.logUI(TAG, "⏭️ 状态已经是 $normalizedTargetState，跳过转换")
//                return
//            }
            
            val reasonLog = if (reason.isNotBlank()) " (原因: $reason)" else ""
            DebugLogger.logUI(TAG, "🔄 [STATE_TRANSITION] $currentUIState → $normalizedTargetState$reasonLog [线程: ${Thread.currentThread().name}]")
            AutoTestLogger.logStateChange(currentUIState.name, normalizedTargetState.name, AsrHandler.isStarted())
            
            // 🔥 在 Main dispatcher 中执行状态转换，确保线程一致性
            withContext(Dispatchers.Main) {
                val context = skillContext.android
                
                // 🔥 根据目标状态设置对应的功能
                when (normalizedTargetState) {
                    VoiceAssistantUIState.IDLE -> {
                        DebugLogger.logUI(TAG, "  ├─ 目标状态: IDLE (待唤醒)")
                        
                        // 1. 停止ASR（如果正在运行）
                        try {
                            if (AsrHandler.isStarted()) {
                                DebugLogger.logUI(TAG, "  ├─ [功能] 停止 ASR")
                                AsrHandler.stop(context)
                            } else {
                                DebugLogger.logUI(TAG, "  ├─ [功能] ASR 已停止")
                            }
                        } catch (e: Exception) {
                            DebugLogger.logUI(TAG, "  ├─ ❌ 停止ASR失败: ${e.message}")
                        }
                        
                        // 2. 启动WakeService（如果未运行）
                        try {
                            if (!com.ai.voice.io.wake.WakeService.isRunning()) {
                                DebugLogger.logUI(TAG, "  ├─ [功能] 启动 WakeService")
                                com.ai.voice.io.wake.WakeService.start(context)
                            } else {
                                DebugLogger.logUI(TAG, "  ├─ [功能] WakeService 已运行")
                            }
                        } catch (e: Exception) {
                            DebugLogger.logUI(TAG, "  ├─ ❌ 启动WakeService失败: ${e.message}")
                        }
                        
                        // 3. 更新UI状态
                        DebugLogger.logUI(TAG, "  ├─ [UI] 设置为 IDLE")
                        updateState(
                            uiState = VoiceAssistantUIState.IDLE,
                            displayText = "",
                            ttsText = ""
                        )
                        
                        DebugLogger.logUI(TAG, "  └─ ✅ IDLE 状态转换完成 [ASR:停止, Wake:运行, UI:IDLE]")
                    }
                    
                    VoiceAssistantUIState.LISTENING -> {
                        DebugLogger.logUI(TAG, "  ├─ 目标状态: LISTENING (语音识别中)")
                        
                        // 1. WakeService 会自动暂停（在其内部检测到 AsrHandler.isStarted()）
                        DebugLogger.logUI(TAG, "  ├─ [功能] WakeService 将自动暂停")
                        
                        // 2. 启动ASR（如果未运行）
                        try {
                            if (!AsrHandler.isStarted()) {
                                DebugLogger.logUI(TAG, "  ├─ [功能] 启动 ASR")
                                AsrHandler.start(context)
                            } else {
                                DebugLogger.logUI(TAG, "  ├─ [功能] ASR 已运行")
                            }
                        } catch (e: Exception) {
                            DebugLogger.logUI(TAG, "  ├─ ❌ 启动ASR失败: ${e.message}")
                        }
                        
                        // 3. 更新UI状态
                        DebugLogger.logUI(TAG, "  ├─ [UI] 设置为 LISTENING")
                        updateState(
                            uiState = VoiceAssistantUIState.LISTENING,
                            displayText = "LISTENING"
                        )
                        
                        DebugLogger.logUI(TAG, "  └─ ✅ LISTENING 状态转换完成 [ASR:运行, Wake:暂停, UI:LISTENING]")
                    }
                    
                    else -> {
                        DebugLogger.logUI(TAG, "  └─ ⚠️ 未知状态: $normalizedTargetState，默认转换为 IDLE")
                        // 未知状态默认转换为 IDLE
                        transitionToState(VoiceAssistantUIState.IDLE, reason = "未知状态: $normalizedTargetState")
                    }
                }
                
                // 通知状态变化
                notifyStateChange("state_transition", mapOf(
                    "fromState" to currentUIState.name,
                    "toState" to normalizedTargetState.name,
                    "reason" to reason,
                    "asrRunning" to AsrHandler.isStarted(),
                    "wakeRunning" to com.ai.voice.io.wake.WakeService.isRunning()
                ))
            }
        }
    }
    
    /**
     * @deprecated 使用 transitionToState 替代，确保状态一致性
     */
    @Deprecated("Use transitionToState instead", ReplaceWith("transitionToState(uiState)"))
    fun updateUIState(uiState: VoiceAssistantUIState) {
        updateState(uiState = uiState)
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
        
        if (hasSignificantChange(previousState, _currentState)) {
            DebugLogger.logUI(TAG, "🔄 State updated: ${_currentState.uiState}, text: '${_currentState.displayText}'")
            
            // AutoTest: 记录关键状态变化
            if (previousState.uiState != _currentState.uiState) {
                AutoTestLogger.logStateChange(
                    previousState.uiState.name,
                    _currentState.uiState.name,
                    AsrHandler.isStarted()
                )
                
                notifyStateChange("state_changed", mapOf(
                    "fromState" to previousState.uiState.name,
                    "toState" to _currentState.uiState.name,
                    "asrRunning" to AsrHandler.isStarted(),
                    "displayText" to _currentState.displayText
                ))
            }
            
            notifyListeners()
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
    
    private fun setupTTSCompletionCallback() {
        speechOutputDeviceWrapper.runWhenFinishedSpeaking {
        }
    }
    
    // ========================================
    // WakeWordCallback 接口实现
    // ========================================
    
    override fun onWakeWordDetected(confidence: Float, wakeWord: String) {
        DebugLogger.logUI(TAG, "🎯 Wake word detected: '$wakeWord' (confidence: $confidence)")
        
        // 🔥 使用统一的状态转换方法，自动启动ASR
        scope.launch {
            transitionToState(VoiceAssistantUIState.LISTENING, reason = "唤醒词检测: $wakeWord")
        }
        
        // 更新置信度
        updateState(confidence = confidence)
    }
    
    override fun onWakeWordListeningStarted() {
        DebugLogger.logUI(TAG, "👂 Wake word listening started")
    }
    
    override fun onWakeWordListeningStopped() {
        DebugLogger.logUI(TAG, "🔇 Wake word listening stopped")
    }
    
    override fun onWakeWordError(error: Throwable) {
        DebugLogger.logUI(TAG, "❌ Wake word error: ${error.message}")
        scope.launch {
            transitionToState(VoiceAssistantUIState.IDLE, reason = "唤醒词错误: ${error.message}")
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        DebugLogger.logUI(TAG, "🧹 Cleaning up VoiceAssistantStateProvider")
        
        stateMonitorJob?.cancel()
        stateMonitorJob = null
        
        // 取消注册唤醒词回调
        WakeWordCallbackManager.unregisterCallback(this)
        
        // 清空监听器
        listeners.clear()
    }
}
