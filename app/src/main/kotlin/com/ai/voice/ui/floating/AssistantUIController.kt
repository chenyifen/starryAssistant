package com.ai.voice.ui.floating

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ai.voice.util.DebugLogger

/**
 * 智能语音助手UI控制器
 * 
 * 负责管理悬浮球 ↔ 半屏界面的状态转换和动画过渡
 * 
 * 状态转换流程：
 * FLOATING_ORB → EXPANDING → HALF_SCREEN → CONTRACTING → FLOATING_ORB
 */
class AssistantUIController(
    private val context: Context
) {
    companion object {
        private const val TAG = "AssistantUIController"
        
        // 动画时长配置
        private const val EXPAND_ANIMATION_DURATION = 300L
        private const val CONTRACT_ANIMATION_DURATION = 250L
        private const val AUTO_DISMISS_DELAY = 5000L // 5秒后自动收起
        
        // 悬浮球配置
        private const val ORB_SIZE_DP = 60
        private const val ORB_EXPANDED_SIZE_DP = 80
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // UI状态管理
    private val _uiMode = MutableStateFlow(AssistantUIMode.FLOATING_ORB)
    val uiMode: StateFlow<AssistantUIMode> = _uiMode.asStateFlow()
    
    private val _animationProgress = MutableStateFlow(0f)
    val animationProgress: StateFlow<Float> = _animationProgress.asStateFlow()
    
    private val _orbPosition = MutableStateFlow(OrbPosition(0f, 0f))
    val orbPosition: StateFlow<OrbPosition> = _orbPosition.asStateFlow()
    
    // 动画控制
    private var autoDismissJob: kotlinx.coroutines.Job? = null
    
    // 交互状态
    var isDragging = false
        private set
    
    var isInteracting = false
        private set
    
    // 回调函数
    var onExpandToHalfScreen: (() -> Unit)? = null
    var onContractToOrb: (() -> Unit)? = null
    
    init {
        DebugLogger.logUI(TAG, "🎮 AssistantUIController initialized")
    }
    
    /**
     * 点击悬浮球 - 展开到半屏模式
     */
    fun onOrbClick() {
        if (_uiMode.value != AssistantUIMode.FLOATING_ORB) {
            DebugLogger.logUI(TAG, "⚠️ Orb click ignored, current mode: ${_uiMode.value}")
            return
        }
        
        DebugLogger.logUI(TAG, "🎯 Orb clicked, expanding to half screen")
        expandToHalfScreen()
    }
    
    /**
     * 长按悬浮球 - 进入拖拽模式或打开设置
     */
    fun onOrbLongPress(): Boolean {
        if (_uiMode.value != AssistantUIMode.FLOATING_ORB) {
            return false
        }
        
        DebugLogger.logUI(TAG, "🔒 Orb long pressed, entering drag mode")
        isDragging = true
        return true
    }
    
    /**
     * 语音唤醒触发 - 自动展开界面
     */
    fun onWakeWordDetected() {
        DebugLogger.logUI(TAG, "🎤 Wake word detected, auto-expanding interface")
        
        // 如果已经是半屏模式，重置自动关闭计时器
        if (_uiMode.value == AssistantUIMode.HALF_SCREEN) {
            resetAutoDismissTimer()
            return
        }
        
        // 悬浮球做唤醒动画，然后展开
        playWakeWordAnimation {
            expandToHalfScreen()
        }
    }
    
    /**
     * 开始语音交互
     */
    fun onVoiceInteractionStart() {
        DebugLogger.logUI(TAG, "🎙️ Voice interaction started")
        isInteracting = true
        cancelAutoDismissTimer()
    }
    
    /**
     * 结束语音交互
     */
    fun onVoiceInteractionEnd() {
        DebugLogger.logUI(TAG, "🎙️ Voice interaction ended")
        isInteracting = false
        
        // 交互结束后启动自动收起计时器
        if (_uiMode.value == AssistantUIMode.HALF_SCREEN) {
            startAutoDismissTimer()
        }
    }
    
    /**
     * 手动关闭半屏界面
     */
    fun dismissHalfScreen() {
        if (_uiMode.value != AssistantUIMode.HALF_SCREEN) {
            return
        }
        
        DebugLogger.logUI(TAG, "❌ Manually dismissing half screen")
        contractToOrb()
    }
    
    /**
     * 更新悬浮球位置（拖拽时调用）
     */
    fun updateOrbPosition(x: Float, y: Float) {
        if (!isDragging) return
        
        _orbPosition.value = OrbPosition(x, y)
        DebugLogger.logUI(TAG, "📍 Orb position updated: ($x, $y)")
    }
    
    /**
     * 结束拖拽
     */
    fun endDragging() {
        if (!isDragging) return
        
        DebugLogger.logUI(TAG, "🏁 Dragging ended")
        isDragging = false
        
        // 可以在这里添加吸附到边缘的逻辑
        snapToEdge()
    }
    
    /**
     * 展开到半屏模式
     */
    fun expandToHalfScreen() {
        Log.d(TAG, "🎯 [EXPAND] expandToHalfScreen 开始执行")
        if (_uiMode.value != AssistantUIMode.FLOATING_ORB) {
            Log.d(TAG, "⚠️ [EXPAND] 当前模式不是FLOATING_ORB: ${_uiMode.value}")
            return
        }
        
        Log.d(TAG, "🔄 [EXPAND] 设置模式为EXPANDING")
        DebugLogger.logUI(TAG, "📈 Starting expand animation")
        _uiMode.value = AssistantUIMode.EXPANDING
        
        Log.d(TAG, "🚀 [EXPAND] 启动协程")
        // 简化版本：直接切换到半屏模式
        scope.launch {
            Log.d(TAG, "⏳ [EXPAND] 协程内: 延迟300ms")
            delay(300) // 模拟动画时间
            Log.d(TAG, "🔄 [EXPAND] 协程内: 设置模式为HALF_SCREEN")
            _uiMode.value = AssistantUIMode.HALF_SCREEN
            DebugLogger.logUI(TAG, "✅ Expand animation completed")
            
            Log.d(TAG, "⏲️ [EXPAND] 协程内: 启动自动收起计时器")
            // 启动自动收起计时器
            startAutoDismissTimer()
            
            Log.d(TAG, "📢 [EXPAND] 协程内: 触发回调")
            // 触发回调
            onExpandToHalfScreen?.invoke()
            Log.d(TAG, "✅ [EXPAND] 协程执行完成")
        }
        Log.d(TAG, "✅ [EXPAND] expandToHalfScreen 主逻辑完成")
    }
    
    /**
     * 收缩回悬浮球模式
     */
    fun contractToOrb() {
        if (_uiMode.value != AssistantUIMode.HALF_SCREEN) {
            return
        }
        
        DebugLogger.logUI(TAG, "📉 Starting contract animation")
        _uiMode.value = AssistantUIMode.CONTRACTING
        
        cancelAutoDismissTimer()
        
        // 简化版本：直接切换到悬浮球模式
        scope.launch {
            delay(250) // 模拟动画时间
            _uiMode.value = AssistantUIMode.FLOATING_ORB
            DebugLogger.logUI(TAG, "✅ Contract animation completed")
            
            // 触发回调
            onContractToOrb?.invoke()
        }
    }
    
    /**
     * 播放唤醒词动画
     */
    private fun playWakeWordAnimation(onComplete: () -> Unit) {
        DebugLogger.logUI(TAG, "🌟 Playing wake word animation")
        
        // 简化版本：延迟后调用回调
        scope.launch {
            delay(500)
            onComplete()
        }
    }
    
    /**
     * 启动自动收起计时器
     */
    private fun startAutoDismissTimer() {
        if (isInteracting) {
            DebugLogger.logUI(TAG, "⏰ Auto-dismiss timer skipped (user interacting)")
            return
        }
        
        cancelAutoDismissTimer()
        autoDismissJob = scope.launch {
            DebugLogger.logUI(TAG, "⏰ Auto-dismiss timer started (${AUTO_DISMISS_DELAY}ms)")
            delay(AUTO_DISMISS_DELAY)
            
            if (_uiMode.value == AssistantUIMode.HALF_SCREEN && !isInteracting) {
                DebugLogger.logUI(TAG, "⏰ Auto-dismissing half screen")
                contractToOrb()
            }
        }
    }
    
    /**
     * 取消自动收起计时器
     */
    private fun cancelAutoDismissTimer() {
        autoDismissJob?.cancel()
        autoDismissJob = null
    }
    
    /**
     * 重置自动收起计时器
     */
    private fun resetAutoDismissTimer() {
        cancelAutoDismissTimer()
        startAutoDismissTimer()
    }
    
    /**
     * 悬浮球吸附到边缘
     */
    private fun snapToEdge() {
        // TODO: 实现吸附到屏幕边缘的逻辑
        DebugLogger.logUI(TAG, "🧲 Snapping orb to edge")
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        DebugLogger.logUI(TAG, "🧹 Cleaning up AssistantUIController")
        cancelAutoDismissTimer()
    }
}

/**
 * 助手UI模式
 */
enum class AssistantUIMode {
    FLOATING_ORB,    // 悬浮球模式
    EXPANDING,       // 展开动画中
    HALF_SCREEN,     // 半屏模式
    CONTRACTING      // 收缩动画中
}

/**
 * 悬浮球位置
 */
data class OrbPosition(
    val x: Float,
    val y: Float
)
