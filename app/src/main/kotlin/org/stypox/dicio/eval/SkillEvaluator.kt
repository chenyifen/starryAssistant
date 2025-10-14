package org.stypox.dicio.eval

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dicio.skill.skill.InteractionPlan
import org.dicio.skill.skill.Permission
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.di.SkillContextInternal
import org.stypox.dicio.di.SttInputDeviceWrapper
import org.stypox.dicio.io.graphical.ErrorSkillOutput
import org.stypox.dicio.io.graphical.MissingPermissionsSkillOutput
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.ui.home.Interaction
import org.stypox.dicio.ui.home.InteractionLog
import org.stypox.dicio.ui.home.PendingQuestion
import org.stypox.dicio.ui.home.QuestionAnswer
import javax.inject.Singleton

interface SkillEvaluator {
    val state: StateFlow<InteractionLog>
    val inputEvents: SharedFlow<InputEvent>

    var permissionRequester: suspend (List<Permission>) -> Boolean

    fun processInputEvent(event: InputEvent)
}

class SkillEvaluatorImpl(
    private val skillContext: SkillContextInternal,
    private val skillHandler: SkillHandler,
    private val sttInputDevice: SttInputDeviceWrapper,
) : SkillEvaluator {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val skillRanker: SkillRanker
        get() = skillHandler.skillRanker.value

    private val _state = MutableStateFlow(
        InteractionLog(
            interactions = listOf(),
            pendingQuestion = null,
        )
    )
    override val state: StateFlow<InteractionLog> = _state
    
    private val _inputEvents = MutableSharedFlow<InputEvent>(replay = 0)
    override val inputEvents: SharedFlow<InputEvent> = _inputEvents.asSharedFlow()

    // must be kept up to date even when the activity is recreated, for this reason it is `var`
    override var permissionRequester: suspend (List<Permission>) -> Boolean = { false }

    override fun processInputEvent(event: InputEvent) {
        // 发送事件到SharedFlow，让UI组件可以监听
        scope.launch {
            _inputEvents.emit(event)
        }
        
        scope.launch {
            suspendProcessInputEvent(event)
        }
    }

    private suspend fun suspendProcessInputEvent(event: InputEvent) {
        val startTime = System.currentTimeMillis()
        when (event) {
            is InputEvent.Error -> {
                addErrorInteractionFromPending(event.throwable)
            }
            is InputEvent.Final -> {
                val utterances = event.utterances.map { it.first }
                Log.d(TAG, "📥 收到Final事件: $utterances")
                
                // 过滤：ASR文本为空时，不触发技能排序
                val firstUtterance = utterances.firstOrNull()?.trim() ?: ""
                if (firstUtterance.isEmpty()) {
                    Log.d(TAG, "⏭️ ASR文本为空，跳过技能排序")
                    _state.value = _state.value.copy(pendingQuestion = null)
                    return
                }
                
                val updateStateStart = System.currentTimeMillis()
                _state.value = _state.value.copy(
                    pendingQuestion = PendingQuestion(
                        userInput = firstUtterance,
                        continuesLastInteraction = skillRanker.hasAnyBatches(),
                        skillBeingEvaluated = null,
                    )
                )
                val updateStateTime = System.currentTimeMillis() - updateStateStart
                Log.d(TAG, "⏱️ [性能] 状态更新耗时: ${updateStateTime}ms")
                
                evaluateMatchingSkill(utterances)
                
                val totalTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "⏱️ [性能] processInputEvent总耗时: ${totalTime}ms")
            }
            InputEvent.None -> {
                _state.value = _state.value.copy(pendingQuestion = null)
            }
            is InputEvent.Partial -> {
                _state.value = _state.value.copy(
                    pendingQuestion = PendingQuestion(
                        userInput = event.utterance,
                        // the next input can be a continuation of the last interaction only if the
                        // last skill invocation provided some skill batches (which are the only way
                        // to continue an interaction/conversation)
                        continuesLastInteraction = skillRanker.hasAnyBatches(),
                        skillBeingEvaluated = null,
                    )
                )
            }
        }
    }

    private suspend fun evaluateMatchingSkill(utterances: List<String>) {
        val evalStartTime = System.currentTimeMillis()
        Log.d(TAG, "🎯 开始技能匹配评估，输入语句: $utterances")
        
        val rankingStartTime = System.currentTimeMillis()
        val (chosenInput, chosenSkill) = try {
            utterances.firstNotNullOfOrNull { input: String ->
                val inputRankStart = System.currentTimeMillis()
                Log.d(TAG, "🔍 尝试匹配输入: '$input'")
                val result = skillRanker.getBest(skillContext, input)
                val inputRankTime = System.currentTimeMillis() - inputRankStart
                if (result != null) {
                    Log.d(TAG, "✅ 找到匹配技能: ${result.skill.correspondingSkillInfo.id}, 评分: ${result.score.scoreIn01Range()}, 耗时: ${inputRankTime}ms")
                } else {
                    Log.d(TAG, "❌ 没有找到匹配的技能, 耗时: ${inputRankTime}ms")
                }
                result?.let { skillWithResult ->
                    Pair(input, skillWithResult)
                }
            } ?: run {
                val fallbackStart = System.currentTimeMillis()
                Log.d(TAG, "🔄 使用fallback技能")
                val result = Pair(utterances[0], skillRanker.getFallbackSkill(skillContext, utterances[0]))
                val fallbackTime = System.currentTimeMillis() - fallbackStart
                Log.d(TAG, "⏱️ [性能] Fallback技能耗时: ${fallbackTime}ms")
                result
            }
        } catch (throwable: Throwable) {
            Log.e(TAG, "❌ 技能匹配过程中发生错误", throwable)
            addErrorInteractionFromPending(throwable)
            return
        }
        val rankingTime = System.currentTimeMillis() - rankingStartTime
        Log.d(TAG, "⏱️ [性能] 技能排序耗时: ${rankingTime}ms")
        
        val skillInfo = chosenSkill.skill.correspondingSkillInfo

        _state.value = _state.value.copy(
            pendingQuestion = PendingQuestion(
                userInput = chosenInput,
                // the skill ranker would have discarded all batches, if the chosen skill was not
                // the continuation of the last interaction (since continuing an
                // interaction/conversation is done through the stack of batches)
                continuesLastInteraction = skillRanker.hasAnyBatches(),
                skillBeingEvaluated = skillInfo,
            )
        )

        try {
            val permissionCheckStart = System.currentTimeMillis()
            val permissions = skillInfo.neededPermissions
            if (permissions.isNotEmpty() && !permissionRequester(permissions)) {
                // permissions were not granted, show message
                addInteractionFromPending(MissingPermissionsSkillOutput(skillInfo))
                return
            }
            val permissionCheckTime = System.currentTimeMillis() - permissionCheckStart
            Log.d(TAG, "⏱️ [性能] 权限检查耗时: ${permissionCheckTime}ms")

            val outputGenStart = System.currentTimeMillis()
            skillContext.previousOutput =
                _state.value.interactions.lastOrNull()?.questionsAnswers?.lastOrNull()?.answer
            val output = chosenSkill.generateOutput(skillContext)
            val outputGenTime = System.currentTimeMillis() - outputGenStart
            Log.d(TAG, "⏱️ [性能] 技能输出生成耗时: ${outputGenTime}ms")

            val interactionPlanStart = System.currentTimeMillis()
            val interactionPlan = output.getInteractionPlan(skillContext)
            addInteractionFromPending(output)
            val interactionPlanTime = System.currentTimeMillis() - interactionPlanStart
            Log.d(TAG, "⏱️ [性能] 交互计划处理耗时: ${interactionPlanTime}ms")
            
            val speechOutputStart = System.currentTimeMillis()
            val speechOutput = output.getSpeechOutput(skillContext)
            val speechOutputTime = System.currentTimeMillis() - speechOutputStart
            Log.d(TAG, "⏱️ [性能] 语音输出获取耗时: ${speechOutputTime}ms")
            Log.d(TAG, "🗣️ [DEBUG] getSpeechOutput() 返回: '$speechOutput'")
            Log.d(TAG, "🗣️ [DEBUG] speechOutput.isNotBlank(): ${speechOutput.isNotBlank()}")
            
            if (speechOutput.isNotBlank()) {
                val ttsStart = System.currentTimeMillis()
                withContext (Dispatchers.Main) {
                    Log.d(TAG, "🗣️ [DEBUG] 即将调用 speechOutputDevice.speak()")
                    skillContext.speechOutputDevice.speak(speechOutput)
                    val ttsTime = System.currentTimeMillis() - ttsStart
                    Log.d(TAG, "⏱️ [性能] TTS调用耗时: ${ttsTime}ms")
                    Log.d(TAG, "🗣️ [DEBUG] speechOutputDevice.speak() 调用完成")
                }
            } else {
                Log.w(TAG, "⚠️ [DEBUG] speechOutput 为空，跳过TTS播放")
            }
            
            val totalEvalTime = System.currentTimeMillis() - evalStartTime
            Log.d(TAG, "⏱️ [性能] ========== 意图识别与执行总耗时: ${totalEvalTime}ms ==========")
            Log.d(TAG, "⏱️ [性能] 其中 - 排序: ${rankingTime}ms, 生成输出: ${outputGenTime}ms, 语音: ${speechOutputTime}ms")

            when (interactionPlan) {
                InteractionPlan.FinishInteraction -> {
                    // current conversation has ended, reset to the default batch of skills
                    skillRanker.removeAllBatches()
                }
                is InteractionPlan.FinishSubInteraction -> {
                    skillRanker.removeTopBatch()
                }
                is InteractionPlan.Continue -> {
                    // nothing to do, just continue with current batches
                }
                is InteractionPlan.StartSubInteraction -> {
                    skillRanker.addBatchToTop(interactionPlan.nextSkills)
                }
                is InteractionPlan.ReplaceSubInteraction -> {
                    skillRanker.removeTopBatch()
                    skillRanker.addBatchToTop(interactionPlan.nextSkills)
                }
            }

            if (interactionPlan.reopenMicrophone) {
                skillContext.speechOutputDevice.runWhenFinishedSpeaking {
                    sttInputDevice.tryLoad(this::processInputEvent)
                }
            }

        } catch (throwable: Throwable) {
            addErrorInteractionFromPending(throwable)
            return
        }
    }

    private fun addErrorInteractionFromPending(throwable: Throwable) {
        Log.e(TAG, "Error while evaluating skills", throwable)
        addInteractionFromPending(ErrorSkillOutput(throwable, true))
    }

    private fun addInteractionFromPending(skillOutput: SkillOutput) {
        val log = _state.value
        val pendingUserInput = log.pendingQuestion?.userInput
        val pendingContinuesLastInteraction = log.pendingQuestion?.continuesLastInteraction
            ?: skillRanker.hasAnyBatches()
        val pendingSkill = log.pendingQuestion?.skillBeingEvaluated
        val questionAnswer = QuestionAnswer(pendingUserInput, skillOutput)

        _state.value = log.copy(
            interactions = log.interactions.toMutableList().also { inters ->
                if (pendingContinuesLastInteraction && inters.isNotEmpty()) {
                    inters[inters.size - 1] = inters[inters.size - 1].let { i -> i.copy(
                        questionsAnswers = i.questionsAnswers.toMutableList()
                            .apply { add(questionAnswer) }
                    ) }
                } else {
                    inters.add(
                        Interaction(
                            skill = pendingSkill,
                            questionsAnswers = listOf(questionAnswer)
                        )
                    )
                }
            },
            pendingQuestion = null,
        )
    }

    companion object {
        val TAG = SkillEvaluator::class.simpleName
    }
}

@Module
@InstallIn(SingletonComponent::class)
class SkillEvaluatorModule {
    @Provides
    @Singleton
    fun provideSkillEvaluator(
        skillContext: SkillContextInternal,
        skillHandler: SkillHandler,
        sttInputDevice: SttInputDeviceWrapper,
    ): SkillEvaluator {
        return SkillEvaluatorImpl(skillContext, skillHandler, sttInputDevice)
    }
}
