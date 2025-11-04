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
import com.ai.voice.ui.floating.components.FloatingTextDisplay
import com.ai.voice.ui.floating.components.LottieAnimationController
import com.ai.voice.ui.floating.components.LottieAnimationStateManager
import com.ai.voice.ui.floating.state.VoiceAssistantFullState
import com.ai.voice.ui.floating.state.VoiceAssistantStateProvider
import com.ai.voice.util.DebugLogger

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
    
    // 动画状态管理器
    private val animationStateManager = LottieAnimationStateManager()
    
    // 当前文本状态 - 使用MutableState以便Compose能检测变化
    private val currentAsrText = mutableStateOf("")
    private val currentTtsText = mutableStateOf("")
    
    // 性能优化：状态缓存
    private var lastUiState: VoiceAssistantUIState? = null
    private var lastDisplayText = ""
    
    // VoiceAssistantStateProvider监听
    private var stateProvider: VoiceAssistantStateProvider? = null
    private var stateListener: ((VoiceAssistantFullState) -> Unit)? = null
    
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
                // 不使用AppTheme，因为Service不是Activity
                // 使用完全透明的背景
                
                // ⚡ 性能优化：延迟加载复杂UI，避免阻塞主线程
                var isFullyInitialized by remember { mutableStateOf(false) }
                
                LaunchedEffect(Unit) {
                    // 延迟100ms，让主线程有时间处理其他任务
                    kotlinx.coroutines.delay(100)
                    isFullyInitialized = true
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
                            .background(Color.Transparent)
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
    
    /**
     * 创建WindowManager布局参数
     */
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
            
            // 窗口位置 - 固定左下角，不需要x和y坐标
            gravity = Gravity.BOTTOM or Gravity.START
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
            currentAsrText.value = state.asrText
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

/**
 * 悬浮球内容组件 (包含Lottie动画和右侧的ASR/TTS文本显示)
 * 简化版 - 不支持交互，文本显示在球体右侧
 */
@Composable
private fun FloatingOrbContent(
    animationStateManager: LottieAnimationStateManager,
    currentAsrText: String,
    currentTtsText: String
) {
    val animationState by animationStateManager.currentState
    val displayText by animationStateManager.displayText
    
    // 性能优化：使用 remember 缓存计算结果
    val shouldShowText = remember(currentAsrText, currentTtsText) {
        currentAsrText.isNotEmpty() || currentTtsText.isNotEmpty()
    }
    
    // 性能优化：使用固定的动画尺寸
    val animationSize = FloatingOrbConfig.animationSizeDp
    val animationSizeInt = FloatingOrbConfig.animationSizeInt

    // 横向布局 - 球体在左，文本在右
    Row(
        modifier = Modifier
            .wrapContentSize()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 悬浮球 - 不可点击，仅显示
        Box(
            modifier = Modifier.size(animationSize),
            contentAlignment = Alignment.Center
        ) {
            // Lottie动画
            LottieAnimationController(
                animationState = animationState,
                displayText = displayText,
                size = animationSizeInt
            )
        }
        
        // ASR/TTS文本显示区域 - 在悬浮球右侧
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


