package org.stypox.dicio.ui.floating.mvp

import kotlinx.coroutines.flow.StateFlow

/**
 * 语音助手Presenter接口
 * 
 * 定义了语音助手的业务逻辑接口，与具体UI实现解耦
 * 负责协调服务层（WakeService、STT、SkillEvaluator）与视图层的交互
 */
interface VoiceAssistantPresenter {
    
    /**
     * 绑定视图
     * @param view 要绑定的视图
     */
    fun attachView(view: VoiceAssistantView)
    
    /**
     * 解绑视图
     */
    fun detachView()
    
    /**
     * 获取当前UI状态流
     */
    val uiState: StateFlow<VoiceAssistantUIState>
    
    /**
     * 获取当前显示文本流
     */
    val displayText: StateFlow<String>
    
    /**
     * 启动语音助手服务
     */
    fun startVoiceAssistant()
    
    /**
     * 停止语音助手服务
     */
    fun stopVoiceAssistant()
    
    /**
     * 处理用户点击事件
     */
    fun onUserClick()
    
    /**
     * 处理用户长按事件
     */
    fun onUserLongPress()
    
    /**
     * 处理拖拽开始事件
     */
    fun onDragStart()
    
    /**
     * 处理拖拽结束事件
     */
    fun onDragEnd()
    
    /**
     * 手动触发唤醒（用于测试或手动激活）
     */
    fun triggerWakeUp()
    
    /**
     * 手动停止监听
     */
    fun stopListening()
    
    /**
     * 清理资源
     */
    fun cleanup()
}

/**
 * 语音助手交互事件
 * 
 * 定义了用户与语音助手的各种交互事件
 */
sealed class VoiceAssistantEvent {
    /** 用户点击 */
    object UserClick : VoiceAssistantEvent()
    
    /** 用户长按 */
    object UserLongPress : VoiceAssistantEvent()
    
    /** 开始拖拽 */
    object DragStart : VoiceAssistantEvent()
    
    /** 结束拖拽 */
    object DragEnd : VoiceAssistantEvent()
    
    /** 手动唤醒 */
    object ManualWakeUp : VoiceAssistantEvent()
    
    /** 停止监听 */
    object StopListening : VoiceAssistantEvent()
}

/**
 * 语音助手配置
 * 
 * 定义了不同UI实现可能需要的配置参数
 */
data class VoiceAssistantConfig(
    /** 是否自动启动唤醒检测 */
    val autoStartWakeDetection: Boolean = true,
    
    /** 是否支持拖拽 */
    val supportDrag: Boolean = true,
    
    /** 是否支持点击交互 */
    val supportClick: Boolean = true,
    
    /** 是否支持长按交互 */
    val supportLongPress: Boolean = true,
    
    /** 自动隐藏延迟（毫秒），-1表示不自动隐藏 */
    val autoHideDelay: Long = -1,
    
    /** 视图类型 */
    val viewType: VoiceAssistantViewType = VoiceAssistantViewType.FLOATING_ORB
)
