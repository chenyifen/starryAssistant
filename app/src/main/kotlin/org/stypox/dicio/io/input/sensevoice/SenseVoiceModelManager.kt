package org.stypox.dicio.io.input.sensevoice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.stypox.dicio.util.AssetModelManager
import org.stypox.dicio.util.DebugLogger
import org.stypox.dicio.util.ModelPathManager
import java.io.File

/**
 * SenseVoice多语言ASR模型管理器
 * 负责模型的加载、验证和路径管理
 */
object SenseVoiceModelManager {
    private const val TAG = "SenseVoiceModelManager"
    
    // SenseVoice模型文件名
    private const val MODEL_FILE = "model.onnx"
    private const val MODEL_INT8_FILE = "model.int8.onnx" 
    private const val TOKENS_FILE = "tokens.txt"
    
    // 模型目录路径
    private const val ASSETS_MODEL_PATH = "models/asr/sensevoice"
    
    // 获取外部存储路径（使用 ModelPathManager）
    private fun getExternalModelPath(context: Context): String {
        return ModelPathManager.getExternalSenseVoiceModelsPath(context)
    }
    
    data class SenseVoiceModelPaths(
        val modelPath: String,
        val tokensPath: String,
        val isQuantized: Boolean = false,
        val isFromAssets: Boolean = false
    )
    
    /**
     * 检查SenseVoice模型是否可用
     */
    suspend fun isModelAvailable(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val paths = getModelPaths(context)
                paths != null && validateModelFiles(paths)
            } catch (e: Exception) {
                Log.e(TAG, "检查SenseVoice模型可用性失败", e)
                false
            }
        }
    }
    
    /**
     * 获取SenseVoice模型路径
     * 优先级: 外部存储 > Assets
     */
    suspend fun getModelPaths(context: Context): SenseVoiceModelPaths? {
        return withContext(Dispatchers.IO) {
            try {
                // 1. 检查外部存储 (noModel渠道)
                val externalPaths = checkExternalStorage(context)
                if (externalPaths != null) {
                    DebugLogger.logModelManagement(TAG, "✅ 使用外部存储SenseVoice模型: ${externalPaths.modelPath}")
                    return@withContext externalPaths
                }
                
                // 2. 检查Assets模型 (withModel渠道，直接使用)
                val assetsPaths = checkAssetsModel(context)
                if (assetsPaths != null) {
                    DebugLogger.logModelManagement(TAG, "✅ 使用Assets中的SenseVoice模型: ${assetsPaths.modelPath}")
                    return@withContext assetsPaths
                }
                
                DebugLogger.logModelManagement(TAG, "❌ 未找到可用的SenseVoice模型")
                null
            } catch (e: Exception) {
                Log.e(TAG, "获取SenseVoice模型路径失败", e)
                null
            }
        }
    }
    
    /**
     * 检查外部存储中的模型
     */
    private fun checkExternalStorage(context: Context): SenseVoiceModelPaths? {
        try {
            val externalModelPath = getExternalModelPath(context)
            val externalDir = File(externalModelPath)
            if (!externalDir.exists() || !externalDir.isDirectory) {
                DebugLogger.logModelManagement(TAG, "外部存储目录不存在: $externalModelPath")
                return null
            }
            
            // 优先使用量化模型
            val quantizedModel = File(externalDir, MODEL_INT8_FILE)
            val regularModel = File(externalDir, MODEL_FILE)
            val tokens = File(externalDir, TOKENS_FILE)
            
            return when {
                quantizedModel.exists() && tokens.exists() -> {
                    DebugLogger.logModelManagement(TAG, "找到外部量化SenseVoice模型")
                    SenseVoiceModelPaths(
                        modelPath = quantizedModel.absolutePath,
                        tokensPath = tokens.absolutePath,
                        isQuantized = true,
                        isFromAssets = false
                    )
                }
                regularModel.exists() && tokens.exists() -> {
                    DebugLogger.logModelManagement(TAG, "找到外部普通SenseVoice模型")
                    SenseVoiceModelPaths(
                        modelPath = regularModel.absolutePath,
                        tokensPath = tokens.absolutePath,
                        isQuantized = false,
                        isFromAssets = false
                    )
                }
                else -> {
                    DebugLogger.logModelManagement(TAG, "外部存储缺少必要的SenseVoice模型文件")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查外部存储SenseVoice模型失败", e)
            return null
        }
    }
    
    
    
    /**
     * 检查Assets模型（直接使用，不复制）
     */
    private fun checkAssetsModel(context: Context): SenseVoiceModelPaths? {
        return try {
            if (!hasModelInAssets(context)) {
                return null
            }
            
            val assets = context.assets.list(ASSETS_MODEL_PATH) ?: return null
            val assetsList = assets.toSet()
            
            // 优先使用量化模型
            val hasQuantized = assetsList.contains(MODEL_INT8_FILE)
            val hasRegular = assetsList.contains(MODEL_FILE)
            val hasTokens = assetsList.contains(TOKENS_FILE)
            
            when {
                hasQuantized && hasTokens -> {
                    DebugLogger.logModelManagement(TAG, "找到Assets量化SenseVoice模型")
                    SenseVoiceModelPaths(
                        modelPath = "$ASSETS_MODEL_PATH/$MODEL_INT8_FILE",
                        tokensPath = "$ASSETS_MODEL_PATH/$TOKENS_FILE",
                        isQuantized = true,
                        isFromAssets = true
                    )
                }
                hasRegular && hasTokens -> {
                    DebugLogger.logModelManagement(TAG, "找到Assets普通SenseVoice模型")
                    SenseVoiceModelPaths(
                        modelPath = "$ASSETS_MODEL_PATH/$MODEL_FILE",
                        tokensPath = "$ASSETS_MODEL_PATH/$TOKENS_FILE",
                        isQuantized = false,
                        isFromAssets = true
                    )
                }
                else -> {
                    DebugLogger.logModelManagement(TAG, "Assets中缺少必要的SenseVoice模型文件")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查Assets SenseVoice模型失败", e)
            null
        }
    }
    
    /**
     * 检查Assets中是否有SenseVoice模型
     */
    private fun hasModelInAssets(context: Context): Boolean {
        return try {
            val assets = context.assets.list(ASSETS_MODEL_PATH) ?: return false
            val assetsList = assets.toSet()
            
            // 至少需要一个模型文件和tokens文件
            val hasModel = assetsList.contains(MODEL_FILE) || assetsList.contains(MODEL_INT8_FILE)
            val hasTokens = assetsList.contains(TOKENS_FILE)
            
            hasModel && hasTokens
        } catch (e: Exception) {
            Log.w(TAG, "检查Assets中SenseVoice模型失败", e)
            false
        }
    }
    
    /**
     * 验证模型文件的完整性
     */
    private fun validateModelFiles(paths: SenseVoiceModelPaths): Boolean {
        return try {
            val modelFile = File(paths.modelPath)
            val tokensFile = File(paths.tokensPath)
            
            val modelValid = modelFile.exists() && modelFile.isFile && modelFile.length() > 0
            val tokensValid = tokensFile.exists() && tokensFile.isFile && tokensFile.length() > 0
            
            if (!modelValid) {
                Log.e(TAG, "SenseVoice模型文件无效: ${paths.modelPath}")
            }
            if (!tokensValid) {
                Log.e(TAG, "SenseVoice tokens文件无效: ${paths.tokensPath}")
            }
            
            modelValid && tokensValid
        } catch (e: Exception) {
            Log.e(TAG, "验证SenseVoice模型文件失败", e)
            false
        }
    }
    
    /**
     * 获取模型信息摘要
     */
    suspend fun getModelInfo(context: Context): String {
        return withContext(Dispatchers.IO) {
            try {
                val paths = getModelPaths(context)
                if (paths == null) {
                    "SenseVoice模型: 未找到"
                } else {
                    if (paths.isFromAssets) {
                        "SenseVoice模型: ${if (paths.isQuantized) "量化" else "普通"} (Assets)"
                    } else {
                        val modelFile = File(paths.modelPath)
                        val tokensFile = File(paths.tokensPath)
                        val modelSize = modelFile.length() / (1024 * 1024) // MB
                        val tokensLines = tokensFile.readLines().size
                        
                        "SenseVoice模型: ${if (paths.isQuantized) "量化" else "普通"} " +
                        "(${modelSize}MB, ${tokensLines}个tokens)"
                    }
                }
            } catch (e: Exception) {
                "SenseVoice模型: 获取信息失败"
            }
        }
    }
}

