# VAD与SimulateStreamingASR协作机制详解

## 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│                  SherpaOnnxSimulateInputDevice              │
│                     (主控制器)                               │
└─────────────────────────────────────────────────────────────┘
                    │                    │
        ┌───────────┴───────┐   ┌───────┴─────────┐
        │   音频采集协程      │   │   音频处理协程   │
        │  (recordAudio)    │   │  (processAudio) │
        │  Dispatchers.IO   │   │ Dispatchers.Default│
        └───────────────────┘   └──────────────────┘
                │                        │
                │ samplesChannel         │
                │ (Channel流)            │
                └────────────────────────┘
                           │
            ┌──────────────┼──────────────┐
            │              │              │
        ┌───▼───┐     ┌───▼───┐    ┌────▼─────┐
        │  VAD  │     │Buffer │    │Recognizer│
        │(语音检测)│   │(缓冲区)│   │(离线识别) │
        └───────┘     └───────┘    └──────────┘
            │                            │
    ┌───────┴────────┐          ┌───────┴──────┐
    │ isSpeechDetected│          │ Partial结果  │
    │   (布尔标志)     │          │ Final结果    │
    └────────────────┘          └──────────────┘
```

---

## 核心组件

### 1. SherpaOnnxManager（单例管理器）
**职责**: 集中管理VAD和Recognizer实例

```kotlin
object SherpaOnnxManager {
    private var _recognizer: OfflineRecognizer? = null  // ASR识别器
    private var _vad: Vad? = null                       // VAD检测器
    
    fun initOfflineRecognizer(context: Context): Boolean
    fun initVad(context: Context): Boolean
}
```

**初始化时机**: 
- `SherpaOnnxSimulateInputDevice.load()` 时调用
- 全局共享，避免重复加载大模型

---

### 2. 双协程架构

#### 协程1: 音频采集 (recordAudio)
**运行在**: `Dispatchers.IO`（IO密集型）

```kotlin
private suspend fun recordAudio() = withContext(Dispatchers.IO) {
    // 1. 创建 AudioRecord
    audioRecord = AudioRecord(
        AUDIO_SOURCE,
        SAMPLE_RATE = 16000,      // 16kHz采样率
        CHANNEL_CONFIG = MONO,     // 单声道
        AUDIO_FORMAT = PCM_16BIT,  // 16位PCM
        bufferSize * 2
    )
    
    // 2. 循环读取音频（每100ms一次）
    val interval = 0.1  // 100ms
    val bufferSampleSize = (0.1 * 16000).toInt() = 1600 samples
    
    while (isRecording) {
        audioRecord.read(audioBuffer, 0, 1600)  // 读取1600个样本
        
        // 3. 转换为浮点数（-1.0 ~ 1.0）
        val samples = FloatArray(1600) { audioBuffer[it] / 32768.0f }
        
        // 4. 发送到处理协程
        samplesChannel.send(samples)  // 👈 通过Channel传递
    }
}
```

**作用**:
- 持续从麦克风读取音频
- 每100ms读取1600个样本（0.1秒音频）
- 通过Channel将数据流式传递给处理协程

---

#### 协程2: 音频处理 (processAudio)
**运行在**: `Dispatchers.Default`（CPU密集型）

```kotlin
private suspend fun processAudio() = withContext(Dispatchers.Default) {
    val vadInstance = SherpaOnnxManager.vad!!
    val recognizerInstance = SherpaOnnxManager.recognizer!!
    
    while (isRecording) {
        for (samples in samplesChannel) {  // 👈 从Channel接收
            // 步骤1: 添加到缓冲区
            buffer.addAll(samples.toList())
            
            // 步骤2: VAD处理
            vad_processing()
            
            // 步骤3: 实时识别（Partial）
            partial_recognition()
            
            // 步骤4: 最终识别（Final）
            final_recognition()
        }
    }
}
```

---

## 详细工作流程

### 阶段1: VAD处理（语音检测）

```kotlin
// VAD处理逻辑
while (offset + VAD_WINDOW_SIZE < buffer.size) {
    // 1. 提取VAD窗口（512样本 = 32ms @ 16kHz）
    val vadFrame = buffer.subList(offset, offset + 512).toFloatArray()
    
    // 2. 喂给VAD模型
    vadInstance.acceptWaveform(vadFrame)
    offset += 512
    
    // 3. 查询VAD判断结果
    val speechDetected = vadInstance.isSpeechDetected()
    
    // 4. 检测语音开始
    if (!isSpeechStarted && speechDetected) {
        isSpeechStarted = true  // 👈 标记：语音开始了！
        startTime = System.currentTimeMillis()
    }
}
```

**VAD工作原理**:

```
音频流输入 → 32ms窗口 → Silero RNN模型 → 输出分数 → 状态机判断
             (512样本)     (ONNX推理)     (0.0-1.0)   (speech/silence)

时间轴示例：
T0:   0ms  [静音] VAD分数=0.05  isSpeechDetected=false
T1:  32ms  [静音] VAD分数=0.10  isSpeechDetected=false
T2:  64ms  [过渡] VAD分数=0.25  isSpeechDetected=false
T3:  96ms  [语音] VAD分数=0.45  isSpeechDetected=true ✅ 触发！
T4: 128ms  [语音] VAD分数=0.60  isSpeechDetected=true
```

**关键参数**（`VadModelManager.kt`）:
```kotlin
SileroVadModelConfig(
    threshold = 0.3f,              // 分数>0.3判定为语音
    minSilenceDuration = 0.1f,     // 最小静音持续时间100ms
    minSpeechDuration = 0.05f,     // 最小语音持续时间50ms
    windowSize = 512,              // VAD窗口大小32ms
    maxSpeechDuration = 10.0f      // 最大语音时长10秒
)
```

---

### 阶段2: 实时识别（Partial Recognition）

```kotlin
// 实时识别逻辑（每200ms触发一次）
val elapsed = System.currentTimeMillis() - startTime

if (isSpeechStarted) {  // 👈 只有VAD检测到语音后才识别
    if (elapsed > 200ms) {  // 每200ms识别一次
        performPartialRecognition(recognizerInstance)
        startTime = System.currentTimeMillis()  // 重置计时器
    }
}
```

**Partial识别流程**:
```kotlin
private suspend fun performPartialRecognition(recognizerInstance: OfflineRecognizer) {
    // 1. 获取当前累积的所有音频
    val audioData = buffer.subList(0, offset).toFloatArray()
    //    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    //    注意：使用原始buffer，包含VAD检测前的数据！
    
    // 2. 创建临时识别流
    val stream = recognizerInstance.createStream()
    
    // 3. 喂给识别器
    stream.acceptWaveform(audioData, SAMPLE_RATE)
    
    // 4. 执行识别
    recognizerInstance.decode(stream)
    
    // 5. 获取结果
    val result = recognizerInstance.getResult(stream)
    stream.release()  // 立即释放
    
    // 6. 发送结果到UI
    val text = result.text
    eventListener?.invoke(InputEvent.Partial(text))
    //                                ^^^^^^^ 实时结果
}
```

**时间轴示例**:
```
T0:   0ms  - 录音开始
T1: 100ms  - VAD检测到语音，isSpeechStarted=true
T2: 300ms  - elapsed=200ms，触发第一次Partial识别
            识别结果: "你"
T3: 500ms  - elapsed=200ms，触发第二次Partial识别
            识别结果: "你好"
T4: 700ms  - elapsed=200ms，触发第三次Partial识别
            识别结果: "你好吗"
```

**特点**:
- ✅ 响应快速（200ms间隔）
- ✅ 用户实时看到识别结果
- ✅ 使用完整buffer，包含语音起始
- ⚠️ 可能不够准确（数据不完整）

---

### 阶段3: 最终识别（Final Recognition）

```kotlin
// VAD队列检查（语音段结束时触发）
while (!vadInstance.empty()) {  // 👈 VAD内部队列有完整语音段
    performFinalRecognition(vadInstance, recognizerInstance)
    vadInstance.pop()  // 弹出已处理的语音段
    
    // 重置状态，准备下一段语音
    isSpeechStarted = false
    buffer.clear()
    offset = 0
}
```

**VAD队列机制**:
```
VAD内部维护一个语音段队列：

┌─────────────────────────────────┐
│  VAD Queue (FIFO)               │
├─────────────────────────────────┤
│ Segment 1: [start=0.5s, duration=2.3s, samples=[...]] │
│ Segment 2: [start=3.1s, duration=1.8s, samples=[...]] │
└─────────────────────────────────┘

当VAD检测到：
  语音开始 → 静音持续100ms → 语音结束

则：
  1. VAD提取这段"纯净语音"
  2. 放入内部队列（front/pop访问）
  3. 触发empty()=false
```

**Final识别流程**:
```kotlin
private suspend fun performFinalRecognition(vadInstance: Vad, recognizerInstance: OfflineRecognizer) {
    // 1. 从VAD队列获取完整语音段
    val speechSegment = vadInstance.front()
    //    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    //    注意：这是VAD提取的"纯净语音段"，可能裁剪了边界！
    
    val audioData = speechSegment.samples  // VAD提取的样本
    
    // 2. 创建识别流
    val stream = recognizerInstance.createStream()
    
    // 3. 喂给识别器
    stream.acceptWaveform(audioData, SAMPLE_RATE)
    
    // 4. 执行识别
    recognizerInstance.decode(stream)
    
    // 5. 获取最终结果
    val result = recognizerInstance.getResult(stream)
    stream.release()
    
    // 6. 发送最终结果
    val text = result.text
    eventListener?.invoke(InputEvent.Final(listOf(text to 1.0f)))
    //                                ^^^^^ 最终结果
}
```

**特点**:
- ✅ 识别准确（完整语音段）
- ✅ 去除了开头/结尾的静音
- ⚠️ 可能裁剪掉语音边界（如开头的轻音）
- ⚠️ 延迟较大（需等待语音结束）

---

## VAD与ASR的协作关系

### 时序图

```
AudioRecord     samplesChannel    VAD              ASR (Partial)    ASR (Final)
    │                 │            │                     │               │
    │─ 100ms音频 ─────>│            │                     │               │
    │                 │─ samples ──>│                     │               │
    │                 │            │─ acceptWaveform     │               │
    │                 │            │─ isSpeechDetected=false            │
    │                 │            │                     │               │
    │─ 100ms音频 ─────>│            │                     │               │
    │                 │─ samples ──>│                     │               │
    │                 │            │─ acceptWaveform     │               │
    │                 │            │─ isSpeechDetected=true ✅ 语音开始!   │
    │                 │            │                     │               │
    │─ 100ms音频 ─────>│            │                     │               │
    │                 │─ samples ──>│                     │               │
    │                 │            │─ acceptWaveform     │               │
    │                 │            │  (200ms到了)────────>│               │
    │                 │            │                   识别buffer[0:offset]
    │                 │            │                     │─ Partial: "你" │
    │                 │            │                     │               │
    │─ 100ms音频 ─────>│            │                     │               │
    │                 │─ samples ──>│                     │               │
    │                 │            │─ acceptWaveform     │               │
    │                 │            │  (200ms到了)────────>│               │
    │                 │            │                     │─ Partial: "你好"│
    │                 │            │                     │               │
    │─ 100ms音频 ─────>│            │                     │               │
    │                 │─ samples ──>│                     │               │
    │                 │            │─ acceptWaveform     │               │
    │                 │            │─ isSpeechDetected=false (静音)       │
    │                 │            │─ 语音段完成!─────────────────────────>│
    │                 │            │                     │          识别完整段
    │                 │            │                     │               │─ Final: "你好"
    │                 │            │                     │               │
```

---

## 数据流对比

### Partial 使用的数据
```
原始音频流:  [静音][过渡][清晰语音........][过渡][静音]
Buffer:       ^─────────────────────────────^
              offset=0                  offset=当前位置

Partial识别: buffer[0:offset] 
             包含所有累积的音频，包括VAD检测前的部分
```

### Final 使用的数据
```
原始音频流:  [静音][过渡][清晰语音........][过渡][静音]
VAD提取:              ^────────────────^
                    start            end
                    
Final识别:   speechSegment.samples
             仅包含VAD判定为"语音"的部分
             可能裁剪掉开头的[过渡]和结尾的[过渡]
```

---

## 关键配置参数

### VAD参数（`VadModelManager.kt`）
```kotlin
threshold = 0.3f              // 语音判断阈值（分数>0.3=语音）
minSilenceDuration = 0.1f     // 最小静音持续时间（100ms静音→语音结束）
minSpeechDuration = 0.05f     // 最小语音持续时间（至少50ms才算语音）
windowSize = 512              // VAD窗口大小（32ms @ 16kHz）
maxSpeechDuration = 10.0f     // 最大语音时长（超过10秒强制切断）
```

### 识别参数（`SherpaOnnxSimulateInputDevice.kt`）
```kotlin
SAMPLE_RATE = 16000           // 音频采样率（16kHz）
VAD_WINDOW_SIZE = 512         // VAD窗口（32ms）
RECOGNITION_INTERVAL_MS = 200 // Partial识别间隔（200ms）
```

---

## 常见问题分析

### Q1: 为什么检测延迟这么长？
**原因**:
1. AudioRecord初始化需要时间（50-200ms）
2. 音频缓冲累积（100-500ms）
3. **VAD需要累积证据**（100-1000ms）← 主要延迟
   - 不会第一帧就判断为语音
   - 需要连续多帧高分数才确认
   - 防止误触发（噪音、爆破音）
4. 系统调度延迟（50-200ms）

**优化方向**:
- 降低`threshold`（0.3 → 0.25，更敏感）
- 减小`minSpeechDuration`（0.05 → 0.03，更快触发）
- 减小音频采集间隔（100ms → 50ms）

### Q2: 为什么Partial正确但Final错误？
**场景**: "回到主页" → Partial显示正确，Final只显示"到主页"

**原因**:
```
用户说话: "回 到 主 页"
音频能量:  低  高 高 高

VAD判断:
  "回"字: 能量低+爆破音 → VAD分数=0.25 → ❌ 判定为静音/过渡
  "到"字: 能量高+持续音 → VAD分数=0.45 → ✅ 语音段开始

结果: Final只包含"到主页"，丢失了"回"
```

**解决方案**:
1. 优先使用Partial结果（包含完整音频）
2. 降低VAD阈值（但会增加误触发）
3. 扩展VAD提取边界（±200ms）
4. 对比Partial/Final，选择更长的

---

## 优化建议

### 1. 智能结果选择（推荐）
```kotlin
// 在 performFinalRecognition 中
val betterResult = when {
    finalText.isBlank() -> lastText          // Final空，用Partial
    lastText.isBlank() -> finalText          // Partial空，用Final
    finalText.length > lastText.length -> finalText  // Final更长
    else -> lastText                         // 默认用Partial（更完整）
}
eventListener?.invoke(InputEvent.Final(listOf(betterResult to 1.0f)))
```

### 2. VAD参数调优
```kotlin
// 更激进的配置（更快响应，但可能增加误触发）
threshold = 0.25f              // 0.3 → 0.25
minSpeechDuration = 0.03f      // 0.05 → 0.03
```

### 3. 添加能量预检测
```kotlin
// 在VAD之前先用简单能量检测
if (amplitude > 0.01f) {
    // 可能有语音，提前通知UI准备
    _uiState.value = SttState.MaybeListening
}
```

---

## 总结

**VAD与ASR的分工**:
- **VAD**: 负责检测"何时说话"（二分类：语音/静音）
- **ASR**: 负责识别"说了什么"（序列识别：音频→文本）

**协作模式**:
1. VAD先检测语音开始 → 触发ASR开始识别
2. VAD持续监控 → ASR每200ms更新Partial结果
3. VAD检测语音结束 → 触发ASR最终识别

**核心优势**:
- ✅ 自动检测语音段（无需按键）
- ✅ 实时反馈（Partial）
- ✅ 准确结果（Final）
- ✅ 节省资源（只在有语音时识别）

**已知问题**:
- ⚠️ VAD检测延迟（需累积证据）
- ⚠️ VAD可能裁剪边界（丢失弱音）
- ⚠️ Partial/Final结果可能不一致

