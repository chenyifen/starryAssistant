package com.ai.voice.skills.app_launcher

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.skill.Skill
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import com.ai.voice.sentences.Sentences
import com.ai.voice.util.MultiLanguageHelper

object AppLauncherInfo : SkillInfo("app_launcher") {
    override fun name(context: Context) =
        "应用启动"

    override fun sentenceExample(context: Context) =
        "打开浏览器"

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Filled.Apps)

    override fun isAvailable(ctx: SkillContext): Boolean {
        return true
    }

    override fun build(ctx: SkillContext): Skill<*> {
        val allData = MultiLanguageHelper.loadAllLanguageData(
            "app_launcher",
            Sentences.AppLauncher::get
        )
        
        if (allData.isEmpty()) {
            throw IllegalStateException("AppLauncher skill: No language data available")
        }
        
        return AppLauncherSkill(AppLauncherInfo, allData)
    }
}

