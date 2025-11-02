package com.ai.voice.ui.floating

import android.content.Context
import android.content.res.Resources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.voice.R

/**
 * 悬浮球配置管理器
 * 
 * 统一管理悬浮球的尺寸、样式等配置，支持从dimens.xml读取可定制的配置
 */
object FloatingOrbConfig {
    
    // 上下文引用，用于读取资源
    private var context: Context? = null
    
    // 屏幕适配相关
    private var screenDensity: Float = 1.0f
    private var screenWidthPx: Int = 1080
    private var screenHeightPx: Int = 1920
    
    // 缓存的尺寸值（避免重复读取资源）
    private var cachedOrbSizeDp: Dp? = null
    private var cachedAnimationSizeDp: Dp? = null
    private var cachedEdgeOrbSizeDp: Dp? = null
    private var cachedEdgeAnimationSizeDp: Dp? = null
    
    /**
     * 初始化配置
     */
    fun initialize(context: Context) {
        this.context = context
        val resources = context.resources
        val displayMetrics = resources.displayMetrics
        
        screenDensity = displayMetrics.density
        screenWidthPx = displayMetrics.widthPixels
        screenHeightPx = displayMetrics.heightPixels
        
        // 清除缓存，强制重新读取资源
        clearCache()
    }
    
    /**
     * 清除缓存的尺寸值
     */
    private fun clearCache() {
        cachedOrbSizeDp = null
        cachedAnimationSizeDp = null
        cachedEdgeOrbSizeDp = null
        cachedEdgeAnimationSizeDp = null
    }
    
    /**
     * 从资源文件读取尺寸值
     */
    private fun getDimensionDp(resId: Int): Dp {
        val ctx = context ?: throw IllegalStateException("FloatingOrbConfig not initialized. Call initialize(context) first.")
        val px = ctx.resources.getDimension(resId)
        return (px / screenDensity).dp
    }
    
    /**
     * 从资源文件读取尺寸值（像素）
     */
    private fun getDimensionPx(resId: Int): Int {
        val ctx = context ?: throw IllegalStateException("FloatingOrbConfig not initialized. Call initialize(context) first.")
        return ctx.resources.getDimension(resId).toInt()
    }
    
    /**
     * 悬浮球容器尺寸 (Compose Dp)
     */
    val orbSizeDp: Dp
        get() = cachedOrbSizeDp ?: getDimensionDp(R.dimen.floating_orb_size).also { cachedOrbSizeDp = it }
    
    /**
     * Lottie动画尺寸 (Compose Dp)
     */
    val animationSizeDp: Dp
        get() = cachedAnimationSizeDp ?: getDimensionDp(R.dimen.floating_orb_animation_size).also { cachedAnimationSizeDp = it }
    
    /**
     * 悬浮球尺寸 (像素值，用于WindowManager)
     */
    val orbSizePx: Int
        get() = getDimensionPx(R.dimen.floating_orb_size)
    
    /**
     * Lottie动画尺寸 (整数值，用于LottieAnimationController)
     */
    val animationSizeInt: Int
        get() = (animationSizeDp.value).toInt()
    
    /**
     * 边缘吸附时的悬浮球尺寸 (Compose Dp)
     */
    val edgeOrbSizeDp: Dp
        get() = cachedEdgeOrbSizeDp ?: getDimensionDp(R.dimen.floating_orb_edge_size).also { cachedEdgeOrbSizeDp = it }
    
    /**
     * 边缘吸附时的动画尺寸 (Compose Dp)
     */
    val edgeAnimationSizeDp: Dp
        get() = cachedEdgeAnimationSizeDp ?: getDimensionDp(R.dimen.floating_orb_edge_animation_size).also { cachedEdgeAnimationSizeDp = it }
    
    /**
     * 边缘吸附时的悬浮球尺寸 (像素值)
     */
    val edgeOrbSizePx: Int
        get() = getDimensionPx(R.dimen.floating_orb_edge_size)
    
    /**
     * 边缘吸附时的动画尺寸 (整数值)
     */
    val edgeAnimationSizeInt: Int
        get() = (edgeAnimationSizeDp.value).toInt()
    
    /**
     * 拖拽相关配置
     */
    object Drag {
        // 长按检测时间 (毫秒)
        const val LONG_PRESS_TIMEOUT = 500L
        
        // 点击移动阈值 (从dimens.xml读取)
        val CLICK_THRESHOLD: Float
            get() = context?.resources?.getDimension(R.dimen.floating_orb_click_threshold) ?: 10f
        
        // 边缘吸附阈值 (从dimens.xml读取)
        val EDGE_SNAP_THRESHOLD: Int
            get() = context?.resources?.getDimension(R.dimen.floating_orb_edge_snap_threshold)?.toInt() ?: 100
        
        // 默认位置 (从dimens.xml读取)
        val DEFAULT_X: Int
            get() = context?.resources?.getDimension(R.dimen.floating_orb_default_x)?.toInt() ?: 100
            
        val DEFAULT_Y: Int
            get() = context?.resources?.getDimension(R.dimen.floating_orb_default_y)?.toInt() ?: 200
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
            - Screen: ${screenWidthPx}x${screenHeightPx}px (density: $screenDensity)
            - Orb Size: ${orbSizeDp} (${orbSizePx}px)
            - Animation Size: ${animationSizeDp} (${animationSizeInt})
            - Edge Orb Size: ${edgeOrbSizeDp} (${edgeOrbSizePx}px)
            - Click Threshold: ${Drag.CLICK_THRESHOLD}px
            - Edge Snap Threshold: ${Drag.EDGE_SNAP_THRESHOLD}px
            - Default Position: (${Drag.DEFAULT_X}, ${Drag.DEFAULT_Y})
        """.trimIndent()
    }
    
    /**
     * 重新加载配置（当dimens.xml发生变化时调用）
     */
    fun reloadConfig() {
        clearCache()
    }
}
