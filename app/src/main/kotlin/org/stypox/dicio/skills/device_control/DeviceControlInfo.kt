package org.stypox.dicio.skills.device_control

import android.content.Context
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.skill.Skill
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.Specificity
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardScore
import org.stypox.dicio.R
import org.stypox.dicio.sentences.Sentences

object DeviceControlInfo : SkillInfo("device_control") {
    private const val TAG = "DeviceControlInfo"
    
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
        // 支持的语言列表（中英韩）
        val supportedLanguages = listOf("en", "zh-CN", "ko")
        
        // 收集所有语言的句子数据
        val allSentencesData = mutableListOf<StandardRecognizerData<Sentences.DeviceControl>>()
        
        for (lang in supportedLanguages) {
            val data = Sentences.DeviceControl[lang]
            if (data != null) {
                allSentencesData.add(data)
                Log.d(TAG, "✅ 加载设备控制语言: $lang")
            } else {
                Log.w(TAG, "⚠️ 未找到设备控制语言: $lang")
            }
        }
        
        if (allSentencesData.isEmpty()) {
            // 如果没有加载到任何语言，回退到当前语言
            Log.w(TAG, "❌ 未加载任何语言，回退到当前语言: ${ctx.sentencesLanguage}")
            val fallbackData = Sentences.DeviceControl[ctx.sentencesLanguage]
                ?: throw IllegalStateException("DeviceControl skill not available for language ${ctx.sentencesLanguage}")
            return DeviceControlSkill(DeviceControlInfo, fallbackData, isMultiLanguage = false)
        }
        
        Log.d(TAG, "🌐 设备控制技能支持 ${allSentencesData.size} 种语言: $supportedLanguages")
        
        // 传入所有语言的数据
        return DeviceControlSkill(DeviceControlInfo, allSentencesData, isMultiLanguage = true)
    }
}

