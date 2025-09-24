package org.stypox.dicio.ui.floating.state

import org.stypox.dicio.ui.floating.VoiceAssistantUIState

/**
 * 语音助手完整状态 - 极简版
 * 
 * 包含UI层需要的所有核心状态信息，用于在VoiceAssistantStateProvider
 * 和各个悬浮球UI之间传递状态数据
 */
data class VoiceAssistantFullState(
    // 基础状态
    val uiState: VoiceAssistantUIState,
    val displayText: String,
    val confidence: Float,
    val timestamp: Long,
    
    // 实时文本
    val asrText: String,           // 当前ASR识别文本
    val ttsText: String,           // 当前TTS播放文本
    
    // 技能结果 - 只保留最核心的
    val result: SimpleResult?,
    
    // 会话历史
    val conversationHistory: List<ConversationMessage>
) {
    companion object {
        val IDLE = VoiceAssistantFullState(
            uiState = VoiceAssistantUIState.IDLE,
            displayText = "",
            confidence = 0f,
            timestamp = System.currentTimeMillis(),
            asrText = "",
            ttsText = "",
            result = null,
            conversationHistory = emptyList()
        )
    }
}

/**
 * 会话消息
 * 
 * 记录用户输入和AI回复的完整对话历史
 */
data class ConversationMessage(
    val text: String,              // 消息文本
    val isUser: Boolean,           // 是否为用户消息
    val timestamp: Long,           // 时间戳
    val confidence: Float = 0f     // 置信度（仅对用户消息有效）
)

/**
 * 简单结果 - 只包含必要信息
 * 
 * 统一的技能执行结果表示，适配所有类型的技能输出
 */
data class SimpleResult(
    val title: String,              // 主标题
    val content: String,            // 内容文本
    val type: ResultType,           // 结果类型
    val success: Boolean,           // 是否成功
    val data: Map<String, String> = emptyMap()  // 额外数据（键值对）
)

/**
 * 结果类型 - 只保留主要分类
 * 
 * 用于UI层根据不同类型的结果显示不同的视觉效果
 */
enum class ResultType {
    /** 信息类（天气、新闻、知识等） */
    INFO,
    
    /** 操作类（打开应用、控制设备等） */
    ACTION,
    
    /** 计算类 */
    CALC,
    
    /** 错误类 */
    ERROR
}
