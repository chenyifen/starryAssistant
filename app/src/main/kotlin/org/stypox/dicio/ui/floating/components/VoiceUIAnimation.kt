package org.stypox.dicio.ui.floating.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.*
import org.stypox.dicio.ui.floating.AssistantState

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
                indication = ripple(bounded = false, radius = (size / 2).dp)
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
        // 如果Lottie动画加载失败，回退到原始的EnergyOrb
        EnergyOrb(
            state = state,
            energyLevel = energyLevel,
            onClick = onClick,
            modifier = modifier,
            size = size.toFloat()
        )
    }
}
