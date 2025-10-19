# 自动化测试集成指南

## 📋 概述

本文档说明如何使用 `voice_assistant_test` 测试框架对 Dicio 语音助手进行自动化测试。

## 🎯 测试日志点

应用已在以下关键位置添加 `AutoTest` 日志：

### 1. 唤醒词检测
**位置**: `WakeService.kt`
```kotlin
AutoTestLogger.logWakeupDetected()
```
**日志输出**: `AutoTest: 唤醒词检测成功`

### 2. ASR识别结果
**位置**: `SkillEvaluator.kt`
```kotlin
Log.i("AutoTest", "ASR结果: $firstUtterance")
```
**日志输出**: `AutoTest: ASR结果: 홈 화면으로 이동해줘`

### 3. 技能执行
**位置**: `SkillEvaluator.kt`
```kotlin
AutoTestLogger.logSkillExecuted(skillInfo.id, speechResult)
```
**日志输出**: `AutoTest: 技能执行: device_control, 结果: 홈 화면으로 이동합니다`

### 4. 测试启动
**位置**: `EnhancedFloatingWindowService.kt`
```kotlin
AutoTestLogger.logTestStarted()
AutoTestLogger.logOrbClicked()
```
**日志输出**: 
- `AutoTest: 收到自动化测试启动指令`
- `AutoTest: 悬浮球被点击`

### 5. ASR监听状态
**位置**: `SenseVoiceInputDevice.kt`
```kotlin
AutoTestLogger.logAsrListeningStarted()
```
**日志输出**: `AutoTest: ASR开始监听`

## 🔧 测试框架配置

测试脚本已更新日志模式以匹配应用输出：

```python
self.patterns = {
    'wakeup_detected': r'(唤醒词检测成功|WAKE WORD DETECTED)',
    'asr_result': r'ASR结果:',
    'command_executed': r'技能执行:',
    'test_started': r'收到自动化测试启动指令',
    'orb_clicked': r'悬浮球被点击',
    'asr_listening_started': r'ASR开始监听',
    'error': r'(error|错误|failed|失败|测试错误)',
}
```

## 🚀 运行测试

### 前置条件
1. 确保设备已连接并通过 `adb devices` 可见
2. 应用已安装并运行
3. 已授予必要权限（麦克风、悬浮窗）

### 快速测试
```bash
cd /Users/user/AndroidStudioProjects/useCosyVoice/voice_assistant_test
python3 run_tests.py --mode quick
```

### 完整测试
```bash
python3 run_tests.py --mode full
```

### 仅唤醒测试
```bash
python3 run_tests.py --mode wakeup
```

### 交互式模式
```bash
python3 run_tests.py --mode interactive
```

## 📊 测试流程

1. **启动测试**: 测试脚本发送广播 `com.xiaozhi.AUTO_TEST_START`
2. **应用响应**: 
   - 记录 `收到自动化测试启动指令`
   - 记录 `悬浮球被点击`
   - 开始ASR监听
3. **播放音频**: 测试脚本通过 `adb shell` 播放唤醒词/指令音频
4. **检测唤醒**: 应用检测到唤醒词，记录 `唤醒词检测成功`
5. **识别语音**: 应用识别语音，记录 `ASR结果: xxx`
6. **执行技能**: 应用执行匹配的技能，记录 `技能执行: xxx, 结果: xxx`
7. **验证结果**: 测试脚本解析日志，验证测试是否通过

## 📝 日志查看

### 实时查看AutoTest日志
```bash
adb logcat -s AutoTest:*
```

### 查看所有测试相关日志
```bash
adb logcat | grep -E "(AutoTest|WakeService|SkillEvaluator|SenseVoice)"
```

### 保存日志到文件
```bash
adb logcat -s AutoTest:* > autotest.log
```

## 🎨 AutoTestLogger API

### 可用方法

```kotlin
// 唤醒词检测
AutoTestLogger.logWakeupDetected()

// ASR识别结果
AutoTestLogger.logAsrResult(text: String)

// 技能执行
AutoTestLogger.logSkillExecuted(skillId: String, result: String)

// 测试启动
AutoTestLogger.logTestStarted()

// 悬浮球点击
AutoTestLogger.logOrbClicked()

// ASR监听状态
AutoTestLogger.logAsrListeningStarted()
AutoTestLogger.logAsrListeningStopped()

// TTS状态
AutoTestLogger.logTtsStarted(text: String)
AutoTestLogger.logTtsCompleted()

// 错误
AutoTestLogger.logTestError(message: String)
```

## 🔍 故障排查

### 问题1: 测试脚本无法检测到日志
**解决方案**: 
- 确认应用正在运行
- 检查 `adb logcat -s AutoTest:*` 是否有输出
- 验证测试脚本的日志模式是否正确

### 问题2: 唤醒词未被检测
**解决方案**:
- 检查唤醒词模型是否正确加载
- 查看 WakeService 日志
- 确认音频播放音量足够

### 问题3: ASR识别失败
**解决方案**:
- 检查 SenseVoice 模型是否正确加载
- 查看 SenseVoiceInputDevice 日志
- 确认麦克风权限已授予

### 问题4: 技能未执行
**解决方案**:
- 检查技能匹配分数
- 查看 SkillEvaluator 日志
- 验证技能定义文件（.yml）

## 📈 测试报告

测试完成后，报告保存在：
```
/Users/user/AndroidStudioProjects/useCosyVoice/voice_assistant_test/reports/
```

报告包含：
- 测试时间
- 通过/失败数量
- 详细的测试结果
- 识别准确率
- 响应时间统计

## 🎯 最佳实践

1. **定期运行测试**: 每次代码更改后运行快速测试
2. **保留测试日志**: 便于问题追踪和性能分析
3. **更新音频文件**: 使用真实的用户语音样本
4. **监控识别率**: 目标 > 90% 的识别准确率
5. **检查响应时间**: 唤醒到技能执行应 < 3秒

## 📚 相关文档

- [AUTO_TEST_README.md](AUTO_TEST_README.md) - 自动化测试系统说明
- [voice_assistant_test/README.md](../useCosyVoice/voice_assistant_test/README.md) - 测试框架详细文档
- [VOICE_COMMAND_TEST_LIST.md](VOICE_COMMAND_TEST_LIST.md) - 支持的语音指令列表

## 🤝 贡献

如需添加新的测试日志点：

1. 在 `AutoTestLogger.kt` 中添加新方法
2. 在相应位置调用该方法
3. 更新 `adb_logger.py` 中的日志模式
4. 更新本文档

---

**最后更新**: 2025-01-13
**维护者**: AI Assistant

