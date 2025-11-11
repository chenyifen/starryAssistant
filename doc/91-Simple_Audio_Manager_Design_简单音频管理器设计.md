# 简单实用的音频管理器设计方案

## 一、现状问题分析

### 1.1 核心问题
```
当前架构问题：
┌─────────────────────────────────────────────────────────┐
│  WakeService          ASR Device         TTS Device     │
│      ↓                    ↓                   ↓         │
│  AudioRecord         AudioRecord         AudioTrack     │
│      ↓                    ↓                   ↓         │
│  ❌ 资源冲突！         ❌ 状态不同步！      ❌ 回声问题！  │
└─────────────────────────────────────────────────────────┘

具体表现：
1. WakeService和ASR同时创建AudioRecord → 资源竞争
2. TTS播放时ASR仍在录音 → 识别到TTS声音（回声）
3. 各组件状态独立 → UI显示与实际状态不一致
4. 没有统一的生命周期管理 → 资源泄漏
```

### 1.2 语音助手的完整业务流程

```
完整业务流程：

1. [空闲状态 IDLE]
   ↓
2. [唤醒词监听 WAKE_LISTENING]
   - WakeService持续监听唤醒词
   - 检测到"Hi Nudge" / "小艺小艺"
   ↓
3. [ASR录音 ASR_RECORDING] 
   - WakeService停止监听
   - ASR设备开始录音
   - VAD检测语音段
   - 实时显示Partial结果
   ↓
4. [语音识别完成]
   - 发送Final结果
   - ASR停止录音
   ↓
5. [意图理解 THINKING]
   - SkillEvaluator处理输入
   - 确定要执行的Skill
   ↓
6. [TTS播放 TTS_PLAYING]
   - TTS合成语音
   - 播放回复
   - ⚠️ 关键：此时必须禁止所有录音
   ↓
7. [TTS完成]
   - 回到空闲或重新监听唤醒词
```

### 1.3 边界状态分析

```
需要处理的边界状态：

1. WakeService → ASR 切换
   - WakeService必须完全停止录音
   - ASR才能启动AudioRecord
   - 延迟：~300ms

2. ASR → TTS 切换
   - ASR必须立即停止录音
   - TTS开始播放
   - 防止回声问题

3. TTS → WakeService 切换
   - TTS播放完成
   - 延迟200ms后WakeService恢复监听
   - 避免捕获到TTS尾音

4. 用户主动中断
   - 点击停止按钮
   - 需要立即停止所有音频操作
   - 清理所有状态

5. 错误处理
   - AudioRecord初始化失败
   - 权限被拒绝
   - 模型加载失败
   - 需要有清晰的错误恢复机制

6. 后台/前台切换
   - APP进入后台但WakeService继续运行
   - APP回到前台恢复UI同步

7. 系统资源抢占
   - 电话来电（系统强制释放麦克风）
   - 其他APP使用麦克风
   - 需要graceful degradation
```

## 二、设计方案

### 2.1 核心理念

> **单一音频资源管理者 + 清晰的状态机**

```
设计原则：
1. ✅ 单一职责：一个管理器专门负责音频资源
2. ✅ 明确状态：使用状态机避免混乱
3. ✅ 互斥访问：同一时刻只有一个组件可以录音
4. ✅ TTS保护：TTS播放时强制禁止录音
5. ✅ 最小改动：不重构现有设备实现
```

### 2.2 架构设计

```kotlin
┌──────────────────────────────────────────────────────────────┐
│                   AudioResourceManager                        │
│                      (单例Singleton)                          │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │         状态机 (State Machine)                        │    │
│  │                                                       │    │
│  │   IDLE → WAKE_LISTENING → ASR_RECORDING             │    │
│  │     ↑          ↓              ↓                       │    │
│  │     └──────← TTS_PLAYING ←────┘                      │    │
│  │                                                       │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │         资源锁 (Resource Lock)                        │    │
│  │                                                       │    │
│  │   currentOwner: NONE / WAKE / ASR                    │    │
│  │   isTtsPlaying: Boolean                              │    │
│  │   audioRecordLock: Mutex                             │    │
│  │                                                       │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │         对外接口 (Public API)                         │    │
│  │                                                       │    │
│  │   requestMicrophone(owner): Boolean                  │    │
│  │   releaseMicrophone(owner): void                     │    │
│  │   notifyTtsStart(): void                             │    │
│  │   notifyTtsEnd(): void                               │    │
│  │   getCurrentState(): AudioState                      │    │
│  │                                                       │    │
│  └─────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
            ↓                ↓                  ↓
    ┌──────────┐      ┌──────────┐      ┌──────────┐
    │WakeService│      │ASR Device│      │TTS Device│
    │          │      │          │      │          │
    │ request  │      │ request  │      │  notify  │
    │ release  │      │ release  │      │  播放状态 │
    └──────────┘      └──────────┘      └──────────┘
```

### 2.3 状态定义

```kotlin
/**
 * 全局音频状态
 */
enum class AudioState {
    IDLE,              // 空闲：没有任何音频活动
    WAKE_LISTENING,    // 唤醒监听：WakeService在监听唤醒词
    ASR_RECORDING,     // ASR录音：ASR设备在录音识别
    TTS_PLAYING,       // TTS播放：TTS在播放，禁止所有录音
}

/**
 * 音频资源持有者
 */
enum class AudioOwner {
    NONE,              // 无持有者
    WAKE_SERVICE,      // WakeService持有
    ASR_DEVICE,        // ASR设备持有
}

/**
 * 与UI状态的映射关系
 */
AudioState.IDLE              → VoiceAssistantUIState.IDLE
AudioState.WAKE_LISTENING    → VoiceAssistantUIState.IDLE (用户感知不到)
AudioState.ASR_RECORDING     → VoiceAssistantUIState.LISTENING
AudioState.TTS_PLAYING       → VoiceAssistantUIState.SPEAKING
```

### 2.4 状态转换规则

```kotlin
状态转换矩阵：

From \ To      │ IDLE │ WAKE_LISTENING │ ASR_RECORDING │ TTS_PLAYING
──────────────┼──────┼────────────────┼───────────────┼─────────────
IDLE          │  ✓   │       ✓        │      ✗        │     ✗
WAKE_LISTENING│  ✓   │       ✓        │      ✓        │     ✗
ASR_RECORDING │  ✓   │       ✗        │      ✓        │     ✓
TTS_PLAYING   │  ✓   │       ✓        │      ✗        │     ✓

转换说明：
✓ = 允许直接转换
✗ = 禁止直接转换

关键规则：
1. ASR_RECORDING 可以直接转到 TTS_PLAYING（ASR识别完成，开始TTS）
2. TTS_PLAYING 必须先回到 IDLE 或 WAKE_LISTENING（不能直接到ASR）
3. ASR_RECORDING 不能直接转到 WAKE_LISTENING（需要先IDLE）
```

## 三、核心实现代码

### 3.1 AudioResourceManager.kt

```kotlin
package org.stypox.dicio.io

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 音频资源管理器 - 单例
 * 
 * 职责：
 * 1. 统一管理AudioRecord资源的申请和释放
 * 2. 管理全局音频状态机
 * 3. 监控TTS播放状态，播放时自动阻止录音
 * 4. 提供与UI状态的同步机制
 * 
 * 设计原则：
 * - 简单：只管理资源和状态，不涉及具体实现
 * - 安全：使用Mutex保证线程安全
 * - 清晰：明确的状态转换规则
 */
object AudioResourceManager {
    private const val TAG = "AudioResourceManager"

    // ========== 状态定义 ==========
    
    enum class AudioState {
        IDLE,              // 空闲
        WAKE_LISTENING,    // WakeService监听中
        ASR_RECORDING,     // ASR录音中
        TTS_PLAYING        // TTS播放中（禁止录音）
    }

    enum class AudioOwner {
        NONE,
        WAKE_SERVICE,
        ASR_DEVICE
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
     */
    suspend fun requestMicrophone(owner: AudioOwner): Boolean {
        return resourceMutex.withLock {
            // TTS播放时拒绝所有录音请求
            if (isTtsPlaying.get()) {
                Log.w(TAG, "❌ [$owner] 请求麦克风被拒绝：TTS正在播放")
                return@withLock false
            }

            // 检查是否已经是持有者
            if (_currentOwner.value == owner) {
                Log.d(TAG, "✅ [$owner] 已持有麦克风，无需重复申请")
                return@withLock true
            }

            // 如果有其他持有者，先释放
            if (_currentOwner.value != AudioOwner.NONE) {
                Log.w(TAG, "⚠️ [$owner] 请求麦克风，但当前持有者是 ${_currentOwner.value}，强制释放")
                forceRelease(_currentOwner.value)
            }

            // 分配资源
            _currentOwner.value = owner
            
            // 更新状态
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
     */
    suspend fun releaseMicrophone(owner: AudioOwner) {
        resourceMutex.withLock {
            if (_currentOwner.value != owner) {
                Log.w(TAG, "⚠️ [$owner] 尝试释放麦克风，但当前持有者是 ${_currentOwner.value}")
                return@withLock
            }

            _currentOwner.value = AudioOwner.NONE
            
            // 如果TTS正在播放，保持TTS_PLAYING状态
            val newState = if (isTtsPlaying.get()) {
                AudioState.TTS_PLAYING
            } else {
                AudioState.IDLE
            }
            
            transitionTo(newState, "[$owner] 释放麦克风")
            
            Log.i(TAG, "✅ [$owner] 释放麦克风资源")
        }
    }

    /**
     * 通知TTS播放开始
     * 会立即阻止所有录音
     */
    suspend fun notifyTtsStart() {
        resourceMutex.withLock {
            if (isTtsPlaying.getAndSet(true)) {
                Log.d(TAG, "⚠️ TTS已在播放中")
                return@withLock
            }

            Log.i(TAG, "🔊 TTS播放开始")
            
            // 保存当前持有者（用于TTS结束后恢复）
            val previousOwner = _currentOwner.value
            
            // 如果有录音在进行，通知暂停
            if (previousOwner != AudioOwner.NONE) {
                Log.w(TAG, "⏸️ TTS播放，通知 [$previousOwner] 暂停录音")
                notifyTtsListeners(true)
            }
            
            transitionTo(AudioState.TTS_PLAYING, "TTS播放开始")
        }
    }

    /**
     * 通知TTS播放结束
     * 允许恢复录音
     */
    suspend fun notifyTtsEnd() {
        resourceMutex.withLock {
            if (!isTtsPlaying.getAndSet(false)) {
                Log.d(TAG, "⚠️ TTS本来就没在播放")
                return@withLock
            }

            Log.i(TAG, "🔇 TTS播放结束")
            
            // 通知监听者可以恢复
            notifyTtsListeners(false)
            
            // 根据当前持有者恢复状态
            val newState = when (_currentOwner.value) {
                AudioOwner.WAKE_SERVICE -> AudioState.WAKE_LISTENING
                AudioOwner.ASR_DEVICE -> AudioState.ASR_RECORDING
                AudioOwner.NONE -> AudioState.IDLE
            }
            
            transitionTo(newState, "TTS播放结束")
        }
    }

    /**
     * 检查是否可以录音
     */
    fun canRecord(): Boolean {
        return !isTtsPlaying.get()
    }

    /**
     * 获取当前状态
     */
    fun getCurrentState(): AudioState {
        return _audioState.value
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
     */
    private fun transitionTo(newState: AudioState, reason: String) {
        val oldState = _audioState.value
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
     */
    private fun isValidTransition(from: AudioState, to: AudioState): Boolean {
        return when (from) {
            AudioState.IDLE -> to in setOf(
                AudioState.IDLE,
                AudioState.WAKE_LISTENING
            )
            
            AudioState.WAKE_LISTENING -> to in setOf(
                AudioState.IDLE,
                AudioState.WAKE_LISTENING,
                AudioState.ASR_RECORDING
            )
            
            AudioState.ASR_RECORDING -> to in setOf(
                AudioState.IDLE,
                AudioState.ASR_RECORDING,
                AudioState.TTS_PLAYING
            )
            
            AudioState.TTS_PLAYING -> to in setOf(
                AudioState.IDLE,
                AudioState.WAKE_LISTENING,
                AudioState.TTS_PLAYING
            )
        }
    }

    /**
     * 强制释放资源（内部使用）
     */
    private fun forceRelease(owner: AudioOwner) {
        Log.w(TAG, "⚠️ 强制释放 [$owner] 的麦克风资源")
        // 这里只更新状态，实际的资源释放由组件自己处理
        // 通过状态变化通知组件释放资源
    }

    /**
     * 通知状态监听器
     */
    @Synchronized
    private fun notifyStateListeners(newState: AudioState) {
        val listeners = stateListeners.toList()
        listeners.forEach { listener ->
            try {
                listener(newState)
            } catch (e: Exception) {
                Log.e(TAG, "❌ 状态监听器执行失败", e)
            }
        }
    }

    /**
     * 通知TTS监听器
     */
    @Synchronized
    private fun notifyTtsListeners(isPlaying: Boolean) {
        val listeners = ttsListeners.toList()
        listeners.forEach { listener ->
            try {
                listener(isPlaying)
            } catch (e: Exception) {
                Log.e(TAG, "❌ TTS监听器执行失败", e)
            }
        }
        Log.d(TAG, "📢 通知 ${listeners.size} 个TTS监听器: ${if (isPlaying) "开始" else "结束"}")
    }
}
```

### 3.2 集成示例：WakeService

```kotlin
// WakeService.kt 的修改

class WakeService : Service() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 注册TTS状态监听
        lifecycleScope.launch {
            AudioResourceManager.addTtsListener { isPlaying ->
                if (isPlaying) {
                    pauseListeningForTts()
                } else {
                    resumeListeningAfterTts()
                }
            }
        }
    }
    
    override fun onDestroy() {
        // 移除监听
        lifecycleScope.launch {
            AudioResourceManager.removeTtsListener(::handleTtsState)
        }
        super.onDestroy()
    }
    
    private fun startListening() {
        scope.launch {
            // 请求麦克风资源
            val granted = AudioResourceManager.requestMicrophone(
                AudioResourceManager.AudioOwner.WAKE_SERVICE
            )
            
            if (!granted) {
                Log.w(TAG, "❌ 无法获取麦克风资源")
                return@launch
            }
            
            try {
                // 创建AudioRecord并开始录音
                audioRecord = createAudioRecord()
                audioRecord?.startRecording()
                
                while (listening.get()) {
                    // 检查是否可以录音
                    if (!AudioResourceManager.canRecord()) {
                        Thread.sleep(50)
                        continue
                    }
                    
                    // 正常录音和唤醒词检测
                    val ret = audioRecord?.read(audio, 0, audio.size)
                    if (ret > 0) {
                        val detected = wakeDevice.processFrame(audio)
                        if (detected) {
                            onWakeWordDetected()
                        }
                    }
                }
            } finally {
                // 释放资源
                AudioResourceManager.releaseMicrophone(
                    AudioResourceManager.AudioOwner.WAKE_SERVICE
                )
            }
        }
    }
    
    private fun onWakeWordDetected() {
        Log.i(TAG, "🎯 检测到唤醒词")
        
        // 停止监听并释放资源
        listening.set(false)
        scope.launch {
            AudioResourceManager.releaseMicrophone(
                AudioResourceManager.AudioOwner.WAKE_SERVICE
            )
        }
        
        // 启动ASR（延迟一下让资源完全释放）
        scope.launch {
            delay(100)
            sttInputDevice.tryLoad(skillEvaluator::processInputEvent)
        }
    }
    
    private fun pauseListeningForTts() {
        Log.d(TAG, "⏸️ TTS播放，暂停唤醒词监听")
        // 暂停读取AudioRecord，但不释放资源
    }
    
    private fun resumeListeningAfterTts() {
        Log.d(TAG, "▶️ TTS结束，恢复唤醒词监听")
        // 恢复读取AudioRecord
    }
}
```

### 3.3 集成示例：ASR设备

```kotlin
// SenseVoiceInputDevice.kt / SherpaOnnxSimulateInputDevice.kt

class SenseVoiceInputDevice(...) : SttInputDevice {
    
    private var ttsListener: ((Boolean) -> Unit)? = null
    
    override fun tryLoad(eventListener: ((InputEvent) -> Unit)?): Boolean {
        scope.launch {
            // 请求麦克风资源
            val granted = AudioResourceManager.requestMicrophone(
                AudioResourceManager.AudioOwner.ASR_DEVICE
            )
            
            if (!granted) {
                Log.w(TAG, "❌ 无法获取麦克风资源（TTS正在播放）")
                _uiState.value = SttState.ErrorLoading(Exception("音频资源被占用"))
                return@launch
            }
            
            // 注册TTS监听
            ttsListener = { isPlaying ->
                if (isPlaying) {
                    pauseRecording()
                } else {
                    resumeRecording()
                }
            }
            AudioResourceManager.addTtsListener(ttsListener!!)
            
            try {
                startRecording()
            } finally {
                // 清理
                ttsListener?.let { AudioResourceManager.removeTtsListener(it) }
                AudioResourceManager.releaseMicrophone(
                    AudioResourceManager.AudioOwner.ASR_DEVICE
                )
            }
        }
        return true
    }
    
    private suspend fun recordAudio() {
        audioRecord?.startRecording()
        
        while (isRecording.get()) {
            // 检查是否可以录音
            if (!AudioResourceManager.canRecord()) {
                Thread.sleep(50)
                continue
            }
            
            val ret = audioRecord?.read(buffer, 0, buffer.size)
            // 处理音频...
        }
    }
    
    override fun stopListening() {
        isRecording.set(false)
        scope.launch {
            AudioResourceManager.releaseMicrophone(
                AudioResourceManager.AudioOwner.ASR_DEVICE
            )
        }
    }
    
    private fun pauseRecording() {
        Log.d(TAG, "⏸️ TTS播放，暂停ASR录音")
        // 停止读取AudioRecord
    }
    
    private fun resumeRecording() {
        Log.d(TAG, "▶️ TTS结束，恢复ASR录音")
        // 继续读取AudioRecord
    }
}
```

### 3.4 集成示例：TTS设备

```kotlin
// SherpaOnnxTtsSpeechDevice.kt

class SherpaOnnxTtsSpeechDevice(...) : SpeechOutputDevice {
    
    override suspend fun speak(text: String): Boolean {
        // 通知TTS开始
        AudioResourceManager.notifyTtsStart()
        
        try {
            // 合成音频
            val audioData = generateAudio(text)
            
            // 播放音频
            playAudio(audioData)
            
            return true
        } finally {
            // 通知TTS结束
            AudioResourceManager.notifyTtsEnd()
        }
    }
    
    private fun playAudio(audioData: FloatArray) {
        val audioTrack = AudioTrack(...)
        
        try {
            audioTrack.play()
            audioTrack.write(audioData, ...)
            
            // 等待播放完成
            while (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                Thread.sleep(10)
            }
        } finally {
            audioTrack.stop()
            audioTrack.release()
        }
    }
}
```

## 四、与UI状态同步

### 4.1 状态桥接

```kotlin
// 在 VoiceAssistantStateProvider 或类似组件中

class VoiceAssistantStateProvider {
    
    init {
        // 监听AudioResourceManager的状态变化
        AudioResourceManager.addStateListener { audioState ->
            val uiState = mapToUIState(audioState)
            updateUIState(uiState)
        }
    }
    
    private fun mapToUIState(audioState: AudioResourceManager.AudioState): VoiceAssistantUIState {
        return when (audioState) {
            AudioResourceManager.AudioState.IDLE -> 
                VoiceAssistantUIState.IDLE
            
            AudioResourceManager.AudioState.WAKE_LISTENING -> 
                VoiceAssistantUIState.IDLE  // 用户感知不到后台监听
            
            AudioResourceManager.AudioState.ASR_RECORDING -> 
                VoiceAssistantUIState.LISTENING
            
            AudioResourceManager.AudioState.TTS_PLAYING -> 
                VoiceAssistantUIState.SPEAKING
        }
    }
}
```

### 4.2 悬浮球状态映射

```
AudioState           →  VoiceAssistantUIState  →  悬浮球动画
─────────────────────────────────────────────────────────────
IDLE                 →  IDLE                   →  呼吸动画
WAKE_LISTENING       →  IDLE                   →  呼吸动画（后台监听）
ASR_RECORDING        →  LISTENING              →  声波动画 + 实时文本
TTS_PLAYING          →  SPEAKING               →  说话动画 + TTS文本
```

## 五、实施步骤

### 5.1 Phase 1: 创建AudioResourceManager（1小时）
```bash
1. 创建 AudioResourceManager.kt
2. 实现基本的状态机和资源锁
3. 添加完整的日志
4. 编写单元测试
```

### 5.2 Phase 2: 集成TTS设备（30分钟）
```bash
1. 修改 SherpaOnnxTtsSpeechDevice
2. 在 speak() 开始时调用 notifyTtsStart()
3. 在播放结束时调用 notifyTtsEnd()
4. 测试TTS状态通知
```

### 5.3 Phase 3: 集成ASR设备（1小时）
```bash
1. 修改 SenseVoiceInputDevice 和 SherpaOnnxSimulateInputDevice
2. 在 tryLoad() 时请求麦克风资源
3. 在 stopListening() 时释放资源
4. 注册TTS监听器，实现暂停/恢复逻辑
5. 测试TTS打断ASR的场景
```

### 5.4 Phase 4: 集成WakeService（1小时）
```bash
1. 修改 WakeService.kt
2. 在 startListening() 时请求麦克风资源
3. 在检测到唤醒词时释放资源
4. 注册TTS监听器
5. 测试WakeService和ASR的切换
```

### 5.5 Phase 5: 状态同步（30分钟）
```bash
1. 在 VoiceAssistantStateProvider 中集成
2. 添加状态映射逻辑
3. 确保悬浮球UI正确响应
4. 测试UI状态同步
```

### 5.6 Phase 6: 测试和优化（2小时）
```bash
1. 测试完整流程
2. 测试边界情况
3. 压力测试
4. 性能优化
5. 错误处理完善
```

**总计：约6小时完成**

## 六、测试用例

### 6.1 正常流程测试
```
测试1: 完整对话流程
1. 启动WakeService → 状态: WAKE_LISTENING
2. 说"Hi Nudge" → 检测到唤醒词
3. WakeService停止 → 状态: IDLE (短暂)
4. ASR启动 → 状态: ASR_RECORDING
5. 说"今天天气怎么样" → ASR识别
6. ASR停止 → 状态: IDLE (短暂)
7. TTS开始播放 → 状态: TTS_PLAYING
8. TTS结束 → 状态: IDLE
9. WakeService恢复 → 状态: WAKE_LISTENING

预期结果: 
✅ 所有状态转换正确
✅ UI显示与实际状态一致
✅ 没有音频冲突
✅ 没有TTS回声
```

### 6.2 TTS回声测试
```
测试2: TTS回声问题修复验证
1. 唤醒并触发ASR
2. ASR识别失败
3. TTS播放 "Could you repeat?"
4. 检查：ASR是否停止录音
5. 检查：是否识别到TTS声音

预期结果:
✅ TTS播放时ASR完全停止录音
✅ TTS声音不会被ASR识别
✅ TTS结束后ASR可以恢复（如果需要）
```

### 6.3 资源竞争测试
```
测试3: WakeService和ASR切换
1. WakeService监听中
2. 快速触发多次唤醒词
3. 检查AudioRecord的创建/释放
4. 检查是否有资源泄漏

预期结果:
✅ 同一时刻只有一个AudioRecord
✅ 资源正确释放
✅ 没有崩溃或错误
```

### 6.4 边界情况测试
```
测试4: 用户主动中断
1. ASR录音中
2. 用户点击停止按钮
3. 检查资源释放
4. 检查状态恢复

测试5: TTS播放时触发唤醒词
1. TTS播放中
2. 说唤醒词
3. 检查：唤醒词是否被忽略（正确）
4. TTS结束后再次说唤醒词
5. 检查：唤醒词是否正确识别

测试6: 系统资源抢占
1. ASR录音中
2. 模拟电话来电
3. 检查：优雅降级
4. 检查：状态恢复
```

## 七、优势分析

### 7.1 解决的问题
```
✅ TTS回声问题：TTS播放时强制禁止录音
✅ 资源竞争：统一的资源管理和互斥锁
✅ 状态不一致：全局唯一的状态机
✅ 资源泄漏：明确的申请/释放机制
✅ UI不同步：直接桥接到UI状态
```

### 7.2 设计优势
```
✅ 简单：只有一个文件，约400行代码
✅ 清晰：明确的状态机和转换规则
✅ 安全：Mutex保证线程安全
✅ 最小改动：现有设备代码改动极小
✅ 可测试：状态和逻辑都可以单独测试
✅ 可维护：集中管理，便于调试
```

### 7.3 扩展性
```
✅ 添加新的ASR设备：实现相同的请求/释放接口
✅ 添加新的状态：在状态枚举中添加
✅ 添加优先级：扩展AudioOwner枚举
✅ 添加更多监听器：使用现有的监听机制
```

## 八、总结

这个设计方案的核心理念是：

> **单一音频资源管理者 + 清晰的状态机 = 简单可靠的解决方案**

通过引入一个轻量级的AudioResourceManager：
1. 统一管理所有音频资源的申请和释放
2. 维护全局唯一的状态机
3. 提供TTS保护机制（播放时禁止录音）
4. 与UI状态完美同步

**关键优势**：
- 只需要创建一个新文件
- 现有代码改动最小（每个组件只需添加几行代码）
- 立即解决TTS回声和资源竞争问题
- 状态清晰，易于调试和维护

**实施成本**：约6小时即可完成全部集成和测试

这是一个真正**简单、实用、有效**的重构方案！

