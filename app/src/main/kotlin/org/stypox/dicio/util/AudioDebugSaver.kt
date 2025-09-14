package org.stypox.dicio.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * 音频调试保存工具类
 * 用于保存唤醒和ASR音频数据以便调试分析
 */
object AudioDebugSaver {
    private val TAG = AudioDebugSaver::class.simpleName ?: "AudioDebugSaver"
    
    // 音频保存目录
    private const val AUDIO_DEBUG_DIR = "audio_debug"
    private const val WAKE_AUDIO_DIR = "wake_audio"
    private const val ASR_AUDIO_DIR = "asr_audio"
    
    // 文件名时间格式
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault())
    
    /**
     * 保存唤醒音频数据
     * @param context 应用上下文
     * @param audioData 16位PCM音频数据
     * @param amplitude 音频幅度
     * @param confidence 置信度
     */
    fun saveWakeAudio(
        context: Context,
        audioData: ShortArray,
        amplitude: Float,
        confidence: Float
    ) {
        if (!DebugLogger.isAudioSaveEnabled()) return
        
        // 只保存有音频信号的数据（幅度不为0）
        if (amplitude <= 0.0f) return
        
        try {
            val timestamp = dateFormat.format(Date())
            val fileName = "wake_${timestamp}_amp${String.format("%.3f", amplitude)}_conf${String.format("%.3f", confidence)}.pcm"
            
            val audioFile = getAudioFile(context, WAKE_AUDIO_DIR, fileName)
            saveAudioToPcm(audioFile, audioData)
            
            DebugLogger.logDebug(TAG, "💾 Saved wake audio: $fileName (${audioData.size} samples)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save wake audio", e)
        }
    }
    
    /**
     * 保存ASR音频数据
     * @param context 应用上下文
     * @param audioData 16位PCM音频数据
     * @param sessionId 会话ID
     */
    fun saveAsrAudio(
        context: Context,
        audioData: ShortArray,
        sessionId: String = "unknown"
    ) {
        if (!DebugLogger.isAudioSaveEnabled()) return
        
        try {
            val timestamp = dateFormat.format(Date())
            val fileName = "asr_${timestamp}_session_${sessionId}.pcm"
            
            val audioFile = getAudioFile(context, ASR_AUDIO_DIR, fileName)
            saveAudioToPcm(audioFile, audioData)
            
            DebugLogger.logDebug(TAG, "💾 Saved ASR audio: $fileName (${audioData.size} samples)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save ASR audio", e)
        }
    }
    
    /**
     * 保存音频数据到PCM文件
     */
    private fun saveAudioToPcm(file: File, audioData: ShortArray) {
        FileOutputStream(file).use { fos ->
            // 将ShortArray转换为字节数组（小端序）
            for (sample in audioData) {
                fos.write(sample.toInt() and 0xFF)  // 低字节
                fos.write((sample.toInt() shr 8) and 0xFF)  // 高字节
            }
        }
    }
    
    /**
     * 获取音频文件对象
     */
    private fun getAudioFile(context: Context, subDir: String, fileName: String): File {
        val audioDebugDir = File(context.filesDir, AUDIO_DEBUG_DIR)
        val subDirectory = File(audioDebugDir, subDir)
        
        // 确保目录存在
        if (!subDirectory.exists()) {
            subDirectory.mkdirs()
        }
        
        return File(subDirectory, fileName)
    }
    
    /**
     * 清理旧的音频文件（保留最近的文件）
     * @param context 应用上下文
     * @param maxFiles 每个目录最多保留的文件数
     */
    fun cleanupOldAudioFiles(context: Context, maxFiles: Int = 50) {
        if (!DebugLogger.isAudioSaveEnabled()) return
        
        try {
            val audioDebugDir = File(context.filesDir, AUDIO_DEBUG_DIR)
            if (!audioDebugDir.exists()) return
            
            // 清理唤醒音频
            cleanupDirectory(File(audioDebugDir, WAKE_AUDIO_DIR), maxFiles)
            
            // 清理ASR音频
            cleanupDirectory(File(audioDebugDir, ASR_AUDIO_DIR), maxFiles)
            
            DebugLogger.logDebug(TAG, "🧹 Cleaned up old audio files, keeping $maxFiles files per directory")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup audio files", e)
        }
    }
    
    /**
     * 清理指定目录中的旧文件
     */
    private fun cleanupDirectory(directory: File, maxFiles: Int) {
        if (!directory.exists()) return
        
        val files = directory.listFiles() ?: return
        if (files.size <= maxFiles) return
        
        // 按修改时间排序，删除最旧的文件
        val sortedFiles = files.sortedBy { it.lastModified() }
        val filesToDelete = sortedFiles.take(files.size - maxFiles)
        
        for (file in filesToDelete) {
            if (file.delete()) {
                DebugLogger.logDebug(TAG, "🗑️ Deleted old audio file: ${file.name}")
            }
        }
    }
    
    /**
     * 获取音频调试目录信息
     */
    fun getAudioDebugInfo(context: Context): String {
        val audioDebugDir = File(context.filesDir, AUDIO_DEBUG_DIR)
        if (!audioDebugDir.exists()) {
            return "Audio debug directory not found"
        }
        
        val wakeDir = File(audioDebugDir, WAKE_AUDIO_DIR)
        val asrDir = File(audioDebugDir, ASR_AUDIO_DIR)
        
        val wakeCount = wakeDir.listFiles()?.size ?: 0
        val asrCount = asrDir.listFiles()?.size ?: 0
        
        return "Audio Debug Files:\n" +
                "Wake audio files: $wakeCount\n" +
                "ASR audio files: $asrCount\n" +
                "Directory: ${audioDebugDir.absolutePath}"
    }
    
    /**
     * 删除所有音频调试文件
     */
    fun clearAllAudioFiles(context: Context): Boolean {
        return try {
            val audioDebugDir = File(context.filesDir, AUDIO_DEBUG_DIR)
            if (audioDebugDir.exists()) {
                audioDebugDir.deleteRecursively()
                DebugLogger.logDebug(TAG, "🗑️ Cleared all audio debug files")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear audio files", e)
            false
        }
    }
    
    /**
     * 复制音频文件到外部存储以便导出
     * 需要 WRITE_EXTERNAL_STORAGE 权限
     */
    fun exportAudioToExternalStorage(context: Context): String? {
        return try {
            val audioDebugDir = File(context.filesDir, AUDIO_DEBUG_DIR)
            if (!audioDebugDir.exists()) {
                return "No audio debug files found"
            }
            
            // 使用外部存储的 Documents 目录
            val externalDir = File(context.getExternalFilesDir(null), "dicio_audio_export")
            if (!externalDir.exists()) {
                externalDir.mkdirs()
            }
            
            // 复制所有音频文件
            audioDebugDir.copyRecursively(externalDir, overwrite = true)
            
            val exportPath = externalDir.absolutePath
            DebugLogger.logDebug(TAG, "📤 Exported audio files to: $exportPath")
            exportPath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export audio files", e)
            null
        }
    }
    
    /**
     * 获取所有音频文件的详细信息
     */
    fun getAudioFilesDetails(context: Context): List<AudioFileInfo> {
        val audioDebugDir = File(context.filesDir, AUDIO_DEBUG_DIR)
        val fileInfoList = mutableListOf<AudioFileInfo>()
        
        if (!audioDebugDir.exists()) return fileInfoList
        
        // 扫描唤醒音频文件
        val wakeDir = File(audioDebugDir, WAKE_AUDIO_DIR)
        if (wakeDir.exists()) {
            wakeDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".pcm")) {
                    fileInfoList.add(
                        AudioFileInfo(
                            name = file.name,
                            path = file.absolutePath,
                            size = file.length(),
                            lastModified = file.lastModified(),
                            type = "Wake Audio"
                        )
                    )
                }
            }
        }
        
        // 扫描ASR音频文件
        val asrDir = File(audioDebugDir, ASR_AUDIO_DIR)
        if (asrDir.exists()) {
            asrDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".pcm")) {
                    fileInfoList.add(
                        AudioFileInfo(
                            name = file.name,
                            path = file.absolutePath,
                            size = file.length(),
                            lastModified = file.lastModified(),
                            type = "ASR Audio"
                        )
                    )
                }
            }
        }
        
        return fileInfoList.sortedByDescending { it.lastModified }
    }
}

/**
 * 音频文件信息数据类
 */
data class AudioFileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val type: String
)
