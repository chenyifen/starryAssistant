package com.ai.voice.skills.power_control

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Power
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.skill.Skill
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import com.ai.voice.R
import com.ai.voice.sentences.Sentences
import com.ai.voice.util.MultiLanguageHelper

object PowerControlInfo : SkillInfo("power_control") {
    override fun name(context: Context) =
        "电源音量控制"

    override fun sentenceExample(context: Context) =
        "音量上升"

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Filled.Power)

    override fun isAvailable(ctx: SkillContext): Boolean {
        return true
    }

    override fun build(ctx: SkillContext): Skill<*> {
        val allData = MultiLanguageHelper.loadAllLanguageData(
            "power_control",
            Sentences.PowerControl::get
        )
        
        if (allData.isEmpty()) {
            throw IllegalStateException("PowerControl skill: No language data available")
        }
        
        return PowerControlSkill(PowerControlInfo, allData)
    }
}

