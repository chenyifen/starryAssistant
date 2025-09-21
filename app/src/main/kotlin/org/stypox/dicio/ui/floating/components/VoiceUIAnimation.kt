package org.stypox.dicio.ui.floating.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import org.stypox.dicio.ui.floating.AssistantState
import org.stypox.dicio.ui.theme.*
import java.util.Locale

@Composable
fun VoiceUIAnimation(
    state: AssistantState,
    energyLevel: Float, // 0.0 ~ 1.0
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 120
) {
    val context = LocalContext.current
    
    // 加载Lottie动画
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("models/openWakeWord/Voice UI.json")
    )
    
    // 根据状态决定是否播放动画
    val isPlaying = when (state) {
        AssistantState.IDLE -> false
        AssistantState.LISTENING -> true
        AssistantState.THINKING -> true
    }
    
    // 动画进度
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = isPlaying,
        iterations = if (isPlaying) LottieConstants.IterateForever else 1,
        speed = 0.5f + (energyLevel * 1.5f) // 根据能量级别调整速度
    )
    
    Box(
        modifier = modifier
            .size(size.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = (size / 4).dp) // 缩小ripple效果
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun VoiceUIAnimationWithFallback(
    state: AssistantState,
    energyLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 120
) {
    val context = LocalContext.current
    
    // 尝试加载Lottie动画
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("models/openWakeWord/Voice UI.json")
    )
    
    if (composition != null) {
        // 如果Lottie动画加载成功，使用Lottie动画
        VoiceUIAnimation(
            state = state,
            energyLevel = energyLevel,
            onClick = onClick,
            modifier = modifier,
            size = size
        )
    } else {
        // 如果Lottie动画加载失败或正在加载，显示基础画面
        BasicVoiceIndicator(
            state = state,
            energyLevel = energyLevel,
            onClick = onClick,
            modifier = modifier,
            size = size
        )
    }
}

@Composable
fun BasicVoiceIndicator(
    state: AssistantState,
    energyLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 120
) {
    // 呼吸动画
    val breathingAnimation by rememberInfiniteTransition(label = "breathing").animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )
    
    // 根据状态确定颜色
    val color = when (state) {
        AssistantState.IDLE -> EnergyBlue
        AssistantState.LISTENING -> AuroraGreen
        AssistantState.THINKING -> VioletGlow
    }
    
    // 根据状态确定是否有脉冲效果
    val pulseScale = if (state != AssistantState.IDLE) breathingAnimation else 1f
    
    Box(
        modifier = modifier
            .size(size.dp)
            .scale(pulseScale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = (size / 4).dp)
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = (this.size.minDimension / 2f) * 0.6f
            
            // 绘制基础圆形
            drawCircle(
                color = color.copy(alpha = 0.3f),
                radius = radius,
                center = center
            )
            
            // 绘制内部圆形（根据energyLevel调整大小）
            val innerRadius = radius * (0.3f + energyLevel * 0.4f)
            drawCircle(
                color = color.copy(alpha = 0.8f),
                radius = innerRadius,
                center = center
            )
            
            // 如果在监听或思考状态，绘制额外的光环
            if (state != AssistantState.IDLE) {
                drawCircle(
                    color = color.copy(alpha = 0.2f),
                    radius = radius * 1.3f,
                    center = center
                )
            }
        }
    }
}

@Composable
fun VoiceUIAnimationWithStatusText(
    state: AssistantState,
    energyLevel: Float,
    onClick: () -> Unit,
    size: Int = 120,
    isWakeWordActive: Boolean,
    isFullScreen: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // 尝试加载Lottie动画
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("models/openWakeWord/Voice UI.json")
    )
    
    Box(
        modifier = modifier.wrapContentSize(),
        contentAlignment = Alignment.Center
    ) {
        if (composition != null) {
            // 如果Lottie动画加载成功，使用Lottie动画
            VoiceUIAnimation(
                state = state,
                energyLevel = energyLevel,
                onClick = onClick,
                size = size
            )
        } else {
            // 如果Lottie动画加载失败或正在加载，显示基础画面
            BasicVoiceIndicator(
                state = state,
                energyLevel = energyLevel,
                onClick = onClick,
                size = size
            )
        }
        
        // 状态文本显示在圆形内部稍下方
        StatusTextOverlay(
            state = state,
            isWakeWordActive = isWakeWordActive,
            isFullScreen = isFullScreen,
            size = size
        )
    }
}

@Composable
private fun StatusTextOverlay(
    state: AssistantState,
    isWakeWordActive: Boolean,
    isFullScreen: Boolean,
    size: Int
) {
    // 获取当前系统语言
    val currentLanguage = Locale.getDefault().language
    
    val text = when (state) {
        AssistantState.IDLE -> if (isWakeWordActive) {
            when (currentLanguage) {
                "zh" -> "说\"Hi Nudget\"唤醒我"
                "ko" -> "\"Hi Nudget\"라고 말해주세요"
                "en" -> "Say \"Hi Nudget\" to wake me"
                else -> "Say \"Hi Nudget\" to wake me"
            }
        } else {
            when (currentLanguage) {
                "zh" -> "叫我\"小艺小艺\""
                "ko" -> "\"작은 예술\"이라고 불러주세요"
                "en" -> "Call me \"Little Art\""
                else -> "Call me \"Little Art\""
            }
        }
        AssistantState.LISTENING -> when (currentLanguage) {
            "zh" -> "正在聆听..."
            "ko" -> "듣고 있어요..."
            "en" -> "Listening..."
            else -> "Listening..."
        }
        AssistantState.THINKING -> when (currentLanguage) {
            "zh" -> "正在思考..."
            "ko" -> "생각하고 있어요..."
            "en" -> "Thinking..."
            else -> "Thinking..."
        }
    }
    
    val textColor = when (state) {
        AssistantState.IDLE -> EnergyBlue.copy(alpha = 0.8f)
        AssistantState.LISTENING -> AuroraGreen
        AssistantState.THINKING -> VioletGlow
    }
    
    // 发光效果动画
    val glowAnimation by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    // 文本位置：在圆形稍下方
    Box(
        modifier = Modifier
            .size(size.dp)
            .offset(y = (size * 0.1f).dp), // 向下偏移10%的圆形大小
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor.copy(alpha = glowAnimation),
            fontSize = if (isFullScreen) 14.sp else 6.sp,  // 适应圆形内部的小字体
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2, // 允许换行
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
