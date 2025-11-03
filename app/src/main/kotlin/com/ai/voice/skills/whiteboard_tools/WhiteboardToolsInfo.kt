package com.ai.voice.skills.whiteboard_tools

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.skill.Skill
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import com.ai.voice.sentences.Sentences
import com.ai.voice.util.MultiLanguageHelper

object WhiteboardToolsInfo : SkillInfo("whiteboard_tools") {
    override fun name(context: Context) =
        "白板工具"

    override fun sentenceExample(context: Context) =
        "打开白板"

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Filled.Edit)

    override fun isAvailable(ctx: SkillContext): Boolean {
        return true
    }

    override fun build(ctx: SkillContext): Skill<*> {
        val allData = MultiLanguageHelper.loadAllLanguageData(
            "whiteboard_tools",
            Sentences.WhiteboardTools::get
        )
        
        if (allData.isEmpty()) {
            throw IllegalStateException("WhiteboardTools skill: No language data available")
        }
        
        return WhiteboardToolsSkill(WhiteboardToolsInfo, allData)
    }
}

