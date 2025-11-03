package com.ai.voice.skills.system_navigation

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.MultiLanguageStandardRecognizerSkill
import com.ai.voice.sentences.Sentences.SystemNavigation
import com.ai.voice.skills.device_control.BaseDeviceControlSkill

/**
 * 系统导航和功能技能
 * 
 * 支持的命令：
 * - home_screen: 返回主屏幕
 * - go_back: 返回上一页
 * - screenshot: 截图
 * - note_mode: 切换到笔记模式
 * - window_mode: 切换到窗口模式
 * - wifi_connect: 连接WiFi
 */
class SystemNavigationSkill(
    correspondingSkillInfo: SkillInfo,
    allLanguageData: List<StandardRecognizerData<SystemNavigation>>,
) : MultiLanguageStandardRecognizerSkill<SystemNavigation>(correspondingSkillInfo, allLanguageData) {

    private val baseSkill = object : BaseDeviceControlSkill() {}

    override suspend fun generateOutput(
        ctx: SkillContext,
        inputData: SystemNavigation
    ): SkillOutput {
        return when (inputData) {
            is SystemNavigation.HomeScreen -> baseSkill.executeHomeScreen(ctx)
            is SystemNavigation.GoBack -> baseSkill.executeGoBack(ctx)
            is SystemNavigation.Screenshot -> baseSkill.executeScreenshot(ctx)
            is SystemNavigation.NoteMode -> baseSkill.executeNoteMode(ctx)
            is SystemNavigation.WindowMode -> baseSkill.executeWindowMode(ctx)
            is SystemNavigation.WifiConnect -> baseSkill.executeWifiConnect(ctx)
        }
    }
}

