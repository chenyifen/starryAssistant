package com.ai.voice.di

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import java.util.Locale

interface SkillContextInternal : SkillContext {
    // allows modifying this value
    override var previousOutput: SkillOutput?
    
    /**
     * 🆕 ASR识别的语言
     * 用于技能根据用户输入语言返回对应的回复文本
     */
    var asrLocale: Locale?
}