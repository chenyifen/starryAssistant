package org.stypox.dicio.util

import android.util.Log

/**
 * 调试日志管理器 - 提供一键开关调试日志的功能
 */
object DebugLogger {
    
    // 🔧 调试开关 - 修改这里可以一键开启/关闭所有调试日志
    private const val DEBUG_ENABLED = true
    
    // 各模块的调试开关
    private const val DEBUG_WAKE_WORD = DEBUG_ENABLED && true
    private const val DEBUG_VOICE_RECOGNITION = DEBUG_ENABLED && true
    private const val DEBUG_AUDIO_PROCESSING = DEBUG_ENABLED && false 
    private const val DEBUG_MODEL_MANAGEMENT = DEBUG_ENABLED && true
    private const val DEBUG_STATE_MACHINE = DEBUG_ENABLED && true
    
    // 音频保存调试功能 - 可以一键关闭
    private const val DEBUG_SAVE_AUDIO = DEBUG_ENABLED && false
    
    // 唤醒词相关日志
    fun logWakeWord(tag: String?, message: String) {
        if (DEBUG_WAKE_WORD && tag != null) {
            Log.d("🔊[$tag]", message)
        }
    }
    
    fun logWakeWordError(tag: String?, message: String, throwable: Throwable? = null) {
        if (DEBUG_WAKE_WORD && tag != null) {
            if (throwable != null) {
                Log.e("🔊[$tag]", message, throwable)
            } else {
                Log.e("🔊[$tag]", message)
            }
        }
    }
    
    // 语音识别相关日志
    fun logVoiceRecognition(tag: String?, message: String) {
        if (DEBUG_VOICE_RECOGNITION && tag != null) {
            Log.d("🎤[$tag]", message)
        }
    }
    
    fun logVoiceRecognitionError(tag: String?, message: String, throwable: Throwable? = null) {
        if (DEBUG_VOICE_RECOGNITION && tag != null) {
            if (throwable != null) {
                Log.e("🎤[$tag]", message, throwable)
            } else {
                Log.e("🎤[$tag]", message)
            }
        }
    }
    
    // 音频处理相关日志
    fun logAudioProcessing(tag: String?, message: String) {
        if (DEBUG_AUDIO_PROCESSING && tag != null) {
            Log.d("🎵[$tag]", message)
        }
    }
    
    // 音频数据日志
    fun logAudio(tag: String?, message: String) {
        if (DEBUG_AUDIO_PROCESSING && tag != null) {
            Log.d("🎵[$tag]", message)
        }
    }
    
    // 识别结果日志
    fun logRecognition(tag: String?, message: String) {
        if (DEBUG_VOICE_RECOGNITION && tag != null) {
            Log.d("🎤[$tag]", message)
        }
    }
    
    // 模型管理相关日志
    fun logModelManagement(tag: String?, message: String) {
        if (DEBUG_MODEL_MANAGEMENT && tag != null) {
            Log.d("📦[$tag]", message)
        }
    }
    
    // 状态机相关日志
    fun logStateMachine(tag: String?, message: String) {
        if (DEBUG_STATE_MACHINE && tag != null) {
            Log.d("⚙️[$tag]", message)
        }
    }
    
    // 通用调试日志
    fun logDebug(tag: String?, message: String) {
        if (DEBUG_ENABLED && tag != null) {
            Log.d("🐛[$tag]", message)
        }
    }
    
    // 性能监控
    fun logPerformance(tag: String?, operation: String, timeMs: Long) {
        if (DEBUG_ENABLED && tag != null) {
            Log.d("⏱️[$tag]", "$operation took ${timeMs}ms")
        }
    }
    
    // 音频数据统计
    fun logAudioStats(tag: String?, frameSize: Int, amplitude: Float, threshold: Float) {
        if (DEBUG_AUDIO_PROCESSING && tag != null) {
            Log.d("📊[$tag]", "Frame: $frameSize, Amplitude: %.3f, Threshold: %.3f".format(amplitude, threshold))
        }
    }
    
    // 唤醒词检测结果
    fun logWakeWordDetection(tag: String?, confidence: Float, threshold: Float, detected: Boolean) {
        if (DEBUG_WAKE_WORD && tag != null) {
            // 过滤掉置信度为0的日志，减少输出噪音
            if (confidence > 0.0f || detected) {
                val status = if (detected) "✅ DETECTED" else "❌ NOT_DETECTED"
                Log.d("🎯[$tag]", "$status - Confidence: %.3f, Threshold: %.3f".format(confidence, threshold))
            }
        }
    }
    
    /**
     * 检查音频保存功能是否启用
     */
    fun isAudioSaveEnabled(): Boolean = DEBUG_SAVE_AUDIO
}

/**
 * 性能监控扩展函数
 */
inline fun <T> measureTimeAndLog(tag: String?, operation: String, block: () -> T): T {
    val startTime = System.currentTimeMillis()
    val result = block()
    val endTime = System.currentTimeMillis()
    //TODO chenyifen DebugLogger.logPerformance(tag, operation, endTime - startTime)
    return result
}
