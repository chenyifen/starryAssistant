package org.stypox.dicio.ui.floating

import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.stypox.dicio.ui.floating.FloatingOrbConfig
import org.stypox.dicio.util.DebugLogger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 拖拽触摸处理器
 * 
 * 特性：
 * - 长按开始拖拽
 * - 拖拽过程中的位置更新
 * - 释放时吸附到屏幕边缘
 * - 位置持久化保存
 * - 防误触处理
 */
class DragTouchHandler(
    private val context: Context,
    private val windowManager: WindowManager,
    private val floatingView: View
) {
    private val TAG = "DragTouchHandler"
    
    // SharedPreferences用于保存位置
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "floating_orb_position", Context.MODE_PRIVATE
    )
    
    // 拖拽状态
    private var isDragging = false
    private var isLongPressing = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    
    // 长按检测
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    
    // 屏幕边界信息
    private lateinit var screenBounds: FloatingOrbConfig.ScreenBounds
    
    // 回调
    var onOrbClick: (() -> Unit)? = null
    var onOrbLongPress: (() -> Unit)? = null
    var onDragStart: (() -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null
    
    companion object {
        private const val PREF_KEY_X = "orb_position_x"
        private const val PREF_KEY_Y = "orb_position_y"
    }
    
    init {
        // 初始化配置并获取屏幕边界
        FloatingOrbConfig.initialize(context)
        screenBounds = FloatingOrbConfig.getScreenBounds()
        DebugLogger.logUI(TAG, "📏 Screen bounds: ${screenBounds.width}x${screenBounds.height}, orb size: ${screenBounds.orbSize}")
        restorePosition()
    }
    
    /**
     * 恢复保存的位置
     */
    private fun restorePosition() {
        val savedX = prefs.getInt(PREF_KEY_X, FloatingOrbConfig.Drag.DEFAULT_X)
        val savedY = prefs.getInt(PREF_KEY_Y, FloatingOrbConfig.Drag.DEFAULT_Y)
        
        // 使用配置管理器限制位置在屏幕范围内
        val clampedX = screenBounds.clampX(savedX)
        val clampedY = screenBounds.clampY(savedY)
        
        updateWindowPosition(clampedX, clampedY)
        DebugLogger.logUI(TAG, "📍 Restored position: ($clampedX, $clampedY)")
    }
    
    /**
     * 保存当前位置
     */
    private fun savePosition(x: Int, y: Int) {
        prefs.edit()
            .putInt(PREF_KEY_X, x)
            .putInt(PREF_KEY_Y, y)
            .apply()
        DebugLogger.logUI(TAG, "💾 Saved position: ($x, $y)")
    }
    
    /**
     * 更新窗口位置
     */
    private fun updateWindowPosition(x: Int, y: Int) {
        try {
            val layoutParams = floatingView.layoutParams as WindowManager.LayoutParams
            layoutParams.x = x
            layoutParams.y = y
            windowManager.updateViewLayout(floatingView, layoutParams)
        } catch (e: Exception) {
            DebugLogger.logUI(TAG, "❌ Error updating window position: ${e.message}")
        }
    }
    
    /**
     * 处理触摸事件
     */
    fun handleTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                handleTouchDown(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                handleTouchMove(event)
                return true
            }
            MotionEvent.ACTION_UP -> {
                handleTouchUp(event)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handleTouchCancel()
                return true
            }
        }
        return false
    }
    
    /**
     * 处理按下事件
     */
    private fun handleTouchDown(event: MotionEvent) {
        DebugLogger.logUI(TAG, "👇 Touch down at (${event.rawX}, ${event.rawY})")
        
        // 记录初始位置
        val layoutParams = floatingView.layoutParams as WindowManager.LayoutParams
        initialX = layoutParams.x
        initialY = layoutParams.y
        initialTouchX = event.rawX
        initialTouchY = event.rawY
        
        // 启动长按检测
        startLongPressDetection()
    }
    
    /**
     * 处理移动事件
     */
    private fun handleTouchMove(event: MotionEvent) {
        val deltaX = event.rawX - initialTouchX
        val deltaY = event.rawY - initialTouchY
        
        // 如果移动距离超过阈值，取消长按检测
        if (abs(deltaX) > FloatingOrbConfig.Drag.CLICK_THRESHOLD || abs(deltaY) > FloatingOrbConfig.Drag.CLICK_THRESHOLD) {
            cancelLongPressDetection()
            
            // 如果正在长按或已经开始拖拽，则更新位置
            if (isLongPressing || isDragging) {
                if (!isDragging) {
                    startDragging()
                }
                
                val newX = (initialX + deltaX).toInt()
                val newY = (initialY + deltaY).toInt()
                
                // 使用配置管理器限制在屏幕范围内
                val clampedX = screenBounds.clampX(newX)
                val clampedY = screenBounds.clampY(newY)
                
                updateWindowPosition(clampedX, clampedY)
            }
        }
    }
    
    /**
     * 处理抬起事件
     */
    private fun handleTouchUp(event: MotionEvent) {
        DebugLogger.logUI(TAG, "👆 Touch up")
        
        cancelLongPressDetection()
        
        if (isDragging) {
            endDragging()
        } else if (!isLongPressing) {
            // 如果没有长按也没有拖拽，则是点击
            handleClick()
        }
        
        resetState()
    }
    
    /**
     * 处理取消事件
     */
    private fun handleTouchCancel() {
        DebugLogger.logUI(TAG, "❌ Touch cancelled")
        cancelLongPressDetection()
        if (isDragging) {
            endDragging()
        }
        resetState()
    }
    
    /**
     * 开始长按检测
     */
    private fun startLongPressDetection() {
        longPressRunnable = Runnable {
            DebugLogger.logUI(TAG, "⏰ Long press detected")
            isLongPressing = true
            onOrbLongPress?.invoke()
        }
        longPressHandler.postDelayed(longPressRunnable!!, FloatingOrbConfig.Drag.LONG_PRESS_TIMEOUT)
    }
    
    /**
     * 取消长按检测
     */
    private fun cancelLongPressDetection() {
        longPressRunnable?.let { runnable ->
            longPressHandler.removeCallbacks(runnable)
            longPressRunnable = null
        }
    }
    
    /**
     * 开始拖拽
     */
    private fun startDragging() {
        DebugLogger.logUI(TAG, "🤏 Start dragging")
        isDragging = true
        onDragStart?.invoke()
    }
    
    /**
     * 结束拖拽
     */
    private fun endDragging() {
        DebugLogger.logUI(TAG, "🤏 End dragging")
        
        // 吸附到边缘
        snapToEdge()
        
        onDragEnd?.invoke()
    }
    
    /**
     * 处理点击
     */
    private fun handleClick() {
        DebugLogger.logUI(TAG, "👆 Click detected")
        onOrbClick?.invoke()
    }
    
    /**
     * 重置状态
     */
    private fun resetState() {
        isDragging = false
        isLongPressing = false
    }
    
    /**
     * 吸附到屏幕边缘
     */
    private fun snapToEdge() {
        val layoutParams = floatingView.layoutParams as WindowManager.LayoutParams
        val currentX = layoutParams.x
        val currentY = layoutParams.y
        
        // 使用配置管理器计算边缘距离
        val edgeDistances = screenBounds.getEdgeDistances(currentX, currentY)
        
        var snapX = currentX
        var snapY = currentY
        
        // 只有距离小于阈值时才吸附
        if (edgeDistances.min < FloatingOrbConfig.Drag.EDGE_SNAP_THRESHOLD) {
            when (edgeDistances.nearestEdge) {
                FloatingOrbConfig.Edge.LEFT -> snapX = 0
                FloatingOrbConfig.Edge.RIGHT -> snapX = screenBounds.maxX
                FloatingOrbConfig.Edge.TOP -> snapY = 0
                FloatingOrbConfig.Edge.BOTTOM -> snapY = screenBounds.maxY
            }
            
            DebugLogger.logUI(TAG, "🧲 Snapping to edge: ($snapX, $snapY)")
            updateWindowPosition(snapX, snapY)
        }
        
        // 保存最终位置
        savePosition(snapX, snapY)
    }
}

/**
 * Compose修饰符扩展，用于处理拖拽
 */
@Composable
fun androidx.compose.ui.Modifier.draggableOrb(
    onOrbClick: () -> Unit,
    onOrbLongPress: () -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit
): androidx.compose.ui.Modifier {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    
    return this
        .pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { 
                    onOrbLongPress()
                },
                onTap = { 
                    onOrbClick()
                }
            )
        }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset ->
                    isDragging = true
                    dragOffset = offset
                    onDragStart()
                },
                onDragEnd = {
                    isDragging = false
                    onDragEnd()
                },
                onDrag = { change, dragAmount ->
                    dragOffset += dragAmount
                }
            )
        }
}
