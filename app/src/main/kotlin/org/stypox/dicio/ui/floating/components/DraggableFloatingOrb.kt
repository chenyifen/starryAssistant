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
import org.stypox.dicio.ui.floating.components.FloatingTextDisplay
import org.stypox.dicio.ui.floating.components.FloatingTextStateManager
import org.stypox.dicio.ui.floating.components.LottieAnimationController
import org.stypox.dicio.ui.floating.components.LottieAnimationState
import org.stypox.dicio.ui.floating.components.LottieAnimationStateManager
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
    
    // 文本显示状态管理器
    private val textStateManager = FloatingTextStateManager(context)
    
    // 拖拽处理器
    private var dragTouchHandler: DragTouchHandler? = null
    
    // 边缘吸附状态
    private var isAtEdge = false
    
    // 拖拽状态
    private var isDragging = false
    private var isLongPressing = false
    
    // 点击回调
    var onOrbClick: (() -> Unit)? = null
    var onOrbLongPress: (() -> Unit)? = null
    
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                ) {
                    FloatingOrbContent(
                        animationStateManager = animationStateManager,
                        isAtEdge = isAtEdge,
                        isDragging = isDragging,
                        isLongPressing = isLongPressing,
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
     * 获取文本状态管理器
     */
    fun getTextStateManager(): FloatingTextStateManager = textStateManager
    
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
            
            // 窗口大小
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            
            // 窗口位置
            gravity = Gravity.TOP or Gravity.START
            x = 100 // 初始X位置
            y = 200 // 初始Y位置
        }
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
        }
    }
    
    /**
     * 设置边缘吸附状态
     */
    fun setEdgeState(atEdge: Boolean) {
        if (isAtEdge != atEdge) {
            isAtEdge = atEdge
            DebugLogger.logUI(TAG, "🧲 Edge state changed: $atEdge")
            
            // 重新创建视图以应用新的尺寸
            if (isShowing) {
                val currentView = floatingView
                if (currentView != null) {
                    // 保存当前位置
                    val layoutParams = currentView.layoutParams as WindowManager.LayoutParams
                    val currentX = layoutParams.x
                    val currentY = layoutParams.y
                    
                    // 隐藏并重新显示
                    hide()
                    show()
                    
                    // 恢复位置
                    updatePosition(currentX, currentY)
                }
            }
        }
    }
    
    /**
     * 获取当前是否在边缘
     */
    fun isAtEdge(): Boolean = isAtEdge
    
    /**
     * 更新拖拽状态
     */
    private fun updateDragState(dragging: Boolean = isDragging, longPressing: Boolean = isLongPressing) {
        if (isDragging != dragging || isLongPressing != longPressing) {
            isDragging = dragging
            isLongPressing = longPressing
            
            // 触发重新组合
            if (isShowing) {
                val currentView = floatingView
                if (currentView != null) {
                    // 保存当前位置
                    val layoutParams = currentView.layoutParams as WindowManager.LayoutParams
                    val currentX = layoutParams.x
                    val currentY = layoutParams.y
                    
                    // 隐藏并重新显示
                    hide()
                    show()
                    
                    // 恢复位置
                    updatePosition(currentX, currentY)
                }
            }
        }
    }
}

/**
 * 悬浮球内容组件 (仅包含Lottie动画，状态文本显示在动画内部)
 */
@Composable
private fun FloatingOrbContent(
    animationStateManager: LottieAnimationStateManager,
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
                .size(if (isAtEdge) FloatingOrbConfig.edgeAnimationSizeDp else FloatingOrbConfig.animationSizeDp) // 使用动画尺寸作为容器尺寸
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
                size = if (isAtEdge) FloatingOrbConfig.edgeAnimationSizeInt else FloatingOrbConfig.animationSizeInt // 根据边缘状态使用不同尺寸
            )
        }
        
        // 文本显示区域已移除 - 状态完全由动画内部文本显示
        // 不再显示悬浮球下方的绿色文本
    }
}
