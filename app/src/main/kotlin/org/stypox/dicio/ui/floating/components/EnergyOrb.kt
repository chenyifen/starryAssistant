package org.stypox.dicio.ui.floating.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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

// 支持的能量环形状
enum class EnergyShapeType {
    CIRCLE,      // 圆环
    POLYGON,     // 多边形
    STAR         // 星形
}

@Composable
fun EnergyOrb(
    state: AssistantState,
    energyLevel: Float, // 0.0 ~ 1.0
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Float = 120f,
    shapeType: EnergyShapeType = EnergyShapeType.CIRCLE,
    targetShapeType: EnergyShapeType = shapeType,
    polygonSides: Int = 3
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

    // 旋转动画
    val rotationAnimation by rememberInfiniteTransition(label = "rotation").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // 声波动画
    val waveAnimation by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )

    // 思考粒子动画
    val thinkingAnimation by rememberInfiniteTransition(label = "thinking").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinking"
    )

    // 脉冲动画（能量爆发）
    val pulseAnimation by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    // 唤醒动画
    var isAwakening by remember { mutableStateOf(false) }
    val awakeningScale by animateFloatAsState(
        targetValue = if (isAwakening) 2f else 1f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        finishedListener = { isAwakening = false },
        label = "awakening"
    )

    LaunchedEffect(state) {
        if (state == AssistantState.LISTENING) {
            isAwakening = true
        }
    }

    // 形状过渡动画
    val shapeProgress by animateFloatAsState(
        targetValue = if (shapeType == targetShapeType) 0f else 1f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "shapeMorph"
    )

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
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = this.size
            val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
            val radius = minOf(canvasSize.width, canvasSize.height) / 2f * 0.8f

            // 主能量球 + 光环变形
            drawEnergyOrb(
                center, radius, energyLevel,
                rotationAnimation, shapeType, targetShapeType,
                polygonSides, shapeProgress
            )

            // 状态特效
            when (state) {
                AssistantState.LISTENING -> drawSoundWaves(center, radius, waveAnimation)
                AssistantState.THINKING -> drawThinkingParticles(center, radius * 1.3f, thinkingAnimation)
                AssistantState.IDLE -> {}
            }

            // 能量脉冲（能量大于0.6时触发）
            if (energyLevel > 0.6f) {
                drawEnergyPulse(center, radius, energyLevel, pulseAnimation)
            }

            // 唤醒光环
            if (isAwakening) {
                drawAwakeningRing(center, radius * awakeningScale, 1f - (awakeningScale - 1f))
            }
        }
    }
}

private fun DrawScope.drawEnergyOrb(
    center: Offset,
    radius: Float,
    energyLevel: Float,
    rotation: Float,
    shapeType: EnergyShapeType,
    targetShapeType: EnergyShapeType,
    polygonSides: Int,
    shapeProgress: Float
) {
    // 球体核心
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
    drawCircle(brush = gradient, radius = radius, center = center)

    // 外发光
    val glowRadius = radius * 1.2f
    val glowGradient = Brush.radialGradient(
        colors = listOf(EnergyBlue.copy(alpha = 0.3f * energyLevel), Color.Transparent),
        center = center,
        radius = glowRadius
    )
    drawCircle(brush = glowGradient, radius = glowRadius, center = center)

    // 绘制混合光环
    rotate(rotation, center) {
        drawMorphingShape(center, radius, energyLevel, shapeType, targetShapeType, polygonSides, rotation, shapeProgress)
    }

    // 径向模糊感
    for (i in 1..3) {
        drawCircle(
            color = EnergyBlue.copy(alpha = 0.05f * energyLevel),
            radius = radius * (1f + i * 0.15f),
            center = center
        )
    }
}

// ---- 形状混合绘制，能量驱动波动 ----
private fun DrawScope.drawMorphingShape(
    center: Offset,
    radius: Float,
    energyLevel: Float,
    fromShape: EnergyShapeType,
    toShape: EnergyShapeType,
    polygonSides: Int,
    rotation: Float,
    progress: Float
) {
    val path = Path()
    val points = 120
    val time = (System.currentTimeMillis() % 4000L) / 4000f
    val waveAmplitude = radius * 0.05f * energyLevel // 能量驱动波动幅度

    for (i in 0..points) {
        val angle = (i.toFloat() / points) * 2f * PI.toFloat()

        // ---- 起始形状半径 ----
        val rFrom = when (fromShape) {
            EnergyShapeType.CIRCLE -> radius * 0.9f +
                sin(angle * 6 + time * 2 * PI).toFloat() * waveAmplitude
            EnergyShapeType.POLYGON -> {
                radius * (0.7f + 0.2f * sin(time * 2 * PI).toFloat())
            }
            EnergyShapeType.STAR -> if (i % 2 == 0) radius else radius * 0.5f
        }

        // ---- 目标形状半径 ----
        val rTo = when (toShape) {
            EnergyShapeType.CIRCLE -> radius * 0.9f +
                sin(angle * 6 + time * 2 * PI).toFloat() * waveAmplitude
            EnergyShapeType.POLYGON -> {
                radius * (0.7f + 0.2f * sin(time * 2 * PI).toFloat())
            }
            EnergyShapeType.STAR -> if (i % 2 == 0) radius else radius * 0.5f
        }

        // ---- 插值半径 ----
        val r = rFrom * (1 - progress) + rTo * progress

        val x = center.x + cos(angle) * r
        val y = center.y + sin(angle) * r

        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()

    // 发光渐变
    val glowBrush = Brush.radialGradient(
        colors = listOf(
            EnergyBlue.copy(alpha = 0.6f * energyLevel),
            VioletGlow.copy(alpha = 0.4f * energyLevel),
            Color.Transparent
        ),
        center = center,
        radius = radius * 1.3f
    )

    drawPath(path, brush = glowBrush, style = Stroke(width = 4.dp.toPx()))
}

// ================= 状态特效 =================

private fun DrawScope.drawSoundWaves(center: Offset, radius: Float, waveProgress: Float) {
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

private fun DrawScope.drawThinkingParticles(center: Offset, radius: Float, rotation: Float) {
    val numParticles = 3
    for (i in 0 until numParticles) {
        val angle = (rotation + i * 120f) * PI.toFloat() / 180f
        val particleRadius = 8.dp.toPx()
        val orbitRadius = radius
        val x = center.x + cos(angle) * orbitRadius
        val y = center.y + sin(angle) * orbitRadius

        drawCircle(color = VioletGlow.copy(alpha = 0.8f), radius = particleRadius, center = Offset(x, y))

        val trailLength = particleRadius * 3
        val trailAngle = angle + PI.toFloat()
        val trailEnd = Offset(
            x + cos(trailAngle) * trailLength,
            y + sin(trailAngle) * trailLength
        )
        val trailGradient = Brush.linearGradient(
            colors = listOf(VioletGlow.copy(alpha = 0.6f), Color.Transparent),
            start = Offset(x, y),
            end = trailEnd
        )
        drawLine(brush = trailGradient, start = Offset(x, y), end = trailEnd, strokeWidth = particleRadius / 2)
    }
}

private fun DrawScope.drawAwakeningRing(center: Offset, radius: Float, alpha: Float) {
    val ringGradient = Brush.radialGradient(
        colors = listOf(Color.Transparent, EnergyBlue.copy(alpha = alpha * 0.8f), Color.Transparent),
        center = center,
        radius = radius
    )
    drawCircle(brush = ringGradient, radius = radius, center = center, style = Stroke(width = 4.dp.toPx()))
}

// ================= 多重能量脉冲 =================
private fun DrawScope.drawEnergyPulse(
    center: Offset,
    radius: Float,
    energyLevel: Float,
    pulseProgress: Float
) {
    val maxPulseRadius = radius * 2.2f
    val layers = 3 // 脉冲层数
    val phaseShift = 1f / layers

    for (i in 0 until layers) {
        val localProgress = (pulseProgress + i * phaseShift) % 1f
        val currentRadius = radius + (maxPulseRadius - radius) * localProgress
        val alpha = (1f - localProgress) * (energyLevel * 0.5f)

        drawCircle(
            color = EnergyBlue.copy(alpha = alpha),
            radius = currentRadius,
            center = center,
            style = Stroke(width = (4 + i).dp.toPx())
        )
    }
}