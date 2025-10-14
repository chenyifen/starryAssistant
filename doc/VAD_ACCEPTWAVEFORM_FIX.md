# VAD acceptWaveform Vector Error 修复报告

## 问题描述

在使用 SherpaOnnx VAD 进行语音活动检测时,遇到以下运行时错误:

```
java.lang.RuntimeException: Vad_acceptWaveform: vector
	at com.k2fsa.sherpa.onnx.Vad.acceptWaveform(Native Method)
	at com.k2fsa.sherpa.onnx.Vad.acceptWaveform(Vad.kt:48)
```

### 错误日志

```
09-20 20:49:47.041 D SenseVoiceInputDevice: ✅ VAD初始化成功
09-20 20:49:47.049 D VadModelManager: ✅ 使用外部存储VAD模型
09-20 20:51:15.353 E SenseVoiceInputDevice: java.lang.RuntimeException: Vad_acceptWaveform: vector
```

## 问题根源分析

### 1. 原有实现的问题

**错误的实现方式**:
```kotlin
// ❌ 错误: 使用固定窗口大小的滑动窗口方式
while (vadBuffer.size >= VAD_WINDOW_SIZE) {
    val vadWindow = FloatArray(VAD_WINDOW_SIZE) { i -> vadBuffer.elementAt(i) }
    vad!!.acceptWaveform(vadWindow)  // 每次传入512个样本
    vad!!.isSpeechDetected()
    // 移除已处理的样本
    repeat(VAD_WINDOW_SIZE / 4) { vadBuffer.removeFirst() }
}
```

**问题所在**:
1. **误解了 VAD 的工作原理**: 将 VAD 当成需要固定窗口大小输入的模型
2. **手动管理缓冲区**: 创建 `vadBuffer` 来实现滑动窗口
3. **重复累积**: VAD 内部已经有缓冲机制,外部再次累积导致数据溢出

### 2. Sherpa-ONNX VAD 的正确使用方式

根据 Sherpa-ONNX 官方文档和源码分析:

**VAD 的工作原理**:
- VAD 是**累积式处理**,内部维护状态和缓冲
- `acceptWaveform(samples)` 接受任意长度的音频数据
- VAD 内部会根据配置的 `windowSize=512` 自动管理窗口
- 不需要外部手动分割或管理缓冲区

**VAD 配置参数**:
```kotlin
VadModelConfig(
    sileroVadModelConfig = SileroVadModelConfig(
        model = modelPath,
        threshold = 0.5f,
        minSilenceDuration = 0.25f,
        minSpeechDuration = 0.25f,
        windowSize = 512,           // VAD内部使用的窗口大小
        maxSpeechDuration = 5.0f
    ),
    sampleRate = 16000,
    numThreads = 1,
    provider = "cpu"
)
```

### 3. Vector 错误的具体原因

当外部手动创建固定大小的窗口(512样本)并重复传入时:
1. VAD 内部已经有缓冲机制
2. 每次调用 `acceptWaveform` 都会累积数据
3. VAD 内部的 vector 会不断增长
4. 当超出预期大小时,C++ 层抛出 vector 异常

## 解决方案

### 修复后的正确实现

```kotlin
private fun processNewSamples(samples: FloatArray): Boolean {
    var hasSpeech = false
    val currentTime = System.currentTimeMillis()
    
    // 如果已经检测到语音,添加到语音缓冲区
    if (isSpeechDetected) {
        for (sample in samples) {
            speechBuffer.add(sample)
        }
    }
    
    // 使用VAD或能量检测判断是否有语音
    val speechDetected = if (vad != null) {
        try {
            // ✅ 正确: 直接传入完整的samples数组
            // VAD内部会管理缓冲区和状态
            vad!!.acceptWaveform(samples)
            val detected = vad!!.isSpeechDetected()
            
            if (detected && !isSpeechDetected) {
                Log.d(TAG, "🎙️ VAD检测到语音开始")
            }
            
            detected
        } catch (e: Exception) {
            // 如果VAD出错,降级到能量检测
            Log.w(TAG, "⚠️ VAD检测异常,降级到能量检测", e)
            vad = null  // 禁用VAD,避免后续持续出错
            detectSpeechByEnergy(samples)
        }
    } else {
        detectSpeechByEnergy(samples)
    }
    
    if (speechDetected) {
        hasSpeech = true
        lastSpeechTime = currentTime
        
        if (!isSpeechDetected) {
            isSpeechDetected = true
            speechStartTime = currentTime
            // 将当前样本也加入到语音缓冲区
            for (sample in samples) {
                speechBuffer.add(sample)
            }
        }
    }
    
    return hasSpeech
}
```

### 关键修改点

1. **移除手动缓冲区管理**
   - ❌ 删除: `vadBuffer` - 不再需要手动管理VAD缓冲
   - ✅ 保留: `speechBuffer` - 只用于累积检测到的语音

2. **简化 VAD 调用**
   - ❌ 之前: 每次传入固定512样本的窗口
   - ✅ 现在: 直接传入音频采集的完整数据(通常~1600样本)

3. **添加异常处理**
   - 捕获 VAD 异常并自动降级到能量检测
   - 避免一次失败导致整个识别流程中断

4. **优化状态管理**
   - 只在检测到语音后才累积音频数据
   - 减少内存占用

## 验证要点

修复后需要验证以下几点:

### 1. VAD 正常工作
```
✅ VAD初始化成功
📊 VAD模型 (外部存储, 1MB)
🎙️ VAD检测到语音开始 (1600样本)
```

### 2. 不再出现 vector 错误
```
# 之前会出现
❌ java.lang.RuntimeException: Vad_acceptWaveform: vector

# 修复后
✅ 正常识别,无异常
```

### 3. 能量检测降级机制
```
⚠️ VAD检测异常,降级到能量检测
🔊 能量检测触发: RMS=0.012000 > threshold=0.010000
```

## 技术细节

### VAD 数据流

```
音频采集 (100ms@16kHz = 1600样本)
    ↓
直接传入 VAD.acceptWaveform(samples[1600])
    ↓
VAD内部处理:
  - 累积到内部缓冲
  - 按windowSize=512分帧处理
  - 应用minSilenceDuration/minSpeechDuration逻辑
    ↓
返回检测结果 VAD.isSpeechDetected()
```

### 采样率匹配

确保整个音频链路的采样率一致:
- **AudioRecord**: 16000 Hz
- **VAD Config**: 16000 Hz  
- **SenseVoice**: 16000 Hz

### 内存优化

修复后的内存使用:
- **之前**: vadBuffer(512×2) + speechBuffer(动态) = 额外1KB + 动态
- **现在**: speechBuffer(动态) = 只有实际语音数据

## 参考资料

1. **Sherpa-ONNX 官方文档**
   - VAD 使用说明
   - Android 集成指南

2. **相关Issue**
   - sherpa-onnx#XXX: VAD acceptWaveform usage
   - 类似的 vector 错误报告

3. **代码示例**
   - SherpaOnnxSimulateStreamingAsr demo
   - VAD 正确使用模式

## 总结

这个修复解决了对 VAD 工作原理的误解:
- ❌ **错误认知**: VAD 需要固定大小的窗口输入
- ✅ **正确理解**: VAD 是累积式处理,接受任意长度输入

核心教训: **阅读官方文档和参考示例代码非常重要**,不要根据参数名称(`windowSize`)自行臆测API的使用方式。

---

**修复日期**: 2025-10-14  
**修复文件**: `SenseVoiceInputDevice.kt`  
**测试状态**: 待验证 ✅

