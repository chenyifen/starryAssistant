package com.ai.voice.ui.floating

import android.content.Context
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.voice.R

object FloatingOrbConfig {
    
    private var context: Context? = null
    private var screenDensity: Float = 1.0f
    private var screenWidthPx: Int = 1080
    private var screenHeightPx: Int = 1920
    private var cachedOrbSizeDp: Dp? = null
    private var cachedAnimationSizeDp: Dp? = null
    
    fun initialize(context: Context) {
        this.context = context
        val resources = context.resources
        val displayMetrics = resources.displayMetrics
        
        screenDensity = displayMetrics.density
        screenWidthPx = displayMetrics.widthPixels
        screenHeightPx = displayMetrics.heightPixels
        
        clearCache()
    }
    
    private fun clearCache() {
        cachedOrbSizeDp = null
        cachedAnimationSizeDp = null
    }
    
    private fun getDimensionDp(resId: Int): Dp {
        val ctx = context ?: throw IllegalStateException("FloatingOrbConfig not initialized. Call initialize(context) first.")
        val px = ctx.resources.getDimension(resId)
        return (px / screenDensity).dp
    }
    
    private fun getDimensionPx(resId: Int): Int {
        val ctx = context ?: throw IllegalStateException("FloatingOrbConfig not initialized. Call initialize(context) first.")
        return ctx.resources.getDimension(resId).toInt()
    }
    
    val orbSizeDp: Dp
        get() = cachedOrbSizeDp ?: getDimensionDp(R.dimen.floating_orb_size).also { cachedOrbSizeDp = it }
    
    val animationSizeDp: Dp
        get() = cachedAnimationSizeDp ?: getDimensionDp(R.dimen.floating_orb_animation_size).also { cachedAnimationSizeDp = it }
    
    val orbSizePx: Int
        get() = getDimensionPx(R.dimen.floating_orb_size)
    
    val animationSizeInt: Int
        get() = (animationSizeDp.value).toInt()
    
    object Animation {
        const val EXPAND_DURATION = 300L
        const val CONTRACT_DURATION = 250L
        const val WAKE_WORD_DURATION = 500L
        const val AUTO_DISMISS_DELAY = 5000L
    }
    
    fun getScreenBounds(): ScreenBounds {
        return ScreenBounds(
            width = screenWidthPx,
            height = screenHeightPx,
            orbSize = orbSizePx
        )
    }
    
    data class ScreenBounds(
        val width: Int,
        val height: Int,
        val orbSize: Int
    )
    
    fun reloadConfig() {
        clearCache()
    }
}
