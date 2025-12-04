package com.ai.voice.di

import android.content.Context
import android.util.Log
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.ai.voice.sentences.Sentences
import com.ai.voice.util.LocaleUtils
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 语言管理器 - 简化版，使用韩语作为默认语言
 */
@Singleton
class LocaleManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    private val systemLocaleList: LocaleListCompat =
        ConfigurationCompat.getLocales(appContext.resources.configuration)

    private val _locale: MutableStateFlow<Locale>
    val locale: StateFlow<Locale>
    private val _sentencesLanguage: MutableStateFlow<String>
    val sentencesLanguage: StateFlow<String>

    init {
        // 默认使用韩语
        val resolutionResult = getSentencesLocale()
        _locale = MutableStateFlow(resolutionResult.availableLocale)
        locale = _locale
        _sentencesLanguage = MutableStateFlow(resolutionResult.supportedLocaleString)
        sentencesLanguage = _sentencesLanguage
    }

    private fun getSentencesLocale(): LocaleUtils.LocaleResolutionResult {
        Log.d(TAG, "🌐 LocaleManager - 使用默认韩语")
        
        // 默认使用韩语
        val availableLocales = LocaleListCompat.create(Locale.KOREAN)
        
        return try {
            LocaleUtils.resolveSupportedLocale(availableLocales, Sentences.languages)
        } catch (e: LocaleUtils.UnsupportedLocaleException) {
            Log.w(TAG, "❌ 韩语不支持，回退到英语")
            LocaleUtils.LocaleResolutionResult(
                availableLocale = Locale.ENGLISH,
                supportedLocaleString = "en",
            )
        }
    }

    companion object {
        val TAG = LocaleManager::class.simpleName

        fun newForPreviews(context: Context): LocaleManager {
            return LocaleManager(context)
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LocaleManagerModule {
    fun getLocaleManager(): LocaleManager
}
