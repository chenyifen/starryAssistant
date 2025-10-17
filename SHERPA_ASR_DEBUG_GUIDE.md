# Sherpa-ONNX ASR 详细调试指南

## 修改内容总结

### 1. 减少唤醒服务日志输出 ✅
**修改文件:**
- `app/src/main/kotlin/org/stypox/dicio/io/wake/WakeService.kt`
- `app/src/main/kotlin/org/stypox/dicio/io/wake/onnx/HiNudgeOnnxV8WakeDevice.kt`
- `app/src/main/kotlin/org/stypox/dicio/io/wake/oww/HiNudgeOpenWakeWordDevice.kt`

**修改内容:**
- 注释掉每100帧/1000帧的常规日志
- 只在检测到唤醒词时打印日志
- 减少日志刷屏，便于调试ASR问题

### 2. 增强Sherpa ASR调试日志 ✅
**修改文件:**
- `app/src/main/kotlin/org/stypox/dicio/io/input/sherpa_simulate/SherpaOnnxSimulateInputDevice.kt`

**新增日志内容:**

#### A. 音频采集阶段
```kotlin
🎙️ AudioRecord 开始录音
🎵 音频采集统计 - Frame: X | Samples: X | MaxAmp: X | Energy: X
🔊 检测到音频信号 - Frame: X | Amp: X | Energy: X | Samples: X
🎵 音频采集结束 - 总帧数: X, 总样本: X, 最大振幅: X
```

#### B. 音频处理阶段
```kotlin
🔄 启动音频处理
✅ VAD和识别器已就绪，开始处理音频流
📥 接收到音频样本 - Frame: X | Samples: X | Buffer: X
📦 已接收音频帧: X | Buffer大小: X | Offset: X
```

#### C. VAD检测阶段
```kotlin
🎤 VAD Frame - offset: X | buffer: X | energy: X | speech: ✅/❌ | started: YES/NO
🎯 语音开始检测!
⏱️ [T1] 语音检测时间: Xms (检测延迟: Xms)
📊 VAD状态 - buffer: X | offset: X | energy: X
```

#### D. 实时识别阶段
```kotlin
🔍 [Partial Recognition] 开始实时识别...
   音频数据: X 样本 (X 秒)
   音频能量: X
   ✓ Stream 已创建
   ✓ 音频数据已提交
   ✓ 解码完成
   识别结果: "XXX" (X 字符)
✅ [Partial] 识别成功!
   📊 总耗时: Xms
   📊 检测延迟: Xms
   📊 识别耗时: Xms
   📝 文本: "XXX"
📤 [Partial] 首次发送/更新结果: XXX
```

#### E. 最终识别阶段
```kotlin
🏁 [Final Recognition] 开始最终识别...
   语音段数据: X 样本 (X 秒)
   语音段起始: X 秒
   音频能量: X
   ✓ Stream 已创建
   ✓ 音频数据已提交
   ✓ 解码完成
   识别结果: "XXX" (X 字符)
🏁 [Final] 识别完成!
   📊 总耗时: Xms
   📊 识别耗时: Xms
   📝 文本: "XXX"
   🔄 上一次部分结果: "XXX"
📤 [Final] 更新为最终结果/直接添加最终结果: XXX
```

## 当前问题分析

### 问题现象
从用户日志：
```
12:41:19.305 - AudioRecord 开始录音
12:41:24.287 - Auto-dismissing half screen （5秒后自动关闭）
12:41:24.539 - 停止语音识别 - 原因: 用户主动停止
```

**关键发现:**
1. ❌ 没有任何VAD日志
2. ❌ 没有任何识别日志
3. ❌ 5秒后自动关闭
4. ❌ 用户说了"返回主页"但没有识别

### 问题根因

#### 1. 5秒自动关闭机制
**位置:** `AssistantUIController.kt:32`
```kotlin
private const val AUTO_DISMISS_DELAY = 5000L // 5秒后自动收起
```

**触发条件:**
- 半屏模式展开后，如果5秒内没有交互
- 自动收起界面，触发STT停止

#### 2. VAD未检测到语音
**可能原因:**
- 麦克风权限问题
- AudioRecord数据为空
- VAD未接收到音频数据
- 音频能量太低

#### 3. 音频采集问题
**需要检查:**
- AudioRecord是否正常读取数据
- 音频振幅是否足够
- Channel是否正常传输数据

## 调试步骤

### 第一步：检查音频采集
重新测试后，查找以下日志：

```
搜索: "音频采集统计"
期望: 每5秒应该看到一次统计日志
问题: 如果没有日志，说明AudioRecord没有数据

搜索: "检测到音频信号"  
期望: 说话时应该看到这个日志
问题: 如果没有，说明麦克风没有采集到声音
```

### 第二步：检查音频处理
```
搜索: "VAD和识别器已就绪"
期望: 应该看到这个日志
问题: 如果没有，说明VAD或识别器初始化失败

搜索: "接收到音频样本"
期望: 应该持续看到这个日志（每100ms一次）
问题: 如果没有，说明Channel没有传输数据
```

### 第三步：检查VAD检测
```
搜索: "VAD Frame"
期望: 每10帧（约640ms）应该看到一次
问题: 如果没有，说明VAD没有处理数据

搜索: "语音开始检测"
期望: 说话时应该看到
问题: 如果没有，VAD没有检测到语音
```

### 第四步：检查识别过程
```
搜索: "[Partial Recognition]"
期望: 检测到语音后每200ms应该看到
问题: 如果没有，说明实时识别没有触发

搜索: "[Final Recognition]"
期望: 语音结束后应该看到
问题: 如果没有，说明最终识别没有触发
```

## 常见问题诊断

### 问题1：完全没有音频采集日志
**症状:**
- 没有"音频采集统计"日志
- 没有"检测到音频信号"日志

**原因:**
- AudioRecord初始化失败
- 麦克风权限被拒绝
- 音频源被其他应用占用

**解决:**
```bash
# 检查权限
adb logcat | grep "RECORD_AUDIO"

# 检查AudioRecord初始化
adb logcat | grep "AudioRecord"
```

### 问题2：有音频采集但VAD没反应
**症状:**
- 有"音频采集统计"日志
- 但没有"VAD Frame"日志

**原因:**
- Channel传输失败
- VAD处理协程未启动
- Buffer累积不足

**解决:**
- 检查是否有"接收到音频样本"日志
- 检查Buffer大小是否增长

### 问题3：VAD检测到语音但没识别
**症状:**
- 有"语音开始检测"日志
- 但没有"[Partial Recognition]"日志

**原因:**
- 200ms定时器未触发
- 识别器崩溃
- Buffer数据问题

**解决:**
- 检查"语音进行中"日志
- 查看elapsed时间是否超过200ms

### 问题4：识别结果为空
**症状:**
- 有"[Partial Recognition]"日志
- 但显示"识别结果为空"

**原因:**
- 音频数据质量太差
- 语言不匹配
- 模型问题

**解决:**
- 检查音频能量值
- 确认说的是支持的语言（中/英/日/韩/粤）
- 验证模型文件完整性

## 使用logcat过滤查看

### 查看所有Sherpa相关日志
```bash
adb logcat -s SherpaSimulate:D SherpaOnnxManager:D
```

### 只看关键信息日志
```bash
adb logcat -s SherpaSimulate:I
```

### 查看详细调试日志
```bash
adb logcat -s SherpaSimulate:D SherpaOnnxManager:D | grep -E "音频|VAD|识别|Recognition"
```

### 查看音频采集统计
```bash
adb logcat -s SherpaSimulate:D | grep "音频采集"
```

### 查看VAD检测
```bash
adb logcat -s SherpaSimulate:D | grep "VAD"
```

### 查看识别结果
```bash
adb logcat -s SherpaSimulate:I | grep "📤"
```

## 性能分析

通过日志中的耗时数据分析性能：

### 检测延迟
```
⏱️ [T1] 语音检测时间: Xms (检测延迟: Xms)
```
**正常值:** < 500ms
**问题阈值:** > 1000ms

### 实时识别耗时
```
📊 识别耗时: Xms
```
**正常值:** < 200ms
**问题阈值:** > 500ms

### 最终识别耗时
```
🏁 [Final] 识别耗时: Xms
```
**正常值:** < 300ms
**问题阈值:** > 1000ms

### 总体响应时间
```
📊 总耗时: Xms
```
**正常值:** < 2000ms（从开始录音到首次识别）
**问题阈值:** > 5000ms

## 下一步测试

1. **重新编译安装** 包含新调试日志的版本
2. **清空logcat** `adb logcat -c`
3. **开始测试** 唤醒后说话
4. **导出日志** `adb logcat -d > sherpa_debug.log`
5. **分析日志** 按照上面的步骤检查每个阶段

## 临时禁用5秒自动关闭

如果想要更长的测试时间，可以临时修改：

**文件:** `AssistantUIController.kt:32`
```kotlin
// 修改前
private const val AUTO_DISMISS_DELAY = 5000L // 5秒

// 修改后（用于调试）
private const val AUTO_DISMISS_DELAY = 30000L // 30秒
```

## 总结

现在的日志系统可以帮助你完整追踪：
1. ✅ 音频采集是否正常
2. ✅ 数据是否正确传输
3. ✅ VAD是否检测到语音
4. ✅ 识别过程是否执行
5. ✅ 识别结果是否正确
6. ✅ 每个阶段的耗时

通过这些详细的日志，可以精确定位问题出在哪个环节！

