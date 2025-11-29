package com.ai.voice.license

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ai.voice.R
import com.ai.voice.ui.floating.FloatingLauncherActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 激活管理Activity - TV友好界面
 * 支持遥控器操作，适用于超大屏幕
 */
class ActivationManagementActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "ActivationManagement"
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
    
    // 激活码输入
    private lateinit var tvInputDisplay: TextView
    private lateinit var inputContainer: View
    
    // 虚拟键盘按钮
    private val numberButtons = mutableListOf<Button>()
    private lateinit var btnBackspace: Button
    private lateinit var btnClear: Button
    private lateinit var btnSubmit: Button
    
    private val inputBuffer = StringBuilder()
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
        tvInputDisplay = findViewById(R.id.tv_input_display)
        inputContainer = findViewById(R.id.input_container)
        
        // 虚拟键盘
        numberButtons.add(findViewById(R.id.btn_num_0))
        numberButtons.add(findViewById(R.id.btn_num_1))
        numberButtons.add(findViewById(R.id.btn_num_2))
        numberButtons.add(findViewById(R.id.btn_num_3))
        numberButtons.add(findViewById(R.id.btn_num_4))
        numberButtons.add(findViewById(R.id.btn_num_5))
        numberButtons.add(findViewById(R.id.btn_num_6))
        numberButtons.add(findViewById(R.id.btn_num_7))
        numberButtons.add(findViewById(R.id.btn_num_8))
        numberButtons.add(findViewById(R.id.btn_num_9))
        
        // 字母按钮（A-Z）
        val letterIds = listOf(
            R.id.btn_letter_a, R.id.btn_letter_b, R.id.btn_letter_c, R.id.btn_letter_d,
            R.id.btn_letter_e, R.id.btn_letter_f, R.id.btn_letter_g, R.id.btn_letter_h,
            R.id.btn_letter_i, R.id.btn_letter_j, R.id.btn_letter_k, R.id.btn_letter_l,
            R.id.btn_letter_m, R.id.btn_letter_n, R.id.btn_letter_o, R.id.btn_letter_p,
            R.id.btn_letter_q, R.id.btn_letter_r, R.id.btn_letter_s, R.id.btn_letter_t,
            R.id.btn_letter_u, R.id.btn_letter_v, R.id.btn_letter_w, R.id.btn_letter_x,
            R.id.btn_letter_y, R.id.btn_letter_z
        )
        
        letterIds.forEach { id ->
            try {
                val btn = findViewById<Button>(id)
                numberButtons.add(btn)
            } catch (e: Exception) {
                // 按钮不存在，跳过
            }
        }
        
        // 连字符
        try {
            numberButtons.add(findViewById(R.id.btn_dash))
        } catch (e: Exception) {
            // 按钮不存在
        }
        
        btnBackspace = findViewById(R.id.btn_backspace)
        btnClear = findViewById(R.id.btn_clear)
        btnSubmit = findViewById(R.id.btn_submit)
        
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
        
        // 虚拟键盘 - 数字和字母
        numberButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                val text = button.text.toString()
                onKeyInput(text)
            }
        }
        
        // 退格键
        btnBackspace.setOnClickListener {
            onBackspace()
        }
        
        // 清除键
        btnClear.setOnClickListener {
            onClear()
        }
        
        // 提交键
        btnSubmit.setOnClickListener {
            onSubmit()
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
                    tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                    
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
                    tvStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
                    
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
                tvStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            }
        }
    }
    
    /**
     * 显示激活码输入面板
     */
    private fun showInputPanel() {
        inputContainer.visibility = View.VISIBLE
        inputBuffer.clear()
        updateInputDisplay()
        
        // 聚焦到第一个按钮
        numberButtons.firstOrNull()?.requestFocus()
        
        Toast.makeText(this, "请使用遥控器输入激活码", Toast.LENGTH_LONG).show()
    }
    
    /**
     * 隐藏激活码输入面板
     */
    private fun hideInputPanel() {
        inputContainer.visibility = View.GONE
        btnActivate.requestFocus()
    }
    
    /**
     * 键盘输入
     */
    private fun onKeyInput(char: String) {
        if (inputBuffer.length < 50) {  // 限制长度
            inputBuffer.append(char)
            updateInputDisplay()
        }
    }
    
    /**
     * 退格
     */
    private fun onBackspace() {
        if (inputBuffer.isNotEmpty()) {
            inputBuffer.deleteCharAt(inputBuffer.length - 1)
            updateInputDisplay()
        }
    }
    
    /**
     * 清除
     */
    private fun onClear() {
        inputBuffer.clear()
        updateInputDisplay()
    }
    
    /**
     * 提交激活码
     */
    private fun onSubmit() {
        val licenseKey = inputBuffer.toString().trim()
        
        if (licenseKey.isEmpty()) {
            Toast.makeText(this, "请输入激活码", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 执行激活
        activateWithLicenseKey(licenseKey)
    }
    
    /**
     * 更新输入显示
     */
    private fun updateInputDisplay() {
        tvInputDisplay.text = inputBuffer.toString()
    }
    
    /**
     * 使用激活码激活
     */
    private fun activateWithLicenseKey(licenseKey: String) {
        lifecycleScope.launch {
            try {
                // 显示加载提示
                tvStatus.text = "正在激活..."
                tvStatus.setTextColor(getColor(android.R.color.holo_blue_dark))
                
                val result = activationManager.activate(licenseKey)
                
                if (result.success) {
                    Toast.makeText(this@ActivationManagementActivity, "✅ 激活成功", Toast.LENGTH_LONG).show()
                    hideInputPanel()
                    updateUI()
                } else {
                    val errorMsg = result.message ?: "激活失败，请检查激活码是否正确"
                    Toast.makeText(this@ActivationManagementActivity, "❌ $errorMsg", Toast.LENGTH_LONG).show()
                    tvStatus.text = "❌ 激活失败"
                    tvStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                }
            } catch (e: Exception) {
                Log.e(TAG, "激活失败", e)
                Toast.makeText(this@ActivationManagementActivity, "❌ 激活过程中发生错误: ${e.message}", Toast.LENGTH_LONG).show()
                tvStatus.text = "❌ 激活失败"
                tvStatus.setTextColor(getColor(android.R.color.holo_red_dark))
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
                tvStatus.setTextColor(getColor(android.R.color.holo_blue_dark))
                
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
            // 如果输入面板可见，隐藏它
            hideInputPanel()
        } else {
            // 否则继续到应用（即使未激活也允许）
            continueToApp()
        }
    }
    
    /**
     * 处理按键事件（支持遥控器数字键直接输入）
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // 如果输入面板可见，支持遥控器数字键直接输入
        if (inputContainer.visibility == View.VISIBLE) {
            when (keyCode) {
                KeyEvent.KEYCODE_0 -> { onKeyInput("0"); return true }
                KeyEvent.KEYCODE_1 -> { onKeyInput("1"); return true }
                KeyEvent.KEYCODE_2 -> { onKeyInput("2"); return true }
                KeyEvent.KEYCODE_3 -> { onKeyInput("3"); return true }
                KeyEvent.KEYCODE_4 -> { onKeyInput("4"); return true }
                KeyEvent.KEYCODE_5 -> { onKeyInput("5"); return true }
                KeyEvent.KEYCODE_6 -> { onKeyInput("6"); return true }
                KeyEvent.KEYCODE_7 -> { onKeyInput("7"); return true }
                KeyEvent.KEYCODE_8 -> { onKeyInput("8"); return true }
                KeyEvent.KEYCODE_9 -> { onKeyInput("9"); return true }
                KeyEvent.KEYCODE_DEL -> { onBackspace(); return true }
            }
        }
        
        return super.onKeyDown(keyCode, event)
    }
}

