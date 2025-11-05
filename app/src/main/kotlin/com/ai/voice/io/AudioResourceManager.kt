package com.ai.voice.io

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 音频资源管理器 - 单例
 * 
 * 职责：
 * 1. 统一管理AudioRecord资源的申请和释放
 * 2. 管理全局音频状态机
 * 3. 提供与UI状态的同步机制
 * 
 * 设计原则：
 * - 简单：只管理资源和状态，不涉及具体实现
 * - 安全：使用Mutex保证线程安全
 * - 清晰：明确的状态转换规则
 * 
 * 状态转换规则：
 * IDLE ⇄ WAKE_LISTENING ⇄ ASR_RECORDING
 * 
 * 🆕 修改：TTS播放不再影响音频状态，录音可以随时进行
 */
object AudioResourceManager {
    private const val TAG = "AudioResourceManager"

    // ========== 状态定义 ==========
    
    /**
     * 全局音频状态
     */
    enum class AudioState {
        IDLE,              // 空闲：没有任何音频活动
        WAKE_LISTENING,    // 唤醒监听：WakeService在监听唤醒词
        ASR_RECORDING      // ASR录音：ASR设备在录音识别
    }

    /**
     * 音频资源持有者
     */
    enum class AudioOwner {
        NONE,              // 无持有者
        WAKE_SERVICE,      // WakeService持有
        ASR_DEVICE         // ASR设备持有
    }

    // ========== 状态管理 ==========
    
    private val _audioState = MutableStateFlow(AudioState.IDLE)
    val audioState: StateFlow<AudioState> = _audioState.asStateFlow()

    private val _currentOwner = MutableStateFlow(AudioOwner.NONE)
    val currentOwner: StateFlow<AudioOwner> = _currentOwner.asStateFlow()

    // TTS状态
    private val isTtsPlaying = AtomicBoolean(false)
    
    // 资源锁
    private val resourceMutex = Mutex()
    
    // 状态监听器列表
    private val stateListeners = mutableListOf<(AudioState) -> Unit>()
    private val ttsListeners = mutableListOf<(Boolean) -> Unit>()

    // ========== 公共API ==========

    /**
     * 请求麦克风资源
     * 
     * @param owner 请求者
     * @return true=成功获取，false=被拒绝
     * 
     * 边界情况：
     * 1. 重复请求视为成功
     * 2. 有其他持有者时强制抢占（记录警告）
     * 
     * 🆕 修改：TTS播放时也允许录音（用户可以边听边说话）
     */
    suspend fun requestMicrophone(owner: AudioOwner): Boolean {
        return resourceMutex.withLock {
            Log.d(TAG, "🔍 [$owner] 请求麦克风 - 当前状态: ${_audioState.value}, 持有者: ${_currentOwner.value}, TTS: ${isTtsPlaying.get()}")
            
            // 🆕 移除：TTS播放时不再拒绝录音请求
            // if (isTtsPlaying.get()) {
            //     Log.w(TAG, "❌ [$owner] 请求麦克风被拒绝：TTS正在播放")
            //     return@withLock false
            // }

            // 边界情况：检查是否已经是持有者
            if (_currentOwner.value == owner) {
                Log.d(TAG, "✅ [$owner] 已持有麦克风，无需重复申请")
                return@withLock true
            }

            // 边界情况：如果有其他持有者，先强制释放
            if (_currentOwner.value != AudioOwner.NONE) {
                Log.w(TAG, "⚠️ [$owner] 请求麦克风，但当前持有者是 ${_currentOwner.value}，强制抢占")
                // 不实际释放，只记录警告，由上层保证顺序
            }

            // 分配资源
            _currentOwner.value = owner
            
            // 更新状态（TTS播放时也允许录音，状态可以为ASR_RECORDING或WAKE_LISTENING）
            val newState = when (owner) {
                AudioOwner.WAKE_SERVICE -> AudioState.WAKE_LISTENING
                AudioOwner.ASR_DEVICE -> AudioState.ASR_RECORDING
                AudioOwner.NONE -> AudioState.IDLE
            }
            
            transitionTo(newState, "[$owner] 获得麦克风")
            
            Log.i(TAG, "✅ [$owner] 成功获取麦克风资源")
            true
        }
    }

    /**
     * 释放麦克风资源
     * 
     * @param owner 释放者（必须是当前持有者）
     * 
     * 边界情况：
     * 1. 非持有者释放：记录警告，不执行操作
     * 2. 重复释放：幂等操作
     */
    suspend fun releaseMicrophone(owner: AudioOwner) {
        resourceMutex.withLock {
            // 边界情况：检查是否是持有者
            if (_currentOwner.value != owner) {
                if (_currentOwner.value == AudioOwner.NONE) {
                    Log.d(TAG, "✓ [$owner] 释放麦克风：已经是NONE状态（幂等操作）")
                } else {
                    Log.w(TAG, "⚠️ [$owner] 尝试释放麦克风，但当前持有者是 ${_currentOwner.value}")
                }
                return@withLock
            }

            _currentOwner.value = AudioOwner.NONE
            transitionTo(AudioState.IDLE, "[$owner] 释放麦克风")
            
            Log.i(TAG, "✅ [$owner] 释放麦克风资源")
        }
    }

    /**
     * 通知TTS播放开始
     * 🆕 修改：不再改变音频状态，TTS播放不影响录音
     * 
     * 边界情况：
     * 1. 重复调用：幂等操作
     */
    suspend fun notifyTtsStart() {
        resourceMutex.withLock {
            // 边界情况：检查是否已经在播放
            if (isTtsPlaying.getAndSet(true)) {
                Log.d(TAG, "⚠️ TTS已在播放中（幂等操作）")
                return@withLock
            }

            Log.i(TAG, "🔊 TTS播放开始（不影响录音状态）")
            
            // 🆕 不再改变音频状态，录音可以继续
            val currentOwner = _currentOwner.value
            if (currentOwner != AudioOwner.NONE) {
                Log.d(TAG, "✓ TTS播放开始，录音继续（持有者: $currentOwner）")
            } else {
                Log.d(TAG, "✓ TTS播放开始，当前无录音活动")
            }
            
            // 🆕 不再调用transitionTo，音频状态保持不变
        }
    }

    /**
     * 通知TTS播放结束
     * 🆕 修改：不再改变音频状态，因为TTS播放时状态从未改变
     * 
     * 边界情况：
     * 1. TTS未播放时调用：幂等操作
     */
    suspend fun notifyTtsEnd() {
        resourceMutex.withLock {
            // 边界情况：检查TTS是否在播放
            if (!isTtsPlaying.getAndSet(false)) {
                Log.d(TAG, "⚠️ TTS本来就没在播放（幂等操作）")
                return@withLock
            }

            Log.i(TAG, "🔇 TTS播放结束（不影响录音状态）")
            
            // 🆕 不再改变音频状态，因为TTS播放时状态从未改变
        }
    }

    /**
     * 检查是否可以录音
     * 🆕 修改：始终返回true，允许TTS播放时也录音
     * 
     * 非阻塞查询，用于录音循环中的快速检查
     */
    fun canRecord(): Boolean {
        // 🆕 修改：始终返回true，允许TTS播放时也录音
        return true
        // return !isTtsPlaying.get()
    }

    /**
     * 获取当前状态
     */
    fun getCurrentState(): AudioState {
        return _audioState.value
    }

    /**
     * 获取当前持有者
     */
    fun getCurrentOwner(): AudioOwner {
        return _currentOwner.value
    }

    /**
     * 注册状态监听器
     */
    @Synchronized
    fun addStateListener(listener: (AudioState) -> Unit) {
        stateListeners.add(listener)
        Log.d(TAG, "📝 注册状态监听器，总数: ${stateListeners.size}")
    }

    /**
     * 移除状态监听器
     */
    @Synchronized
    fun removeStateListener(listener: (AudioState) -> Unit) {
        stateListeners.remove(listener)
        Log.d(TAG, "🗑️ 移除状态监听器，总数: ${stateListeners.size}")
    }

    /**
     * 注册TTS状态监听器
     */
    @Synchronized
    fun addTtsListener(listener: (Boolean) -> Unit) {
        ttsListeners.add(listener)
        Log.d(TAG, "📝 注册TTS监听器，总数: ${ttsListeners.size}")
    }

    /**
     * 移除TTS状态监听器
     */
    @Synchronized
    fun removeTtsListener(listener: (Boolean) -> Unit) {
        ttsListeners.remove(listener)
        Log.d(TAG, "🗑️ 移除TTS监听器，总数: ${ttsListeners.size}")
    }

    /**
     * 获取调试信息
     */
    fun getDebugInfo(): String {
        return """
            AudioResourceManager状态:
            - 音频状态: ${_audioState.value}
            - 当前持有者: ${_currentOwner.value}
            - TTS播放中: ${isTtsPlaying.get()}
            - 状态监听器: ${stateListeners.size}
            - TTS监听器: ${ttsListeners.size}
        """.trimIndent()
    }

    // ========== 私有方法 ==========

    /**
     * 状态转换
     * 
     * 验证状态转换的合法性，并通知监听器
     */
    private fun transitionTo(newState: AudioState, reason: String) {
        val oldState = _audioState.value
        
        // 相同状态不需要转换
        if (oldState == newState) {
            return
        }

        // 验证状态转换合法性
        if (!isValidTransition(oldState, newState)) {
            Log.e(TAG, "❌ 非法状态转换: $oldState → $newState (原因: $reason)")
            return
        }

        _audioState.value = newState
        Log.i(TAG, "🔄 状态转换: $oldState → $newState (原因: $reason)")
        
        // 通知监听器
        notifyStateListeners(newState)
    }

    /**
     * 验证状态转换是否合法
     * 
     * 状态转换矩阵：
     * IDLE              → IDLE, WAKE_LISTENING, ASR_RECORDING
     * WAKE_LISTENING    → IDLE, WAKE_LISTENING, ASR_RECORDING
     * ASR_RECORDING     → IDLE, ASR_RECORDING
     * 
     * 🆕 修改：移除TTS_PLAYING状态，TTS播放不再影响音频状态
     */
    private fun isValidTransition(from: AudioState, to: AudioState): Boolean {
        return when (from) {
            AudioState.IDLE -> to in setOf(
                AudioState.IDLE,
                AudioState.WAKE_LISTENING,
                AudioState.ASR_RECORDING
            )
            
            AudioState.WAKE_LISTENING -> to in setOf(
                AudioState.IDLE,
                AudioState.WAKE_LISTENING,
                AudioState.ASR_RECORDING
            )
            
            AudioState.ASR_RECORDING -> to in setOf(
                AudioState.IDLE,
                AudioState.ASR_RECORDING
            )
        }
    }

    /**
     * 通知状态监听器
     * 
     * 异常处理：单个监听器失败不影响其他监听器
     */
    @Synchronized
    private fun notifyStateListeners(newState: AudioState) {
        val listeners = stateListeners.toList() // 复制以避免并发修改
        listeners.forEach { listener ->
            try {
                listener(newState)
            } catch (e: Exception) {
                Log.e(TAG, "❌ 状态监听器执行失败", e)
            }
        }
        
        if (listeners.isNotEmpty()) {
            Log.d(TAG, "📢 通知 ${listeners.size} 个状态监听器: $newState")
        }
    }

    /**
     * 通知TTS监听器
     * 
     * 异常处理：单个监听器失败不影响其他监听器
     */
    @Synchronized
    private fun notifyTtsListeners(isPlaying: Boolean) {
        val listeners = ttsListeners.toList() // 复制以避免并发修改
        listeners.forEach { listener ->
            try {
                listener(isPlaying)
            } catch (e: Exception) {
                Log.e(TAG, "❌ TTS监听器执行失败", e)
            }
        }
        
        if (listeners.isNotEmpty()) {
            Log.d(TAG, "📢 通知 ${listeners.size} 个TTS监听器: ${if (isPlaying) "开始播放" else "结束播放"}")
        }
    }

    /**
     * 重置所有状态（用于测试或错误恢复）
     */
    @Synchronized
    fun reset() {
        Log.w(TAG, "⚠️ 重置AudioResourceManager状态")
        _audioState.value = AudioState.IDLE
        _currentOwner.value = AudioOwner.NONE
        isTtsPlaying.set(false)
    }
}

