package org.stypox.dicio.util

import android.content.Context
import org.stypox.dicio.BuildConfig

/**
 * 构建变体检测器
 * 用于检测当前构建变体并决定模型加载策略
 */
object ModelVariantDetector {
    
    /**
     * 检查是否应该使用 AssetManager 加载模型
     * withModels 变体使用 AssetManager，noModels 变体使用外部存储
     */
    fun shouldUseAssetManager(context: Context): Boolean {
        return try {
            // 通过 BuildConfig 检查是否有内置模型
            BuildConfig.HAS_MODELS_IN_ASSETS
        } catch (e: Exception) {
            // 如果没有 BuildConfig 字段，默认使用外部存储
            false
        }
    }
    
    /**
     * 获取当前构建变体名称
     */
    fun getVariantName(context: Context): String {
        return if (shouldUseAssetManager(context)) {
            "withModels"
        } else {
            "noModels"
        }
    }
    
    /**
     * 获取模型基础路径
     */
    fun getModelBasePath(context: Context): String {
        return if (shouldUseAssetManager(context)) {
            "models" // assets 目录下的相对路径
        } else {
            "/storage/emulated/0/Dicio/models" // 外部存储的绝对路径
        }
    }
    
    /**
     * 获取 SherpaOnnx KWS 模型路径
     */
    fun getSherpaKwsModelPath(context: Context): String {
        val basePath = getModelBasePath(context)
        return if (shouldUseAssetManager(context)) {
            "$basePath/sherpa_onnx_kws"
        } else {
            "$basePath/sherpa_onnx_kws"
        }
    }
    
    /**
     * 检查外部存储的 SherpaOnnx KWS 模型是否可用
     */
    fun checkExternalSherpaKwsModelsAvailable(): Boolean {
        val modelBasePath = "/storage/emulated/0/Dicio/models/sherpa_onnx_kws"
        val keyFiles = listOf(
            "encoder-epoch-12-avg-2-chunk-16-left-64.onnx",
            "decoder-epoch-12-avg-2-chunk-16-left-64.onnx", 
            "joiner-epoch-12-avg-2-chunk-16-left-64.onnx",
            "keywords.txt",
            "tokens.txt"
        )
        
        return try {
            keyFiles.all { fileName ->
                val file = java.io.File(modelBasePath, fileName)
                file.exists() && file.canRead()
            }
        } catch (e: Exception) {
            DebugLogger.logModelManagement("ModelVariantDetector", "检查外部 SherpaOnnx KWS 模型文件失败: ${e.message}")
            false
        }
    }
    
    /**
     * 获取模型信息
     */
    data class ModelInfo(
        val isExternal: Boolean,
        val basePath: String,
        val message: String
    )
    
    /**
     * 获取 SherpaOnnx KWS 模型信息
     */
    fun getSherpaKwsModelInfo(context: Context): ModelInfo {
        val useAssetManager = shouldUseAssetManager(context)
        val basePath = getSherpaKwsModelPath(context)
        
        return if (useAssetManager) {
            ModelInfo(
                isExternal = false,
                basePath = basePath,
                message = "使用内置 SherpaOnnx KWS 模型 (withModels 变体)"
            )
        } else {
            val externalAvailable = checkExternalSherpaKwsModelsAvailable()
            ModelInfo(
                isExternal = true,
                basePath = basePath,
                message = if (externalAvailable) {
                    "使用外部 SherpaOnnx KWS 模型 (noModels 变体)"
                } else {
                    "外部 SherpaOnnx KWS 模型不可用 (noModels 变体)"
                }
            )
        }
    }
}
