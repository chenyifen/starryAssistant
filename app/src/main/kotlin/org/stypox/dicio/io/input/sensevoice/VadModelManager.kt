package org.stypox.dicio.io.input.sensevoice

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import org.stypox.dicio.util.PermissionHelper
import org.stypox.dicio.util.ModelPathManager
import java.io.File

/**
 * VAD模型管理器
 * 负责VAD模型的路径管理和可用性检查
 */
object VadModelManager {
    
    private const val TAG = "VadModelManager"
    
    // VAD模型路径配置
    private const val ASSETS_VAD_PATH = "models/vad"                            // withModels渠道
    private const val VAD_MODEL_FILE = "silero_vad.onnx"
    
    // 获取外部存储VAD路径（使用 ModelPathManager）
    private fun getExternalVadDir(context: Context): String {
        return ModelPathManager.getExternalVadModelsPath(context)
    }
    
    /**
     * VAD模型路径信息
     */
    data class VadModelPaths(
        val modelPath: String,
        val isFromAssets: Boolean
    )
    
    /**
     * 检查VAD模型是否可用
     */
    fun isVadModelAvailable(context: Context): Boolean {
        return try {
            val modelPaths = getVadModelPaths(context)
            if (modelPaths != null) {
                if (modelPaths.isFromAssets) {
                    // 检查Assets中的模型
                    context.assets.open("models/vad/$VAD_MODEL_FILE").use { true }
                } else {
                    // 检查外部存储中的模型
                    File(modelPaths.modelPath).exists()
                }
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查VAD模型可用性失败", e)
            false
        }
    }
    
    /**
     * 获取VAD模型路径
     */
    fun getVadModelPaths(context: Context): VadModelPaths? {
        return try {
            // 检查构建变体，决定优先级
            val buildType = context.packageName.contains("withModels") || 
                           context.applicationInfo.sourceDir.contains("withModels")
            
            if (buildType) {
                // withModels变体：优先使用Assets
                Log.d(TAG, "🏷️ 检测到withModels变体，优先使用Assets模型")
                try {
                    context.assets.open("$ASSETS_VAD_PATH/$VAD_MODEL_FILE").use {
                        Log.d(TAG, "✅ 使用Assets中的VAD模型: $ASSETS_VAD_PATH/$VAD_MODEL_FILE")
                        return VadModelPaths(
                            modelPath = "$ASSETS_VAD_PATH/$VAD_MODEL_FILE",
                            isFromAssets = true
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "withModels变体中未找到Assets VAD模型，尝试外部存储")
                }
            }
            
            // main渠道或Assets失败：检查外部存储
            if (PermissionHelper.hasExternalStoragePermission(context)) {
                val externalVadDir = getExternalVadDir(context)
                val externalModelFile = File(externalVadDir, VAD_MODEL_FILE)
                if (externalModelFile.exists()) {
                    Log.d(TAG, "✅ 使用外部存储VAD模型: ${externalModelFile.absolutePath}")
                    return VadModelPaths(
                        modelPath = externalModelFile.absolutePath,
                        isFromAssets = false
                    )
                } else {
                    Log.d(TAG, "外部存储VAD模型不存在: ${externalModelFile.absolutePath}")
                }
            } else {
                Log.d(TAG, "无外部存储权限，无法访问外部VAD模型")
            }
            
            // 如果不是withModels变体，再尝试Assets作为备选
            if (!buildType) {
                try {
                    context.assets.open("$ASSETS_VAD_PATH/$VAD_MODEL_FILE").use {
                        Log.d(TAG, "✅ 使用Assets中的VAD模型作为备选: $ASSETS_VAD_PATH/$VAD_MODEL_FILE")
                        return VadModelPaths(
                            modelPath = "$ASSETS_VAD_PATH/$VAD_MODEL_FILE",
                            isFromAssets = true
                        )
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Assets中也未找到VAD模型")
                }
            }
            
            Log.e(TAG, "❌ 未找到可用的VAD模型")
            null
        } catch (e: Exception) {
            Log.e(TAG, "获取VAD模型路径失败", e)
            null
        }
    }
    
    /**
     * 创建VAD配置（优化版：快速响应）
     */
    fun createVadConfig(context: Context): VadModelConfig? {
        val modelPaths = getVadModelPaths(context) ?: return null
        
        return VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = modelPaths.modelPath,
                threshold = 0.3f,             // 降低阈值：0.5 → 0.3 (更敏感)
                minSilenceDuration = 0.1f,    // 最小静音持续时间: 0.25 → 0.1秒 (更快响应)
                minSpeechDuration = 0.05f,    // 最小语音持续时间: 0.25 → 0.05秒 (更快触发)
                windowSize = 512,             // 窗口大小: 32ms (保持不变)
                maxSpeechDuration = 10.0f     // 最大语音持续时间: 5 → 10秒 (支持更长语音)
            ),
            sampleRate = 16000,
            numThreads = 1,
            provider = "cpu",
            debug = false
        )
    }
    
    /**
     * 获取VAD模型信息
     */
    fun getVadModelInfo(context: Context): String {
        val modelPaths = getVadModelPaths(context)
        return if (modelPaths != null) {
            val source = if (modelPaths.isFromAssets) "Assets" else "外部存储"
            val file = if (modelPaths.isFromAssets) {
                null
            } else {
                File(modelPaths.modelPath)
            }
            val size = file?.let { "${it.length() / 1024 / 1024}MB" } ?: "未知大小"
            "VAD模型 ($source, $size)"
        } else {
            "VAD模型不可用"
        }
    }
}
