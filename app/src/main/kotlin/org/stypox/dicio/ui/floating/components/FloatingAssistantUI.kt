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
            .fillMaxSize()
            .background(
                if (isFullScreen) {
                    // 满屏模式：深色半透明背景
                    DeepSpace.copy(alpha = 0.8f)
                } else {
                    // 小窗模式：调试边框
                    Color.Red.copy(alpha = 0.2f)
                }
            )
            .padding(if (isFullScreen) 32.dp else 16.dp),
        contentAlignment = Alignment.Center
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
            verticalArrangement = Arrangement.spacedBy(if (isFullScreen) 16.dp else 8.dp),
            modifier = Modifier.wrapContentSize()
        ) {
            // 语音UI动画（Lottie）- 根据模式调整大小
            VoiceUIAnimationWithFallback(
                state = uiState.assistantState,
                energyLevel = uiState.energyLevel,
                onClick = onEnergyOrbClick,
                size = if (isFullScreen) 280 else 120  // 调整动画大小，为文本留出更多空间
            )
            
            // 状态文本 - 根据模式调整字体大小
            StatusText(
                state = uiState.assistantState,
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
            
            // 设置图标 - 放在右上角
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                PolyhedronSettingsIcon(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .size(if (isFullScreen) 40.dp else 28.dp)
                        .align(Alignment.TopEnd)
                )
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
    val text = when (state) {
        AssistantState.IDLE -> if (isWakeWordActive) {
            "Hi Nudget: 하이넛지"
        } else {
            "叫我\"小艺小艺\""
        }
        AssistantState.LISTENING -> "듣고 있어요…"
        AssistantState.THINKING -> "생각하고 있어요…"
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
        fontSize = if (isFullScreen) 20.sp else 12.sp,  // 满屏模式使用更大字体
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = if (isFullScreen) 16.dp else 8.dp)
    )
}

@Composable
private fun VoiceTextDisplay(
    asrText: String,
    ttsText: String,
    state: AssistantState,
    isFullScreen: Boolean = false
) {
    // 动态高度和宽度
    val maxWidth = if (isFullScreen) 700.dp else 280.dp
    val minHeight = if (isFullScreen) 120.dp else 100.dp // 小窗模式下也需要足够的高度
    
    // 添加调试日志
    android.util.Log.d("FloatingAssistantUI", "🎨 VoiceTextDisplay渲染: asrText='$asrText', ttsText='$ttsText', isFullScreen=$isFullScreen")
    
    Card(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .heightIn(min = minHeight)
            .animateContentSize(), // 恢复动态尺寸
        colors = CardDefaults.cardColors(
            containerColor = DeepSpace.copy(alpha = 0.8f) // 恢复原来的背景色
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(if (isFullScreen) 16.dp else 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isFullScreen) 24.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (isFullScreen) 12.dp else 8.dp)
        ) {
            // 当前状态指示器
            CurrentStateIndicator(
                state = state,
                isFullScreen = isFullScreen
            )
            
            // ASR实时识别文本区域
            AsrTextSection(
                asrText = asrText,
                state = state,
                isFullScreen = isFullScreen
            )
            
            // TTS回复文本区域
            TtsTextSection(
                ttsText = ttsText,
                isFullScreen = isFullScreen
            )
        }
    }
}

@Composable
private fun CurrentStateIndicator(
    state: AssistantState,
    isFullScreen: Boolean
) {
    val (text, color, icon) = when (state) {
        AssistantState.IDLE -> Triple("待机中", VioletGlow.copy(alpha = 0.7f), "💤")
        AssistantState.LISTENING -> Triple("正在听取...", AuroraGreen, "🎧")
        AssistantState.THINKING -> Triple("正在思考...", EnergyBlue, "🤔")
    }
    
    // 状态指示器的脉冲动画
    val pulseAnimation by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = icon,
            fontSize = if (isFullScreen) 20.sp else 16.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = text,
            color = color.copy(alpha = pulseAnimation),
            fontSize = if (isFullScreen) 16.sp else 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AsrTextSection(
    asrText: String,
    state: AssistantState,
    isFullScreen: Boolean
) {
    Column {
        // ASR标题
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "🎤 您说：",
                color = EnergyBlue.copy(alpha = 0.8f),
                fontSize = if (isFullScreen) 14.sp else 10.sp,
                fontWeight = FontWeight.Medium
            )
            
            // 如果正在监听，显示动态指示器
            if (state == AssistantState.LISTENING) {
                Spacer(modifier = Modifier.width(8.dp))
                ListeningIndicator(isFullScreen = isFullScreen)
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // ASR文本内容
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = GalaxyGray.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(if (isFullScreen) 16.dp else 12.dp)
                .heightIn(min = if (isFullScreen) 40.dp else 30.dp)
        ) {
            if (asrText.isNotEmpty()) {
                Text(
                    text = asrText,
                    color = EnergyBlue,
                    fontSize = if (isFullScreen) 16.sp else 12.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = if (isFullScreen) 22.sp else 16.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                val placeholderText = if (state == AssistantState.LISTENING) "正在识别您的语音..." else "等待语音输入"
                Text(
                    text = placeholderText,
                    color = VioletGlow.copy(alpha = 0.5f),
                    fontSize = if (isFullScreen) 14.sp else 10.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
        }
    }
}

@Composable
private fun TtsTextSection(
    ttsText: String,
    isFullScreen: Boolean
) {
    Column {
        // TTS标题
        Text(
            text = "🤖 小艺回复：",
            color = AuroraGreen.copy(alpha = 0.8f),
            fontSize = if (isFullScreen) 14.sp else 10.sp,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // TTS文本内容
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color.Black.copy(alpha = 0.8f), // 使用更深的背景色增强对比度
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(if (isFullScreen) 16.dp else 12.dp)
                .heightIn(min = if (isFullScreen) 40.dp else 30.dp)
        ) {
            // 强制显示测试文本，确保组件可见
            Text(
                text = if (ttsText.isNotEmpty()) "TTS: $ttsText" else "TTS: 测试文本 - 如果你能看到这个，说明组件正常",
                color = Color.Cyan, // 使用明显的青色
                fontSize = if (isFullScreen) 18.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = if (isFullScreen) 24.sp else 18.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
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
