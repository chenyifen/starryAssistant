package com.ai.voice.util

import android.util.Log
import com.ai.voice.BuildConfig

/**
 * 调试日志管理器 - 根据构建类型自动控制日志输出
 * 
 * Debug版本: 输出所有调试日志
 * Release版本: 仅输出错误日志，移除所有调试日志
 */
object DebugLogger {
    
    // 🔒 Release版本禁用所有调试日志，仅保留错误日志
    // Debug版本启用所有日志
    private val DEBUG_ENABLED = BuildConfig.DEBUG
    
    // 各模块的调试开关（仅在Debug版本启用）
    private val DEBUG_WAKE_WORD = DEBUG_ENABLED
    private val DEBUG_VOICE_RECOGNITION = DEBUG_ENABLED
    private val DEBUG_AUDIO_PROCESSING = DEBUG_ENABLED
    private val DEBUG_MODEL_MANAGEMENT = DEBUG_ENABLED
    private val DEBUG_STATE_MACHINE = DEBUG_ENABLED
    private val DEBUG_UI = DEBUG_ENABLED
    
    // 音频保存调试功能 - Debug版本可启用
    private val DEBUG_SAVE_AUDIO = DEBUG_ENABLED && false
    
    // ASR文本显示专用调试开关 - Debug版本可启用
    private val DEBUG_ASR_TEXT_FLOW = DEBUG_ENABLED
    
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
    
    // ASR文本流调试 - 专门用于调试ASR文本显示问题
    fun logAsrTextFlow(tag: String?, message: String) {
        if (DEBUG_ASR_TEXT_FLOW && tag != null) {
            Log.i("🔍ASR_FLOW[$tag]", message)
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
    
    // UI相关日志
    fun logUI(tag: String?, message: String) {
        if (DEBUG_UI && tag != null) {
            Log.d("🎨[$tag]", message)
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
    
    /**
     * Release版本安全的日志输出
     * Debug版本: 正常输出
     * Release版本: 不输出（会被ProGuard移除）
     */
    fun logIfDebug(tag: String, message: String) {
        if (DEBUG_ENABLED) {
            Log.d(tag, message)
        }
    }
    
    /**
     * Release版本安全的警告日志
     * Debug版本: 正常输出
     * Release版本: 不输出（会被ProGuard移除）
     */
    fun logWarnIfDebug(tag: String, message: String) {
        if (DEBUG_ENABLED) {
            Log.w(tag, message)
        }
    }
    
    /**
     * Release版本安全的错误日志
     * 错误日志在Release版本也会输出（用于问题排查）
     */
    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
    
    /**
     * 🔒 关键日志：ASR识别结果（Release版本也输出）
     * 用于生产环境监控和问题排查
     */
    fun logAsrResult(tag: String, text: String, confidence: Float = 0f) {
        if (text.isNotBlank()) {
            Log.i("🎤[$tag]", "ASR识别结果: \"$text\"${if (confidence > 0) " (置信度: $confidence)" else ""}")
        }
    }
    
    /**
     * 🔒 关键日志：唤醒词检测成功（Release版本也输出）
     */
    fun logWakeWordSuccess(tag: String) {
        Log.i("🔊[$tag]", "✅ 唤醒词检测成功")
    }
    
    /**
     * 🔒 关键日志：命令执行成功（Release版本也输出）
     */
    fun logCommandExecuted(tag: String, skillId: String, command: String?, result: String = "") {
        val commandText = command ?: "未知命令"
        val resultText = if (result.isNotBlank()) " -> $result" else ""
        Log.i("✅[$tag]", "命令执行: [$skillId] \"$commandText\"$resultText")
    }
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
