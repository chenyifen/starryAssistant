# 技能匹配后设置IDLE状态时停止ASR并启动唤醒

## 问题描述
当技能匹配后设置UI状态为IDLE时，系统应该：
1. **停止ASR（Automatic Speech Recognition）** - 停止语音识别监听
2. **启动唤醒服务（WakeService）** - 恢复唤醒词检测，等待下一次唤醒

## 修改内容

### 文件：`VoiceAssistantStateProvider.kt`

#### 1. TTS播放完成回调（`setupTTSCompletionCallback`）

**位置**：第618-682行

**场景**：技能执行完成，TTS播放结束后

**修改逻辑**：
```kotlin
private fun setupTTSCompletionCallback() {
    speechOutputDeviceWrapper.runWhenFinishedSpeaking {
        scope.launch {
            delay(1000)
            if (AsrHandler.isStarted()) {
                // ASR正在运行，保持LISTENING状态
                updateState(uiState = VoiceAssistantUIState.LISTENING, ttsText = "", displayText = "LISTENING")
            } else {
                // 🔥 技能匹配完成，执行完整的状态恢复流程
                
                // 1. 停止ASR（确保不再监听）
                try {
                    val context = skillContext.android
                    if (AsrHandler.isStarted()) {
                        DebugLogger.logUI(TAG, "🛑 停止ASR监听")
                        AsrHandler.stop(context)
                    }
                } catch (e: Exception) {
                    DebugLogger.logUI(TAG, "❌ 停止ASR失败: ${e.message}")
                }
                
                // 2. 设置UI状态为IDLE
                updateState(uiState = VoiceAssistantUIState.IDLE, ttsText = "", displayText = "")
                
                // 3. 启动唤醒服务（恢复唤醒词检测）
                try {
                    val context = skillContext.android
                    if (!com.ai.voice.io.wake.WakeService.isRunning()) {
                        DebugLogger.logUI(TAG, "▶️ 启动唤醒服务")
                        com.ai.voice.io.wake.WakeService.start(context)
                    } else {
                        DebugLogger.logUI(TAG, "✅ 唤醒服务已在运行，无需重新启动")
                    }
                } catch (e: Exception) {
                    DebugLogger.logUI(TAG, "❌ 启动唤醒服务失败: ${e.message}")
                }
            }
        }
    }
}
```

#### 2. 静音超时处理（`InputEvent.None`）

**位置**：第268-307行

**场景**：用户无语音输入，静音超时（10秒）

**修改逻辑**：
```kotlin
InputEvent.None -&gt; {
    DebugLogger.logUI(TAG, "🔇 No speech detected (10秒静音超时，停止监听)")
    
    // 清理ASR相关状态
    updateState(asrText = "")
    speechOutputDeviceWrapper.setAsrLocale(null)
    skillContext.asrLocale = null
    
    // 1. 停止ASR（确保不再监听）
    try {
        val context = skillContext.android
        if (AsrHandler.isStarted()) {
            DebugLogger.logUI(TAG, "🛑 静音超时，停止ASR监听")
            AsrHandler.stop(context)
        }
    } catch (e: Exception) {
        DebugLogger.logUI(TAG, "❌ 停止ASR失败: ${e.message}")
    }
    
    // 2. 设置UI状态为IDLE
    updateState(
        uiState = VoiceAssistantUIState.IDLE,
        displayText = "",
        ttsText = ""
    )
    
    // 3. 启动唤醒服务（恢复唤醒词检测）
    try {
        val context = skillContext.android
        if (!com.ai.voice.io.wake.WakeService.isRunning()) {
            DebugLogger.logUI(TAG, "▶️ 静音超时后启动唤醒服务")
            com.ai.voice.io.wake.WakeService.start(context)
        } else {
            DebugLogger.logUI(TAG, "✅ 唤醒服务已在运行")
        }
    } catch (e: Exception) {
        DebugLogger.logUI(TAG, "❌ 启动唤醒服务失败: ${e.message}")
    }
}
```

## 状态转换流程

### 完整的语音交互流程

```
1. IDLE (唤醒服务运行，等待唤醒词)
   ↓
2. 唤醒词检测到
   ↓
3. LISTENING (启动ASR，识别用户语音)
   ↓
4. 技能匹配成功
   ↓
5. SPEAKING (播放TTS回复)
   ↓
6. TTS播放完成
   ↓
7. IDLE (停止ASR，启动唤醒服务) ← 本次修改的重点
```

### 静音超时流程

```
1. LISTENING (ASR监听中)
   ↓
2. 10秒无语音输入
   ↓
3. InputEvent.None触发
   ↓
4. IDLE (停止ASR，启动唤醒服务) ← 本次修改的重点
```

## 修复的问题

### 修复前
- 技能执行后，只设置UI状态为IDLE
- ASR可能仍在运行（资源浪费）
- 唤醒服务可能未启动（无法再次唤醒）

### 修复后
- 技能执行后，完整的状态恢复：
  1. ✅ 停止ASR监听
  2. ✅ 设置UI状态为IDLE
  3. ✅ 启动唤醒服务
- 确保系统能正确恢复到待唤醒状态
- 资源得到正确释放和重新分配

## 日志输出

修改后会看到以下关键日志：

### 技能执行完成时
```
🏠 技能执行完成，停止ASR并启动唤醒服务
🛑 停止ASR监听
▶️ 启动唤醒服务
```

### 静音超时时
```
🔇 No speech detected (10秒静音超时，停止监听)
🛑 静音超时，停止ASR监听
▶️ 静音超时后启动唤醒服务
```

## 测试建议

1. **技能执行测试**
   - 唤醒 → 说出命令 → 等待技能执行和TTS播放
   - 确认TTS播放完成后能再次唤醒

2. **静音超时测试**
   - 唤醒 → 不说话等待10秒
   - 确认超时后能再次唤醒

3. **连续对话测试**
   - 唤醒 → 命令1 → TTS1 → 再次唤醒 → 命令2 → TTS2
   - 确认整个流程顺畅

## 相关组件

- **AsrHandler**: ASR语音识别处理器
- **WakeService**: 唤醒词检测服务
- **VoiceAssistantStateProvider**: 语音助手状态管理中心
- **SkillEvaluator**: 技能评估和执行器
- **SpeechOutputDeviceWrapper**: TTS播放管理器
