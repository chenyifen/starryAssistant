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
import androidx.compose.ui.graphics.Color
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
import org.stypox.dicio.ui.floating.draggableOrb
import org.stypox.dicio.ui.floating.FloatingOrbConfig
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
    
    // 拖拽处理器
    private var dragTouchHandler: DragTouchHandler? = null
    
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
            
            // 设置生命周期相关的TreeOwner
            composeView.setViewTreeLifecycleOwner(lifecycleOwner)
            composeView.setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            composeView.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
            
            composeView.setContent {
                // 不使用AppTheme，因为Service不是Activity
                MaterialTheme {
                    FloatingOrbContent(
                        animationStateManager = animationStateManager,
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
     * 创建WindowManager布局参数
     */
    private fun createWindowLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            // 窗口类型
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            
            // 窗口标志 - 关键：FLAG_NOT_FOCUSABLE避免抢焦点
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            
            // 像素格式
            format = PixelFormat.TRANSLUCENT
            
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
        DebugLogger.logUI(TAG, "👆 Orb long pressed")
        onOrbLongPress?.invoke()
    }
    
    /**
     * 处理拖动开始
     */
    private fun handleDragStart() {
        DebugLogger.logUI(TAG, "🤏 Drag started")
        // 可以在这里改变动画状态，比如显示拖拽提示
        animationStateManager.setActive("拖拽中...")
    }
    
    /**
     * 处理拖动结束
     */
    private fun handleDragEnd() {
        DebugLogger.logUI(TAG, "🤏 Drag ended")
        // 拖拽结束后回到待机状态
        animationStateManager.setIdle()
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
}

/**
 * 悬浮球内容组件
 */
@Composable
private fun FloatingOrbContent(
    animationStateManager: LottieAnimationStateManager,
    onOrbClick: () -> Unit,
    onOrbLongPress: () -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit
) {
    val animationState by animationStateManager.currentState
    val displayText by animationStateManager.displayText
    
    Box(
        modifier = Modifier
            .size(FloatingOrbConfig.orbSizeDp) // 使用动态配置的尺寸
            .draggableOrb(
                onOrbClick = onOrbClick,
                onOrbLongPress = onOrbLongPress,
                onDragStart = onDragStart,
                onDragEnd = onDragEnd
            ),
        contentAlignment = Alignment.Center
    ) {
        // Lottie动画
        LottieAnimationController(
            animationState = animationState,
            displayText = displayText,
            size = FloatingOrbConfig.animationSizeInt // 使用动态配置的尺寸
        )
    }
}
