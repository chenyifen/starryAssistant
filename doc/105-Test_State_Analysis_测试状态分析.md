# 语音测试状态问题分析报告

## 📊 失败模式分类

### 类型1: 唤醒+监听OK，但无ASR结果 (5个案例)

这些案例唤醒词检测成功，ASR也开始监听，但最终没有输出任何ASR识别结果：

1. **sample2_07_google** (行357-359)
   ```
   01-13 23:50:46.222 I/AutoTest(24269): 唤醒词检测成功
   01-13 23:50:46.224 I/AutoTest(24269): ASR开始监听
   [无ASR结果]
   ```

2. **sample3_07_google** (行365-367)
   ```
   01-13 23:52:27.883 I/AutoTest(24269): 唤醒词检测成功
   01-13 23:52:27.884 I/AutoTest(24269): ASR开始监听
   [无ASR结果]
   ```

3. **sample6_07_google** (行397-399)
   ```
   01-13 23:56:38.979 I/AutoTest(24269): 唤醒词检测成功
   01-13 23:56:38.981 I/AutoTest(24269): ASR开始监听
   [无ASR结果]
   ```

4. **sample1_08_wifi** (行435-437)
   ```
   01-13 23:49:31.929 I/AutoTest(24269): 唤醒词检测成功
   01-13 23:49:31.931 I/AutoTest(24269): ASR开始监听
   [无ASR结果]
   ```

5. **sample2_08_wifi** (行443-445)
   ```
   01-13 23:51:01.948 I/AutoTest(24269): 唤醒词检测成功
   01-13 23:51:01.952 I/AutoTest(24269): ASR开始监听
   [无ASR结果]
   ```

**问题分析**：
- ✅ 唤醒词检测正常
- ✅ ASR监听器启动正常
- ❌ ASR没有输出任何识别结果（partial或final都没有）
- ❌ 没有"停止监听"或"超时"的日志

**可能原因**：
1. **VAD未检测到语音开始** - 音频能量不足或VAD阈值过高
2. **ASR录音线程异常** - AudioRecord未正常录制音频
3. **ASR处理超时** - 达到MAX_RECORDING_DURATION_MS但无日志输出
4. **音频数据丢失** - 从唤醒到ASR的音频交接出问题

---

### 类型2: 唤醒NO，但ASR监听OK (2个案例)

这是最异常的状态 - 日志显示没有唤醒，但ASR却开始监听了：

1. **sample7_10_blue** (行600-606)
   ```
   01-13 23:59:10.289 I/AutoTest(24269): ASR结果: 헤이너지.
   01-13 23:59:10.304 I/AutoTest(24269): 技能执行: text, 结果: 다시 말씀해 주시겠어요?
   01-13 23:59:10.307 I/AutoTest(24269): ASR开始监听
   01-13 23:59:14.709 I/AutoTest(24269): ASR结果: 다.
   01-13 23:59:14.725 I/AutoTest(24269): 技能执行: text, 结果: 이해하지 못했습니다
   ```

2. **sample8_10_blue** (行615-617)
   ```
   01-14 00:00:54.545 I/AutoTest(24269): ASR结果: <|Speech|>헤이너지.
   01-14 00:00:54.565 I/AutoTest(24269): 技能执行: text, 结果: 다시 말씀해 주시겠어요?
   01-14 00:00:54.567 I/AutoTest(24269): ASR开始监听
   ```

**问题分析**：
- ❌ 测试报告标记为"唤醒: NO"
- ✅ 但实际上ASR在监听
- ⚠️ sample7识别出"헤이너지"（唤醒词），但被当作ASR结果处理
- ⚠️ sample8识别出"<|Speech|>헤이너지"，也被当作ASR结果

**可能原因**：
1. **前一个测试的ASR未停止** - 连续测试时ASR保持监听状态
2. **唤醒词被ASR错误处理** - 唤醒词应该被唤醒设备处理，却被ASR识别了
3. **多轮对话状态异常** - 系统处于多轮对话模式，不需要唤醒就继续监听
4. **测试框架检测逻辑问题** - 测试脚本误判了唤醒状态

---

### 类型3: 唤醒NO，ASR监听NO (7个案例)

完全没有任何响应：

1. sample2_02_go (行36-41)
2. sample3_02_go (行42-48)
3. sample8_04_windows (行181-187)
4. sample1_07_google (行345-351)
5. sample6_06_youtube (行312-318)
6. sample4_10_blue (行570-576)

**可能原因**：
1. **音频质量问题** - 唤醒词音频太弱或失真
2. **前一测试干扰** - 连续测试时前一个测试的TTS或处理未完成
3. **音频焦点问题** - 音频焦点被其他应用占用
4. **唤醒设备状态错误** - 唤醒设备未处于监听状态

---

## 🔍 关键问题定位

### 问题1: ASR启动后无任何输出

**现象**：唤醒+监听都OK，但无ASR结果、无超时、无任何后续日志

**需要检查的代码点**：

1. **SenseVoiceInputDevice.kt** - `startListening()` 方法
   - AudioRecord是否正常启动
   - 录音线程是否正常运行
   - VAD是否能检测到语音

2. **VAD检测逻辑** - 音频能量计算
   ```kotlin
   val threshold = 0.003  // 当前阈值
   val detected = rms > threshold
   ```
   如果音频RMS一直低于0.003，VAD永远不会触发"语音开始"

3. **超时机制** - `MAX_RECORDING_DURATION_MS = 10000L`
   - 10秒超时后应该有日志输出
   - 如果无日志，说明录音循环提前退出

**建议检查的日志**：
```
🎤 检测到语音开始
🔊 能量检测触发
📊 音频数据
🔇 检测到静音超时
🏁 音频处理结束
```

---

### 问题2: 唤醒词被ASR识别

**现象**：sample7/8_10_blue中，"헤이너지"被识别为ASR结果

**分析**：
- 正常流程：唤醒设备检测 → 触发ASR → ASR识别命令
- 异常流程：ASR直接识别到了唤醒词 → 说明唤醒设备和ASR音频流冲突

**可能原因**：
1. 唤醒设备未停止，与ASR同时录音
2. 多轮对话模式下，ASR一直在监听
3. 音频流共享问题

---

### 问题3: 连续测试状态污染

**现象**：后续测试的失败率明显高于前面

**统计**：
- 02_go: sample1失败（韩语错误），sample2/3/4失败（无响应），sample5/7通过
- 到10_blue: 全部失败（0/4）

**可能原因**：
1. ASR未正确释放资源
2. 唤醒设备未重新进入监听状态
3. 音频焦点未释放
4. TTS播放未完成就开始下一个测试

---

## 💡 调试建议

### 1. 增强日志输出

在 `SenseVoiceInputDevice.kt` 的关键位置添加日志：

```kotlin
// startListening() 开始
Log.i("AutoTest", "🎙️ ASR启动: AudioRecord.state=${audioRecord?.state}")

// 录音循环
Log.i("AutoTest", "🔊 音频帧#$frameCount: size=$audioData.size, rms=$rms, threshold=$threshold")

// VAD检测
if (speechDetected) {
    Log.i("AutoTest", "🎤 VAD检测到语音: rms=$rms > threshold=$threshold")
}

// 超时检测
if (超时) {
    Log.i("AutoTest", "⏱️ ASR超时: duration=${duration}ms, maxDuration=${MAX_RECORDING_DURATION_MS}ms")
}

// 正常结束
Log.i("AutoTest", "✅ ASR停止: reason=$reason, hasResult=${partialText.isNotEmpty()}")
```

### 2. 检查连续测试间隔

在测试脚本中添加更长的延迟：

```bash
# 当前间隔可能太短
sleep 5  # 增加到5秒，确保前一个测试完全结束
```

### 3. 添加状态检查

在每个测试开始前检查系统状态：

```bash
# 检查ASR是否还在运行
adb shell "dumpsys media.audio_policy | grep AudioRecord"

# 检查音频焦点
adb shell "dumpsys audio | grep 'Audio Focus'"
```

---

## 🎯 优先修复建议

### Priority 1: ASR无输出问题

**影响**: 5个测试用例
**位置**: `SenseVoiceInputDevice.kt`
**修复方向**:
1. 降低VAD能量阈值：`0.003 → 0.002`
2. 添加超时兜底日志
3. 检查AudioRecord状态

### Priority 2: 连续测试状态管理

**影响**: 可能导致后续所有测试失败
**位置**: 测试脚本 + ASR/Wake设备
**修复方向**:
1. 确保ASR在测试间完全停止
2. 确保唤醒设备重新进入监听状态
3. 增加测试间隔时间

### Priority 3: 唤醒词与ASR冲突

**影响**: 2个测试用例（10_blue）
**位置**: 音频流管理
**修复方向**:
1. 检查唤醒设备是否在ASR启动时正确停止
2. 确认音频流独占性
3. 多轮对话状态清理

---

## 📋 下一步行动

1. ✅ 命令变体已添加（device_control.yml已更新）
2. ⏳ 添加详细日志定位ASR无输出问题
3. ⏳ 检查连续测试的状态清理逻辑
4. ⏳ 优化VAD阈值和超时设置
5. ⏳ 重新运行测试验证修复效果

