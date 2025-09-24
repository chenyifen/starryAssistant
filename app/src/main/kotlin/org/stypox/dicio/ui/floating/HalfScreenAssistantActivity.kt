package org.stypox.dicio.ui.floating

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.stypox.dicio.util.DebugLogger

/**
 * 半屏助手Activity
 * 
 * 特性：
 * - 透明背景，只占据屏幕下半部分
 * - 从底部滑入动画
 * - 不干扰上半屏的内容
 * - 自动收起功能
 * - 支持语音交互界面
 */
class HalfScreenAssistantActivity : ComponentActivity() {

    private val TAG = "HalfScreenAssistantActivity"

    private var isContentVisible by mutableStateOf(false)
    private var shouldFinish by mutableStateOf(false)

    companion object {
        private const val EXTRA_TRIGGER_TYPE = "trigger_type"
        private const val TRIGGER_TYPE_CLICK = "click"
        private const val TRIGGER_TYPE_WAKE_WORD = "wake_word"
        
        /**
         * 启动半屏助手 - 点击触发
         */
        fun startFromClick(context: Context) {
            val intent = Intent(context, HalfScreenAssistantActivity::class.java).apply {
                putExtra(EXTRA_TRIGGER_TYPE, TRIGGER_TYPE_CLICK)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or
                       Intent.FLAG_ACTIVITY_NO_ANIMATION
            }
            context.startActivity(intent)
        }
        
        /**
         * 启动半屏助手 - 语音唤醒触发
         */
        fun startFromWakeWord(context: Context) {
            val intent = Intent(context, HalfScreenAssistantActivity::class.java).apply {
                putExtra(EXTRA_TRIGGER_TYPE, TRIGGER_TYPE_WAKE_WORD)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or
                       Intent.FLAG_ACTIVITY_NO_ANIMATION
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        DebugLogger.logUI(TAG, "🎭 HalfScreenAssistantActivity created")
        
        // 设置透明主题和窗口属性
        setupTransparentWindow()
        
        // 获取触发类型
        val triggerType = intent.getStringExtra(EXTRA_TRIGGER_TYPE) ?: TRIGGER_TYPE_CLICK
        DebugLogger.logUI(TAG, "🎯 Triggered by: $triggerType")
        
        setContent {
            HalfScreenAssistantScreen(
                triggerType = triggerType,
                onDismiss = { dismissWithAnimation() },
                onFinish = { finishActivity() }
            )
        }
        
        // 启动进入动画
        isContentVisible = true
        
        // 设置自动收起定时器
        startAutoDismissTimer()
    }
    
    /**
     * 设置透明窗口属性
     */
    private fun setupTransparentWindow() {
        // 设置窗口为透明
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
        }
        
        // 设置状态栏和导航栏透明
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        
        // 设置系统UI可见性
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = 
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    
    /**
     * 启动自动收起定时器
     */
    private fun startAutoDismissTimer() {
        // 使用协程启动定时器
        lifecycleScope.launch {
            delay(FloatingOrbConfig.Animation.AUTO_DISMISS_DELAY)
            if (!shouldFinish) {
                DebugLogger.logUI(TAG, "⏰ Auto dismiss triggered")
                dismissWithAnimation()
            }
        }
    }
    
    /**
     * 带动画的收起
     */
    private fun dismissWithAnimation() {
        DebugLogger.logUI(TAG, "📉 Dismissing with animation")
        isContentVisible = false
        
        // 延迟后关闭Activity
        lifecycleScope.launch {
            delay(FloatingOrbConfig.Animation.CONTRACT_DURATION)
            finishActivity()
        }
    }
    
    /**
     * 完成Activity
     */
    private fun finishActivity() {
        DebugLogger.logUI(TAG, "🏁 Finishing activity")
        shouldFinish = true
        
        // 通知Service收起悬浮球（通过广播）
        sendBroadcast(android.content.Intent("org.stypox.dicio.HALF_SCREEN_DISMISSED"))
        
        finish()
        overridePendingTransition(0, 0) // 无动画退出
    }
    
    override fun onBackPressed() {
        DebugLogger.logUI(TAG, "🔙 Back pressed")
        dismissWithAnimation()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        DebugLogger.logUI(TAG, "💀 HalfScreenAssistantActivity destroyed")
    }
}

/**
 * 半屏助手界面
 */
@Composable
fun HalfScreenAssistantScreen(
    triggerType: String,
    onDismiss: () -> Unit,
    onFinish: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    val screenHeight = with(density) { configuration.screenHeightDp.dp }
    val halfScreenHeight = screenHeight / 2
    
    var isAnimationVisible by remember { mutableStateOf(false) }
    
    // 启动进入动画
    LaunchedEffect(Unit) {
        delay(100) // 短暂延迟确保界面已准备好
        isAnimationVisible = true
    }
    
    // 动画状态
    val slideOffset by animateDpAsState(
        targetValue = if (isAnimationVisible) 0.dp else halfScreenHeight,
        animationSpec = tween(
            durationMillis = FloatingOrbConfig.Animation.EXPAND_DURATION.toInt(),
            easing = FastOutSlowInEasing
        ),
        label = "slideAnimation"
    )
    
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isAnimationVisible) 0.3f else 0f,
        animationSpec = tween(
            durationMillis = FloatingOrbConfig.Animation.EXPAND_DURATION.toInt(),
            easing = FastOutSlowInEasing
        ),
        label = "backgroundAlpha"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = backgroundAlpha))
    ) {
        // 半屏卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(halfScreenHeight)
                .offset(y = slideOffset)
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            HalfScreenAssistantContent(
                triggerType = triggerType,
                onDismiss = onDismiss,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 半屏助手内容
 */
@Composable
fun HalfScreenAssistantContent(
    triggerType: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 顶部指示器
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 标题
        Text(
            text = when (triggerType) {
                "wake_word" -> "语音助手已激活"
                else -> "我在这里为您服务"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 主要内容区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 状态指示
                when (triggerType) {
                    "wake_word" -> {
                        Text(
                            text = "🎤 正在聆听...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        // 音频波形动画占位
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "音频波形动画区域",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = "👋 您好！我是您的智能助手",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Text(
                            text = "请说出您的需求，我会尽力帮助您。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // 底部操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text("收起")
            }
            
            Button(
                onClick = { 
                    // TODO: 启动语音识别
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("开始对话")
            }
        }
    }
}
