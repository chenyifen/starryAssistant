package com.ai.voice.ui.floating.components
import android.util.Log
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import androidx.compose.material3.MaterialTheme
// 移除拖拽处理器导入 - Hyundai IT版本不需要拖拽功能
import com.ai.voice.ui.floating.FloatingOrbConfig
import com.ai.voice.ui.floating.VoiceAssistantUIState
import com.ai.voice.ui.floating.components.FloatingTextDisplay
import com.ai.voice.ui.floating.components.LottieAnimationController
import com.ai.voice.ui.floating.components.LottieAnimationState
import com.ai.voice.ui.floating.components.LottieAnimationStateManager
import com.ai.voice.ui.floating.state.VoiceAssistantFullState
import com.ai.voice.ui.floating.state.VoiceAssistantStateProvider
import com.ai.voice.util.DebugLogger

/**
 * 悬浮球组件（Hyundai IT版本）
 * 
 * 特性：
 * - 使用WindowManager创建系统级悬浮窗
 * - 固定在屏幕左下角，不支持拖动
 * - 集成Lottie动画
 * - 显示ASR和TTS文本
 * - FLAG_NOT_FOCUSABLE避免抢焦点
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
    
    // Hyundai IT版本：悬浮球固定在左下角，不支持拖拽、点击、吸附等操作
    private val fixedX = 48  // 左边距16dp（约48px）
    private val fixedY = calculateBottomLeftY()  // 动态计算底部Y坐标
    
    // VoiceAssistantStateProvider监听
    private var stateProvider: VoiceAssistantStateProvider? = null
    private var stateListener: ((VoiceAssistantFullState) -> Unit)? = null
    
    /**
     * 计算左下角Y坐标
     */
    private fun calculateBottomLeftY(): Int {
        val displayMetrics = context.resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val orbHeight = 240 // 悬浮球高度（约80dp）
        val bottomMargin = 48 // 底部边距（约16dp）
        val navigationBarHeight = 144 // 导航栏高度（约48dp）

        // 获取状态栏高度（如果有）
        val statusBarHeight = getStatusBarHeight()

        // 计算可用高度：屏幕高度 - 状态栏高度
        val availableHeight = screenHeight - statusBarHeight

        return availableHeight - orbHeight - bottomMargin - navigationBarHeight
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            0 // 默认值，如果获取失败
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
            
            // Hyundai IT版本：移除所有触摸交互（拖动、点击、吸附等）
            // 悬浮球仅用于显示，不接受任何用户交互
            
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
            // 保存当前位置
            floatingView?.let { view ->
                val layoutParams = view.layoutParams as WindowManager.LayoutParams
                savedX = layoutParams.x
                savedY = layoutParams.y
            }
            
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
            
            // 窗口标志 - 关键：FLAG_NOT_FOCUSABLE避免抢焦点
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            
            // 像素格式 - 使用RGBA_8888支持完全透明
            format = PixelFormat.RGBA_8888
            
            // 窗口大小 - 固定宽度避免文本变化导致位置跳变，动态高度适应内容
            width = calculateWindowWidth()
            height = calculateWindowHeight()
            
            // 窗口位置 - Hyundai IT版本：固定在左下角
            gravity = Gravity.TOP or Gravity.START
            x = fixedX // 固定X位置：16dp左边距
            y = fixedY // 固定Y位置：动态计算底部位置
        }
    }
    
    /**
     * 计算窗口宽度 - 固定宽度避免文本变化导致位置跳变
     */
    private fun calculateWindowWidth(): Int {
        // 使用固定宽度，足够容纳最长的文本气泡（280dp + padding）
        val maxTextWidth = 280 // TextBubble的最大宽度
        val padding = 32 // 左右各16dp的padding
        val orbWidth = FloatingOrbConfig.orbSizePx
        
        // 取悬浮球宽度和文本区域宽度的最大值
        return maxOf(orbWidth.toInt(), maxTextWidth + padding)
    }
    
    /**
     * 计算窗口高度 - 动态适配内容
     */
    private fun calculateWindowHeight(): Int {
        val orbHeight = FloatingOrbConfig.orbSizePx
        
        var totalHeight = orbHeight.toInt()
        val spacing = 8 // dp转px的间距
        
        // ASR/TTS文本区域高度（如果有文本）
        val hasText = currentAsrText.value.isNotEmpty() || currentTtsText.value.isNotEmpty()
        if (hasText) {
            val textAreaHeight = 150
            totalHeight += textAreaHeight + spacing
        }
        
        // 添加底部边距，确保内容不会被截断
        totalHeight += 20
        
        return totalHeight
    }
    
    
    // 移除updatePosition方法 - Hyundai IT版本悬浮球固定位置
    
    
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
        
        // 修复：允许所有文本变化（包括空文本），确保文本能正确显示和清理
        // 只要文本发生变化就更新，包括：
        // 1. 从空到有文本（显示文本）
        // 2. 从有文本到空（清理文本）
        // 3. 文本内容变化（更新文本）
        if (asrTextChanged) {
            DebugLogger.logUI(TAG, "📝 更新ASR文本: '${state.asrText}' (之前: '${currentAsrText.value}')")
            currentAsrText.value = state.asrText
        }

        if (ttsTextChanged) {
            DebugLogger.logUI(TAG, "🤖 更新TTS文本: '${state.ttsText}' (之前: '${currentTtsText.value}')")
            currentTtsText.value = state.ttsText
        }
        
        // 性能优化：智能更新策略
        when {
            // 情况1：仅文本变化 - 使用文本就地更新，避免refreshUI()
            (asrTextChanged || ttsTextChanged) && !uiStateChanged && !displayTextChanged -> {
                updateTextOnly()
            }
            
            // 情况2：UI状态或显示文本变化 - 需要完整UI更新
            uiStateChanged || displayTextChanged -> {
                updateUIState(state)
                if (asrTextChanged || ttsTextChanged) {
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
        
        // 文本变化时需要更新窗口高度，但保持位置不变
        updateWindowHeightOnly()
    }
    
    /**
     * 仅更新窗口高度，保持位置不变
     */
    private fun updateWindowHeightOnly() {
        if (isShowing) {
            val currentView = floatingView
            if (currentView != null) {
                val layoutParams = currentView.layoutParams as WindowManager.LayoutParams
                val oldHeight = layoutParams.height
                val newHeight = calculateWindowHeight()
                
                // 只有高度真正变化时才更新
                if (oldHeight != newHeight) {
                    // 保存当前位置 - 重要：不改变X和Y坐标
                    val currentX = layoutParams.x
                    val currentY = layoutParams.y
                    
                    // 仅更新高度
                    layoutParams.height = newHeight
                    
                    try {
                        windowManager.updateViewLayout(currentView, layoutParams)
                    } catch (e: Exception) {
                        DebugLogger.logUI(TAG, "❌ Failed to update window height: ${e.message}")
                    }
                }
            }
        }
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
 * Hyundai IT版本：简化版，无交互功能
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
    
    // 固定动画尺寸（不再有边缘状态）
    val animationSize = FloatingOrbConfig.animationSizeDp
    val animationSizeInt = FloatingOrbConfig.animationSizeInt

    // Hyundai IT版本：水平布局 - 悬浮球在左，文本在右
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.wrapContentSize() // 自适应大小
    ) {
        // 悬浮球 - 左侧（无交互）
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
        
        // ASR/TTS文本显示区域 - 右侧
        if (shouldShowText) {
            FloatingTextDisplay(
                userText = currentAsrText,
                aiText = currentTtsText,
                isVisible = true,
                modifier = Modifier
                    .weight(1f) // 占据剩余空间
                    .padding(start = 8.dp)
            )
        }
    }
}


