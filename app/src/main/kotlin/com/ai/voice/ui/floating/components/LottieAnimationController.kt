package com.ai.voice.ui.floating.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.*
import com.airbnb.lottie.LottieProperty
import com.ai.voice.util.DebugLogger

/**
 * Lottie动画控制器
 * 
 * 管理ai_robot.json动画的不同状态：
 * - IDLE: 待机状态 (0-116帧) - 呼吸效果
 * - LOADING: 加载状态 (116-168帧) - 跳动小点
 * - ACTIVE: 激活状态 (168-360帧) - 显示文本和光晕
 */
@Composable
fun LottieAnimationController(
    animationState: LottieAnimationState,
    displayText: String = "I'm here for you!",
    modifier: Modifier = Modifier,
    size: Int = 80
) {
    val TAG = "LottieAnimationController"
    
    // 加载Lottie动画
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("ai_robot.json")
    )
    
    // 动画进度控制
    val animationProgress by animateLottieCompositionAsState(
        composition = composition,
        iterations = when (animationState) {
            LottieAnimationState.IDLE -> LottieConstants.IterateForever
            LottieAnimationState.LOADING -> LottieConstants.IterateForever
            LottieAnimationState.ACTIVE -> 1
            LottieAnimationState.WAKE_WORD -> 1
        },
        speed = when (animationState) {
            LottieAnimationState.IDLE -> 0.5f
            LottieAnimationState.LOADING -> 1.0f
            LottieAnimationState.ACTIVE -> 1.0f
            LottieAnimationState.WAKE_WORD -> 1.5f
        },
        clipSpec = when (animationState) {
            LottieAnimationState.IDLE -> LottieClipSpec.Frame(0, 116)
            LottieAnimationState.LOADING -> LottieClipSpec.Frame(116, 168)
            LottieAnimationState.ACTIVE -> LottieClipSpec.Frame(168, 360)
            LottieAnimationState.WAKE_WORD -> LottieClipSpec.Frame(168, 360)
        }
    )
    
    // 记录状态变化
    LaunchedEffect(animationState) {
        DebugLogger.logUI(TAG, "🎭 Animation state changed to: $animationState")
    }
    
    Box(
        modifier = modifier
            .size(size.dp)
            .background(Color.Transparent), // 确保Box背景透明
        contentAlignment = Alignment.Center
    ) {
        // Lottie动画
        LottieAnimation(
            composition = composition,
            progress = { animationProgress },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent), // 确保Lottie动画背景透明
            clipToCompositionBounds = false, // 不裁剪到组合边界
            enableMergePaths = true, // 启用路径合并优化
            dynamicProperties = rememberLottieDynamicProperties(
                // 隐藏背景层 - Shape Layer 13 (ind: 17)
                rememberLottieDynamicProperty(
                    property = LottieProperty.OPACITY,
                    value = 0,
                    keyPath = arrayOf("Shape Layer 13", "**")
                ),
                // 动态替换文本内容
                rememberLottieDynamicProperty(
                    property = LottieProperty.TEXT,
                    value = displayText,
                    keyPath = arrayOf("**") // 匹配所有文本层
                )
            )
        )
        
        // 文本已通过 LottieProperty.TEXT 动态替换，无需额外处理
        LaunchedEffect(displayText) {
            if (displayText.isNotEmpty()) {
                DebugLogger.logUI(TAG, "🎨 Animation text updated: $displayText")
            }
        }
    }
}

/**
 * Lottie动画状态枚举
 */
enum class LottieAnimationState {
    /**
     * 待机状态 - 缓慢呼吸效果
     * 对应动画帧: 0-116
     */
    IDLE,
    
    /**
     * 加载状态 - 跳动小点动画
     * 对应动画帧: 116-168
     */
    LOADING,
    
    /**
     * 激活状态 - 显示文本和光晕
     * 对应动画帧: 168-360
     */
    ACTIVE,
    
    /**
     * 唤醒词触发状态 - 快速播放激活动画
     * 对应动画帧: 168-360 (加速播放)
     */
    WAKE_WORD
}

/**
 * 动画状态管理器
 */
class LottieAnimationStateManager {
    private val TAG = "LottieAnimationStateManager"
    
    private val _currentState = mutableStateOf(LottieAnimationState.IDLE)
    val currentState: State<LottieAnimationState> = _currentState
    
    private val _displayText = mutableStateOf("I'm here for you!")
    val displayText: State<String> = _displayText
    
    /**
     * 切换到待机状态
     */
    fun setIdle() {
        DebugLogger.logUI(TAG, "🔄 Switching to IDLE state")
        _currentState.value = LottieAnimationState.IDLE
    }
    
    /**
     * 切换到加载状态
     */
    fun setLoading() {
        DebugLogger.logUI(TAG, "🔄 Switching to LOADING state")
        _currentState.value = LottieAnimationState.LOADING
    }
    
    /**
     * 切换到激活状态
     */
    fun setActive(text: String = "I'm here for you!") {
        DebugLogger.logUI(TAG, "🔄 Switching to ACTIVE state with text: $text")
        _displayText.value = text
        _currentState.value = LottieAnimationState.ACTIVE
    }
    
    /**
     * 触发唤醒词动画
     */
    fun triggerWakeWord(text: String = "正在听取...") {
        DebugLogger.logUI(TAG, "🔄 Triggering WAKE_WORD animation with text: $text")
        _displayText.value = text
        _currentState.value = LottieAnimationState.WAKE_WORD
    }
    
    /**
     * 设置显示文本
     */
    fun setDisplayText(text: String) {
        DebugLogger.logUI(TAG, "📝 Setting display text: $text")
        _displayText.value = text
    }
}

/**
 * 预定义的动画文本
 */
object LottieAnimationTexts {
    const val DEFAULT = "I'm here for you!"
    const val LISTENING = "正在听取..."
    const val PROCESSING = "正在处理..."
    const val THINKING = "正在思考..."
    const val READY = "我在这里！"
    const val WAKE_WORD_DETECTED = "唤醒词检测到"
    const val ERROR = "出现错误"
    const val OFFLINE = "离线模式"
}
