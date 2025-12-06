package com.ai.voice.util

import com.ai.voice.di.SkillContextInternal
import com.ai.voice.ui.floating.state.SimpleResult
import com.ai.voice.ui.floating.state.SimpleResultBuilder
import org.dicio.skill.skill.SkillOutput
import javax.inject.Inject

object SkillOutputConverter {
    
    fun convertSkillOutputToSimpleResult(
        skillOutput: SkillOutput,
        skillContext: SkillContextInternal
    ): SimpleResult {
        return try {
            val speechText = skillOutput.getSpeechOutput(skillContext)
            SimpleResultBuilder.info("命令执行", speechText)
        } catch (e: Exception) {
            SimpleResultBuilder.error("技能处理错误: ${e.message}")
        }
    }
}
