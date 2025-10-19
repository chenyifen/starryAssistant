package com.ai.voice.skills.fallback.text

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import com.ai.voice.util.RecognizeEverythingSkill

class TextFallbackSkill(correspondingSkillInfo: SkillInfo) :
    RecognizeEverythingSkill(correspondingSkillInfo) {
    override suspend fun generateOutput(ctx: SkillContext, inputData: String): SkillOutput {
        return TextFallbackOutput(
            // 用户配置：总是回到IDLE等待唤醒，不自动重新录音
            // 原逻辑：第一次askToRepeat=true（重新录音），第二次askToRepeat=false（回到IDLE）
            askToRepeat = false
        )
    }
}
