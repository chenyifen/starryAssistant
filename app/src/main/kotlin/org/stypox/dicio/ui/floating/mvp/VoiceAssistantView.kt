package org.stypox.dicio.ui.floating.mvp

/**
 * 语音助手视图接口
 * 
 * 定义了所有语音助手UI需要实现的基本视图操作
 * 不同的UI实现（悬浮球、悬浮窗口）都需要实现这个接口
 */
interface VoiceAssistantView {
    
    /**
     * 显示视图
     */
    fun show()
    
    /**
     * 隐藏视图
     */
    fun hide()
    
    /**
     * 更新语音助手状态
     * @param state 新的状态
     * @param displayText 要显示的文本（可选）
     */
    fun updateVoiceState(state: VoiceAssistantUIState, displayText: String = "")
    
    /**
     * 显示唤醒动画
     * @param wakeWord 检测到的唤醒词
     */
    fun showWakeWordAnimation(wakeWord: String)
    
    /**
     * 显示监听状态
     * @param isListening 是否正在监听
     */
    fun showListeningState(isListening: Boolean)
    
    /**
     * 显示识别结果
     * @param text 识别到的文本
     * @param isPartial 是否为部分结果
     */
    fun showRecognitionResult(text: String, isPartial: Boolean)
    
    /**
     * 显示思考状态
     * @param isThinking 是否正在思考
     */
    fun showThinkingState(isThinking: Boolean)
    
    /**
     * 显示回复
     * @param response 回复文本
     */
    fun showResponse(response: String)
    
    /**
     * 显示错误状态
     * @param error 错误信息
     */
    fun showError(error: String)
    
    /**
     * 清除显示内容
     */
    fun clearDisplay()
    
    /**
     * 设置点击监听器
     * @param listener 点击事件监听器
     */
    fun setOnClickListener(listener: () -> Unit)
    
    /**
     * 设置长按监听器
     * @param listener 长按事件监听器
     */
    fun setOnLongClickListener(listener: () -> Unit)
    
    /**
     * 获取视图类型
     */
    fun getViewType(): VoiceAssistantViewType
}

/**
 * 语音助手视图类型
 */
enum class VoiceAssistantViewType {
    /** 悬浮球视图 */
    FLOATING_ORB,
    
    /** 悬浮窗口视图 */
    FLOATING_WINDOW,
    
    /** 全屏视图 */
    FULL_SCREEN
}

/**
 * 统一的语音助手UI状态
 * 
 * 这个枚举被所有UI实现共享
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
