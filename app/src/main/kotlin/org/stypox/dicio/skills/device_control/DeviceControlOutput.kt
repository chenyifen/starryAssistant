package org.stypox.dicio.skills.device_control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.context.SkillContext
import org.stypox.dicio.R

data class DeviceControlOutput(
    val command: String,
    val success: Boolean,
    val message: String? = null
) : SkillOutput {

    override fun getSpeechOutput(ctx: SkillContext): String {
        return if (success) {
            message ?: "Device control command executed"
        } else {
            message ?: "Failed to execute device control command"
        }
    }

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        val modifier = Modifier
        Column(
            modifier = modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.skill_device_control_command),
                style = MaterialTheme.typography.titleMedium
            )
            
            Text(
                text = command,
                style = MaterialTheme.typography.bodyLarge
            )
            
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (success) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }
    }
}

