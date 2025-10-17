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
    
    // 多个候选路径（按优先级排序）
    private val CANDIDATE_BASE_PATHS = listOf(
        "/sdcard/Android/data/org.stypox.dicio.master/files/models", // 2. 应用专用外部存储
        "/storage/emulated/0/Assistant/models",        // 3. 传统路径（兼容性）
    )
    
    // 各类型模型的子目录
    private const val VOSK_SUBDIR = "vosk"
    private const val TTS_SUBDIR = "tts"
    private const val SENSEVOICE_SUBDIR = "asr/sensevoice"
    private const val KWS_SUBDIR = "sherpa_onnx_kws"
    private const val OPENWAKEWORD_SUBDIR = "openWakeWord"
    private const val VAD_SUBDIR = "vad"
    
    /**
     * 智能选择最佳的外部模型存储基础路径
     * 统一处理所有候选路径，优先使用已有模型文件的路径
     */
    private fun getExternalModelsBasePath(context: Context): String {
        // 构建完整的候选路径列表（包括应用专用外部存储）
        val allCandidatePaths = mutableListOf<String>()
        
        // 添加静态候选路径
        allCandidatePaths.addAll(CANDIDATE_BASE_PATHS)
        
        // 添加应用专用外部存储路径
        val appExternalDir = context.getExternalFilesDir(MODELS_BASE_DIR)
        if (appExternalDir != null) {
            allCandidatePaths.add(appExternalDir.absolutePath)
        }
        
        // 第一轮：优先选择已有模型文件的路径
        for (candidatePath in allCandidatePaths) {
            if (testPathAccessibility(candidatePath) && hasExistingModels(candidatePath)) {
                Log.d(TAG, "✅ 选择已有模型路径: $candidatePath")
                return candidatePath
            }
        }
        
        // 第二轮：选择第一个可用的路径（用于创建新模型目录）
        for (candidatePath in allCandidatePaths) {
            if (testPathAccessibility(candidatePath)) {
                Log.d(TAG, "✅ 选择可用路径: $candidatePath")
                return candidatePath
            } else {
                Log.d(TAG, "❌ 路径不可用: $candidatePath")
            }
        }
        
        // 如果所有路径都不可用，返回第一个候选路径（让应用尝试创建）
        val fallbackPath = CANDIDATE_BASE_PATHS.first()
        Log.w(TAG, "⚠️ 所有路径都不可用，回退到: $fallbackPath")
        return fallbackPath
    }
    
    /**
     * 检查路径中是否已有模型文件
     */
    private fun hasExistingModels(basePath: String): Boolean {
        return try {
            val baseDir = java.io.File(basePath)
            if (!baseDir.exists() || !baseDir.isDirectory) {
                return false
            }
            
            // 检查各种模型子目录是否存在且有文件
            val modelSubdirs = listOf(
                SENSEVOICE_SUBDIR,
                KWS_SUBDIR,
                TTS_SUBDIR,
                VOSK_SUBDIR,
                OPENWAKEWORD_SUBDIR,
                VAD_SUBDIR
            )
            
            for (subdir in modelSubdirs) {
                val modelDir = java.io.File(baseDir, subdir)
                if (modelDir.exists() && modelDir.isDirectory) {
                    val files = modelDir.listFiles()
                    if (files != null && files.isNotEmpty()) {
                        // 过滤掉隐藏文件和系统文件
                        val modelFiles = files.filter { file ->
                            !file.name.startsWith(".") && 
                            file.isFile && 
                            file.length() > 0 &&
                            (file.name.endsWith(".onnx") || 
                             file.name.endsWith(".txt") || 
                             file.name.endsWith(".json") ||
                             file.name.endsWith(".bin"))
                        }
                        if (modelFiles.isNotEmpty()) {
                            Log.d(TAG, "✅ 发现已有模型文件: $basePath/$subdir (${modelFiles.size}个文件)")
                            return true
                        }
                    }
                }
            }
            
            Log.d(TAG, "❌ 路径中无模型文件: $basePath")
            false
        } catch (e: Exception) {
            Log.w(TAG, "检查已有模型失败 $basePath: ${e.message}")
            false
        }
    }

    /**
     * 测试路径的可访问性
     */
    private fun testPathAccessibility(path: String): Boolean {
        return try {
            val dir = java.io.File(path)
            val exists = dir.exists()
            val isDirectory = dir.isDirectory
            
            Log.d(TAG, "路径信息 $path: exists=$exists, isDirectory=$isDirectory")
            
            // 如果目录存在，就认为可用（简化权限检查）
            if (exists && isDirectory) {
                Log.d(TAG, "✅ 路径可用 $path: 目录存在")
                return true
            }
            
            // 如果目录不存在，尝试创建
            if (!exists) {
                val created = dir.mkdirs()
                if (created) {
                    Log.d(TAG, "✅ 路径可用 $path: 成功创建目录")
                    return true
                } else {
                    Log.d(TAG, "❌ 路径不可用 $path: 无法创建目录")
                    return false
                }
            }
            
            // 路径存在但不是目录
            Log.d(TAG, "❌ 路径不可用 $path: 存在但不是目录")
            false
        } catch (e: Exception) {
            Log.w(TAG, "测试路径 $path 失败: ${e.message}")
            false
        }
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
     * 获取VAD模型外部存储路径
     */
    fun getExternalVadModelsPath(context: Context): String {
        return "${getExternalModelsBasePath(context)}/$VAD_SUBDIR"
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
            "openwakeword" to getExternalOpenWakeWordModelsPath(context),
            "vad" to getExternalVadModelsPath(context)
        )
    }
    
    /**
     * 检查外部存储路径是否可用（只检查存在性）
     */
    fun isExternalStorageAvailable(context: Context): Boolean {
        return try {
            val basePath = getExternalModelsBasePath(context)
            val baseDir = java.io.File(basePath)
            
            // 尝试创建目录
            if (!baseDir.exists()) {
                baseDir.mkdirs()
            }
            
            // 只检查目录是否存在（移除可写检查，简化权限处理）
            baseDir.exists() && baseDir.isDirectory
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
     * 获取所有候选路径的状态信息（用于调试和故障排除）
     */
    fun getAllPathsStatus(context: Context): String {
        val status = StringBuilder()
        status.append("📊 ModelPathManager 路径状态报告:\n")
        status.append("=".repeat(50) + "\n")
        
        // 应用外部文件目录
        val appExternalDir = context.getExternalFilesDir(MODELS_BASE_DIR)
        if (appExternalDir != null) {
            val accessible = testPathAccessibility(appExternalDir.absolutePath)
            status.append("🏠 应用外部文件目录: ${if (accessible) "✅" else "❌"}\n")
            status.append("   路径: ${appExternalDir.absolutePath}\n")
        } else {
            status.append("🏠 应用外部文件目录: ❌ 不可用\n")
        }
        
        // 候选路径
        status.append("\n📁 候选外部存储路径:\n")
        CANDIDATE_BASE_PATHS.forEachIndexed { index, path ->
            val accessible = testPathAccessibility(path)
            status.append("   ${index + 1}. ${if (accessible) "✅" else "❌"} $path\n")
        }
        
        // 当前选择的路径
        val selectedPath = getExternalModelsBasePath(context)
        status.append("\n🎯 当前选择路径: $selectedPath\n")
        
        // 各模型类型的具体路径
        status.append("\n🗂️ 模型类型路径:\n")
        val paths = getAllExternalModelsPaths(context)
        for ((type, path) in paths) {
            val dir = java.io.File(path)
            val exists = dir.exists()
            val isDirectory = if (exists) dir.isDirectory else false
            
            // 只检查存在性，不检查可读可写（简化权限处理）
            status.append("   $type: ${if (exists && isDirectory) "✅" else "❌"}\n")
            status.append("     路径: $path\n")
            status.append("     状态: 存在=$exists, 目录=$isDirectory\n")
        }
        
        return status.toString()
    }
    
    /**
     * 获取当前路径状态信息（简化版）
     */
    fun getPathStatus(context: Context): String {
        val basePath = getExternalModelsBasePath(context)
        val isAvailable = isExternalStorageAvailable(context)
        
        return "当前模型路径: $basePath (可用: $isAvailable)"
    }
    
    /**
     * 强制重新选择最佳路径（用于路径配置更改后）
     */
    fun refreshBestPath(context: Context): String {
        Log.i(TAG, "🔄 重新选择最佳存储路径...")
        return getExternalModelsBasePath(context)
    }
    
    /**
     * 获取推荐的模型推送命令
     */
    fun getModelPushCommands(context: Context): List<String> {
        val basePath = getExternalModelsBasePath(context)
        val kwsPath = "$basePath/$KWS_SUBDIR"
        
        return listOf(
            "# 📋 SherpaOnnx KWS 模型推送命令",
            "adb shell mkdir -p \"$kwsPath\"",
            "adb push app/src/main/assets/models/sherpa_onnx_kws/* \"$kwsPath/\"",
            "",
            "# 🔍 验证推送结果",
            "adb shell ls -la \"$kwsPath/\"",
            "",
            "# 📍 当前推荐路径: $kwsPath"
        )
    }
    
    // 向后兼容的传统路径方法（已废弃，建议使用上面的新方法）
    @Deprecated("使用 getExternalVoskModelsPath(context) 替代")
    fun getLegacyVoskPath(): String = "${CANDIDATE_BASE_PATHS[2]}/$VOSK_SUBDIR"
    
    @Deprecated("使用 getExternalTtsModelsPath(context) 替代")
    fun getLegacyTtsPath(): String = "${CANDIDATE_BASE_PATHS[2]}/$TTS_SUBDIR"
    
    @Deprecated("使用 getExternalKwsModelsPath(context) 替代")  
    fun getLegacyKwsPath(): String = "${CANDIDATE_BASE_PATHS[2]}/$KWS_SUBDIR"
}
