package com.ai.voice.ui.floating.components
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import com.ai.voice.ui.floating.FloatingOrbConfig
import com.ai.voice.ui.floating.VoiceAssistantUIState
import com.ai.voice.ui.floating.components.LottieAnimationStateManager
import com.ai.voice.ui.floating.components.LottieAnimationState
import com.ai.voice.ui.floating.state.VoiceAssistantFullState
import com.ai.voice.ui.floating.state.VoiceAssistantStateProvider
import com.ai.voice.util.DebugLogger
import com.ai.voice.util.AsrHandler

class DraggableFloatingOrb(
    private val context: Context,
    private val lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    private val viewModelStoreOwner: ViewModelStoreOwner,
    private val savedStateRegistryOwner: SavedStateRegistryOwner
) {
    private val TAG = "DraggableFloatingOrb"
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatingView: View? = null
    private var isShowing = false
    private val animationStateManager = LottieAnimationStateManager()
    private val currentAsrText = mutableStateOf("")
    private val currentTtsText = mutableStateOf("")
    private var lastUiState: VoiceAssistantUIState? = null
    private var lastDisplayText = ""
    private var stateProvider: VoiceAssistantStateProvider? = null
    private var stateListener: ((VoiceAssistantFullState) -> Unit)? = null
    var filterAsrTextEnabled: Boolean = true
    
    private fun filterAsrText(text: String): String {
        return text.filter { char ->
            // 英语字符：a-z, A-Z, 0-9
            val isEnglish = char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9'
            // 韩语字符：Hangul Syllables (AC00-D7AF)
            val isKorean = char.code in 0xAC00..0xD7AF
            // 保留空格
            val isSpace = char == ' '
            isEnglish || isKorean || isSpace
        }
    }
    
    fun show() {
        if (isShowing) return
        
        // 初始化配置
        FloatingOrbConfig.initialize(context)
        
        try {
            val composeView = ComposeView(context)
            
            // 设置透明背景
            composeView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            // 设置生命周期相关的TreeOwner
            composeView.setViewTreeLifecycleOwner(lifecycleOwner)
            composeView.setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            composeView.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
            
            composeView.setContent {
                // 不使用AppTheme，因为Service不是Activity
                // 使用完全透明的背景
                
                // ⚡ 性能优化：延迟加载复杂UI，避免阻塞主线程
                var isFullyInitialized by remember { mutableStateOf(false) }
                
                LaunchedEffect(Unit) {
                    // 延迟100ms，让主线程有时间处理其他任务
                    kotlinx.coroutines.delay(100)
                    isFullyInitialized = true
                }
                
                // 监听 AsrHandler 状态变化
                LaunchedEffect(Unit) {
                    while (true) {
                        kotlinx.coroutines.delay(100)
                        val isAsrStarted = AsrHandler.isStarted()
                        
                        val currentState = animationStateManager.currentState.value
                        when {
                            isAsrStarted && currentState != LottieAnimationState.LISTENING -> {
                                animationStateManager.setListening("正在听取...")
                            }
                            !isAsrStarted && currentState != LottieAnimationState.IDLE -> {
                                val currentText = animationStateManager.displayText.value
                                animationStateManager.setIdle(currentText)
                            }
                        }
                        
                        val resultList = AsrHandler.getResultList()
                        if (resultList.isNotEmpty()) {
                            var latestText = resultList.last()
                            if (filterAsrTextEnabled) {
                                latestText = filterAsrText(latestText)
                            }
                            if (currentAsrText.value != latestText) {
                                currentAsrText.value = latestText
                            }
                        }
                    }
                }
                
                if (!isFullyInitialized) {
                    // 简单占位符 - 快速渲染
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Transparent)
                    )
                } else {
                    // 在Composable内部读取状态，以便触发重组
                    val asrText by currentAsrText
                    val ttsText by currentTtsText
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Transparent),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        FloatingOrbContent(
                            animationStateManager = animationStateManager,
                            currentAsrText = asrText,
                            currentTtsText = ttsText
                        )
                    }
                }
            }
            
            val layoutParams = createWindowLayoutParams()
            windowManager.addView(composeView, layoutParams)
            
            floatingView = composeView
            isShowing = true
            
            // 移除所有触摸交互，设置为不可点击
            composeView.isClickable = false
            composeView.isFocusable = false
            
            // 默认设置为待机状态
            animationStateManager.setIdle()
            
            // 设置VoiceAssistantStateProvider监听
            setupStateProviderListener()
            
        } catch (e: Exception) {
            DebugLogger.logUI(TAG, "Error showing floating orb: ${e.message}")
        }
    }
    
    fun hide() {
        if (!isShowing) return
        
        try {
            // 清理状态监听
            cleanupStateProviderListener()
            
            floatingView?.let { view ->
                windowManager.removeView(view)
                floatingView = null
                isShowing = false
            }
        } catch (e: Exception) {
            DebugLogger.logUI(TAG, "Error hiding floating orb: ${e.message}")
        }
    }
    
    fun getAnimationStateManager(): LottieAnimationStateManager = animationStateManager
    
    private fun createWindowLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            // 窗口类型
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            
            // 窗口标志 - 设置为完全不可交互
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            
            // 像素格式 - 使用RGBA_8888支持完全透明
            format = PixelFormat.RGBA_8888
            
            // 窗口大小 - 动态适配内容（横向布局）
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            
            // 窗口位置 - 固定左下角，添加左边距和底边距
            gravity = Gravity.BOTTOM or Gravity.LEFT
            x = 0
            y = 0
        }
    }
    
    private fun setupStateProviderListener() {
        try {
            stateProvider = VoiceAssistantStateProvider.getInstance()
            stateListener = { state ->
                handleVoiceAssistantStateChange(state)
            }
            stateListener?.let { listener ->
                stateProvider?.addListener(listener)
            }
        } catch (e: Exception) { }
    }

    private fun cleanupStateProviderListener() {
        try {
            stateListener?.let { listener ->
                stateProvider?.removeListener(listener)
            }
            stateProvider = null
            stateListener = null
        } catch (e: Exception) { }
    }

    private fun handleVoiceAssistantStateChange(state: VoiceAssistantFullState) {
        val asrTextChanged = currentAsrText.value != state.asrText
        val ttsTextChanged = currentTtsText.value != state.ttsText
        val uiStateChanged = lastUiState != state.uiState
        
        val shouldUpdateAsr = asrTextChanged && !(state.asrText.isEmpty() && currentAsrText.value.isEmpty())
        val shouldUpdateTts = ttsTextChanged && !(state.ttsText.isEmpty() && currentTtsText.value.isEmpty())
        
        if (shouldUpdateAsr) {
            currentAsrText.value = if (filterAsrTextEnabled) filterAsrText(state.asrText) else state.asrText
        }
        
        if (shouldUpdateTts) {
            currentTtsText.value = state.ttsText
        }
        
        if (uiStateChanged) {
            updateUIState(state)
        }
    }
    
    fun updateStatusText(statusText: String) {
        val currentState = animationStateManager.currentState.value
        when (currentState) {
            LottieAnimationState.IDLE -> {
                animationStateManager.setIdle(statusText)
            }
            LottieAnimationState.LISTENING -> {
                animationStateManager.setDisplayText(statusText)
            }
        }
    }
    
    private fun updateUIState(state: VoiceAssistantFullState) {
        lastUiState = state.uiState
        lastDisplayText = state.displayText
    }
}

@Composable
private fun FloatingOrbContent(
    animationStateManager: LottieAnimationStateManager,
    currentAsrText: String,
    currentTtsText: String
) {
    val animationState by animationStateManager.currentState
    val displayText by animationStateManager.displayText
    val shouldShowText = currentAsrText.isNotEmpty() || currentTtsText.isNotEmpty()
    val animationSize = FloatingOrbConfig.animationSizeDp
    val animationSizeInt = FloatingOrbConfig.animationSizeInt
    val shouldShowStatusText = displayText.isNotEmpty() && displayText != "I'm here for you!"

    Column(
        modifier = Modifier
            .wrapContentSize()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (shouldShowStatusText) {
            StatusTextDisplay(
                text = displayText,
                modifier = Modifier.wrapContentWidth()
            )
        }
        
        Row(
            modifier = Modifier.wrapContentSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(animationSize),
                contentAlignment = Alignment.Center
            ) {
                LottieAnimationController(
                    animationState = animationState,
                    displayText = displayText,
                    size = animationSizeInt
                )
            }
            
            if (shouldShowText) {
                FloatingTextDisplay(
                    userText = currentAsrText,
                    aiText = currentTtsText,
                    isVisible = true,
                    modifier = Modifier.wrapContentWidth()
                )
            }
        }
    }
}

@Composable
private fun StatusTextDisplay(
    text: String,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Text(
        text = text,
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(Color(0xFF424242).copy(alpha = 0.85f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
    )
}
