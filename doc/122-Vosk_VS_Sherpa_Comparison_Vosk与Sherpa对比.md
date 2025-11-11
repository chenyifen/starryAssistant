# Vosk vs SimulateStreamingASR 对比分析

## 总览对比

| 特性 | Vosk | SimulateStreamingASR (Sherpa-ONNX) |
|------|------|-----------------------------------|
| **音频采集** | 使用Vosk库内置SpeechService | 自己实现AudioRecord采集 |
| **VAD** | 内置在Vosk识别器中 | 独立的Silero VAD模型 |
| **识别模式** | 在线流式识别 | 离线批量识别（模拟流式） |
| **协程架构** | 单协程（状态机管理） | 双协程（采集+处理分离） |
| **状态复杂度** | 14个状态 | 5个状态 |
| **模型管理** | 需要下载/解压 | 预置在assets |
| **音频控制** | 交给Vosk库管理 | 完全自主控制 |

---

## 一、音频采集机制对比

### 1.1 Vosk的音频采集

```
┌─────────────────────────────────────────────────────────┐
│               VoskInputDevice                           │
│   (仅负责状态管理和生命周期)                              │
└─────────────────────────────────────────────────────────┘
                        │
                        │ startListening()
                        ▼
┌─────────────────────────────────────────────────────────┐
│           org.vosk.android.SpeechService                │
│         (Vosk库内置的音频服务)                            │
│                                                         │
│  ┌─────────────────────────────────────────┐           │
│  │  内部实现（黑盒）:                        │           │
│  │  - AudioRecord 创建和管理                │           │
│  │  - 音频循环读取                          │           │
│  │  - VAD检测（内置）                       │           │
│  │  - 实时识别                              │           │
│  │  - 回调触发                              │           │
│  └─────────────────────────────────────────┘           │
└─────────────────────────────────────────────────────────┘
                        │
                        │ RecognitionListener回调
                        ▼
┌─────────────────────────────────────────────────────────┐
│                 VoskListener                            │
│   (处理回调事件并转换为InputEvent)                        │
│   - onPartialResult()  → InputEvent.Partial            │
│   - onResult()         → InputEvent.Final              │
│   - onError()          → InputEvent.Error              │
│   - onTimeout()        → 停止识别                       │
└─────────────────────────────────────────────────────────┘
```

**特点**:
- ✅ **简单**: 音频采集完全由Vosk库处理，开发者无需关心
- ✅ **可靠**: Vosk库经过充分测试，稳定性好
- ❌ **黑盒**: 无法控制音频采集细节（采样率固定44.1kHz）
- ❌ **不透明**: 无法获取原始音频数据进行调试
- ❌ **耦合**: VAD和识别耦合在一起，无法单独调整

**代码示例**:
```kotlin
// VoskInputDevice.kt (简化版)
private fun startListening(
    speechService: SpeechService,
    eventListener: (InputEvent) -> Unit,
) {
    _state.value = Listening(speechService, eventListener)
    // 👇 一行代码启动，后续音频采集完全由Vosk库管理
    speechService.startListening(VoskListener(this, eventListener, speechService))
}
```

**音频参数**:
```kotlin
// VoskInputDevice.kt
companion object {
    private const val SAMPLE_RATE = 44100.0f  // 固定44.1kHz
    private const val ALTERNATIVE_COUNT = 5   // 返回最多5个候选结果
}
```

---

### 1.2 SimulateStreamingASR的音频采集

```
┌─────────────────────────────────────────────────────────┐
│         SherpaOnnxSimulateInputDevice                   │
│         (主控制器)                                       │
└─────────────────────────────────────────────────────────┘
            │                              │
            │ 协程1: recordAudio()         │ 协程2: processAudio()
            │ Dispatchers.IO               │ Dispatchers.Default
            ▼                              ▼
┌───────────────────────┐        ┌────────────────────────┐
│   音频采集协程          │        │   音频处理协程          │
│                       │        │                        │
│  AudioRecord.create() │        │  ┌──────────────┐     │
│  audioRecord.start()  │        │  │    VAD       │     │
│                       │        │  │  (Silero)    │     │
│  while (isRecording) {│        │  └──────────────┘     │
│    read 1600 samples  │────────┼──→ samplesChannel     │
│    (每100ms)          │ Channel│     │                 │
│  }                    │        │     ▼                 │
│                       │        │  acceptWaveform()     │
│  audioRecord.stop()   │        │  isSpeechDetected()   │
│  audioRecord.release()│        │     │                 │
└───────────────────────┘        │     ▼                 │
                                 │  ┌──────────────┐     │
                                 │  │ Recognizer   │     │
                                 │  │ (SenseVoice) │     │
                                 │  └──────────────┘     │
                                 │  Partial识别(200ms)   │
                                 │  Final识别(语音段结束) │
                                 └────────────────────────┘
```

**特点**:
- ✅ **完全控制**: 可以控制所有音频采集参数
- ✅ **透明**: 可以打印音频能量、振幅等调试信息
- ✅ **解耦**: VAD和识别器分离，可以单独调优
- ✅ **灵活**: 可以保存原始音频用于离线分析
- ❌ **复杂**: 需要自己管理AudioRecord生命周期
- ❌ **责任大**: 需要处理各种边界情况和错误

**代码示例**:
```kotlin
// SherpaOnnxSimulateInputDevice.kt (简化版)
private suspend fun recordAudio() = withContext(Dispatchers.IO) {
    val bufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,      // 16000 Hz
        CHANNEL_CONFIG,   // MONO
        AUDIO_FORMAT      // PCM_16BIT
    )
    
    // 👇 自己创建AudioRecord
    audioRecord = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        16000,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize * 2
    )
    
    audioRecord?.startRecording()
    val audioBuffer = ShortArray(1600)  // 100ms @ 16kHz
    
    // 👇 手动循环读取音频
    while (isRecording.get()) {
        val ret = audioRecord?.read(audioBuffer, 0, 1600)
        if (ret > 0) {
            val samples = FloatArray(ret) { audioBuffer[it] / 32768.0f }
            
            // 👇 可以分析音频特征
            val amplitude = samples.maxOfOrNull { abs(it) } ?: 0f
            val energy = samples.map { it * it }.average()
            
            // 👇 发送到处理协程
            samplesChannel.send(samples)
        }
    }
    
    // 👇 手动清理资源
    audioRecord?.stop()
    audioRecord?.release()
}
```

**音频参数**:
```kotlin
// SherpaOnnxSimulateInputDevice.kt
companion object {
    private const val SAMPLE_RATE = 16000          // 16kHz（低带宽，节省资源）
    private const val CHANNEL_CONFIG = MONO        // 单声道
    private const val AUDIO_FORMAT = PCM_16BIT     // 16位PCM
    private const val VAD_WINDOW_SIZE = 512        // VAD窗口：32ms
    private const val RECOGNITION_INTERVAL_MS = 200 // 实时识别间隔：200ms
}
```

---

## 二、状态转换机制对比

### 2.1 Vosk的状态机（14个状态）

```
┌──────────────────────────────────────────────────────────┐
│                  VoskState (Sealed Interface)            │
├──────────────────────────────────────────────────────────┤
│  1. NotInitialized   - 未初始化                          │
│  2. NotAvailable     - 当前语言不支持                     │
│  3. NotDownloaded    - 模型未下载                        │
│  4. Downloading      - 正在下载（带进度）                 │
│  5. ErrorDownloading - 下载失败                          │
│  6. Downloaded       - 已下载                            │
│  7. Unzipping        - 正在解压（带进度）                 │
│  8. ErrorUnzipping   - 解压失败                          │
│  9. NotLoaded        - 模型未加载到内存                   │
│ 10. Loading          - 正在加载（可选择加载后是否启动）    │
│ 11. ErrorLoading     - 加载失败                          │
│ 12. Loaded           - 已加载，可以开始识别               │
│ 13. Listening        - 正在识别                          │
│ 14. (implicitly: transition back to Loaded after onResult)│
└──────────────────────────────────────────────────────────┘
```

**状态转换图**:
```
NotInitialized
    │
    ├─ (locale不支持) ──→ NotAvailable
    │
    ├─ (无模型) ──→ NotDownloaded
    │                     │
    │                     │ onClick() / download()
    │                     ▼
    │              Downloading (Progress)
    │                     │
    │                     ├─ (失败) ──→ ErrorDownloading
    │                     │
    │                     │ (成功)
    │                     ▼
    │              Downloaded
    │                     │
    │                     │ onClick() / unzip()
    │                     ▼
    │              Unzipping (Progress)
    │                     │
    │                     ├─ (失败) ──→ ErrorUnzipping
    │                     │
    │                     │ (成功)
    │                     ▼
    └─ (有模型) ──→ NotLoaded
                          │
                          │ onClick() / tryLoad()
                          ▼
                   Loading(thenStartListening)
                          │
                          ├─ (失败) ──→ ErrorLoading
                          │
                          │ (成功)
                          ▼
                   Loaded (SpeechService就绪)
                          │
                          │ onClick() / startListening()
                          ▼
                   Listening
                          │
                          │ onResult() / stopListening()
                          │ onError() / onTimeout()
                          ▼
                   Loaded (回到就绪状态)
```

**特点**:
- ✅ **完整**: 覆盖从下载到识别的完整生命周期
- ✅ **用户友好**: 可以在UI显示详细的状态和进度
- ✅ **可恢复**: 每个错误状态都可以重试
- ❌ **复杂**: 14个状态，状态转换逻辑复杂
- ❌ **重量级**: 需要维护下载、解压、加载等多个流程

**代码示例**:
```kotlin
// VoskInputDevice.kt
override fun onClick(eventListener: (InputEvent) -> Unit) {
    when (val s = _state.value) {
        is NotInitialized -> {}                      // 等待初始化
        is NotAvailable -> {}                        // 语言不支持
        is NotDownloaded -> download(s.modelUrl)     // 开始下载
        is Downloading -> {}                         // 等待下载
        is ErrorDownloading -> download(s.modelUrl)  // 重试下载
        is Downloaded -> unzip()                     // 开始解压
        is Unzipping -> {}                          // 等待解压
        is ErrorUnzipping -> unzip()                // 重试解压
        is NotLoaded -> load(eventListener)         // 加载模型
        is Loading -> toggleThenStartListening(...)  // 切换启动意图
        is ErrorLoading -> load(eventListener)      // 重试加载
        is Loaded -> startListening(...)            // 开始识别 ✅
        is Listening -> stopListening(...)          // 停止识别 ⏹️
    }
}
```

---

### 2.2 SimulateStreamingASR的状态机（5个状态）

```
┌──────────────────────────────────────────────────────────┐
│                  SttState (Sealed Interface)             │
├──────────────────────────────────────────────────────────┤
│  1. NotInitialized  - 未初始化                           │
│  2. NotAvailable    - 不可用（罕见）                      │
│  3. Loaded          - 已就绪，可以开始识别                │
│  4. Listening       - 正在识别                           │
│  5. (NotLoaded 等其他状态复用自SttState基类，但很少使用) │
└──────────────────────────────────────────────────────────┘
```

**状态转换图**:
```
NotInitialized
    │
    │ load()
    │ - 检查SenseVoice模型
    │ - 检查VAD模型
    │ - 初始化SherpaOnnxManager
    │
    ▼
Loaded (模型就绪)
    │
    │ tryLoad() / onClick()
    │ - 启动双协程
    │ - 开始AudioRecord
    │
    ▼
Listening (正在识别)
    │
    │ - VAD检测语音
    │ - 每200ms Partial识别
    │ - 语音结束 Final识别
    │
    │ stopListening() / 自动停止
    │
    ▼
Loaded (回到就绪状态)
```

**特点**:
- ✅ **简单**: 只有5个主要状态，易于理解
- ✅ **快速**: 模型预置在assets，无需下载
- ✅ **专注**: 只关注识别本身，不管理模型下载
- ❌ **不灵活**: 无法在运行时切换模型或语言
- ❌ **占用大**: 模型打包在APK中，增加应用体积

**代码示例**:
```kotlin
// SherpaOnnxSimulateInputDevice.kt
override suspend fun load(): Boolean {
    Log.d(TAG, "🔧 开始加载 Sherpa-ONNX...")
    
    // 👇 简单的一次性初始化
    val recognizerOk = SherpaOnnxManager.initOfflineRecognizer(appContext)
    val vadOk = SherpaOnnxManager.initVad(appContext)
    
    if (recognizerOk && vadOk) {
        isInitialized.set(true)
        _uiState.value = SttState.Loaded
        return true
    } else {
        _uiState.value = SttState.NotAvailable
        return false
    }
}

override fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean {
    if (!isInitialized.get()) {
        // 👇 需要先load()
        return false
    }
    
    if (thenStartListeningEventListener != null) {
        // 👇 直接启动识别
        startRecording(thenStartListeningEventListener)
    }
    return true
}
```

---

## 三、VAD（语音检测）对比

### 3.1 Vosk的VAD

**位置**: 内置在Vosk识别器中（不可见）

**特征**:
- ✅ **集成度高**: VAD和识别器深度集成
- ✅ **无需配置**: 开箱即用
- ❌ **不可调**: 无法调整VAD参数（阈值、灵敏度等）
- ❌ **不透明**: 无法获取VAD分数或状态
- ❌ **固定策略**: 检测到静音后自动停止识别

**工作方式**（推测）:
```
音频流 → Vosk识别器
            │
            ├─ 内部VAD检测
            │  （静音/语音判断）
            │
            ├─ 语音段: 实时识别 → onPartialResult()
            │
            └─ 检测到静音（约1-2秒）→ onResult() → 自动停止
```

**回调机制**:
```kotlin
// VoskListener.kt
override fun onPartialResult(s: String) {
    // 👈 实时结果（语音进行中）
    val partialInput = JSONObject(s).getString("partial")
    eventListener(InputEvent.Partial(partialInput))
}

override fun onResult(s: String) {
    // 👈 最终结果（检测到静音后）
    voskInputDevice.stopListening(...)  // 自动停止
    val inputs = utterancesFromJson(JSONObject(s))
    eventListener(InputEvent.Final(inputs))
}
```

---

### 3.2 SimulateStreamingASR的VAD

**位置**: 独立的Silero VAD模型（`SherpaOnnxManager.vad`）

**特征**:
- ✅ **独立可控**: VAD和识别器完全分离
- ✅ **可配置**: 可以调整阈值、最小语音时长等
- ✅ **透明**: 可以查询VAD分数、状态
- ✅ **灵活策略**: 可以选择立即停止或继续监听
- ❌ **需要调优**: 需要根据场景调整参数

**配置**:
```kotlin
// VadModelManager.kt
SileroVadModelConfig(
    threshold = 0.3f,              // 语音判断阈值（0.0-1.0）
    minSilenceDuration = 0.1f,     // 最小静音持续时间（秒）
    minSpeechDuration = 0.05f,     // 最小语音持续时间（秒）
    windowSize = 512,              // VAD窗口大小（样本数）
    maxSpeechDuration = 10.0f      // 最大语音时长（秒）
)
```

**工作方式**:
```
音频流 → processAudio()协程
            │
            ├─ 每32ms处理一个VAD窗口
            │  vadInstance.acceptWaveform(512 samples)
            │
            ├─ 查询VAD状态
            │  isSpeechDetected = vadInstance.isSpeechDetected()
            │
            ├─ 检测到语音开始
            │  isSpeechStarted = true
            │  启动200ms实时识别定时器
            │
            ├─ 实时识别（每200ms）
            │  → performPartialRecognition()
            │
            └─ VAD检测到语音结束
               vadInstance.empty() == false
               → performFinalRecognition()
               → 处理完整语音段
```

**VAD队列机制**:
```kotlin
// SherpaOnnxSimulateInputDevice.kt
while (!vadInstance.empty()) {
    // 👈 VAD内部队列有完整语音段
    val speechSegment = vadInstance.front()  // 获取语音段
    
    // 识别这个完整语音段
    performFinalRecognition(vadInstance, recognizerInstance)
    
    vadInstance.pop()  // 弹出已处理的语音段
    
    // 重置状态，准备下一段语音
    isSpeechStarted = false
    buffer.clear()
}
```

---

## 四、识别流程对比

### 4.1 Vosk的识别流程

```
时间线（基于用户说话）:

T0: 用户开始说话 "你好世界"
    │
    ├─ Vosk内部VAD检测到语音开始
    │
T1: 0.2s → onPartialResult("你")
    │
T2: 0.5s → onPartialResult("你好")
    │
T3: 1.0s → onPartialResult("你好世界")
    │
T4: 用户停止说话
    │
T5: 1.5s 静音 → Vosk VAD判断语音结束
    │
T6: onResult("你好世界") 
    │
    └─ 自动调用 stopListening()
       SpeechService 停止
       状态: Listening → Loaded
```

**特点**:
- ✅ 自动管理整个流程
- ✅ 一次说话结束后自动停止
- ❌ 无法连续识别多段语音
- ❌ 静音超时固定，不可调整

**事件序列**:
```kotlin
// 用户点击按钮
voskInputDevice.onClick(eventListener)
    ↓
startListening(speechService, eventListener)
    ↓
speechService.startListening(VoskListener(...))
    ↓
// === Vosk内部开始工作（黑盒）===
    ↓
onPartialResult("你")           // 第一个字
onPartialResult("你好")         // 前两个字
onPartialResult("你好世界")     // 完整句子
    ↓
// 检测到静音
    ↓
onResult("你好世界")
    ↓
stopListening()  // 自动停止
    ↓
状态: Listening → Loaded
```

---

### 4.2 SimulateStreamingASR的识别流程

```
时间线（基于用户说话）:

T0: tryLoad() → 启动双协程
    │
    ├─ recordAudio() 协程启动
    │  audioRecord.startRecording()
    │
    ├─ processAudio() 协程启动
    │  等待音频数据...
    │
T1: 用户开始说话 "你好世界"
    │
T2: 音频流入 → buffer累积
    │
T3: VAD检测到语音开始（经过100-500ms累积）
    isSpeechStarted = true
    │
T4: +200ms → 第一次 Partial 识别
    识别 buffer[0:offset] → "你"
    │
T5: +200ms → 第二次 Partial 识别
    识别 buffer[0:offset] → "你好"
    │
T6: +200ms → 第三次 Partial 识别
    识别 buffer[0:offset] → "你好世界"
    │
T7: 用户停止说话
    │
T8: VAD检测到语音结束（静音100ms）
    vadInstance.empty() == false
    │
T9: Final 识别
    识别 VAD提取的语音段 → "你好世界"（或可能"到世界"如果裁剪了开头）
    │
T10: 继续监听（不自动停止）
     或 调用 stopListening() 手动停止
```

**特点**:
- ✅ 完全可控的流程
- ✅ 可以连续识别多段语音
- ✅ 可以调整识别间隔
- ✅ 可以选择是否自动停止
- ❌ 需要手动管理停止逻辑
- ❌ Partial和Final可能不一致

**事件序列**:
```kotlin
// 用户点击按钮或唤醒词触发
tryLoad(eventListener)
    ↓
startRecording(eventListener)
    ↓
scope.launch {
    // 协程1: 音频采集
    recordAudio()
        ↓
    while (isRecording) {
        read 1600 samples
        samplesChannel.send(samples)
    }
}
    │
    └─ scope.launch {
        // 协程2: 音频处理
        processAudio()
            ↓
        for (samples in samplesChannel) {
            buffer.add(samples)
            
            // VAD处理
            vadInstance.acceptWaveform(vadFrame)
            if (isSpeechDetected && !isSpeechStarted) {
                isSpeechStarted = true  // 语音开始！
            }
            
            // 实时识别（每200ms）
            if (isSpeechStarted && elapsed > 200ms) {
                performPartialRecognition()
                    ↓
                eventListener(InputEvent.Partial("你"))
                eventListener(InputEvent.Partial("你好"))
                eventListener(InputEvent.Partial("你好世界"))
            }
            
            // 最终识别（VAD检测到结束）
            if (!vadInstance.empty()) {
                performFinalRecognition()
                    ↓
                eventListener(InputEvent.Final("你好世界"))
                
                // 重置状态，准备下一段语音
                isSpeechStarted = false
                buffer.clear()
            }
        }
    }
```

---

## 五、性能对比

### 5.1 资源占用

| 项目 | Vosk | SimulateStreamingASR |
|------|------|---------------------|
| **模型大小** | 约50MB（需下载） | 约500MB（打包在APK） |
| **内存占用** | 约100-200MB | 约200-300MB |
| **CPU占用** | 中等（单线程VAD+识别） | 较高（双协程+独立VAD） |
| **采样率** | 44.1kHz | 16kHz（节省带宽） |
| **启动时间** | 快（模型较小） | 慢（模型较大） |

### 5.2 识别性能

| 项目 | Vosk | SimulateStreamingASR |
|------|------|---------------------|
| **实时性** | 高（真正的流式识别） | 中（模拟流式，有延迟） |
| **准确度** | 中等（小模型） | 高（大模型） |
| **多语言** | 需要切换模型 | 自动检测多语言 |
| **候选数量** | 最多5个 | 1个 |
| **置信度** | 提供置信度分数 | 固定1.0 |

### 5.3 响应延迟

**Vosk**:
```
用户说话开始 → 100-300ms → 第一个Partial结果
用户说话结束 → 1000-2000ms静音检测 → Final结果 → 自动停止

总延迟: 约1.5-2.5秒
```

**SimulateStreamingASR**:
```
用户说话开始 → 300-1000ms（VAD累积） → 语音检测
语音检测 → 200ms → 第一个Partial结果
用户说话结束 → 100ms（VAD判断） → Final结果

总延迟: 约0.5-1.5秒（但前期VAD延迟较长）
```

---

## 六、优缺点总结

### 6.1 Vosk的优缺点

**优点**:
1. ✅ **简单易用**: 一行代码启动，无需管理音频采集
2. ✅ **稳定可靠**: 经过充分测试的成熟库
3. ✅ **体积小**: 模型可按需下载，不增加APK体积
4. ✅ **真正流式**: 识别延迟低，实时性好
5. ✅ **多候选**: 提供最多5个识别结果供选择
6. ✅ **置信度**: 提供confidence分数，可以判断识别质量

**缺点**:
1. ❌ **黑盒系统**: 无法控制VAD和音频采集细节
2. ❌ **不够灵活**: 静音超时固定，无法调整
3. ❌ **单语言**: 一次只能识别一种语言
4. ❌ **需要网络**: 首次使用需要下载模型
5. ❌ **状态复杂**: 14个状态，增加维护成本
6. ❌ **自动停止**: 一段话结束后自动停止，无法连续识别

---

### 6.2 SimulateStreamingASR的优缺点

**优点**:
1. ✅ **完全控制**: 可以控制所有音频采集和VAD参数
2. ✅ **透明调试**: 可以打印音频能量、VAD分数等信息
3. ✅ **多语言**: 自动检测中英日韩粤五种语言
4. ✅ **准确度高**: 大模型（SenseVoice），识别准确度高
5. ✅ **离线可用**: 模型打包在APK，无需下载
6. ✅ **连续识别**: 可以连续识别多段语音
7. ✅ **VAD可调**: 可以调整VAD参数适应不同场景

**缺点**:
1. ❌ **复杂**: 需要自己管理AudioRecord和双协程
2. ❌ **体积大**: 模型占用APK空间约500MB
3. ❌ **内存占用高**: 大模型需要更多内存
4. ❌ **模拟流式**: 实际是批量识别，有一定延迟
5. ❌ **VAD延迟**: 需要累积证据，初始检测延迟较长
6. ❌ **结果不一致**: Partial和Final可能识别出不同结果
7. ❌ **无置信度**: 只返回最优结果，不提供置信度分数

---

## 七、适用场景建议

### 使用Vosk的场景:
1. ✅ 需要快速接入ASR功能
2. ✅ 对APK体积有严格限制
3. ✅ 单一语言识别
4. ✅ 需要置信度分数
5. ✅ 短句识别（问答、命令）
6. ✅ 用户可以接受首次下载模型

### 使用SimulateStreamingASR的场景:
1. ✅ 需要完全离线识别
2. ✅ 多语言混合识别
3. ✅ 对识别准确度要求高
4. ✅ 需要调试和优化VAD参数
5. ✅ 连续对话场景（如语音助手）
6. ✅ 需要保存原始音频进行分析
7. ✅ 可以接受较大的APK体积

---

## 八、混合使用建议

可以同时提供两种选择，让用户根据需求切换：

```kotlin
// 用户设置
enum class AsrEngine {
    VOSK,           // 轻量级、快速启动
    SHERPA_ONNX     // 高准确度、离线
}

// 根据场景自动选择
fun selectAsrEngine(context: Context): AsrEngine {
    return when {
        // 首次安装，网络可用 → Vosk（避免大APK下载）
        !isModelDownloaded() && isNetworkAvailable() -> VOSK
        
        // 多语言需求 → SherpaOnnx
        needsMultiLanguage() -> SHERPA_ONNX
        
        // 离线场景 → SherpaOnnx
        !isNetworkAvailable() -> SHERPA_ONNX
        
        // 默认：用户选择
        else -> userPreference.asrEngine
    }
}
```

---

## 总结

**Vosk**: 像汽车自动挡 🚗
- 简单、易用、开箱即用
- 适合大多数日常场景
- 牺牲了一些控制权换取便利性

**SimulateStreamingASR**: 像汽车手动挡 🏎️
- 复杂、强大、完全可控
- 适合专业场景和深度定制
- 需要更多的技术投入和调优

选择哪个取决于你的具体需求：
- 如果追求简单快速 → **Vosk**
- 如果追求性能可控 → **SimulateStreamingASR**

