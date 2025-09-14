package org.stypox.dicio.ui.settings

import android.content.Intent
import android.os.Build
import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.stypox.dicio.R
import org.stypox.dicio.util.PermissionHelper

/**
 * 权限设置组件
 * 显示当前权限状态并提供权限请求功能
 */
@Composable
fun PermissionSettingsWidget(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var permissionStatus by remember { mutableStateOf(checkPermissionStatus(context)) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (permissionStatus.hasAllPermissions) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (permissionStatus.hasAllPermissions) {
                        Icons.Default.Security
                    } else {
                        Icons.Default.Warning
                    },
                    contentDescription = null,
                    tint = if (permissionStatus.hasAllPermissions) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
                Text(
                    text = "应用权限状态",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (permissionStatus.hasAllPermissions) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }
            
            // 权限状态详情
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PermissionStatusItem(
                    name = "录音权限",
                    granted = permissionStatus.hasRecordAudio,
                    description = "用于语音识别和语音指令"
                )
                
                PermissionStatusItem(
                    name = "通知权限",
                    granted = permissionStatus.hasNotification,
                    description = "用于显示语音助手服务状态"
                )
                
                PermissionStatusItem(
                    name = "存储权限",
                    granted = permissionStatus.hasExternalStorage,
                    description = "用于访问外部模型文件 (noModels变体需要)"
                )
            }
            
            // 权限请求按钮
            if (!permissionStatus.hasAllPermissions) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!permissionStatus.hasBasicPermissions) {
                        Button(
                            onClick = {
                                if (context is android.app.Activity) {
                                    PermissionHelper.requestBasicPermissions(context)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("请求基础权限")
                        }
                    }
                    
                    if (!permissionStatus.hasExternalStorage) {
                        Button(
                            onClick = {
                                if (context is android.app.Activity) {
                                    PermissionHelper.requestExternalStoragePermission(context)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("请求存储权限")
                        }
                    }
                }
                
                // 权限说明
                Text(
                    text = "Dicio需要这些权限才能正常工作。请授予所需权限以获得完整功能。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            } else {
                Text(
                    text = "✅ 所有必要权限已授予，应用可以正常工作。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun PermissionStatusItem(
    name: String,
    granted: Boolean,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Text(
            text = if (granted) "✅" else "❌",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private data class PermissionStatus(
    val hasRecordAudio: Boolean,
    val hasNotification: Boolean,
    val hasExternalStorage: Boolean
) {
    val hasBasicPermissions: Boolean get() = hasRecordAudio && hasNotification
    val hasAllPermissions: Boolean get() = hasBasicPermissions && hasExternalStorage
}

private fun checkPermissionStatus(context: android.content.Context): PermissionStatus {
    return PermissionStatus(
        hasRecordAudio = PermissionHelper.hasRecordAudioPermission(context),
        hasNotification = PermissionHelper.hasNotificationPermission(context),
        hasExternalStorage = PermissionHelper.hasExternalStoragePermission(context)
    )
}

@Preview
@Composable
private fun PermissionSettingsWidgetPreview() {
    MaterialTheme {
        PermissionSettingsWidget()
    }
}
