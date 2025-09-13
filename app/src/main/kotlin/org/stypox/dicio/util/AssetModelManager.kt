package org.stypox.dicio.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 管理从assets目录复制预打包的模型文件到应用数据目录
 */
object AssetModelManager {
    private const val TAG = "AssetModelManager"
    
    /**
     * 复制OpenWakeWord模型文件从assets到应用数据目录
     */
    suspend fun copyOpenWakeWordModels(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val owwFolder = File(context.filesDir, "openWakeWord")
                owwFolder.mkdirs()
                
                val modelFiles = listOf(
                    "melspectrogram.tflite",
                    "embedding.tflite", 
                    "wake.tflite"
                )
                
                for (fileName in modelFiles) {
                    val targetFile = File(owwFolder, fileName)
                    if (!targetFile.exists()) {
                        copyAssetFile(context, "models/openWakeWord/$fileName", targetFile)
                        Log.d(TAG, "Copied OpenWakeWord model: $fileName")
                    }
                }
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy OpenWakeWord models", e)
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
}
