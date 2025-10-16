# 语音识别停止原因日志增强

## 修改目的
在停止语音识别时打印详细的停止原因，便于调试和用户理解。

## 实现方案

### 1. 新增停止原因枚举

```kotlin
enum class StopReason(val displayName: String) {
    USER_REQUESTED("用户主动停止"),
    SPEECH_END_DETECTED("检测到语音结束"),
    SILENCE_TIMEOUT("静音超时"),
    MAX_DURATION_REACHED("达到最大录音时长"),
    ERROR("发生错误"),
    DEVICE_DESTROY("设备销毁"),
    TOGGLE_OFF("切换关闭")
}
```

### 2. 修改 stopListening 方法

**修改前**：
```kotlin
override fun stopListening() {
    Log.d(TAG, "🛑 停止语音识别")
    // ...
}
```

**修改后**：
```kotlin
override fun stopListening() {
    stopListeningWithReason(StopReason.USER_REQUESTED)
}

private fun stopListeningWithReason(reason: StopReason) {
    Log.i(TAG, "🛑 停止语音识别 - 原因: ${reason.displayName}")
    // ...
}
```

### 3. 各场景的停止原因

| 触发场景 | 停止原因 | 日志示例 |
|---------|---------|---------|
| 用户点击停止 | `USER_REQUESTED` | 🛑 停止语音识别 - 原因: 用户主动停止 |
| 点击切换按钮 | `TOGGLE_OFF` | 🛑 停止语音识别 - 原因: 切换关闭 |
| VAD检测语音结束 | (不触发stopListening) | 🔚 检测到语音段结束 - 开始最终识别<br>✅ 语音段处理完成，等待下一段语音... |
| VAD处理错误 | `ERROR` | 🛑 停止语音识别 - 原因: 发生错误 |
| 设备销毁 | `DEVICE_DESTROY` | 🛑 停止语音识别 - 原因: 设备销毁 |

### 4. 语音段结束检测日志

在VAD检测到语音结束时，增加了详细的日志：

```kotlin
while (!vadInstance.empty()) {
    Log.d(TAG, "🔚 检测到语音段结束 - 开始最终识别")
    performFinalRecognition(vadInstance, recognizerInstance)
    
    vadInstance.pop()
    isSpeechStarted = false
    buffer = arrayListOf()
    offset = 0
    Log.d(TAG, "✅ 语音段处理完成，等待下一段语音...")
}
```

## 日志输出示例

### 正常流程

```
🚀 启动语音识别 (VAD: 启用)
🎤 开始音频采集
⏱️ [T1] 语音检测时间: 1234ms (检测延迟: 123ms)
🔄 [Partial] 识别耗时: 45ms | 文本: "你好"
🔄 [Partial] 识别耗时: 50ms | 文本: "你好世界"
🔚 检测到语音段结束 - 开始最终识别
🏁 [Final] 总耗时: 2345ms | 识别耗时: 67ms | 文本: "你好世界"
✅ 语音段处理完成，等待下一段语音...
🛑 停止语音识别 - 原因: 用户主动停止
   ✓ AudioRecord已停止
```

### 错误流程

```
🚀 启动语音识别 (VAD: 启用)
🎤 开始音频采集
❌ VAD处理失败: java.lang.Exception
🛑 停止语音识别 - 原因: 发生错误
   ✓ AudioRecord已停止
```

## 未来扩展

可以根据需要添加更多停止原因：
- `SILENCE_TIMEOUT` - 静音超时（需要实现超时检测逻辑）
- `MAX_DURATION_REACHED` - 最大时长限制（需要实现时长检测）
- 其他业务需求的停止原因

## 调试建议

使用以下adb命令过滤日志：
```bash
# 查看所有停止原因
adb logcat | grep "停止语音识别 - 原因"

# 查看语音段结束
adb logcat | grep "检测到语音段结束"

# 查看完整流程
adb logcat | grep "SherpaSimulate"
```

