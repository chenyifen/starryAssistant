package org.stypox.dicio.ui.floating.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import org.stypox.dicio.util.DebugLogger

/**
 * 悬浮球文本显示组件
 * 
 * 在悬浮球下方显示2行文本：
 * - 第一行：用户语音转录文本
 * - 第二行：AI回复文本
 */
@Composable
fun FloatingTextDisplay(
    userText: String,
    aiText: String,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val TAG = "FloatingTextDisplay"
    
    // 动画状态
    val animatedVisibility by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "textVisibility"
    )
    
    // 记录文本变化
    LaunchedEffect(userText) {
        if (userText.isNotEmpty()) {
            DebugLogger.logUI(TAG, "👤 User text updated: $userText")
        }
    }
    
    LaunchedEffect(aiText) {
        if (aiText.isNotEmpty()) {
            DebugLogger.logUI(TAG, "🤖 AI text updated: $aiText")
        }
    }
    
    // 只有当有文本内容时才显示
    val hasContent = userText.isNotEmpty() || aiText.isNotEmpty()
    
    if (hasContent) {
        
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .graphicsLayer {
                    alpha = animatedVisibility
                    scaleX = 0.8f + (0.2f * animatedVisibility)
                    scaleY = 0.8f + (0.2f * animatedVisibility)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 用户文本（第一行）
            AnimatedVisibility(
                visible = userText.isNotEmpty(),
                enter = slideInVertically(
                    animationSpec = tween(300),
                    initialOffsetY = { -it }
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    animationSpec = tween(200),
                    targetOffsetY = { -it }
                ) + fadeOut(animationSpec = tween(200))
            ) {
                TextBubble(
                    text = userText,
                    isUser = true
                )
            }
            
            // AI文本（第二行）
            AnimatedVisibility(
                visible = aiText.isNotEmpty(),
                enter = slideInVertically(
                    animationSpec = tween(300, delayMillis = 100),
                    initialOffsetY = { it }
                ) + fadeIn(animationSpec = tween(300, delayMillis = 100)),
                exit = slideOutVertically(
                    animationSpec = tween(200),
                    targetOffsetY = { it }
                ) + fadeOut(animationSpec = tween(200))
            ) {
                TextBubble(
                    text = aiText,
                    isUser = false
                )
            }
        }
    }
}

/**
 * 文本气泡组件
 */
@Composable
private fun TextBubble(
    text: String,
    isUser: Boolean,
    modifier: Modifier = Modifier
) {
    
    val backgroundColor = if (isUser) {
        Color(0xFF2196F3).copy(alpha = 0.9f) // 用户文本：蓝色
    } else {
        Color(0xFF4CAF50).copy(alpha = 0.9f) // AI文本：绿色
    }
    
    val textColor = Color.White
    
    Box(
        modifier = modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (isUser) FontWeight.Normal else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp
        )
    }
}

/**
 * 文本显示状态管理器
 */
class FloatingTextStateManager(private val context: Context) {
    private val TAG = "FloatingTextStateManager"
    
    private val _userText = mutableStateOf("")
    val userText: State<String> = _userText
    
    private val _aiText = mutableStateOf("")
    val aiText: State<String> = _aiText
    
    private val _isVisible = mutableStateOf(false)
    val isVisible: State<Boolean> = _isVisible
    
    /**
     * 设置用户文本（ASR转录结果）
     */
    fun setUserText(text: String) {
        DebugLogger.logUI(TAG, "📝 Setting user text: $text")
        _userText.value = text
        _isVisible.value = text.isNotEmpty() || _aiText.value.isNotEmpty()
    }
    
    /**
     * 设置AI文本（TTS回复）
     */
    fun setAiText(text: String) {
        DebugLogger.logUI(TAG, "🤖 Setting AI text: $text")
        _aiText.value = text
        _isVisible.value = _userText.value.isNotEmpty() || text.isNotEmpty()
    }
    
    /**
     * 清空所有文本
     */
    fun clearAllText() {
        DebugLogger.logUI(TAG, "🧹 Clearing all text")
        _userText.value = ""
        _aiText.value = ""
        _isVisible.value = false
    }
    
    /**
     * 开始新的对话（清空旧文本）
     */
    fun startNewConversation() {
        DebugLogger.logUI(TAG, "🆕 Starting new conversation")
        clearAllText()
    }
    
    /**
     * 设置正在听取状态
     */
    fun setListening() {
        DebugLogger.logUI(TAG, "👂 Setting listening state")
        _userText.value = context.getString(FloatingTextConstants.LISTENING)
        _aiText.value = ""
        _isVisible.value = true
    }
    
    /**
     * 设置正在思考状态
     */
    fun setThinking() {
        DebugLogger.logUI(TAG, "🤔 Setting thinking state")
        _aiText.value = context.getString(FloatingTextConstants.THINKING)
        _isVisible.value = true
    }
    
    /**
     * 设置正在处理状态
     */
    fun setProcessing() {
        DebugLogger.logUI(TAG, "⚙️ Setting processing state")
        _userText.value = context.getString(FloatingTextConstants.PROCESSING)
        _isVisible.value = true
    }
    
    /**
     * 设置唤醒检测状态
     */
    fun setWakeDetected() {
        DebugLogger.logUI(TAG, "🎯 Setting wake detected state")
        _userText.value = context.getString(FloatingTextConstants.WAKE_DETECTED)
        _aiText.value = ""
        _isVisible.value = true
    }
    
    /**
     * 设置错误状态
     */
    fun setError() {
        DebugLogger.logUI(TAG, "❌ Setting error state")
        _aiText.value = context.getString(FloatingTextConstants.ERROR)
        _isVisible.value = true
    }
    
    /**
     * 设置准备就绪状态
     */
    fun setReady() {
        DebugLogger.logUI(TAG, "✅ Setting ready state")
        _aiText.value = context.getString(FloatingTextConstants.READY)
        _isVisible.value = true
    }
    
    /**
     * 设置正在说话状态
     */
    fun setSpeaking() {
        DebugLogger.logUI(TAG, "🗣️ Setting speaking state")
        // AI文本会通过setAiText设置实际内容
        _isVisible.value = true
    }
    
    /**
     * 获取调试信息
     */
    fun getDebugInfo(): String {
        return "FloatingTextState(user='${_userText.value}', ai='${_aiText.value}', visible=${_isVisible.value})"
    }
}

/**
 * 预定义的状态文本资源ID
 */
object FloatingTextConstants {
    val DEFAULT = org.stypox.dicio.R.string.floating_text_idle
    val LISTENING = org.stypox.dicio.R.string.floating_text_listening
    val PROCESSING = org.stypox.dicio.R.string.floating_text_processing
    val THINKING = org.stypox.dicio.R.string.floating_text_thinking
    val SPEAKING = org.stypox.dicio.R.string.floating_text_speaking
    val READY = org.stypox.dicio.R.string.floating_text_ready
    val WAKE_DETECTED = org.stypox.dicio.R.string.floating_text_wake_detected
    val ERROR = org.stypox.dicio.R.string.floating_text_error
    val NO_INPUT = org.stypox.dicio.R.string.floating_text_no_input
    val TIMEOUT = org.stypox.dicio.R.string.floating_text_timeout
    val INITIALIZING = org.stypox.dicio.R.string.floating_text_initializing
    val LOADING = org.stypox.dicio.R.string.floating_text_loading
}
