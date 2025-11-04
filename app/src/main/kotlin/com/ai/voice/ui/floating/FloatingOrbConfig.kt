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
    )
    
    /**
     * 获取调试信息
     */
    fun getDebugInfo(): String {
        return """
            FloatingOrbConfig Debug Info:
            - Screen: ${screenWidthPx}x${screenHeightPx}px (density: $screenDensity)
            - Orb Size: ${orbSizeDp} (${orbSizePx}px)
            - Animation Size: ${animationSizeDp} (${animationSizeInt})
        """.trimIndent()
    }
    
    /**
     * 重新加载配置（当dimens.xml发生变化时调用）
     */
    fun reloadConfig() {
        clearCache()
    }
}
