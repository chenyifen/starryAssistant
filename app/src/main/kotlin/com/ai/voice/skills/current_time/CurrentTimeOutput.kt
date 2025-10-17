package com.ai.voice.skills.current_time

import org.dicio.skill.context.SkillContext
import com.ai.voice.R
import com.ai.voice.io.graphical.HeadlineSpeechSkillOutput
import com.ai.voice.util.getString

class CurrentTimeOutput(
    private val timeStr: String,
) : HeadlineSpeechSkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String =
        ctx.getString(R.string.skill_time_current_time, timeStr)
}
