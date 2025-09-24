package org.stypox.dicio.ui.floating.components

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import androidx.compose.material3.MaterialTheme
import org.stypox.dicio.ui.floating.DragTouchHandler
import org.stypox.dicio.ui.floating.FloatingOrbConfig
import org.stypox.dicio.ui.floating.VoiceAssistantUIState
import org.stypox.dicio.ui.floating.components.FloatingTextDisplay
import org.stypox.dicio.ui.floating.components.LottieAnimationController
import org.stypox.dicio.ui.floating.components.LottieAnimationState
import org.stypox.dicio.ui.floating.components.LottieAnimationStateManager
import org.stypox.dicio.ui.floating.state.VoiceAssistantFullState
import org.stypox.dicio.ui.floating.state.VoiceAssistantStateProvider
import org.stypox.dicio.util.DebugLogger

/**
 * 可拖动的悬浮球组件
 * 
 * 特性：
 * - 使用WindowManager创建系统级悬浮窗
 * - 支持拖动和点击
 * - 集成Lottie动画
 * - FLAG_NOT_FOCUSABLE避免抢焦点
 */
class DraggableFloatingOrb(
    private val context: Context,
    private val lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    private val viewModelStoreOwner: ViewModelStoreOwner,
    private val savedStateRegistryOwner: SavedStateRegistryOwner
) {
    private val TAG = "DraggableFloatingOrb"
    
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatingView: View? = null
    private var isShowing = false
    
    // 动画状态管理器
    private val animationStateManager = LottieAnimationStateManager()
    
    // 当前文本状态 - 使用MutableState以便Compose能检测变化
    private val currentAsrText = mutableStateOf("")
    private val currentTtsText = mutableStateOf("")
    
    // 性能优化：状态缓存
    private var lastUiState: VoiceAssistantUIState? = null
    private var lastDisplayText = ""
    
    // 拖拽处理器
    private var dragTouchHandler: DragTouchHandler? = null
    
    // 边缘吸附状态
    private var isAtEdge = false
    
    // 位置保存 - 用于hide/show时恢复位置
    private var savedX = 100
    private var savedY = 200
    
    // 拖拽状态 - 使用MutableState以便Compose能检测变化
    private val isDragging = mutableStateOf(false)
    private val isLongPressing = mutableStateOf(false)
    
    // 点击回调
    var onOrbClick: (() -> Unit)? = null
    var onOrbLongPress: (() -> Unit)? = null
    
    // VoiceAssistantStateProvider监听
    private var stateProvider: VoiceAssistantStateProvider? = null
    private var stateListener: ((VoiceAssistantFullState) -> Unit)? = null
    
    /**
     * 显示悬浮球
     */
    fun show() {
        if (isShowing) return
        
        // 初始化配置
        FloatingOrbConfig.initialize(context)
        DebugLogger.logUI(TAG, "🎈 Showing floating orb")
        DebugLogger.logUI(TAG, FloatingOrbConfig.getDebugInfo())
        
        try {
            val composeView = ComposeView(context)
            
            // 设置透明背景
            composeView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            // 设置生命周期相关的TreeOwner
            composeView.setViewTreeLifecycleOwner(lifecycleOwner)
            composeView.setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            composeView.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
            
            composeView.setContent {
                // 不使用AppTheme，因为Service不是Activity
                // 使用完全透明的背景
                
                // 在Composable内部读取状态，以便触发重组
                val asrText by currentAsrText
                val ttsText by currentTtsText
                val dragging by isDragging
                val longPressing by isLongPressing
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                ) {
                    FloatingOrbContent(
                        animationStateManager = animationStateManager,
                        currentAsrText = asrText,
                        currentTtsText = ttsText,
                        isAtEdge = isAtEdge,
                        isDragging = dragging,
                        isLongPressing = longPressing,
                        onOrbClick = { handleOrbClick() },
                        onOrbLongPress = { handleOrbLongPress() },
                        onDragStart = { handleDragStart() },
                        onDragEnd = { handleDragEnd() }
                    )
                }
            }
            
            val layoutParams = createWindowLayoutParams()
            windowManager.addView(composeView, layoutParams)
            
            floatingView = composeView
            isShowing = true
            
            // 初始化拖拽处理器
            dragTouchHandler = DragTouchHandler(context, windowManager, composeView).apply {
                onOrbClick = { handleOrbClick() }
                onOrbLongPress = { handleOrbLongPress() }
                onDragStart = { handleDragStart() }
                onDragEnd = { handleDragEnd() }
                onEdgeStateChanged = { atEdge -> setEdgeState(atEdge) }
            }
            
            // 在ComposeView上设置触摸监听器
            composeView.setOnTouchListener { _, event ->
                dragTouchHandler?.onTouchEvent(event) ?: false
            }
            
            // 默认设置为待机状态
            animationStateManager.setIdle()
            
            // 设置VoiceAssistantStateProvider监听
            setupStateProviderListener()
            
            DebugLogger.logUI(TAG, "✅ Floating orb shown successfully")
            
        } catch (e: Exception) {
            DebugLogger.logUI(TAG, "❌ Error showing floating orb: ${e.message}")
        }
    }
    
    /**
     * 隐藏悬浮球
     */
    fun hide() {
        if (!isShowing) return
        
        DebugLogger.logUI(TAG, "🎈 Hiding floating orb")
        
        try {
            // 保存当前位置
            floatingView?.let { view ->
                val layoutParams = view.layoutParams as WindowManager.LayoutParams
                savedX = layoutParams.x
                savedY = layoutParams.y
                DebugLogger.logUI(TAG, "💾 Position saved: x=$savedX, y=$savedY")
            }
            
            // 清理状态监听
            cleanupStateProviderListener()
            
            floatingView?.let { view ->
                windowManager.removeView(view)
                floatingView = null
                isShowing = false
            }
        } catch (e: Exception) {
            DebugLogger.logUI(TAG, "❌ Error hiding floating orb: ${e.message}")
        }
    }
    
    /**
     * 获取动画状态管理器
     */
    fun getAnimationStateManager(): LottieAnimationStateManager = animationStateManager
    
    /**
     * 获取当前ASR文本
     */
    fun getCurrentAsrText(): String = currentAsrText.value
    
    /**
     * 获取当前TTS文本
     */
    fun getCurrentTtsText(): String = currentTtsText.value
    
    /**
     * 创建WindowManager布局参数
     */
    private fun createWindowLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            // 窗口类型
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            
            // 窗口标志 - 关键：FLAG_NOT_FOCUSABLE避免抢焦点
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            
            // 像素格式 - 使用RGBA_8888支持完全透明
            format = PixelFormat.RGBA_8888
            
            // 窗口大小 - 固定宽度避免文本变化导致位置跳变，动态高度适应内容
            width = calculateWindowWidth()
            height = calculateWindowHeight()
            
            // 窗口位置 - 使用保存的位置
            gravity = Gravity.TOP or Gravity.START
            x = savedX // 使用保存的X位置
            y = savedY // 使用保存的Y位置
        }
    }
    
    /**
     * 计算窗口宽度 - 固定宽度避免文本变化导致位置跳变
     */
    private fun calculateWindowWidth(): Int {
        // 使用固定宽度，足够容纳最长的文本气泡（280dp + padding）
        val maxTextWidth = 280 // TextBubble的最大宽度
        val padding = 32 // 左右各16dp的padding
        val orbWidth = if (isAtEdge) FloatingOrbConfig.edgeOrbSizePx else FloatingOrbConfig.orbSizePx
        
        // 取悬浮球宽度和文本区域宽度的最大值
        return maxOf(orbWidth.toInt(), maxTextWidth + padding)
    }
    
    /**
     * 计算窗口高度
     */
    private fun calculateWindowHeight(): Int {
        val orbHeight = if (isAtEdge) FloatingOrbConfig.edgeOrbSizePx else FloatingOrbConfig.orbSizePx
        val textAreaHeight = if (isAtEdge) 0 else 150 // 边缘状态时不显示文本，不需要额外高度
        val spacing = 24 // 8dp spacing * 3 (density factor)
        
        return (orbHeight + textAreaHeight + spacing).toInt()
    }
    
    /**
     * 处理悬浮球点击
     */
    private fun handleOrbClick() {
        DebugLogger.logUI(TAG, "👆 Orb clicked")
        onOrbClick?.invoke()
    }
    
    /**
     * 处理悬浮球长按
     */
    private fun handleOrbLongPress() {
        DebugLogger.logUI(TAG, "👆 Orb long pressed - entering drag mode")
        
        // 添加震动反馈
        addHapticFeedback()
        
        // 长按时不改变动画状态，保持当前状态
        // 更新UI状态
        updateDragState(longPressing = true)
        
        onOrbLongPress?.invoke()
    }
    
    /**
     * 处理拖动开始
     */
    private fun handleDragStart() {
        DebugLogger.logUI(TAG, "🤏 Drag started")
        
        // 拖拽时不改变动画状态，保持当前状态
        
        // 添加震动反馈
        addHapticFeedback()
        
        // 更新UI状态
        updateDragState(dragging = true, longPressing = true)
    }
    
    /**
     * 处理拖动结束
     */
    private fun handleDragEnd() {
        DebugLogger.logUI(TAG, "🤏 Drag ended")
        
        // 拖拽结束后恢复原来的动画状态（不强制设为待机）
        
        // 添加震动反馈
        addHapticFeedback()
        
        // 更新UI状态
        updateDragState(dragging = false, longPressing = false)
    }
    
    /**
     * 添加触觉反馈
     */
    private fun addHapticFeedback() {
        try {
            floatingView?.let { view ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.GESTURE_START)
                } else {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                }
            }
        } catch (e: Exception) {
            DebugLogger.logUI(TAG, "❌ Haptic feedback failed: ${e.message}")
        }
    }
    
    /**
     * 更新悬浮球位置
     */
    fun updatePosition(x: Int, y: Int) {
        floatingView?.let { view ->
            val layoutParams = view.layoutParams as WindowManager.LayoutParams
            layoutParams.x = x
            layoutParams.y = y
            windowManager.updateViewLayout(view, layoutParams)
            
            // 同时更新保存的位置
            savedX = x
            savedY = y
            DebugLogger.logUI(TAG, "📍 Position updated and saved: x=$x, y=$y")
        }
    }
    
    /**
     * 设置边缘吸附状态
     */
    fun setEdgeState(atEdge: Boolean) {
        if (isAtEdge != atEdge) {
            isAtEdge = atEdge
            DebugLogger.logUI(TAG, "🧲 Edge state changed: $atEdge")
            
            // 更新窗口布局参数以适应新的尺寸
            if (isShowing) {
                val currentView = floatingView
                if (currentView != null) {
                    // 保存当前位置
                    val layoutParams = currentView.layoutParams as WindowManager.LayoutParams
                    val currentX = layoutParams.x
                    val currentY = layoutParams.y
                    
                    // 更新窗口高度
                    layoutParams.height = calculateWindowHeight()
                    
                    try {
                        windowManager.updateViewLayout(currentView, layoutParams)
                        DebugLogger.logUI(TAG, "🔄 Window layout updated for edge state: $atEdge, height: ${layoutParams.height}")
                    } catch (e: Exception) {
                        DebugLogger.logUI(TAG, "❌ Failed to update window layout: ${e.message}")
                        // 如果更新失败，回退到重新创建视图
                        hide()
                        show()
                        updatePosition(currentX, currentY)
                    }
                }
            }
        }
    }
    
    /**
     * 获取当前是否在边缘
     */
    fun isAtEdge(): Boolean = isAtEdge
    
    /**
     * 设置VoiceAssistantStateProvider监听
     */
    private fun setupStateProviderListener() {
        try {
            stateProvider = VoiceAssistantStateProvider.getInstance()
            stateListener = { state ->
                handleVoiceAssistantStateChange(state)
            }
            stateListener?.let { listener ->
                stateProvider?.addListener(listener)
                DebugLogger.logUI(TAG, "📡 VoiceAssistantStateProvider listener registered")
            }
        } catch (e: Exception) {
            DebugLogger.logUI(TAG, "❌ Failed to setup VoiceAssistantStateProvider listener: ${e.message}")
        }
    }

    /**
     * 清理VoiceAssistantStateProvider监听
     */
    private fun cleanupStateProviderListener() {
        try {
            stateListener?.let { listener ->
                stateProvider?.removeListener(listener)
                DebugLogger.logUI(TAG, "📡 VoiceAssistantStateProvider listener removed")
            }
            stateProvider = null
            stateListener = null
        } catch (e: Exception) {
            DebugLogger.logUI(TAG, "❌ Failed to cleanup VoiceAssistantStateProvider listener: ${e.message}")
        }
    }

    /**
     * 处理语音助手状态变化
     */
    private fun handleVoiceAssistantStateChange(state: VoiceAssistantFullState) {
        DebugLogger.logUI(TAG, "🔄 Voice assistant state changed: ${state.uiState}, display: '${state.displayText}'")
        
        // 性能优化：检测变化类型
        val asrTextChanged = currentAsrText.value != state.asrText
        val ttsTextChanged = currentTtsText.value != state.ttsText
        val uiStateChanged = lastUiState != state.uiState
        val displayTextChanged = lastDisplayText != state.displayText
        
        // 更新文本状态
        if (asrTextChanged) {
            currentAsrText.value = state.asrText
            DebugLogger.logUI(TAG, "📝 ASR text updated: '${state.asrText}'")
        }
        
        if (ttsTextChanged) {
            currentTtsText.value = state.ttsText
            DebugLogger.logUI(TAG, "🎵 TTS text updated: '${state.ttsText}'")
        }
        
        // 性能优化：智能更新策略
        when {
            // 情况1：仅文本变化 - 使用文本就地更新，避免refreshUI()
            (asrTextChanged || ttsTextChanged) && !uiStateChanged && !displayTextChanged -> {
                DebugLogger.logUI(TAG, "⚡ Text-only update, skipping UI rebuild")
                updateTextOnly()
            }
            
            // 情况2：UI状态或显示文本变化 - 需要完整UI更新
            uiStateChanged || displayTextChanged -> {
                updateUIState(state)
                if (asrTextChanged || ttsTextChanged) {
                    updateTextOnly()
                }
            }
            
            // 情况3：无变化 - 跳过更新
            else -> {
                DebugLogger.logUI(TAG, "⏭️ No significant changes, skipping update")
            }
        }
        
        // 记录技能结果
        state.result?.let { result ->
            DebugLogger.logUI(TAG, "🎯 Skill result: ${result.title} - ${result.content}")
        }
    }
    
    /**
     * 性能优化：文本就地更新 - 避免refreshUI()
     */
    private fun updateTextOnly() {
        // Compose会自动检测状态变化并重组相关组件
        // 无需调用refreshUI()，大幅提升性能
        DebugLogger.logUI(TAG, "📝 Text updated in-place (ASR: '${currentAsrText.value}', TTS: '${currentTtsText.value}')")
        
        // 文本变化时需要更新窗口高度，但保持位置不变
        updateWindowHeightOnly()
    }
    
    /**
     * 仅更新窗口高度，保持位置不变
     */
    private fun updateWindowHeightOnly() {
        if (isShowing) {
            val currentView = floatingView
            if (currentView != null) {
                val layoutParams = currentView.layoutParams as WindowManager.LayoutParams
                val oldHeight = layoutParams.height
                val newHeight = calculateWindowHeight()
                
                // 只有高度真正变化时才更新
                if (oldHeight != newHeight) {
                    // 保存当前位置 - 重要：不改变X和Y坐标
                    val currentX = layoutParams.x
                    val currentY = layoutParams.y
                    
                    // 仅更新高度
                    layoutParams.height = newHeight
                    
                    try {
                        windowManager.updateViewLayout(currentView, layoutParams)
                        DebugLogger.logUI(TAG, "📏 Window height updated: $oldHeight → $newHeight (position preserved: x=$currentX, y=$currentY)")
                    } catch (e: Exception) {
                        DebugLogger.logUI(TAG, "❌ Failed to update window height: ${e.message}")
                        // 如果更新失败，不要回退到hide/show，这会导致位置跳变
                        // 只记录错误，让Compose自己处理布局
                    }
                }
            }
        }
    }
    
    /**
     * 性能优化：UI状态更新 - 带缓存的状态切换
     */
    private fun updateUIState(state: VoiceAssistantFullState) {
        // 更新缓存
        lastUiState = state.uiState
        lastDisplayText = state.displayText
        
        // 根据UI状态更新动画 - 中央状态文本
        when (state.uiState) {
            VoiceAssistantUIState.IDLE -> {
                animationStateManager.setIdle()
            }
            VoiceAssistantUIState.WAKE_DETECTED -> {
                val displayText = state.displayText.ifBlank { "LISTENING" }
                animationStateManager.triggerWakeWord(displayText)
            }
            VoiceAssistantUIState.LISTENING -> {
                animationStateManager.setActive("LISTENING")
                DebugLogger.logUI(TAG, "👂 LISTENING state activated")
            }
            VoiceAssistantUIState.THINKING -> {
                animationStateManager.setLoading()
                DebugLogger.logUI(TAG, "🤔 THINKING state activated")
            }
            VoiceAssistantUIState.SPEAKING -> {
                animationStateManager.setActive("SPEAKING")
                DebugLogger.logUI(TAG, "🎵 SPEAKING state activated")
            }
            VoiceAssistantUIState.ERROR -> {
                val displayText = state.displayText.ifBlank { "ERROR" }
                animationStateManager.setActive(displayText)
            }
        }
        
        DebugLogger.logUI(TAG, "🎨 UI state updated: ${state.uiState}")
    }
    
    /**
     * 更新拖拽状态
     */
    private fun updateDragState(dragging: Boolean = isDragging.value, longPressing: Boolean = isLongPressing.value) {
        if (isDragging.value != dragging || isLongPressing.value != longPressing) {
            isDragging.value = dragging
            isLongPressing.value = longPressing
            
            DebugLogger.logUI(TAG, "🎨 Drag state updated: dragging=$dragging, longPressing=$longPressing")
            
            // Compose会自动检测MutableState变化并重组，无需手动刷新
        }
    }
}

/**
 * 悬浮球内容组件 (包含Lottie动画和下方的ASR/TTS文本显示)
 */
@Composable
private fun FloatingOrbContent(
    animationStateManager: LottieAnimationStateManager,
    currentAsrText: String,
    currentTtsText: String,
    isAtEdge: Boolean = false,
    isDragging: Boolean = false,
    isLongPressing: Boolean = false,
    onOrbClick: () -> Unit,
    onOrbLongPress: () -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit
) {
    val animationState by animationStateManager.currentState
    val displayText by animationStateManager.displayText
    
    // 性能优化：使用 remember 缓存计算结果
    val shouldShowText = remember(currentAsrText, currentTtsText, isAtEdge) {
        !isAtEdge && (currentAsrText.isNotEmpty() || currentTtsText.isNotEmpty())
    }
    
    // 性能优化：使用 derivedStateOf 优化动画尺寸计算
    val animationSize by remember {
        derivedStateOf {
            if (isAtEdge) FloatingOrbConfig.edgeAnimationSizeDp else FloatingOrbConfig.animationSizeDp
        }
    }
    
    val animationSizeInt by remember {
        derivedStateOf {
            if (isAtEdge) FloatingOrbConfig.edgeAnimationSizeInt else FloatingOrbConfig.animationSizeInt
        }
    }
    
    // 简化的动画效果 - 只保留必要的拖拽反馈
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.05f else 1.0f, // 只在拖拽时轻微放大
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 悬浮球 - 容器大小等于动画大小，去掉多余空间
        Box(
            modifier = Modifier
                .size(animationSize) // 使用缓存的动画尺寸
                .scale(scale) // 只在拖拽时轻微缩放
                .let { modifier ->
                    // 只在拖拽时添加60%透明度的白色边框
                    if (isDragging) {
                        modifier.border(
                            width = 2.dp,
                            color = Color.White.copy(alpha = 0.6f), // 60%透明度的白色
                            shape = CircleShape
                        )
                    } else {
                        modifier
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Lottie动画
            LottieAnimationController(
                animationState = animationState,
                displayText = displayText,
                size = animationSizeInt // 使用缓存的动画尺寸
            )
        }
        
        // ASR/TTS文本显示区域 - 在边缘时隐藏，使用缓存的shouldShowText
        if (!isAtEdge) {
            DebugLogger.logUI("FloatingOrbContent", "📱 Text display: ASR='$currentAsrText', TTS='$currentTtsText', shouldShow=$shouldShowText, isAtEdge=$isAtEdge")
            
            // 性能优化：只有在需要显示文本时才渲染FloatingTextDisplay
            if (shouldShowText) {
                FloatingTextDisplay(
                    userText = currentAsrText,
                    aiText = currentTtsText,
                    isVisible = true
                )
            }
        } else {
            DebugLogger.logUI("FloatingOrbContent", "🚫 Text display hidden due to edge state")
        }
    }
}


