package org.stypox.dicio.io.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import org.dicio.skill.context.SpeechOutputDevice
import org.stypox.dicio.R
import java.util.Locale

class AndroidTtsSpeechDevice(private var context: Context, inputLocale: Locale) : SpeechOutputDevice {
    private var textToSpeech: TextToSpeech? = null
    private var initializedCorrectly = false
    private val runnablesWhenFinished: MutableList<Runnable> = ArrayList()
    private var lastUtteranceId = 0

    // 处理语言映射，确保TTS能正确识别
    private val locale: Locale = mapToTtsCompatibleLocale(inputLocale)
    
    init {
        Log.d(TAG, "🔊 AndroidTtsSpeechDevice - 初始化TTS:")
        Log.d(TAG, "  📥 输入语言: $inputLocale (language=${inputLocale.language}, country=${inputLocale.country})")
        Log.d(TAG, "  🔄 映射后语言: $locale (language=${locale.language}, country=${locale.country})")
        
        // 检查系统TTS支持情况
        checkSystemTtsSupport()
        
        textToSpeech = TextToSpeech(context) { status: Int ->
            Log.d(TAG, "🔊 TTS初始化回调 - status: $status")
            if (status == TextToSpeech.SUCCESS) {
                Log.d(TAG, "  ✅ TTS初始化成功，设置语言...")
                textToSpeech?.run {
                    val errorCode = setLanguage(locale)
                    Log.d(TAG, "  🌐 setLanguage($locale) 返回码: $errorCode")
                    if (errorCode >= 0) { // errors are -1 or -2
                        Log.d(TAG, "  ✅ TTS语言设置成功")
                        initializedCorrectly = true
                        setOnUtteranceProgressListener(object :
                            UtteranceProgressListener() {
                            override fun onStart(utteranceId: String) {}
                            override fun onDone(utteranceId: String) {
                                if ("dicio_$lastUtteranceId" == utteranceId) {
                                    // run only when the last enqueued utterance is finished
                                    for (runnable in runnablesWhenFinished) {
                                        runnable.run()
                                    }
                                    runnablesWhenFinished.clear()
                                }
                            }

                            @Deprecated("")
                            override fun onError(utteranceId: String) {
                            }
                        })
                    } else {
                        Log.e(TAG, "❌ TTS不支持的语言: $locale, 错误码: $errorCode")
                        Log.e(TAG, "  💡 错误码含义: ${getTtsErrorCodeMeaning(errorCode)}")
                        handleInitializationError(R.string.android_tts_unsupported_language)
                    }
                }
            } else {
                Log.e(TAG, "❌ TTS初始化失败: $status")
                Log.e(TAG, "  💡 状态码含义: ${getTtsStatusMeaning(status)}")
                handleInitializationError(R.string.android_tts_error)
            }
        }
    }

    override fun speak(speechOutput: String) {
        if (initializedCorrectly) {
            lastUtteranceId += 1
            textToSpeech?.speak(
                speechOutput, TextToSpeech.QUEUE_ADD, null,
                "dicio_$lastUtteranceId"
            )
        } else {
            Toast.makeText(context, speechOutput, Toast.LENGTH_LONG).show()
        }
    }

    override fun stopSpeaking() {
        textToSpeech?.stop()
    }

    override val isSpeaking: Boolean
        get() = textToSpeech?.isSpeaking == true

    override fun runWhenFinishedSpeaking(runnable: Runnable) {
        if (isSpeaking) {
            runnablesWhenFinished.add(runnable)
        } else {
            runnable.run()
        }
    }

    override fun cleanup() {
        textToSpeech?.apply {
            shutdown()
            textToSpeech = null
        }
    }

    private fun handleInitializationError(@StringRes errorString: Int) {
        Toast.makeText(context, errorString, Toast.LENGTH_SHORT).show()
        cleanup()
    }
    
    private fun getTtsErrorCodeMeaning(errorCode: Int): String {
        return when (errorCode) {
            TextToSpeech.LANG_MISSING_DATA -> "LANG_MISSING_DATA (-1): 语言数据缺失"
            TextToSpeech.LANG_NOT_SUPPORTED -> "LANG_NOT_SUPPORTED (-2): 语言不支持"
            else -> "未知错误码: $errorCode"
        }
    }
    
    private fun getTtsStatusMeaning(status: Int): String {
        return when (status) {
            TextToSpeech.SUCCESS -> "SUCCESS: 成功"
            TextToSpeech.ERROR -> "ERROR: 一般错误"
            else -> "未知状态码: $status"
        }
    }
    
    private fun checkSystemTtsSupport() {
        Log.d(TAG, "🔍 检查系统TTS支持情况:")
        
        // 简化TTS引擎检查，避免API兼容性问题
        Log.d(TAG, "  🎛️ 检查TTS引擎可用性...")
        
        // 直接检查TTS初始化是否成功
        var testTts: TextToSpeech? = null
        testTts = TextToSpeech(context) { status ->
            when (status) {
                TextToSpeech.SUCCESS -> {
                    Log.d(TAG, "    ✅ TTS引擎可用")
                    testTts?.shutdown()
                }
                else -> {
                    Log.w(TAG, "    ❌ TTS引擎不可用，状态: $status")
                }
            }
        }
        
        // 检查默认TTS引擎
        try {
            val defaultEngine = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "tts_default_synth"
            )
            Log.d(TAG, "  🎯 默认TTS引擎: $defaultEngine")
        } catch (e: Exception) {
            Log.w(TAG, "  ⚠️ 无法获取默认TTS引擎: ${e.message}")
        }
        
        // 检查TTS数据安装状态
        checkTtsDataInstallation()
    }
    
    private fun checkTtsDataInstallation() {
        Log.d(TAG, "  📦 检查TTS数据安装状态...")
        
        // 创建临时TTS实例来检查数据
        var tempTts: TextToSpeech? = null
        tempTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                Log.d(TAG, "    ✅ TTS引擎初始化成功")
                
                tempTts?.let { tts ->
                    // 检查各种语言支持
                    val languagesToCheck = listOf(
                        Locale.ENGLISH to "英语",
                        Locale.CHINESE to "中文",
                        Locale("cn") to "中文(cn)",
                        Locale("ko") to "韩语",
                        Locale.KOREAN to "韩语(标准)"
                    )
                    
                    languagesToCheck.forEach { (locale, name) ->
                        val result = tts.isLanguageAvailable(locale)
                        val resultText = when (result) {
                            TextToSpeech.LANG_AVAILABLE -> "✅ 完全支持"
                            TextToSpeech.LANG_COUNTRY_AVAILABLE -> "🟡 国家支持"
                            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> "🟡 变体支持"
                            TextToSpeech.LANG_MISSING_DATA -> "❌ 缺少数据"
                            TextToSpeech.LANG_NOT_SUPPORTED -> "❌ 不支持"
                            else -> "❓ 未知状态($result)"
                        }
                        Log.d(TAG, "    $name ($locale): $resultText")
                    }
                    
                    tts.shutdown()
                }
            } else {
                Log.e(TAG, "    ❌ 临时TTS引擎初始化失败: $status")
            }
        }
    }
    
    /**
     * 将输入的Locale映射为TTS兼容的Locale
     */
    private fun mapToTtsCompatibleLocale(inputLocale: Locale): Locale {
        return when (inputLocale.language) {
            "cn" -> {
                Log.d(TAG, "  🔄 映射cn -> zh (中文)")
                Locale.CHINESE  // 或者 Locale("zh", "CN")
            }
            "ko" -> {
                Log.d(TAG, "  🔄 映射ko -> ko (韩语)")
                Locale.KOREAN  // 使用标准韩语Locale
            }
            else -> {
                Log.d(TAG, "  ✅ 保持原始Locale: $inputLocale")
                inputLocale
            }
        }
    }

    companion object {
        val TAG: String = AndroidTtsSpeechDevice::class.simpleName!!
    }
}
