package org.stypox.dicio.audio

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import org.stypox.dicio.io.input.SttState
import org.stypox.dicio.io.wake.WakeState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 音频Pipeline状态机协调器
 * 
 * 设计原则：
 * 1. 利用现有的WakeState和SttState状态机
 * 2. 通过StateFlow响应式管理音频资源
 * 3. 基于现有架构的Pipeline设计
 * 4. 最小化侵入性，保持现有接口不变
 */
@Singleton
class AudioResourceCoordinator @Inject constructor() {
    
    companion object {
        private const val TAG = "AudioPipelineCoordinator"
    }
    
    // 音频Pipeline状态
    sealed class AudioPipelineState {
        object Idle : AudioPipelineState()                    // 空闲状态
        object WakeListening : AudioPipelineState()          // 唤醒词监听
        object WakeDetected : AudioPipelineState()           // 检测到唤醒词
        object AsrListening : AudioPipelineState()           // 语音识别中
        object Processing : AudioPipelineState()             // 处理中
        data class Error(val throwable: Throwable) : AudioPipelineState()  // 错误状态
    }
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Pipeline状态
    private val _pipelineState = MutableStateFlow<AudioPipelineState>(AudioPipelineState.Idle)
    val pipelineState: StateFlow<AudioPipelineState> = _pipelineState.asStateFlow()
    
    // 外部状态输入
    private val _wakeState = MutableStateFlow<WakeState?>(null)
    private val _sttState = MutableStateFlow<SttState?>(null)
    
    init {
        // 启动状态机
        startPipelineStateMachine()
    }
    
    /**
     * 更新唤醒设备状态
     */
    fun updateWakeState(wakeState: WakeState?) {
        _wakeState.value = wakeState
        Log.v(TAG, "🔄 Wake状态更新: $wakeState")
    }
    
    /**
     * 更新STT设备状态
     */
    fun updateSttState(sttState: SttState?) {
        _sttState.value = sttState
        Log.v(TAG, "🔄 STT状态更新: $sttState")
    }
    
    /**
     * 触发唤醒词检测事件
     */
    fun onWakeWordDetected() {
        Log.d(TAG, "🎯 检测到唤醒词")
        _pipelineState.value = AudioPipelineState.WakeDetected
    }
    
    /**
     * 检查是否可以使用AudioRecord（用于WakeService）
     */
    fun canWakeServiceUseAudio(): Boolean {
        val currentState = _pipelineState.value
        val canUse = currentState is AudioPipelineState.Idle || 
                     currentState is AudioPipelineState.WakeListening
        
        if (!canUse) {
            Log.v(TAG, "🚫 WakeService当前无法使用音频，Pipeline状态: $currentState")
        }
        
        return canUse
    }
    
    /**
     * 检查是否可以启动ASR
     */
    fun canStartAsr(): Boolean {
        val currentState = _pipelineState.value
        val canStart = currentState is AudioPipelineState.WakeDetected ||
                       currentState is AudioPipelineState.Idle
        
        Log.d(TAG, "🎤 ASR启动检查: $canStart, 当前状态: $currentState")
        return canStart
    }
    
    /**
     * 启动Pipeline状态机
     */
    private fun startPipelineStateMachine() {
        scope.launch {
            // 监听Wake和STT状态变化，自动管理Pipeline状态
            combine(_wakeState, _sttState, _pipelineState) { wakeState, sttState, pipelineState ->
                Triple(wakeState, sttState, pipelineState)
            }.collect { (wakeState, sttState, currentPipelineState) ->
                
                val newPipelineState = calculateNewPipelineState(
                    wakeState, sttState, currentPipelineState
                )
                
                if (newPipelineState != currentPipelineState) {
                    Log.d(TAG, "🔄 Pipeline状态转换: $currentPipelineState -> $newPipelineState")
                    Log.d(TAG, "   📊 Wake: $wakeState, STT: $sttState")
                    _pipelineState.value = newPipelineState
                }
            }
        }
    }
    
    /**
     * 计算新的Pipeline状态
     */
    private fun calculateNewPipelineState(
        wakeState: WakeState?,
        sttState: SttState?,
        currentState: AudioPipelineState
    ): AudioPipelineState {
        
        // 处理错误状态
        if (wakeState is WakeState.ErrorLoading || sttState is SttState.ErrorLoading) {
            val error = (wakeState as? WakeState.ErrorLoading)?.throwable 
                     ?: (sttState as? SttState.ErrorLoading)?.throwable
                     ?: Exception("Unknown error")
            return AudioPipelineState.Error(error)
        }
        
        // 基于STT状态的优先级处理
        when (sttState) {
            SttState.Listening -> {
                return AudioPipelineState.AsrListening
            }
            is SttState.Loading -> {
                return AudioPipelineState.Processing
            }
            SttState.Loaded -> {
                // STT已加载，检查是否刚检测到唤醒词
                if (currentState is AudioPipelineState.WakeDetected) {
                    return AudioPipelineState.Processing // 准备启动ASR
                }
                // 如果当前在ASR监听状态，且STT已完成加载，说明识别完成，应该回到唤醒监听
                if (currentState is AudioPipelineState.AsrListening) {
                    return if (wakeState == WakeState.Loaded) {
                        AudioPipelineState.WakeListening
                    } else {
                        AudioPipelineState.Idle
                    }
                }
            }
            else -> {
                // 其他STT状态不影响Pipeline状态
            }
        }
        
        // 基于Wake状态处理
        when (wakeState) {
            WakeState.Loaded -> {
                // 如果当前不在ASR状态，则进入唤醒监听
                if (currentState !is AudioPipelineState.AsrListening && 
                    currentState !is AudioPipelineState.Processing &&
                    currentState !is AudioPipelineState.WakeDetected) {
                    return AudioPipelineState.WakeListening
                }
            }
            WakeState.Loading, WakeState.NotLoaded -> {
                if (currentState !is AudioPipelineState.AsrListening &&
                    currentState !is AudioPipelineState.Processing) {
                    return AudioPipelineState.Idle
                }
            }
            else -> {
                // 其他Wake状态不影响Pipeline状态
            }
        }
        
        // 状态转换超时处理
        if (currentState is AudioPipelineState.WakeDetected) {
            // 如果检测到唤醒词后长时间没有进入ASR，回到监听状态
            return AudioPipelineState.WakeListening
        }
        
        if (currentState is AudioPipelineState.Processing) {
            // 处理完成后的状态转换
            if (sttState == null || sttState == SttState.Loaded) {
                return if (wakeState == WakeState.Loaded) {
                    AudioPipelineState.WakeListening
                } else {
                    AudioPipelineState.Idle
                }
            }
        }
        
        return currentState // 保持当前状态
    }
    
    /**
     * 获取当前Pipeline状态信息
     */
    fun getPipelineStatusInfo(): String {
        val pipelineState = _pipelineState.value
        val wakeState = _wakeState.value
        val sttState = _sttState.value
        return "Pipeline: $pipelineState, Wake: $wakeState, STT: $sttState"
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        scope.cancel("AudioPipelineCoordinator cleanup")
        Log.d(TAG, "🧹 AudioPipelineCoordinator资源清理完成")
    }
}
