package com.ai.voice.skills.app_launcher

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.MultiLanguageStandardRecognizerSkill
import com.ai.voice.sentences.Sentences.AppLauncher
import com.ai.voice.skills.device_control.BaseDeviceControlSkill

/**
 * 应用启动技能
 * 
 * 支持的命令：
 * - google: 打开Google
 * - browser: 打开浏览器
 * - play_store: 打开Play商店
 * - youtube: 打开YouTube
 * - settings: 打开设置
 * - recorder: 打开录音机
 * - eshare: 打开EShare
 * - camera: 打开相机
 * - finder: 打开文件管理器
 */
class AppLauncherSkill(
    correspondingSkillInfo: SkillInfo,
    allLanguageData: List<StandardRecognizerData<AppLauncher>>,
) : MultiLanguageStandardRecognizerSkill<AppLauncher>(correspondingSkillInfo, allLanguageData) {

    private val baseSkill = object : BaseDeviceControlSkill() {}

    override suspend fun generateOutput(
        ctx: SkillContext,
        inputData: AppLauncher
    ): SkillOutput {
        return when (inputData) {
            is AppLauncher.Google -> baseSkill.executeGoogle(ctx)
            is AppLauncher.Browser -> baseSkill.executeBrowser(ctx)
            is AppLauncher.PlayStore -> baseSkill.executePlayStore(ctx)
            is AppLauncher.Youtube -> baseSkill.executeYoutube(ctx)
            is AppLauncher.Settings -> baseSkill.executeSettings(ctx)
            is AppLauncher.Recorder -> baseSkill.executeRecorder(ctx)
            is AppLauncher.Eshare -> baseSkill.executeEshare(ctx)
            is AppLauncher.Camera -> baseSkill.executeCamera(ctx)
            is AppLauncher.Finder -> baseSkill.executeFinder(ctx)
        }
    }
}

