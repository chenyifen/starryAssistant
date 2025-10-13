package org.stypox.dicio.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 管理从assets目录和外部存储复制预打包的模型文件到应用数据目录
 */
object AssetModelManager {
    private const val TAG = "AssetModelManager"
    
    // 外部存储中的Vosk模型路径
    // 外部存储中的Vosk模型路径（使用统一路径管理器）
    
    // 语言代码到模型名称的映射（从VoskInputDevice.MODEL_URLS提取）
    private val LANGUAGE_TO_MODEL_NAME = mapOf(
        "en" to "vosk-model-small-en-us-0.15",
        "en-in" to "vosk-model-small-en-in-0.4",
        "cn" to "vosk-model-small-cn-0.22",
        "ru" to "vosk-model-small-ru-0.22",
        "fr" to "vosk-model-small-fr-0.22",
        "de" to "vosk-model-small-de-0.15",
        "es" to "vosk-model-small-es-0.42",
        "pt" to "vosk-model-small-pt-0.3",
        "tr" to "vosk-model-small-tr-0.3",
        "vn" to "vosk-model-small-vn-0.4",
        "it" to "vosk-model-small-it-0.22",
        "nl" to "vosk-model-small-nl-0.22",
        "ca" to "vosk-model-small-ca-0.4",
        "ar" to "vosk-model-ar-mgb2-0.4",
        "ar-tn" to "vosk-model-small-ar-tn-0.1-linto",
        "fa" to "vosk-model-small-fa-0.42",
        "ph" to "vosk-model-tl-ph-generic-0.6",
        "uk" to "vosk-model-small-uk-v3-nano",
        "kz" to "vosk-model-small-kz-0.15",
        "sv" to "vosk-model-small-sv-rhasspy-0.15",
        "ja" to "vosk-model-small-ja-0.22",
        "eo" to "vosk-model-small-eo-0.42",
        "hi" to "vosk-model-small-hi-0.22",
        "cs" to "vosk-model-small-cs-0.4-rhasspy",
        "pl" to "vosk-model-small-pl-0.22",
        "uz" to "vosk-model-small-uz-0.22",
        "ko" to "vosk-model-small-ko-0.22",
        "br" to "vosk-model-br-0.8",
        "gu" to "vosk-model-small-gu-0.42",
        "tg" to "vosk-model-small-tg-0.22",
        "te" to "vosk-model-small-te-0.42"
    )
    
    /**
     * 复制OpenWakeWord模型文件从assets到应用数据目录
     */
    suspend fun copyOpenWakeWordModels(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔄 Starting copyOpenWakeWordModels...")
                val owwFolder = File(context.filesDir, "openWakeWord")
                Log.d(TAG, "📁 Target folder: ${owwFolder.absolutePath}")
                
                val mkdirResult = owwFolder.mkdirs()
                Log.d(TAG, "📁 mkdirs result: $mkdirResult, exists: ${owwFolder.exists()}, isDirectory: ${owwFolder.isDirectory}")
                
                val modelFiles = listOf(
                    "melspectrogram.tflite",
                    "embedding.tflite", 
                    "wake.tflite"
                )
                
                Log.d(TAG, "📄 Will copy ${modelFiles.size} files")
                
                for (fileName in modelFiles) {
                    val targetFile = File(owwFolder, fileName)
                    Log.d(TAG, "📄 Processing $fileName, exists: ${targetFile.exists()}, size: ${if (targetFile.exists()) targetFile.length() else 0}")
                    
                    if (!targetFile.exists()) {
                        Log.d(TAG, "📥 Copying $fileName from assets...")
                        copyAssetFile(context, "models/openWakeWord/$fileName", targetFile)
                        Log.d(TAG, "✅ Copied OpenWakeWord model: $fileName (${targetFile.length()} bytes)")
                    } else {
                        Log.d(TAG, "⏭️  Skipping $fileName, already exists")
                    }
                }
                
                Log.d(TAG, "✅ Successfully copied all OpenWakeWord models")
                true
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to copy OpenWakeWord models", e)
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * 复制Vosk模型文件从assets到应用数据目录
     */
    suspend fun copyVoskModel(context: Context, language: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val modelDirectory = File(context.filesDir, "vosk-model")
                
                // 如果模型目录已存在且包含正确的语言模型，跳过复制
                val modelExistFileCheck = File(modelDirectory, "ivector")
                val languageMarkerFile = File(context.filesDir, "vosk-model-language")
                
                if (modelExistFileCheck.exists() && languageMarkerFile.exists()) {
                    val currentLanguage = try {
                        languageMarkerFile.readText().trim()
                    } catch (e: Exception) {
                        ""
                    }
                    
                    if (currentLanguage == language) {
                        Log.d(TAG, "Vosk model for $language already exists")
                        return@withContext true
                    }
                }
                
                // 删除旧模型目录
                modelDirectory.deleteRecursively()
                modelDirectory.mkdirs()
                
                // 复制新的语言模型
                copyAssetDirectory(context, "models/vosk/$language", modelDirectory)
                
                // 记录当前语言
                languageMarkerFile.writeText(language)
                
                Log.d(TAG, "Copied Vosk model for language: $language")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy Vosk model for $language", e)
                false
            }
        }
    }
    
    /**
     * 检查assets中是否存在指定语言的Vosk模型
     */
    fun hasVoskModelInAssets(context: Context, language: String): Boolean {
        return try {
            val assets = context.assets.list("models/vosk") ?: return false
            assets.contains(language)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check Vosk model in assets for $language", e)
            false
        }
    }
    
    /**
     * 检查外部存储中是否存在指定语言的Vosk模型
     * 支持多种目录名称格式：语言代码(cn)或完整模型名称(vosk-model-small-cn-0.22)
     */
    fun hasVoskModelInExternalStorage(context: Context, language: String): Boolean {
        return try {
            val possiblePaths = getPossibleExternalModelPaths(context, language)
            
            for (path in possiblePaths) {
                val modelDir = File(path)
                val modelExistCheck = File(modelDir, "ivector")
                if (modelDir.exists() && modelDir.isDirectory && modelExistCheck.exists()) {
                    Log.d(TAG, "External Vosk model found for $language at: $path")
                    return true
                }
            }
            
            Log.d(TAG, "External Vosk model not found for $language. Checked paths: $possiblePaths")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check external Vosk model for $language", e)
            false
        }
    }
    
    /**
     * 获取指定语言可能的外部存储模型路径
     */
    private fun getPossibleExternalModelPaths(context: Context, language: String): List<String> {
        val paths = mutableListOf<String>()
        
        // 获取所有可能的外部存储基础路径
        val possibleBasePaths = listOf(
            // 1. 应用私有外部文件目录（Android兼容）
            context.getExternalFilesDir("models")?.absolutePath?.let { "$it/vosk" },
            // 2. 传统外部存储路径（用户可见）
            "/storage/emulated/0/Dicio/models/vosk"
        ).filterNotNull()
        
        Log.d(TAG, "检查外部存储基础路径 for $language: $possibleBasePaths")
        
        for (basePath in possibleBasePaths) {
            // 1. 直接使用语言代码作为目录名
            paths.add("$basePath/$language")
            
            // 2. 使用完整的模型名称作为目录名
            LANGUAGE_TO_MODEL_NAME[language]?.let { modelName ->
                paths.add("$basePath/$modelName")
            }
            
            // 3. 扫描外部存储目录，查找包含语言代码的目录
            try {
                val voskDir = File(basePath)
                if (voskDir.exists() && voskDir.isDirectory) {
                    voskDir.listFiles()?.forEach { subDir ->
                        if (subDir.isDirectory && subDir.name.contains(language, ignoreCase = true)) {
                            paths.add(subDir.absolutePath)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to scan external Vosk directory: $basePath", e)
            }
        }
        
        val distinctPaths = paths.distinct()
        Log.d(TAG, "最终检查路径列表 for $language: $distinctPaths")
        return distinctPaths
    }
    
    /**
     * 查找指定语言的实际外部存储模型目录
     */
    private fun findExternalModelDirectory(context: Context, language: String): File? {
        val possiblePaths = getPossibleExternalModelPaths(context, language)
        
        for (path in possiblePaths) {
            val modelDir = File(path)
            val modelExistCheck = File(modelDir, "ivector")
            if (modelDir.exists() && modelDir.isDirectory && modelExistCheck.exists()) {
                return modelDir
            }
        }
        
        return null
    }
    
    /**
     * 从外部存储复制Vosk模型到应用数据目录
     */
    suspend fun copyVoskModelFromExternalStorage(context: Context, language: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val externalModelDir = findExternalModelDirectory(context, language)
                if (externalModelDir == null) {
                    Log.w(TAG, "External Vosk model not found for language: $language")
                    return@withContext false
                }
                
                val internalModelDir = File(context.filesDir, "vosk-model")
                
                // 检查是否已经复制了相同的模型
                val languageMarkerFile = File(context.filesDir, "vosk-model-language")
                val modelExistFileCheck = File(internalModelDir, "ivector")
                
                if (modelExistFileCheck.exists() && languageMarkerFile.exists()) {
                    val currentLanguage = try {
                        languageMarkerFile.readText().trim()
                    } catch (e: Exception) {
                        ""
                    }
                    
                    if (currentLanguage == language) {
                        Log.d(TAG, "Vosk model for $language already copied from external storage")
                        return@withContext true
                    }
                }
                
                // 删除旧模型目录
                internalModelDir.deleteRecursively()
                internalModelDir.mkdirs()
                
                // 复制外部存储的模型到内部存储
                Log.d(TAG, "Copying Vosk model from ${externalModelDir.absolutePath} to ${internalModelDir.absolutePath}")
                copyDirectory(externalModelDir, internalModelDir)
                
                // 记录当前语言
                languageMarkerFile.writeText(language)
                
                Log.d(TAG, "Successfully copied Vosk model for $language from external storage (${externalModelDir.name})")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy Vosk model from external storage for $language", e)
                false
            }
        }
    }
    
    /**
     * 检查assets中是否存在OpenWakeWord模型
     */
    fun hasOpenWakeWordModelsInAssets(context: Context): Boolean {
        return try {
            val assets = context.assets.list("models/openWakeWord") ?: return false
            val requiredFiles = setOf("melspectrogram.tflite", "embedding.tflite", "wake.tflite")
            assets.toSet().containsAll(requiredFiles)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check OpenWakeWord models in assets", e)
            false
        }
    }
    
    /**
     * 复制单个文件从assets到目标位置
     */
    private fun copyAssetFile(context: Context, assetPath: String, targetFile: File) {
        context.assets.open(assetPath).use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
    
    /**
     * 递归复制assets目录到目标位置
     */
    private fun copyAssetDirectory(context: Context, assetPath: String, targetDir: File) {
        val assets = context.assets.list(assetPath) ?: return
        
        for (asset in assets) {
            val assetFullPath = "$assetPath/$asset"
            val targetFile = File(targetDir, asset)
            
            val subAssets = context.assets.list(assetFullPath)
            if (subAssets != null && subAssets.isNotEmpty()) {
                // 这是一个目录
                targetFile.mkdirs()
                copyAssetDirectory(context, assetFullPath, targetFile)
            } else {
                // 这是一个文件
                copyAssetFile(context, assetFullPath, targetFile)
            }
        }
    }
    
    /**
     * 递归复制文件系统目录到目标位置
     */
    private fun copyDirectory(sourceDir: File, targetDir: File) {
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            throw IOException("Source directory does not exist or is not a directory: ${sourceDir.absolutePath}")
        }
        
        targetDir.mkdirs()
        
        sourceDir.listFiles()?.forEach { sourceFile ->
            val targetFile = File(targetDir, sourceFile.name)
            
            if (sourceFile.isDirectory) {
                copyDirectory(sourceFile, targetFile)
            } else {
                sourceFile.copyTo(targetFile, overwrite = true)
            }
        }
    }
}
