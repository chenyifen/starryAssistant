package com.ai.voice.ui.floating.components
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import com.ai.voice.ui.floating.FloatingOrbConfig
import com.ai.voice.ui.floating.VoiceAssistantUIState
import com.ai.voice.ui.floating.components.LottieAnimationStateManager
import com.ai.voice.ui.floating.state.VoiceAssistantFullState
import com.ai.voice.ui.floating.state.VoiceAssistantStateProvider
import com.ai.voice.util.DebugLogger
import com.ai.voice.util.AsrHandler
import com.ai.voice.license.LicenseActivationManager

/**
 * 悬浮球组件（简化版 - 不可拖动、不可点击）
 * 
 * 特性：
 * - 使用WindowManager创建系统级悬浮窗
 * - 仅显示动画和文本，不支持任何交互
 * - 集成Lottie动画
 * - FLAG_NOT_TOUCHABLE确保不可点击和拖动
 */
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
    private val activationStatusText = mutableStateOf("")
    
    private var lastResultListSize = 0
    
    private var lastUiState: VoiceAssistantUIState? = null
    private var lastDisplayText = ""
    
    private var stateProvider: VoiceAssistantStateProvider? = null
    private var stateListener: ((VoiceAssistantFullState) -> Unit)? = null
    
    var filterAsrTextEnabled: Boolean = true
    
    /**
     * 过滤ASR文本：只保留英语和韩语字符，去除所有标点符号
     * @param text 原始文本
     * @return 过滤后的文本
     */
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
    
    /**
     * 显示悬浮球
     */
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
                var isFullyInitialized by remember { mutableStateOf(false) }
                
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(100)
                    isFullyInitialized = true
                }
                
                LaunchedEffect(Unit) {
                    while (true) {
                        kotlinx.coroutines.delay(100)
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
                
                LaunchedEffect(Unit) {
                    while (true) {
                        kotlinx.coroutines.delay(2000)
                        checkAndUpdateActivationStatus()
                    }
                }
                
                if (!isFullyInitialized) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Transparent)
                    )
                } else {
                    val asrText by currentAsrText
                    val ttsText by currentTtsText
                    val statusText by activationStatusText
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Transparent),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        FloatingOrbContent(
                            animationStateManager = animationStateManager,
                            currentAsrText = asrText,
                            currentTtsText = ttsText,
                            activationStatus = statusText
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
            
            animationStateManager.setIdle()
            setupStateProviderListener()
            checkAndUpdateActivationStatus()
            
        } catch (e: Exception) {
            DebugLogger.logUI(TAG, "❌ Error showing floating orb: ${e.message}")
        }
    }
    
    /**
     * 隐藏悬浮球
     */
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
            DebugLogger.logUI(TAG, "❌ Error hiding floating orb: ${e.message}")
        }
    }
    
    /**
     * 获取动画状态管理器
     */
    fun getAnimationStateManager(): LottieAnimationStateManager = animationStateManager
    
    /**
     * 获取当前ASR文本
     */
    fun getCurrentAsrText(): String = currentAsrText.value
    
    /**
     * 获取当前TTS文本
     */
    fun getCurrentTtsText(): String = currentTtsText.value
    
    private fun checkAndUpdateActivationStatus() {
        try {
            val manager = LicenseActivationManager.getInstance()
            val isActivated = manager.isActivated()
            activationStatusText.value = if (isActivated) "" else LottieAnimationTexts.NOT_ACTIVATED
        } catch (e: Exception) {
            DebugLogger.logUI(TAG, "Check activation failed: ${e.message}")
            activationStatusText.value = ""
        }
    }
    
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
    
    
    
    /**
     * 设置VoiceAssistantStateProvider监听
     */
    private fun setupStateProviderListener() {
        try {
            stateProvider = VoiceAssistantStateProvider.getInstance()
            stateListener = { state ->
                handleVoiceAssistantStateChange(state)
            }
            stateListener?.let { listener ->
                stateProvider?.addListener(listener)
            }
        } catch (e: Exception) {
            DebugLogger.logUI(TAG, "❌ Failed to setup VoiceAssistantStateProvider listener: ${e.message}")
        }
    }

    /**
     * 清理VoiceAssistantStateProvider监听
     */
    private fun cleanupStateProviderListener() {
        try {
            stateListener?.let { listener ->
                stateProvider?.removeListener(listener)
            }
            stateProvider = null
            stateListener = null
        } catch (e: Exception) {
            DebugLogger.logUI(TAG, "❌ Failed to cleanup VoiceAssistantStateProvider listener: ${e.message}")
        }
    }

    /**
     * 处理语音助手状态变化
     */
    private fun handleVoiceAssistantStateChange(state: VoiceAssistantFullState) {
        // 性能优化：检测变化类型
        val asrTextChanged = currentAsrText.value != state.asrText
        val ttsTextChanged = currentTtsText.value != state.ttsText
        val uiStateChanged = lastUiState != state.uiState
        val displayTextChanged = lastDisplayText != state.displayText
        
        // 更新文本状态（过滤空文本变化）
        // 注意：如果新文本为空且旧文本也为空，则不触发更新
        val shouldUpdateAsr = asrTextChanged && !(state.asrText.isEmpty() && currentAsrText.value.isEmpty())
        val shouldUpdateTts = ttsTextChanged && !(state.ttsText.isEmpty() && currentTtsText.value.isEmpty())
        
        if (shouldUpdateAsr) {
            // 如果启用过滤，则过滤文本
            val textToSet = if (filterAsrTextEnabled) {
                filterAsrText(state.asrText)
            } else {
                state.asrText
            }
            currentAsrText.value = textToSet
        }
        
        if (shouldUpdateTts) {
            currentTtsText.value = state.ttsText
        }
        
        // 性能优化：智能更新策略
        when {
            // 情况1：仅文本变化 - 使用文本就地更新，避免refreshUI()
            (shouldUpdateAsr || shouldUpdateTts) && !uiStateChanged && !displayTextChanged -> {
                updateTextOnly()
            }
            
            // 情况2：UI状态或显示文本变化 - 需要完整UI更新
            uiStateChanged || displayTextChanged -> {
                updateUIState(state)
                if (shouldUpdateAsr || shouldUpdateTts) {
                    updateTextOnly()
                }
            }
        }
    }
    
    /**
     * 性能优化：文本就地更新 - 避免refreshUI()
     */
    private fun updateTextOnly() {
        // Compose会自动检测状态变化并重组相关组件
        // 无需调用refreshUI()，大幅提升性能
        // 窗口大小使用WRAP_CONTENT，会自动适配内容变化
    }
    
    /**
     * 性能优化：UI状态更新 - 带缓存的状态切换
     */
    private fun updateUIState(state: VoiceAssistantFullState) {
        // 更新缓存
        lastUiState = state.uiState
        lastDisplayText = state.displayText
        
        // 根据UI状态更新动画 - 中央状态文本
        when (state.uiState) {
            VoiceAssistantUIState.IDLE -> {
                animationStateManager.setIdle()
            }
            VoiceAssistantUIState.WAKE_DETECTED -> {
                val displayText = state.displayText.ifBlank { "LISTENING" }
                animationStateManager.triggerWakeWord(displayText)
            }
            VoiceAssistantUIState.LISTENING -> {
                animationStateManager.setActive("LISTENING")
            }
            VoiceAssistantUIState.THINKING -> {
                animationStateManager.setLoading()
            }
            VoiceAssistantUIState.SPEAKING -> {
                animationStateManager.setActive("SPEAKING")
            }
            VoiceAssistantUIState.ERROR -> {
                val displayText = state.displayText.ifBlank { "ERROR" }
                animationStateManager.setActive(displayText)
            }
        }
    }
    
    
}

@Composable
private fun FloatingOrbContent(
    animationStateManager: LottieAnimationStateManager,
    currentAsrText: String,
    currentTtsText: String,
    activationStatus: String = ""
) {
    val animationState by animationStateManager.currentState
    val displayText by animationStateManager.displayText
    
    val shouldShowText = remember(currentAsrText, currentTtsText) {
        currentAsrText.isNotEmpty() || currentTtsText.isNotEmpty()
    }
    
    val animationSize = FloatingOrbConfig.animationSizeDp
    val animationSizeInt = FloatingOrbConfig.animationSizeInt

    Column(
        modifier = Modifier
            .wrapContentSize()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        if (activationStatus.isNotEmpty()) {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
            androidx.compose.material3.Surface(
                color = Color(0xFFFF3B30).copy(alpha = 0.9f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            ) {
                androidx.compose.material3.Text(
                    text = activationStatus,
                    color = Color.White,
                    fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
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


