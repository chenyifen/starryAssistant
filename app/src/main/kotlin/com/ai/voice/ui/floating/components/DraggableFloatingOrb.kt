package com.ai.voice.ui.floating.components
import android.util.Log
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import androidx.compose.material3.MaterialTheme
import com.ai.voice.ui.floating.DragTouchHandler
import com.ai.voice.ui.floating.FloatingOrbConfig
import com.ai.voice.ui.floating.VoiceAssistantUIState
import com.ai.voice.ui.floating.components.FloatingTextDisplay
import com.ai.voice.ui.floating.components.LottieAnimationController
import com.ai.voice.ui.floating.components.LottieAnimationState
import com.ai.voice.ui.floating.components.LottieAnimationStateManager
import com.ai.voice.ui.floating.state.VoiceAssistantFullState
import com.ai.voice.ui.floating.state.VoiceAssistantStateProvider
import com.ai.voice.util.DebugLogger

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
    
    // 位置保存 - Hyundai IT版本：固定在左下角
    // X坐标：16dp边距（约48px）
    // Y坐标：屏幕高度-悬浮球高度-底部导航栏高度-16dp边距
    private var savedX = 48
    private var savedY = calculateBottomLeftY()
    
    // 拖拽状态 - Hyundai IT版本：禁用拖拽
    private val isDragging = mutableStateOf(false)
    private val isLongPressing = mutableStateOf(false)
    
    // 点击回调 - Hyundai IT版本：禁用点击交互（保留变量但不设置）
    var onOrbClick: (() -> Unit)? = null
    var onOrbLongPress: (() -> Unit)? = null
    
    // VoiceAssistantStateProvider监听
    private var stateProvider: VoiceAssistantStateProvider? = null
    private var stateListener: ((VoiceAssistantFullState) -> Unit)? = null
    
    /**
     * 计算左下角Y坐标
     */
    private fun calculateBottomLeftY(): Int {
        val displayMetrics = context.resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val orbHeight = 240 // 悬浮球高度（约80dp）
        val bottomMargin = 48 // 底部边距（约16dp）
        val navigationBarHeight = 144 // 导航栏高度（约48dp）
        return screenHeight - orbHeight - bottomMargin - navigationBarHeight
    }
    
    /**
     * 显示悬浮球
     */
    fun show() {
        if (isShowing) return
        
        // 初始化配置
        FloatingOrbConfig.initialize(context)
        
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
                
                // ⚡ 性能优化：延迟加载复杂UI，避免阻塞主线程
                var isFullyInitialized by remember { mutableStateOf(false) }
                
                LaunchedEffect(Unit) {
                    // 延迟100ms，让主线程有时间处理其他任务
                    kotlinx.coroutines.delay(100)
                    isFullyInitialized = true
                }
                
                if (!isFullyInitialized) {
                    // 简单占位符 - 快速渲染
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Transparent)
                    )
                } else {
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
            
        } catch (e: Exception) {
            DebugLogger.logUI(TAG, "❌ Error showing floating orb: ${e.message}")
        }
    }
    
    /**
     * 隐藏悬浮球
     */
    fun hide() {
        if (!isShowing) return
        
        try {
            // 保存当前位置
            floatingView?.let { view ->
                val layoutParams = view.layoutParams as WindowManager.LayoutParams
                savedX = layoutParams.x
                savedY = layoutParams.y
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
     * 计算窗口高度 - 动态适配内容
     */
    private fun calculateWindowHeight(): Int {
        val orbHeight = if (isAtEdge) FloatingOrbConfig.edgeOrbSizePx else FloatingOrbConfig.orbSizePx
        
        // 边缘吸附时只显示小图标
        if (isAtEdge) {
            return (orbHeight * 0.6f).toInt()
        }
        
        var totalHeight = orbHeight.toInt()
        val spacing = 8 // dp转px的间距
        
        // ASR/TTS文本区域高度（如果有文本）
        val hasText = currentAsrText.value.isNotEmpty() || currentTtsText.value.isNotEmpty()
        if (hasText) {
            val textAreaHeight = 150
            totalHeight += textAreaHeight + spacing
        }
        
        // 添加底部边距，确保内容不会被截断
        totalHeight += 20
        
        return totalHeight
    }
    
    /**
     * 处理悬浮球点击
     * Hyundai IT版本：点击回调已禁用，此方法为空操作
     */
    private fun handleOrbClick() {
        // Hyundai IT版本：不执行任何操作
        // onOrbClick?.invoke()
        DebugLogger.logUI(TAG, "💤 Orb click ignored (interaction disabled)")
    }
    
    /**
     * 处理悬浮球长按
     * Hyundai IT版本：长按回调已禁用，此方法为空操作
     */
    private fun handleOrbLongPress() {
        // Hyundai IT版本：不执行任何操作
        // addHapticFeedback()
        // updateDragState(longPressing = true)
        // onOrbLongPress?.invoke()
        DebugLogger.logUI(TAG, "💤 Orb long press ignored (interaction disabled)")
    }
    
    /**
     * 处理拖动开始
     */
    private fun handleDragStart() {
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
        // 拖拽结束后恢复原来的动画状态（不强制设为待机）
        
        // 添加震动反馈
        addHapticFeedback()
        
        // 更新UI状态
        updateDragState(dragging = false, longPressing = false)
    }
    
    /**
     * 吸附到最近的边缘
     */
    private fun snapToNearestEdge(layoutParams: WindowManager.LayoutParams, screenWidth: Int, screenHeight: Int) {
        val centerX = layoutParams.x + calculateWindowWidth() / 2
        val centerY = layoutParams.y + calculateWindowHeight() / 2
        
        val distanceToLeft = centerX
        val distanceToRight = screenWidth - centerX
        val distanceToTop = centerY
        val distanceToBottom = screenHeight - centerY
        
        val minDistance = minOf(distanceToLeft, distanceToRight, distanceToTop, distanceToBottom)
        
        when (minDistance) {
            distanceToLeft -> {
                // 吸附到左边缘
                layoutParams.x = 0
            }
            distanceToRight -> {
                // 吸附到右边缘
                layoutParams.x = screenWidth - calculateWindowWidth()
            }
            distanceToTop -> {
                // 吸附到顶部边缘
                layoutParams.y = 0
            }
            distanceToBottom -> {
                // 吸附到底部边缘
                layoutParams.y = screenHeight - calculateWindowHeight()
            }
        }
        
        try {
            windowManager.updateViewLayout(floatingView, layoutParams)
        } catch (e: Exception) {
            DebugLogger.logUI(TAG, "❌ Failed to snap to edge: ${e.message}")
        }
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
        }
    }
    
    /**
     * 设置边缘吸附状态
     */
    fun setEdgeState(atEdge: Boolean) {
        if (isAtEdge != atEdge) {
            isAtEdge = atEdge
            
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
        // 性能优化：检测变化类型
        val asrTextChanged = currentAsrText.value != state.asrText
        val ttsTextChanged = currentTtsText.value != state.ttsText
        val uiStateChanged = lastUiState != state.uiState
        val displayTextChanged = lastDisplayText != state.displayText
        
        // 更新文本状态（过滤空文本变化）
        // 注意：如果新文本为空且旧文本也为空，则不触发更新
        val shouldUpdateAsr = asrTextChanged && !(state.asrText.isEmpty() && currentAsrText.value.isEmpty())
        val shouldUpdateTts = ttsTextChanged && !(state.ttsText.isEmpty() && currentTtsText.value.isEmpty())
        
        if (shouldUpdateAsr) {
            currentAsrText.value = state.asrText
        }
        
        if (shouldUpdateTts) {
            currentTtsText.value = state.ttsText
        }
        
        // 性能优化：智能更新策略
        when {
            // 情况1：仅文本变化 - 使用文本就地更新，避免refreshUI()
            (shouldUpdateAsr || shouldUpdateTts) && !uiStateChanged && !displayTextChanged -> {
                updateTextOnly()
            }
            
            // 情况2：UI状态或显示文本变化 - 需要完整UI更新
            uiStateChanged || displayTextChanged -> {
                updateUIState(state)
                if (shouldUpdateAsr || shouldUpdateTts) {
                    updateTextOnly()
                }
            }
        }
    }
    
    /**
     * 性能优化：文本就地更新 - 避免refreshUI()
     */
    private fun updateTextOnly() {
        // Compose会自动检测状态变化并重组相关组件
        // 无需调用refreshUI()，大幅提升性能
        
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
                    } catch (e: Exception) {
                        DebugLogger.logUI(TAG, "❌ Failed to update window height: ${e.message}")
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
            }
            VoiceAssistantUIState.THINKING -> {
                animationStateManager.setLoading()
            }
            VoiceAssistantUIState.SPEAKING -> {
                animationStateManager.setActive("SPEAKING")
            }
            VoiceAssistantUIState.ERROR -> {
                val displayText = state.displayText.ifBlank { "ERROR" }
                animationStateManager.setActive(displayText)
            }
        }
    }
    
    /**
     * 更新拖拽状态
     */
    private fun updateDragState(dragging: Boolean = isDragging.value, longPressing: Boolean = isLongPressing.value) {
        if (isDragging.value != dragging || isLongPressing.value != longPressing) {
            isDragging.value = dragging
            isLongPressing.value = longPressing
            
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

    // Hyundai IT版本：水平布局 - 悬浮球在左，文本在右
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.wrapContentSize() // 自适应大小
    ) {
        // 悬浮球 - 左侧
        Box(
            modifier = Modifier
                .size(animationSize) // 使用缓存的动画尺寸
                .scale(scale) // 只在拖拽时轻微缩放
                .clickable { onOrbClick() } // 只有这个区域可以点击（实际已禁用）
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
        
        // ASR/TTS文本显示区域 - 右侧（Hyundai IT版本）
        if (!isAtEdge && shouldShowText) {
            FloatingTextDisplay(
                userText = currentAsrText,
                aiText = currentTtsText,
                isVisible = true,
                modifier = Modifier
                    .weight(1f) // 占据剩余空间
                    .padding(start = 8.dp)
            )
        }
    }
}


