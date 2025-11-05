# AudioRecord初始化失败问题分析与修复

## 问题现象

```
11-02 10:01:02.229  7284  7369 E 🔊[WakeService]: ❌ AudioRecord not initialized: source=DEFAULT, state=0
```

- **错误代码**: `state=0` (AudioRecord.STATE_UNINITIALIZED)
- **位置**: `WakeService.kt:286` - `createOptimalAudioRecord()`方法
- **影响**: 唤醒词服务无法启动AudioRecord录音

## 根本原因分析

### 1. **严重逻辑缺陷**（已修复✅）

在 `listenForWakeWord()` 方法中发现致命bug:

```kotlin
// ❌ 原代码 - 获取了granted但从未检查！
val granted = runBlocking {
    AudioResourceManager.requestMicrophone(AudioResourceManager.AudioOwner.WAKE_SERVICE)
}

DebugLogger.logWakeWord(TAG, "✅ 成功获取麦克风资源")  // 总是执行！
```

**问题**:
- `AudioResourceManager.requestMicrophone()` 可能返回 `false`
- 代码获取了返回值但**从未检查**
- 无论资源请求成功与否，都继续执行AudioRecord初始化
- 导致AudioRecord在资源被占用时初始化失败

### 2. **可能的拒绝原因**

根据 `AudioResourceManager.kt` 的实现，麦克风请求会在以下情况被拒绝：

#### a. TTS正在播放
```kotlin
// AudioResourceManager.kt:86-90
if (isTtsPlaying.get()) {
    Log.w(TAG, "❌ [$owner] 请求麦克风被拒绝：TTS正在播放")
    return@withLock false
}
```

#### b. 其他持有者占用
```kotlin
// AudioResourceManager.kt:99-102
if (_currentOwner.value != AudioOwner.NONE) {
    Log.w(TAG, "⚠️ [$owner] 请求麦克风，但当前持有者是 ${_currentOwner.value}，强制抢占")
    // 虽然会强制抢占，但可能存在时序问题
}
```

### 3. **时序竞态条件**

典型场景：
1. WakeService启动 → 请求麦克风
2. TTS突然开始播放 → 设置 `isTtsPlaying=true`
3. 麦克风请求被拒绝 → 返回 `false`
4. **但代码没有检查返回值** → 继续初始化AudioRecord
5. AudioRecord初始化失败 → `state=0`

## 已实施的修复

### 修复1: 添加资源请求检查（WakeService.kt）

```kotlin
// ✅ 修复后的代码
val granted = runBlocking {
    AudioResourceManager.requestMicrophone(AudioResourceManager.AudioOwner.WAKE_SERVICE)
}

if (!granted) {
    DebugLogger.logWakeWordError(TAG, "❌ 麦克风资源请求被拒绝")
    return
}

DebugLogger.logWakeWord(TAG, "✅ 成功获取麦克风资源")
```

**效果**:
- 如果资源请求被拒绝，立即返回，不再尝试初始化AudioRecord
- 避免无效的AudioRecord初始化导致的错误日志

### 修复2: 增强AudioResourceManager日志（AudioResourceManager.kt）

```kotlin
suspend fun requestMicrophone(owner: AudioOwner): Boolean {
    return resourceMutex.withLock {
        Log.d(TAG, "🔍 [$owner] 请求麦克风 - 当前状态: ${_audioState.value}, 持有者: ${_currentOwner.value}, TTS: ${isTtsPlaying.get()}")
        // ...
    }
}
```

**效果**:
- 每次请求麦克风时输出详细状态
- 便于诊断资源竞争问题

## 诊断步骤

如果问题仍然存在，按以下顺序检查：

### 1. 检查AudioResourceManager状态

查找日志关键字：
```bash
adb logcat | grep -E "AudioResourceManager|🔍.*请求麦克风"
```

期望看到：
```
🔍 [WAKE_SERVICE] 请求麦克风 - 当前状态: IDLE, 持有者: NONE, TTS: false
✅ [WAKE_SERVICE] 成功获取麦克风资源
```

如果看到拒绝：
```
❌ [WAKE_SERVICE] 请求麦克风被拒绝：TTS正在播放
```
→ 说明TTS状态管理有问题

### 2. 检查模型加载状态

查找日志关键字：
```bash
adb logcat | grep -E "Wake device state|WakeState|模型加载"
```

**用户当前配置**:
- 默认使用: `SherpaOnnxWakeDevice`
- 模型路径检查:
  ```bash
  adb shell ls -la /data/data/包名/files/sherpa_onnx_kws/
  ```

期望看到的文件：
- `encoder-epoch-12-avg-2-chunk-16-left-64.onnx`
- `decoder-epoch-12-avg-2-chunk-16-left-64.onnx`
- `joiner-epoch-12-avg-2-chunk-16-left-64.onnx`
- `keywords.txt`
- `tokens.txt`

如果模型文件缺失或加载失败：
```
❌ 模型加载失败: <错误信息>
```
→ 模型问题会导致状态为 `WakeState.ErrorLoading`
→ 代码第174行会检测到并停止监听

### 3. 检查权限状态

```bash
adb shell dumpsys package 包名 | grep -A 5 "runtime permissions"
```

期望看到：
```
android.permission.RECORD_AUDIO: granted=true
```

### 4. 检查是否有其他应用占用麦克风

```bash
adb shell dumpsys audio | grep -i "record"
```

## 预防措施

### 1. 代码层面

- ✅ 所有 `requestMicrophone()` 调用都必须检查返回值
- ✅ 使用 `canRecord()` 快速检查TTS状态
- ✅ 在循环中定期检查资源状态

### 2. 测试场景

需要测试的竞态条件：
1. **TTS播放期间尝试启动唤醒服务**
2. **唤醒词触发后TTS立即开始播放**
3. **ASR录音中检测到唤醒词**
4. **多次快速唤醒触发**

### 3. 监控建议

在关键位置添加日志：
```kotlin
// 在AudioRecord初始化前
DebugLogger.logWakeWord(TAG, "准备初始化AudioRecord - 资源状态: ${AudioResourceManager.getDebugInfo()}")

// 在AudioRecord初始化后
DebugLogger.logWakeWord(TAG, "AudioRecord状态: state=${ar.state}, recordingState=${ar.recordingState}")
```

## 相关代码位置

- **主要修复**: `app/src/main/kotlin/com/ai/voice/io/wake/WakeService.kt:372-381`
- **资源管理**: `app/src/main/kotlin/com/ai/voice/io/AudioResourceManager.kt:83-120`
- **错误位置**: `app/src/main/kotlin/com/ai/voice/io/wake/WakeService.kt:286`

## 总结

### 主要问题
**资源请求返回值未检查** - 导致在资源不可用时仍然尝试初始化AudioRecord

### 修复方案
1. ✅ 添加 `granted` 检查，资源请求失败时立即返回
2. ✅ 增强日志输出，便于诊断
3. ⏳ 需要在实际运行中验证效果

### 建议下一步
1. 重新编译并运行应用
2. 观察日志中的 `🔍 [WAKE_SERVICE] 请求麦克风` 消息
3. 检查是否还有 `AudioRecord not initialized` 错误
4. 如果仍有问题，查看资源管理器的状态日志

---

**修复时间**: 2025-11-02  
**修复人**: AI Assistant  
**验证状态**: 待用户确认





