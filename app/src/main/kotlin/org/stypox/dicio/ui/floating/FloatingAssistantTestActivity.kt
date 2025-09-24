package org.stypox.dicio.ui.floating

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import org.stypox.dicio.ui.theme.AppTheme
import org.stypox.dicio.util.DebugLogger

/**
 * 悬浮助手测试Activity
 * 
 * 用于测试悬浮球功能：
 * - 权限申请
 * - 启动/停止悬浮球服务
 * - 动画状态测试
 */
@AndroidEntryPoint
class FloatingAssistantTestActivity : ComponentActivity() {
    
    private val TAG = "FloatingAssistantTestActivity"
    
    // 权限请求启动器
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (Settings.canDrawOverlays(this)) {
            DebugLogger.logUI(TAG, "✅ Overlay permission granted")
        } else {
            DebugLogger.logUI(TAG, "❌ Overlay permission denied")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        DebugLogger.logUI(TAG, "🚀 FloatingAssistantTestActivity created")
        
        setContent {
            AppTheme {
                FloatingAssistantTestScreen(
                    onRequestPermission = { requestOverlayPermission() },
                    onStartService = { startFloatingService() },
                    onStopService = { stopFloatingService() }
                )
            }
        }
    }
    
    /**
     * 请求悬浮窗权限
     */
    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            DebugLogger.logUI(TAG, "🔐 Requesting overlay permission")
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            DebugLogger.logUI(TAG, "✅ Overlay permission already granted")
        }
    }
    
    /**
     * 启动悬浮球服务
     */
    private fun startFloatingService() {
        if (Settings.canDrawOverlays(this)) {
            DebugLogger.logUI(TAG, "🎈 Starting floating service")
            EnhancedFloatingWindowService.start(this)
        } else {
            DebugLogger.logUI(TAG, "❌ Cannot start service - no overlay permission")
        }
    }
    
    /**
     * 停止悬浮球服务
     */
    private fun stopFloatingService() {
        DebugLogger.logUI(TAG, "🛑 Stopping floating service")
        EnhancedFloatingWindowService.stop(this)
    }
}

/**
 * 测试界面
 */
@Composable
fun FloatingAssistantTestScreen(
    onRequestPermission: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember { 
        mutableStateOf(Settings.canDrawOverlays(context)) 
    }
    
    // 定期检查权限状态
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1000)
        hasPermission = Settings.canDrawOverlays(context)
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = "🎈 悬浮助手测试",
            style = MaterialTheme.typography.headlineMedium
        )
        
        // 权限状态
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "权限状态",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (hasPermission) "✅ 已授权" else "❌ 未授权",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (hasPermission) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.error
                )
            }
        }
        
        // 权限申请按钮
        if (!hasPermission) {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔐 申请悬浮窗权限")
            }
        }
        
        // 服务控制按钮
        if (hasPermission) {
            Button(
                onClick = onStartService,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🚀 启动悬浮球")
            }
            
            Button(
                onClick = onStopService,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("🛑 停止悬浮球")
            }
        }
        
        // 使用说明
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "使用说明",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = """
                        1. 首先申请悬浮窗权限
                        2. 启动悬浮球服务
                        3. 悬浮球将显示在屏幕上
                        4. 点击悬浮球测试交互
                        5. 长按悬浮球测试拖动
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        // 动画状态说明
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "动画状态",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = """
                        🔵 待机状态 - 缓慢呼吸效果
                        🟡 加载状态 - 跳动小点
                        🟢 激活状态 - 显示文本和光晕
                        🔴 唤醒状态 - 快速激活动画
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
