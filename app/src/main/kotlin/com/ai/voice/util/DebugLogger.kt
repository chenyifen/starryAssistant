package com.ai.voice.util

import android.util.Log
import java.lang.reflect.Method

object DebugLogger {
    
    private const val SYSTEM_PROP_DEBUG_LOG = "persist.debug.voice.log"
    private const val SYSTEM_PROP_WAKE_WORD = "persist.debug.voice.wake_word"
    private const val SYSTEM_PROP_VOICE_RECOGNITION = "persist.debug.voice.recognition"
    private const val SYSTEM_PROP_AUDIO_PROCESSING = "persist.debug.voice.audio"
    private const val SYSTEM_PROP_MODEL_MANAGEMENT = "persist.debug.voice.model"
    private const val SYSTEM_PROP_STATE_MACHINE = "persist.debug.voice.state"
    private const val SYSTEM_PROP_UI = "persist.debug.voice.ui"
    private const val SYSTEM_PROP_ASR_TEXT_FLOW = "persist.debug.voice.asr_text"
    private const val SYSTEM_PROP_SAVE_AUDIO = "persist.debug.voice.save_audio"
    
    private var systemPropertiesGet: Method? = null
    private var debugLogEnabled: Boolean? = null
    private var debugLogInitialized = false
    
    init {
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            systemPropertiesGet = clazz.getMethod("get", String::class.java, String::class.java)
        } catch (e: Exception) {
        }
    }
    
    private fun getSystemProperty(key: String, defaultValue: String = "false"): String {
        return try {
            val result = systemPropertiesGet?.invoke(null, key, defaultValue) as? String ?: defaultValue
            result
        } catch (e: Exception) {
            defaultValue
        }
    }
    
    private fun getSystemPropertyBoolean(key: String, defaultValue: Boolean = false): Boolean {
        val value = getSystemProperty(key, if (defaultValue) "true" else "false")
        val result = value == "true" || value == "1"
        return result
    }
    
    private fun isDebugEnabled(): Boolean {
        if (!debugLogInitialized) {
            debugLogEnabled = getSystemPropertyBoolean(SYSTEM_PROP_DEBUG_LOG)
            val value = getSystemProperty(SYSTEM_PROP_DEBUG_LOG, "false")
            Log.i("DebugLogger", "系统属性 $SYSTEM_PROP_DEBUG_LOG = $value (默认值: false)")
            Log.i("DebugLogger", "系统属性 $SYSTEM_PROP_DEBUG_LOG 布尔值 = $debugLogEnabled (原始值: $value)")
            Log.i("DebugLogger", "Debug日志总开关: $debugLogEnabled")
            debugLogInitialized = true
        }
        return debugLogEnabled ?: false
    }
    
    private fun isWakeWordEnabled(): Boolean = getSystemPropertyBoolean(SYSTEM_PROP_WAKE_WORD) || isDebugEnabled()
    private fun isVoiceRecognitionEnabled(): Boolean = getSystemPropertyBoolean(SYSTEM_PROP_VOICE_RECOGNITION) || isDebugEnabled()
    private fun isAudioProcessingEnabled(): Boolean = getSystemPropertyBoolean(SYSTEM_PROP_AUDIO_PROCESSING) || isDebugEnabled()
    private fun isModelManagementEnabled(): Boolean = getSystemPropertyBoolean(SYSTEM_PROP_MODEL_MANAGEMENT) || isDebugEnabled()
    private fun isStateMachineEnabled(): Boolean = getSystemPropertyBoolean(SYSTEM_PROP_STATE_MACHINE) || isDebugEnabled()
    private fun isUIEnabled(): Boolean = getSystemPropertyBoolean(SYSTEM_PROP_UI) || isDebugEnabled()
    private fun isAsrTextFlowEnabled(): Boolean = getSystemPropertyBoolean(SYSTEM_PROP_ASR_TEXT_FLOW) || isDebugEnabled()
    private fun isSaveAudioEnabled(): Boolean = getSystemPropertyBoolean(SYSTEM_PROP_SAVE_AUDIO)
    
    fun logWakeWord(tag: String?, message: String) {
        if (isWakeWordEnabled() && tag != null) {
            Log.i("🔊[$tag]", message)
        }
    }
    
    fun logWakeWordError(tag: String?, message: String, throwable: Throwable? = null) {
        if (isWakeWordEnabled() && tag != null) {
            if (throwable != null) {
                Log.e("🔊[$tag]", message, throwable)
            } else {
                Log.e("🔊[$tag]", message)
            }
        }
    }
    
    fun logVoiceRecognition(tag: String?, message: String) {
        if (isVoiceRecognitionEnabled() && tag != null) {
            Log.i("🎤[$tag]", message)
        }
    }
    
    fun logVoiceRecognitionError(tag: String?, message: String, throwable: Throwable? = null) {
        if (isVoiceRecognitionEnabled() && tag != null) {
            if (throwable != null) {
                Log.e("🎤[$tag]", message, throwable)
            } else {
                Log.e("🎤[$tag]", message)
            }
        }
    }
    
    fun logAudioProcessing(tag: String?, message: String) {
        if (isAudioProcessingEnabled() && tag != null) {
            Log.d("🎵[$tag]", message)
        }
    }
    
    fun logAudio(tag: String?, message: String) {
        if (isAudioProcessingEnabled() && tag != null) {
            Log.d("🎵[$tag]", message)
        }
    }
    
    fun logRecognition(tag: String?, message: String) {
        if (isVoiceRecognitionEnabled() && tag != null) {
            Log.d("🎤[$tag]", message)
        }
    }
    
    fun logAsrTextFlow(tag: String?, message: String) {
        if (isAsrTextFlowEnabled() && tag != null) {
            Log.i("🔍ASR_FLOW[$tag]", message)
        }
    }
    
    fun logModelManagement(tag: String?, message: String) {
        if (isModelManagementEnabled() && tag != null) {
            Log.d("📦[$tag]", message)
        }
    }
    
    fun logStateMachine(tag: String?, message: String) {
        if (isStateMachineEnabled() && tag != null) {
            Log.d("⚙️[$tag]", message)
        }
    }
    
    fun logDebug(tag: String?, message: String) {
        if (isDebugEnabled() && tag != null) {
            Log.i("🐛[$tag]", message)
        }
    }
    
    fun logUI(tag: String?, message: String) {
        if (isUIEnabled() && tag != null) {
            Log.i("🎨[$tag]", message)
        }
    }
    
    fun logPerformance(tag: String?, operation: String, timeMs: Long) {
        if (isDebugEnabled() && tag != null) {
            Log.d("⏱️[$tag]", "$operation took ${timeMs}ms")
        }
    }
    
    fun logAudioStats(tag: String?, frameSize: Int, amplitude: Float, threshold: Float) {
        if (isAudioProcessingEnabled() && tag != null) {
            Log.d("📊[$tag]", "Frame: $frameSize, Amplitude: %.3f, Threshold: %.3f".format(amplitude, threshold))
        }
    }
    
    fun logWakeWordDetection(tag: String?, confidence: Float, threshold: Float, detected: Boolean) {
        if (isWakeWordEnabled() && tag != null) {
            if (confidence > 0.0f || detected) {
                val status = if (detected) "✅ DETECTED" else "❌ NOT_DETECTED"
                Log.d("🎯[$tag]", "$status - Confidence: %.3f, Threshold: %.3f".format(confidence, threshold))
            }
        }
    }
    
    fun isAudioSaveEnabled(): Boolean = isSaveAudioEnabled()
    
    fun logIfDebug(tag: String, message: String) {
        if (isDebugEnabled()) {
            Log.i(tag, message)
        }
    }
    
    fun logWarnIfDebug(tag: String, message: String) {
        if (isDebugEnabled()) {
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
