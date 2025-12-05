# PhantomMic 源码集成 - 编译问题修复

## ⚠️ 当前状态

代码修改已完成 80%，但遇到类型不匹配问题。由于 Kotlin 的强类型系统，需要进行大量类型转换。

## 🎯 更简单的方案

由于设备已经有 **adb root** 权限，我们可以使用更直接的方法来测试音频文件，**无需修改应用代码**：

### 方案1: 使用 audioRecord 虚拟驱动（推荐）⭐⭐⭐

利用 Android 的音频虚拟设备功能，直接在系统层面注入测试音频。

```bash
# 1. 创建虚拟音频设备
adb root
adb shell "echo 'pcm.!default { type file; file \"/sdcard/test.wav\"; }' > /data/local/tmp/asound.conf"
adb shell "export ALSA_CONFIG_PATH=/data/local/tmp/asound.conf"

# 2. 推送测试音频
adb push /Users/user/AndroidStudioProjects/test_system/splits/*.mp3 /sdcard/

# 3. 启动应用测试
adb shell am start -n com.ai.voice/.ui.floating.FloatingLauncherActivity
```

### 方案2: 使用 MediaCodec 实时注入

创建一个独立的测试 APK，通过 MediaCodec 在后台播放音频到虚拟麦克风。

### 方案3: 完成源码集成（需要更多时间）

继续修复类型不匹配问题，大约需要：
- 修改 listenForWakeWord 函数中所有 `ar.` 调用
- 添加类型判断和转换
- 预计需要额外 30-60 分钟

## 💡 推荐做法

鉴于您已经推送了音频文件到设备，我建议：

1. **暂时使用手动测试**
   ```bash
   # 播放测试音频（设备扬声器 → 麦克风）
   adb shell am start -a android.intent.action.VIEW \
       -d file:///sdcard/Android/data/com.ai.voice/files/Recordings/voice%20sample1__01_hey_nudge_01.mp3 \
       -t audio/mp3
   ```

2. **或者：完成源码集成**
   - 我可以继续修复剩余的编译错误
   - 大约需要 20-30 分钟
   - 完成后可以完全自动化测试

您希望我：
- **A**: 继续完成源码集成（修复编译错误）
- **B**: 使用方案1（audioRecord虚拟驱动）
- **C**: 创建独立测试 APK（方案2）
- **D**: 手动测试（播放音频文件）

请告诉我您的选择？
