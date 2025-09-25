# SIGSEGV 根本原因分析 & 解决方案

## 问题现象
```
01-13 13:14:30.722  4498  4524 F libc    : Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0 in tid 4524 (DefaultDispatch), pid 4498 (ox.dicio.master)
```
崩溃发生在 `recognizer.getResult(stream)` 调用时。

## 根本原因分析

### 1. 初步分析 - 资源冲突假设
最初认为是 SenseVoice ASR (`OfflineRecognizer`) 和 SherpaOnnx KWS (`KeywordSpotter`) 之间的资源冲突导致的 SIGSEGV。

**已采取的措施**:
- ✅ 实现独立实例：确保每个组件使用独立的 SherpaOnnx 实例
- ✅ 添加线程安全：实例级别的 `Mutex` 保护
- ✅ 改进资源管理：增强 `destroy()` 和 `release()` 方法

### 2. 干扰排除测试
为了排除 WakeService 的干扰，临时禁用了 WakeService：

```kotlin
// 在 MainActivity.kt 中
// 临时禁用WakeService以排除对SenseVoice的干扰
Log.d("MainActivity", "🔇 临时禁用WakeService以测试SenseVoice")
WakeService.stop(this)
```

### 3. 关键发现 - API 使用错误

通过对比 **SherpaOnnxSimulateStreamingAsr 官方示例**，发现关键问题：

#### ❌ 错误的实现 (导致 SIGSEGV)
```kotlin
// 我们之前的错误实现
stream.acceptWaveform(audioData, SAMPLE_RATE)
stream.inputFinished()  // ⚠️ 这个调用导致了问题！
recognizer.decode(stream)
val result = recognizer.getResult(stream)
```

#### ✅ 正确的实现 (官方示例)
```kotlin
// SherpaOnnxSimulateStreamingAsr 官方示例
stream.acceptWaveform(audioData, sampleRateInHz)
// 注意：没有调用 inputFinished()！
recognizer.decode(stream)
val result = recognizer.getResult(stream)
stream.release()
```

### 4. 核心问题
**`stream.inputFinished()` 的错误调用是导致 SIGSEGV 的根本原因！**

在 SherpaOnnx 的 `OfflineRecognizer` 中：
- `inputFinished()` 方法应该只在特定场景下调用
- 对于实时/流式识别场景，**不应该**调用 `inputFinished()`
- 调用 `inputFinished()` 后再调用 `getResult()` 会导致内存访问违规

## 解决方案

### 1. 修复 SenseVoiceRecognizer
按照 SherpaOnnxSimulateStreamingAsr 官方示例修复：

```kotlin
// 修复后的正确实现
stream.acceptWaveform(audioData, SAMPLE_RATE)
// 移除 stream.inputFinished() 调用
recognizer.decode(stream)
val result = recognizer.getResult(stream)  // 现在应该不会崩溃
stream.release()
```

### 2. 保持其他改进
保留之前的改进措施：
- ✅ 独立实例：避免资源冲突
- ✅ 线程安全：实例级别同步
- ✅ 异常处理：安全的错误处理
- ✅ 资源管理：完善的清理机制

### 3. 临时禁用 WakeService
在验证修复效果时，保持 WakeService 禁用状态，避免干扰测试。

## 验证计划

1. **编译测试**: 确保修复后代码能正常编译
2. **功能测试**: 在禁用 WakeService 的情况下测试 SenseVoice 独立运行
3. **稳定性测试**: 多次触发语音识别，确认不再出现 SIGSEGV
4. **集成测试**: 确认修复后重新启用 WakeService，验证两个组件能和谐共存

## 经验教训

1. **API 文档的重要性**: 仔细阅读官方示例和文档至关重要
2. **盲目移植的风险**: 不能简单地从其他项目(如 HandsFree)复制 API 调用方式
3. **官方示例优先**: 应该以官方示例 (SherpaOnnxSimulateStreamingAsr) 为准
4. **逐步排查**: 通过禁用干扰因素来逐步定位问题是有效的调试方法

## 结论

SIGSEGV 的根本原因是**错误调用了 `stream.inputFinished()`**，而不是资源冲突。移除这个调用后，应该能解决崩溃问题。独立实例和线程安全的改进仍然有价值，能提供更好的稳定性和可维护性。
