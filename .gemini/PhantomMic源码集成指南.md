# PhantomMic 源码集成方案 - 实施指南

## ✅ 方案优势

这个方案**不需要 Root**，不需要 LSPosed/Xposed 框架，通过直接在源码中集成虚拟麦克风功能来实现自动化测试。

### 优点
- ✅ **无需 Root** - 任何设备都可以使用
- ✅ **完全可控** - 代码级别的精确控制
- ✅ **高度稳定** - 不依赖第三方框架
- ✅ **易于调试** - 可以添加详细日志
- ✅ **自动化友好** - 适合 CI/CD 集成

## 📦 已创建的文件

### 1. PhantomAudioRecord.kt
**位置**: `/Users/user/AndroidStudioProjects/dicio-android/app/src/main/kotlin/com/ai/voice/util/PhantomAudioRecord.kt`

**功能**:
- 模拟 `AudioRecord` 的行为
- 自动检测 `phantom.txt` 配置文件
- 从音频文件读取数据或使用真实麦克风
- 支持 ByteArray 和 ShortArray 读取
- 自动循环播放音频文件

## 🔧 集成步骤

### 步骤 1: 修改 WakeService（唤醒词检测）

找到创建 `AudioRecord` 的地方并替换：

```kotlin
// 原代码（在 HiNudgeOnnxV8WakeDevice.kt 中）
val audioRecord = AudioRecord(
    MediaRecorder.AudioSource.MIC,
    SAMPLE_RATE,
    AudioFormat.CHANNEL_IN_MONO,
    AudioFormat.ENCODING_PCM_16BIT,
    bufferSize
)

//  替换为
val audioRecord = PhantomAudioRecord.create(
    context = appContext,  // 需要传入 context
    audioSource = MediaRecorder.AudioSource.MIC,
    sampleRateInHz = SAMPLE_RATE,
    channelConfig = AudioFormat.CHANNEL_IN_MONO,
    audioFormat = AudioFormat.ENCODING_PCM_16BIT,
    bufferSizeInBytes = bufferSize
)
```

### 步骤 2: 修改 AsrHandler（ASR 识别）

找到 `AsrHandler.kt` 中 `doAsr()` 函数里创建 `AudioRecord` 的地方：

```kotlin
// 在 AsrHandler.kt 的 doAsr() 函数中
// 原代码（大约在 400 行左右）
val record = AudioRecord(
    MediaRecorder.AudioSource.VOICE_RECOGNITION,
    SAMPLE_RATE_IN_HZ,
    AudioFormat.CHANNEL_IN_MONO,
    AudioFormat.ENCODING_PCM_16BIT,
    bufferSize
)

// 替换为
val record = PhantomAudioRecord.create(
    context = context,
    audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION,
    sampleRateInHz = SAMPLE_RATE_IN_HZ,
    channelConfig = AudioFormat.CHANNEL_IN_MONO,
    audioFormat = AudioFormat.ENCODING_PCM_16BIT,
    bufferSizeInBytes = bufferSize
)
```

### 步骤 3: 添加导入语句

在修改的文件顶部添加：

```kotlin
import com.ai.voice.util.PhantomAudioRecord
```

### 步骤 4: 构建并安装

```bash
cd /Users/user/AndroidStudioProjects/dicio-android

# 构建 APK
./gradlew assembleDebug

# 安装到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 🎯 使用方法

### 配置文件位置
```
/sdcard/Android/data/com.ai.voice/files/Recordings/phantom.txt
```

### 配置示例

#### 1. 使用虚拟麦克风
```bash
# 设置唤醒词音频
adb shell "echo 'voice sample1__01_hey_nudge_01.mp3' > /sdcard/Android/data/com.ai.voice/files/Recordings/phantom.txt"

# 或者不带扩展名（会自动添加 .mp3）
adb shell "echo 'voice sample1__01_hey_nudge_01' > /sdcard/Android/data/com.ai.voice/files/Recordings/phantom.txt"
```

#### 2. 切换回真实麦克风
```bash
# 清空配置文件
adb shell "echo '' > /sdcard/Android/data/com.ai.voice/files/Recordings/phantom.txt"

# 或删除配置文件
adb shell "rm /sdcard/Android/data/com.ai.voice/files/Recordings/phantom.txt"
```

## 📊 测试流程

### 测试唤醒词

```bash
# 1. 设置唤醒词音频
adb shell "echo 'voice sample1__01_hey_nudge_01.mp3' > /sdcard/Android/data/com.ai.voice/files/Recordings/phantom.txt"

# 2. 启动应用
adb shell am start -n com.ai.voice/.ui.floating.FloatingLauncherActivity

# 3. 查看日志（新终端）
adb logcat | grep -E "PhantomMic|Wake|Nudge"
```

**预期日志**:
```
PhantomMic: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
PhantomMic: ✅ PhantomMic ENABLED
PhantomMic: 📁 Config: /sdcard/Android/data/com.ai.voice/files/Recordings/phantom.txt
PhantomMic: 🎵 Audio: /sdcard/Android/data/com.ai.voice/files/Recordings/voice sample1__01_hey_nudge_01.mp3
PhantomMic: 📊 Size: 29595 bytes
PhantomMic: 🎙️ Sample Rate: 16000 Hz
PhantomMic: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
PhantomMic: ▶️ PhantomMic: Start streaming audio from file
...
HiNudgeOnnxV8WakeDevice: ✅ Wake word detected! Score: 0.89
```

### 测试 ASR 命令

```bash
# 1. 先测试唤醒（使用上述步骤）

# 2. 切换到 ASR 命令音频
adb shell "echo 'voice sample1__02_go_home_01.mp3' > /sdcard/Android/data/com.ai.voice/files/Recordings/phantom.txt"

# 3. 查看 ASR 日志
adb logcat | grep -E "PhantomMic|ASR|Recognition|Skill"
```

## ⚠️ 音频格式要求

### 推荐格式
- **格式**: WAV (16-bit PCM)
- **采样率**: 16000 Hz
- **声道**: 单声道 (Mono)

### 转换 MP3 到 WAV

由于当前实现的解码器较简单，建议先将 MP3 转换为 WAV：

```bash
# 使用 ffmpeg 批量转换
cd /Users/user/AndroidStudioProjects/test_system/splits

# 转换单个文件
ffmpeg -i "voice sample1__01_hey_nudge_01.mp3" -ar 16000 -ac 1 -f s16le "voice sample1__01_hey_nudge_01.wav"

# 批量转换所有 MP3
for file in *.mp3; do
    output="${file%.mp3}.wav"
    ffmpeg -i "$file" -ar 16000 -ac 1 -f s16le "$output"
done

# 推送 WAV 文件到设备
adb push *.wav /sdcard/Android/data/com.ai.voice/files/Recordings/
```

### 或者增强解码器

如果要直接支持 MP3，需要集成 MediaCodec 或 FFmpeg。我可以帮您实现完整的 MP3 解码器。

## 🔍 调试技巧

### 1. 检查 PhantomMic 是否启用

```bash
adb logcat | grep "PhantomMic"
```

如果看到 `✅ PhantomMic ENABLED`，说明配置成功。

### 2. 检查配置文件

```bash
# 查看配置文件内容
adb shell "cat /sdcard/Android/data/com.ai.voice/files/Recordings/phantom.txt"

# 检查音频文件是否存在
adb shell "ls -la /sdcard/Android/data/com.ai.voice/files/Recordings/*.mp3 | head -10"
```

### 3. 查看完整日志

```bash
# 过滤相关日志
adb logcat | grep -E "PhantomMic|PhantomAudioDecoder|HiNudgeOnnxV8WakeDevice|AsrHandler"
```

## 📝 代码修改清单

需要修改以下文件：

- [ ] `app/src/main/kotlin/com/ai/voice/io/wake/onnx/HiNudgeOnnxV8WakeDevice.kt`
  - 替换唤醒词检测的 AudioRecord 创建

- [ ] `app/src/main/kotlin/com/ai/voice/util/AsrHandler.kt`
  - 替换 ASR 识别的 AudioRecord 创建

具体修改位置我可以帮您找到并修改。

## 🚀 下一步

您希望我：

1. **立即修改代码** - 我可以帮您修改 `HiNudgeOnnxV8WakeDevice.kt` 和 `AsrHandler.kt`
2. **先转换音频** - 将 MP3 转换为 WAV 格式以确保兼容性
3. **增强解码器** - 实现完整的 MP3 解码支持
4. **先测试构建** - 确保新代码可以成功编译

请告诉我您希望如何继续？

---

**创建时间**: 2025-12-05 17:00  
**状态**: ✅ PhantomAudioRecord 已创建，等待集成到 WakeService 和 AsrHandler
