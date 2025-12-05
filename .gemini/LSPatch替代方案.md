# LSPatch + PhantomMic 配置指南（无需 Root）

## ⚠️ 重要发现

**当前设备状态**:
- 设备: seewo XP32N
- Android: 10
- Root 状态: ❌ **未 Root**
- LSPosed 状态: ❌ **未安装**

**结论**: 由于设备未 Root，无法使用传统的 LSPosed 框架。

## 🔄 替代方案

### 方案1: LSPatch（推荐，无需 Root）⭐

LSPatch 可以在**不需要 Root** 的情况下，通过修补 APK 来注入 Xposed 模块。

#### 优点
- ✅ 无需 Root
- ✅ 可以单独为 dicio-android 应用注入 PhantomMic
- ✅ 不影响系统其他部分

#### 缺点
- ⚠️ 需要重新签名应用（可能会丢失原有签名）
- ⚠️ 需要卸载原应用并安装修补后的版本

#### 操作步骤

1. **下载 LSPatch**
   ```bash
   # 下载 LSPatch Manager
   # https://github.com/LSPosed/LSPatch/releases
   ```

2. **准备应用 APK**
   ```bash
   # 导出当前安装的 dicio-android APK
   adb shell pm path com.ai.voice
   # 输出: package:/data/app/com.ai.voice-xxx/base.apk
   
   # 拉取到本地
   adb pull /data/app/com.ai.voice-xxx/base.apk ~/Desktop/VoiceAssistant.apk
   ```

3. **使用 LSPatch 修补**
   - 在 Android 设备上打开 LSPatch Manager
   - 选择 "修补" → 选择 VoiceAssistant.apk
   - 在模块列表中勾选 "Phantom Mic"
   - 开始修补

4. **安装修补后的 APK**
   ```bash
   # 卸载原应用
   adb uninstall com.ai.voice
   
   # 安装修补后的 APK
   adb install VoiceAssistant-lsp.apk
   ```

5. **配置 PhantomMic**
   - 音频文件已经在正确位置
   - phantom.txt 已配置
   - 直接启动测试即可

### 方案2: 应用层集成（修改源码）⭐⭐ 最可靠

直接在 dicio-android 源码中集成音频注入功能，完全绕过 Xposed 框架。

#### 优点
- ✅ 不需要 Root
- ✅ 不需要任何框架
- ✅ 完全可控
- ✅ 最稳定可靠

#### 实现方式

在 `AudioRecord` 创建时，添加一个开关来选择音频源：

```kotlin
// 在 AsrHandler 或 WakeService 中添加
class TestableAudioRecord(
    audioSource: Int,
    sampleRateInHz: Int,
    channelConfig: Int,
    audioFormat: Int,
    bufferSizeInBytes: Int
) {
    private val usePhantomMic = File("/sdcard/Android/data/com.ai.voice/files/Recordings/phantom.txt").exists()
    private var audioFileStream: InputStream? = null
    
    init {
        if (usePhantomMic) {
            // 读取 phantom.txt 获取音频文件名
            val phantomFile = File("/sdcard/Android/data/com.ai.voice/files/Recordings/phantom.txt")
            if (phantomFile.exists()) {
                val audioFileName = phantomFile.readText().trim()
                if (audioFileName.isNotEmpty()) {
                    val audioFile = File("/sdcard/Android/data/com.ai.voice/files/Recordings/$audioFileName")
                    if (audioFile.exists()) {
                        audioFileStream = audioFile.inputStream()
                        Log.d("PhantomMic", "Using phantom audio: $audioFileName")
                    }
                }
            }
        }
    }
    
    fun read(audioData: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int {
        return if (audioFileStream != null) {
            // 从音频文件读取
            audioFileStream!!.read(audioData, offsetInBytes, sizeInBytes)
        } else {
            // 从真实麦克风读取
            realAudioRecord.read(audioData, offsetInBytes, sizeInBytes)
        }
    }
}
```

### 方案3: 外部音频注入（使用虚拟音频设备）

通过 USB 或蓝牙外部音频设备来播放测试音频。

#### 操作步骤
1. 连接蓝牙音箱或 USB 音频设备
2. 在电脑上播放测试音频文件
3. 设备通过外部音频设备"听到"声音
4. 应用正常处理音频输入

#### 缺点
- ⚠️ 不够精确
- ⚠️ 容易受环境噪音干扰
- ⚠️ 难以自动化

## 🎯 推荐方案对比

| 方案 | 难度 | 稳定性 | 自动化 | 是否需要 Root |
|------|------|--------|--------|--------------|
| LSPatch | 中等 | 高 | 高 | ❌ 否 |
| 源码集成 | 低（已有源码）| 最高 | 最高 | ❌ 否 |
| 外部音频 | 低 | 低 | 低 | ❌ 否 |

## ✅ 建议：使用方案2（源码集成）

由于您有 dicio-android 的完整源码访问权限，**强烈建议使用方案2**：

### 实现步骤

1. **创建 PhantomMic 辅助类**
2. **修改 AudioRecord 使用方式**
3. **在 WakeService 和 AsrHandler 中集成**
4. **构建测试版本**
5. **安装测试**

这样可以：
- ✅ 完全控制音频源切换
- ✅ 支持自动化测试
- ✅ 不需要任何外部框架
- ✅ 代码级别的精确控制

需要我帮您实现这个方案吗？

---

**更新时间**: 2025-12-05 16:59
