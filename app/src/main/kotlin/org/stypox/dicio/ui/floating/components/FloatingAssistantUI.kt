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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // 背景模糊效果
        if (uiState.showCommandSuggestions) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DeepSpace.copy(alpha = 0.3f))
                    .blur(8.dp)
                    .clickable { onDismiss() }
            )
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 设置图标（右上角多面体）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                PolyhedronSettingsIcon(
                    onClick = onSettingsClick,
                    modifier = Modifier.offset(x = 60.dp, y = (-60).dp)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 主要内容区域
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 能量球
                EnergyOrb(
                    state = uiState.assistantState,
                    energyLevel = uiState.energyLevel,
                    onClick = onEnergyOrbClick,
                    size = 120f
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 状态文本
                StatusText(
                    state = uiState.assistantState,
                    isWakeWordActive = uiState.isWakeWordActive
                )
                
                // ASR和TTS文本显示
                if (uiState.assistantState != AssistantState.IDLE) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    VoiceTextDisplay(
                        asrText = uiState.asrText,
                        ttsText = uiState.ttsText,
                        state = uiState.assistantState
                    )
                }
                
                // 命令建议面板
                AnimatedVisibility(
                    visible = uiState.showCommandSuggestions,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(300)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(300))
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    CommandSuggestionsPanel(
                        onCommandClick = onCommandClick,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusText(
    state: AssistantState,
    isWakeWordActive: Boolean
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
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun VoiceTextDisplay(
    asrText: String,
    ttsText: String,
    state: AssistantState
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GalaxyGray.copy(alpha = 0.8f),
                        DeepSpace.copy(alpha = 0.6f)
                    )
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
            .widthIn(max = 280.dp)
    ) {
        // ASR文本
        if (asrText.isNotEmpty()) {
            Text(
                text = asrText,
                color = EnergyBlue,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // 分隔线
        if (asrText.isNotEmpty() && ttsText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(
                color = VioletGlow.copy(alpha = 0.3f),
                thickness = 1.dp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // TTS文本
        if (ttsText.isNotEmpty()) {
            Text(
                text = ttsText,
                color = AuroraGreen,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
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
                    modifier = Modifier.fillMaxWidth(),
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
