package com.ai.voice.eval

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import com.ai.voice.di.LocaleManager
import com.ai.voice.di.SkillContextImpl
import com.ai.voice.di.SkillContextInternal
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
    private val localeManager: LocaleManager,
    private val skillContext: SkillContextInternal,
) {
    val allSkillInfoList = listOf(
        PowerControlInfo,
        InputSourceControlInfo,
        AppLauncherInfo,
        WhiteboardToolsInfo,
        SystemNavigationInfo,
    )

    private val fallbackSkillInfoList = listOf(
        TextFallbackInfo,
    )

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _enabledSkillsInfo: MutableStateFlow<List<SkillInfo>?> = MutableStateFlow(null)
    val enabledSkillsInfo: StateFlow<List<SkillInfo>?> = _enabledSkillsInfo

    private val _skillRanker = MutableStateFlow(
        SkillRanker(listOf(), buildSkillFromInfo(fallbackSkillInfoList[0]))
    )
    val skillRanker: StateFlow<SkillRanker> = _skillRanker

    init {
        scope.launch {
            kotlinx.coroutines.flow.combine(
                localeManager.locale,
                localeManager.sentencesLanguage
            ) { locale, sentencesLang ->
                Pair(locale, sentencesLang)
            }.collectLatest { (_, sentencesLang) ->
                Log.d(TAG, "🔄 语言变化，重新构建技能列表: sentencesLanguage=$sentencesLang")
                val newEnabledSkillsInfo = allSkillInfoList
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
                    
                Log.d(TAG, "✅ 技能列表初始化完成，共 ${newEnabledSkillsInfo.size} 个技能")
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
                LocaleManager.newForPreviews(context),
                SkillContextImpl.newForPreviews(context),
            )
        }
    }
}
