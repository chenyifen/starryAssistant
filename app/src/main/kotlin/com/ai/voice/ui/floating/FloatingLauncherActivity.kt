package com.ai.voice.ui.floating

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import com.ai.voice.R
import com.ai.voice.util.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint

/**
 * 悬浮球启动器Activity
 * 
 * 这个Activity作为应用的主入口，负责：
 * 1. 主动检查并请求所有必需权限（录音、通知、存储、悬浮窗）
 * 2. 权限全部获得后启动悬浮球服务
 * 3. 立即关闭自己，不显示任何界面
 * 
 * 权限检查顺序：
 * - 基础权限（录音、通知）
 * - 存储权限（访问模型文件）
 * - 悬浮窗权限
 */
@AndroidEntryPoint
class FloatingLauncherActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "FloatingLauncher"
        private const val REQUEST_BASIC_PERMISSIONS = 1001
        private const val REQUEST_STORAGE_PERMISSION = 1002
    }
    
    // 当前权限检查步骤
    private var currentStep = PermissionStep.CHECK_BASIC
    
    // 悬浮窗权限请求
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        handleOverlayPermissionResult()
    }
    
    // 存储权限请求 (Android 11+)
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        handleStoragePermissionResult()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "🚀 FloatingLauncherActivity 启动")
        
        // 开始权限检查流程
        checkNextPermission()
    }
    
    /**
     * 检查下一个权限
     */
    private fun checkNextPermission() {
        when (currentStep) {
            PermissionStep.CHECK_BASIC -> checkBasicPermissions()
            PermissionStep.CHECK_STORAGE -> checkStoragePermission()
            PermissionStep.CHECK_OVERLAY -> checkOverlayPermission()
            PermissionStep.START_SERVICE -> startServiceAndFinish()
        }
    }
    
    /**
     * 检查基础权限（录音、通知）
     */
    private fun checkBasicPermissions() {
        val missing = PermissionHelper.getMissingBasicPermissions(this)
        
        if (missing.isEmpty()) {
            Log.d(TAG, "✅ 基础权限已具备")
            currentStep = PermissionStep.CHECK_STORAGE
            checkNextPermission()
        } else {
            Log.d(TAG, "❌ 缺少基础权限: ${missing.joinToString(", ")}")
            // 主动请求权限，系统会弹出权限对话框
            requestPermissions(missing, REQUEST_BASIC_PERMISSIONS)
        }
    }
    
    /**
     * 检查存储权限（访问模型文件）
     * Hyundai IT版本：模型文件已内置到assets，无需外部存储权限
     */
    private fun checkStoragePermission() {
        // Hyundai IT版本跳过存储权限检查
        Log.d(TAG, "✅ Hyundai IT版本 - 模型已内置，跳过存储权限检查")
        currentStep = PermissionStep.CHECK_OVERLAY
        checkNextPermission()
        
        /* 原始存储权限检查逻辑已禁用
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 检查 MANAGE_EXTERNAL_STORAGE
            if (Environment.isExternalStorageManager()) {
                Log.d(TAG, "✅ 存储权限已具备")
                currentStep = PermissionStep.CHECK_OVERLAY
                checkNextPermission()
            } else {
                Log.d(TAG, "❌ 缺少存储权限，请求 MANAGE_EXTERNAL_STORAGE")
                // 显示说明对话框后跳转到设置
                showStoragePermissionDialog()
            }
        } else {
            // Android 10 及以下检查 READ_EXTERNAL_STORAGE
            if (PermissionHelper.hasExternalStoragePermission(this)) {
                Log.d(TAG, "✅ 存储权限已具备")
                currentStep = PermissionStep.CHECK_OVERLAY
                checkNextPermission()
            } else {
                Log.d(TAG, "❌ 缺少存储权限，请求 READ_EXTERNAL_STORAGE")
                requestPermissions(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_STORAGE_PERMISSION
                )
            }
        }
        */
    }
    
    /**
     * 显示存储权限说明对话框
     */
    private fun showStoragePermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要存储权限")
            .setMessage("语音助手需要访问外部存储以加载语音模型文件。\n\n请在下一个页面中允许\"所有文件访问权限\"。")
            .setPositiveButton("前往设置") { _, _ ->
                requestManageExternalStoragePermission()
            }
            .setNegativeButton("取消") { _, _ ->
                Toast.makeText(this, "缺少存储权限，无法启动", Toast.LENGTH_LONG).show()
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 请求 MANAGE_EXTERNAL_STORAGE 权限
     */
    private fun requestManageExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                storagePermissionLauncher.launch(intent)
            } catch (e: Exception) {
                Log.e(TAG, "无法打开存储权限设置页面", e)
                Toast.makeText(this, "无法打开设置页面", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    
    /**
     * 检查悬浮窗权限
     */
    private fun checkOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            Log.d(TAG, "✅ 悬浮窗权限已具备")
            currentStep = PermissionStep.START_SERVICE
            checkNextPermission()
        } else {
            Log.d(TAG, "❌ 缺少悬浮窗权限")
            showOverlayPermissionDialog()
        }
    }
    
    /**
     * 显示悬浮窗权限说明对话框
     */
    private fun showOverlayPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要悬浮窗权限")
            .setMessage("语音助手需要悬浮窗权限以显示悬浮球。\n\n请在下一个页面中允许\"显示在其他应用上层\"。")
            .setPositiveButton("前往设置") { _, _ ->
                requestOverlayPermission()
            }
            .setNegativeButton("取消") { _, _ ->
                Toast.makeText(this, "缺少悬浮窗权限，无法启动", Toast.LENGTH_LONG).show()
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 请求悬浮窗权限
     */
    private fun requestOverlayPermission() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "无法打开悬浮窗权限设置页面", e)
            Toast.makeText(this, "无法打开设置页面", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    /**
     * 启动悬浮球服务并关闭Activity
     */
    private fun startServiceAndFinish() {
        Log.d(TAG, "✅ 所有权限已具备，启动悬浮球服务")
        EnhancedFloatingWindowService.start(this)
        Toast.makeText(this, R.string.floating_assistant_started, Toast.LENGTH_SHORT).show()
        finish()
    }
    
    /**
     * 处理基础权限请求结果
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        val result = PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            REQUEST_BASIC_PERMISSIONS -> {
                if (result.allGranted) {
                    Log.d(TAG, "✅ 基础权限已授予")
                    currentStep = PermissionStep.CHECK_STORAGE
                    checkNextPermission()
                } else {
                    Log.w(TAG, "❌ 基础权限被拒绝: ${result.deniedPermissions.joinToString(", ")}")
                    showPermissionDeniedDialog("基础权限", result.deniedPermissions)
                }
            }
            REQUEST_STORAGE_PERMISSION -> {
                if (result.allGranted) {
                    Log.d(TAG, "✅ 存储权限已授予")
                    currentStep = PermissionStep.CHECK_OVERLAY
                    checkNextPermission()
                } else {
                    Log.w(TAG, "❌ 存储权限被拒绝")
                    showPermissionDeniedDialog("存储权限", result.deniedPermissions)
                }
            }
        }
    }
    
    /**
     * 处理悬浮窗权限返回结果
     */
    private fun handleOverlayPermissionResult() {
        if (Settings.canDrawOverlays(this)) {
            Log.d(TAG, "✅ 悬浮窗权限已授予")
            currentStep = PermissionStep.START_SERVICE
            checkNextPermission()
        } else {
            Log.w(TAG, "❌ 悬浮窗权限被拒绝")
            Toast.makeText(this, "缺少悬浮窗权限，无法启动", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    /**
     * 处理存储权限返回结果
     */
    private fun handleStoragePermissionResult() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Log.d(TAG, "✅ 存储权限已授予")
                currentStep = PermissionStep.CHECK_OVERLAY
                checkNextPermission()
            } else {
                Log.w(TAG, "❌ 存储权限被拒绝")
                Toast.makeText(this, "缺少存储权限，无法启动", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
    
    /**
     * 显示权限被拒绝对话框
     */
    private fun showPermissionDeniedDialog(permissionName: String, deniedPermissions: List<String>) {
        val message = "应用需要以下${permissionName}才能正常运行：\n\n" +
                deniedPermissions.joinToString("\n") { permission ->
                    PermissionHelper.getPermissionDescription(permission)
                }
        
        AlertDialog.Builder(this)
            .setTitle("权限被拒绝")
            .setMessage(message)
            .setPositiveButton("去设置") { _, _ ->
                PermissionHelper.openAppSettings(this)
                finish()
            }
            .setNegativeButton("退出") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 权限检查步骤
     */
    private enum class PermissionStep {
        CHECK_BASIC,    // 检查基础权限
        CHECK_STORAGE,  // 检查存储权限
        CHECK_OVERLAY,  // 检查悬浮窗权限
        START_SERVICE   // 启动服务
    }
}

