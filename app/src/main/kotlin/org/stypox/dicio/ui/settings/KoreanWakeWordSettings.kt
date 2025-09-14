package org.stypox.dicio.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.stypox.dicio.R
import org.stypox.dicio.util.DebugLogger
import org.stypox.dicio.util.KoreanWakeWordManager
import org.stypox.dicio.util.WakeWordInfo

/**
 * 韩语唤醒词设置组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KoreanWakeWordSettings(
    modifier: Modifier = Modifier,
    onWakeWordChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var currentWakeWordInfo by remember { mutableStateOf<WakeWordInfo?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    
    // 初始化时获取当前唤醒词信息
    LaunchedEffect(Unit) {
        currentWakeWordInfo = KoreanWakeWordManager.getCurrentWakeWordInfo(context)
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题
            Text(
                text = "唤醒词设置",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            // 当前唤醒词信息
            currentWakeWordInfo?.let { info ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "当前唤醒词",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = info.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${info.romanized} (${info.language})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Divider()
            
            // 唤醒词选择
            Text(
                text = "选择唤醒词",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            // 默认唤醒词选项
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = currentWakeWordInfo?.isCustom == false,
                        onClick = {
                            scope.launch {
                                isLoading = true
                                statusMessage = "切换到默认唤醒词..."
                                
                                val success = KoreanWakeWordManager.removeKoreanWakeWord(context)
                                if (success) {
                                    currentWakeWordInfo = KoreanWakeWordManager.getCurrentWakeWordInfo(context)
                                    statusMessage = "✅ 已切换到 Hey Dicio"
                                    onWakeWordChanged()
                                } else {
                                    statusMessage = "❌ 切换失败"
                                }
                                isLoading = false
                            }
                        }
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = currentWakeWordInfo?.isCustom == false,
                    onClick = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Hey Dicio",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "English (默认)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 韩语唤醒词选项
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = currentWakeWordInfo?.isCustom == true && 
                                 currentWakeWordInfo?.language == "Korean",
                        onClick = {
                            scope.launch {
                                isLoading = true
                                statusMessage = "安装韩语唤醒词..."
                                
                                val success = KoreanWakeWordManager.installKoreanWakeWord(context)
                                if (success) {
                                    currentWakeWordInfo = KoreanWakeWordManager.getCurrentWakeWordInfo(context)
                                    statusMessage = "✅ 已切换到 하이넛지"
                                    onWakeWordChanged()
                                } else {
                                    statusMessage = "❌ 安装失败，请检查模型文件"
                                }
                                isLoading = false
                            }
                        }
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = currentWakeWordInfo?.isCustom == true && 
                             currentWakeWordInfo?.language == "Korean",
                    onClick = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "하이넛지",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Hi Nutji (Korean)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 状态信息
            if (statusMessage.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (statusMessage.startsWith("✅")) 
                            MaterialTheme.colorScheme.primaryContainer
                        else if (statusMessage.startsWith("❌"))
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            Divider()
            
            // 高级选项
            Text(
                text = "高级选项",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            // 模型验证按钮
            OutlinedButton(
                onClick = {
                    scope.launch {
                        isLoading = true
                        statusMessage = "验证模型文件..."
                        
                        val validation = KoreanWakeWordManager.validateKoreanWakeWordModel(context)
                        statusMessage = if (validation.isValid) {
                            "✅ 模型文件有效"
                        } else {
                            "❌ ${validation.message}"
                        }
                        isLoading = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text("验证韩语模型")
            }
            
            // 模型统计信息
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val stats = KoreanWakeWordManager.getKoreanWakeWordStats(context)
                        statusMessage = """
                            📊 模型统计:
                            • 已安装: ${if (stats.isInstalled) "是" else "否"}
                            • 文件大小: ${stats.fileSize / 1024}KB
                            • 有效性: ${if (stats.isValid) "有效" else "无效"}
                        """.trimIndent()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text("查看模型信息")
            }
            
            // 调试信息
            if (DebugLogger.isAudioSaveEnabled()) {
                Divider()
                Text(
                    text = "调试选项",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                OutlinedButton(
                    onClick = {
                        statusMessage = """
                            🔧 调试模式已启用
                            • 音频保存: 开启
                            • 详细日志: 开启
                            • 使用 adb logcat 查看实时日志
                            • 使用 ./scripts/pull_audio_debug.sh 拉取音频
                        """.trimIndent()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("调试信息")
                }
            }
        }
    }
}

/**
 * 唤醒词类型枚举
 */
enum class WakeWordType {
    DEFAULT,    // Hey Dicio
    KOREAN      // 하이넛지
}
