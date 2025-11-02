package com.ai.voice.io.speech

import android.content.Context
import android.util.Log
import com.ai.voice.util.ModelPathManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

/**
 * TTS模型管理器
 * 负责管理TTS模型的加载，支持assets和外部存储两种方式
 */
object TtsModelManager {
    private const val TAG = "TtsModelManager"
    
    // 使用 ModelPathManager 获取 TTS 模型路径
    private fun getExternalTtsModelsPathInternal(context: Context): String {
        return ModelPathManager.getExternalTtsModelsPath(context)
    }
    
    // Assets中的TTS模型路径
    private const val ASSETS_TTS_MODELS_PATH = "models/tts"
    
    // hyundaiit变体TTS模型路径（根目录）
    private const val HYUNDAIIT_TTS_KO_PATH = "tts-ko"
    private const val HYUNDAIIT_TTS_EN_PATH = "tts-en"
    
    /**
     * TTS模型配置数据类
     */
    data class TtsModelConfig(
        val modelDir: String,
        val modelName: String,
        val lexicon: String = "",
        val dataDir: String = "",
        val dictDir: String = "",
        val ruleFsts: String = "",
        val ruleFars: String = "",
        val useAssets: Boolean = false
    )
    
    /**
     * 检查hyundaiit/assets下的TTS模型是否可用
     */
    private fun hasHyundaiitTtsModelInAssets(context: Context, languageCode: String): Boolean {
        return try {
            val hyundaiitPath = when (languageCode) {
                "ko" -> HYUNDAIIT_TTS_KO_PATH
                "en" -> HYUNDAIIT_TTS_EN_PATH
                else -> null
            } ?: return false
            
            val modelFiles = context.assets.list(hyundaiitPath) ?: return false
            val hyundaiitModelName = when (languageCode) {
                "ko" -> "ko_KO-kss_low.onnx"
                "en" -> "en_US-amy-low.onnx"
                else -> null
            } ?: return false
            
            val hasModel = modelFiles.contains(hyundaiitModelName)
            Log.d(TAG, "检查hyundaiit/assets中的TTS模型 $languageCode: $hasModel")
            Log.d(TAG, "  路径: $hyundaiitPath")
            Log.d(TAG, "  模型文件: $hyundaiitModelName")
            
            hasModel
        } catch (e: Exception) {
            Log.w(TAG, "检查hyundaiit/assets TTS模型失败: ${e.message}")
            false
        }
    }
    
    /**
     * 检查TTS模型是否在assets中可用
     * 优先级: hyundaiit/assets > 传统assets路径
     */
    fun hasTtsModelInAssets(context: Context, languageCode: String): Boolean {
        return try {
            // 优先检查hyundaiit/assets
            if (hasHyundaiitTtsModelInAssets(context, languageCode)) {
                return true
            }
            
            // 检查传统路径
            val modelConfig = getModelConfigForLanguage(languageCode) ?: return false
            val assetPath = "$ASSETS_TTS_MODELS_PATH/${modelConfig.modelDir}"
            
            // 检查主要模型文件是否存在
            val modelFiles = context.assets.list(assetPath)
            val hasModel = modelFiles?.contains(modelConfig.modelName) == true
            
            Log.d(TAG, "检查assets中的TTS模型 $languageCode: $hasModel")
            Log.d(TAG, "  路径: $assetPath")
            Log.d(TAG, "  模型文件: ${modelConfig.modelName}")
            
            hasModel
        } catch (e: Exception) {
            Log.w(TAG, "检查assets TTS模型失败: ${e.message}")
            false
        }
    }
    
    /**
     * 检查外部存储中的TTS模型是否可用
     */
    fun hasTtsModelInExternalStorage(context: Context, languageCode: String): Boolean {
        return try {
            val modelConfig = getModelConfigForLanguage(languageCode) ?: return false
            val externalTtsPath = getExternalTtsModelsPathInternal(context)
            val externalPath = "$externalTtsPath/${modelConfig.modelDir}"
            val modelFile = File(externalPath, modelConfig.modelName)
            
            val exists = modelFile.exists() && modelFile.isFile
            Log.d(TAG, "检查外部存储TTS模型 $languageCode: $exists")
            Log.d(TAG, "  完整路径: ${modelFile.absolutePath}")
            Log.d(TAG, "  父目录存在: ${modelFile.parentFile?.exists()}")
            Log.d(TAG, "  父目录可读: ${modelFile.parentFile?.canRead()}")
            
            // 如果模型不存在，列出父目录内容
            if (!exists && modelFile.parentFile?.exists() == true) {
                val parentFiles = modelFile.parentFile?.listFiles()
                Log.d(TAG, "  父目录内容 (${parentFiles?.size ?: 0} 个文件):")
                parentFiles?.forEach { file ->
                    Log.d(TAG, "    - ${file.name} (${if (file.isDirectory) "目录" else "文件, ${file.length()}字节"})")
                }
            }
            
            exists
        } catch (e: Exception) {
            Log.w(TAG, "检查外部存储TTS模型失败: ${e.message}")
            false
        }
    }
    
    /**
     * 从assets复制TTS模型到外部存储
     */
    fun copyTtsModelFromAssets(context: Context, languageCode: String): Boolean {
        return try {
            val modelConfig = getModelConfigForLanguage(languageCode) ?: return false
            
            val assetPath = "$ASSETS_TTS_MODELS_PATH/${modelConfig.modelDir}"
            val externalPath = "${getExternalTtsModelsPathInternal(context)}/${modelConfig.modelDir}"
            val externalDir = File(externalPath)
            
            // 检查模型是否已存在
            if (externalDir.exists() && externalDir.listFiles()?.isNotEmpty() == true) {
                Log.d(TAG, "TTS模型 $languageCode 已存在，跳过复制: $externalPath")
                return true
            }
            
            Log.d(TAG, "开始复制TTS模型 $languageCode 从assets到外部存储")
            
            // 创建外部目录
            if (!externalDir.exists()) {
                externalDir.mkdirs()
            }
            
            // 复制所有模型文件
            copyAssetDirectory(context, assetPath, externalPath)
            
            Log.d(TAG, "TTS模型复制完成: $languageCode")
            true
        } catch (e: Exception) {
            Log.e(TAG, "复制TTS模型失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 获取TTS模型配置
     * 优先使用对应语言的模型，如果不存在则回退到英语
     */
    fun getTtsModelConfig(context: Context, locale: Locale): TtsModelConfig? {
        val languageCode = mapLocaleToLanguageCode(locale)
        Log.d(TAG, "🔍 查找TTS模型: 输入locale=$locale, 映射languageCode=$languageCode")
        
        // 首先尝试获取对应语言的模型
        var baseConfig = getModelConfigForLanguage(languageCode)
        var actualLanguageCode = languageCode
        
        if (baseConfig == null) {
            Log.w(TAG, "⚠️ 未找到 $languageCode 语言的TTS模型配置，回退到英语")
            baseConfig = getModelConfigForLanguage("en")
            actualLanguageCode = "en"
            
            if (baseConfig == null) {
                Log.e(TAG, "❌ 连英语TTS模型配置都未找到")
                return null
            }
        }
        
        Log.d(TAG, "📦 使用TTS模型语言: $actualLanguageCode")
        Log.d(TAG, "📦 基础配置: modelDir=${baseConfig.modelDir}, modelName=${baseConfig.modelName}")
        
        // 详细检查外部存储路径
        val externalTtsPath = getExternalTtsModelsPathInternal(context)
        Log.d(TAG, "📁 外部存储基础路径: $externalTtsPath")
        
        val externalBaseDir = File(externalTtsPath)
        Log.d(TAG, "📂 外部存储基础目录状态:")
        Log.d(TAG, "  - 存在: ${externalBaseDir.exists()}")
        Log.d(TAG, "  - 可读: ${externalBaseDir.canRead()}")
        Log.d(TAG, "  - 是目录: ${externalBaseDir.isDirectory}")
        
        if (externalBaseDir.exists()) {
            val subDirs = externalBaseDir.listFiles()
            Log.d(TAG, "  - 子目录列表 (${subDirs?.size ?: 0} 个):")
            subDirs?.forEach { subDir ->
                Log.d(TAG, "    * ${subDir.name} (${if (subDir.isDirectory) "目录" else "文件"})")
            }
        }
        
        // 优先检查hyundaiit/assets路径
        if (hasHyundaiitTtsModelInAssets(context, actualLanguageCode)) {
            val hyundaiitPath = when (actualLanguageCode) {
                "ko" -> HYUNDAIIT_TTS_KO_PATH
                "en" -> HYUNDAIIT_TTS_EN_PATH
                else -> null
            }
            
            if (hyundaiitPath != null) {
                val hyundaiitModelName = when (actualLanguageCode) {
                    "ko" -> "ko_KO-kss_low.onnx"
                    "en" -> "en_US-amy-low.onnx"
                    else -> baseConfig.modelName
                }
                
                Log.d(TAG, "✅ 使用hyundaiit/assets TTS模型: $actualLanguageCode")
                return TtsModelConfig(
                    modelDir = hyundaiitPath,
                    modelName = hyundaiitModelName,
                    lexicon = baseConfig.lexicon,
                    dataDir = if (baseConfig.dataDir.isNotEmpty()) 
                        "$hyundaiitPath/${baseConfig.dataDir}" 
                        else "",
                    dictDir = if (baseConfig.dictDir.isNotEmpty()) 
                        "$hyundaiitPath/${baseConfig.dictDir}" 
                        else "",
                    useAssets = true
                )
            }
        }
        
        // 检查传统assets路径（withModels变体）
        if (hasTtsModelInAssets(context, actualLanguageCode)) {
            Log.d(TAG, "✅ 使用传统assets TTS模型: $actualLanguageCode")
            return baseConfig.copy(
                modelDir = "$ASSETS_TTS_MODELS_PATH/${baseConfig.modelDir}",
                dataDir = if (baseConfig.dataDir.isNotEmpty()) 
                    "$ASSETS_TTS_MODELS_PATH/${baseConfig.modelDir}/${baseConfig.dataDir}" 
                    else "",
                dictDir = if (baseConfig.dictDir.isNotEmpty()) 
                    "$ASSETS_TTS_MODELS_PATH/${baseConfig.modelDir}/${baseConfig.dictDir}" 
                    else "",
                useAssets = true
            )
        }
        
        // 检查外部存储是否有模型（noModels变体）
        if (hasTtsModelInExternalStorage(context, actualLanguageCode)) {
            Log.d(TAG, "✅ 使用外部存储TTS模型: $actualLanguageCode")
            return baseConfig.copy(
                modelDir = "$externalTtsPath/${baseConfig.modelDir}",
                dataDir = if (baseConfig.dataDir.isNotEmpty()) 
                    "$externalTtsPath/${baseConfig.modelDir}/${baseConfig.dataDir}" 
                    else "",
                dictDir = if (baseConfig.dictDir.isNotEmpty()) 
                    "$externalTtsPath/${baseConfig.modelDir}/${baseConfig.dictDir}" 
                    else "",
                useAssets = false
            )
        }
        
        Log.e(TAG, "❌ 未找到任何可用的TTS模型: $actualLanguageCode")
        return null
    }
    
    /**
     * 将Locale映射为语言代码
     */
    private fun mapLocaleToLanguageCode(locale: Locale): String {
        return when (locale.language) {
            "zh" -> "zh"
            "ko" -> "ko" 
            "en" -> "en"
            else -> locale.language
        }
    }
    
    /**
     * 根据语言获取对应的TTS模型配置
     */
    private fun getModelConfigForLanguage(languageCode: String): TtsModelConfig? {
        return when (languageCode) {
            "zh" -> TtsModelConfig(
                modelDir = "vits-zh-hf-fanchen-C",
                modelName = "vits-zh-hf-fanchen-C.onnx",
                lexicon = "lexicon.txt",
                dictDir = "dict"
            )
            "ko" -> TtsModelConfig(
                modelDir = "vits-mimic3-ko_KO-kss_low",
                modelName = "ko_KO-kss_low.onnx",
                lexicon = "tokens.txt",
                dataDir = "espeak-ng-data"
            )
            "en" -> TtsModelConfig(
                modelDir = "vits-piper-en_US-amy-low",
                modelName = "en_US-amy-low.onnx",
                lexicon = "tokens.txt",
                dataDir = "espeak-ng-data"
            )
            else -> null
        }
    }
    
    /**
     * 递归复制assets目录到外部存储
     */
    private fun copyAssetDirectory(context: Context, assetPath: String, externalPath: String) {
        try {
            val assets = context.assets.list(assetPath) ?: return
            
            val externalDir = File(externalPath)
            if (!externalDir.exists()) {
                externalDir.mkdirs()
            }
            
            for (filename in assets) {
                val assetFilePath = "$assetPath/$filename"
                val externalFilePath = "$externalPath/$filename"
                
                try {
                    // 尝试作为目录处理
                    val subAssets = context.assets.list(assetFilePath)
                    if (subAssets != null && subAssets.isNotEmpty()) {
                        // 这是一个目录，递归复制
                        copyAssetDirectory(context, assetFilePath, externalFilePath)
                    } else {
                        // 这是一个文件，直接复制
                        copyAssetFile(context, assetFilePath, externalFilePath)
                    }
                } catch (e: Exception) {
                    // 如果作为目录失败，尝试作为文件复制
                    copyAssetFile(context, assetFilePath, externalFilePath)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "复制assets目录失败: $assetPath -> $externalPath", e)
            throw e
        }
    }
    
    /**
     * 复制单个assets文件到外部存储
     */
    private fun copyAssetFile(context: Context, assetPath: String, externalPath: String) {
        try {
            context.assets.open(assetPath).use { inputStream ->
                File(externalPath).outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Log.d(TAG, "复制文件: $assetPath -> $externalPath")
        } catch (e: Exception) {
            Log.e(TAG, "复制文件失败: $assetPath -> $externalPath", e)
            throw e
        }
    }
    
    /**
     * 获取外部存储TTS模型目录
     */
    fun getExternalTtsModelsPath(context: Context): String = getExternalTtsModelsPathInternal(context)
    
    /**
     * 清理外部存储中的TTS模型
     */
    fun cleanExternalTtsModels(context: Context): Boolean {
        return try {
            val externalTtsPath = getExternalTtsModelsPathInternal(context)
            val modelsDir = File(externalTtsPath)
            if (modelsDir.exists()) {
                modelsDir.deleteRecursively()
                Log.d(TAG, "清理外部TTS模型完成")
                true
            } else {
                Log.d(TAG, "外部TTS模型目录不存在")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理外部TTS模型失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 获取TTS模型信息
     */
    fun getTtsModelInfo(context: Context): Map<String, ModelInfo> {
        val supportedLanguages = listOf("zh", "ko", "en")
        val modelInfo = mutableMapOf<String, ModelInfo>()
        
        for (lang in supportedLanguages) {
            val hasAssets = hasTtsModelInAssets(context, lang)
            val hasExternal = hasTtsModelInExternalStorage(context, lang)
            val config = getModelConfigForLanguage(lang)
            
            modelInfo[lang] = ModelInfo(
                language = lang,
                hasAssetsModel = hasAssets,
                hasExternalModel = hasExternal,
                modelName = config?.modelName ?: "未知",
                modelDir = config?.modelDir ?: "未知"
            )
        }
        
        return modelInfo
    }
    
    /**
     * 模型信息数据类
     */
    data class ModelInfo(
        val language: String,
        val hasAssetsModel: Boolean,
        val hasExternalModel: Boolean,
        val modelName: String,
        val modelDir: String
    )
}
