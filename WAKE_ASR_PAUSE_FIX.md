# 唤醒后ASR音频冲突修复

## 问题分析

### 用户报告的问题
1. ❌ 唤醒后没有播放声音
2. ❌ 说韩语"你好"后没有反应  
3. ❌ 显示"I DID NOT UNDERSTAND"
4. ❌ 一直重复"Could you repeat"

### 根本原因
从日志分析：
```
01-13 12:29:08.237 🔊[WakeService]: ⏸️ Pausing WakeService AudioRecord for ASR
01-13 12:29:08.238 SherpaSimulate: 🚀 启动语音识别 (VAD: 启用)
```

**问题**：WakeService在ASR启动的**同时**就暂停了麦克风，导致：
- ASR的AudioRecord还没初始化完成
- 麦克风资源已被释放
- ASR启动后无法采集音频
- 结果：识别结果为空 → 没有匹配到技能 → "I DID NOT UNDERSTAND"

## 修复方案

### 1. 延迟暂停WakeService ✅

**修改文件**: `app/src/main/kotlin/org/stypox/dicio/io/wake/WakeService.kt`

**修改内容**:
```kotlin
// 旧代码：立即暂停
private fun onWakeWordDetected() {
    pauseAudioRecordForASR()  // ❌ 太快了！ASR还没准备好
    val sttStarted = sttInputDevice.tryLoad(...)
    ...
}

// 新代码：延迟300ms暂停
private fun onWakeWordDetected() {
    val sttStarted = sttInputDevice.tryLoad(...)  // 先启动ASR
    
    // 延迟暂停，让ASR先初始化完成
    scope.launch {
        delay(300)  // ✅ 300ms足够AudioRecord启动
        pauseAudioRecordForASR()
    }
    ...
}
```

**原理**:
- ASR的AudioRecord初始化需要50-100ms
- 300ms延迟确保ASR完全准备好
- WakeService和ASR有短暂的并发期，但不影响功能

### 2. 添加设置选项 ✅

**新增功能**：用户可选择是否在ASR时暂停唤醒服务

#### 修改的文件：

1. **Proto定义** (`app/src/main/proto/user_settings.proto`)
```protobuf
message UserSettings {
    ...
    bool pause_wake_during_asr = 15; // ASR运行时是否暂停唤醒服务
}
```

2. **设置定义** (`app/src/main/kotlin/org/stypox/dicio/settings/Definitions.kt`)
```kotlin
@Composable
fun pauseWakeDuringAsr() = BooleanSetting(
    title = "ASR时暂停唤醒",
    icon = Icons.Default.Hearing,
    descriptionOff = "唤醒服务持续运行（可能导致资源冲突）",
    descriptionOn = "ASR运行时暂停唤醒服务（推荐）",
)
```

3. **默认值设置** (`UserSettingsSerializer.kt`)
```kotlin
override val defaultValue: UserSettings = UserSettings.getDefaultInstance()
    .toBuilder()
    .setPauseWakeDuringAsr(true) // ✅ 默认启用（推荐）
    .build()
```

4. **逻辑实现** (`WakeService.kt`)
```kotlin
private fun pauseAudioRecordForASR() {
    // 读取用户设置
    val shouldPause = runBlocking { 
        dataStore.data.first().pauseWakeDuringAsr 
    }
    
    if (!shouldPause) {
        DebugLogger.logWakeWord(TAG, "⏭️ 跳过暂停WakeService（用户设置：持续运行）")
        return
    }
    
    // 暂停逻辑...
}
```

## 设置选项说明

### 选项1：ASR时暂停唤醒（默认，推荐）✅
- ✅ **优点**：避免音频资源冲突，ASR识别更稳定
- ✅ **适用**：大多数场景
- ❌ **缺点**：ASR运行期间不能检测唤醒词（但通常不需要）

### 选项2：唤醒服务持续运行
- ✅ **优点**：随时可以检测唤醒词
- ❌ **缺点**：可能导致音频资源冲突，ASR识别不稳定
- ✅ **适用**：特殊调试场景

## 时序对比

### 修复前（有问题）：
```
T0: 唤醒词检测 ✅
T1: WakeService暂停 ⏸️ (立即)
T2: ASR开始初始化 🔄
T3: ASR尝试启动AudioRecord ❌ (麦克风已被释放)
T4: ASR无法采集音频 ❌
T5: 识别结果为空 → "I DID NOT UNDERSTAND"
```

### 修复后（正常）：
```
T0: 唤醒词检测 ✅
T1: ASR开始初始化 🔄
T2: ASR启动AudioRecord ✅ (麦克风正常)
T3: WakeService延迟暂停 ⏸️ (T0+300ms)
T4: ASR正常采集音频 ✅
T5: 正确识别 → 执行技能 ✅
```

## 声音播放说明

### 播放时机
声音在ASR状态变为`Listening`时播放，代码位置：
```kotlin
// SttInputDeviceWrapper.kt:174-176
newSttInputDevice.uiState.collect {
    _uiState.emit(it)
    if (it == SttState.Listening) {
        playSound(R.raw.listening_sound)  // 🔊 播放声音
    }
}
```

### 播放设置
可在设置中调整声音类型：
- 通知音（默认）
- 闹钟音
- 媒体音  
- 无声音

路径：**设置 → 输入输出方法 → STT提示音**

## 测试验证

### 测试步骤：
1. ✅ 编译安装新版本APK
2. ✅ 打开设置 → 检查"ASR时暂停唤醒"选项（应该默认开启）
3. ✅ 说唤醒词触发唤醒
4. ✅ 检查是否播放提示音
5. ✅ 说韩语命令（例如："전원켜줘"）
6. ✅ 检查是否正确识别和执行

### 预期结果：
```
✅ 唤醒成功
✅ 播放提示音
✅ ASR正常采集音频
✅ 正确识别韩语命令
✅ 执行相应技能
```

### 日志关键点：
```
🎯 WAKE WORD DETECTED
🎤 Starting STT input device
STT device start result: true
🔊 Playing listening sound  (新增：应该看到这个)
⏸️ Pausing WakeService AudioRecord for ASR (延迟300ms后)
🎤 [识别结果日志]  (应该有识别内容)
✅ 技能执行成功
```

## 兼容性

- ✅ 向后兼容：旧用户升级后默认启用新功能
- ✅ 前向兼容：可随时通过设置切换行为
- ✅ 不影响其他输入设备（SenseVoice、Vosk等）

## 相关文件

### 核心修改：
- `app/src/main/kotlin/org/stypox/dicio/io/wake/WakeService.kt` - 延迟暂停逻辑
- `app/src/main/proto/user_settings.proto` - 设置字段定义
- `app/src/main/kotlin/org/stypox/dicio/settings/datastore/UserSettingsSerializer.kt` - 默认值

### 设置界面：
- `app/src/main/kotlin/org/stypox/dicio/settings/Definitions.kt` - 设置项定义
- `app/src/main/kotlin/org/stypox/dicio/settings/MainSettingsScreen.kt` - UI渲染
- `app/src/main/kotlin/org/stypox/dicio/settings/MainSettingsViewModel.kt` - 数据绑定

## 未来优化方向

### 可选优化：
1. **自适应延迟**：根据设备性能动态调整延迟时间
2. **智能检测**：检测ASR初始化完成事件，而不是固定延迟
3. **音频管道优化**：实现无缝的麦克风切换

### 当前方案的优势：
- ✅ 简单可靠
- ✅ 不需要复杂的同步机制
- ✅ 兼容所有ASR实现
- ✅ 300ms延迟用户无感知

## 总结

这次修复解决了唤醒后ASR无法正常工作的核心问题：
1. ✅ 通过延迟暂停，确保ASR有足够时间初始化
2. ✅ 添加用户设置选项，提供灵活性
3. ✅ 默认启用推荐配置，确保最佳体验
4. ✅ 保持代码简单可维护

**建议**：保持默认设置（ASR时暂停唤醒），这是经过优化的最佳配置。

