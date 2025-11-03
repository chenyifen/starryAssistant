package com.ai.voice.skills.input_source

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Input
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.skill.Skill
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import com.ai.voice.sentences.Sentences
import com.ai.voice.util.MultiLanguageHelper

object InputSourceControlInfo : SkillInfo("input_source_control") {
    override fun name(context: Context) =
        "输入源切换"

    override fun sentenceExample(context: Context) =
        "切换到HDMI一"

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Filled.Input)

    override fun isAvailable(ctx: SkillContext): Boolean {
        return true
    }

    override fun build(ctx: SkillContext): Skill<*> {
        val allData = MultiLanguageHelper.loadAllLanguageData(
            "input_source_control",
            Sentences.InputSourceControl::get
        )
        
        if (allData.isEmpty()) {
            throw IllegalStateException("InputSourceControl skill: No language data available")
        }
        
        return InputSourceSkill(InputSourceControlInfo, allData)
    }
}

