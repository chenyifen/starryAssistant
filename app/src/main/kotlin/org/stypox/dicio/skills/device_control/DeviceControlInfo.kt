package org.stypox.dicio.skills.device_control

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.skill.Skill
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.util.MultiLanguageHelper

object DeviceControlInfo : SkillInfo("device_control") {
    override fun name(context: Context) =
        context.getString(R.string.skill_name_device_control)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_device_control)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Filled.Devices)

    override fun isAvailable(ctx: SkillContext): Boolean {
        // 设备控制技能总是可用，因为我们会加载所有支持的语言
        return true
    }

    override fun build(ctx: SkillContext): Skill<*> {
        val allData = MultiLanguageHelper.loadAllLanguageData(
            "device_control",
            Sentences.DeviceControl::get
        )
        
        if (allData.isEmpty()) {
            throw IllegalStateException("DeviceControl skill: No language data available")
        }
        
        return DeviceControlSkill(DeviceControlInfo, allData)
    }
}

