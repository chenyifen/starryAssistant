package com.ai.voice.eval

import android.util.Log
import com.ai.voice.util.DebugLogger
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.dicio.skill.skill.InteractionPlan
import org.dicio.skill.skill.Permission
import org.dicio.skill.skill.SkillOutput
import com.ai.voice.di.SkillContextInternal
import com.ai.voice.di.SttInputDeviceWrapper
import com.ai.voice.io.graphical.ErrorSkillOutput
import com.ai.voice.io.graphical.MissingPermissionsSkillOutput
import com.ai.voice.io.input.InputEvent
import com.ai.voice.ui.home.Interaction
import com.ai.voice.ui.home.InteractionLog
import com.ai.voice.ui.home.PendingQuestion
import com.ai.voice.ui.home.QuestionAnswer
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
    
    /**
     * 清理ASR识别文本，去除空格、标点等，提高技能匹配准确性
     */
    private fun cleanTextForSkillMatching(text: String): String {
        var cleaned = text.trim()
            // 移除多个连续空格
            .replace(Regex("\\s+"), " ")
            // 移除常见的标点符号（保留韩文、中文、英文的基本字符）
            .replace(Regex("[.,。，!！?？;；:：]"), "")
            // 移除前后空格
            .trim()
        
        // 🆕 根据语言转换数字为文字
        cleaned = convertNumbersToWords(cleaned)
        
        return cleaned
    }
    
    /**
     * 从技能输入数据中提取子技能ID
     * 例如：AppLauncher.Google -> "google", PowerControl.PowerOff -> "power_off"
     * @param inputData 技能输入数据（sealed class实例）
     * @return 子技能ID，如果没有子技能则返回null
     */
    private fun extractSubSkillId(inputData: Any?): String? {
        if (inputData == null) return null
        
        // 获取类名（例如：AppLauncher$Google）
        val className = inputData.javaClass.simpleName
        
        // 如果类名包含$，说明是sealed class的子类
        // 例如：AppLauncher$Google -> Google
        val subClassName = if (className.contains('$')) {
            className.substringAfter('$')
        } else {
            className
        }
        
        // 将PascalCase转换为snake_case
        // 例如：PowerOff -> power_off, Google -> google
        val subSkillId = subClassName
            .replace(Regex("([a-z])([A-Z])"), "$1_$2")
            .lowercase()
        
        // 如果转换后的ID与原始类名相同（都是小写），说明没有子技能
        // 或者如果subSkillId为空，返回null
        return if (subSkillId.isBlank() || subSkillId == className.lowercase()) {
            null
        } else {
            subSkillId
        }
    }
    
    /**
     * 根据文本语言将数字转换为对应语言的文字表达
     * 例如: "hdmi 2" -> "hdmi two", "에이치디엠아이 2" -> "에이치디엠아이 투"
     */
    private fun convertNumbersToWords(text: String): String {
        // 如果文本中没有数字，直接返回
        if (!text.contains(Regex("\\d"))) {
            return text
        }
        
        // 检测语言
        val locale = com.ai.voice.util.LanguageDetector.detectLocale(text, java.util.Locale.KOREAN)
        val isKorean = locale.language == "ko"
        val isEnglish = locale.language == "en"
        
        // 数字映射表
        val koreanNumbers = mapOf(
            "0" to "영", "1" to "일", "2" to "이", "3" to "삼", "4" to "사",
            "5" to "오", "6" to "육", "7" to "칠", "8" to "팔", "9" to "구"
        )
        
        val koreanNumbersAlt = mapOf(
            "0" to "공", "1" to "원", "2" to "투", "3" to "쓰리", "4" to "포",
            "5" to "파이브", "6" to "식스", "7" to "세븐", "8" to "에잇", "9" to "나인"
        )
        
        val englishNumbers = mapOf(
            "0" to "zero", "1" to "one", "2" to "two", "3" to "three", "4" to "four",
            "5" to "five", "6" to "six", "7" to "seven", "8" to "eight", "9" to "nine"
        )
        
        var result = text
        var converted = false
        
        when {
            isKorean -> {
                // 韩语环境：优先使用外来语数字（HDMI等技术术语常用）
                // 如果数字前面是字母/韩文，在前面加空格
                result = result.replace(Regex("([\\p{L}])(\\d)")) { matchResult ->
                    val char = matchResult.groupValues[1]
                    val digit = matchResult.groupValues[2]
                    converted = true
                    "$char ${koreanNumbersAlt[digit] ?: digit}"
                }
                // 处理剩余的独立数字
                result = result.replace(Regex("(\\d)")) { matchResult ->
                    val digit = matchResult.value
                    converted = true
                    koreanNumbersAlt[digit] ?: digit
                }
            }
            isEnglish -> {
                // 英语环境：转换为英文数字
                // 如果数字前面是字母，在前面加空格；如果数字后面是字母，在后面加空格
                result = result.replace(Regex("([a-zA-Z])(\\d)([a-zA-Z]?)")) { matchResult ->
                    val before = matchResult.groupValues[1]
                    val digit = matchResult.groupValues[2]
                    val after = matchResult.groupValues[3]
                    converted = true
                    val word = englishNumbers[digit] ?: digit
                    if (after.isNotEmpty()) {
                        "$before $word $after"
                    } else {
                        "$before $word"
                    }
                }
                // 处理剩余的独立数字
                result = result.replace(Regex("(\\d)")) { matchResult ->
                    val digit = matchResult.value
                    converted = true
                    englishNumbers[digit] ?: digit
                }
            }
            // 其他语言暂不处理
        }
        
        if (converted) {
            Log.d(TAG, "🔢 数字转换: '$text' -> '$result' (${locale.language})")
        }
        
        return result
    }

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
    
    // 🆕 标记Partial是否已执行技能（使用Mutex保证线程安全）
    private var partialSkillExecuted = false
    private var partialExecutionMutex = Mutex()
    // 🆕 记录Partial阶段执行的文本和技能ID，用于Final阶段对比
    private var partialExecutedText = ""
    private var partialExecutedSkillId: String? = null

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
                    // 重置标记（线程安全）
                    partialExecutionMutex.withLock {
                        partialSkillExecuted = false
                        partialExecutedText = ""
                        partialExecutedSkillId = null
                    }
                    return
                }
                
                // 🔥 清理文本：去除空格、标点等，提高技能匹配准确性
                val cleanedUtterance = cleanTextForSkillMatching(firstUtterance)
                Log.d(TAG, "🧹 文本清理: '$firstUtterance' -> '$cleanedUtterance'")
                
                // 🆕 检测ASR语言并设置到SkillContext（必须在技能执行前设置）
                if (cleanedUtterance.isNotBlank()) {
                    val asrLocale = com.ai.voice.util.LanguageDetector.detectLocale(cleanedUtterance, java.util.Locale.KOREAN)
                    Log.d(TAG, "🎤 [Final] ASR识别语言: ${com.ai.voice.util.LanguageDetector.getLocaleName(asrLocale)}")
                    skillContext.asrLocale = asrLocale
                } else {
                    skillContext.asrLocale = null
                }
                
                // 自动化测试：打印识别结果
                Log.i("AutoTest", "ASR结果: $cleanedUtterance")
                
                // 🆕 检查Partial是否已执行，以及Final文本是否与Partial不同
                val (shouldSkip, partialText, partialSkillId) = partialExecutionMutex.withLock {
                    val skip = partialSkillExecuted
                    val text = partialExecutedText
                    val skillId = partialExecutedSkillId
                    if (skip) {
                        partialSkillExecuted = false  // 重置标记
                    }
                    Triple(skip, text, skillId)
                }
                
                // 🆕 如果Partial已执行，检查Final文本是否与Partial不同
                if (shouldSkip && partialText.isNotBlank()) {
                    // partialText已经是清理后的文本，直接比较
                    val textsSimilar = cleanedUtterance.lowercase().contains(partialText.lowercase()) ||
                                      partialText.lowercase().contains(cleanedUtterance.lowercase()) ||
                                      cleanedUtterance.lowercase() == partialText.lowercase()
                    
                    if (textsSimilar) {
                        Log.i(TAG, "⏭️ [Final] Partial已执行技能，Final文本与Partial相似，跳过重复执行 (Partial: '$partialText', Final: '$cleanedUtterance')")
                        _state.value = _state.value.copy(pendingQuestion = null)
                        // 重置记录
                        partialExecutionMutex.withLock {
                            partialExecutedText = ""
                            partialExecutedSkillId = null
                        }
                        return
                    } else {
                        // 🆕 Final文本与Partial不同，需要重新匹配和执行
                        Log.i(TAG, "🔄 [Final] Partial已执行，但Final文本与Partial不同，重新匹配技能 (Partial: '$partialText' -> $partialSkillId, Final: '$cleanedUtterance')")
                        // 继续执行，重新匹配Final阶段的技能
                    }
                } else if (shouldSkip) {
                    Log.i(TAG, "⏭️ [Final] Partial已执行技能，跳过重复执行")
                    _state.value = _state.value.copy(pendingQuestion = null)
                    return
                }
                
                val updateStateStart = System.currentTimeMillis()
                _state.value = _state.value.copy(
                    pendingQuestion = PendingQuestion(
                        userInput = firstUtterance, // UI显示原始文本
                        continuesLastInteraction = skillRanker.hasAnyBatches(),
                        skillBeingEvaluated = null,
                    )
                )
                val updateStateTime = System.currentTimeMillis() - updateStateStart
                Log.d(TAG, "⏱️ [性能] 状态更新耗时: ${updateStateTime}ms")
                
                // 🔥 使用清理后的文本进行技能匹配
                evaluateMatchingSkill(listOf(cleanedUtterance))
                
                val totalTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "⏱️ [性能] processInputEvent总耗时: ${totalTime}ms")
                
                // 重置标记（线程安全）
                partialExecutionMutex.withLock {
                    partialSkillExecuted = false
                    partialExecutedText = ""
                    partialExecutedSkillId = null
                }
            }
            InputEvent.None -> {
                _state.value = _state.value.copy(pendingQuestion = null)
            }
            is InputEvent.Partial -> {
                val utterance = event.utterance.trim()
                
                // 🔥 清理文本：去除空格、标点等，提高技能匹配准确性
                val cleanedUtterance = cleanTextForSkillMatching(utterance)
                
                // 更新pending状态
                _state.value = _state.value.copy(
                    pendingQuestion = PendingQuestion(
                        userInput = utterance, // UI显示原始文本
                        continuesLastInteraction = skillRanker.hasAnyBatches(),
                        skillBeingEvaluated = null,
                    )
                )
                
                // 🆕 Partial识别优化：尝试匹配技能
                if (cleanedUtterance.isNotEmpty()) {
                    Log.d(TAG, "🔍 [Partial] 尝试匹配技能: '$cleanedUtterance'")
                    
                    try {
                        val result = skillRanker.getBest(skillContext, cleanedUtterance)
                        
                        if (result != null) {
                            val score = result.score.scoreIn01Range()
                            Log.d(TAG, "🎯 [Partial] 找到匹配: ${result.skill.correspondingSkillInfo.id}, 分数: $score")
                            
                            // 只有高分匹配(≥0.5)才立即执行
                            if (score >= 0.5f) {
                                Log.i(TAG, "✅ [Partial] 高分匹配(${score})，立即执行技能")
                                
                                // 🔥 原子操作：检查并设置标记，然后执行技能（防止竞态条件）
                                val shouldExecute = partialExecutionMutex.withLock {
                                    if (!partialSkillExecuted) {
                                        partialSkillExecuted = true
                                        true
                                    } else {
                                        false
                                    }
                                }
                                
                                if (shouldExecute) {
                                    // 🆕 检测ASR语言并设置到SkillContext（必须在技能执行前设置）
                                    val asrLocale = com.ai.voice.util.LanguageDetector.detectLocale(cleanedUtterance, java.util.Locale.KOREAN)
                                    Log.d(TAG, "🎤 [Partial] ASR识别语言: ${com.ai.voice.util.LanguageDetector.getLocaleName(asrLocale)}")
                                    skillContext.asrLocale = asrLocale
                                    
                                    // 🔥 修复：使用预匹配的技能，禁止fallback
                                    evaluateMatchingSkill(
                                        utterances = listOf(cleanedUtterance), // 🔥 使用清理后的文本
                                        preMatchedSkill = result,
                                        allowFallback = false
                                    )
                                    // 🆕 记录Partial阶段执行的文本和技能ID（保存清理后的文本用于后续比较）
                                    partialExecutionMutex.withLock {
                                        partialExecutedText = cleanedUtterance
                                        partialExecutedSkillId = result.skill.correspondingSkillInfo.id
                                    }
                                } else {
                                    Log.d(TAG, "⏭️ [Partial] 技能已被执行，跳过")
                                }
                            } else {
                                Log.d(TAG, "⏸️ [Partial] 分数较低($score < 0.5)，等待Final结果")
                            }
                        } else {
                            Log.d(TAG, "⏸️ [Partial] 无匹配，等待Final结果")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ [Partial] 技能匹配异常: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * 评估并执行匹配的技能
     * 
     * @param utterances 用户输入的文本列表
     * @param preMatchedSkill 预匹配的技能结果（如果已经通过getBest()评估过）
     * @param allowFallback 是否允许在无匹配时使用fallback技能（Final阶段为true，Partial阶段为false）
     */
    private suspend fun evaluateMatchingSkill(
        utterances: List<String>,
        preMatchedSkill: SkillWithResult<*>? = null,
        allowFallback: Boolean = true
    ) {
        val evalStartTime = System.currentTimeMillis()
        
        // 🔥 清理所有utterances，确保技能匹配使用清理后的文本
        val cleanedUtterances = utterances.map { cleanTextForSkillMatching(it) }
        
        val (chosenInput, chosenSkill) = try {
            // 🔥 如果提供了预匹配技能，直接使用，避免重复评估
            if (preMatchedSkill != null) {
                Log.d(TAG, "🎯 使用预匹配技能: ${preMatchedSkill.skill.correspondingSkillInfo.id}, 评分: ${preMatchedSkill.score.scoreIn01Range()}")
                Pair(cleanedUtterances[0], preMatchedSkill)
            } else {
                // 原有逻辑：尝试匹配技能
                cleanedUtterances.firstNotNullOfOrNull { input: String ->
                    val inputRankStart = System.currentTimeMillis()
                    Log.d(TAG, "🔍 尝试匹配输入: '$input'")
                    val result = skillRanker.getBest(skillContext, input)
                    val inputRankTime = System.currentTimeMillis() - inputRankStart
                    if (result != null) {
                        Log.d(TAG, "✅ 匹配技能: ${result.skill.correspondingSkillInfo.id}, 评分: ${result.score.scoreIn01Range()}")
                    }
                    result?.let { skillWithResult ->
                        Pair(input, skillWithResult)
                    }
                } ?: run {
                    // 🔥 只有允许fallback时才使用fallback技能
                    if (allowFallback) {
                        Log.d(TAG, "⚠️ 无匹配技能，使用fallback")
                        Pair(cleanedUtterances[0], skillRanker.getFallbackSkill(skillContext, cleanedUtterances[0]))
                    } else {
                        Log.d(TAG, "❌ [Partial] 无匹配技能且禁止fallback，跳过执行")
                        return
                    }
                }
            }
        } catch (throwable: Throwable) {
            Log.e(TAG, "❌ 技能匹配过程中发生错误", throwable)
            addErrorInteractionFromPending(throwable)
            return
        }
        
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
            val permissions = skillInfo.neededPermissions
            if (permissions.isNotEmpty() && !permissionRequester(permissions)) {
                // permissions were not granted, show message
                addInteractionFromPending(MissingPermissionsSkillOutput(skillInfo))
                return
            }

            skillContext.previousOutput =
                _state.value.interactions.lastOrNull()?.questionsAnswers?.lastOrNull()?.answer
            val output = chosenSkill.generateOutput(skillContext)
            
            // 记录技能执行结果（用于自动化测试）
            val speechResult = output.getSpeechOutput(skillContext)
            // 🔥 提取子技能ID（如app_launcher:google）
            val subSkillId = extractSubSkillId(chosenSkill.inputData)
            com.ai.voice.util.AutoTestLogger.logSkillExecuted(skillInfo.id, speechResult, subSkillId)
            
            // 🔒 关键日志：命令执行成功（Release版本也输出）
            // chosenInput 可能为 String?，但 logCommandExecuted 方法已接受 String? 类型
            DebugLogger.logCommandExecuted(TAG, skillInfo.id, chosenInput, speechResult)

            val interactionPlan = output.getInteractionPlan(skillContext)
            addInteractionFromPending(output)
            
            val speechOutput = output.getSpeechOutput(skillContext)
            
            // 🆕 如果识别不出具体命令（fallback技能），不播放TTS
            // 只有执行了具体命令才有TTS回复
            val isFallbackSkill = skillInfo.id == "text"
            if (speechOutput.isNotBlank() && !isFallbackSkill) {
                withContext (Dispatchers.Main) {
                    skillContext.speechOutputDevice.speak(speechOutput)
                }
                Log.d(TAG, "✅ 执行具体命令 (${skillInfo.id})，播放TTS: \"$speechOutput\"")
            } else if (isFallbackSkill) {
                Log.d(TAG, "⏭️ 识别不出具体命令（fallback），不播放TTS")
            }
            
            val totalEvalTime = System.currentTimeMillis() - evalStartTime
            Log.d(TAG, "⏱️ [性能] 意图识别与执行总耗时: ${totalEvalTime}ms")

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
                // 🔧 已禁用：不再使用 sttInputDevice，ASR 现在由 EnhancedFloatingWindowService 通过 AsrHandler 管理
                // 如果需要重新打开麦克风，应该通知 EnhancedFloatingWindowService 重新启动 AsrHandler
                // skillContext.speechOutputDevice.runWhenFinishedSpeaking {
                //     sttInputDevice.tryLoad(this::processInputEvent)
                // }
                Log.d(TAG, "⏭️ 跳过自动重新打开麦克风（已改用 AsrHandler，由 EnhancedFloatingWindowService 管理）")
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
        val TAG: String = SkillEvaluator::class.java.simpleName
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
