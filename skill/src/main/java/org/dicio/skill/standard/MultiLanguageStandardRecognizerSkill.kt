package org.dicio.skill.standard

import android.util.Log
import org.dicio.skill.skill.Skill
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Score
import org.dicio.skill.skill.SkillInfo

/**
 * 支持多语言匹配的标准识别器技能基类
 * 
 * 允许技能在匹配时尝试所有已加载的语言，而不仅仅是当前用户选择的语言。
 * 这使得用户可以混合使用不同语言的命令。
 * 
 * @param T 技能输入数据的类型
 * @param correspondingSkillInfo 技能信息对象
 * @param allLanguageData 所有支持语言的句子数据列表
 */
abstract class MultiLanguageStandardRecognizerSkill<T>(
    correspondingSkillInfo: SkillInfo,
    private val allLanguageData: List<StandardRecognizerData<T>>,
) : Skill<T>(correspondingSkillInfo, allLanguageData.firstOrNull()?.specificity 
    ?: throw IllegalArgumentException("allLanguageData must not be empty")) {

    companion object {
        private const val TAG = "MultiLanguageSkill"
    }

    /**
     * 覆盖父类的 score 方法以支持多语言匹配
     * 
     * 遍历所有已加载的语言数据，计算每种语言的匹配分数，
     * 返回得分最高的匹配结果。
     */
    override fun score(ctx: SkillContext, input: String): Pair<Score, T> {
        var bestResult: Pair<Score, T>? = null
        var bestScore = 0.0
        
        val skillId = correspondingSkillInfo.id
        Log.d(TAG, "🌐 [$skillId] 多语言匹配开始: '$input'")
        
        for ((index, data) in allLanguageData.withIndex()) {
            try {
                val result = data.score(input)
                val score = result.first.scoreIn01Range().toDouble()
                
                Log.d(TAG, "  [$skillId] 语言${index + 1} 匹配分数: $score (raw=${result.first})")
                
                if (bestResult == null || score > bestScore) {
                    bestResult = result
                    bestScore = score
                }
            } catch (e: Exception) {
                Log.w(TAG, "  [$skillId] 语言${index + 1} 匹配失败: ${e.message}", e)
            }
        }
        
        Log.d(TAG, "✅ [$skillId] 最佳匹配分数: $bestScore")
        
        if (bestResult == null) {
            Log.e(TAG, "❌ [$skillId] 所有语言匹配失败，输入: '$input', 语言数据数量: ${allLanguageData.size}")
            throw IllegalStateException("No match found for input: $input")
        }
        
        return bestResult
    }
}

