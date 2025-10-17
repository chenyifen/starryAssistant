# 统一音频与状态管理机制设计

## 问题分析

### 当前的混乱状态
```
WakeService AudioRecord ─┐
                         ├─→ 都想用麦克风 → 冲突！
ASR InputDevice AudioRecord ─┘

TTS 播放 ─────────────────┐
                          ├─→ TTS声音被ASR识别 → 回声问题！
ASR 还在录音 ────────────┘
```

### 核心问题
1. **音频资源竞争**：多个组件同时想用AudioRecord
2. **TTS回声**：TTS播放时ASR没暂停，识别到"Could you repeat"
3. **状态不一致**：没有统一的状态管理，各自为政

---

## 设计原则

1. ✅ **简单优先**：最小化改动，不重构现有代码
2. ✅ **集中管理**：一个地方管理所有音频和状态
3. ✅ **互斥锁**：同一时刻只有一个组件可以录音
4. ✅ **TTS优先**：TTS播放时自动暂停所有录音

---

## 解决方案：AudioCoordinator（音频协调器）

### 架构图

```
┌──────────────────────────────────────────────────────────┐
│              AudioCoordinator (单例)                      │
│          统一音频资源和状态管理                            │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  状态管理:                                               │
│  - currentState: IDLE / WAKE_LISTENING / ASR_RECORDING   │
│                  / TTS_PLAYING                           │
│                                                          │
│  音频锁管理:                                             │
│  - audioLock: Mutex                                      │
│  - currentOwner: WakeService / ASR / null               │
│                                                          │
│  TTS状态监听:                                            │
│  - isTtsPlaying: AtomicBoolean                          │
│  - ttsCallbacks: List<() -> Unit>                       │
│                                                          │
└──────────────────────────────────────────────────────────┘
           ↓              ↓              ↓
    ┌──────────┐   ┌──────────┐   ┌──────────┐
    │WakeService│   │ASR Device│   │TTS Device│
    └──────────┘   └──────────┘   └──────────┘
         ↓              ↓              ↓
    请求/释放      请求/释放        通知播放状态
    录音资源       录音资源
```

---

## 核心代码实现

### 1. AudioCoordinator.kt（核心协调器）

```kotlin
package org.stypox.dicio.io

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 音频协调器 - 统一管理音频资源和状态
 * 
 * 职责：
 * 1. 管理AudioRecord资源的申请和释放
 * 2. 监控TTS播放状态，播放时自动暂停所有录音
 * 3. 提供统一的状态查询接口
 */
object AudioCoordinator {
    private const val TAG = "AudioCoordinator"

    /**
     * 全局音频状态
     */
    enum class AudioState {
        IDLE,              // 空闲
        WAKE_LISTENING,    // WakeService正在监听
        ASR_RECORDING,     // ASR正在录音
        TTS_PLAYING        // TTS正在播放（此时禁止录音）
    }

    /**
     * 音频资源持有者
     */
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

    // ========== TTS状态 ==========
    private val isTtsPlaying = AtomicBoolean(false)
    private val ttsStateListeners = mutableListOf<(Boolean) -> Unit>()

    // ========== 音频资源锁 ==========
    @Volatile
    private var audioResourceLocked = false

    /**
     * 请求音频资源（录音权限）
     * 
     * @param owner 请求者
     * @return true=成功获取，false=被拒绝（TTS正在播放）
     */
    @Synchronized
    fun requestAudioResource(owner: AudioOwner): Boolean {
        // TTS播放时禁止所有录音
        if (isTtsPlaying.get()) {
            Log.w(TAG, "❌ TTS正在播放，拒绝 $owner 的录音请求")
            return false
        }

        // 如果有其他持有者，先释放
        if (_currentOwner.value != AudioOwner.NONE && _currentOwner.value != owner) {
            Log.w(TAG, "⚠️ ${_currentOwner.value} 还在使用音频资源，强制释放")
            releaseAudioResource(_currentOwner.value)
        }

        _currentOwner.value = owner
        audioResourceLocked = true

        when (owner) {
            AudioOwner.WAKE_SERVICE -> _audioState.value = AudioState.WAKE_LISTENING
            AudioOwner.ASR_DEVICE -> _audioState.value = AudioState.ASR_RECORDING
            AudioOwner.NONE -> _audioState.value = AudioState.IDLE
        }

        Log.d(TAG, "✅ $owner 获得音频资源，状态: ${_audioState.value}")
        return true
    }

    /**
     * 释放音频资源
     * 
     * @param owner 释放者（必须是当前持有者）
     */
    @Synchronized
    fun releaseAudioResource(owner: AudioOwner) {
        if (_currentOwner.value != owner) {
            Log.w(TAG, "⚠️ $owner 尝试释放音频资源，但当前持有者是 ${_currentOwner.value}")
            return
        }

        _currentOwner.value = AudioOwner.NONE
        audioResourceLocked = false
        _audioState.value = if (isTtsPlaying.get()) AudioState.TTS_PLAYING else AudioState.IDLE

        Log.d(TAG, "🔓 $owner 释放音频资源，状态: ${_audioState.value}")
    }

    /**
     * 通知TTS播放开始
     * 会自动暂停所有正在进行的录音
     */
    @Synchronized
    fun notifyTtsPlaybackStarted() {
        if (isTtsPlaying.getAndSet(true)) {
            Log.d(TAG, "⚠️ TTS已在播放中")
            return
        }

        Log.i(TAG, "🔊 TTS播放开始")
        
        // 保存之前的状态
        val previousState = _audioState.value
        val previousOwner = _currentOwner.value
        
        // 如果有录音在进行，暂停它
        if (previousOwner != AudioOwner.NONE) {
            Log.w(TAG, "⏸️ TTS播放，自动暂停 $previousOwner 的录音")
            
            // 通知监听者暂停录音
            notifyTtsStateChange(true)
        }

        _audioState.value = AudioState.TTS_PLAYING
        
        Log.d(TAG, "🎵 状态切换: $previousState → TTS_PLAYING (owner: $previousOwner → NONE)")
    }

    /**
     * 通知TTS播放结束
     * 自动恢复之前暂停的录音
     */
    @Synchronized
    fun notifyTtsPlaybackFinished() {
        if (!isTtsPlaying.getAndSet(false)) {
            Log.d(TAG, "⚠️ TTS本来就没在播放")
            return
        }

        Log.i(TAG, "🔇 TTS播放结束")

        // 通知监听者可以恢复录音
        notifyTtsStateChange(false)

        // 如果没有人持有音频资源，回到IDLE
        if (_currentOwner.value == AudioOwner.NONE) {
            _audioState.value = AudioState.IDLE
        } else {
            // 恢复之前的状态
            when (_currentOwner.value) {
                AudioOwner.WAKE_SERVICE -> _audioState.value = AudioState.WAKE_LISTENING
                AudioOwner.ASR_DEVICE -> _audioState.value = AudioState.ASR_RECORDING
                AudioOwner.NONE -> _audioState.value = AudioState.IDLE
            }
        }

        Log.d(TAG, "▶️ 状态恢复: ${_audioState.value}")
    }

    /**
     * 注册TTS状态监听器
     * 
     * @param listener (isPlaying: Boolean) -> Unit
     */
    @Synchronized
    fun registerTtsStateListener(listener: (Boolean) -> Unit) {
        ttsStateListeners.add(listener)
        Log.d(TAG, "📝 注册TTS状态监听器，当前总数: ${ttsStateListeners.size}")
    }

    /**
     * 注销TTS状态监听器
     */
    @Synchronized
    fun unregisterTtsStateListener(listener: (Boolean) -> Unit) {
        ttsStateListeners.remove(listener)
        Log.d(TAG, "🗑️ 注销TTS状态监听器，当前总数: ${ttsStateListeners.size}")
    }

    /**
     * 通知所有监听者TTS状态变化
     */
    private fun notifyTtsStateChange(isPlaying: Boolean) {
        val listeners = ttsStateListeners.toList() // 复制以避免并发修改
        listeners.forEach { listener ->
            try {
                listener(isPlaying)
            } catch (e: Exception) {
                Log.e(TAG, "❌ TTS状态监听器执行失败", e)
            }
        }
        Log.d(TAG, "📢 通知 ${listeners.size} 个监听器: TTS ${if (isPlaying) "开始" else "结束"}")
    }

    /**
     * 检查是否可以录音
     */
    fun canRecord(): Boolean {
        return !isTtsPlaying.get()
    }

    /**
     * 获取当前状态信息（调试用）
     */
    fun getStateInfo(): String {
        return """
            AudioCoordinator State:
            - Audio State: ${_audioState.value}
            - Current Owner: ${_currentOwner.value}
            - TTS Playing: ${isTtsPlaying.get()}
            - Resource Locked: $audioResourceLocked
            - Listeners: ${ttsStateListeners.size}
        """.trimIndent()
    }
}
```

---

### 2. WakeService集成

```kotlin
// app/src/main/kotlin/org/stypox/dicio/io/wake/WakeService.kt

class WakeService : LifecycleService() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 注册TTS状态监听
        AudioCoordinator.registerTtsStateListener { isPlaying ->
            if (isPlaying) {
                // TTS开始播放，暂停WakeService录音
                pauseForTts()
            } else {
                // TTS结束播放，恢复WakeService录音
                resumeAfterTts()
            }
        }
    }
    
    override fun onDestroy() {
        // 注销监听器
        AudioCoordinator.unregisterTtsStateListener(::handleTtsStateChange)
        super.onDestroy()
    }
    
    private fun startListening() {
        scope.launch(Dispatchers.IO) {
            // 请求音频资源
            if (!AudioCoordinator.requestAudioResource(AudioCoordinator.AudioOwner.WAKE_SERVICE)) {
                Log.w(TAG, "❌ 无法获取音频资源（TTS正在播放）")
                return@launch
            }
            
            try {
                // 创建AudioRecord并开始录音
                audioRecord?.startRecording()
                
                while (listening.get()) {
                    // 检查是否可以录音
                    if (!AudioCoordinator.canRecord()) {
                        Thread.sleep(50)
                        continue
                    }
                    
                    // 正常录音和唤醒词检测
                    val ret = audioRecord?.read(audio, 0, audio.size)
                    // ... 处理音频
                }
            } finally {
                // 释放音频资源
                AudioCoordinator.releaseAudioResource(AudioCoordinator.AudioOwner.WAKE_SERVICE)
            }
        }
    }
    
    private fun pauseForTts() {
        Log.d(TAG, "⏸️ TTS播放，暂停WakeService")
        // 不需要实际停止AudioRecord，只需要停止读取
    }
    
    private fun resumeAfterTts() {
        Log.d(TAG, "▶️ TTS结束，恢复WakeService")
        // AudioRecord继续读取
    }
}
```

---

### 3. ASR InputDevice集成

```kotlin
// SherpaOnnxSimulateInputDevice.kt / SenseVoiceInputDevice.kt

class SherpaOnnxSimulateInputDevice(...) : SttInputDevice {
    
    override fun tryLoad(eventListener: ((InputEvent) -> Unit)?): Boolean {
        // 请求音频资源
        if (!AudioCoordinator.requestAudioResource(AudioCoordinator.AudioOwner.ASR_DEVICE)) {
            Log.w(TAG, "❌ 无法获取音频资源（TTS正在播放）")
            return false
        }
        
        // 注册TTS状态监听
        val ttsListener: (Boolean) -> Unit = { isPlaying ->
            if (isPlaying) {
                pauseRecording()  // TTS开始，暂停录音
            } else {
                resumeRecording() // TTS结束，恢复录音
            }
        }
        AudioCoordinator.registerTtsStateListener(ttsListener)
        
        // 启动双协程
        scope.launch(Dispatchers.IO) {
            try {
                recordAudio()
            } finally {
                AudioCoordinator.unregisterTtsStateListener(ttsListener)
            }
        }
        
        scope.launch(Dispatchers.Default) {
            processAudio()
        }
        
        return true
    }
    
    private suspend fun recordAudio() = withContext(Dispatchers.IO) {
        audioRecord?.startRecording()
        
        while (isRecording.get()) {
            // 检查是否可以录音
            if (!AudioCoordinator.canRecord()) {
                Thread.sleep(50)
                continue
            }
            
            val ret = audioRecord?.read(audioBuffer, 0, audioBuffer.size)
            // ... 处理音频
        }
    }
    
    override fun stopListening() {
        // 释放音频资源
        AudioCoordinator.releaseAudioResource(AudioCoordinator.AudioOwner.ASR_DEVICE)
        // ... 其他清理
    }
    
    private fun pauseRecording() {
        Log.d(TAG, "⏸️ TTS播放，ASR暂停录音")
        // 停止读取AudioRecord
    }
    
    private fun resumeRecording() {
        Log.d(TAG, "▶️ TTS结束，ASR恢复录音")
        // 继续读取AudioRecord
    }
}
```

---

### 4. TTS Device集成

```kotlin
// SherpaOnnxTtsSpeechDevice.kt

class SherpaOnnxTtsSpeechDevice(...) : SpeechOutputDevice {
    
    override suspend fun speak(text: String): Boolean {
        // 通知TTS播放开始
        AudioCoordinator.notifyTtsPlaybackStarted()
        
        try {
            // 合成和播放TTS
            val audioData = generateAudio(text)
            playAudio(audioData)
            
            return true
        } finally {
            // 通知TTS播放结束
            AudioCoordinator.notifyTtsPlaybackFinished()
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

---

## 时序图

### 正常流程（无TTS）

```
WakeService          AudioCoordinator          ASR Device
    │                       │                       │
    ├─ requestAudioResource ─→│                     │
    │  (WAKE_SERVICE)         │                     │
    │←─────── true ───────────┤                     │
    │                       │                       │
    │  [录音中...]          │                       │
    │                       │                       │
    │  检测到唤醒词         │                       │
    │                       │                       │
    ├─ releaseAudioResource ─→│                     │
    │                       │                       │
    │                       │←─ requestAudioResource ─┤
    │                       │   (ASR_DEVICE)       │
    │                       ├────── true ─────────→│
    │                       │                       │
    │                       │   [ASR录音识别...]   │
    │                       │                       │
    │                       │←─ releaseAudioResource ─┤
    │                       │                       │
```

### TTS打断流程

```
ASR Device       AudioCoordinator       TTS Device       WakeService
    │                   │                    │                │
    │  [录音中...]       │                    │                │
    │                   │←─ notifyTtsPlaybackStarted ─┤        │
    │                   │                    │                │
    │                   ├─ 通知监听器: TTS开始 ─→│              │
    │←─ pauseRecording() ─┤                  │                │
    │                   │                    ├─ pauseForTts() ─→│
    │                   │                    │                │
    │  [暂停录音]        │   [TTS播放中...]   │   [暂停监听]  │
    │                   │                    │                │
    │                   │←─ notifyTtsPlaybackFinished ─┤       │
    │                   │                    │                │
    │                   ├─ 通知监听器: TTS结束 ─→│              │
    │←─ resumeRecording() ─┤                 │                │
    │                   │                    ├─ resumeAfterTts() ─→│
    │                   │                    │                │
    │  [恢复录音]        │                    │   [恢复监听]  │
```

---

## 优势分析

### ✅ 简单
- 只需添加一个新文件 `AudioCoordinator.kt`
- 现有代码改动最小（只在关键点添加几行代码）
- 不需要重构整个架构

### ✅ 有效
- **解决TTS回声问题**：TTS播放时自动暂停所有录音
- **解决资源竞争**：统一的锁机制
- **状态一致**：全局唯一的状态管理

### ✅ 可扩展
- 未来添加新的ASR Device，只需实现接口即可
- 可以轻松添加更多状态监听器
- 可以添加优先级机制

### ✅ 可调试
- 集中的日志输出
- `getStateInfo()` 方法可随时查询当前状态
- 清晰的状态转换日志

---

## 实施步骤

### Phase 1: 创建协调器（30分钟）
1. 创建 `AudioCoordinator.kt`
2. 实现基本的锁机制
3. 添加TTS状态通知

### Phase 2: 集成TTS Device（15分钟）
1. 在 `SherpaOnnxTtsSpeechDevice.speak()` 开始时调用 `notifyTtsPlaybackStarted()`
2. 在播放结束时调用 `notifyTtsPlaybackFinished()`

### Phase 3: 集成ASR Device（30分钟）
1. 在 `tryLoad()` 时调用 `requestAudioResource()`
2. 在 `stopListening()` 时调用 `releaseAudioResource()`
3. 注册TTS状态监听器
4. 实现 `pauseRecording()` 和 `resumeRecording()`

### Phase 4: 集成WakeService（30分钟）
1. 在 `startListening()` 时调用 `requestAudioResource()`
2. 在停止时调用 `releaseAudioResource()`
3. 注册TTS状态监听器
4. 实现暂停/恢复逻辑

### Phase 5: 测试（1小时）
1. 测试TTS播放时ASR是否暂停
2. 测试TTS结束后ASR是否恢复
3. 测试WakeService和ASR的切换
4. 测试边界情况

**总计：约2.5小时完成**

---

## 测试用例

### 测试1：TTS回声问题
```
1. 唤醒词触发 → ASR启动
2. ASR识别失败 → TTS播放 "Could you repeat?"
3. 预期：TTS播放时ASR暂停，不会识别到TTS声音
4. TTS结束 → ASR自动恢复录音
```

### 测试2：WakeService和ASR切换
```
1. WakeService监听中
2. 检测到唤醒词 → WakeService释放资源
3. ASR获取资源开始录音
4. ASR完成 → 释放资源
5. WakeService恢复监听
```

### 测试3：多次TTS打断
```
1. ASR录音中
2. TTS播放 "Could you repeat?" → ASR暂停
3. TTS结束 → ASR恢复
4. 用户再次说话
5. ASR识别失败 → TTS再次播放 → ASR再次暂停
6. 循环测试稳定性
```

---

## 总结

这个设计是**最简单**且**最有效**的解决方案：

1. **一个新文件**：`AudioCoordinator.kt`（约200行代码）
2. **最小改动**：每个组件只需添加3-5行代码
3. **立即见效**：解决TTS回声和资源竞争问题
4. **易于维护**：集中管理，清晰的职责划分

核心理念：
> **不是让每个组件自己管理音频，而是让一个协调器统一管理**

这样就避免了各自为政导致的混乱，同时保持了系统的简单性。

