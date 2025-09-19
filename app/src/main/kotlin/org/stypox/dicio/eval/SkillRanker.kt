package org.stypox.dicio.eval

import android.util.Log
import org.dicio.skill.skill.Skill
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Specificity
import org.dicio.skill.util.CleanableUp
import java.util.Stack

class SkillRanker(
    defaultSkillBatch: List<Skill<*>>,
    private var fallbackSkill: Skill<*>
) : CleanableUp {

    private class SkillBatch(skills: List<Skill<*>>) {
        // all of the skills by specificity category (high, medium and low)
        private val highSkills: MutableList<Skill<*>> = ArrayList()
        private val mediumSkills: MutableList<Skill<*>> = ArrayList()
        private val lowSkills: MutableList<Skill<*>> = ArrayList()

        init {
            for (skill in skills) {
                when (skill.specificity) {
                    Specificity.HIGH -> highSkills.add(skill)
                    Specificity.MEDIUM -> mediumSkills.add(skill)
                    Specificity.LOW -> lowSkills.add(skill)
                }
            }
        }

        fun getBest(ctx: SkillContext, input: String): SkillWithResult<*>? {
            Log.d(TAG, "🎯 SkillBatch.getBest() 开始评估输入: '$input'")
            Log.d(TAG, "📊 技能数量 - High: ${highSkills.size}, Medium: ${mediumSkills.size}, Low: ${lowSkills.size}")
            
            // first round: considering only high-priority skills
            val bestHigh = getBestForSpecificity(ctx, highSkills, input)
            Log.d(TAG, "🔴 第一轮(High): ${bestHigh?.let { "${it.skill.correspondingSkillInfo.id} (${it.score.scoreIn01Range()})" } ?: "无匹配"}")
            if (bestHigh != null && bestHigh.score.scoreIn01Range() > HIGH_THRESHOLD_1) {
                Log.d(TAG, "✅ 第一轮通过，阈值: $HIGH_THRESHOLD_1")
                return bestHigh
            }

            // second round: considering both medium- and high-priority skills
            val bestMedium = getBestForSpecificity(ctx, mediumSkills, input)
            Log.d(TAG, "🟡 第二轮(Medium): ${bestMedium?.let { "${it.skill.correspondingSkillInfo.id} (${it.score.scoreIn01Range()})" } ?: "无匹配"}")
            if (bestMedium != null && bestMedium.score.scoreIn01Range() > MEDIUM_THRESHOLD_2) {
                Log.d(TAG, "✅ 第二轮Medium通过，阈值: $MEDIUM_THRESHOLD_2")
                return bestMedium
            } else if (bestHigh != null && bestHigh.score.scoreIn01Range() > HIGH_THRESHOLD_2) {
                Log.d(TAG, "✅ 第二轮High通过，阈值: $HIGH_THRESHOLD_2")
                return bestHigh
            }

            // third round: all skills are considered
            val bestLow = getBestForSpecificity(ctx, lowSkills, input)
            Log.d(TAG, "🟢 第三轮(Low): ${bestLow?.let { "${it.skill.correspondingSkillInfo.id} (${it.score.scoreIn01Range()})" } ?: "无匹配"}")
            if (bestLow != null && bestLow.score.scoreIn01Range() > LOW_THRESHOLD_3) {
                Log.d(TAG, "✅ 第三轮Low通过，阈值: $LOW_THRESHOLD_3")
                return bestLow
            } else if (bestMedium != null && bestMedium.score.scoreIn01Range() > MEDIUM_THRESHOLD_3) {
                Log.d(TAG, "✅ 第三轮Medium通过，阈值: $MEDIUM_THRESHOLD_3")
                return bestMedium
            } else if (bestHigh != null && bestHigh.score.scoreIn01Range() > HIGH_THRESHOLD_3) {
                Log.d(TAG, "✅ 第三轮High通过，阈值: $HIGH_THRESHOLD_3")
                return bestHigh
            }

            // nothing was matched
            Log.d(TAG, "❌ 所有轮次都未通过阈值检查")
            return null
        }

        companion object {
            private fun getBestForSpecificity(
                ctx: SkillContext,
                skills: List<Skill<*>>,
                input: String,
            ): SkillWithResult<*>? {
                if (skills.isEmpty()) {
                    Log.d(TAG, "  📭 技能列表为空")
                    return null
                }
                
                Log.d(TAG, "  🔍 评估 ${skills.size} 个技能:")
                // this ensures that if `skills` is empty and null skill is returned,
                // nothing bad happens since its score cannot be higher than any other float value.
                var bestSkillSoFar: SkillWithResult<*>? = null
                for (skill in skills) {
                    val res = skill.scoreAndWrapResult(ctx, input)
                    Log.d(TAG, "    📝 ${skill.correspondingSkillInfo.id}: ${res.score.scoreIn01Range()}")
                    if (bestSkillSoFar == null || res.score.isBetterThan(bestSkillSoFar.score)) {
                        bestSkillSoFar = res
                    }
                }
                Log.d(TAG, "  🏆 最佳技能: ${bestSkillSoFar?.skill?.correspondingSkillInfo?.id} (${bestSkillSoFar?.score?.scoreIn01Range()})")
                return bestSkillSoFar
            }
        }
    }

    private var defaultBatch: SkillBatch = SkillBatch(defaultSkillBatch)
    private val batches: Stack<SkillBatch> = Stack()

    fun addBatchToTop(skillBatch: List<Skill<*>>) {
        batches.push(SkillBatch(skillBatch))
    }

    fun hasAnyBatches(): Boolean {
        return batches.isNotEmpty()
    }

    fun removeTopBatch() {
        if (!batches.isEmpty()) {
            batches.pop()
        }
    }

    fun removeAllBatches() {
        batches.removeAllElements()
    }

    fun getBest(
        ctx: SkillContext,
        input: String,
    ): SkillWithResult<*>? {
        for (i in batches.indices.reversed()) {
            val skillFromBatch = batches[i].getBest(ctx, input)
            if (skillFromBatch != null) {
                // found a matching skill: remove all skills in batch above it
                for (j in i + 1 until batches.size) {
                    removeTopBatch()
                }
                return skillFromBatch
            }
        }

        val skillFromBatch = defaultBatch.getBest(ctx, input)
        if (skillFromBatch != null) {
            // found a matching skill in the default batch: remove all other skill batches
            removeAllBatches()
        }
        return skillFromBatch
    }

    fun getFallbackSkill(
        ctx: SkillContext,
        input: String,
    ): SkillWithResult<*> {
        return fallbackSkill.scoreAndWrapResult(ctx, input)
    }

    override fun cleanup() {
        batches.clear()
    }

    companion object {
        private val TAG = SkillRanker::class.simpleName
        
        // various thresholds for different specificity categories (high, medium and low)
        // first round
        private const val HIGH_THRESHOLD_1 = 0.85f

        // second round
        private const val MEDIUM_THRESHOLD_2 = 0.90f
        private const val HIGH_THRESHOLD_2 = 0.80f

        // third round
        private const val LOW_THRESHOLD_3 = 0.90f
        private const val MEDIUM_THRESHOLD_3 = 0.80f
        private const val HIGH_THRESHOLD_3 = 0.70f
    }
}
