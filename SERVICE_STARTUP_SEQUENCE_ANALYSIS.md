# 服务启动顺序和执行时序分析

## 1. 整体架构

```
FloatingLauncherActivity (应用入口)
    ↓
    ├─→ WakeService (业务层 - 唤醒词检测)
    └─→ EnhancedFloatingWindowService (UI层 - 悬浮球显示)
    
通信机制：
- WakeService → EnhancedFloatingWindowService: WakeWordCallbackManager
- 状态同步: VoiceAssistantStateProvider
- 音频资源管理: AudioResourceManager
```

## 2. 启动时序图

### 2.1 应用启动阶段

```
时间轴 →

[FloatingLauncherActivity.onCreate]
    │
    ├─→ checkNextPermission()
    │   │
    │   ├─→ CHECK_BASIC: 检查基础权限（录音、通知）
    │   │   └─→ 如果缺少，请求权限 → onRequestPermissionsResult()
    │   │
    │   ├─→ CHECK_STORAGE: 检查存储权限（Hyundai IT版本跳过）
    │   │   └─→ 直接通过
    │   │
    │   ├─→ CHECK_OVERLAY: 检查悬浮窗权限
    │   │   └─→ 如果缺少，打开设置页面 → handleOverlayPermissionResult()
    │   │
    │   └─→ START_SERVICE: 所有权限具备
    │
    └─→ startServiceAndFinish()
        │
        ├─→ [1] WakeService.start(context)
        │   │
        │   └─→ Intent → startForegroundService()
        │       │
        │       └─→ [WakeService.onCreate]
        │           │
        │           ├─→ 创建前台通知
        │           ├─→ 初始化AudioManager
        │           ├─→ 监听唤醒词类型变化
        │           ├─→ 清理旧音频调试文件
        │           └─→ scheduleStateListenerSetup() [延迟1秒]
        │               └─→ setupStateListener() [监听VoiceAssistantStateProvider]
        │
        ├─→ Thread.sleep(500) [等待WakeService启动]
        │
        └─→ [2] EnhancedFloatingWindowService.start(context)
                │
                └─→ Intent → startForegroundService()
                    │
                    └─→ [EnhancedFloatingWindowService.onCreate]
                        │
                        ├─→ 创建前台通知
                        ├─→ WakeWordCallbackManager.registerCallback(this)
                        ├─→ 初始化生命周期
                        ├─→ 检查悬浮窗权限
                        ├─→ initializeComponents()
                        │   ├─→ 创建AssistantUIController
                        │   └─→ 创建DraggableFloatingOrb
                        ├─→ observeSettings()
                        └─→ registerAutoTestReceiver()
```

### 2.2 WakeService启动监听流程

```
[WakeService.onStartCommand]
    │
    ├─→ 创建前台通知
    ├─→ 检查是否已在监听 → 如果是，直接返回
    ├─→ 检查录音权限 → 如果缺少，返回（等待权限恢复）
    │
    └─→ startPersistentListening()
        │
        ├─→ listening.set(true)
        ├─→ WakeWordCallbackManager.notifyListeningStarted()
        ├─→ AutoTestLogger.logWakeListeningStarted()
        ├─→ 主动触发模型加载（如果未加载）
        │
        └─→ scope.launch { 持续监听循环 }
            │
            └─→ while (listening.get()) {
                │
                ├─→ 检查模型状态
                ├─→ listenForWakeWord()
                │   │
                │   ├─→ [请求麦克风资源]
                │   │   AudioResourceManager.requestMicrophone(WAKE_SERVICE)
                │   │   └─→ 状态: IDLE → WAKE_LISTENING
                │   │
                │   ├─→ [等待模型加载] (最多30秒)
                │   │
                │   ├─→ [创建AudioRecord]
                │   │   ├─→ requestAudioFocus() [Android 15+]
                │   │   ├─→ createOptimalAudioRecord()
                │   │   └─→ 测试AudioRecord
                │   │
                │   ├─→ [开始录音循环]
                │   │   ar.startRecording()
                │   │   │
                │   │   └─→ while (listening.get()) {
                │   │       │
                │   │       ├─→ 检查canRecord() [TTS播放时暂停]
                │   │       ├─→ 检查audioRecordPaused [ASR使用音频时暂停]
                │   │       ├─→ ar.read(audio) [读取音频数据]
                │   │       └─→ wakeDevice.processFrame(audio) [检测唤醒词]
                │   │           │
                │   │           └─→ if (wakeWordDetected) {
                │   │                   onWakeWordDetected()
                │   │               }
                │   │   }
                │   │
                │   └─→ [清理资源]
                │       ├─→ ar.stop()
                │       ├─→ ar.release()
                │       ├─→ releaseAudioFocus()
                │       └─→ AudioResourceManager.releaseMicrophone(WAKE_SERVICE)
                │
                └─→ 如果正常退出，等待1秒后重启
            }
```

### 2.3 唤醒词检测触发流程

```
[WakeService.listenForWakeWord循环]
    │
    └─→ wakeDevice.processFrame(audio)
        │
        └─→ if (wakeWordDetected) {
                │
                └─→ [WakeService.onWakeWordDetected]
                    │
                    ├─→ 取消之前的恢复任务
                    ├─→ 重置audioRecordPaused标志
                    │
                    ├─→ [通知所有回调]
                    │   WakeWordCallbackManager.notifyWakeWordDetected()
                    │   │
                    │   └─→ [EnhancedFloatingWindowService.onWakeWordDetected]
                    │       │
                    │       └─→ handleVoiceWakeUp()
                    │           │
                    │           ├─→ floatingOrb?.show()
                    │           ├─→ floatingOrb?.getAnimationStateManager()?.setIdle()
                    │           └─→ startVoiceRecognition()
                    │               │
                    │               └─→ sttInputDeviceWrapper.tryLoad(skillEvaluator::processInputEvent)
                    │
                    ├─→ [同步释放麦克风资源]
                    │   runBlocking {
                    │       │
                    │       ├─→ pauseAudioRecordForASR()
                    │       │   ├─→ audioRecordPaused.set(true)
                    │       │   └─→ ar.stop() [同步停止]
                    │       │
                    │       ├─→ delay(200) [等待AudioRecord完全停止]
                    │       │
                    │       └─→ AudioResourceManager.releaseMicrophone(WAKE_SERVICE)
                    │           └─→ 状态: WAKE_LISTENING → IDLE
                    │   }
                    │
                    ├─→ [启动ASR]
                    │   sttInputDevice.tryLoad(skillEvaluator::processInputEvent)
                    │   │
                    │   └─→ [SenseVoiceInputDevice.startListening]
                    │       │
                    │       ├─→ AudioResourceManager.requestMicrophone(ASR_DEVICE)
                    │       │   └─→ 状态: IDLE → ASR_RECORDING
                    │       │
                    │       ├─→ requestAudioFocus()
                    │       ├─→ 创建AudioRecord
                    │       ├─→ 启动录制协程
                    │       └─→ 启动VAD处理协程
                    │
                    └─→ [延迟恢复WakeService]
                        handler.postDelayed({
                            resumeAudioRecordAfterASR()
                        }, 10000ms)
```

### 2.4 ASR录制和处理流程

```
[SenseVoiceInputDevice.startListening]
    │
    ├─→ [录制协程] recordAudioData()
    │   │
    │   └─→ while (isRecording.get()) {
    │       │
    │       ├─→ 检查canRecord() [TTS播放时暂停]
    │       ├─→ ar.read(buffer) [读取音频数据]
    │       └─→ samplesChannel.send(samples) [发送到处理通道]
    │   }
    │
    └─→ [VAD处理协程] processAudioForRecognition()
        │
        └─→ while (isListening.get()) {
            │
            ├─→ 从samplesChannel接收音频数据
            ├─→ audioBuffer.addAudioChunk(samples)
            ├─→ detectSpeech(samples) [VAD检测]
            │
            ├─→ if (isSpeech) {
            │       │
            │       ├─→ 标记speechDetected = true
            │       ├─→ 记录speechStartTime
            │       └─→ 如果满足条件，执行Partial识别
            │           └─→ performPartialRecognition()
            │               └─→ skillEvaluator.processInputEvent(InputEvent.Partial)
            │   }
            │
            ├─→ if (!isSpeech && speechDetected) {
            │       │
            │       ├─→ 检查是否满足提前结束条件
            │       ├─→ 检查静音超时
            │       └─→ if (静音超时 && partialText不为空) {
            │               stopListeningAndProcess()
            │           }
            │   }
            │
            └─→ if (达到最大录制时间) {
                    stopListeningAndProcess()
                }
        }

[stopListeningAndProcess]
    │
    ├─→ isListening.set(false)
    ├─→ samplesChannel.close()
    ├─→ vadJob?.cancel() && vadJob?.join()
    ├─→ stopRecording() [取消录制协程]
    ├─→ recordingJob?.join() [等待录制协程退出]
    ├─→ cleanupAudioRecord() [清理AudioRecord资源]
    │   ├─→ ar.stop()
    │   ├─→ ar.release()
    │   ├─→ releaseAudioFocus()
    │   └─→ audioBuffer.clear()
    │
    └─→ performFinalRecognition()
        │
        ├─→ audioBuffer.getAccumulatedAudio()
        ├─→ recognizer.recognize(audioData)
        └─→ skillEvaluator.processInputEvent(InputEvent.Final)
            │
            └─→ [SkillEvaluator处理]
                │
                ├─→ 评估技能
                ├─→ 执行技能
                └─→ 触发TTS播放
                    │
                    └─→ AudioResourceManager.notifyTtsStart()
                        └─→ 状态: ASR_RECORDING → TTS_PLAYING
```

### 2.5 ASR结束后恢复WakeService流程

```
[VoiceAssistantStateProvider检测到状态变化]
    │
    └─→ 状态: ASR_RECORDING → IDLE
        │
        └─→ [WakeService.stateListener]
            │
            └─→ if (state.uiState == IDLE && !listening.get()) {
                    │
                    ├─→ AudioResourceManager.requestMicrophone(WAKE_SERVICE)
                    │   └─→ 状态: IDLE → WAKE_LISTENING
                    │
                    └─→ startPersistentListening()
                        └─→ 重新开始唤醒词监听循环
                }
```

## 3. 关键时序点

### 3.1 启动时序

| 时间点 | 操作 | 说明 |
|--------|------|------|
| T0 | FloatingLauncherActivity.onCreate | 应用入口启动 |
| T1 | 权限检查完成 | 所有权限具备 |
| T2 | WakeService.start() | 启动WakeService |
| T2+500ms | EnhancedFloatingWindowService.start() | 启动UI服务 |
| T2+1000ms | WakeService.setupStateListener() | 设置状态监听器 |
| T2+1500ms | FloatingLauncherActivity.finish() | 关闭启动Activity |

### 3.2 唤醒词检测时序

| 时间点 | 操作 | 说明 |
|--------|------|------|
| T0 | WakeService开始监听 | 创建AudioRecord，开始录音循环 |
| T1 | 检测到唤醒词 | wakeDevice.processFrame()返回true |
| T1+0ms | onWakeWordDetected() | 通知回调 |
| T1+0ms | EnhancedFloatingWindowService.onWakeWordDetected() | UI层响应 |
| T1+0ms | pauseAudioRecordForASR() | 同步停止WakeService的AudioRecord |
| T1+200ms | releaseMicrophone(WAKE_SERVICE) | 释放WakeService的麦克风资源 |
| T1+200ms | ASR请求麦克风资源 | SenseVoiceInputDevice.startListening() |
| T1+500ms | ASR开始录音 | ASR的AudioRecord开始工作 |

### 3.3 ASR处理时序

| 时间点 | 操作 | 说明 |
|--------|------|------|
| T0 | ASR开始录音 | AudioRecord开始读取数据 |
| T1 | 检测到语音 | VAD检测到语音开始 |
| T1+500ms | Partial识别 | 首次快速响应（0.5秒） |
| T2 | 语音结束 | VAD检测到静音 |
| T2+700ms | 静音超时 | 触发最终识别 |
| T3 | stopListeningAndProcess() | 停止录音并处理 |
| T3+100ms | performFinalRecognition() | 执行最终识别 |
| T4 | 技能评估完成 | 触发TTS播放 |
| T4+0ms | notifyTtsStart() | 音频状态转为TTS_PLAYING |

### 3.4 恢复WakeService时序

| 时间点 | 操作 | 说明 |
|--------|------|------|
| T0 | ASR释放麦克风 | performFinalRecognition()完成 |
| T0+0ms | 状态变为IDLE | VoiceAssistantStateProvider更新状态 |
| T0+0ms | WakeService.stateListener触发 | 检测到IDLE状态 |
| T0+100ms | requestMicrophone(WAKE_SERVICE) | WakeService重新请求麦克风 |
| T0+200ms | startPersistentListening() | 重新开始监听循环 |

## 4. 音频资源管理流程

### 4.1 资源持有者切换

```
初始状态: IDLE (AudioOwner.NONE)

WakeService启动监听:
    IDLE → WAKE_LISTENING (AudioOwner.WAKE_SERVICE)

检测到唤醒词:
    WAKE_LISTENING → IDLE (释放WAKE_SERVICE)
    IDLE → ASR_RECORDING (AudioOwner.ASR_DEVICE)

ASR处理完成:
    ASR_RECORDING → TTS_PLAYING (TTS播放，持有者仍为ASR_DEVICE)
    TTS_PLAYING → IDLE (TTS结束，释放ASR_DEVICE)

WakeService恢复监听:
    IDLE → WAKE_LISTENING (AudioOwner.WAKE_SERVICE)
```

### 4.2 资源切换同步机制

```
WakeService → ASR:
    1. pauseAudioRecordForASR() [同步停止AudioRecord]
    2. delay(200ms) [等待AudioRecord完全停止]
    3. releaseMicrophone(WAKE_SERVICE) [释放资源]
    4. ASR请求资源 [requestMicrophone(ASR_DEVICE)]

ASR → TTS:
    1. notifyTtsStart() [设置TTS播放标志]
    2. TTS播放期间，canRecord()返回false
    3. ASR和WakeService都会暂停录音

TTS → WakeService:
    1. notifyTtsEnd() [清除TTS播放标志]
    2. 如果WakeService持有资源，恢复WAKE_LISTENING状态
    3. 如果无持有者，保持IDLE状态
```

## 5. 状态同步机制

### 5.1 VoiceAssistantStateProvider状态流

```
底层服务状态变化:
    ├─→ STT状态变化 (SttState)
    ├─→ InputEvent (Partial/Final/Error/None)
    └─→ SkillEvaluator状态 (InteractionLog)

    ↓

VoiceAssistantStateProvider.updateState()
    │
    ├─→ 更新内部状态 (_currentState)
    ├─→ 通知所有监听器
    │   ├─→ WakeService.stateListener
    │   └─→ DraggableFloatingOrb (通过StateProvider监听)
    │
    └─→ UI更新
```

### 5.2 关键状态转换

```
IDLE
    ↓ [唤醒词检测]
LISTENING
    ↓ [ASR处理]
THINKING
    ↓ [技能评估完成]
SPEAKING
    ↓ [TTS播放完成]
IDLE
```

## 6. 潜在问题和优化建议

### 6.1 时序问题

1. **WakeService启动延迟**: scheduleStateListenerSetup()延迟1秒，可能导致ASR快速完成时状态监听未设置
   - **建议**: 减少延迟或使用更可靠的初始化检查

2. **资源切换延迟**: delay(200ms)可能导致音频数据丢失
   - **建议**: 使用更精确的同步机制，确保AudioRecord完全停止

3. **ASR恢复WakeService延迟**: resumeAudioRecordAfterASR()延迟10秒
   - **建议**: 通过状态监听器立即恢复，而不是固定延迟

### 6.2 资源竞争问题

1. **TTS播放期间**: WakeService和ASR都会暂停，但状态管理可能不一致
   - **建议**: 统一通过AudioResourceManager管理

2. **快速连续唤醒**: 可能导致资源切换失败
   - **建议**: 添加资源切换锁或队列机制

### 6.3 状态同步问题

1. **DraggableFloatingOrb监听**: 如果同时有多个监听器，可能导致状态更新冲突
   - **建议**: 统一使用一个监听器，或使用观察者模式

2. **状态更新时机**: StateProvider的状态更新可能与实际音频状态不同步
   - **建议**: 确保状态更新与资源管理同步

## 7. 总结

当前架构采用分层设计：
- **FloatingLauncherActivity**: 应用入口，负责权限检查和服务启动
- **WakeService**: 业务层，负责唤醒词检测和音频资源管理
- **EnhancedFloatingWindowService**: UI层，负责悬浮球显示和用户交互

通信机制：
- **WakeWordCallbackManager**: WakeService → EnhancedFloatingWindowService的直接通信
- **VoiceAssistantStateProvider**: 统一的状态管理，所有组件都可以监听
- **AudioResourceManager**: 音频资源的统一管理，确保资源正确切换

时序关键点：
1. WakeService先启动，确保唤醒词检测可用
2. EnhancedFloatingWindowService后启动，注册回调
3. 唤醒词检测后，同步释放资源，确保ASR能获取麦克风
4. ASR结束后，通过状态监听器恢复WakeService

