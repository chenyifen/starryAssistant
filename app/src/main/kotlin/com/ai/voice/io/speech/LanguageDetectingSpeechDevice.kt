package com.ai.voice.io.speech

import android.content.Context
import android.util.Log
import com.ai.voice.util.LanguageDetector
import org.dicio.skill.context.SpeechOutputDevice
import java.util.Locale
import kotlinx.coroutines.*

/**
 * 智能语言检测TTS设备
 * 
 * 根据文本内容自动检测语言并切换对应的TTS设备
 * 
 * 功能：
 * 1. 自动检测韩语/英语
 * 2. 维护多个语言的TTS设备
 * 3. 根据检测结果选择合适的TTS进行朗读
 * 4. 默认使用韩语（当无法判断时）
 * 
 * @param context Android Context
 * @param defaultLocale 默认语言（通常是用户设置的语言）
 * @param deviceFactory TTS设备工厂函数，用于创建特定语言的TTS设备
 */
class LanguageDetectingSpeechDevice(
    private val context: Context,
    private val defaultLocale: Locale,
    private val deviceFactory: suspend (Context, Locale) -> SpeechOutputDevice?
) : SpeechOutputDevice {
    
    companion object {
        private const val TAG = "LanguageDetectingTTS"
        
        /**
         * 支持的语言列表
         * 默认支持韩语和英语
         */
        private val SUPPORTED_LOCALES = listOf(
            Locale.KOREAN,
            Locale.ENGLISH,
            Locale.US,  // 美式英语
            Locale.UK   // 英式英语
        )
    }
    
    // TTS设备缓存：locale -> device
    private val ttsDevices = mutableMapOf<Locale, SpeechOutputDevice>()
    
    // 当前正在说话的设备
    private var currentSpeakingDevice: SpeechOutputDevice? = null
    
    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // 是否已初始化
    private var initialized = false
    
    // 🆕 ASR识别的语言（用于优先选择TTS语言）
    private var asrLocale: Locale? = null
    
    // 回退设备（当所有TTS都失败时使用Toast）
    private val fallbackDevice: SpeechOutputDevice by lazy {
        ToastSpeechDevice(context)
    }
    
    init {
        Log.d(TAG, "🌐 初始化多语言TTS设备")
        Log.d(TAG, "  📍 默认语言: ${LanguageDetector.getLocaleName(defaultLocale)}")
        Log.d(TAG, "  🎯 支持语言: ${SUPPORTED_LOCALES.joinToString { LanguageDetector.getLocaleName(it) }}")
        
        // 异步初始化默认语言的TTS设备
        scope.launch {
            try {
                getOrCreateTtsDevice(defaultLocale)
                initialized = true
                Log.d(TAG, "✅ 默认语言TTS设备初始化完成")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 默认语言TTS设备初始化失败", e)
            }
        }
    }
    
    /**
     * 🆕 设置ASR识别的语言
     * 
     * 当ASR识别出用户输入后，调用此方法设置识别语言
     * 后续的TTS回复将优先使用该语言
     * 
     * @param locale ASR识别的语言，如果为null则清除设置
     */
    fun setAsrLocale(locale: Locale?) {
        asrLocale = locale
        if (locale != null) {
            Log.d(TAG, "🎤 ASR语言已设置: ${LanguageDetector.getLocaleName(locale)}")
        } else {
            Log.d(TAG, "🎤 ASR语言已清除")
        }
    }
    
    /**
     * 朗读文本（优先使用ASR识别语言，但需要与回复文本语言匹配）
     */
    override fun speak(speechOutput: String) {
        if (speechOutput.isBlank()) {
            Log.d(TAG, "⏭️ 文本为空，跳过朗读")
            return
        }
        
        Log.d(TAG, "🗣️ 准备朗读: \"$speechOutput\"")
        
        scope.launch {
            try {
                // 1. 检测回复文本的语言
                val textLanguage = LanguageDetector.detectLanguage(speechOutput)
                val textLocale = LanguageDetector.detectLocale(speechOutput, defaultLocale)
                
                // 2. 确定最终使用的TTS语言
                val targetLocale = if (asrLocale != null) {
                    // 🆕 检查ASR语言和文本语言是否兼容
                    // 如果ASR是英语但文本是中文/韩文，或者ASR是韩语但文本是中文/英文，则使用文本语言
                    val isLanguageCompatible = when {
                        // ASR是英语，文本也应该是英语或混合
                        asrLocale!!.language == "en" -> {
                            textLanguage == LanguageDetector.DetectedLanguage.ENGLISH || 
                            textLanguage == LanguageDetector.DetectedLanguage.MIXED
                        }
                        // ASR是韩语，文本也应该是韩语或混合
                        asrLocale!!.language == "ko" -> {
                            textLanguage == LanguageDetector.DetectedLanguage.KOREAN || 
                            textLanguage == LanguageDetector.DetectedLanguage.MIXED
                        }
                        // 其他情况使用文本语言
                        else -> false
                    }
                    
                    if (isLanguageCompatible) {
                        Log.d(TAG, "🎤 使用ASR识别的语言: ${LanguageDetector.getLocaleName(asrLocale!!)} (与文本语言兼容)")
                        asrLocale!!
                    } else {
                        // 不兼容时，如果文本是UNKNOWN（可能是中文），使用默认语言（韩语）
                        // 否则使用文本检测到的语言
                        val finalLocale = if (textLanguage == LanguageDetector.DetectedLanguage.UNKNOWN) {
                            Log.d(TAG, "⚠️ 文本语言未知，使用默认语言（韩语）")
                            defaultLocale
                        } else {
                            Log.d(TAG, "⚠️ ASR语言(${LanguageDetector.getLocaleName(asrLocale!!)})与文本语言(${LanguageDetector.getLanguageName(textLanguage)})不兼容，使用文本语言")
                            textLocale
                        }
                        finalLocale
                    }
                } else {
                    // 没有ASR语言信息，使用文本检测语言
                    Log.d(TAG, "🔍 文本语言检测结果:")
                    Log.d(TAG, "  📊 检测类型: ${LanguageDetector.getLanguageName(textLanguage)}")
                    Log.d(TAG, "  🎯 目标语言: ${LanguageDetector.getLocaleName(textLocale)}")
                    textLocale
                }
                
                Log.d(TAG, "🎯 最终选择TTS语言: ${LanguageDetector.getLocaleName(targetLocale)}")
                
                // 3. 获取或创建对应语言的TTS设备
                val ttsDevice = getOrCreateTtsDevice(targetLocale)
                
                if (ttsDevice != null) {
                    // 3. 停止当前朗读（如果有）
                    currentSpeakingDevice?.let {
                        if (it != ttsDevice) {
                            Log.d(TAG, "⏸️ 停止之前的TTS设备")
                            it.stopSpeaking()
                        }
                    }
                    
                    // 4. 使用检测到的语言进行朗读
                    currentSpeakingDevice = ttsDevice
                    Log.d(TAG, "▶️ 使用 ${LanguageDetector.getLocaleName(targetLocale)} TTS朗读")
                    
                    withContext(Dispatchers.Main) {
                        ttsDevice.speak(speechOutput)
                    }
                } else {
                    // 5. 如果无法获取TTS设备，使用fallback
                    Log.w(TAG, "⚠️ 无法获取TTS设备，使用fallback")
                    withContext(Dispatchers.Main) {
                        fallbackDevice.speak(speechOutput)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 朗读失败", e)
                withContext(Dispatchers.Main) {
                    fallbackDevice.speak(speechOutput)
                }
            }
        }
    }
    
    /**
     * 获取或创建指定语言的TTS设备
     */
    private suspend fun getOrCreateTtsDevice(locale: Locale): SpeechOutputDevice? {
        // 1. 检查缓存
        ttsDevices[locale]?.let {
            Log.d(TAG, "♻️ 使用缓存的TTS设备: ${LanguageDetector.getLocaleName(locale)}")
            return it
        }
        
        // 2. 尝试创建新设备
        Log.d(TAG, "🔨 创建新TTS设备: ${LanguageDetector.getLocaleName(locale)}")
        return try {
            val device = deviceFactory(context, locale)
            if (device != null) {
                ttsDevices[locale] = device
                Log.d(TAG, "✅ TTS设备创建成功: ${LanguageDetector.getLocaleName(locale)}")
                device
            } else {
                Log.w(TAG, "⚠️ TTS设备创建返回null: ${LanguageDetector.getLocaleName(locale)}")
                
                // 尝试使用默认语言的设备
                if (locale != defaultLocale) {
                    Log.d(TAG, "🔄 尝试使用默认语言设备")
                    ttsDevices[defaultLocale]
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ TTS设备创建失败: ${LanguageDetector.getLocaleName(locale)}", e)
            
            // 尝试使用默认语言的设备
            if (locale != defaultLocale) {
                Log.d(TAG, "🔄 尝试使用默认语言设备")
                ttsDevices[defaultLocale]
            } else {
                null
            }
        }
    }
    
    override fun stopSpeaking() {
        Log.d(TAG, "⏹️ 停止所有TTS设备")
        currentSpeakingDevice?.stopSpeaking()
        currentSpeakingDevice = null
    }
    
    override fun runWhenFinishedSpeaking(runnable: Runnable) {
        // 转发到当前说话的设备
        currentSpeakingDevice?.runWhenFinishedSpeaking(runnable)
    }
    
    override val isSpeaking: Boolean
        get() = currentSpeakingDevice?.isSpeaking == true
    
    override fun cleanup() {
        Log.d(TAG, "🧹 清理所有TTS设备")
        
        // 取消所有协程
        scope.cancel()
        
        // 清理所有TTS设备
        ttsDevices.values.forEach { device ->
            try {
                device.cleanup()
            } catch (e: Exception) {
                Log.e(TAG, "清理TTS设备时出错", e)
            }
        }
        ttsDevices.clear()
        
        currentSpeakingDevice = null
        
        // 清理fallback设备
        try {
            fallbackDevice.cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "清理fallback设备时出错", e)
        }
    }
    
    /**
     * 预加载指定语言的TTS设备
     * 
     * 用于在应用启动时预先初始化常用语言的TTS，提高响应速度
     */
    suspend fun preloadLanguage(locale: Locale) {
        Log.d(TAG, "⏬ 预加载TTS设备: ${LanguageDetector.getLocaleName(locale)}")
        getOrCreateTtsDevice(locale)
    }
    
    /**
     * 预加载所有支持的语言
     */
    suspend fun preloadAllLanguages() {
        Log.d(TAG, "⏬ 预加载所有支持的语言")
        for (locale in SUPPORTED_LOCALES) {
            try {
                preloadLanguage(locale)
            } catch (e: Exception) {
                Log.w(TAG, "预加载 ${LanguageDetector.getLocaleName(locale)} 失败", e)
            }
        }
    }
}

