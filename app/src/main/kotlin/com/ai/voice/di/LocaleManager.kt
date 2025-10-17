package com.ai.voice.di

import android.content.Context
import android.util.Log
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.ai.voice.sentences.Sentences
import com.ai.voice.settings.datastore.Language
import com.ai.voice.settings.datastore.UserSettings
import com.ai.voice.settings.datastore.UserSettingsModule.Companion.newDataStoreForPreviews
import com.ai.voice.util.LocaleUtils
import com.ai.voice.util.distinctUntilChangedBlockingFirst
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chooses locale and localeString from languages configured in the user's system, making sure in
 * particular that skill examples exist for the chosen locale, because otherwise the LLM wouldn't
 * work.
 */
@Singleton
class LocaleManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    dataStore: DataStore<UserSettings>,
) {
    // We obtain the system locale list when the app starts (which is also when `LocaleManager` is
    // instantiated, since after `setLocale()` will be called in `BaseActivity`, the
    // `appContext.resources.configuration` will not contain the system locale anymore, but
    // only the newly set locale.
    private val systemLocaleList: LocaleListCompat =
        ConfigurationCompat.getLocales(appContext.resources.configuration)

    private val scope = CoroutineScope(Dispatchers.Default)
    private val _locale: MutableStateFlow<Locale>
    val locale: StateFlow<Locale>
    private val _sentencesLanguage: MutableStateFlow<String>
    val sentencesLanguage: StateFlow<String>

    init {
        // run blocking, because we can't start the app if we don't know the language
        val (firstLanguage, nextLanguageFlow) = dataStore.data
            .map { it.language }
            .distinctUntilChangedBlockingFirst()

        val initialResolutionResult = getSentencesLocale(firstLanguage)
        _locale = MutableStateFlow(initialResolutionResult.availableLocale)
        locale = _locale
        _sentencesLanguage = MutableStateFlow(initialResolutionResult.supportedLocaleString)
        sentencesLanguage = _sentencesLanguage

        scope.launch {
            nextLanguageFlow.collect { newLanguage ->
                val resolutionResult = getSentencesLocale(newLanguage)
                _locale.value = resolutionResult.availableLocale
                _sentencesLanguage.value = resolutionResult.supportedLocaleString
            }
        }
    }

    private fun getSentencesLocale(language: Language): LocaleUtils.LocaleResolutionResult {
        Log.d(TAG, "🌐 LocaleManager.getSentencesLocale() - 语言解析调试:")
        Log.d(TAG, "  📥 输入Language: $language")
        
        val availableLocales = getAvailableLocalesFromLanguage(language)
        Log.d(TAG, "  🔄 解析后的可用Locales: ${(0 until availableLocales.size()).map { availableLocales[it] }}")
        Log.d(TAG, "  📚 Sentences支持的语言: ${Sentences.languages}")
        
        return try {
            val result = LocaleUtils.resolveSupportedLocale(
                availableLocales,
                Sentences.languages
            )
            Log.d(TAG, "  ✅ 解析成功: availableLocale=${result.availableLocale}, supportedLocaleString=${result.supportedLocaleString}")
            Log.d(TAG, "  🎯 最终传给应用的Locale: ${result.availableLocale}")
            result
        } catch (e: LocaleUtils.UnsupportedLocaleException) {
            Log.w(TAG, "❌ 当前语言不支持，回退到英语: ${e.message}")
            Log.w(TAG, "  💡 可能的原因: 语言映射不匹配或Sentences中缺少对应语言")
            Log.w(TAG, "  🔄 回退到英语")
            // TODO ask the user to manually choose a locale instead of defaulting to english
            LocaleUtils.LocaleResolutionResult(
                availableLocale = Locale.ENGLISH,
                supportedLocaleString = "en",
            )
        }
    }

    private fun getAvailableLocalesFromLanguage(language: Language): LocaleListCompat {
        return when (language) {
            Language.LANGUAGE_SYSTEM,
            Language.UNRECOGNIZED -> {
                systemLocaleList // the original system locale list from when the app started
            }
            // 特殊处理中文语言映射
            Language.LANGUAGE_ZH_CN -> {
                Log.d(TAG, "  🔄 特殊处理中文简体: LANGUAGE_ZH_CN -> cn")
                LocaleListCompat.create(Locale("cn"))
            }
            Language.LANGUAGE_ZH_TW -> {
                Log.d(TAG, "  🔄 特殊处理中文繁体: LANGUAGE_ZH_TW -> cn")
                LocaleListCompat.create(Locale("cn"))
            }
            // 特殊处理韩语映射
            Language.LANGUAGE_KO -> {
                Log.d(TAG, "  🔄 特殊处理韩语: LANGUAGE_KO -> ko")
                LocaleListCompat.create(Locale("ko"))
            }
            else -> {
                // exploit the fact that each `Language` is of the form LANGUAGE or LANGUAGE_COUNTRY
                LocaleListCompat.create(LocaleUtils.parseLanguageCountry(language.toString()))
            }
        }
    }

    companion object {
        val TAG = LocaleManager::class.simpleName

        fun newForPreviews(context: Context): LocaleManager {
            return LocaleManager(
                context,
                newDataStoreForPreviews(),
            )
        }
    }
}

/**
 * This module allows getting an instance of [LocaleManager] outside of @Inject using
 * `EntryPointAccessors.fromApplication()`, which can be used before an activity's `onCreate()`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface LocaleManagerModule {
    fun getLocaleManager(): LocaleManager
}
