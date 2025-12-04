package com.ai.voice.io.graphical

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import okio.IOException
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import com.ai.voice.R
import com.ai.voice.di.SkillContextImpl
import com.ai.voice.error.ErrorInfo
import com.ai.voice.error.ErrorUtils
import com.ai.voice.error.ExceptionUtils
import com.ai.voice.util.getString

data class ErrorSkillOutput(
    private val throwable: Throwable,
    private val fromSkillEvaluation: Boolean,
) : SkillOutput {
    private val isNetworkError = ExceptionUtils.isNetworkError(throwable)

    override fun getSpeechOutput(ctx: SkillContext): String =
        if (isNetworkError) {
            ctx.getString(R.string.eval_network_error_description)
        } else if (fromSkillEvaluation) {
            ctx.getString(R.string.eval_fatal_error)
        } else {
            ctx.getString(R.string.error_sorry)
        }

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        if (isNetworkError) {
            Column {
                Headline(text = stringResource(id = R.string.eval_network_error))
                Subtitle(text = stringResource(id = R.string.eval_network_error_description))
            }
        } else {
            val errorMessage = listOf(
                throwable.localizedMessage,
                throwable.message,
                throwable::class.simpleName,
                throwable::class.qualifiedName
            ).firstNotNullOfOrNull {
                it?.takeIf { it.isNotBlank() }
            } ?: throwable.toString()

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Headline(text = getSpeechOutput(ctx))
                Spacer(modifier = Modifier.height(4.dp))
                Subtitle(text = errorMessage)
                Spacer(modifier = Modifier.height(4.dp))

                val context = LocalContext.current
                ElevatedButton(
                    onClick = {
                        ErrorUtils.openActivity(
                            context,
                            ErrorInfo(
                                throwable,
                                if (fromSkillEvaluation) "Skill evaluation" else "Evaluation"
                            )
                        )
                    },
                ) {
                    Text(text = stringResource(R.string.error_report))
                }
            }
        }
    }
}

@Preview
@Composable
private fun NetworkErrorNotSkillPreview() {
    ErrorSkillOutput(
        IOException("Whatever error"),
        false,
    ).GraphicalOutput(ctx = SkillContextImpl.newForPreviews(LocalContext.current))
}

@Preview
@Composable
private fun NetworkErrorFromSkillPreview() {
    ErrorSkillOutput(
        IOException(),
        true,
    ).GraphicalOutput(ctx = SkillContextImpl.newForPreviews(LocalContext.current))
}

@Preview
@Composable
private fun OtherErrorNotSkillPreview() {
    ErrorSkillOutput(
        Exception("Whatever error"),
        false,
    ).GraphicalOutput(ctx = SkillContextImpl.newForPreviews(LocalContext.current))
}

@Preview
@Composable
private fun OtherErrorFromSkillPreview() {
    ErrorSkillOutput(
        Exception(),
        true,
    ).GraphicalOutput(ctx = SkillContextImpl.newForPreviews(LocalContext.current))
}
