package com.ai.voice.skills.whiteboard_tools

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.MultiLanguageStandardRecognizerSkill
import com.ai.voice.sentences.Sentences.WhiteboardTools
import com.ai.voice.skills.device_control.BaseDeviceControlSkill

/**
 * 白板工具技能
 * 
 * 支持的命令：
 * - whiteboard: 打开白板
 * - save_whiteboard: 保存白板
 * - red_pen: 切换到红笔
 * - blue_pen: 切换到蓝笔
 * - white_pen: 切换到白笔
 * - black_pen: 切换到黑笔
 * - eraser: 切换到橡皮擦
 * - delete_all: 清空白板
 * - highlight_pen: 切换到荧光笔
 * - fountain_pen: 切换到钢笔
 * - brush_pen: 切换到毛笔
 */
class WhiteboardToolsSkill(
    correspondingSkillInfo: SkillInfo,
    allLanguageData: List<StandardRecognizerData<WhiteboardTools>>,
) : MultiLanguageStandardRecognizerSkill<WhiteboardTools>(correspondingSkillInfo, allLanguageData) {

    private val baseSkill = object : BaseDeviceControlSkill() {}

    override suspend fun generateOutput(
        ctx: SkillContext,
        inputData: WhiteboardTools
    ): SkillOutput {
        return when (inputData) {
            is WhiteboardTools.Whiteboard -> baseSkill.executeWhiteboard(ctx)
            is WhiteboardTools.SaveWhiteboard -> baseSkill.executeSaveWhiteboard(ctx)
            is WhiteboardTools.RedPen -> baseSkill.executeRedPen(ctx)
            is WhiteboardTools.BluePen -> baseSkill.executeBluePen(ctx)
            is WhiteboardTools.WhitePen -> baseSkill.executeWhitePen(ctx)
            is WhiteboardTools.BlackPen -> baseSkill.executeBlackPen(ctx)
            is WhiteboardTools.Eraser -> baseSkill.executeEraser(ctx)
            is WhiteboardTools.DeleteAll -> baseSkill.executeDeleteAll(ctx)
            is WhiteboardTools.HighlightPen -> baseSkill.executeHighlightPen(ctx)
            is WhiteboardTools.FountainPen -> baseSkill.executeFountainPen(ctx)
            is WhiteboardTools.BrushPen -> baseSkill.executeBrushPen(ctx)
        }
    }
}

