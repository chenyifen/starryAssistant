package com.ai.voice.ui.floating.components

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
            LottieAnimationState.LISTENING -> LottieConstants.IterateForever
        },
        speed = when (animationState) {
            LottieAnimationState.IDLE -> 0.5f
            LottieAnimationState.LISTENING -> 1.0f
        },
        clipSpec = when (animationState) {
            LottieAnimationState.IDLE -> LottieClipSpec.Frame(0, 116)
            LottieAnimationState.LISTENING -> LottieClipSpec.Frame(168, 360)
        }
    )
    
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
                // 隐藏文本层 - Hi, I'm ME AI (ind: 2)
                rememberLottieDynamicProperty(
                    property = LottieProperty.OPACITY,
                    value = 0,
                    keyPath = arrayOf("Hi, I'm ME AI", "**")
                )
            )
        )
    }
}

enum class LottieAnimationState {
    IDLE,
    LISTENING
}

class LottieAnimationStateManager {
    private val _currentState = mutableStateOf(LottieAnimationState.IDLE)
    val currentState: State<LottieAnimationState> = _currentState
    
    private val _displayText = mutableStateOf("I'm here for you!")
    val displayText: State<String> = _displayText
    
    fun setIdle(text: String = "I'm here for you!") {
        _displayText.value = text
        _currentState.value = LottieAnimationState.IDLE
    }
    
    fun setListening(text: String = "正在听取...") {
        _displayText.value = text
        _currentState.value = LottieAnimationState.LISTENING
    }
    
    fun setDisplayText(text: String) {
        _displayText.value = text
    }
}

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
