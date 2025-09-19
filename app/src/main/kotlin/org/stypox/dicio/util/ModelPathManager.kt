package org.stypox.dicio.util

import android.content.Context
import android.util.Log

/**
 * 模型路径管理器
 * 统一管理所有模型文件的存储路径，支持Android分区存储兼容性
 * 
 * 职责：
 * - 提供统一的外部存储路径获取方法
 * - 兼容Android 10+分区存储限制
 * - 集中管理所有模型类型的路径配置
 */
object ModelPathManager {
    private const val TAG = "ModelPathManager"
    
    // 基础路径配置
    private const val MODELS_BASE_DIR = "models"
    private const val LEGACY_BASE_PATH = "/storage/emulated/0/Dicio/models"
    
    // 各类型模型的子目录
    private const val VOSK_SUBDIR = "vosk"
    private const val TTS_SUBDIR = "tts"
    private const val SENSEVOICE_SUBDIR = "asr/sensevoice"
    private const val KWS_SUBDIR = "sherpa_onnx_kws"
    private const val OPENWAKEWORD_SUBDIR = "openWakeWord"
    
    /**
     * 获取应用外部模型存储基础路径
     * 优先使用应用外部文件目录（Android兼容），回退到传统路径
     */
    private fun getExternalModelsBasePath(context: Context): String {
        // 优先使用应用外部文件目录（Android兼容，无需特殊权限）
        val appExternalDir = context.getExternalFilesDir(MODELS_BASE_DIR)
        if (appExternalDir != null) {
            Log.d(TAG, "使用应用外部文件目录: ${appExternalDir.absolutePath}")
            return appExternalDir.absolutePath
        }
        
        // 备用：传统路径（需要MANAGE_EXTERNAL_STORAGE权限）
        Log.w(TAG, "回退到传统外部存储路径: $LEGACY_BASE_PATH")
        return LEGACY_BASE_PATH
    }
    
    /**
     * 获取Vosk模型外部存储路径
     */
    fun getExternalVoskModelsPath(context: Context): String {
        return "${getExternalModelsBasePath(context)}/$VOSK_SUBDIR"
    }
    
    /**
     * 获取TTS模型外部存储路径
     */
    fun getExternalTtsModelsPath(context: Context): String {
        return "${getExternalModelsBasePath(context)}/$TTS_SUBDIR"
    }
    
    /**
     * 获取SenseVoice模型外部存储路径
     */
    fun getExternalSenseVoiceModelsPath(context: Context): String {
        return "${getExternalModelsBasePath(context)}/$SENSEVOICE_SUBDIR"
    }
    
    /**
     * 获取SherpaOnnx KWS模型外部存储路径
     */
    fun getExternalKwsModelsPath(context: Context): String {
        return "${getExternalModelsBasePath(context)}/$KWS_SUBDIR"
    }
    
    /**
     * 获取OpenWakeWord模型外部存储路径
     */
    fun getExternalOpenWakeWordModelsPath(context: Context): String {
        return "${getExternalModelsBasePath(context)}/$OPENWAKEWORD_SUBDIR"
    }
    
    /**
     * 获取所有模型类型的外部存储路径
     */
    fun getAllExternalModelsPaths(context: Context): Map<String, String> {
        return mapOf(
            "vosk" to getExternalVoskModelsPath(context),
            "tts" to getExternalTtsModelsPath(context),
            "sensevoice" to getExternalSenseVoiceModelsPath(context),
            "kws" to getExternalKwsModelsPath(context),
            "openwakeword" to getExternalOpenWakeWordModelsPath(context)
        )
    }
    
    /**
     * 检查外部存储路径是否可用
     */
    fun isExternalStorageAvailable(context: Context): Boolean {
        return try {
            val basePath = getExternalModelsBasePath(context)
            val baseDir = java.io.File(basePath)
            
            // 尝试创建目录
            if (!baseDir.exists()) {
                baseDir.mkdirs()
            }
            
            // 检查是否可写
            baseDir.canWrite()
        } catch (e: Exception) {
            Log.e(TAG, "检查外部存储可用性失败", e)
            false
        }
    }
    
    /**
     * 创建所有模型目录
     */
    fun createModelDirectories(context: Context): Boolean {
        return try {
            val paths = getAllExternalModelsPaths(context)
            var allCreated = true
            
            for ((type, path) in paths) {
                val dir = java.io.File(path)
                if (!dir.exists()) {
                    val created = dir.mkdirs()
                    if (created) {
                        Log.d(TAG, "创建模型目录: $type -> $path")
                    } else {
                        Log.e(TAG, "创建模型目录失败: $type -> $path")
                        allCreated = false
                    }
                } else {
                    Log.d(TAG, "模型目录已存在: $type -> $path")
                }
            }
            
            allCreated
        } catch (e: Exception) {
            Log.e(TAG, "创建模型目录失败", e)
            false
        }
    }
    
    /**
     * 获取路径状态信息（用于调试）
     */
    fun getPathStatus(context: Context): String {
        val basePath = getExternalModelsBasePath(context)
        val isAvailable = isExternalStorageAvailable(context)
        val paths = getAllExternalModelsPaths(context)
        
        val status = StringBuilder()
        status.append("ModelPathManager状态:\n")
        status.append("  基础路径: $basePath\n")
        status.append("  存储可用: $isAvailable\n")
        status.append("  模型路径:\n")
        
        for ((type, path) in paths) {
            val dir = java.io.File(path)
            val exists = dir.exists()
            val canRead = if (exists) dir.canRead() else false
            val canWrite = if (exists) dir.canWrite() else false
            
            status.append("    $type: $path\n")
            status.append("      存在: $exists, 可读: $canRead, 可写: $canWrite\n")
        }
        
        return status.toString()
    }
    
    // 向后兼容的传统路径方法（已废弃，建议使用上面的新方法）
    @Deprecated("使用 getExternalVoskModelsPath(context) 替代")
    fun getLegacyVoskPath(): String = "$LEGACY_BASE_PATH/$VOSK_SUBDIR"
    
    @Deprecated("使用 getExternalTtsModelsPath(context) 替代")
    fun getLegacyTtsPath(): String = "$LEGACY_BASE_PATH/$TTS_SUBDIR"
    
    @Deprecated("使用 getExternalKwsModelsPath(context) 替代")  
    fun getLegacyKwsPath(): String = "$LEGACY_BASE_PATH/$KWS_SUBDIR"
}
