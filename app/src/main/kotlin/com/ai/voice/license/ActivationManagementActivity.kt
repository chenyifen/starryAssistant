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

class ActivationManagementActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "ActivationManagement"
        private val ACTIVATION_CODE_PATTERN = Pattern.compile("^LS-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{1}$")
        private const val ACTIVATION_CODE_LENGTH = 13
        private const val PREFIX = "LS-"
    }
    
    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvDeviceInfo: TextView
    private lateinit var tvLicenseKey: TextView
    private lateinit var tvActivationTime: TextView
    private lateinit var tvExpiryTime: TextView
    private lateinit var statusContainer: View
    
    private lateinit var btnActivate: Button
    private lateinit var btnCheckStatus: Button
    private lateinit var btnContinue: Button
    private lateinit var btnClearActivation: Button
    
    private lateinit var inputContainer: View
    private lateinit var btnSubmit: Button
    private lateinit var etBarcodeInput: EditText
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
    
    private fun initViews() {
        tvTitle = findViewById(R.id.tv_title)
        tvStatus = findViewById(R.id.tv_status)
        tvDeviceInfo = findViewById(R.id.tv_device_info)
        tvLicenseKey = findViewById(R.id.tv_license_key)
        tvActivationTime = findViewById(R.id.tv_activation_time)
        tvExpiryTime = findViewById(R.id.tv_expiry_time)
        statusContainer = findViewById(R.id.status_container)
        
        btnActivate = findViewById(R.id.btn_activate)
        btnCheckStatus = findViewById(R.id.btn_check_status)
        btnContinue = findViewById(R.id.btn_continue)
        btnClearActivation = findViewById(R.id.btn_clear_activation)
        
        inputContainer = findViewById(R.id.input_container)
        btnSubmit = findViewById(R.id.btn_submit)
        etBarcodeInput = findViewById(R.id.et_barcode_input)
        
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
        inputContainer.visibility = View.GONE
    }
    
    private fun setupListeners() {
        btnActivate.setOnClickListener { showInputPanel() }
        btnCheckStatus.setOnClickListener { checkActivationStatus() }
        btnContinue.setOnClickListener { continueToApp() }
        btnClearActivation.setOnLongClickListener { clearActivation(); true }
        btnSubmit.setOnClickListener { onSubmit() }
        setupSegmentInputListeners()
        setupBarcodeInputListener()
    }
    
    private fun setupBarcodeInputListener() {
        etBarcodeInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString().trim()
                if (input.length >= 10) {
                    Log.d(TAG, "Barcode input: $input")
                    fillFromBarcodeInput(input)
                    etBarcodeInput.setText("")
                }
            }
        })
        
        etBarcodeInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || 
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val input = etBarcodeInput.text.toString().trim()
                if (input.isNotEmpty()) {
                    fillFromBarcodeInput(input)
                    etBarcodeInput.setText("")
                }
                true
            } else {
                false
            }
        }
    }
    
    private fun fillFromBarcodeInput(input: String) {
        val formatted = formatActivationCode(input)
        if (validateActivationCode(formatted)) {
            val cleaned = formatted.replace("-", "").uppercase().filter { it.isLetterOrDigit() }
            val withoutPrefix = if (cleaned.startsWith("LS", ignoreCase = true)) {
                cleaned.substring(2)
            } else {
                cleaned
            }
            
            if (withoutPrefix.length == 13) {
                for (i in etInputs.indices) {
                    if (i < withoutPrefix.length) {
                        etInputs[i].setText(withoutPrefix[i].toString())
                    }
                }
                onSubmit()
            } else {
                Toast.makeText(this, "Invalid format: $input", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Invalid format: $input", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupSegmentInputListeners() {
        for (i in etInputs.indices) {
            val currentEditText = etInputs[i]
            
            currentEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                
                override fun afterTextChanged(s: Editable?) {
                    val text = s.toString().uppercase().filter { it.isLetterOrDigit() }
                    if (text.isNotEmpty() && text != s.toString()) {
                        s?.replace(0, s.length, text.take(1))
                    }
                    if (text.isNotEmpty() && i < etInputs.size - 1) {
                        etInputs[i + 1].requestFocus()
                    }
                }
            })
            
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
    
    private fun showKeyboard() {
        if (etInputs.isNotEmpty()) {
            etInputs[0].requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etInputs[0], InputMethodManager.SHOW_IMPLICIT)
        }
    }
    
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocus = currentFocus
        if (currentFocus != null) {
            imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
        }
    }
    
    private fun collectActivationCode(): String {
        val code = StringBuilder(PREFIX)
        var charCount = 0
        for (i in etInputs.indices) {
            val text = etInputs[i].text.toString().uppercase().filter { it.isLetterOrDigit() }
            if (text.isNotEmpty()) {
                code.append(text.take(1))
                charCount++
            }
            when (i) {
                3, 7, 11 -> code.append("-")
            }
        }
        val result = code.toString()
        Log.d(TAG, "Collected: $result, chars: $charCount")
        return result
    }
    
    private fun clearAllInputs() {
        for (editText in etInputs) {
            editText.setText("")
        }
    }
    
    private fun updateUI() {
        lifecycleScope.launch {
            try {
                val summary = activationManager.getActivationSummary()
                val fingerprint = activationManager.getDeviceFingerprint()
                
                if (summary.isActivated) {
                    tvStatus.text = "Activated"
                    tvStatus.setTextColor(0xFF34C759.toInt())
                    statusContainer.setBackgroundResource(R.drawable.bg_status_activated)
                    
                    tvLicenseKey.text = "${summary.licenseKey?.take(16) ?: ""}..."
                    tvLicenseKey.visibility = if (summary.licenseKey != null) View.VISIBLE else View.GONE
                    
                    tvActivationTime.text = "Activated: ${formatDateTime(summary.activatedAt)}"
                    tvActivationTime.visibility = if (summary.activatedAt != null) View.VISIBLE else View.GONE
                    
                    if (summary.expiresAt != null && summary.expiresAt != "null") {
                        tvExpiryTime.text = "Expires: ${formatDateTime(summary.expiresAt)}"
                        tvExpiryTime.visibility = View.VISIBLE
                    } else {
                        tvExpiryTime.text = "Expires: Never"
                        tvExpiryTime.visibility = View.VISIBLE
                    }
                    
                    btnActivate.visibility = View.GONE
                    btnContinue.visibility = View.VISIBLE
                } else {
                    tvStatus.text = "Not Activated"
                    tvStatus.setTextColor(0xFFFF9500.toInt())
                    statusContainer.setBackgroundResource(R.drawable.bg_status_pending)
                    
                    tvLicenseKey.visibility = View.GONE
                    tvActivationTime.visibility = View.GONE
                    tvExpiryTime.visibility = View.GONE
                    
                    btnActivate.visibility = View.VISIBLE
                    btnContinue.visibility = View.GONE
                }
                
                val deviceId = summary.macAddress ?: fingerprint.mac
                val isRealMac = deviceId?.matches(Regex("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$")) == true
                
                tvDeviceInfo.text = buildString {
                    if (deviceId != null) {
                        if (isRealMac) {
                            append("MAC: $deviceId\n")
                        } else {
                            append("Device ID: $deviceId\n")
                        }
                    }
                    append("Model: ${fingerprint.toSummary()}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Update UI failed", e)
                tvStatus.text = "Error"
                tvStatus.setTextColor(0xFFFF3B30.toInt())
                statusContainer.setBackgroundResource(R.drawable.bg_status_pending)
            }
        }
    }
    
    private fun showInputPanel() {
        inputContainer.visibility = View.VISIBLE
        clearAllInputs()
        etBarcodeInput.requestFocus()
        if (etInputs.isNotEmpty()) {
            etInputs[0].requestFocus()
        }
    }
    
    private fun hideInputPanel() {
        hideKeyboard()
        inputContainer.visibility = View.GONE
        btnActivate.requestFocus()
    }
    
    private fun formatActivationCodeWithHyphens(input: String): String {
        if (input.isEmpty()) return ""
        val sb = StringBuilder()
        var index = 0
        if (input.length > index) {
            val end = minOf(index + 2, input.length)
            sb.append(input.substring(index, end))
            index = end
            if (index < input.length) sb.append("-")
        }
        while (index < input.length) {
            val end = minOf(index + 5, input.length)
            sb.append(input.substring(index, end))
            index = end
            if (index < input.length) sb.append("-")
        }
        return sb.toString()
    }
    
    private fun formatActivationCode(input: String): String {
        val cleaned = input.replace("-", "").uppercase().filter { it.isLetterOrDigit() }
        if (cleaned.isEmpty()) return ""
        val withoutPrefix = if (cleaned.startsWith("LS", ignoreCase = true)) cleaned.substring(2) else cleaned
        if (withoutPrefix.length != 13) return cleaned
        return "LS-${withoutPrefix.substring(0, 4)}-${withoutPrefix.substring(4, 8)}-${withoutPrefix.substring(8, 12)}-${withoutPrefix.substring(12, 13)}"
    }
    
    
    private fun validateActivationCode(code: String): Boolean {
        val cleaned = code.replace("-", "").uppercase().filter { it.isLetterOrDigit() }
        val withoutPrefix = if (cleaned.startsWith("LS", ignoreCase = true)) cleaned.substring(2) else cleaned
        if (withoutPrefix.length != 13) {
            Log.d(TAG, "Invalid length: ${withoutPrefix.length}")
            return false
        }
        val formatted = formatActivationCode(cleaned)
        val matches = ACTIVATION_CODE_PATTERN.matcher(formatted).matches()
        if (!matches) Log.d(TAG, "Format mismatch: $formatted")
        return matches
    }
    
    private fun onSubmit() {
        val licenseKey = collectActivationCode()
        val allFilled = etInputs.all { it.text.toString().isNotEmpty() }
        if (!allFilled) {
            Toast.makeText(this, "Please complete the code", Toast.LENGTH_SHORT).show()
            return
        }
        if (!validateActivationCode(licenseKey)) {
            Toast.makeText(this, "Invalid format", Toast.LENGTH_SHORT).show()
            return
        }
        hideKeyboard()
        activateWithLicenseKey(licenseKey)
    }
    
    private fun activateWithLicenseKey(licenseKey: String) {
        lifecycleScope.launch {
            try {
                tvStatus.text = "Activating..."
                tvStatus.setTextColor(0xFF007AFF.toInt())
                
                val result = activationManager.activate(licenseKey)
                
                if (result.success) {
                    Toast.makeText(this@ActivationManagementActivity, "Activation successful", Toast.LENGTH_LONG).show()
                    hideInputPanel()
                    updateUI()
                } else {
                    val errorMsg = result.message ?: "Activation failed"
                    Toast.makeText(this@ActivationManagementActivity, errorMsg, Toast.LENGTH_LONG).show()
                    tvStatus.text = "Failed"
                    tvStatus.setTextColor(0xFFFF3B30.toInt())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Activation failed", e)
                Toast.makeText(this@ActivationManagementActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                tvStatus.text = "Error"
                tvStatus.setTextColor(0xFFFF3B30.toInt())
            }
        }
    }
    
    private fun checkActivationStatus() {
        lifecycleScope.launch {
            try {
                tvStatus.text = "Verifying..."
                tvStatus.setTextColor(0xFF007AFF.toInt())
                
                val result = activationManager.checkOnStartup()
                
                if (result.activated) {
                    Toast.makeText(this@ActivationManagementActivity, "Verified", Toast.LENGTH_SHORT).show()
                } else if (result.needLicense) {
                    Toast.makeText(this@ActivationManagementActivity, "License required", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ActivationManagementActivity, "Verification failed: ${result.error}", Toast.LENGTH_LONG).show()
                }
                
                updateUI()
            } catch (e: Exception) {
                Log.e(TAG, "Verification failed", e)
                Toast.makeText(this@ActivationManagementActivity, "Verification error", Toast.LENGTH_SHORT).show()
                updateUI()
            }
        }
    }
    
    private fun continueToApp() {
        val intent = Intent(this, FloatingLauncherActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun clearActivation() {
        lifecycleScope.launch {
            try {
                activationManager.clearActivation()
                Toast.makeText(this@ActivationManagementActivity, "Activation cleared", Toast.LENGTH_SHORT).show()
                updateUI()
            } catch (e: Exception) {
                Log.e(TAG, "Clear activation failed", e)
                Toast.makeText(this@ActivationManagementActivity, "Operation failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
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

