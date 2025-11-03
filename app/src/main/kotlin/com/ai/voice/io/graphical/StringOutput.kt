package com.ai.voice.io.graphical

import org.dicio.skill.context.SkillContext

/**
 * 简单的字符串输出实现
 */
class StringOutput(
    private val text: String
) : HeadlineSpeechSkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String = text
}

