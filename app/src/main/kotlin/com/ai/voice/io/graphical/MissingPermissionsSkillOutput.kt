package com.ai.voice.io.graphical

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import com.ai.voice.R
import com.ai.voice.util.commaJoinPermissions
import com.ai.voice.util.getString

class MissingPermissionsSkillOutput(
    private val skill: SkillInfo
) : SkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String =
        ctx.getString(
            R.string.eval_missing_permissions,
            skill.name(ctx.android),
            commaJoinPermissions(ctx.android, skill.neededPermissions)
        )

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = getSpeechOutput(ctx))
        }
    }
}
