package com.ai.voice.skills.system_navigation

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.skill.Skill
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import com.ai.voice.sentences.Sentences
import com.ai.voice.util.MultiLanguageHelper

object SystemNavigationInfo : SkillInfo("system_navigation") {
    override fun name(context: Context) =
        "系统导航"

    override fun sentenceExample(context: Context) =
        "返回主屏幕"

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Filled.Home)

    override fun isAvailable(ctx: SkillContext): Boolean {
        return true
    }

    override fun build(ctx: SkillContext): Skill<*> {
        val allData = MultiLanguageHelper.loadAllLanguageData(
            "system_navigation",
            Sentences.SystemNavigation::get
        )
        
        if (allData.isEmpty()) {
            throw IllegalStateException("SystemNavigation skill: No language data available")
        }
        
        return SystemNavigationSkill(SystemNavigationInfo, allData)
    }
}

