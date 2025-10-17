package com.ai.voice.skills.media

import org.dicio.skill.context.SkillContext
import com.ai.voice.R
import com.ai.voice.io.graphical.HeadlineSpeechSkillOutput
import com.ai.voice.sentences.Sentences.Media
import com.ai.voice.util.getString

class MediaOutput(
    private val performedAction: Media?
) : HeadlineSpeechSkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String = when (performedAction) {
        null -> ctx.getString(R.string.skill_media_no_media_session)
        is Media.Play -> ctx.getString(R.string.skill_media_playing)
        is Media.Pause -> ctx.getString(R.string.skill_media_pausing)
        is Media.Previous -> ctx.getString(R.string.skill_media_previous)
        is Media.Next -> ctx.getString(R.string.skill_media_next)
    }
}
