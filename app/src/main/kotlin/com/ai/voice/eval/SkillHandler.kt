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
import com.ai.voice.skills.power_control.PowerControlInfo
import com.ai.voice.skills.input_source.InputSourceControlInfo
import com.ai.voice.skills.app_launcher.AppLauncherInfo
import com.ai.voice.skills.whiteboard_tools.WhiteboardToolsInfo
import com.ai.voice.skills.system_navigation.SystemNavigationInfo
import com.ai.voice.skills.fallback.text.TextFallbackInfo
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
        PowerControlInfo,
        InputSourceControlInfo,
        AppLauncherInfo,
        WhiteboardToolsInfo,
        SystemNavigationInfo,
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
                    
                    // 🔥 打印和保存技能列表（用于调试和测试）
                    try {
                        val context = skillContext.android
                        com.ai.voice.util.AutoTestLogger.logSkillList(context, allSkillInfoList)
                        com.ai.voice.util.AutoTestLogger.saveSkillListToFile(context, allSkillInfoList)
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ 打印/保存技能列表失败", e)
                    }
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
