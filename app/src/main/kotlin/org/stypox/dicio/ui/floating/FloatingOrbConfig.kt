package org.stypox.dicio.ui.floating

import android.content.Context
import android.content.res.Resources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 悬浮球配置管理器
 * 
 * 统一管理悬浮球的尺寸、样式等配置，支持动态适配不同屏幕尺寸
 */
object FloatingOrbConfig {
    
    // 基础配置
    private const val BASE_ORB_SIZE_DP = 80f
    private const val BASE_ANIMATION_SIZE_DP = 64f
    
    // 缩放因子 - 可以根据需要调整
    private var scaleFactor: Float = 3.0f
    
    // 屏幕适配相关
    private var screenDensity: Float = 1.0f
    private var screenWidthPx: Int = 1080
    private var screenHeightPx: Int = 1920
    
    /**
     * 初始化配置
     */
    fun initialize(context: Context) {
        val resources = context.resources
        val displayMetrics = resources.displayMetrics
        
        screenDensity = displayMetrics.density
        screenWidthPx = displayMetrics.widthPixels
        screenHeightPx = displayMetrics.heightPixels
        
        // 根据屏幕尺寸自动调整缩放因子
        autoAdjustScaleFactor()
    }
    
    /**
     * 根据屏幕尺寸自动调整缩放因子
     */
    private fun autoAdjustScaleFactor() {
        val screenWidthDp = screenWidthPx / screenDensity
        val screenHeightDp = screenHeightPx / screenDensity
        
        // 根据屏幕尺寸调整缩放因子
        scaleFactor = when {
            // 小屏设备 (< 360dp width)
            screenWidthDp < 360 -> 2.0f
            // 中等屏幕 (360-600dp width)  
            screenWidthDp < 600 -> 2.5f
            // 大屏设备 (600-900dp width)
            screenWidthDp < 900 -> 3.0f
            // 超大屏设备 (> 900dp width)
            else -> 3.5f
        }
    }
    
    /**
     * 手动设置缩放因子
     */
    fun setScaleFactor(factor: Float) {
        scaleFactor = factor.coerceIn(1.0f, 5.0f) // 限制在合理范围内
    }
    
    /**
     * 获取当前缩放因子
     */
    fun getScaleFactor(): Float = scaleFactor
    
    /**
     * 悬浮球容器尺寸 (Compose Dp)
     */
    val orbSizeDp: Dp
        get() = (BASE_ORB_SIZE_DP * scaleFactor).dp
    
    /**
     * Lottie动画尺寸 (Compose Dp)
     */
    val animationSizeDp: Dp
        get() = (BASE_ANIMATION_SIZE_DP * scaleFactor).dp
    
    /**
     * 悬浮球尺寸 (像素值，用于WindowManager)
     */
    val orbSizePx: Int
        get() = (BASE_ORB_SIZE_DP * scaleFactor * screenDensity).toInt()
    
    /**
     * Lottie动画尺寸 (整数值，用于LottieAnimationController)
     */
    val animationSizeInt: Int
        get() = (BASE_ANIMATION_SIZE_DP * scaleFactor).toInt()
    
    /**
     * 拖拽相关配置
     */
    object Drag {
        // 长按检测时间 (毫秒)
        const val LONG_PRESS_TIMEOUT = 500L
        
        // 点击移动阈值 (像素)
        const val CLICK_THRESHOLD = 10f
        
        // 边缘吸附阈值 (像素)
        const val EDGE_SNAP_THRESHOLD = 100
        
        // 默认位置
        const val DEFAULT_X = 100
        const val DEFAULT_Y = 200
    }
    
    /**
     * 动画相关配置
     */
    object Animation {
        // 展开动画时长 (毫秒)
        const val EXPAND_DURATION = 300L
        
        // 收缩动画时长 (毫秒)
        const val CONTRACT_DURATION = 250L
        
        // 唤醒词动画时长 (毫秒)
        const val WAKE_WORD_DURATION = 500L
        
        // 自动收起延迟 (毫秒)
        const val AUTO_DISMISS_DELAY = 5000L
    }
    
    /**
     * 获取屏幕边界信息
     */
    fun getScreenBounds(): ScreenBounds {
        return ScreenBounds(
            width = screenWidthPx,
            height = screenHeightPx,
            orbSize = orbSizePx
        )
    }
    
    /**
     * 屏幕边界信息
     */
    data class ScreenBounds(
        val width: Int,
        val height: Int,
        val orbSize: Int
    ) {
        // 最大X坐标 (确保悬浮球不超出屏幕)
        val maxX: Int get() = width - orbSize
        
        // 最大Y坐标 (确保悬浮球不超出屏幕)
        val maxY: Int get() = height - orbSize
        
        // 限制坐标在屏幕范围内
        fun clampX(x: Int): Int = x.coerceIn(0, maxX)
        fun clampY(y: Int): Int = y.coerceIn(0, maxY)
        
        // 计算到各边缘的距离
        fun getEdgeDistances(x: Int, y: Int): EdgeDistances {
            return EdgeDistances(
                left = x,
                right = maxX - x,
                top = y,
                bottom = maxY - y
            )
        }
    }
    
    /**
     * 边缘距离信息
     */
    data class EdgeDistances(
        val left: Int,
        val right: Int,
        val top: Int,
        val bottom: Int
    ) {
        // 获取最小距离
        val min: Int get() = minOf(left, right, top, bottom)
        
        // 获取最近的边缘类型
        val nearestEdge: Edge get() = when (min) {
            left -> Edge.LEFT
            right -> Edge.RIGHT
            top -> Edge.TOP
            else -> Edge.BOTTOM
        }
    }
    
    /**
     * 边缘类型
     */
    enum class Edge {
        LEFT, RIGHT, TOP, BOTTOM
    }
    
    /**
     * 获取调试信息
     */
    fun getDebugInfo(): String {
        return """
            FloatingOrbConfig Debug Info:
            - Scale Factor: $scaleFactor
            - Screen: ${screenWidthPx}x${screenHeightPx}px (density: $screenDensity)
            - Orb Size: ${orbSizeDp} (${orbSizePx}px)
            - Animation Size: ${animationSizeDp} (${animationSizeInt})
        """.trimIndent()
    }
}
