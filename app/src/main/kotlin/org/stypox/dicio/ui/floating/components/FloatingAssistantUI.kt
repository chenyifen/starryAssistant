package org.stypox.dicio.ui.floating.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.stypox.dicio.R
import org.stypox.dicio.ui.floating.AssistantState
import org.stypox.dicio.ui.floating.FloatingUiState
import org.stypox.dicio.ui.theme.*

@Composable
fun FloatingAssistantUI(
    uiState: FloatingUiState,
    onEnergyOrbClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCommandClick: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isFullScreen: Boolean = false
) {
    val context = LocalContext.current
    
    Box(
        modifier = modifier
            .wrapContentSize() // 根据内容调整大小
            .background(
                if (isFullScreen) {
                    // 满屏模式：深色半透明背景
                    DeepSpace.copy(alpha = 0.8f)
                } else {
                    // 小窗模式：透明背景
                    Color.Transparent
                }
            )
            .padding(if (isFullScreen) 32.dp else 4.dp), // 进一步减少小窗模式的padding
        contentAlignment = if (isFullScreen) Alignment.Center else Alignment.TopCenter // 小窗模式向上对齐
    ) {
        // 背景模糊效果（仅在显示命令建议时）
        if (uiState.showCommandSuggestions) {
            Box(
                modifier = Modifier
                    .size(400.dp) // 限制背景大小，不占据整个屏幕
                    .background(DeepSpace.copy(alpha = 0.3f))
                    .blur(8.dp)
                    .clickable { onDismiss() }
            )
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isFullScreen) 16.dp else 2.dp), // 进一步减少间距
            modifier = Modifier.wrapContentSize()
        ) {
            // 语音UI动画（Lottie）- 缩小尺寸，紧凑布局，内置状态文本
            VoiceUIAnimationWithStatusText(
                state = uiState.assistantState,
                energyLevel = uiState.energyLevel,
                onClick = onEnergyOrbClick,
                size = if (isFullScreen) 200 else 60,  // 大幅缩小动画尺寸
                isWakeWordActive = uiState.isWakeWordActive,
                isFullScreen = isFullScreen
            )
            
            // 主要文本显示区域 - 显示ASR和TTS文本
            VoiceTextDisplay(
                asrText = uiState.asrText,
                ttsText = uiState.ttsText,
                state = uiState.assistantState,
                isFullScreen = isFullScreen
            )
            
            // 设置图标 - 只在有足够空间时显示
            if (isFullScreen || uiState.asrText.isBlank() && uiState.ttsText.isBlank()) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PolyhedronSettingsIcon(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .size(if (isFullScreen) 40.dp else 20.dp) // 进一步缩小设置图标
                            .align(Alignment.TopEnd)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusText(
    state: AssistantState,
    isWakeWordActive: Boolean,
    isFullScreen: Boolean = false
) {
    val context = LocalContext.current
    
    // 获取当前系统语言
    val currentLanguage = java.util.Locale.getDefault().language
    
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
    
    Text(
        text = text,
        color = textColor.copy(alpha = glowAnimation),
        fontSize = if (isFullScreen) 18.sp else 10.sp,  // 进一步缩小字体
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = if (isFullScreen) 16.dp else 4.dp) // 减少水平padding
    )
}

@Composable
private fun VoiceTextDisplay(
    asrText: String,
    ttsText: String,
    state: AssistantState,
    isFullScreen: Boolean = false
) {
    // 只有在有文本时才显示
    if (asrText.isBlank() && ttsText.isBlank()) {
        return
    }
    
    // 动态宽度，小窗模式更紧凑
    val maxWidth = if (isFullScreen) 700.dp else 180.dp
    
    // 添加调试日志
    android.util.Log.d("FloatingAssistantUI", "🎨 VoiceTextDisplay渲染: asrText='$asrText', ttsText='$ttsText', isFullScreen=$isFullScreen")
    
    Card(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .wrapContentHeight() // 根据内容调整高度
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = DeepSpace.copy(alpha = if (isFullScreen) 0.8f else 0.9f) // 小窗模式稍微不透明一些
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFullScreen) 8.dp else 2.dp), // 小窗模式减少阴影
        shape = RoundedCornerShape(if (isFullScreen) 16.dp else 6.dp) // 小窗模式更小的圆角
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isFullScreen) 24.dp else 8.dp), // 进一步减少小窗模式的padding
            verticalArrangement = Arrangement.spacedBy(if (isFullScreen) 12.dp else 4.dp) // 进一步减少间距
        ) {
            // ASR实时识别文本区域 - 只有有文本时才显示
            if (asrText.isNotBlank()) {
                AsrTextSection(
                    asrText = asrText,
                    state = state,
                    isFullScreen = isFullScreen
                )
            }
            
            // TTS回复文本区域 - 只有有文本时才显示
            if (ttsText.isNotBlank()) {
                TtsTextSection(
                    ttsText = ttsText,
                    isFullScreen = isFullScreen
                )
            }
        }
    }
}


@Composable
private fun AsrTextSection(
    asrText: String,
    state: AssistantState,
    isFullScreen: Boolean
) {
    // 简化的ASR文本显示，只显示内容
    if (asrText.isNotEmpty()) {
        Text(
            text = asrText,
            color = EnergyBlue,
            fontSize = if (isFullScreen) 16.sp else 10.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = if (isFullScreen) 22.sp else 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = EnergyBlue.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(if (isFullScreen) 12.dp else 6.dp)
        )
    } else if (state == AssistantState.LISTENING) {
        // 监听状态显示简单的指示
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = EnergyBlue.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(if (isFullScreen) 12.dp else 6.dp)
        ) {
            ListeningIndicator(isFullScreen = isFullScreen)
            Spacer(modifier = Modifier.width(if (isFullScreen) 8.dp else 4.dp))
            Text(
                text = "正在聆听...",
                color = EnergyBlue.copy(alpha = 0.7f),
                fontSize = if (isFullScreen) 14.sp else 8.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}

@Composable
private fun TtsTextSection(
    ttsText: String,
    isFullScreen: Boolean
) {
    // 简化的TTS文本显示，只显示内容
    if (ttsText.isNotEmpty()) {
        Text(
            text = ttsText,
            color = AuroraGreen,
            fontSize = if (isFullScreen) 16.sp else 10.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = if (isFullScreen) 22.sp else 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = AuroraGreen.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(if (isFullScreen) 12.dp else 6.dp)
        )
    }
}

@Composable
private fun ListeningIndicator(isFullScreen: Boolean) {
    // 监听指示器的波浪动画
    val waveAnimation by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave"
    )
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(if (isFullScreen) 6.dp else 4.dp)
                    .background(
                        color = AuroraGreen.copy(
                            alpha = if (index == 1) waveAnimation else waveAnimation * 0.6f
                        ),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
        }
    }
}

@Composable
private fun CommandSuggestionsPanel(
    onCommandClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val commands = listOf(
        "打开计算器" to "calculator",
        "打开相机" to "camera", 
        "今天天气" to "weather",
        "设置闹钟" to "alarm",
        "播放音乐" to "music",
        "发送消息" to "message"
    )
    
    Card(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = GalaxyGray.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "推荐命令",
                color = EnergyBlue,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            commands.chunked(2).forEach { rowCommands ->
                Row(
                    modifier = Modifier.wrapContentWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowCommands.forEach { (displayText, command) ->
                        CommandButton(
                            text = displayText,
                            onClick = { onCommandClick(command) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // 如果行中只有一个命令，添加空白占位
                    if (rowCommands.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CommandButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = VioletGlow.copy(alpha = 0.2f),
            contentColor = EnergyBlue
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PolyhedronSettingsIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 旋转动画
    val rotationAnimation by rememberInfiniteTransition(label = "rotation").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        VioletGlow.copy(alpha = 0.3f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings",
            tint = VioletGlow,
            modifier = Modifier
                .size(24.dp)
                // TODO: 替换为多面体图标
        )
    }
}
