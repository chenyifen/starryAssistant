package com.ai.voice.ui.floating.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import android.content.Context
import androidx.compose.material3.Text
import com.ai.voice.util.DebugLogger

@Composable
fun FloatingTextDisplay(
    userText: String,
    aiText: String,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedVisibility by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "textVisibility"
    )
    
    val hasContent = userText.isNotEmpty() || aiText.isNotEmpty()
    
    if (hasContent) {
        
        Column(
            modifier = modifier
                .wrapContentWidth()
                .graphicsLayer {
                    alpha = animatedVisibility
                    scaleX = 0.8f + (0.2f * animatedVisibility)
                    scaleY = 0.8f + (0.2f * animatedVisibility)
                },
            horizontalAlignment = Alignment.Start,
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
            textAlign = TextAlign.Start,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp
        )
    }
}

class FloatingTextStateManager(private val context: Context) {
    private val _userText = mutableStateOf("")
    val userText: State<String> = _userText
    
    private val _aiText = mutableStateOf("")
    val aiText: State<String> = _aiText
    
    private val _isVisible = mutableStateOf(false)
    val isVisible: State<Boolean> = _isVisible
    
    fun setUserText(text: String) {
        if (text.isEmpty() || text == _userText.value) return
        _userText.value = text
        _isVisible.value = text.isNotEmpty() || _aiText.value.isNotEmpty()
    }
    
    fun setAiText(text: String) {
        if (text.isEmpty() || text == _aiText.value) return
        _aiText.value = text
        _isVisible.value = _userText.value.isNotEmpty() || text.isNotEmpty()
    }
    
    fun clearAllText() {
        _userText.value = ""
        _aiText.value = ""
        _isVisible.value = false
    }
    
    fun startNewConversation() {
        clearAllText()
    }
    
    fun setListening() {
        _userText.value = context.getString(FloatingTextConstants.LISTENING)
        _aiText.value = ""
        _isVisible.value = true
    }
    
    fun setThinking() {
        _aiText.value = context.getString(FloatingTextConstants.THINKING)
        _isVisible.value = true
    }
    
    fun setProcessing() {
        _userText.value = context.getString(FloatingTextConstants.PROCESSING)
        _isVisible.value = true
    }
    
    fun setWakeDetected() {
        _userText.value = context.getString(FloatingTextConstants.WAKE_DETECTED)
        _aiText.value = ""
        _isVisible.value = true
    }
    
    fun setError() {
        _aiText.value = context.getString(FloatingTextConstants.ERROR)
        _isVisible.value = true
    }
    
    fun setReady() {
        _aiText.value = context.getString(FloatingTextConstants.READY)
        _isVisible.value = true
    }
    
    fun setSpeaking() {
        _isVisible.value = true
    }
}

object FloatingTextConstants {
    val DEFAULT = com.ai.voice.R.string.floating_text_idle
    val LISTENING = com.ai.voice.R.string.floating_text_listening
    val PROCESSING = com.ai.voice.R.string.floating_text_processing
    val THINKING = com.ai.voice.R.string.floating_text_thinking
    val SPEAKING = com.ai.voice.R.string.floating_text_speaking
    val READY = com.ai.voice.R.string.floating_text_ready
    val WAKE_DETECTED = com.ai.voice.R.string.floating_text_wake_detected
    val ERROR = com.ai.voice.R.string.floating_text_error
    val NO_INPUT = com.ai.voice.R.string.floating_text_no_input
    val TIMEOUT = com.ai.voice.R.string.floating_text_timeout
    val INITIALIZING = com.ai.voice.R.string.floating_text_initializing
    val LOADING = com.ai.voice.R.string.floating_text_loading
}
