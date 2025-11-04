package com.ai.voice.io.input.sensevoice

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import com.ai.voice.util.PermissionHelper
import com.ai.voice.util.ModelPathManager
import java.io.File

/**
 * VAD模型管理器
 * 负责VAD模型的路径管理和可用性检查
 */
object VadModelManager {
    
    private const val TAG = "VadModelManager"
    
    // VAD模型路径配置
    private const val ASSETS_VAD_PATH = "models/vad"                            // withModels渠道
    private const val ASSETS_ROOT_VAD_PATH = "silero_vad.onnx"                  // 🆕 assets根目录下的VAD模型
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
                    context.assets.open(modelPaths.modelPath).use { true }
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
     * 🆕 优先级：1. assets根目录的silero_vad.onnx 2. models/vad/silero_vad.onnx 3. 外部存储
     */
    fun getVadModelPaths(context: Context): VadModelPaths? {
        return try {
            // 🆕 优先级1：优先检查assets根目录下的silero_vad.onnx
            try {
                context.assets.open(ASSETS_ROOT_VAD_PATH).use {
                    Log.d(TAG, "✅ 使用Assets根目录下的VAD模型: $ASSETS_ROOT_VAD_PATH")
                    return VadModelPaths(
                        modelPath = ASSETS_ROOT_VAD_PATH,
                        isFromAssets = true
                    )
                }
            } catch (e: Exception) {
                Log.d(TAG, "Assets根目录下未找到VAD模型，尝试其他路径")
            }
            
            // 优先级2：检查models/vad/silero_vad.onnx
            try {
                context.assets.open("$ASSETS_VAD_PATH/$VAD_MODEL_FILE").use {
                    Log.d(TAG, "✅ 使用Assets中的VAD模型: $ASSETS_VAD_PATH/$VAD_MODEL_FILE")
                    return VadModelPaths(
                        modelPath = "$ASSETS_VAD_PATH/$VAD_MODEL_FILE",
                        isFromAssets = true
                    )
                }
            } catch (e: Exception) {
                Log.d(TAG, "Assets/${ASSETS_VAD_PATH}下未找到VAD模型，尝试外部存储")
            }
            
            // 优先级3：检查外部存储
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
            
            Log.e(TAG, "❌ 未找到可用的VAD模型")
            null
        } catch (e: Exception) {
            Log.e(TAG, "获取VAD模型路径失败", e)
            null
        }
    }
    
    /**
     * 创建VAD配置
     */
    fun createVadConfig(context: Context): VadModelConfig? {
        val modelPaths = getVadModelPaths(context) ?: return null
        
        return VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = modelPaths.modelPath,
                threshold = 0.5f,
                minSilenceDuration = 0.25f,  // 最小静音持续时间
                minSpeechDuration = 0.25f,   // 最小语音持续时间
                windowSize = 512,            // 窗口大小
                maxSpeechDuration = 5.0f     // 最大语音持续时间
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
