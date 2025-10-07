# 韩语TTS无声音问题调试指南

## 问题描述
UI显示韩语文本 "다시 말씀해 주시겠어요?" 但没有语音输出

## 可能的原因

### 1. WebSocket连接问题
**检查日志关键字:**
```
🔌 WebSocket连接状态
WebSocketTtsSpeechDevice
ConnectionState
```

**检查点:**
- WebSocket是否已连接
- TTS降级链是否跳过了WebSocket TTS
- 是否回退到了 AndroidTTS 或其他设备

### 2. TTS设备初始化问题
**检查日志关键字:**
```
SpeechOutputDeviceWrapper
初始化TTS设备
使用TTS设备
```

**检查点:**
- 当前使用的是哪个TTS设备（WebSocket/SherpaOnnx/AndroidTTS）
- TTS设备初始化是否成功
- 语言设置是否正确（应该是 ko 或 ko-KR）

### 3. AndroidTTS韩语支持问题
如果降级到了AndroidTTS，需要检查：

**检查日志关键字:**
```
AndroidTtsSpeechDevice
映射后语言
TTS engine
```

**检查点:**
- 系统TTS引擎是否支持韩语
- TTS语言设置是否正确（ko-KR）
- TTS引擎是否初始化成功

### 4. 服务器TTS模型问题
如果使用WebSocket TTS：

**检查服务器端日志:**
- 是否收到了TTS请求
- 韩语TTS模型是否加载
- 音频数据是否发送

## 调试步骤

### 步骤1: 确认当前TTS设备
```bash
adb logcat | grep -E "SpeechOutputDeviceWrapper|当前TTS设备|降级"
```

### 步骤2: 检查WebSocket连接
```bash
adb logcat | grep -E "WebSocket|ConnectionState|连接状态"
```

### 步骤3: 检查TTS请求
```bash
adb logcat | grep -E "TTS合成|speak|请求 TTS"
```

### 步骤4: 检查音频播放
```bash
adb logcat | grep -E "AudioTrack|播放|音频数据"
```

### 步骤5: 检查AndroidTTS（如果降级到AndroidTTS）
```bash
adb logcat | grep -E "AndroidTtsSpeechDevice|TextToSpeech|TTS engine"
```

## 常见解决方案

### 方案1: WebSocket未连接
**症状:** 日志显示WebSocket连接失败或未连接

**解决:** 
1. 检查服务器地址配置
2. 确认服务器是否运行
3. 检查网络连接

### 方案2: AndroidTTS不支持韩语
**症状:** 日志显示 "Language not supported" 或 "setLanguage failed"

**解决:**
1. 在系统设置中安装韩语TTS语音包
2. 确认系统TTS引擎支持韩语
3. 尝试使用SherpaOnnx TTS代替

### 方案3: 降级链配置问题
**症状:** TTS降级到了 NothingSpeechDevice 或 Toast

**解决:**
1. 检查TTS降级链配置是否正确
2. 确认降级链中至少有一个设备可用
3. 调整降级顺序，将可用设备放在前面

### 方案4: 音频播放问题
**症状:** TTS请求成功但没有声音

**解决:**
1. 检查系统音量设置
2. 检查音频焦点权限
3. 检查AudioTrack初始化状态

## 快速定位命令

### 完整TTS调试日志
```bash
adb logcat -v time | grep -E "TTS|Speech|AudioTrack|WebSocket.*tts|SpeechOutputDevice|다시"
```

### 查看最近的TTS相关错误
```bash
adb logcat -v time | grep -E "❌.*TTS|⚠️.*TTS|Error.*TTS"
```

### 查看TTS设备切换
```bash
adb logcat -v time | grep -E "使用TTS设备|TTS设备类型|降级|fallback"
```

## 预期的正常日志流程

### WebSocket TTS正常流程:
```
1. 📋 使用默认TTS降级链: [WEBSOCKET, SHERPA_ONNX, ANDROID_TTS, TOAST, SNACKBAR]
2. 🔌 WebSocket连接成功
3. ✅ 初始化TTS设备: WebSocketTtsSpeechDevice
4. 🗣️ 请求 TTS 合成: '다시 말씀해 주시겠어요?'
5. 📤 发送TTS请求到服务器
6. 📥 接收音频数据: xxx bytes
7. ▶️ 开始播放音频
8. ✅ TTS播放完成
```

### AndroidTTS正常流程:
```
1. 📋 使用默认TTS降级链: [WEBSOCKET, SHERPA_ONNX, ANDROID_TTS, TOAST, SNACKBAR]
2. ⚠️ WebSocket连接失败，尝试降级
3. ✅ 初始化TTS设备: AndroidTtsSpeechDevice
4. 📥 输入语言: ko (language=ko, country=)
5. 🔄 映射后语言: ko_KR (language=ko, country=KR)
6. ✅ TTS引擎初始化成功
7. 🗣️ 请求 TTS 合成: '다시 말씀해 주시겠어요?'
8. ▶️ TTS开始播放
9. ✅ TTS播放完成
```

## 临时调试增强

如果需要更详细的日志，可以在以下文件中添加日志：

1. **SpeechOutputDeviceWrapper.kt**
   - `speak()` 方法开始和结束
   - `isCurrentDeviceAvailable()` 返回值

2. **WebSocketTtsSpeechDevice.kt**
   - `speak()` 方法调用
   - 音频数据接收
   - AudioTrack状态

3. **AndroidTtsSpeechDevice.kt**
   - TTS引擎状态
   - 语言设置结果
   - speak()调用结果

## 相关文件位置
- TTS设备包装器: `app/src/main/kotlin/org/stypox/dicio/di/SpeechOutputDeviceWrapper.kt`
- WebSocket TTS: `app/src/main/kotlin/org/stypox/dicio/io/speech/WebSocketTtsSpeechDevice.kt`
- Android TTS: `app/src/main/kotlin/org/stypox/dicio/io/speech/AndroidTtsSpeechDevice.kt`
- SherpaOnnx TTS: `app/src/main/kotlin/org/stypox/dicio/io/speech/SherpaOnnxTtsSpeechDevice.kt`

