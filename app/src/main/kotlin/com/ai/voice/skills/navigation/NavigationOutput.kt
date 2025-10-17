package com.ai.voice.skills.navigation

import org.dicio.skill.context.SkillContext
import com.ai.voice.R
import com.ai.voice.io.graphical.HeadlineSpeechSkillOutput
import com.ai.voice.util.getString

class NavigationOutput(
    private val where: String?,
) : HeadlineSpeechSkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String = if (where.isNullOrBlank()) {
        ctx.getString(R.string.skill_navigation_specify_where)
    } else {
        ctx.getString(R.string.skill_navigation_navigating_to, where)
    }
}
