package org.stypox.dicio.ui.floating.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.stypox.dicio.ui.floating.AssistantState
import org.stypox.dicio.ui.theme.EnergyBlue
import org.stypox.dicio.ui.theme.VioletGlow
import org.stypox.dicio.ui.theme.AuroraGreen
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun EnergyOrb(
    state: AssistantState,
    energyLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Float = 120f
) {
    val density = LocalDensity.current
    val sizeDp = with(density) { size.toDp() }
    
    // 呼吸动画
    val breathingAnimation by rememberInfiniteTransition(label = "breathing").animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    // 旋转动画（极光纹理）
    val rotationAnimation by rememberInfiniteTransition(label = "rotation").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // 声波动画（听取状态）
    val waveAnimation by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )

    // 思考状态的光点动画
    val thinkingAnimation by rememberInfiniteTransition(label = "thinking").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinking"
    )

    // 唤醒时的光环扩散动画
    var isAwakening by remember { mutableStateOf(false) }
    val awakeningScale by animateFloatAsState(
        targetValue = if (isAwakening) 2f else 1f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        finishedListener = { isAwakening = false },
        label = "awakening"
    )

    // 监听状态变化触发唤醒动画
    LaunchedEffect(state) {
        if (state == AssistantState.LISTENING) {
            isAwakening = true
        }
    }

    Box(
        modifier = modifier
            .size(sizeDp)
            .scale(breathingAnimation)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = sizeDp / 2),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val canvasSize = this.size
            val centerX = canvasSize.width / 2f
            val centerY = canvasSize.height / 2f
            val center = Offset(centerX, centerY)
            val radius = minOf(canvasSize.width, canvasSize.height) / 2f * 0.8f

            // 绘制主能量球
            drawEnergyOrb(
                center = center,
                radius = radius,
                energyLevel = energyLevel,
                rotation = rotationAnimation,
                state = state
            )

            // 根据状态绘制不同效果
            when (state) {
                AssistantState.LISTENING -> {
                    drawSoundWaves(
                        center = center,
                        radius = radius,
                        waveProgress = waveAnimation
                    )
                }
                AssistantState.THINKING -> {
                    drawThinkingParticles(
                        center = center,
                        radius = radius * 1.3f,
                        rotation = thinkingAnimation
                    )
                }
                AssistantState.IDLE -> {
                    // 待机状态只显示基础能量球
                }
            }

            // 唤醒光环
            if (isAwakening) {
                drawAwakeningRing(
                    center = center,
                    radius = radius * awakeningScale,
                    alpha = 1f - (awakeningScale - 1f)
                )
            }
        }
    }
}

private fun DrawScope.drawEnergyOrb(
    center: Offset,
    radius: Float,
    energyLevel: Float,
    rotation: Float,
    state: AssistantState
) {
    // 创建径向渐变
    val gradient = Brush.radialGradient(
        colors = listOf(
            EnergyBlue.copy(alpha = 0.8f * energyLevel),
            VioletGlow.copy(alpha = 0.6f * energyLevel),
            AuroraGreen.copy(alpha = 0.4f * energyLevel),
            Color.Transparent
        ),
        center = center,
        radius = radius
    )

    // 绘制主球体
    drawCircle(
        brush = gradient,
        radius = radius,
        center = center
    )

    // 绘制极光纹理
    rotate(rotation, center) {
        drawAuroraTexture(center, radius, energyLevel)
    }

    // 绘制外发光
    val glowRadius = radius * 1.2f
    val glowGradient = Brush.radialGradient(
        colors = listOf(
            EnergyBlue.copy(alpha = 0.3f * energyLevel),
            Color.Transparent
        ),
        center = center,
        radius = glowRadius
    )
    
    drawCircle(
        brush = glowGradient,
        radius = glowRadius,
        center = center
    )
}

private fun DrawScope.drawAuroraTexture(
    center: Offset,
    radius: Float,
    energyLevel: Float
) {
    val path = Path()
    val numWaves = 8
    
    for (i in 0 until numWaves) {
        val angle = (i * 360f / numWaves) * PI.toFloat() / 180f
        val waveRadius = radius * (0.6f + 0.3f * sin(angle * 3.0).toFloat())
        
        val x = center.x + cos(angle).toFloat() * waveRadius
        val y = center.y + sin(angle).toFloat() * waveRadius
        
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()
    
    drawPath(
        path = path,
        color = AuroraGreen.copy(alpha = 0.4f * energyLevel),
        style = Stroke(width = 2.dp.toPx())
    )
}

private fun DrawScope.drawSoundWaves(
    center: Offset,
    radius: Float,
    waveProgress: Float
) {
    val numRings = 3
    
    for (i in 0 until numRings) {
        val ringRadius = radius * (1.2f + 0.3f * i) * waveProgress
        val alpha = (1f - waveProgress) * (1f - i * 0.3f)
        
        drawCircle(
            color = EnergyBlue.copy(alpha = alpha * 0.6f),
            radius = ringRadius,
            center = center,
            style = Stroke(width = (3 - i).dp.toPx())
        )
    }
}

private fun DrawScope.drawThinkingParticles(
    center: Offset,
    radius: Float,
    rotation: Float
) {
    val numParticles = 3
    
    for (i in 0 until numParticles) {
        val angle = (rotation + i * 120f) * PI.toFloat() / 180f
        val particleRadius = 8.dp.toPx()
        val orbitRadius = radius
        
        val x = center.x + cos(angle).toFloat() * orbitRadius
        val y = center.y + sin(angle).toFloat() * orbitRadius
        
        // 粒子主体
        drawCircle(
            color = VioletGlow.copy(alpha = 0.8f),
            radius = particleRadius,
            center = Offset(x, y)
        )
        
        // 粒子拖尾
        val trailLength = particleRadius * 3
        val trailAngle = angle + PI.toFloat()
        val trailEnd = Offset(
            x + cos(trailAngle).toFloat() * trailLength,
            y + sin(trailAngle).toFloat() * trailLength
        )
        
        val trailGradient = Brush.linearGradient(
            colors = listOf(
                VioletGlow.copy(alpha = 0.6f),
                Color.Transparent
            ),
            start = Offset(x, y),
            end = trailEnd
        )
        
        drawLine(
            brush = trailGradient,
            start = Offset(x, y),
            end = trailEnd,
            strokeWidth = particleRadius / 2
        )
    }
}

private fun DrawScope.drawAwakeningRing(
    center: Offset,
    radius: Float,
    alpha: Float
) {
    val ringGradient = Brush.radialGradient(
        colors = listOf(
            Color.Transparent,
            EnergyBlue.copy(alpha = alpha * 0.8f),
            Color.Transparent
        ),
        center = center,
        radius = radius
    )
    
    drawCircle(
        brush = ringGradient,
        radius = radius,
        center = center,
        style = Stroke(width = 4.dp.toPx())
    )
}
