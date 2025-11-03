package com.ai.voice.skills.power_control

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.MultiLanguageStandardRecognizerSkill
import com.ai.voice.sentences.Sentences.PowerControl
import com.ai.voice.skills.device_control.BaseDeviceControlSkill

/**
 * 电源和音量控制技能
 * 
 * 支持的命令：
 * - power_off: 关机
 * - power_on: 开机
 * - volume_up: 音量增加
 * - volume_down: 音量减少
 * - mute_on: 静音
 */
class PowerControlSkill(
    correspondingSkillInfo: SkillInfo,
    allLanguageData: List<StandardRecognizerData<PowerControl>>,
) : MultiLanguageStandardRecognizerSkill<PowerControl>(correspondingSkillInfo, allLanguageData) {

    private val baseSkill = object : BaseDeviceControlSkill() {
        // 使用匿名对象创建基类实例
    }

    override suspend fun generateOutput(
        ctx: SkillContext,
        inputData: PowerControl
    ): SkillOutput {
        return when (inputData) {
            is PowerControl.PowerOff -> baseSkill.executePowerOff(ctx)
            is PowerControl.PowerOn -> baseSkill.executePowerOn(ctx)
            is PowerControl.VolumeUp -> baseSkill.executeVolumeUp(ctx)
            is PowerControl.VolumeDown -> baseSkill.executeVolumeDown(ctx)
            is PowerControl.MuteOn -> baseSkill.executeMute(ctx)
        }
    }
}

