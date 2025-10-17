package com.ai.voice.skills.calculator

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import com.ai.voice.R
import com.ai.voice.io.graphical.Headline
import com.ai.voice.io.graphical.Subtitle
import com.ai.voice.util.getString

class CalculatorOutput(
    private val result: String?,
    private val spokenResult: String,
    private val inputInterpretation: String,
) : SkillOutput {
    override fun getSpeechOutput(ctx: SkillContext) = if (result == null) {
        ctx.getString(R.string.skill_calculator_could_not_calculate)
    } else {
        spokenResult
    }

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        if (result == null) {
            Headline(text = getSpeechOutput(ctx))
        } else {
            Column {
                Subtitle(text = inputInterpretation)
                Headline(text = result)
            }
        }
    }
}
