package com.ai.voice.ui.floating.state

import android.content.Context
import com.ai.voice.ui.floating.VoiceAssistantUIState
import com.ai.voice.util.DebugLogger

/**
 * 悬浮球基类 - 极简版
 * 
 * 核心职责：
 * 1. 自动订阅VoiceAssistantStateProvider的状态变化
 * 2. 提供统一的状态访问接口
 * 3. 简化子类实现，只需要关注状态变化的响应
 * 4. 统一的生命周期管理
 */
abstract class BaseFloatingOrb(protected val context: Context) {
    
    companion object {
        private const val TAG = "BaseFloatingOrb"
    }
    
    // 状态提供者
    protected val stateProvider = VoiceAssistantStateProvider.getInstance()
    
    // 当前状态
    protected var currentState: VoiceAssistantFullState = stateProvider.getCurrentState()
    
    // 状态监听器
    private val stateListener: (VoiceAssistantFullState) -> Unit = { newState ->
        val oldState = currentState
        currentState = newState
        
        DebugLogger.logUI(TAG, "🔄 ${this::class.simpleName} state updated: ${newState.uiState}")
        
        // 调用子类的状态更新方法
        onStateChanged(newState, oldState)
    }
    
    init {
        // 自动注册状态监听
        stateProvider.addListener(stateListener)
        DebugLogger.logUI(TAG, "📡 ${this::class.simpleName} registered for state updates")
    }
    
    /**
     * 状态变化回调 - 子类必须实现
     * 
     * @param newState 新的状态
     * @param oldState 旧的状态
     */
    protected abstract fun onStateChanged(
        newState: VoiceAssistantFullState,
        oldState: VoiceAssistantFullState
    )
    
    /**
     * 显示悬浮球 - 子类必须实现
     */
    abstract fun show()
    
    /**
     * 隐藏悬浮球 - 子类必须实现
     */
    abstract fun hide()
    
    /**
     * 便捷方法：获取当前UI状态
     */
    protected fun getCurrentUIState(): VoiceAssistantUIState {
        return currentState.uiState
    }
    
    /**
     * 便捷方法：获取当前显示文本
     */
    protected fun getCurrentDisplayText(): String {
        return currentState.displayText
    }
    
    /**
     * 便捷方法：获取当前ASR文本
     */
    protected fun getCurrentASRText(): String {
        return currentState.asrText
    }
    
    /**
     * 便捷方法：获取当前TTS文本
     */
    protected fun getCurrentTTSText(): String {
        return currentState.ttsText
    }
    
    /**
     * 便捷方法：获取当前技能结果
     */
    protected fun getCurrentResult(): SimpleResult? {
        return currentState.result
    }
    
    /**
     * 便捷方法：获取当前置信度
     */
    protected fun getCurrentConfidence(): Float {
        return currentState.confidence
    }
    
    /**
     * 便捷方法：检查是否有新的技能结果
     */
    protected fun hasNewResult(oldState: VoiceAssistantFullState): Boolean {
        return currentState.result != null && currentState.result != oldState.result
    }
    
    /**
     * 便捷方法：检查UI状态是否改变
     */
    protected fun hasUIStateChanged(oldState: VoiceAssistantFullState): Boolean {
        return currentState.uiState != oldState.uiState
    }
    
    /**
     * 便捷方法：检查显示文本是否改变
     */
    protected fun hasDisplayTextChanged(oldState: VoiceAssistantFullState): Boolean {
        return currentState.displayText != oldState.displayText
    }
    
    /**
     * 便捷方法：检查ASR文本是否改变
     */
    protected fun hasASRTextChanged(oldState: VoiceAssistantFullState): Boolean {
        return currentState.asrText != oldState.asrText
    }
    
    /**
     * 便捷方法：检查TTS文本是否改变
     */
    protected fun hasTTSTextChanged(oldState: VoiceAssistantFullState): Boolean {
        return currentState.ttsText != oldState.ttsText
    }
    
    /**
     * 便捷方法：检查是否处于活跃状态
     */
    protected fun isActive(): Boolean {
        return when (currentState.uiState) {
            VoiceAssistantUIState.LISTENING,
            VoiceAssistantUIState.THINKING,
            VoiceAssistantUIState.SPEAKING,
            VoiceAssistantUIState.WAKE_DETECTED -> true
            else -> false
        }
    }
    
    /**
     * 便捷方法：检查是否处于错误状态
     */
    protected fun isError(): Boolean {
        return currentState.uiState == VoiceAssistantUIState.ERROR
    }
    
    /**
     * 便捷方法：检查是否处于空闲状态
     */
    protected fun isIdle(): Boolean {
        return currentState.uiState == VoiceAssistantUIState.IDLE
    }
    
    /**
     * 清理资源 - 子类可以重写进行额外清理
     */
    open fun cleanup() {
        DebugLogger.logUI(TAG, "🧹 ${this::class.simpleName} cleaning up")
        stateProvider.removeListener(stateListener)
    }
    
    /**
     * 获取悬浮球类型名称 - 用于调试
     */
    protected fun getOrbTypeName(): String {
        return this::class.simpleName ?: "UnknownOrb"
    }
    
    /**
     * 记录状态变化日志 - 便捷方法
     */
    protected fun logStateChange(message: String) {
        DebugLogger.logUI(TAG, "🎯 ${getOrbTypeName()}: $message")
    }
    
    /**
     * 记录错误日志 - 便捷方法
     */
    protected fun logError(message: String, throwable: Throwable? = null) {
        DebugLogger.logUI(TAG, "❌ ${getOrbTypeName()}: $message ${throwable?.message ?: ""}")
    }
}
