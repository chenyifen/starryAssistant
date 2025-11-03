package com.ai.voice.skills.input_source

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.MultiLanguageStandardRecognizerSkill
import com.ai.voice.sentences.Sentences.InputSourceControl
import com.ai.voice.skills.device_control.BaseDeviceControlSkill

/**
 * 输入源切换技能
 * 
 * 支持的命令：
 * - input_source: 打开输入源选择菜单
 * - hdmi_one: 切换到HDMI 1
 * - hdmi_two: 切换到HDMI 2
 * - dp_port: 切换到DP端口
 * - front_hdmi: 切换到前面板HDMI
 * - front_usb_c: 切换到前面板USB-C
 * - ops: 切换到OPS
 */
class InputSourceSkill(
    correspondingSkillInfo: SkillInfo,
    allLanguageData: List<StandardRecognizerData<InputSourceControl>>,
) : MultiLanguageStandardRecognizerSkill<InputSourceControl>(correspondingSkillInfo, allLanguageData) {

    private val baseSkill = object : BaseDeviceControlSkill() {}

    override suspend fun generateOutput(
        ctx: SkillContext,
        inputData: InputSourceControl
    ): SkillOutput {
        return when (inputData) {
            is InputSourceControl.InputSource -> baseSkill.executeInputSource(ctx)
            is InputSourceControl.HdmiOne -> baseSkill.executeHdmiOne(ctx)
            is InputSourceControl.HdmiTwo -> baseSkill.executeHdmiTwo(ctx)
            is InputSourceControl.DpPort -> baseSkill.executeDpPort(ctx)
            is InputSourceControl.FrontHdmi -> baseSkill.executeFrontHdmi(ctx)
            is InputSourceControl.FrontUsbC -> baseSkill.executeFrontUsbC(ctx)
            is InputSourceControl.Ops -> baseSkill.executeOps(ctx)
        }
    }
}

