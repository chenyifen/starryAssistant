package com.ai.voice.license

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.launch

/**
 * 激活码输入对话框
 */
object ActivationDialog {
    
    /**
     * 显示激活码输入对话框
     */
    fun show(
        context: Context,
        lifecycleScope: LifecycleCoroutineScope,
        onActivated: () -> Unit
    ) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("请输入激活码")
        builder.setMessage("设备未激活，请输入激活码以继续使用")
        
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "请输入激活码"
        }
        builder.setView(input)
        
        builder.setPositiveButton("激活") { dialog, _ ->
            val licenseKey = input.text.toString().trim()
            if (licenseKey.isEmpty()) {
                Toast.makeText(context, "激活码不能为空", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            
            // 显示加载提示
            val loadingDialog = AlertDialog.Builder(context)
                .setMessage("正在激活...")
                .setCancelable(false)
                .create()
            loadingDialog.show()
            
            // 执行激活
            lifecycleScope.launch {
                try {
                    val activationManager = LicenseActivationManager.getInstance()
                    val result = activationManager.activate(licenseKey)
                    
                    loadingDialog.dismiss()
                    
                    if (result.success) {
                        Toast.makeText(context, "激活成功", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        onActivated()
                    } else {
                        val errorMsg = result.message ?: "激活失败，请检查激活码是否正确"
                        AlertDialog.Builder(context)
                            .setTitle("激活失败")
                            .setMessage(errorMsg)
                            .setPositiveButton("确定", null)
                            .show()
                    }
                } catch (e: Exception) {
                    loadingDialog.dismiss()
                    AlertDialog.Builder(context)
                        .setTitle("激活失败")
                        .setMessage("激活过程中发生错误: ${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }
        
        builder.setNegativeButton("取消") { dialog, _ -> 
            dialog.cancel()
        }
        
        builder.setCancelable(false)
        builder.show()
    }
}

