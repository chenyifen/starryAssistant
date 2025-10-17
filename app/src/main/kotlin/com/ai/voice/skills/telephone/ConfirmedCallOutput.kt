package com.ai.voice.skills.telephone

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import com.ai.voice.R
import com.ai.voice.io.graphical.Headline
import com.ai.voice.util.getString

class ConfirmedCallOutput(
    private val number: String?,
) : SkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String = if (number == null) {
        ctx.getString(R.string.skill_telephone_not_calling)
    } else {
        "" // do not speak anything since a call has just started
    }

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        Headline(
            text = if (number == null) {
                stringResource(R.string.skill_telephone_not_calling)
            } else {
                stringResource(R.string.skill_telephone_calling, number)
            }
        )
    }
}
