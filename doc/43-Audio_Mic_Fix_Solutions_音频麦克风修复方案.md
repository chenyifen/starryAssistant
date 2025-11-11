# AudioRecord 麦克风问题解决方案

## 问题总结
该希沃（SEEWO）设备的**Built-In Mic（内置麦克风）设备未被系统识别**，导致所有使用标准麦克风源的AudioRecord创建都会失败。

## ✅ 可行解决方案

### 方案1：联系设备厂商（推荐）
**这是最根本的解决方案**

1. 联系希沃技术支持
2. 说明问题：Built-In Mic设备未在AudioPolicyManager中注册
3. 请求：
   - 固件更新
   - audio_policy_configuration.xml修复
   - vendor audio HAL配置修正

### 方案2：使用外接USB麦克风（最简单）
**立即可用的临时方案**

```bash
# 连接USB麦克风后，它会自动被识别为新的输入设备
# 应用无需修改代码，AudioRecord会自动使用USB麦克风
```

优点：
- 无需修改代码
- 即插即用
- 音质可能更好

### 方案3：修改应用使用HDMI输入（可能可行）
该设备有**HDMIIn**输入设备可用，可以尝试使用它：

#### 修改WakeService.kt

```kotlin
@SuppressLint("MissingPermission")
private fun createOptimalAudioRecord(): AudioRecord? {
    // 优先尝试标准麦克风源
    val standardSources = arrayOf(
        MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
        MediaRecorder.AudioSource.MIC to "MIC",
        MediaRecorder.AudioSource.DEFAULT to "DEFAULT",
    )
    
    // 备用：尝试特殊设备（希沃设备workaround）
    val fallbackSources = arrayOf(
        // 尝试FM_TUNER源（对应DPIn设备）
        MediaRecorder.AudioSource.FM_TUNER to "FM_TUNER (DPIn)",
        // 注意：HDMI输入可能需要特殊权限或配置
    )
    
    val bufferSizes = arrayOf(6400, 3200, 1600, 8000)
    
    // 先尝试标准源
    for ((source, sourceName) in standardSources) {
        for (bufferSize in bufferSizes) {
            try {
                DebugLogger.logAudioProcessing(TAG, "🔧 Trying AudioRecord: source=$sourceName, bufferSize=$bufferSize")
                
                val ar = AudioRecord(
                    source,
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16_BIT,
                    bufferSize
                )
                
                if (ar.state == AudioRecord.STATE_INITIALIZED) {
                    if (testAudioRecord(ar)) {
                        DebugLogger.logAudioProcessing(TAG, "✅ AudioRecord initialized: source=$sourceName")
                        return ar
                    } else {
                        ar.release()
                    }
                }
            } catch (e: Exception) {
                DebugLogger.logWakeWordError(TAG, "❌ Exception creating AudioRecord: source=$sourceName", e)
            }
        }
    }
    
    // 标准源失败，尝试fallback
    DebugLogger.logWakeWord(TAG, "⚠️ 标准麦克风源均失败，尝试设备特定workaround...")
    
    for ((source, sourceName) in fallbackSources) {
        for (bufferSize in bufferSizes) {
            try {
                DebugLogger.logAudioProcessing(TAG, "🔧 [Fallback] Trying AudioRecord: source=$sourceName, bufferSize=$bufferSize")
                
                val ar = AudioRecord(
                    source,
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16_BIT,
                    bufferSize
                )
                
                if (ar.state == AudioRecord.STATE_INITIALIZED) {
                    if (testAudioRecord(ar)) {
                        DebugLogger.logAudioProcessing(TAG, "✅ [Fallback] AudioRecord initialized: source=$sourceName")
                        return ar
                    } else {
                        ar.release()
                    }
                }
            } catch (e: Exception) {
                DebugLogger.logWakeWordError(TAG, "❌ [Fallback] Exception: source=$sourceName", e)
            }
        }
    }
    
    return null
}
```

⚠️ **注意**：HDMIIn和DPIn设备可能需要外部音频输入连接才能工作。

### 方案4：Root修改系统配置（高级）
**需要设备root权限和system分区可写**

步骤：
```bash
# 1. 重新挂载vendor分区为可写
adb shell "mount -o remount,rw /vendor"

# 2. 备份原配置
adb pull /vendor/etc/audio_policy_configuration.xml

# 3. 修改配置（添加Built-In Mic设备连接声明）
# 4. 推送回设备
adb push audio_policy_configuration.xml /vendor/etc/

# 5. 重启
adb reboot
```

⚠️ 风险：可能导致设备音频系统完全无法工作

### 方案5：检查物理连接
检查设备是否有物理麦克风问题：

```bash
# 使用adb shell查看硬件状态
adb shell "cat /proc/asound/cards"
adb shell "cat /proc/asound/card5/pcm3c/info"
```

可能的物理问题：
- 麦克风硬件未连接
- 麦克风被物理开关禁用
- 硬件故障

## 🔍 进一步诊断

### 检查audio HAL日志
```bash
adb logcat -b all -s audio_hw:V AudioPolicyManager:V AudioFlinger:V
```

### 检查vendor配置
```bash
adb shell "getprop | grep vendor.audio"
```

### 检查SELinux
```bash
adb shell "getenforce"
adb logcat | grep avc  # 查看SELinux拒绝日志
```

## 当前设备状态

### 可用输入设备
- ✅ HDMIIn (AUDIO_DEVICE_IN_HDMI)
- ✅ Remote Submix In (AUDIO_DEVICE_IN_REMOTE_SUBMIX) 
- ✅ DPIn (AUDIO_DEVICE_IN_FM_TUNER)
- ❌ Built-In Mic (AUDIO_DEVICE_IN_BUILTIN_MIC) - **不可用**

### 已尝试的修复
- ✅ 禁用com.ifpdos.osrecord服务
- ✅ 重启设备
- ✅ 重启audioserver
- ✅ 修改persist.vendor.audio.mic.builtin属性
- ❌ 所有尝试均无效

## 建议行动计划

1. **立即**：使用USB麦克风测试应用功能
2. **短期**：修改代码尝试DPIn设备
3. **长期**：联系希沃厂商获取固件/配置更新

## 技术联系方式
- 希沃官网：https://www.seewo.com/
- 希沃技术支持：可能需要通过授权经销商联系

## 相关文件
- 详细诊断报告：`AUDIO_MIC_NOT_AVAILABLE_DIAGNOSIS.md`
- WakeService源码：`app/src/main/kotlin/com/ai/voice/io/wake/WakeService.kt`





