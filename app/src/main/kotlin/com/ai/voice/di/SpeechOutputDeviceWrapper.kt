package com.ai.voice.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.dicio.skill.context.SpeechOutputDevice
import com.ai.voice.io.speech.AndroidTtsSpeechDevice
import com.ai.voice.io.speech.LanguageDetectingSpeechDevice
import com.ai.voice.io.speech.NothingSpeechDevice
import com.ai.voice.io.speech.SherpaOnnxTtsSpeechDevice
import com.ai.voice.io.speech.SnackbarSpeechDevice
import com.ai.voice.io.speech.ToastSpeechDevice
import com.ai.voice.settings.datastore.SpeechOutputDevice.SPEECH_OUTPUT_DEVICE_ANDROID_TTS
import com.ai.voice.settings.datastore.SpeechOutputDevice.SPEECH_OUTPUT_DEVICE_NOTHING
import com.ai.voice.settings.datastore.SpeechOutputDevice.SPEECH_OUTPUT_DEVICE_SHERPA_ONNX_TTS
import com.ai.voice.settings.datastore.SpeechOutputDevice.SPEECH_OUTPUT_DEVICE_SNACKBAR
import com.ai.voice.settings.datastore.SpeechOutputDevice.SPEECH_OUTPUT_DEVICE_TOAST
import com.ai.voice.settings.datastore.SpeechOutputDevice.SPEECH_OUTPUT_DEVICE_UNSET
import com.ai.voice.settings.datastore.SpeechOutputDevice.UNRECOGNIZED
import com.ai.voice.settings.datastore.UserSettings
import com.ai.voice.settings.datastore.TtsFallbackDevice
import com.ai.voice.settings.datastore.TtsFallbackChain
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechOutputDeviceWrapper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<UserSettings>,
    private val localeManager: LocaleManager,
    // this is always instantiated, but will do nothing if
    // it is not the speech device chosen by the user
    private val snackbarSpeechDevice: SnackbarSpeechDevice,
) : SpeechOutputDevice {

    // instantiate SpeechOutputDevices on the main thread
    private val scope = CoroutineScope(Dispatchers.Main)
    private var wrappedSpeechDevice: SpeechOutputDevice = NothingSpeechDevice()
    
    // 降级链配置：默认降级顺序
    // 使用完整版sherpa-onnx 1.12.14，包含TTS功能
    private val defaultTtsFallbackChain = listOf(
        TtsFallbackDevice.TTS_FALLBACK_DEVICE_SHERPA_ONNX,  // 完整版AAR包含TTS
        TtsFallbackDevice.TTS_FALLBACK_DEVICE_ANDROID_TTS,
        TtsFallbackDevice.TTS_FALLBACK_DEVICE_TOAST,
        TtsFallbackDevice.TTS_FALLBACK_DEVICE_SNACKBAR
    )
    
    private var currentFallbackChain: List<TtsFallbackDevice> = defaultTtsFallbackChain
    private var currentFallbackIndex = 0
    
    // 自动语言检测设置（默认启用）
    private var enableAutoLanguageDetection = true

    init {
        scope.launch {
            dataStore.data
                .combine(localeManager.locale) { userSettings, locale ->
                    Triple(userSettings.ttsFallbackChain, userSettings.speechOutputDevice, locale)
                }
                .distinctUntilChanged()
                .collect { (fallbackChain, setting, locale) ->
                    // 更新降级链配置
                    currentFallbackChain = if (fallbackChain != null && fallbackChain.devicesList.isNotEmpty()) {
                        Log.d(TAG, "📋 使用自定义TTS降级链: ${fallbackChain.devicesList}")
                        fallbackChain.devicesList
                    } else {
                        Log.d(TAG, "📋 使用默认TTS降级链: $defaultTtsFallbackChain")
                        defaultTtsFallbackChain
                    }
                    
                    // 重置降级索引
                    currentFallbackIndex = 0
                    
                    // 初始化TTS设备（使用降级链）
                    val prevDevice = wrappedSpeechDevice
                    wrappedSpeechDevice = tryCreateTtsDeviceWithFallback(locale)
                    prevDevice.cleanup()
                }
        }
    }

    /**
     * 尝试使用降级链创建TTS设备
     */
    private suspend fun tryCreateTtsDeviceWithFallback(locale: java.util.Locale): SpeechOutputDevice {
        // 🌐 如果启用自动语言检测，创建多语言TTS设备
        if (enableAutoLanguageDetection) {
            Log.i(TAG, "🌐 启用自动语言检测，创建多语言TTS设备")
            try {
                val languageDetectingDevice = LanguageDetectingSpeechDevice(
                    context = context,
                    defaultLocale = locale,
                    deviceFactory = { ctx, loc -> createTtsDevice(currentFallbackChain[0], loc) }
                )
                return languageDetectingDevice
            } catch (e: Exception) {
                Log.e(TAG, "❌ 多语言TTS设备创建失败，使用单语言TTS", e)
                // 降级到单语言TTS
            }
        }
        
        // 🔤 单语言TTS模式（使用降级链）
        for (i in currentFallbackIndex until currentFallbackChain.size) {
            val deviceType = currentFallbackChain[i]
            try {
                val device = createTtsDevice(deviceType, locale)
                if (device != null) {
                    currentFallbackIndex = i
                    Log.i(TAG, "✅ TTS降级链: 使用 ${deviceType.name} (索引: $i)")
                    return device
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ TTS降级链: ${deviceType.name} 创建失败: ${e.message}, 尝试下一个")
            }
        }
        
        // 所有设备都失败，返回NothingSpeechDevice作为最后的保底
        Log.e(TAG, "❌ TTS降级链: 所有设备创建失败，使用 NothingSpeechDevice")
        return NothingSpeechDevice()
    }
    
    /**
     * 根据类型创建TTS设备
     */
    private suspend fun createTtsDevice(deviceType: TtsFallbackDevice, locale: java.util.Locale): SpeechOutputDevice? {
        return when (deviceType) {
            TtsFallbackDevice.TTS_FALLBACK_DEVICE_SHERPA_ONNX -> {
                try {
                    SherpaOnnxTtsSpeechDevice(context, locale)
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ SherpaOnnxTtsSpeechDevice创建失败: ${e.message}")
                    null
                }
            }
            TtsFallbackDevice.TTS_FALLBACK_DEVICE_ANDROID_TTS -> {
                try {
                    AndroidTtsSpeechDevice(context, locale)
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ AndroidTtsSpeechDevice创建失败: ${e.message}")
                    null
                }
            }
            TtsFallbackDevice.TTS_FALLBACK_DEVICE_TOAST -> {
                ToastSpeechDevice(context)
            }
            TtsFallbackDevice.TTS_FALLBACK_DEVICE_SNACKBAR -> {
                snackbarSpeechDevice
            }
            TtsFallbackDevice.TTS_FALLBACK_DEVICE_NOTHING -> {
                NothingSpeechDevice()
            }
            else -> {
                Log.w(TAG, "⚠️ 未知的TTS设备类型: $deviceType")
                null
            }
        }
    }

    override fun speak(speechOutput: String) {
        Log.d(TAG, "🗣️ [DEBUG] speak() 被调用: '$speechOutput'")
        Log.d(TAG, "🗣️ [DEBUG] 当前TTS设备类型: ${wrappedSpeechDevice::class.simpleName}")
        
        // 在每次speak调用前检查当前设备是否可用
        scope.launch {
            val isAvailable = isCurrentDeviceAvailable()
            Log.d(TAG, "🗣️ [DEBUG] 当前TTS设备可用性: $isAvailable")
            
            if (!isAvailable) {
                Log.w(TAG, "⚠️ 当前TTS设备不可用，尝试降级")
                // 尝试降级到下一个设备
                currentFallbackIndex++
                val newDevice = tryCreateTtsDeviceWithFallback(localeManager.locale.value)
                wrappedSpeechDevice.cleanup()
                wrappedSpeechDevice = newDevice
                Log.d(TAG, "🗣️ [DEBUG] 降级后TTS设备类型: ${wrappedSpeechDevice::class.simpleName}")
            }
            
            Log.d(TAG, "🗣️ [DEBUG] 调用 wrappedSpeechDevice.speak()")
            wrappedSpeechDevice.speak(speechOutput)
            Log.d(TAG, "🗣️ [DEBUG] wrappedSpeechDevice.speak() 调用完成")
        }
    }
    
    /**
     * 检查当前设备是否可用
     */
    private fun isCurrentDeviceAvailable(): Boolean {
        val result = when (wrappedSpeechDevice) {
            is NothingSpeechDevice -> {
                // NothingSpeechDevice表示降级链已耗尽，返回false触发重新尝试
                Log.d(TAG, "🔍 [DEBUG] NothingSpeechDevice 不可用")
                false
            }
            is SherpaOnnxTtsSpeechDevice -> {
                // SherpaOnnx TTS初始化后应该始终可用
                Log.d(TAG, "🔍 [DEBUG] SherpaOnnxTTS 可用")
                true
            }
            is AndroidTtsSpeechDevice -> {
                // AndroidTTS初始化后应该始终可用
                Log.d(TAG, "🔍 [DEBUG] AndroidTTS 可用")
                true
            }
            else -> {
                // 其他设备默认认为可用
                Log.d(TAG, "🔍 [DEBUG] 其他TTS设备 (${wrappedSpeechDevice::class.simpleName}) 可用")
                true
            }
        }
        return result
    }

    override fun stopSpeaking() {
        wrappedSpeechDevice.stopSpeaking()
    }

    override val isSpeaking: Boolean
        get() = wrappedSpeechDevice.isSpeaking

    override fun runWhenFinishedSpeaking(runnable: Runnable) {
        wrappedSpeechDevice.runWhenFinishedSpeaking(runnable)
    }

    override fun cleanup() {
        // never called, nothing to do
        Log.w(TAG, "Unexpected call to SpeechOutputDeviceWrapper.cleanup()")
    }

    /**
     * 🆕 设置ASR识别的语言
     * 
     * 当ASR识别出用户输入后，调用此方法设置识别语言
     * 后续的TTS回复将优先使用该语言
     * 
     * @param locale ASR识别的语言，如果为null则清除设置
     */
    fun setAsrLocale(locale: java.util.Locale?) {
        val device = wrappedSpeechDevice
        if (device is LanguageDetectingSpeechDevice) {
            device.setAsrLocale(locale)
        } else {
            Log.d(TAG, "⚠️ 当前TTS设备不是LanguageDetectingSpeechDevice，无法设置ASR语言")
        }
    }

    companion object {
        val TAG: String = SpeechOutputDeviceWrapper::class.simpleName!!
    }
}
