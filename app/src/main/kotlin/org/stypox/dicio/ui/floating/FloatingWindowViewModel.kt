package org.stypox.dicio.ui.floating

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.stypox.dicio.MainActivity
import org.stypox.dicio.di.SkillContextInternal
import org.stypox.dicio.di.SttInputDeviceWrapper
import org.stypox.dicio.di.WakeDeviceWrapper
import org.stypox.dicio.eval.SkillEvaluator
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.io.input.SttState
import org.stypox.dicio.io.wake.WakeState
import org.stypox.dicio.ui.home.InteractionLog

class FloatingWindowViewModel(
    private val context: Context,
    private val skillEvaluator: SkillEvaluator,
    private val sttInputDevice: SttInputDeviceWrapper,
    private val wakeDevice: WakeDeviceWrapper,
    private val skillContext: SkillContextInternal
) : ViewModel() {

    private val _uiState = MutableStateFlow(FloatingUiState())
    val uiState: StateFlow<FloatingUiState> = _uiState.asStateFlow()
    
    // 存储当前的ASR文本
    private var currentAsrText = ""
    private var currentTtsText = ""

    init {
        // 监听语音识别状态
        viewModelScope.launch {
            combine(
                sttInputDevice.uiState,
                wakeDevice.state,
                skillEvaluator.state
            ) { sttState, wakeState, interactionLog ->
                updateUiState(sttState, wakeState, interactionLog)
            }.collect { /* 状态已在updateUiState中更新 */ }
        }
        
        // 初始化STT监听
        initializeSttListening()
    }

    /**
     * 初始化STT监听，设置事件处理器
     */
    private fun initializeSttListening() {
        android.util.Log.d("FloatingWindowViewModel", "🎤 初始化STT监听...")
        
        // 监听SkillEvaluator的输入事件，这样可以同时获得ASR结果
        viewModelScope.launch {
            skillEvaluator.inputEvents.collect { inputEvent ->
                android.util.Log.d("FloatingWindowViewModel", "🎤 从SkillEvaluator接收STT事件: $inputEvent")
                handleInputEvent(inputEvent)
            }
        }
    }
    
    /**
     * 处理来自STT设备的输入事件
     */
    private fun handleInputEvent(inputEvent: InputEvent) {
        android.util.Log.d("FloatingWindowViewModel", "📝 收到输入事件: $inputEvent")
        
        when (inputEvent) {
            is InputEvent.Partial -> {
                // 部分识别结果 - 实时更新ASR文本
                val oldText = currentAsrText
                currentAsrText = inputEvent.utterance
                updateCurrentUiState()
                android.util.Log.d("FloatingWindowViewModel", "🎯 部分识别: '$oldText' -> '${inputEvent.utterance}'")
            }
            is InputEvent.Final -> {
                // 最终识别结果
                val finalText = inputEvent.utterances.firstOrNull()?.first ?: ""
                val oldText = currentAsrText
                currentAsrText = finalText
                updateCurrentUiState()
                android.util.Log.d("FloatingWindowViewModel", "✅ 最终识别: '$oldText' -> '$finalText'")
                
                // 注意：不要在这里再次调用skillEvaluator.processInputEvent(inputEvent)
                // 因为我们是从SkillEvaluator的SharedFlow中收到这个事件的，再次调用会导致死循环
                android.util.Log.d("FloatingWindowViewModel", "📝 事件已从SkillEvaluator接收，无需重复处理")
                
                // 最终结果处理完成后，准备下次识别（延迟清空ASR文本）
                if (finalText.isNotBlank()) {
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(2000) // 2秒后清空ASR文本，为下次识别做准备
                        if (currentAsrText == finalText) { // 只有当前文本没有被新的识别覆盖时才清空
                            android.util.Log.d("FloatingWindowViewModel", "🧹 清空ASR文本，准备下次识别")
                            currentAsrText = ""
                            updateCurrentUiState()
                        }
                    }
                } else {
                    android.util.Log.w("FloatingWindowViewModel", "⚠️ 最终识别结果为空")
                    // 空结果也清空文本
                    currentAsrText = ""
                    updateCurrentUiState()
                }
            }
            is InputEvent.None -> {
                // 没有识别到内容
                val oldText = currentAsrText
                currentAsrText = ""
                updateCurrentUiState()
                android.util.Log.d("FloatingWindowViewModel", "🔇 没有识别到内容: '$oldText' -> ''")
            }
            is InputEvent.Error -> {
                // 识别错误
                val oldText = currentAsrText
                currentAsrText = "识别错误: ${inputEvent.throwable.message}"
                updateCurrentUiState()
                android.util.Log.e("FloatingWindowViewModel", "❌ 识别错误: '$oldText' -> '$currentAsrText'", inputEvent.throwable)
                
                // 错误信息显示3秒后清空
                viewModelScope.launch {
                    kotlinx.coroutines.delay(3000)
                    if (currentAsrText.startsWith("识别错误:")) {
                        currentAsrText = ""
                        updateCurrentUiState()
                    }
                }
            }
        }
    }

    private fun updateUiState(
        sttState: SttState?,
        wakeState: WakeState?,
        interactionLog: InteractionLog
    ) {
        val currentState = _uiState.value
        
        // 只在状态真正从非监听转为监听时，才重新设置事件监听器
        if (sttState is SttState.Listening && currentState.assistantState != AssistantState.LISTENING) {
            android.util.Log.d("FloatingWindowViewModel", "🔄 STT状态转为监听，但不重复设置监听器（已在initializeSttListening中设置）")
            // 移除重复的tryLoad调用，避免启动多个录制协程
        }
        
        // 简化状态逻辑：只根据STT状态判断，不显示思考中状态
        val assistantState = when {
            sttState is SttState.Listening -> {
                // 开始新的监听时，清空之前的ASR文本（如果不是部分识别状态）
                if (currentState.assistantState != AssistantState.LISTENING) {
                    android.util.Log.d("FloatingWindowViewModel", "🎤 开始新的监听，清空ASR文本")
                    currentAsrText = ""
                }
                AssistantState.LISTENING
            }
            else -> AssistantState.IDLE
        }

        // 从SkillEvaluator获取TTS文本
        val newTtsText = when {
            interactionLog.interactions.isNotEmpty() -> {
                // 获取最后一个交互的最后一个答案
                val lastInteraction = interactionLog.interactions.last()
                if (lastInteraction.questionsAnswers.isNotEmpty()) {
                    val lastAnswer = lastInteraction.questionsAnswers.last().answer
                    try {
                        // 使用SkillContext调用getSpeechOutput获取实际文本
                        val speechOutput = lastAnswer.getSpeechOutput(skillContext)
                        android.util.Log.d("FloatingWindowViewModel", "🤖 获取到TTS文本: '$speechOutput'")
                        speechOutput
                    } catch (e: Exception) {
                        android.util.Log.w("FloatingWindowViewModel", "获取语音输出失败", e)
                        "回复获取失败: ${e.message}"
                    }
                } else {
                    // 如果没有问答，但有交互，可能是正在处理中
                    if (assistantState == AssistantState.LISTENING) {
                        currentTtsText // 保持当前TTS文本
                    } else {
                        "正在处理您的请求..."
                    }
                }
            }
            else -> {
                // 没有交互记录时的处理
                if (assistantState == AssistantState.LISTENING) {
                    // 清空之前的TTS文本，准备新的对话
                    ""
                } else {
                    currentTtsText // 保持当前TTS文本
                }
            }
        }
        
        // 只在TTS文本真正变化时更新并记录日志
        if (newTtsText != currentTtsText) {
            android.util.Log.d("FloatingWindowViewModel", "🔄 TTS文本更新: '$currentTtsText' -> '$newTtsText'")
            currentTtsText = newTtsText
        }

        _uiState.value = currentState.copy(
            assistantState = assistantState,
            asrText = currentAsrText,
            ttsText = currentTtsText,
            isWakeWordActive = wakeState is WakeState.Loaded
        )
        
        android.util.Log.d("FloatingWindowViewModel", "🔄 UI状态更新: assistantState=$assistantState, asrText='$currentAsrText', ttsText='$currentTtsText'")
    }
    
    /**
     * 更新当前UI状态（保持其他状态不变）
     */
    private fun updateCurrentUiState() {
        val currentState = _uiState.value
        val newState = currentState.copy(
            asrText = currentAsrText,
            ttsText = currentTtsText
        )
        _uiState.value = newState
        android.util.Log.d("FloatingWindowViewModel", "💫 当前UI状态更新: asrText='$currentAsrText', ttsText='$currentTtsText', assistantState=${newState.assistantState}")
        android.util.Log.d("FloatingWindowViewModel", "📱 UI状态详情: asrEmpty=${currentAsrText.isEmpty()}, ttsEmpty=${currentTtsText.isEmpty()}")
    }

    fun onEnergyOrbClick() {
        val currentState = _uiState.value
        android.util.Log.d("FloatingWindowViewModel", "🔘 能量球点击，当前状态: ${currentState.assistantState}")
        
        when (currentState.assistantState) {
            AssistantState.IDLE -> {
                // 开始监听
                android.util.Log.d("FloatingWindowViewModel", "🎤 开始语音监听...")
                // 直接调用sttInputDevice.tryLoad，让它将事件发送到SkillEvaluator
                // SkillEvaluator会通过SharedFlow将事件传递给我们的handleInputEvent
                sttInputDevice.tryLoad(skillEvaluator::processInputEvent)
            }
            AssistantState.LISTENING -> {
                // 停止监听
                android.util.Log.d("FloatingWindowViewModel", "🛑 停止语音监听...")
                try {
                    sttInputDevice.stopListening()
                    currentAsrText = ""
                    updateCurrentUiState()
                } catch (e: Exception) {
                    android.util.Log.w("FloatingWindowViewModel", "停止监听失败", e)
                }
            }
            AssistantState.THINKING -> {
                // 思考中，暂时不做处理
                android.util.Log.d("FloatingWindowViewModel", "🤔 正在思考中，暂不处理点击")
            }
        }
    }

    fun onSettingsClick() {
        // 打开主应用的设置页面
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "settings")
        }
        context.startActivity(intent)
    }

    fun onCommandClick(command: String) {
        // 执行命令
        when (command) {
            "calculator" -> executeCommand("打开计算器")
            "camera" -> executeCommand("打开相机")
            "weather" -> executeCommand("今天天气怎么样")
            "alarm" -> executeCommand("设置闹钟")
            "music" -> executeCommand("播放音乐")
            "message" -> executeCommand("发送消息")
            else -> executeCommand(command)
        }
        
        // 隐藏命令建议
        _uiState.value = _uiState.value.copy(showCommandSuggestions = false)
    }

    private fun executeCommand(command: String) {
        // 直接调用skillEvaluator.processInputEvent，因为这是用户主动触发的命令
        // 不是从SharedFlow接收的事件，所以不会造成循环
        skillEvaluator.processInputEvent(InputEvent.Final(listOf(Pair(command, 1.0f))))
    }

    fun onDismiss() {
        _uiState.value = _uiState.value.copy(showCommandSuggestions = false)
    }

    fun startListening() {
        // 直接使用skillEvaluator::processInputEvent作为回调
        // 这样事件会先到SkillEvaluator，然后通过SharedFlow传递给我们的handleInputEvent
        sttInputDevice.tryLoad(skillEvaluator::processInputEvent)
    }

    fun triggerWakeAnimation() {
        // 触发唤醒动画
        val currentState = _uiState.value
        _uiState.value = currentState.copy(
            assistantState = AssistantState.LISTENING,
            energyLevel = 1.0f
        )
        
        android.util.Log.d("FloatingWindowViewModel", "🎬 触发唤醒动画：Lottie动画开始播放")
    }

    fun updateEnergyLevel(level: Float) {
        val currentState = _uiState.value
        _uiState.value = currentState.copy(energyLevel = level.coerceIn(0f, 1f))
    }
}

data class FloatingUiState(
    val assistantState: AssistantState = AssistantState.IDLE,
    val asrText: String = "",
    val ttsText: String = "",
    val showCommandSuggestions: Boolean = false,
    val isWakeWordActive: Boolean = false,
    val energyLevel: Float = 0.5f // 0.0 到 1.0，用于控制能量球的亮度
)

enum class AssistantState {
    IDLE,       // 待机状态
    LISTENING,  // 听取中
    THINKING    // 思考中
}