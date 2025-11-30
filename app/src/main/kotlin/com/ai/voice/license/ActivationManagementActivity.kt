package com.ai.voice.license

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ai.voice.R
import com.ai.voice.ui.floating.FloatingLauncherActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * 激活管理Activity - TV友好界面
 * 支持遥控器操作，适用于超大屏幕
 */
class ActivationManagementActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "ActivationManagement"
        private val ACTIVATION_CODE_PATTERN = Pattern.compile("^LS-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{1}$")
        private const val ACTIVATION_CODE_LENGTH = 13  // 不含LS前缀的字符数：4+4+4+1=13
        private const val PREFIX = "LS-"
    }
    
    // UI组件
    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvDeviceInfo: TextView
    private lateinit var tvLicenseKey: TextView
    private lateinit var tvActivationTime: TextView
    private lateinit var tvExpiryTime: TextView
    
    private lateinit var btnActivate: Button
    private lateinit var btnCheckStatus: Button
    private lateinit var btnContinue: Button
    private lateinit var btnClearActivation: Button
    
    // 激活码输入 - 分段输入
    private lateinit var inputContainer: View
    private lateinit var btnSubmit: Button
    private val etInputs = mutableListOf<EditText>()
    private val segmentSizes = listOf(4, 4, 4, 1)
    
    private val activationManager by lazy { LicenseActivationManager.getInstance() }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_activation_management)
        
        initViews()
        setupListeners()
        updateUI()
        
        Log.d(TAG, "激活管理界面已启动")
    }
    
    /**
     * 初始化视图
     */
    private fun initViews() {
        // 状态显示
        tvTitle = findViewById(R.id.tv_title)
        tvStatus = findViewById(R.id.tv_status)
        tvDeviceInfo = findViewById(R.id.tv_device_info)
        tvLicenseKey = findViewById(R.id.tv_license_key)
        tvActivationTime = findViewById(R.id.tv_activation_time)
        tvExpiryTime = findViewById(R.id.tv_expiry_time)
        
        // 操作按钮
        btnActivate = findViewById(R.id.btn_activate)
        btnCheckStatus = findViewById(R.id.btn_check_status)
        btnContinue = findViewById(R.id.btn_continue)
        btnClearActivation = findViewById(R.id.btn_clear_activation)
        
        // 激活码输入
        inputContainer = findViewById(R.id.input_container)
        btnSubmit = findViewById(R.id.btn_submit)
        
        // 初始化分段输入框
        etInputs.clear()
        etInputs.add(findViewById(R.id.et_segment1_char1))
        etInputs.add(findViewById(R.id.et_segment1_char2))
        etInputs.add(findViewById(R.id.et_segment1_char3))
        etInputs.add(findViewById(R.id.et_segment1_char4))
        etInputs.add(findViewById(R.id.et_segment2_char1))
        etInputs.add(findViewById(R.id.et_segment2_char2))
        etInputs.add(findViewById(R.id.et_segment2_char3))
        etInputs.add(findViewById(R.id.et_segment2_char4))
        etInputs.add(findViewById(R.id.et_segment3_char1))
        etInputs.add(findViewById(R.id.et_segment3_char2))
        etInputs.add(findViewById(R.id.et_segment3_char3))
        etInputs.add(findViewById(R.id.et_segment3_char4))
        etInputs.add(findViewById(R.id.et_segment4_char1))
        
        // 默认隐藏输入面板
        inputContainer.visibility = View.GONE
    }
    
    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 激活按钮
        btnActivate.setOnClickListener {
            showInputPanel()
        }
        
        // 检查状态按钮
        btnCheckStatus.setOnClickListener {
            checkActivationStatus()
        }
        
        // 继续按钮
        btnContinue.setOnClickListener {
            continueToApp()
        }
        
        // 清除激活按钮（长按）
        btnClearActivation.setOnLongClickListener {
            clearActivation()
            true
        }
        
        // 提交按钮
        btnSubmit.setOnClickListener {
            onSubmit()
        }
        
        // 设置分段输入框监听器
        setupSegmentInputListeners()
    }
    
    /**
     * 设置分段输入框监听器
     */
    private fun setupSegmentInputListeners() {
        for (i in etInputs.indices) {
            val currentEditText = etInputs[i]
            
            // 文本变化监听 - 自动跳转到下一个输入框
            currentEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                
                override fun afterTextChanged(s: Editable?) {
                    val text = s.toString().uppercase().filter { it.isLetterOrDigit() }
                    if (text.isNotEmpty() && text != s.toString()) {
                        s?.replace(0, s.length, text.take(1))
        }
        
                    // 如果输入了字符，跳转到下一个输入框
                    if (text.isNotEmpty() && i < etInputs.size - 1) {
                        etInputs[i + 1].requestFocus()
                    }
                }
            })
            
            // 按键监听 - 处理删除键
            currentEditText.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                    if (currentEditText.text.isEmpty() && i > 0) {
                        etInputs[i - 1].requestFocus()
                        etInputs[i - 1].setSelection(etInputs[i - 1].text.length)
                        return@setOnKeyListener true
                    }
                }
                false
            }
            
            // 最后一个输入框的完成按钮
            if (i == etInputs.size - 1) {
                currentEditText.setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                onSubmit()
                true
            } else {
                false
                    }
                }
            }
        }
    }
    
    /**
     * 显示系统键盘
     */
    private fun showKeyboard() {
        if (etInputs.isNotEmpty()) {
            etInputs[0].requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etInputs[0], InputMethodManager.SHOW_IMPLICIT)
        }
    }
    
    /**
     * 隐藏系统键盘
     */
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocus = currentFocus
        if (currentFocus != null) {
            imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
        }
    }
    
    /**
     * 收集激活码
     * 格式: LS-XXXX-XXXX-XXXX-X (共13个字符，不含LS前缀)
     */
    private fun collectActivationCode(): String {
        val code = StringBuilder(PREFIX)
        var charCount = 0
        for (i in etInputs.indices) {
            val text = etInputs[i].text.toString().uppercase().filter { it.isLetterOrDigit() }
            if (text.isNotEmpty()) {
                code.append(text.take(1))
                charCount++
            }
            
            // 在适当位置添加连字符
            // 索引: 0,1,2,3 (第一段4个) -> 在3后添加
            // 索引: 4,5,6,7 (第二段4个) -> 在7后添加
            // 索引: 8,9,10,11 (第三段4个) -> 在11后添加
            // 索引: 12 (第四段1个) -> 不需要添加
            when (i) {
                3 -> code.append("-")  // 第一段后
                7 -> code.append("-")  // 第二段后
                11 -> code.append("-") // 第三段后
            }
        }
        val result = code.toString()
        Log.d(TAG, "收集激活码: $result, 字符数: $charCount, 期望: $ACTIVATION_CODE_LENGTH")
        return result
    }
    
    /**
     * 清空所有输入框
     */
    private fun clearAllInputs() {
        for (editText in etInputs) {
            editText.setText("")
        }
    }
    
    /**
     * 更新UI显示
     */
    private fun updateUI() {
        lifecycleScope.launch {
            try {
                val summary = activationManager.getActivationSummary()
                val fingerprint = activationManager.getDeviceFingerprint()
                
                // 显示激活状态
                if (summary.isActivated) {
                    tvStatus.text = "✅ ${summary.message}"
                    tvStatus.setTextColor(0xFF4DFFB8.toInt())
                    
                    tvLicenseKey.text = "授权码: ${summary.licenseKey?.take(16) ?: "无"}..."
                    tvLicenseKey.visibility = if (summary.licenseKey != null) View.VISIBLE else View.GONE
                    
                    tvActivationTime.text = "激活时间: ${formatDateTime(summary.activatedAt)}"
                    tvActivationTime.visibility = if (summary.activatedAt != null) View.VISIBLE else View.GONE
                    
                    if (summary.expiresAt != null && summary.expiresAt != "null") {
                        tvExpiryTime.text = "过期时间: ${formatDateTime(summary.expiresAt)}"
                        tvExpiryTime.visibility = View.VISIBLE
                    } else {
                        tvExpiryTime.text = "过期时间: 永久有效"
                        tvExpiryTime.visibility = View.VISIBLE
                    }
                    
                    btnActivate.visibility = View.GONE
                    btnContinue.visibility = View.VISIBLE
                } else {
                    tvStatus.text = "⚠️ ${summary.message}"
                    tvStatus.setTextColor(0xFFFFAA00.toInt())
                    
                    tvLicenseKey.visibility = View.GONE
                    tvActivationTime.visibility = View.GONE
                    tvExpiryTime.visibility = View.GONE
                    
                    btnActivate.visibility = View.VISIBLE
                    btnContinue.visibility = View.GONE
                }
                
                // 显示设备信息
                val deviceId = summary.macAddress ?: fingerprint.mac
                val isRealMac = deviceId?.matches(Regex("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$")) == true
                
                tvDeviceInfo.text = buildString {
                    append("设备信息:\n")
                    if (deviceId != null) {
                        if (isRealMac) {
                            append("• MAC地址: $deviceId\n")
                        } else {
                            append("• 设备标识: $deviceId (Android ID)\n")
                        }
                    } else {
                        append("• 设备标识: 未知\n")
                    }
                    append("• Android ID: ${fingerprint.androidId}\n")
                    append("• 设备型号: ${fingerprint.toSummary()}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "更新UI失败", e)
                tvStatus.text = "❌ 获取激活状态失败"
                tvStatus.setTextColor(0xFFFF4444.toInt())
            }
        }
    }
    
    /**
     * 显示激活码输入面板
     */
    private fun showInputPanel() {
        inputContainer.visibility = View.VISIBLE
        clearAllInputs()
        
        // 聚焦到第一个输入框，自动弹出系统键盘
        if (etInputs.isNotEmpty()) {
            etInputs[0].requestFocus()
        showKeyboard()
        }
    }
    
    /**
     * 隐藏激活码输入面板
     */
    private fun hideInputPanel() {
        hideKeyboard()
        inputContainer.visibility = View.GONE
        btnActivate.requestFocus()
    }
    
    /**
     * 格式化激活码（实时添加连字符）
     * 格式: LS-QYKHV-48YS5-DUKXE-SPJBF-3
     */
    private fun formatActivationCodeWithHyphens(input: String): String {
        if (input.isEmpty()) return ""
        
        val sb = StringBuilder()
        var index = 0
        
        // 第一部分: 2个字符
        if (input.length > index) {
            val end = minOf(index + 2, input.length)
            sb.append(input.substring(index, end))
            index = end
            
            // 如果还有字符，添加连字符
            if (index < input.length) {
                sb.append("-")
            }
        }
        
        // 后续部分: 每5个字符一组，每组前添加连字符
        while (index < input.length) {
            val end = minOf(index + 5, input.length)
            sb.append(input.substring(index, end))
            index = end
            
            // 如果还有字符，添加连字符
            if (index < input.length) {
                sb.append("-")
            }
        }
        
        return sb.toString()
    }
    
    /**
     * 格式化激活码（完整格式）
     * 格式: LS-XXXX-XXXX-XXXX-X (例如: LS-HQVT-LB94-46SR-9)
     */
    private fun formatActivationCode(input: String): String {
        val cleaned = input.replace("-", "").uppercase().filter { it.isLetterOrDigit() }
        if (cleaned.isEmpty()) return ""
        
        // 移除前缀LS（如果存在）
        val withoutPrefix = if (cleaned.startsWith("LS", ignoreCase = true)) {
            cleaned.substring(2)
        } else {
            cleaned
        }
        
        if (withoutPrefix.length != 13) {
            return cleaned
        }
        
        // 格式: 4-4-4-1
        val part1 = withoutPrefix.substring(0, 4)
        val part2 = withoutPrefix.substring(4, 8)
        val part3 = withoutPrefix.substring(8, 12)
        val part4 = withoutPrefix.substring(12, 13)
        
        return "LS-$part1-$part2-$part3-$part4"
    }
    
    
    /**
     * 验证激活码格式
     */
    private fun validateActivationCode(code: String): Boolean {
        // 移除连字符和前缀，只保留字符部分
        val cleaned = code.replace("-", "").uppercase().filter { it.isLetterOrDigit() }
        
        // 移除LS前缀（如果存在）
        val withoutPrefix = if (cleaned.startsWith("LS", ignoreCase = true)) {
            cleaned.substring(2)
        } else {
            cleaned
        }
        
        // 检查长度：应该是13个字符（4+4+4+1）
        if (withoutPrefix.length != 13) {
            Log.d(TAG, "激活码长度不正确: ${withoutPrefix.length}, 期望: 13, 输入: $code, 清理后: $cleaned")
            return false
        }
        
        // 格式化为标准格式并验证
        val formatted = formatActivationCode(cleaned)
        val matches = ACTIVATION_CODE_PATTERN.matcher(formatted).matches()
        if (!matches) {
            Log.d(TAG, "激活码格式不匹配: $formatted, 模式: ${ACTIVATION_CODE_PATTERN.pattern()}")
        }
        return matches
    }
    
    /**
     * 提交激活码
     */
    private fun onSubmit() {
        val licenseKey = collectActivationCode()
        
        // 检查是否所有输入框都已填写
        val allFilled = etInputs.all { it.text.toString().isNotEmpty() }
        if (!allFilled) {
            Toast.makeText(this, "请完整输入激活码", Toast.LENGTH_SHORT).show()
            return
        }
        
        val validationResult = validateActivationCode(licenseKey)
        if (!validationResult) {
            val cleaned = licenseKey.replace("-", "").uppercase().filter { it.isLetterOrDigit() }
            val withoutPrefix = if (cleaned.startsWith("LS", ignoreCase = true)) {
                cleaned.substring(2)
            } else {
                cleaned
            }
            Toast.makeText(this, "激活码格式不正确\n输入长度: ${withoutPrefix.length}, 期望: $ACTIVATION_CODE_LENGTH\n格式: LS-XXXX-XXXX-XXXX-X\n实际输入: $licenseKey", Toast.LENGTH_LONG).show()
            Log.d(TAG, "激活码验证失败: $licenseKey, 清理后: $cleaned, 不含前缀: $withoutPrefix, 长度: ${withoutPrefix.length}")
            return
        }
        
        // 隐藏键盘
        hideKeyboard()
        
        // 执行激活
        activateWithLicenseKey(licenseKey)
    }
    
    /**
     * 使用激活码激活
     */
    private fun activateWithLicenseKey(licenseKey: String) {
        lifecycleScope.launch {
            try {
                // 显示加载提示
                tvStatus.text = "正在激活..."
                tvStatus.setTextColor(0xFF4ADFFF.toInt())
                
                val result = activationManager.activate(licenseKey)
                
                if (result.success) {
                    Toast.makeText(this@ActivationManagementActivity, "✅ 激活成功", Toast.LENGTH_LONG).show()
                    hideInputPanel()
                    updateUI()
                } else {
                    val errorMsg = result.message ?: "激活失败，请检查激活码是否正确"
                    Toast.makeText(this@ActivationManagementActivity, "❌ $errorMsg", Toast.LENGTH_LONG).show()
                    tvStatus.text = "❌ 激活失败"
                    tvStatus.setTextColor(0xFFFF4444.toInt())
                }
            } catch (e: Exception) {
                Log.e(TAG, "激活失败", e)
                Toast.makeText(this@ActivationManagementActivity, "❌ 激活过程中发生错误: ${e.message}", Toast.LENGTH_LONG).show()
                tvStatus.text = "❌ 激活失败"
                tvStatus.setTextColor(0xFFFF4444.toInt())
            }
        }
    }
    
    /**
     * 检查激活状态
     */
    private fun checkActivationStatus() {
        lifecycleScope.launch {
            try {
                tvStatus.text = "正在验证..."
                tvStatus.setTextColor(0xFF4ADFFF.toInt())
                
                val result = activationManager.checkOnStartup()
                
                if (result.activated) {
                    Toast.makeText(this@ActivationManagementActivity, "✅ 验证成功", Toast.LENGTH_SHORT).show()
                } else if (result.needLicense) {
                    Toast.makeText(this@ActivationManagementActivity, "⚠️ 需要激活码", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ActivationManagementActivity, "❌ 验证失败: ${result.error}", Toast.LENGTH_LONG).show()
                }
                
                updateUI()
            } catch (e: Exception) {
                Log.e(TAG, "验证失败", e)
                Toast.makeText(this@ActivationManagementActivity, "❌ 验证过程中发生错误", Toast.LENGTH_SHORT).show()
                updateUI()
            }
        }
    }
    
    /**
     * 继续到应用
     */
    private fun continueToApp() {
        // 启动FloatingLauncherActivity
        val intent = Intent(this, FloatingLauncherActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    /**
     * 清除激活状态（需要长按确认）
     */
    private fun clearActivation() {
        lifecycleScope.launch {
            try {
                activationManager.clearActivation()
                Toast.makeText(this@ActivationManagementActivity, "✅ 激活状态已清除", Toast.LENGTH_SHORT).show()
                updateUI()
            } catch (e: Exception) {
                Log.e(TAG, "清除激活状态失败", e)
                Toast.makeText(this@ActivationManagementActivity, "❌ 操作失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 格式化日期时间
     */
    private fun formatDateTime(dateTimeStr: String?): String {
        if (dateTimeStr == null || dateTimeStr == "null") return "未知"
        
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(dateTimeStr)
            
            val displaySdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            displaySdf.format(date ?: Date())
        } catch (e: Exception) {
            dateTimeStr
        }
    }
    
    /**
     * 处理返回键
     */
    override fun onBackPressed() {
        if (inputContainer.visibility == View.VISIBLE) {
            // 如果输入面板可见，先隐藏键盘，再隐藏面板
            val hasFocus = etInputs.any { it.hasFocus() }
            if (hasFocus) {
                hideKeyboard()
                etInputs.forEach { it.clearFocus() }
            } else {
                hideInputPanel()
            }
        } else {
            // 否则继续到应用（即使未激活也允许）
            continueToApp()
        }
    }
    
}

