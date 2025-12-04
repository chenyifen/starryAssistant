package com.ai.voice.di

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.dicio.skill.context.SpeechOutputDevice
import com.ai.voice.io.speech.AndroidTtsSpeechDevice
import com.ai.voice.io.speech.LanguageDetectingSpeechDevice
import com.ai.voice.io.speech.NothingSpeechDevice
import com.ai.voice.io.speech.SherpaOnnxTtsSpeechDevice
import com.ai.voice.io.speech.SnackbarSpeechDevice
import com.ai.voice.io.speech.ToastSpeechDevice
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechOutputDeviceWrapper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localeManager: LocaleManager,
    private val snackbarSpeechDevice: SnackbarSpeechDevice,
) : SpeechOutputDevice {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var wrappedSpeechDevice: SpeechOutputDevice = NothingSpeechDevice()

    init {
        scope.launch {
            localeManager.locale.collect { locale ->
                val prevDevice = wrappedSpeechDevice
                wrappedSpeechDevice = tryCreateTtsDevice(locale)
                prevDevice.cleanup()
            }
        }
    }

    private suspend fun tryCreateTtsDevice(locale: java.util.Locale): SpeechOutputDevice {
        // 启用自动语言检测
        try {
            Log.i(TAG, "🌐 创建多语言TTS设备")
            return LanguageDetectingSpeechDevice(
                context = context,
                defaultLocale = locale,
                deviceFactory = { ctx, loc -> createPrimaryTtsDevice(loc) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ 多语言TTS设备创建失败: ${e.message}")
        }

        // 降级链：SherpaOnnx -> AndroidTTS -> Toast
        return createPrimaryTtsDevice(locale)
            ?: try { AndroidTtsSpeechDevice(context, locale) } catch (e: Exception) { null }
            ?: ToastSpeechDevice(context)
    }

    private fun createPrimaryTtsDevice(locale: java.util.Locale): SpeechOutputDevice? {
        return try {
            SherpaOnnxTtsSpeechDevice(context, locale)
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ SherpaOnnxTtsSpeechDevice创建失败: ${e.message}")
            null
        }
    }

    override fun speak(speechOutput: String) {
        Log.d(TAG, "🗣️ speak(): '$speechOutput'")
        wrappedSpeechDevice.speak(speechOutput)
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
        Log.w(TAG, "Unexpected call to SpeechOutputDeviceWrapper.cleanup()")
    }

    fun setAsrLocale(locale: java.util.Locale?) {
        val device = wrappedSpeechDevice
        if (device is LanguageDetectingSpeechDevice) {
            device.setAsrLocale(locale)
        }
    }

    companion object {
        val TAG: String = SpeechOutputDeviceWrapper::class.simpleName!!
    }
}
