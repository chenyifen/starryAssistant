# TTS集成验证报告

## 修改内容

### 1. SherpaOnnxTtsSpeechDevice.kt

#### 1.1 导入AudioResourceManager
```kotlin
import org.stypox.dicio.io.AudioResourceManager
```

#### 1.2 playAudio()开始时通知TTS播放开始
```kotlin
private fun playAudio(audio: GeneratedAudio) {
    // 通知AudioResourceManager: TTS播放开始
    scope.launch {
        try {
            AudioResourceManager.notifyTtsStart()
            Log.d(TAG, "  📢 已通知AudioResourceManager: TTS播放开始")
        } catch (e: Exception) {
            Log.e(TAG, "  ❌ 通知TTS开始失败", e)
        }
    }
    
    // ... 播放音频代码
}
```

**关键点**：
- ✅ 在audioTrack.play()之前通知
- ✅ 使用协程调用suspend函数
- ✅ 捕获异常避免崩溃
- ✅ 添加日志便于调试

#### 1.3 onSpeakingFinished()中通知TTS播放结束
```kotlin
private fun onSpeakingFinished() {
    isSpeakingFlag.set(false)
    audioTrack?.stop()
    audioTrack?.release()
    audioTrack = null
    
    // 通知AudioResourceManager: TTS播放结束
    scope.launch {
        try {
            AudioResourceManager.notifyTtsEnd()
            Log.d(TAG, "  📢 已通知AudioResourceManager: TTS播放结束")
        } catch (e: Exception) {
            Log.e(TAG, "  ❌ 通知TTS结束失败", e)
        }
    }
    
    // 执行完成回调...
}
```

**关键点**：
- ✅ 在AudioTrack停止后通知
- ✅ 在执行回调之前通知（确保ASR可以恢复）
- ✅ 异常捕获保护

#### 1.4 stopSpeaking()中通知TTS停止
```kotlin
override fun stopSpeaking() {
    val wasSpeaking = isSpeakingFlag.getAndSet(false)
    
    currentJob?.cancel()
    currentJob = null
    
    audioTrack?.stop()
    audioTrack?.release()
    audioTrack = null
    
    // 如果之前正在播放，通知AudioResourceManager
    if (wasSpeaking) {
        scope.launch {
            try {
                AudioResourceManager.notifyTtsEnd()
                Log.d(TAG, "  📢 已通知AudioResourceManager: TTS停止")
            } catch (e: Exception) {
                Log.e(TAG, "  ❌ 通知TTS停止失败", e)
            }
        }
    }
    
    Log.d(TAG, "  ⏹️ TTS播放停止")
}
```

**关键点**：
- ✅ 只在实际播放时通知结束
- ✅ 使用getAndSet原子操作判断状态
- ✅ 处理主动停止的场景

## 流程验证

### 2.1 正常播放流程
```
1. speak("你好") 被调用
   ↓
2. 生成音频成功
   ↓
3. playAudio() 被调用
   ↓
4. AudioResourceManager.notifyTtsStart()  🔔
   ↓
5. audioTrack.play() 开始播放
   ↓
6. 播放完成触发 onMarkerReached()
   ↓
7. onSpeakingFinished() 被调用
   ↓
8. AudioResourceManager.notifyTtsEnd()  🔔
   ↓
9. 执行完成回调

预期结果:
✅ ASR在步骤4后暂停录音
✅ ASR在步骤8后恢复录音
✅ TTS声音不会被ASR识别
```

### 2.2 主动停止流程
```
1. speak("很长的一段话...") 开始播放
   ↓
2. AudioResourceManager.notifyTtsStart()  🔔
   ↓
3. 用户点击停止或新的speak()到来
   ↓
4. stopSpeaking() 被调用
   ↓
5. wasSpeaking=true
   ↓
6. AudioResourceManager.notifyTtsEnd()  🔔

预期结果:
✅ 中断时也会通知TTS结束
✅ ASR可以正确恢复
```

### 2.3 异常流程
```
场景1: 音频生成失败
speak() → 生成失败 → onSpeakingFinished()
预期: 不会调用notifyTtsStart()，因为没有进入playAudio()

场景2: 播放异常
playAudio() → notifyTtsStart() → 异常 → onSpeakingFinished() → notifyTtsEnd()
预期: ✅ Start和End成对出现

场景3: 快速连续speak()
speak("A") → notifyTtsStart() → speak("B") → stopSpeaking() → notifyTtsEnd() → speak("B")的notifyTtsStart()
预期: ✅ 旧的TTS被正确终止，新的TTS开始
```

## 边界情况验证

| 场景 | 处理方式 | 验证结果 |
|------|---------|---------|
| TTS未初始化调用speak() | 直接返回，不通知AudioResourceManager | ✅ 正确 |
| 播放过程中cleanup() | stopSpeaking()会被调用，通知结束 | ✅ 正确 |
| 连续快速speak()多次 | 每次都会stopSpeaking()旧的 | ✅ 正确 |
| notifyTts异常 | 捕获异常，不影响TTS播放 | ✅ 正确 |
| 协程被取消 | catch处理，执行onSpeakingFinished() | ✅ 正确 |

## 上下游完整性检查

### 3.1 上游：谁调用TTS
```
SkillEvaluator
  ↓
SpeechOutputDeviceWrapper
  ↓
SherpaOnnxTtsSpeechDevice.speak()
  ↓
AudioResourceManager.notifyTtsStart()
```

### 3.2 下游：TTS通知影响谁
```
AudioResourceManager.notifyTtsStart()
  ↓
通知所有注册的TTS监听器
  ↓
ASR设备 (SenseVoiceInputDevice, SherpaOnnxSimulateInputDevice)
  ↓
暂停录音（停止读取AudioRecord但不释放）
  ↓
避免TTS回声问题 ✅

AudioResourceManager.notifyTtsEnd()
  ↓
通知所有TTS监听器
  ↓
ASR设备恢复录音
```

### 3.3 WakeService的处理
```
AudioResourceManager.notifyTtsStart()
  ↓
WakeService监听器被触发
  ↓
WakeService暂停唤醒词检测
  ↓
TTS播放时不会误触发唤醒词 ✅
```

## 异常处理检查清单

- [x] notifyTtsStart()异常不影响播放
- [x] notifyTtsEnd()异常不影响清理
- [x] 协程取消正确处理
- [x] AudioTrack异常仍能通知结束
- [x] 快速连续调用不会状态混乱
- [x] cleanup()时确保通知结束

## 日志输出验证

预期日志序列（正常播放）：
```
🗣️ 开始TTS合成: 'xxx'
✅ 音频生成成功，样本数: xxx
📢 已通知AudioResourceManager: TTS播放开始
🎵 音频播放中，写入字节数: xxx
✅ 音频播放完成
📢 已通知AudioResourceManager: TTS播放结束
🏁 TTS播放完成
```

预期日志序列（主动停止）：
```
🗣️ 开始TTS合成: 'xxx'
⏹️ TTS播放停止
📢 已通知AudioResourceManager: TTS停止
```

## 待实际测试验证项

这些需要在设备上运行后验证：

- [ ] TTS播放时ASR确实停止录音
- [ ] TTS结束后ASR确实恢复录音
- [ ] TTS声音不会被ASR识别（回声消除）
- [ ] 快速连续播放TTS的稳定性
- [ ] 与WakeService的协作正确性
- [ ] 用户主动中断TTS的响应性

## 结论

✅ **TTS设备集成完成，代码审查通过**

关键成果：
1. 在播放开始前通知AudioResourceManager
2. 在播放结束和停止时通知
3. 完善的异常处理
4. 清晰的日志输出
5. 支持正常播放和主动停止两种场景

准备进行下一步：集成ASR设备。

