package org.stypox.dicio.skills.fallback.text

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.preference.PreferenceFragmentCompat
import org.dicio.skill.skill.Skill
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R

object TextFallbackInfo : SkillInfo("text") {
    override fun name(context: Context) =
        context.getString(R.string.skill_fallback_name_text)

    override fun sentenceExample(context: Context) =
        ""

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.Warning)

    override fun isAvailable(ctx: SkillContext): Boolean {
        return true
    }

    override fun build(ctx: SkillContext): Skill<*> {
        return TextFallbackSkill(TextFallbackInfo)
    }
}
