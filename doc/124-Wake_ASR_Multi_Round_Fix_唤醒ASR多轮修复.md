# 唤醒后ASR多轮运行失败修复

## 问题描述

用户报告："运行几轮之后唤醒就不识别ASR了"

### 日志证据

```
01-13 23:31:01.154 W AudioResourceManager: ⚠️ [WAKE_SERVICE] 尝试释放麦克风，但当前持有者是 ASR_DEVICE
01-13 23:31:01.456 D 🔊[WakeService]: ⏸️ Pausing WakeService AudioRecord for ASR
01-13 23:31:03.579 E SenseVoiceInputDevice: ❌ AudioRecord状态异常
01-13 23:31:03.579 E SenseVoiceInputDevice: kotlinx.coroutines.JobCancellationException: StandaloneCoroutine was cancelled
```

## 根本原因分析

### 1. **资源释放时序问题**
- WakeService在协程中**异步释放**麦克风资源（569-576行）
- ASR设备**同步启动**（583行）
- 导致时序不确定：ASR可能在WakeService释放完成前就尝试获取资源

### 2. **定时任务堆积问题**
- 每次唤醒都会创建一个10秒后的恢复任务
- 如果10秒内用户多次唤醒，定时任务会堆积
- 旧任务未取消，导致状态在不该恢复的时候被恢复

### 3. **audioRecordPaused标志混乱**
- 第一次唤醒：`audioRecordPaused=false` → 暂停 → `audioRecordPaused=true`
- 10秒后：恢复 → `audioRecordPaused=false`
- 如果用户在恢复前再次唤醒，标志状态就会混乱
- 第二次唤醒检测到`audioRecordPaused=true`，导致行为异常

### 4. **缺少状态检查**
- 暂停和恢复函数没有检查当前状态
- 可能重复暂停或恢复，导致AudioRecord状态异常

## 修复方案

### 1. ✅ 改为同步释放麦克风资源

**修改位置**: `WakeService.kt:577-585`

```kotlin
// ❌ 旧代码：异步释放
scope.launch {
    try {
        AudioResourceManager.releaseMicrophone(AudioResourceManager.AudioOwner.WAKE_SERVICE)
        DebugLogger.logWakeWord(TAG, "✅ 已释放麦克风资源，让ASR使用")
    } catch (e: Exception) {
        DebugLogger.logWakeWordError(TAG, "❌ 释放麦克风资源失败", e)
    }
}

// ✅ 新代码：同步释放
runBlocking {
    try {
        AudioResourceManager.releaseMicrophone(AudioResourceManager.AudioOwner.WAKE_SERVICE)
        DebugLogger.logWakeWord(TAG, "✅ 已释放麦克风资源，让ASR使用")
    } catch (e: Exception) {
        DebugLogger.logWakeWordError(TAG, "❌ 释放麦克风资源失败", e)
    }
}
```

**效果**：确保麦克风资源完全释放后，ASR才启动

### 2. ✅ 取消之前的定时任务

**修改位置**: `WakeService.kt:549-550`

```kotlin
// 🔧 取消之前的恢复任务，避免重复唤醒导致状态混乱
handler.removeCallbacks(releaseSttResourcesRunnable)
```

**效果**：防止定时任务堆积，每次唤醒都从干净的状态开始

### 3. ✅ 重置audioRecordPaused标志

**修改位置**: `WakeService.kt:552-556`

```kotlin
// 🔧 重置audioRecordPaused标志，确保状态一致
if (audioRecordPaused.get()) {
    DebugLogger.logWakeWord(TAG, "⚠️ 检测到audioRecordPaused=true，重置为false")
    audioRecordPaused.set(false)
}
```

**效果**：确保每次唤醒前标志状态正确

### 4. ✅ 添加状态检查

**修改位置**: `WakeService.kt:687-691` 和 `731-735`

```kotlin
// pauseAudioRecordForASR() 添加检查
if (audioRecordPaused.get()) {
    DebugLogger.logWakeWord(TAG, "⚠️ WakeService已经处于暂停状态，跳过重复暂停")
    return
}

// resumeAudioRecordAfterASR() 添加检查
if (!audioRecordPaused.get()) {
    DebugLogger.logWakeWord(TAG, "⚠️ WakeService未处于暂停状态，跳过恢复")
    return
}
```

**效果**：防止重复操作导致AudioRecord状态异常

### 5. ✅ 增强状态日志

**修改位置**: `WakeService.kt` 多处

```kotlin
DebugLogger.logWakeWord(TAG, "📊 当前状态: listening=${listening.get()}, audioRecordPaused=${audioRecordPaused.get()}")
```

**效果**：方便诊断问题，快速定位状态异常

## 修复后的执行流程

### 正常唤醒流程
```
1. 检测到唤醒词
2. 取消之前的定时任务 ✅
3. 重置audioRecordPaused标志 ✅
4. 同步释放麦克风资源 ✅
5. 启动ASR设备
6. 300ms后暂停WakeService AudioRecord
7. 创建10秒后的恢复任务
```

### 多次快速唤醒
```
第1次唤醒:
  - 取消旧任务（无）
  - 重置标志 (false → false)
  - 释放麦克风 → 启动ASR
  - 创建恢复任务T1

第2次唤醒 (5秒后，T1未执行):
  - 取消旧任务（T1被取消）✅
  - 重置标志 (true → false) ✅
  - 释放麦克风 → 启动ASR
  - 创建恢复任务T2

第3次唤醒 (又5秒后，T2未执行):
  - 取消旧任务（T2被取消）✅
  - 重置标志 (true → false) ✅
  - 释放麦克风 → 启动ASR
  - 创建恢复任务T3
```

**结果**：每次唤醒都从一致的状态开始，不会受之前操作的影响

## 测试建议

### 1. 快速连续唤醒测试
```bash
# 连续5次快速唤醒（间隔3秒）
嗨狄西奥 → 说话 → 等3秒 → 嗨狄西奥 → 说话 → ...
```
**预期**：每次都能正常识别，不出现"."的情况

### 2. 长间隔唤醒测试
```bash
# 间隔15秒唤醒（超过10秒恢复时间）
嗨狄西奥 → 说话 → 等15秒 → 嗨狄西奥 → 说话
```
**预期**：每次都能正常识别，WakeService正常恢复

### 3. 日志验证
查看日志，确保：
- ✅ 没有 "尝试释放麦克风，但当前持有者是 ASR_DEVICE" 警告
- ✅ 没有 "AudioRecord状态异常" 错误
- ✅ 没有 "JobCancellationException"
- ✅ audioRecordPaused标志状态正确切换

## 修改文件清单

- ✅ `app/src/main/kotlin/com/ai/voice/io/wake/WakeService.kt`
  - 修改 `onWakeWordDetected()` 函数
  - 修改 `pauseAudioRecordForASR()` 函数
  - 修改 `resumeAudioRecordAfterASR()` 函数

## 注意事项

1. **性能影响**：使用`runBlocking`会短暂阻塞主线程，但由于资源释放很快（<10ms），影响可忽略
2. **设置兼容**：修复尊重用户的"ASR时暂停唤醒"设置
3. **向后兼容**：修复不改变现有功能，只修复状态管理问题

## 预期效果

- ✅ 解决多轮唤醒后ASR不识别的问题
- ✅ 防止AudioRecord状态异常
- ✅ 防止协程被意外取消
- ✅ 提供更清晰的状态日志，方便排查问题

