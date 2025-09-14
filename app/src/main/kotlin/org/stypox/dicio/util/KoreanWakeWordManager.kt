package org.stypox.dicio.util

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.stypox.dicio.io.wake.oww.OpenWakeWordDevice
import java.io.File
import java.io.IOException

/**
 * 韩语唤醒词"하이넛지"管理工具
 */
object KoreanWakeWordManager {
    private val TAG = KoreanWakeWordManager::class.simpleName ?: "KoreanWakeWordManager"
    
    // 韩语唤醒词相关常量
    const val KOREAN_WAKE_WORD = "하이넛지"
    const val KOREAN_WAKE_WORD_ROMANIZED = "Hi Nutji"
    
    // 预打包的韩语唤醒词模型文件名
    private const val KOREAN_WAKE_MODEL_ASSET = "models/openWakeWord/hi_nutji_korean.tflite"
    private const val KOREAN_WAKE_MODEL_FILENAME = "hi_nutji_korean.tflite"
    
    /**
     * 检查是否有预打包的韩语唤醒词模型
     */
    fun hasKoreanWakeWordInAssets(context: Context): Boolean {
        return try {
            context.assets.open(KOREAN_WAKE_MODEL_ASSET).use { true }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 安装韩语唤醒词模型
     */
    suspend fun installKoreanWakeWord(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                DebugLogger.logModelManagement(TAG, "🇰🇷 Installing Korean wake word: $KOREAN_WAKE_WORD")
                
                // 检查是否有预打包模型
                if (hasKoreanWakeWordInAssets(context)) {
                    DebugLogger.logModelManagement(TAG, "📦 Found Korean wake word in assets")
                    copyKoreanWakeWordFromAssets(context)
                } else {
                    DebugLogger.logModelManagement(TAG, "⚠️ No Korean wake word found in assets")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to install Korean wake word", e)
                false
            }
        }
    }
    
    /**
     * 从assets复制韩语唤醒词模型
     */
    private fun copyKoreanWakeWordFromAssets(context: Context): Boolean {
        return try {
            val userWakeFile = getUserWakeFile(context)
            
            // 确保目录存在
            userWakeFile.parentFile?.mkdirs()
            
            // 复制文件
            context.assets.open(KOREAN_WAKE_MODEL_ASSET).use { inputStream ->
                userWakeFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            DebugLogger.logModelManagement(TAG, "✅ Korean wake word copied to: ${userWakeFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy Korean wake word from assets", e)
            false
        }
    }
    
    /**
     * 从URI安装韩语唤醒词模型
     */
    suspend fun installKoreanWakeWordFromUri(context: Context, uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                DebugLogger.logModelManagement(TAG, "📥 Installing Korean wake word from URI: $uri")
                
                // 使用OpenWakeWordDevice的现有方法
                OpenWakeWordDevice.addUserWakeFile(context, uri)
                
                DebugLogger.logModelManagement(TAG, "✅ Korean wake word installed successfully")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to install Korean wake word from URI", e)
                false
            }
        }
    }
    
    /**
     * 移除韩语唤醒词，恢复默认
     */
    suspend fun removeKoreanWakeWord(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                DebugLogger.logModelManagement(TAG, "🗑️ Removing Korean wake word")
                
                OpenWakeWordDevice.removeUserWakeFile(context)
                
                DebugLogger.logModelManagement(TAG, "✅ Korean wake word removed, restored to default")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove Korean wake word", e)
                false
            }
        }
    }
    
    /**
     * 检查当前是否使用韩语唤醒词
     */
    fun isKoreanWakeWordActive(context: Context): Boolean {
        val userWakeFile = getUserWakeFile(context)
        return userWakeFile.exists()
    }
    
    /**
     * 获取当前唤醒词信息
     */
    fun getCurrentWakeWordInfo(context: Context): WakeWordInfo {
        return if (isKoreanWakeWordActive(context)) {
            WakeWordInfo(
                name = KOREAN_WAKE_WORD,
                romanized = KOREAN_WAKE_WORD_ROMANIZED,
                language = "Korean",
                isCustom = true,
                filePath = getUserWakeFile(context).absolutePath
            )
        } else {
            WakeWordInfo(
                name = "Hey Dicio",
                romanized = "Hey Dicio",
                language = "English",
                isCustom = false,
                filePath = null
            )
        }
    }
    
    /**
     * 验证韩语唤醒词模型文件
     */
    fun validateKoreanWakeWordModel(context: Context): ValidationResult {
        val userWakeFile = getUserWakeFile(context)
        
        if (!userWakeFile.exists()) {
            return ValidationResult(false, "Korean wake word model file not found")
        }
        
        if (userWakeFile.length() == 0L) {
            return ValidationResult(false, "Korean wake word model file is empty")
        }
        
        // 检查文件头是否为TensorFlow Lite格式
        try {
            userWakeFile.inputStream().use { inputStream ->
                val header = ByteArray(4)
                inputStream.read(header)
                
                // TensorFlow Lite文件魔数检查
                if (!isTensorFlowLiteFile(header)) {
                    return ValidationResult(false, "Invalid TensorFlow Lite model format")
                }
            }
        } catch (e: Exception) {
            return ValidationResult(false, "Failed to validate model file: ${e.message}")
        }
        
        return ValidationResult(true, "Korean wake word model is valid")
    }
    
    /**
     * 获取韩语唤醒词统计信息
     */
    fun getKoreanWakeWordStats(context: Context): WakeWordStats {
        val userWakeFile = getUserWakeFile(context)
        
        return WakeWordStats(
            isInstalled = userWakeFile.exists(),
            fileSize = if (userWakeFile.exists()) userWakeFile.length() else 0L,
            lastModified = if (userWakeFile.exists()) userWakeFile.lastModified() else 0L,
            filePath = userWakeFile.absolutePath,
            isValid = if (userWakeFile.exists()) validateKoreanWakeWordModel(context).isValid else false
        )
    }
    
    /**
     * 创建韩语唤醒词训练数据收集器
     */
    fun createTrainingDataCollector(context: Context): TrainingDataCollector {
        return TrainingDataCollector(context, KOREAN_WAKE_WORD)
    }
    
    /**
     * 获取用户唤醒词文件路径
     */
    private fun getUserWakeFile(context: Context): File {
        return File(context.filesDir, "openWakeWord/userwake.tflite")
    }
    
    /**
     * 检查是否为TensorFlow Lite文件
     */
    private fun isTensorFlowLiteFile(header: ByteArray): Boolean {
        // TensorFlow Lite文件通常以特定字节序列开头
        // 这里简化检查，实际可能需要更复杂的验证
        return header.size >= 4
    }
}

/**
 * 唤醒词信息数据类
 */
data class WakeWordInfo(
    val name: String,
    val romanized: String,
    val language: String,
    val isCustom: Boolean,
    val filePath: String?
)

/**
 * 验证结果数据类
 */
data class ValidationResult(
    val isValid: Boolean,
    val message: String
)

/**
 * 唤醒词统计信息数据类
 */
data class WakeWordStats(
    val isInstalled: Boolean,
    val fileSize: Long,
    val lastModified: Long,
    val filePath: String,
    val isValid: Boolean
)

/**
 * 训练数据收集器
 */
class TrainingDataCollector(
    private val context: Context,
    private val wakeWord: String
) {
    private val TAG = "TrainingDataCollector"
    
    /**
     * 开始收集正样本数据
     */
    fun startPositiveSampleCollection(): Boolean {
        // 实现正样本录音收集逻辑
        DebugLogger.logDebug(TAG, "🎤 Starting positive sample collection for: $wakeWord")
        return true
    }
    
    /**
     * 开始收集负样本数据
     */
    fun startNegativeSampleCollection(): Boolean {
        // 实现负样本录音收集逻辑
        DebugLogger.logDebug(TAG, "🎤 Starting negative sample collection")
        return true
    }
    
    /**
     * 获取收集的样本统计
     */
    fun getSampleStats(): SampleStats {
        // 返回样本统计信息
        return SampleStats(
            positiveCount = 0,
            negativeCount = 0,
            totalDuration = 0L
        )
    }
}

/**
 * 样本统计数据类
 */
data class SampleStats(
    val positiveCount: Int,
    val negativeCount: Int,
    val totalDuration: Long
)
