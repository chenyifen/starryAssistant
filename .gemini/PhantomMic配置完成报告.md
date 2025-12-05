# PhantomMic for dicio-android - 配置完成报告

## ✅ 已完成的配置

**日期**: 2025-12-05  
**设备**: WWHARK6PKZT485LB  
**应用**: com.ai.voice (dicio-android)  

### 1. PhantomMic 安装
- ✅ APK 已下载并安装
- 📦 包名: `tn.amin.phantom_mic`
- 📌 版本: 2.0

### 2. 音频文件部署
- ✅ **163个测试音频文件**已推送到设备
- 📁 目标路径: `/sdcard/Android/data/com.ai.voice/files/Recordings/`
- 🎵 包含内容:
  - **18个唤醒词样本** (hey_nudge)
  - **145个ASR命令样本** (go_home, youtube, google, wifi, windows_mode, whiteboard, red_pen, blue_pen)

### 3. PhantomMic 控制文件
- ✅ `phantom.txt` 已创建
- 📄 当前设置: `voice sample1__01_hey_nudge_01.mp3`
- 🎯 位置: `/sdcard/Android/data/com.ai.voice/files/Recordings/phantom.txt`

### 4. 测试工具
- ✅ `quick_test.sh` - 交互式测试工具
- ✅ `phantom_mic_test.sh` - 完整自动化测试脚本
- 📁 位置: `/Users/user/AndroidStudioProjects/test_system/`

## ⚠️ 重要：LSPosed 配置

### 必须完成以下步骤，PhantomMic 才能工作：

#### 1. 打开 LSPosed Manager
```bash
# 如果设备已 Root 并安装了 Magisk + LSPosed
# 从应用列表或系统设置中打开 LSPosed Manager
```

#### 2. 启用 Phantom Mic 模块
1. 进入 **模块** 标签页
2. 找到 "Phantom Mic" 模块
3. 打开右侧开关启用该模块

#### 3. 配置应用作用域
1. 点击 "Phantom Mic" 模块进入详情
2. 选择 "应用列表" / "作用域"
3. **勾选** `Voice Assistant` (com.ai.voice)
4. 保存设置

#### 4. 重启应用
```bash
adb shell am force-stop com.ai.voice
adb shell am start -n com.ai.voice/.ui.floating.FloatingLauncherActivity
```

#### 5. 首次运行配置（如需要）
- 首次启动时，PhantomMic 可能会弹窗要求选择文件夹
- 如果对话框不工作，**默认使用**: `/sdcard/Android/data/com.ai.voice/files/Recordings/`
- 我们的音频文件已经放在这个默认位置了 ✅

## 🧪 测试方法

### 方法1: 使用交互式测试工具（推荐）

```bash
cd /Users/user/AndroidStudioProjects/test_system
./quick_test.sh
```

**功能菜单**:
- `[1] 测试唤醒词识别` - 自动设置唤醒词音频并启动应用
- `[2] 测试 ASR 命令识别` - 测试各种语音命令
- `[3] 完整测试流程` - 测试唤醒 → ASR 完整流程
- `[4] 列出所有音频文件` - 查看可用的测试音频
- `[5] 手动设置音频文件` - 自定义音频文件
- `[6] 清空设置` - 恢复真实麦克风
- `[m] 实时监控日志` - 查看实时日志输出

### 方法2: 手动测试步骤

#### 测试唤醒词

```bash
# 1. 设置唤醒词音频
adb shell "echo 'voice sample1__01_hey_nudge_01.mp3' > /sdcard/Android/data/com.ai.voice/files/Recordings/phantom.txt"

# 2. 清空日志
adb logcat -c

# 3. 启动应用
adb shell am start -n com.ai.voice/.ui.floating.FloatingLauncherActivity

# 4. 等待8秒，然后查看日志
sleep 8
adb logcat -d | grep -E "Wake|Nudge|HiNudge"
```

**预期结果**: 
- WakeService 应该检测到唤醒词
- 应用进入 LISTENING 状态
- UI 显示语音识别界面

#### 测试 ASR 命令

```bash
# 1. 先触发唤醒（使用上述步骤）

# 2. 设置 ASR 命令音频
adb shell "echo 'voice sample1__02_go_home_01.mp3' > /sdcard/Android/data/com.ai.voice/files/Recordings/phantom.txt"

# 3. 等待 ASR 识别
sleep 5

# 4. 查看识别结果
adb logcat -d | grep -E "ASR|Recognition|Skill|AutoTest"
```

**预期结果**:
- ASR 正确识别语音内容
- 对应技能被触发执行
- 应用返回 IDLE 状态

### 方法3: 实时日志监控

在单独的终端窗口中运行：

```bash
adb logcat | grep --line-buffered -E "Wake|Nudge|ASR|Recognition|Skill|AutoTest|PhantomMic|AudioRecord"
```

然后在另一个窗口执行测试操作，实时观察日志输出。

## 📊 可用的测试音频文件

### 唤醒词 (Hey Nudge) - 18个

```
voice sample1__01_hey_nudge_01.mp3
voice sample1__01_hey_nudge_02.mp3
...
voice sample8__01_hey_nudge_02.mp3
```

### ASR 命令 - 145个

| 命令类型 | 文件示例 | 样本数 |
|---------|---------|--------|
| Go Home | voice sample1__02_go_home_01.mp3 | 24 |
| Windows Mode | voice sample1__04_windows_mode_01.mp3 | 21 |
| Whiteboard | voice sample1__05_whiteboard_01.mp3 | 18 |
| YouTube | voice sample1__06_youtube_01.mp3 | 24 |
| Google | voice sample1__07_google_01.mp3 | 24 |
| WiFi | voice sample1__08_wifi_01.mp3 | 24 |
| Red Pen | voice sample4__09_red_pen_01.mp3 | 6 |
| Blue Pen | voice sample4__10_blue_pen_01.mp3 | 6 |

## 🔧 常用命令

### 查看当前设置
```bash
adb shell "cat /sdcard/Android/data/com.ai.voice/files/Recordings/phantom.txt"
```

### 切换音频文件
```bash
# 不含扩展名也可以
adb shell "echo 'voice sample1__06_youtube_01' > /sdcard/Android/data/com.ai.voice/files/Recordings/phantom.txt"

# 或含扩展名
adb shell "echo 'voice sample1__06_youtube_01.mp3' > /sdcard/Android/data/com.ai.voice/files/Recordings/phantom.txt"
```

### 恢复真实麦克风
```bash
adb shell "echo '' > /sdcard/Android/data/com.ai.voice/files/Recordings/phantom.txt"
```

### 列出所有音频文件
```bash
adb shell "ls /sdcard/Android/data/com.ai.voice/files/Recordings/*.mp3"
```

### 启动/停止应用
```bash
# 启动
adb shell am start -n com.ai.voice/.ui.floating.FloatingLauncherActivity

# 停止
adb shell am force-stop com.ai.voice
```

## 🐛 故障排除

### 问题1: PhantomMic 不工作，应用仍使用真实麦克风

**检查清单**:
1. ✅ LSPosed 是否已安装并正常运行？
2. ✅ Phantom Mic 模块是否已在 LSPosed Manager 中启用？
3. ✅ 是否已勾选 `com.ai.voice` 应用作用域？
4. ✅ 是否已重启应用？
5. ✅ `phantom.txt` 文件内容是否正确？

**验证 LSPosed**:
```bash
# 检查 LSPosed 是否安装
adb shell pm list packages | grep lsposed

# 如果没有输出，说明 LSPosed 未安装
```

**验证音频文件**:
```bash
# 检查文件是否存在
adb shell "ls -la /sdcard/Android/data/com.ai.voice/files/Recordings/*.mp3 | head -10"

# 检查 phantom.txt 内容
adb shell "cat /sdcard/Android/data/com.ai.voice/files/Recordings/phantom.txt"
```

### 问题2: 找不到音频文件

**解决方案**:
```bash
# 确认文件名正确（区分大小写）
adb shell "ls /sdcard/Android/data/com.ai.voice/files/Recordings/" | grep "hey_nudge"

# 使用完整文件名（含扩展名）
adb shell "echo 'voice sample1__01_hey_nudge_01.mp3' > /sdcard/Android/data/com.ai.voice/files/Recordings/phantom.txt"
```

### 问题3: 应用无响应

**解决方案**:
```bash
# 强制停止并重启
adb shell am force-stop com.ai.voice
sleep 2
adb shell am start -n com.ai.voice/.ui.floating.FloatingLauncherActivity
```

### 问题4: 无法查看日志

**解决方案**:
```bash
# 清空旧日志
adb logcat -c

# 使用简单的 grep
adb logcat | grep "com.ai.voice"

# 或使用标签过滤
adb logcat -s "WakeService:*" "AsrHandler:*"
```

## 📝 测试检查清单

### 唤醒词测试

- [ ] LSPosed 已配置并为 com.ai.voice 启用
- [ ] phantom.txt 已设置为唤醒词音频
- [ ] 应用已启动
- [ ] WakeService 日志显示检测到唤醒
- [ ] 应用进入 LISTENING 状态
- [ ] UI 显示语音识别界面

### ASR 命令测试

- [ ] 唤醒词测试已通过
- [ ] phantom.txt 已切换为命令音频
- [ ] ASR 识别日志显示正确结果
- [ ] 对应技能被触发
- [ ] AutoTest 日志显示命令执行结果
- [ ] 应用返回 IDLE 状态

## 🎯 下一步建议

1. **确认 LSPosed 配置** ⚠️ 
   - 这是最关键的步骤
   - 没有正确配置 LSPosed，PhantomMic 无法工作

2. **运行快速测试**
   ```bash
   cd /Users/user/AndroidStudioProjects/test_system
   ./quick_test.sh
   ```
   - 选择 `[1] 测试唤醒词识别`
   - 观察是否检测到唤醒

3. **检查日志输出**
   - 在单独的终端中运行实时监控
   - 观察 PhantomMic 是否注入音频

4. **逐步测试**
   - 先测试单个唤醒词样本
   - 再测试单个 ASR 命令
   - 最后批量测试所有样本

5. **记录测试结果**
   - 哪些样本识别正确
   - 哪些样本识别错误
   - 分析错误原因

## 📚 参考文档

- **PhantomMic 使用指南**: `/Users/user/AndroidStudioProjects/dicio-android/.gemini/PhantomMic使用指南.md`
- **测试脚本**: `/Users/user/AndroidStudioProjects/test_system/quick_test.sh`
- **音频文件**: `/Users/user/AndroidStudioProjects/test_system/splits/`
- **GitHub**: https://github.com/Mino260806/PhantomMic

---

**状态**: ✅ 所有文件已配置完成，等待 LSPosed 配置后即可开始测试  
**最后更新**: 2025-12-05 16:55
