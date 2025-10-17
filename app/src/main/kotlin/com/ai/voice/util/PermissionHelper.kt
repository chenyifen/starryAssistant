package com.ai.voice.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

/**
 * 权限助手类，负责Dicio语音助手所需的各种权限检查和申请
 * 
 * 职责：
 * - 检查录音、通知等基础权限
 * - 检查外部存储权限（用于访问模型文件）
 * - 提供权限申请和引导用户到设置页面的方法
 * - 测试实际文件访问能力
 */
object PermissionHelper {
    
    // 权限请求码
    const val REQUEST_RECORD_AUDIO = 1001
    const val REQUEST_NOTIFICATIONS = 1002
    const val REQUEST_EXTERNAL_STORAGE = 1003
    const val REQUEST_MANAGE_EXTERNAL_STORAGE = 1004
    const val REQUEST_ALL_PERMISSIONS = 1005
    
    /**
     * 基础权限列表
     */
    private val BASIC_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS
    )
    
    /**
     * 存储权限列表（用于访问外部模型文件）
     */
    private val STORAGE_PERMISSIONS = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE
    )
    
    /**
     * 检查是否具有录音权限
     */
    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 检查是否具有通知权限
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 13 以下默认有通知权限
        }
    }
    
    /**
     * 检查是否具有外部存储读取权限
     */
    fun hasExternalStoragePermission(context: Context): Boolean {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 检查 MANAGE_EXTERNAL_STORAGE
            Environment.isExternalStorageManager()
        } else {
            // Android 10 及以下检查 READ_EXTERNAL_STORAGE
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
        
        DebugLogger.logModelManagement("PermissionHelper", "🔐 外部存储权限检查: ${if (granted) "✅ 已授予" else "❌ 未授予"}")
        
        // 实际文件访问测试
        testFileAccess()
        
        return granted
    }
    
    /**
     * 测试实际文件访问能力
     */
    fun testFileAccess(): Boolean {
        val testPaths = listOf(
            "/storage/emulated/0/Dicio",
            "/storage/emulated/0/Dicio/models",
            "/storage/emulated/0/Dicio/models/sherpa_onnx_kws",
            "/storage/emulated/0/Dicio/models/sherpa_onnx_kws/keywords.txt"
        )
        
        DebugLogger.logModelManagement("PermissionHelper", "🔍 开始文件访问测试...")
        
        var hasValidAccess = false
        testPaths.forEach { path ->
            try {
                val file = File(path)
                val exists = file.exists()
                val canRead = file.canRead()
                val isDirectory = file.isDirectory()
                
                DebugLogger.logModelManagement("PermissionHelper", "📁 路径: $path")
                DebugLogger.logModelManagement("PermissionHelper", "   - 存在: ${if (exists) "✅" else "❌"}")
                DebugLogger.logModelManagement("PermissionHelper", "   - 可读: ${if (canRead) "✅" else "❌"}")
                DebugLogger.logModelManagement("PermissionHelper", "   - 目录: ${if (isDirectory) "✅" else "❌"}")
                
                if (exists && canRead && path.endsWith("keywords.txt")) {
                    try {
                        val content = file.readText().take(50)
                        DebugLogger.logModelManagement("PermissionHelper", "   - 内容预览: $content...")
                        hasValidAccess = true
                    } catch (e: Exception) {
                        DebugLogger.logWakeWordError("PermissionHelper", "   - 读取失败: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                DebugLogger.logWakeWordError("PermissionHelper", "   - 访问异常: ${e.message}")
            }
        }
        
        return hasValidAccess
    }
    
    /**
     * 检查是否具有所有基础权限
     */
    fun hasAllBasicPermissions(context: Context): Boolean {
        return hasRecordAudioPermission(context) && hasNotificationPermission(context)
    }
    
    /**
     * 检查是否具有访问外部模型文件所需的权限
     */
    fun hasModelAccessPermissions(context: Context): Boolean {
        val basicPermissions = hasAllBasicPermissions(context)
        val storagePermission = hasExternalStoragePermission(context)
        val result = basicPermissions && storagePermission
        
        DebugLogger.logModelManagement("PermissionHelper", "🔐 模型访问权限检查:")
        DebugLogger.logModelManagement("PermissionHelper", "  - 基础权限: ${if (basicPermissions) "✅" else "❌"}")
        DebugLogger.logModelManagement("PermissionHelper", "  - 存储权限: ${if (storagePermission) "✅" else "❌"}")
        DebugLogger.logModelManagement("PermissionHelper", "  - 总体结果: ${if (result) "✅ 可访问" else "❌ 需申请"}")
        
        return result
    }
    
    /**
     * 获取缺失的基础权限
     */
    fun getMissingBasicPermissions(context: Context): Array<String> {
        return BASIC_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }
    
    /**
     * 申请基础权限
     */
    fun requestBasicPermissions(activity: Activity, requestCode: Int = REQUEST_ALL_PERMISSIONS) {
        val missingPermissions = getMissingBasicPermissions(activity)
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, missingPermissions, requestCode)
        }
    }
    
    /**
     * 申请模型访问所需的所有权限（包括基础权限和存储权限）
     */
    fun requestModelAccessPermissions(activity: Activity, requestCode: Int = REQUEST_ALL_PERMISSIONS) {
        val missingPermissions = mutableListOf<String>()
        
        // 检查基础权限
        missingPermissions.addAll(getMissingBasicPermissions(activity))
        
        // 检查存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要 MANAGE_EXTERNAL_STORAGE
            if (!Environment.isExternalStorageManager()) {
                DebugLogger.logModelManagement("PermissionHelper", "🔐 需要申请 MANAGE_EXTERNAL_STORAGE 权限")
                requestManageExternalStoragePermission(activity)
                return
            }
        } else {
            // Android 10 及以下检查 READ_EXTERNAL_STORAGE
            if (!hasExternalStoragePermission(activity)) {
                missingPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        
        if (missingPermissions.isNotEmpty()) {
            DebugLogger.logModelManagement("PermissionHelper", "🔐 发现缺失权限: ${missingPermissions.joinToString(", ")}")
            DebugLogger.logModelManagement("PermissionHelper", "🔐 正在弹出权限申请对话框...")
            ActivityCompat.requestPermissions(activity, missingPermissions.toTypedArray(), requestCode)
        } else {
            DebugLogger.logModelManagement("PermissionHelper", "🔐 所有模型访问权限都已具备")
        }
    }
    
    /**
     * 申请 MANAGE_EXTERNAL_STORAGE 权限 (Android 11+)
     */
    fun requestManageExternalStoragePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE)
                DebugLogger.logModelManagement("PermissionHelper", "🔐 已跳转到 MANAGE_EXTERNAL_STORAGE 权限设置页面")
            } catch (e: Exception) {
                DebugLogger.logWakeWordError("PermissionHelper", "❌ 无法打开权限设置页面: ${e.message}")
                // 降级到通用设置页面
                openAppSettings(activity)
            }
        }
    }
    
    /**
     * 申请录音权限
     */
    fun requestRecordAudioPermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO
        )
    }
    
    /**
     * 申请通知权限
     */
    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS
            )
        }
    }
    
    /**
     * 申请外部存储权限
     */
    fun requestExternalStoragePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestManageExternalStoragePermission(activity)
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_EXTERNAL_STORAGE
            )
        }
    }
    
    /**
     * 引导用户到应用详情页面
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
    
    /**
     * 检查权限申请结果
     */
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ): PermissionResult {
        val granted = mutableListOf<String>()
        val denied = mutableListOf<String>()
        
        for (i in permissions.indices) {
            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                granted.add(permissions[i])
            } else {
                denied.add(permissions[i])
            }
        }
        
        return PermissionResult(
            requestCode = requestCode,
            grantedPermissions = granted,
            deniedPermissions = denied,
            allGranted = denied.isEmpty()
        )
    }
    
    /**
     * 获取权限描述
     */
    fun getPermissionDescription(permission: String): String {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> "录音权限：用于语音识别和语音指令"
            Manifest.permission.POST_NOTIFICATIONS -> "通知权限：用于显示语音助手服务状态"
            Manifest.permission.READ_EXTERNAL_STORAGE -> "存储权限：用于访问外部模型文件"
            else -> "未知权限"
        }
    }
    
    /**
     * 获取缺失权限的提示信息
     */
    fun getMissingPermissionMessage(context: Context): String {
        val missing = mutableListOf<String>()
        
        if (!hasRecordAudioPermission(context)) {
            missing.add("录音权限")
        }
        
        if (!hasNotificationPermission(context)) {
            missing.add("通知权限")
        }
        
        if (!hasExternalStoragePermission(context)) {
            missing.add("存储权限")
        }
        
        return if (missing.isEmpty()) {
            ""
        } else {
            "Dicio 需要以下权限才能正常工作：${missing.joinToString("、")}"
        }
    }
    
    /**
     * 检查SherpaOnnx模型文件是否可访问
     */
    fun checkSherpaModelFilesAccess(): Boolean {
        val modelBasePath = "/storage/emulated/0/Dicio/models/sherpa_onnx_kws"
        val requiredFiles = listOf(
            "encoder-epoch-12-avg-2-chunk-16-left-64.onnx",
            "decoder-epoch-12-avg-2-chunk-16-left-64.onnx", 
            "joiner-epoch-12-avg-2-chunk-16-left-64.onnx",
            "keywords.txt",
            "tokens.txt"
        )
        
        return try {
            requiredFiles.all { fileName ->
                val file = File(modelBasePath, fileName)
                val exists = file.exists()
                val canRead = file.canRead()
                
                DebugLogger.logModelManagement("PermissionHelper", "📄 检查文件: $fileName - 存在:${if (exists) "✅" else "❌"} 可读:${if (canRead) "✅" else "❌"}")
                
                exists && canRead
            }
        } catch (e: Exception) {
            DebugLogger.logWakeWordError("PermissionHelper", "❌ 检查模型文件失败: ${e.message}")
            false
        }
    }
}

/**
 * 权限申请结果
 */
data class PermissionResult(
    val requestCode: Int,
    val grantedPermissions: List<String>,
    val deniedPermissions: List<String>,
    val allGranted: Boolean
)
