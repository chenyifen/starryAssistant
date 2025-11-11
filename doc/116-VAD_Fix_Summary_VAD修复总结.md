# VAD AcceptWaveform 错误修复总结

## 🎯 问题描述

应用在使用 Sherpa-ONNX VAD 进行语音活动检测时,出现运行时崩溃:

```
java.lang.RuntimeException: Vad_acceptWaveform: vector
	at com.k2fsa.sherpa.onnx.Vad.acceptWaveform(Native Method)
```

## 🔍 根本原因

**错误的理解**: 误以为 VAD 需要接收固定大小(512样本)的音频窗口

**实际情况**: Sherpa-ONNX VAD 是累积式处理:
- 内部维护缓冲区和状态
- `acceptWaveform()` 接受**任意长度**的音频数据
- `windowSize=512` 是 VAD 内部处理的窗口大小,不是输入要求

## ✅ 修复方案

### 修改前 (❌ 错误)

```kotlin
// 手动管理VAD缓冲区
private val vadBuffer = ArrayDeque<Float>(VAD_WINDOW_SIZE * 2)

while (vadBuffer.size >= VAD_WINDOW_SIZE) {
    val vadWindow = FloatArray(VAD_WINDOW_SIZE) { i -> vadBuffer.elementAt(i) }
    vad!!.acceptWaveform(vadWindow)  // 每次传入512样本
    vad!!.isSpeechDetected()
    repeat(VAD_WINDOW_SIZE / 4) { vadBuffer.removeFirst() }
}
```

**问题**:
1. 外部手动管理缓冲区,与 VAD 内部缓冲冲突
2. 重复累积数据导致 VAD 内部 vector 溢出
3. 增加代码复杂度和内存占用

### 修改后 (✅ 正确)

```kotlin
// 不需要VAD缓冲区,只保留语音缓冲
private val speechBuffer = arrayListOf<Float>()

val speechDetected = if (vad != null) {
    try {
        // 直接传入完整音频数据 (~1600样本/100ms)
        vad!!.acceptWaveform(samples)
        vad!!.isSpeechDetected()
    } catch (e: Exception) {
        // 自动降级到能量检测
        vad = null
        detectSpeechByEnergy(samples)
    }
} else {
    detectSpeechByEnergy(samples)
}
```

**优点**:
1. 符合 Sherpa-ONNX VAD 的正确使用方式
2. 代码更简洁,移除了不必要的缓冲区管理
3. 添加了异常处理和降级机制
4. 减少内存占用

## 📊 验证结果

### 编译测试
```bash
✅ BUILD SUCCESSFUL in 4s
✅ 无 linter 错误
✅ 无语法错误
```

### 代码改动

**修改文件**: `SenseVoiceInputDevice.kt`

**删除内容**:
- `vadBuffer` 缓冲区及相关操作 (~50行代码)
- 复杂的滑动窗口逻辑

**新增内容**:
- 异常捕获和降级机制
- 改进的调试日志

**代码行数**: 净减少 ~40行

## 🎓 技术教训

1. **RTFM (Read The F***ing Manual)**
   - 不要根据参数名称(windowSize)臆测 API 用法
   - 查阅官方文档和示例代码至关重要

2. **理解工具的设计模式**
   - VAD 是**流式处理**工具,不是批处理
   - 内部状态管理由库自身负责

3. **添加防御性编程**
   - try-catch 捕获底层异常
   - 提供降级方案(能量检测)确保功能可用

## 📁 相关文件

- **修复文件**: `app/src/main/kotlin/org/stypox/dicio/io/input/sensevoice/SenseVoiceInputDevice.kt`
- **详细分析**: `doc/VAD_ACCEPTWAVEFORM_FIX.md`
- **配置管理**: `app/src/main/kotlin/org/stypox/dicio/io/input/sensevoice/VadModelManager.kt`

## 🔜 后续工作

1. **实机测试**
   - 验证 VAD 正常检测语音
   - 确认不再出现 vector 错误
   - 测试降级机制是否正常工作

2. **性能优化**
   - 监控 VAD 的 CPU 和内存使用
   - 评估是否需要调整 VAD 参数

3. **日志优化**
   - 减少冗余日志
   - 关键事件保留调试信息

---

**修复时间**: 2025-10-14 22:00  
**修复状态**: ✅ 编译通过,待实机验证  
**影响范围**: 语音输入模块 VAD 检测部分

