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
import androidx.lifecycle.lifecycleScope
import com.ai.voice.R
import com.ai.voice.util.PermissionHelper
import com.ai.voice.license.LicenseActivationManager
import com.ai.voice.license.ActivationDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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
        
        // 先检查是否需要显示激活管理界面
        if (shouldShowActivationManagement()) {
            Log.d(TAG, "跳转到激活管理界面")
            val intent = Intent(this, com.ai.voice.license.ActivationManagementActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        
        // 开始权限检查流程
        checkNextPermission()
    }
    
    /**
     * 是否应该显示激活管理界面
     */
    private fun shouldShowActivationManagement(): Boolean {
        return try {
            val activationManager = LicenseActivationManager.getInstance()
            
            // 检查是否有特殊标志要求显示激活界面
            val showActivation = intent.getBooleanExtra("show_activation_ui", false)
            if (showActivation) {
                return true
            }
            
            // 如果本地未激活，显示激活界面
            if (!activationManager.isActivated()) {
                Log.d(TAG, "本地未激活，需要显示激活界面")
                return true
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "检查激活状态失败", e)
            // 出错时不显示激活界面，允许继续
            false
        }
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
     * 检查基础权限（录音、通知、位置）
     */
    private fun checkBasicPermissions() {
        val missing = PermissionHelper.getMissingBasicPermissions(this)
        
        if (missing.isEmpty()) {
            Log.d(TAG, "✅ 基础权限已具备")
            currentStep = PermissionStep.CHECK_STORAGE
            checkNextPermission()
        } else {
            Log.d(TAG, "❌ 缺少基础权限: ${missing.joinToString(", ")}")
            
            // 检查是否有权限被永久拒绝（用户选择了"不再询问"）
            val permanentlyDenied = missing.filter { permission ->
                !shouldShowRequestPermissionRationale(permission)
            }
            
            if (permanentlyDenied.isNotEmpty()) {
                // 有权限被永久拒绝，直接显示对话框引导用户去设置
                Log.w(TAG, "⚠️ 以下权限被永久拒绝: ${permanentlyDenied.joinToString(", ")}")
                showPermissionPermanentlyDeniedDialog(missing)
            } else {
                // 首次请求或之前只是拒绝（没选"不再询问"），显示说明后请求
                showBasicPermissionExplanationDialog(missing)
            }
        }
    }
    
    /**
     * 显示基础权限说明对话框
     */
    private fun showBasicPermissionExplanationDialog(permissions: Array<String>) {
        val message = buildString {
            append("语音助手需要以下权限才能正常工作：\n\n")
            permissions.forEach { permission ->
                append("• ${PermissionHelper.getPermissionDescription(permission)}\n")
            }
            append("\n点击\"授予权限\"后，请在弹出的对话框中选择\"允许\"。")
        }
        
        AlertDialog.Builder(this)
            .setTitle("需要权限")
            .setMessage(message)
            .setPositiveButton("授予权限") { _, _ ->
                // 请求权限，系统会弹出权限对话框
                requestPermissions(permissions, REQUEST_BASIC_PERMISSIONS)
            }
            .setNegativeButton("退出") { _, _ ->
                Toast.makeText(this, "缺少必要权限，无法启动", Toast.LENGTH_LONG).show()
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 显示权限被永久拒绝的对话框
     */
    private fun showPermissionPermanentlyDeniedDialog(permissions: Array<String>) {
        val message = buildString {
            append("检测到您之前拒绝了以下权限：\n\n")
            permissions.forEach { permission ->
                append("• ${PermissionHelper.getPermissionDescription(permission)}\n")
            }
            append("\n语音助手必须获得这些权限才能正常工作。\n")
            append("请点击\"去设置\"，然后在权限设置中手动开启这些权限。")
        }
        
        AlertDialog.Builder(this)
            .setTitle("需要手动授予权限")
            .setMessage(message)
            .setPositiveButton("去设置") { _, _ ->
                PermissionHelper.openAppSettings(this)
                finish()
            }
            .setNegativeButton("退出") { _, _ ->
                Toast.makeText(this, "缺少必要权限，无法启动", Toast.LENGTH_LONG).show()
                finish()
            }
            .setCancelable(false)
            .show()
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
        Log.d(TAG, "✅ 所有权限已具备，检查激活状态...")
        
        // 严格验证激活状态
        lifecycleScope.launch {
            try {
                val activationManager = LicenseActivationManager.getInstance()
                
                // 🔒 严格模式：即使本地已激活，也进行网络验证（防止篡改）
                val localActivated = activationManager.isActivated()
                
                if (localActivated) {
                    // 本地已激活，但仍然尝试网络验证（不阻塞）
                    Log.d(TAG, "✅ 本地已激活，进行后台验证...")
                    
                    // 后台定期验证
                    launch {
                        try {
                            val verifyResult = activationManager.verifyPeriodically()
                            if (!verifyResult.activated) {
                                Log.w(TAG, "⚠️ 定期验证失败，但不阻止启动")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "定期验证异常", e)
                        }
                    }
                    
                    // 直接启动服务
                    doStartService()
                    return@launch
                }
                
                // 本地未激活，尝试通过MAC地址验证
                Log.d(TAG, "本地未激活，尝试MAC地址验证...")
                val result = activationManager.checkOnStartup()
                
                if (result.activated) {
                    Log.d(TAG, "✅ MAC地址验证成功，启动服务")
                    doStartService()
                } else if (result.needLicense) {
                    // 🔒 需要激活码，跳转到激活管理界面（TV友好）
                    Log.d(TAG, "⚠️ 需要激活码，跳转到激活管理界面")
                    showActivationManagementActivity()
                } else {
                    // 🔒 验证失败，严格模式下阻止启动
                    Log.e(TAG, "❌ 激活验证失败: ${result.error}，跳转到激活管理界面")
                    showActivationManagementActivity()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 激活检查失败: ${e.message}", e)
                // 🔒 出错时也跳转到激活管理界面
                showActivationManagementActivity()
            }
        }
    }
    
    /**
     * 实际启动服务
     */
    private fun doStartService() {
        EnhancedFloatingWindowService.start(this)
        Toast.makeText(this, R.string.floating_assistant_started, Toast.LENGTH_SHORT).show()
        finish()
    }
    
    /**
     * 显示激活管理界面（TV友好）
     */
    private fun showActivationManagementActivity() {
        val intent = Intent(this, com.ai.voice.license.ActivationManagementActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    /**
     * 显示激活对话框（已废弃，使用ActivationManagementActivity代替）
     */
    @Deprecated("使用showActivationManagementActivity代替")
    private fun showActivationDialog() {
        ActivationDialog.show(
            context = this,
            lifecycleScope = lifecycleScope,
            onActivated = {
                // 激活成功后启动服务
                doStartService()
            }
        )
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
                // 检查关键权限（录音和位置）是否已授予
                val criticalDenied = result.deniedPermissions.filter { permission ->
                    PermissionHelper.isCriticalPermission(permission)
                }
                
                if (criticalDenied.isEmpty()) {
                    // 所有关键权限已授予
                    Log.d(TAG, "✅ 关键权限已授予")
                    
                    // 检查可选权限（通知）
                    val optionalDenied = result.deniedPermissions.filter { permission ->
                        !PermissionHelper.isCriticalPermission(permission)
                    }
                    
                    if (optionalDenied.isNotEmpty()) {
                        Log.w(TAG, "⚠️ 可选权限被拒绝（不影响运行）: ${optionalDenied.joinToString(", ")}")
                        Toast.makeText(
                            this,
                            "通知权限被拒绝，部分功能可能受限",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    
                    // 继续下一步
                    currentStep = PermissionStep.CHECK_STORAGE
                    checkNextPermission()
                } else {
                    // 关键权限被拒绝，无法继续
                    Log.e(TAG, "❌ 关键权限被拒绝: ${criticalDenied.joinToString(", ")}")
                    showPermissionDeniedDialog("关键权限", criticalDenied)
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

