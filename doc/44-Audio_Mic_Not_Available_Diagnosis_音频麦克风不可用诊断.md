# AudioRecord 麦克风不可用问题诊断报告

## 问题现象
应用无法创建AudioRecord，所有音频源配置都失败，错误代码：-22 (INVALID_OPERATION)

## 错误信息
```
APM_AudioPolicyManager: getInputForAttr() could not find device for source 1
AudioFlinger: createRecord() getInputForAttr return error -22
AudioRecord: createRecord_l(0): AudioFlinger could not create record track, status: -22
```

## 根本原因
**Built-In Mic设备未被AudioPolicyManager识别为可用输入设备**

### 证据
1. **可用输入设备列表中缺少Built-In Mic**
   ```
   Available input devices (3):
     1. HDMIIn (AUDIO_DEVICE_IN_HDMI)
     2. Remote Submix In (AUDIO_DEVICE_IN_REMOTE_SUBMIX)
     3. DPIn (AUDIO_DEVICE_IN_FM_TUNER)
   ```
   ❌ 缺少：Built-In Mic (AUDIO_DEVICE_IN_BUILTIN_MIC)

2. **配置文件中有定义但未激活**
   - `/vendor/etc/audio_policy_configuration.xml` 中定义了 "Built-In Mic"
   - 但设备从未被attach到系统

3. **硬件设备被audioserver占用**
   ```
   # lsof | grep pcmC5D3c
   audioserver  7805  12u  CHR  116,19  /dev/snd/pcmC5D3c
   ```
   - 设备节点存在：`/dev/snd/pcmC5D3c`
   - audioserver已打开设备但状态异常

4. **AudioFlinger输入线程状态异常**
   ```
   Input device: 0 (AUDIO_DEVICE_NONE)
   Audio source: 0 (AUDIO_SOURCE_DEFAULT)
   ```

## 可能原因

### 1. Vendor Audio HAL问题
- 这是一个定制设备（希沃/SEEWO设备）
- persist.vendor.audio配置：
  - `persist.vendor.audio.miccard=5`
  - `persist.vendor.audio.micdev=3`  
  - `persist.vendor.audio.mic.builtin=0`
- Vendor HAL可能没有正确报告Built-In Mic为可用设备

### 2. 设备连接事件未触发
- Audio HAL初始化时应发送设备连接事件
- Built-In Mic的连接事件可能未被触发
- 需要手动触发或修复HAL实现

### 3. 系统录音服务冲突（已排除）
- 之前com.ifpdos.osrecord占用麦克风
- 已禁用该服务，问题仍存在

## 解决方案建议

###  方案A：修改Audio HAL配置（需要root + remount）
1. 检查 `/vendor/etc/audio_policy_configuration.xml`
2. 确保Built-In Mic设备配置正确
3. 可能需要修改vendor分区配置

### 方案B：系统级workaround
1. 使用USB外接麦克风
2. 使用蓝牙麦克风
3. 使用HDMI输入作为替代

### 方案C：应用层适配
修改WakeService使用可用的输入设备（HDMIIn或DPIn）：
```kotlin
// 尝试使用HDMI输入设备
val ar = AudioRecord(
    MediaRecorder.AudioSource.HDMI,
    16000,
    AudioFormat.CHANNEL_IN_MONO,
    AudioFormat.ENCODING_PCM_16_BIT,
    bufferSize
)
```

### 方案D：联系设备厂商
- 设备：希沃（SEEWO）智能设备
- 可能需要厂商提供固件更新或配置修复

## 技术细节

### 设备信息
- ADB连接：192.168.1.69:5555
- Audio卡：Card 5, Device 3
- 设备节点：`/dev/snd/pcmC5D3c`
- 权限：`crw-rw---- system audio`

### 系统属性
```
persist.vendor.audio.miccard=5
persist.vendor.audio.micdev=3
persist.vendor.audio.mic.builtin=0
persist.vendor.audio.mic.NoMicProc=0
```

### AudioFlinger状态
- 输入线程存在但设备类型为AUDIO_DEVICE_NONE
- PCM设备被audioserver打开但未正确初始化
- 无法创建新的录音轨道

## 下一步调查方向
1. 检查vendor audio HAL日志：`logcat -b all | grep audio_hw`
2. 检查SELinux是否阻止设备访问
3. 查看厂商文档了解特殊配置要求
4. 尝试使用tinycap测试原始硬件（需要停止audioserver）

## 时间线
- 2025-11-02 09:51:53 - 首次发现AudioRecord创建失败
- 2025-11-02 09:54 - 发现com.ifpdos.osrecord占用麦克风，已禁用
- 2025-11-02 09:55 - 重启设备，问题仍存在
- 2025-11-02 09:59 - 确认Built-In Mic未在可用设备列表中
- 2025-11-02 10:01 - 确认audioserver占用/dev/snd/pcmC5D3c
- 2025-11-02 10:02 - 诊断完成，问题根源确定




