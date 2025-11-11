package com.ai.voice.util

import android.content.Context

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
        // 渠道已移除：统一视为包含内置模型
        return true
    }
    
    /**
     * 获取当前构建变体名称
     */
    fun getVariantName(context: Context): String {
        return "withModels"
    }
    
    /**
     * 获取模型基础路径
     */
    fun getModelBasePath(context: Context): String {
        return "models" // assets 目录下的相对路径
    }
    
    /**
     * 获取 SherpaOnnx KWS 模型路径
     */
    fun getSherpaKwsModelPath(context: Context): String {
        val basePath = getModelBasePath(context)
        return "$basePath/sherpa_onnx_kws"
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
        val basePath = getSherpaKwsModelPath(context)
        return ModelInfo(
            isExternal = false,
            basePath = basePath,
            message = "使用内置 SherpaOnnx KWS 模型 (withModels)"
        )
    }
}
