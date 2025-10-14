# SenseVoiceInputDevice 重构方案

## 📋 文档信息

- **创建日期**: 2024-10-09
- **当前版本**: v1.0
- **状态**: 方案设计阶段
- **目标**: 修复协程时序混乱和功能正确性问题

---

## 🎯 重构目标

### 主要问题

当前 `SenseVoiceInputDevice` 存在以下严重问题：

1. **协程作用域管理混乱**
   - `vadJob` 变量被重复赋值，导致超时监控Job丢失引用
   - 超时监控协程和音频处理协程使用同一个变量
   - 导致"未达到30秒却显示超时"的问题

2. **异步调用时序问题**
   - `startListening()` 在协程中调用 `startRecording()`
   - `startRecording()` 内部又创建协程，导致时序不可控
   - 超时Job可能在错误的时间点创建

3. **协程取消传播错误**
   - `stopListeningAndProcess()` 调用 `stopRecording()` 取消协程
   - 但 `performFinalRecognition()` 还在同一作用域运行
   - 导致 `JobCancellationException` 中断识别流程

4. **职责划分不清**
   - `startListening()` 和 `startRecording()` 职责重叠
   - 业务逻辑层和硬件层耦合

### 重构目标

1. ✅ **清晰的协程生命周期管理**
2. ✅ **独立的Job变量管理**
3. ✅ **正确的取消传播机制**
4. ✅ **明确的职责分离**
5. ✅ **参考官方demo的最佳实践**

---

## 📚 参考实现分析

### 官方Demo (SherpaOnnxSimulateStreamingAsr)

#### 架构特点

```kotlin
// 1. 简单的状态管理
var isStarted by remember { mutableStateOf(false) }

// 2. 单一的Channel通信
val samplesChannel = Channel<FloatArray>(capacity = Channel.UNLIMITED)

// 3. 两个独立的协程
// 协程1: 音频采集 (IO Dispatcher)
CoroutineScope(Dispatchers.IO).launch {
    audioRecord?.startRecording()
    while (isStarted) {
        val ret = audioRecord?.read(buffer, 0, buffer.size)
        samplesChannel.send(samples)
    }
}

// 协程2: 音频处理和识别 (Default Dispatcher)
CoroutineScope(Dispatchers.Default).launch {
    while (isStarted) {
        for (s in samplesChannel) {
            // VAD处理
            // 实时识别
            // 最终识别
        }
    }
}

// 4. 简单的停止逻辑
isStarted = false  // 停止标志
audioRecord?.stop()
audioRecord?.release()
```

#### 关键设计原则

1. **状态驱动**: 使用简单的boolean标志控制流程
2. **协程独立**: 两个协程互不干扰，通过Channel通信
3. **资源管理**: 明确的资源创建和释放时机
4. **无Job引用**: 不需要保存Job引用，依赖状态标志控制

---

## 🔧 重构方案设计

### 方案A: 参考官方Demo的简化设计 (推荐)

#### 核心思想

完全参考官方demo的设计模式，使用状态驱动而非Job管理。

#### 架构设计

```kotlin
class SenseVoiceInputDevice private constructor(...) : SttInputDevice {
    
    // ========== 状态管理 ==========
    private val isInitialized = AtomicBoolean(false)
    private val isRecording = AtomicBoolean(false)  // 主控制标志
    
    // ========== 硬件资源 ==========
    private var audioRecord: AudioRecord? = null
    private var senseVoiceRecognizer: SenseVoiceRecognizer? = null
    private var vad: Vad? = null
    
    // ========== 通信Channel ==========
    private var samplesChannel = Channel<FloatArray>(capacity = Channel.UNLIMITED)
    
    // ========== 协程作用域 ==========
    private var scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // ========== 音频缓冲 ==========
    private val audioBuffer = arrayListOf<Float>()
    private var bufferOffset = 0
    
    // ========== VAD状态 ==========
    private var isSpeechDetected = false
    private var speechStartTime = 0L
    private var lastRecognitionTime = 0L
    
    // ========== 配置常量 ==========
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val VAD_WINDOW_SIZE = 512
        private const val RECOGNITION_INTERVAL_MS = 200L
        private const val SPEECH_TIMEOUT_MS = 3000L
        private const val MAX_RECORDING_DURATION_MS = 30000L
    }
}
```

#### 核心方法重构

##### 1. 启动录制

```kotlin
/**
 * 启动录制和识别
 * 参考官方demo的简洁设计
 */
override fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean {
    Log.d(TAG, "🚀 开始语音识别流程")
    
    // 检查初始化状态
    if (!isInitialized.get() || senseVoiceRecognizer == null) {
        Log.e(TAG, "❌ 识别器未初始化")
        return false
    }
    
    // 防止重复启动
    if (isRecording.get()) {
        Log.w(TAG, "⚠️ 已在录制中")
        return true
    }
    
    // 保存事件监听器
    this.eventListener = thenStartListeningEventListener
    
    // 设置录制标志
    isRecording.set(true)
    
    // 重置状态
    resetRecordingState()
    
    // 更新UI状态
    _uiState.value = SttState.Listening
    
    // 启动音频采集协程
    scope.launch(Dispatchers.IO) {
        recordAudio()
    }
    
    // 启动音频处理协程
    scope.launch(Dispatchers.Default) {
        processAudio()
    }
    
    return true
}

/**
 * 重置录制状态
 */
private fun resetRecordingState() {
    audioBuffer.clear()
    bufferOffset = 0
    isSpeechDetected = false
    speechStartTime = 0L
    lastRecognitionTime = 0L
    vad?.reset()
    
    // 重新创建Channel
    samplesChannel.close()
    samplesChannel = Channel(capacity = Channel.UNLIMITED)
}
```

##### 2. 音频采集协程

```kotlin
/**
 * 音频采集协程 - 运行在IO Dispatcher
 * 参考官方demo的实现
 */
private suspend fun recordAudio() = withContext(Dispatchers.IO) {
    try {
        Log.d(TAG, "🎵 启动音频采集")
        
        // 创建AudioRecord
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )
        
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "❌ AudioRecord初始化失败")
            isRecording.set(false)
            return@withContext
        }
        
        // 开始录制
        audioRecord?.startRecording()
        
        // 音频采集缓冲区 (100ms = 0.1秒)
        val interval = 0.1
        val frameSize = (interval * SAMPLE_RATE).toInt()
        val buffer = ShortArray(frameSize)
        
        // 持续采集直到停止标志
        while (isRecording.get()) {
            val ret = audioRecord?.read(buffer, 0, buffer.size) ?: -1
            
            if (ret > 0) {
                // 转换为Float并归一化
                val samples = FloatArray(ret) { i ->
                    buffer[i].toFloat() / 32768.0f
                }
                
                // 发送到处理通道
                samplesChannel.send(samples)
            } else if (ret < 0) {
                Log.e(TAG, "❌ 音频读取错误: $ret")
                break
            }
        }
        
        // 发送结束信号
        samplesChannel.send(FloatArray(0))
        
        Log.d(TAG, "🏁 音频采集结束")
        
    } catch (e: Exception) {
        Log.e(TAG, "❌ 音频采集异常", e)
    } finally {
        // 清理AudioRecord
        cleanupAudioRecord()
    }
}

/**
 * 清理AudioRecord资源
 */
private fun cleanupAudioRecord() {
    audioRecord?.let {
        try {
            if (it.state == AudioRecord.STATE_INITIALIZED) {
                it.stop()
            }
            it.release()
        } catch (e: Exception) {
            Log.e(TAG, "清理AudioRecord失败", e)
        }
    }
    audioRecord = null
}
```

##### 3. 音频处理协程

```kotlin
/**
 * 音频处理协程 - 运行在Default Dispatcher
 * 参考官方demo的VAD和识别逻辑
 */
private suspend fun processAudio() = withContext(Dispatchers.Default) {
    try {
        Log.d(TAG, "🔄 启动音频处理")
        
        val maxDuration = MAX_RECORDING_DURATION_MS
        val startTime = System.currentTimeMillis()
        
        while (isRecording.get()) {
            for (samples in samplesChannel) {
                // 检查是否为结束信号
                if (samples.isEmpty()) {
                    Log.d(TAG, "📥 收到结束信号")
                    break
                }
                
                // 添加到缓冲区
                audioBuffer.addAll(samples.toList())
                
                // VAD处理
                processVAD()
                
                // 检查超时
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed > maxDuration) {
                    Log.d(TAG, "⏰ 达到最大录制时间")
                    break
                }
                
                // 实时识别
                performPartialRecognition()
                
                // 检查静音超时
                if (isSpeechDetected) {
                    val silenceDuration = System.currentTimeMillis() - speechStartTime
                    if (silenceDuration > SPEECH_TIMEOUT_MS) {
                        Log.d(TAG, "🔇 检测到静音超时")
                        break
                    }
                }
            }
            
            // 如果退出了for循环，说明需要停止
            break
        }
        
        Log.d(TAG, "🎯 开始最终识别")
        
        // 执行最终识别
        performFinalRecognition()
        
    } catch (e: CancellationException) {
        Log.d(TAG, "🛑 音频处理被取消")
        throw e  // 重新抛出取消异常
    } catch (e: Exception) {
        Log.e(TAG, "❌ 音频处理异常", e)
        withContext(Dispatchers.Main) {
            eventListener?.invoke(InputEvent.Error(e))
        }
    } finally {
        // 确保状态重置
        isRecording.set(false)
        _uiState.value = SttState.Loaded
        Log.d(TAG, "🏁 音频处理结束")
    }
}

/**
 * VAD处理
 */
private fun processVAD() {
    while (bufferOffset + VAD_WINDOW_SIZE < audioBuffer.size) {
        val vadSamples = audioBuffer.subList(
            bufferOffset,
            bufferOffset + VAD_WINDOW_SIZE
        ).toFloatArray()
        
        vad?.acceptWaveform(vadSamples)
        bufferOffset += VAD_WINDOW_SIZE
        
        // 检测语音开始
        if (!isSpeechDetected && vad?.isSpeechDetected() == true) {
            isSpeechDetected = true
            speechStartTime = System.currentTimeMillis()
            Log.d(TAG, "🎙️ 检测到语音开始")
        }
    }
}

/**
 * 实时部分识别
 */
private suspend fun performPartialRecognition() {
    if (!isSpeechDetected) return
    
    val currentTime = System.currentTimeMillis()
    val elapsed = currentTime - lastRecognitionTime
    
    // 每200ms执行一次识别
    if (elapsed >= RECOGNITION_INTERVAL_MS && bufferOffset > 0) {
        val audioData = audioBuffer.subList(0, bufferOffset).toFloatArray()
        val text = senseVoiceRecognizer?.recognize(audioData) ?: ""
        
        if (text.isNotBlank()) {
            withContext(Dispatchers.Main) {
                eventListener?.invoke(InputEvent.Partial(text))
            }
        }
        
        lastRecognitionTime = currentTime
    }
}

/**
 * 最终识别
 * 使用NonCancellable确保不被中断
 */
private suspend fun performFinalRecognition() = withContext(NonCancellable) {
    try {
        // 检查是否有有效音频
        if (audioBuffer.isEmpty() || !isSpeechDetected) {
            withContext(Dispatchers.Main) {
                eventListener?.invoke(InputEvent.None)
            }
            return@withContext
        }
        
        Log.d(TAG, "🚀 执行最终识别，音频长度: ${audioBuffer.size}样本")
        
        // 执行识别
        val audioData = audioBuffer.toFloatArray()
        val text = senseVoiceRecognizer?.recognize(audioData) ?: ""
        
        Log.d(TAG, "✅ 识别结果: \"$text\"")
        
        // 发送结果
        withContext(Dispatchers.Main) {
            if (text.isNotBlank()) {
                eventListener?.invoke(InputEvent.Final(listOf(Pair(text, 1.0f))))
            } else {
                eventListener?.invoke(InputEvent.None)
            }
        }
        
    } catch (e: Exception) {
        Log.e(TAG, "❌ 最终识别异常", e)
        withContext(Dispatchers.Main) {
            eventListener?.invoke(InputEvent.Error(e))
        }
    }
}
```

##### 4. 停止录制

```kotlin
/**
 * 停止录制
 * 简单设置标志，让协程自然结束
 */
override fun stopListening() {
    if (!isRecording.get()) {
        return
    }
    
    Log.d(TAG, "🛑 停止语音识别")
    
    // 设置停止标志
    isRecording.set(false)
    
    // 注意：不需要手动取消协程或清理资源
    // 协程会通过isRecording标志自然结束
    // 资源会在finally块中清理
}
```

---

### 方案B: 改进当前设计 (备选)

如果不想大幅度重构，可以改进当前设计：

#### 核心改进

```kotlin
// 1. 分离Job变量
private var recordingJob: Job? = null          // 音频采集
private var audioProcessingJob: Job? = null    // 音频处理
private var timeoutJob: Job? = null            // 超时监控

// 2. 修改startListening
private fun startListening(): Boolean {
    isListening.set(true)
    resetVadState()
    _uiState.value = SttState.Listening
    
    // 在协程中启动
    scope.launch {
        if (startRecording()) {
            // 录制成功后才启动超时监控
            startTimeoutMonitor()
        }
    }
    return true
}

// 3. 独立的超时监控
private fun startTimeoutMonitor() {
    timeoutJob = scope.launch {
        delay(MAX_RECORDING_DURATION_MS)
        Log.d(TAG, "⏰ 达到最大录制时间")
        stopListeningAndProcess()
    }
}

// 4. 修改startRecording
private suspend fun startRecording(): Boolean {
    // ... 创建AudioRecord ...
    
    // 启动采集协程
    recordingJob = scope.launch(Dispatchers.IO) {
        recordAudioData()
    }
    
    // 启动处理协程（重命名）
    audioProcessingJob = scope.launch(Dispatchers.Default) {
        processAudioForRecognition()
    }
    
    return true
}

// 5. 修改stopListening
override fun stopListening() {
    isListening.set(false)
    
    // 取消所有Job
    timeoutJob?.cancel()
    timeoutJob = null
    
    recordingJob?.cancel()
    recordingJob = null
    
    audioProcessingJob?.cancel()
    audioProcessingJob = null
    
    cleanupAudioRecord()
    _uiState.value = SttState.Loaded
}

// 6. 使用NonCancellable保护最终识别
private suspend fun performFinalRecognition() = withContext(NonCancellable) {
    try {
        // ... 识别逻辑 ...
    } catch (e: Exception) {
        // ... 错误处理 ...
    }
}
```

---

## 📊 方案对比

| 特性 | 方案A (简化设计) | 方案B (改进设计) |
|------|-----------------|-----------------|
| **复杂度** | ⭐⭐ 低 | ⭐⭐⭐ 中 |
| **与官方Demo一致性** | ✅ 高度一致 | ⚠️ 部分一致 |
| **改动范围** | ⭐⭐⭐⭐ 大 | ⭐⭐ 小 |
| **维护性** | ✅ 优秀 | ⚠️ 良好 |
| **Bug风险** | ✅ 低 | ⚠️ 中 |
| **Job管理** | ✅ 无需管理 | ⚠️ 需要管理3个Job |
| **资源清理** | ✅ 自动化 | ⚠️ 手动管理 |
| **取消传播** | ✅ 清晰 | ⚠️ 需要NonCancellable |
| **代码行数** | ⬇️ 减少约20% | ➡️ 略有增加 |

---

## 🎯 推荐方案

### ✅ 推荐：方案A (简化设计)

#### 理由

1. **与官方Demo高度一致**
   - 使用相同的设计模式
   - 降低理解和维护成本
   - 验证过的稳定架构

2. **更简单的状态管理**
   - 单一的 `isRecording` 标志
   - 无需管理多个Job引用
   - 状态驱动而非Job驱动

3. **更清晰的资源管理**
   - 资源创建和释放时机明确
   - finally块保证清理
   - 减少资源泄漏风险

4. **更少的Bug风险**
   - 消除Job覆盖问题
   - 消除取消传播问题
   - 消除时序混乱问题

5. **更好的可维护性**
   - 代码结构清晰
   - 职责划分明确
   - 易于理解和修改

---

## 📝 实施计划

### 阶段1: 准备工作 (1天)

- [ ] 备份当前实现
- [ ] 创建测试分支
- [ ] 准备测试用例
- [ ] 审查官方demo代码

### 阶段2: 核心重构 (2-3天)

- [ ] 重构状态管理
- [ ] 重构音频采集协程
- [ ] 重构音频处理协程
- [ ] 重构停止逻辑
- [ ] 移除不必要的Job变量

### 阶段3: 测试验证 (2天)

- [ ] 单元测试
- [ ] 集成测试
- [ ] 性能测试
- [ ] 边界情况测试

### 阶段4: 优化完善 (1天)

- [ ] 代码审查
- [ ] 文档更新
- [ ] 日志优化
- [ ] 性能优化

---

## 🔍 详细实施步骤

### 步骤1: 简化状态管理

**当前代码:**
```kotlin
private val isInitialized = AtomicBoolean(false)
private val isListening = AtomicBoolean(false)
private val isRecording = AtomicBoolean(false)
private var recordingJob: Job? = null
private var vadJob: Job? = null
```

**重构后:**
```kotlin
private val isInitialized = AtomicBoolean(false)
private val isRecording = AtomicBoolean(false)  // 主控制标志
// 移除 isListening（与isRecording合并）
// 移除 Job变量（不再需要）
```

### 步骤2: 重构启动方法

**当前代码:**
```kotlin
private fun startListening(): Boolean {
    isListening.set(true)
    scope.launch {
        if (startRecording()) {
            vadJob = scope.launch { /* 超时监控 */ }
        }
    }
    return true
}

private suspend fun startRecording(): Boolean {
    // ...
    vadJob = scope.launch { /* 音频处理 */ }  // 覆盖了！
    return true
}
```

**重构后:**
```kotlin
override fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean {
    if (!isRecording.compareAndSet(false, true)) {
        return true  // 已在录制
    }
    
    this.eventListener = thenStartListeningEventListener
    resetRecordingState()
    _uiState.value = SttState.Listening
    
    // 直接启动两个协程
    scope.launch(Dispatchers.IO) { recordAudio() }
    scope.launch(Dispatchers.Default) { processAudio() }
    
    return true
}
```

### 步骤3: 重构音频处理

**关键改进:**

1. **移除Job变量依赖**
2. **使用状态标志控制**
3. **在processAudio中处理超时**
4. **使用NonCancellable保护最终识别**

### 步骤4: 简化停止逻辑

**当前代码:**
```kotlin
override fun stopListening() {
    isListening.set(false)
    stopRecording()  // 取消Job
    vadJob?.cancel()
    vadJob = null
    _uiState.value = SttState.Loaded
}
```

**重构后:**
```kotlin
override fun stopListening() {
    if (!isRecording.get()) return
    
    Log.d(TAG, "🛑 停止语音识别")
    isRecording.set(false)
    
    // 协程会通过isRecording标志自然结束
    // 资源会在finally块中清理
}
```

---

## ⚠️ 风险评估

### 高风险项

1. **大规模重构**
   - 风险: 引入新Bug
   - 缓解: 充分测试，分步实施

2. **行为变化**
   - 风险: 用户体验变化
   - 缓解: 保持接口一致，渐进式发布

### 中风险项

1. **性能影响**
   - 风险: 识别延迟变化
   - 缓解: 性能测试对比

2. **资源管理**
   - 风险: 内存泄漏
   - 缓解: 仔细审查finally块

### 低风险项

1. **兼容性**
   - 风险: 破坏现有功能
   - 缓解: 接口保持不变

---

## ✅ 验收标准

### 功能标准

- [ ] 语音识别正常工作
- [ ] 实时反馈正常显示
- [ ] 最终结果准确
- [ ] 超时机制正确（30秒）
- [ ] 静音检测正常
- [ ] 错误处理完善

### 性能标准

- [ ] 识别延迟 < 300ms
- [ ] 内存占用合理
- [ ] 无内存泄漏
- [ ] CPU使用率正常

### 代码质量标准

- [ ] 无编译警告
- [ ] 无linter错误
- [ ] 代码覆盖率 > 80%
- [ ] 文档完整

### 稳定性标准

- [ ] 连续运行1小时无崩溃
- [ ] 多次启动停止无异常
- [ ] 边界情况处理正确
- [ ] 日志输出清晰

---

## 📚 参考资料

### 官方Demo

- 项目: SherpaOnnxSimulateStreamingAsr
- 路径: `/Users/user/code/sherpa-onnx/android/SherpaOnnxSimulateStreamingAsr`
- 关键文件: `app/src/main/java/com/k2fsa/sherpa/onnx/simulate/streaming/asr/screens/Home.kt`

### Kotlin协程

- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Coroutine Context and Dispatchers](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html)
- [Cancellation and Timeouts](https://kotlinlang.org/docs/cancellation-and-timeouts.html)

### Android音频

- [AudioRecord Documentation](https://developer.android.com/reference/android/media/AudioRecord)
- [Audio Capture](https://developer.android.com/guide/topics/media/audio-capture)

---

## 💬 待讨论问题

### 技术决策

1. **是否完全移除startListening和startRecording的分离？**
   - 优点: 更简单
   - 缺点: 失去分层
   - 建议: 合并为单一的tryLoad实现

2. **是否需要保留VAD功能？**
   - 当前: VAD暂时禁用
   - 建议: 保留接口，待模型兼容性解决后启用

3. **超时机制的实现方式？**
   - 方案A: 在processAudio中检查时间
   - 方案B: 独立的超时Job
   - 建议: 方案A（与demo一致）

### 用户体验

1. **识别间隔是否需要可配置？**
   - 当前: 固定200ms
   - 建议: 后期可考虑配置

2. **错误提示是否需要优化？**
   - 当前: 简单的错误信息
   - 建议: 提供更友好的提示

---

## 📅 时间表

| 阶段 | 任务 | 预计时间 | 负责人 |
|------|------|---------|--------|
| 准备 | 代码审查和备份 | 1天 | TBD |
| 开发 | 核心重构 | 2-3天 | TBD |
| 测试 | 功能和性能测试 | 2天 | TBD |
| 优化 | 代码优化和文档 | 1天 | TBD |
| **总计** | | **6-7天** | |

---

## 🔗 相关文档

- [项目架构总览](./01-项目架构总览.md)
- [语音处理系统](./04-语音处理系统.md)
- [SenseVoice集成总结](./SENSEVOICE_INTEGRATION_SUMMARY.md)

---

## 📝 更新日志

### v1.0 (2024-10-09)

- ✅ 完成问题分析
- ✅ 研究官方demo实现
- ✅ 设计方案A和方案B
- ✅ 确定推荐方案
- ✅ 制定实施计划

---

## 👥 审批流程

| 角色 | 姓名 | 审批意见 | 签字 | 日期 |
|------|------|---------|------|------|
| 设计者 | AI Assistant | 待审批 | | |
| 审批者 | User | 待审批 | | |
| 实施者 | TBD | 待确认 | | |

---

**注意**: 本文档为设计方案，需经过审批后才能开始实施。请在开始重构前确认方案细节。

