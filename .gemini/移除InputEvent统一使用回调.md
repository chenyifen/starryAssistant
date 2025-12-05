# 移除 InputEvent 机制，统一使用回调机制

## 📋 修改时间
2025-12-05 15:59

## 🎯 修改目标

**简化架构**：移除未完整实现的 InputEvent 事件流机制，统一使用现有的回调机制保证功能完整。

## ❌ 移除的原因

### 问题分析

1. **InputEvent 机制未完整实现**
   - ❌ AsrHandler 从未发送 `InputEvent.Partial`
   - ❌ AsrHandler 从未发送 `InputEvent.None`  
   - ✅ 只有 `InputEvent.Final` 通过 `finalResultCallback` 间接实现
   - 结果：大量的 InputEvent 处理代码实际上是"死代码"

2. **架构复杂性**
   - 两套机制并存：InputEvent 事件流 + 回调机制
   - VoiceAssistantStateProvider 监听 `skillEvaluator.inputEvents`
   - 但大部分事件（Partial, None）从未被触发
   - 增加了代码复杂度，降低了可维护性

3. **功能重复**
   - `InputEvent.Final` → 通过 `finalResultCallback` 实现
   - `InputEvent.None` → 通过 `silenceTimeoutCallback` 实现
   - `InputEvent.Partial` → 未实现，也不影响核心功能
   - 两套机制处理相同的事情，造成混乱

## ✅ 修改内容

### 1. 增强 silenceTimeoutCallback（完整状态恢复）

**文件**: `EnhancedFloatingWindowService.kt` 第 246-282 行

**修改前**:
```kotlin
AsrHandler.setSilenceTimeoutCallback {
    // 只更新悬浮球动画和UI状态
    floatingOrb?.getAnimationStateManager()?.setIdle()
    voiceAssistantStateProvider.updateUIState(VoiceAssistantUIState.IDLE)
    voiceAssistantStateProvider.setASRText("")
}
```

**修改后**:
```kotlin
AsrHandler.setSilenceTimeoutCallback {
    Log.d(TAG, "🔔 [SILENCE_TIMEOUT] AsrHandler静音超时回调触发")
    
    // 1. 停止ASR（确保不再监听）
    try {
        if (AsrHandler.isStarted()) {
            Log.d(TAG, "🛑 静音超时，停止ASR监听")
            AsrHandler.stop(this)
        }
    } catch (e: Exception) {
        Log.e(TAG, "❌ 停止ASR失败: ${e.message}", e)
    }
    
    // 2. 更新悬浮球动画
    floatingOrb?.getAnimationStateManager()?.setIdle()
    
    // 3. 更新UI状态为IDLE
    voiceAssistantStateProvider.updateUIState(VoiceAssistantUIState.IDLE)
    voiceAssistantStateProvider.setASRText("")
    voiceAssistantStateProvider.setTTSText("")
    
    // 4. 启动唤醒服务（恢复唤醒词检测）
    try {
        if (!WakeService.isRunning()) {
            Log.d(TAG, "▶️ 静音超时后启动唤醒服务")
            WakeService.start(this)
        } else {
            Log.d(TAG, "✅ 唤醒服务已在运行")
        }
    } catch (e: Exception) {
        Log.e(TAG, "❌ 启动唤醒服务失败: ${e.message}", e)
    }
    
    Log.d(TAG, "✅ [SILENCE_TIMEOUT] 完整状态恢复完成：ASR已停止，UI为IDLE，唤醒服务运行中")
}
```

**改进**:
- ✅ 完整的状态恢复：停止ASR → 更新UI → 启动Wake
- ✅ 错误处理更完善
- ✅ 日志更详细，便于调试

### 2. 删除 InputEvent 监听代码

**文件**: `VoiceAssistantStateProvider.kt` 第 180-192 行

**删除的代码**:
```kotlin
// 1. 监听SkillEvaluator的InputEvent来获取ASR实时文本
scope.launch {
    skillEvaluator.inputEvents.collect { inputEvent ->
        handleInputEvent(inputEvent)
    }
}
```

**保留的代码**:
```kotlin
// 监听SkillEvaluator的状态变化来获取技能结果
scope.launch {
    skillEvaluator.state.collect { interactionLog ->
        handleSkillEvaluatorState(interactionLog)
    }
}
```

### 3. 删除 handleInputEvent 方法

**文件**: `VoiceAssistantStateProvider.kt` 第 193-301 行（共109行）

**删除内容**:
- `InputEvent.Partial` 处理（从未被触发）
- `InputEvent.Final` 处理（已由 skillEvaluator 内部处理）
- `InputEvent.Error` 处理（从未被触发）
- `InputEvent.None` 处理（改用 silenceTimeoutCallback）

### 4. 删除 InputEvent import

**文件**: `VoiceAssistantStateProvider.kt` 第 12 行

```kotlin
import com.ai.voice.io.input.InputEvent  // ❌ 删除
```

### 5. 删除未使用的变量

**文件**: `VoiceAssistantStateProvider.kt` 第 122 行

```kotlin
private var lastAsrText = ""  // ❌ 删除（只在 Partial 处理中使用）
```

## 📊 修改统计

| 项目 | 数量 |
|------|------|
| 删除的代码行数 | ~110 行 |
| 删除的方法 | 1 个（`handleInputEvent`）|
| 删除的 import | 1 个 |
| 删除的变量 | 1 个 |
| 修改的方法 | 2 个（`observeServices`, `silenceTimeoutCallback`）|

## 🔄 当前的完整状态循环

### 正常流程

```
1. IDLE (WakeService 运行)
   ↓ 唤醒词检测
2. onWakeWordDetected()
   ↓ startVoiceRecognition()
3. AsrHandler.start()
   ↓ 设置 finalResultCallback + silenceTimeoutCallback
4. LISTENING (ASR 监听)
   ↓ 用户说话
5. VAD 检测语音结束
   ↓ finalResultCallback 触发
6. InputEvent.Final
   ↓ skillEvaluator.processInputEvent(Final)
7. 技能匹配和执行
   ↓
8. SPEAKING (TTS 播放)
   ↓ TTS 完成
9. setupTTSCompletionCallback()
   ↓ 停止ASR + 启动Wake
10. IDLE (循环)
```

### 静音超时流程

```
1. LISTENING (ASR 监听)
   ↓ 10秒无语音
2. AsrHandler 检测静音超时
   ↓ silenceTimeoutCallback 触发
3. 回调函数执行：
   - 停止 AsrHandler
   - 更新 UI 为 IDLE
   - 启动 WakeService
   ↓
4. IDLE (循环)
```

## ✅ 保留的回调机制

### AsrHandler 回调

1. **finalResultCallback** - Final 识别完成
   ```kotlin
   AsrHandler.setFinalResultCallback { finalText ->
       val finalEvent = InputEvent.Final(listOf(Pair(finalText, 1.0f)))
       skillEvaluator.processInputEvent(finalEvent)
   }
   ```

2. **silenceTimeoutCallback** - 静音超时
   ```kotlin
   AsrHandler.setSilenceTimeoutCallback {
       // 完整的状态恢复：停止ASR + 更新UI + 启动Wake
   }
   ```

### TTS 回调

```kotlin
setupTTSCompletionCallback() {
    speechOutputDeviceWrapper.runWhenFinishedSpeaking {
        // TTS 完成后的状态恢复
    }
}
```

### WakeWord 回调

```kotlin
override fun onWakeWordDetected(confidence: Float, wakeWord: String) {
    startVoiceRecognition()
}
```

## 🎯 架构优化效果

### 优化前

```
回调机制 + InputEvent 事件流（未完整实现）
  ↓
复杂、代码重复、有大量死代码
```

### 优化后

```
纯回调机制
  ↓
简单、清晰、所有代码都有用
```

## 🔍 功能完整性验证

| 功能 | 实现机制 | 状态 |
|------|---------|------|
| 唤醒检测 | WakeWordCallback | ✅ 正常 |
| ASR Final 识别 | finalResultCallback → InputEvent.Final | ✅ 正常 |
| 技能匹配执行 | SkillEvaluator.processInputEvent(Final) | ✅ 正常 |
| TTS 播放 | SpeechOutputDevice | ✅ 正常 |
| TTS 完成后恢复 | setupTTSCompletionCallback | ✅ 正常 |
| 静音超时恢复 | silenceTimeoutCallback | ✅ 正常（已增强）|
| 状态一致性 | 回调中统一管理 | ✅ 正常 |

## 📝 注意事项

1. **InputEvent.Final 仍在使用**
   - SkillEvaluator 内部仍然使用 InputEvent.Final
   - 但是通过 finalResultCallback 创建，不再通过 VoiceAssistantStateProvider 监听
   - 这是可以接受的，因为 SkillEvaluator 需要这个事件来触发技能匹配

2. **未来优化方向**
   - 可以考虑完全移除 InputEvent，直接在 SkillEvaluator 中处理文本输入
   - 但这需要重构 SkillEvaluator 的接口，工作量较大
   - 当前方案已经足够简洁和有效

3. **测试重点**
   - ✅ 静音超时后能否正常唤醒
   - ✅ 技能执行后能否正常唤醒
   - ✅ TTS 播放完成后能否正常唤醒
   - ✅ ASR 和 WakeService 的状态切换是否正常

## 🎉 总结

**移除了 ~110 行未使用的 InputEvent 处理代码**，**统一使用回调机制**，架构更简洁，功能保持完整。

**核心改进**:
- ✅ 删除了从未被触发的 InputEvent 处理代码
- ✅ 增强了 silenceTimeoutCallback，确保状态恢复完整
- ✅ 简化了代码结构，提高了可维护性
- ✅ 所有功能测试通过（唤醒、识别、执行、恢复）
