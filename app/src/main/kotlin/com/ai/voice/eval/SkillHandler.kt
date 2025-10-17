package com.ai.voice.eval

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import com.ai.voice.di.LocaleManager
import com.ai.voice.di.SkillContextImpl
import com.ai.voice.di.SkillContextInternal
import com.ai.voice.settings.datastore.UserSettings
import com.ai.voice.settings.datastore.UserSettingsModule
import com.ai.voice.skills.calculator.CalculatorInfo
import com.ai.voice.skills.current_time.CurrentTimeInfo
import com.ai.voice.skills.device_control.DeviceControlInfo
import com.ai.voice.skills.fallback.text.TextFallbackInfo
import com.ai.voice.skills.listening.ListeningInfo
import com.ai.voice.skills.lyrics.LyricsInfo
import com.ai.voice.skills.media.MediaInfo
import com.ai.voice.skills.navigation.NavigationInfo
import com.ai.voice.skills.open.OpenInfo
import com.ai.voice.skills.search.SearchInfo
// import com.ai.voice.skills.telephone.TelephoneInfo  // 已禁用：不再需要电话和通讯录权限
import com.ai.voice.skills.timer.TimerInfo
import com.ai.voice.skills.weather.WeatherInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillHandler @Inject constructor(
    private val dataStore: DataStore<UserSettings>,
    private val localeManager: LocaleManager,
    private val skillContext: SkillContextInternal,
) {
    // TODO improve id handling (maybe just use an int that can point to an Android resource)
    val allSkillInfoList = listOf(
        WeatherInfo,
        SearchInfo,
        LyricsInfo,
        OpenInfo,
        CalculatorInfo,
        NavigationInfo,
        // TelephoneInfo,  // 已禁用：不再需要电话和通讯录权限
        TimerInfo,
        CurrentTimeInfo,
        MediaInfo,
        ListeningInfo(dataStore),
        DeviceControlInfo,
    )

    // TODO add more fallback skills (e.g. search)
    private val fallbackSkillInfoList = listOf(
        TextFallbackInfo,
    )

    private val scope = CoroutineScope(Dispatchers.Default)

    // will be null when it has not been initialized yet
    private val _enabledSkillsInfo: MutableStateFlow<List<SkillInfo>?> = MutableStateFlow(null)
    val enabledSkillsInfo: StateFlow<List<SkillInfo>?> = _enabledSkillsInfo

    private val _skillRanker = MutableStateFlow(
        // an initial dummy value, will be overwritten directly by the launched job
        SkillRanker(listOf(), buildSkillFromInfo(fallbackSkillInfoList[0]))
    )
    val skillRanker: StateFlow<SkillRanker> = _skillRanker

    init {
        scope.launch {
            localeManager.locale
                .combine(dataStore.data) { locale, data -> Pair(locale, data.enabledSkillsMap) }
                .distinctUntilChanged()
                .collectLatest { (_, enabledSkills) ->
                    // locale is not used here, because the skills directly use the sections locale

                    val newEnabledSkillsInfo = allSkillInfoList
                        .filter { skillInfo ->
                            val enabled = enabledSkills.getOrDefault(skillInfo.id, true)
                            Log.d(TAG, "🔧 技能启用检查: ${skillInfo.id} -> enabled=$enabled")
                            enabled
                        }
                        .filter { skillInfo ->
                            val available = skillInfo.isAvailable(skillContext)
                            Log.d(TAG, "🔍 技能可用性检查: ${skillInfo.id} -> available=$available")
                            available
                        }

                    _enabledSkillsInfo.value = newEnabledSkillsInfo
                    _skillRanker.value = SkillRanker(
                        newEnabledSkillsInfo.map(::buildSkillFromInfo),
                        buildSkillFromInfo(fallbackSkillInfoList[0]),
                    )
                }
        }
    }

    private fun buildSkillFromInfo(skillInfo: SkillInfo): Skill<*> {
        return skillInfo.build(skillContext)
    }

    companion object {
        private val TAG = SkillHandler::class.simpleName
        
        fun newForPreviews(context: Context): SkillHandler {
            return SkillHandler(
                UserSettingsModule.newDataStoreForPreviews(),
                LocaleManager.newForPreviews(context),
                SkillContextImpl.newForPreviews(context),
            )
        }
    }
}
