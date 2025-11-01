package com.ai.voice.ui.floating

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

