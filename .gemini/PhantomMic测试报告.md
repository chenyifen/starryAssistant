# PhantomMic 自动化测试报告

## 📋 测试环境配置

**时间**: 2025-12-05 16:53  
**设备**: WWHARK6PKZT485LB  
**应用**: dicio-android (com.ai.voice)  
**PhantomMic 版本**: 2.0  

### ✅ 完成的配置步骤

1. **PhantomMic 安装**: ✅ 已安装
2. **LSPosed 配置**: ⚠️ 需要在 LSPosed Manager 中启用模块并勾选 `com.ai.voice` 作用域
3. **音频文件推送**: ✅ 已完成 (163个文件)
4. **控制文件配置**: ✅ 已设置为 `voice sample1__01_hey_nudge_01.mp3`

### 📁 测试文件结构

```
/sdcard/PhantomMic/
├── phantom.txt                              # 控制文件
├── voice sample1__01_hey_nudge_01.mp3       # 唤醒词样本
├── voice sample1__01_hey_nudge_02.mp3
├── ... (共163个音频文件)
└── voice sample8__10_blue_pen_02.mp3
```

## 🎯 测试用例

### 1. 唤醒词测试 (Hey Nudge)

#### 测试样本分布
- **Sample 1**: 6个唤醒词文件
- **Sample 4**: 6个唤醒词文件
- **Sample 6**: 2个唤醒词文件
- **Sample 7**: 2个唤醒词文件
- **Sample 8**: 2个唤醒词文件

**总计**: 18个唤醒词样本

#### 测试步骤
```bash
# 1. 设置唤醒词音频
adb shell "echo 'voice sample1__01_hey_nudge_01.mp3' > /sdcard/PhantomMic/phantom.txt"

# 2. 启动应用
adb shell am start -n com.ai.voice/.ui.home.MainActivity

# 3. 观察 WakeService 是否检测到唤醒
adb logcat | grep -i "wake\|nudge\|HiNudge"
```

#### 预期结果
- ✅ WakeService 检测到唤醒词
- ✅ 应用进入 LISTENING 状态
- ✅ UI 显示语音识别界面

### 2. ASR 命令测试

#### 可用命令类型

| 命令类型 | 示例文件 | 样本数 |
|---------|---------|--------|
| Go Home | `voice sample1__02_go_home_01.mp3` | 24 |
| Windows Mode | `voice sample1__04_windows_mode_01.mp3` | 21 |
| Whiteboard | `voice sample1__05_whiteboard_01.mp3` | 18 |
| YouTube | `voice sample1__06_youtube_01.mp3` | 24 |
| Google | `voice sample1__07_google_01.mp3` | 24 |
| WiFi | `voice sample1__08_wifi_01.mp3` | 24 |
| Red Pen | `voice sample4__09_red_pen_01.mp3` | 6 |
| Blue Pen | `voice sample4__10_blue_pen_01.mp3` | 6 |

**总计**: 145个命令样本

#### 测试步骤
```bash
# 1. 先触发唤醒
adb shell "echo 'voice sample1__01_hey_nudge_01.mp3' > /sdcard/PhantomMic/phantom.txt"
# 等待唤醒

# 2. 设置 ASR 命令音频
adb shell "echo 'voice sample1__02_go_home_01.mp3' > /sdcard/PhantomMic/phantom.txt"

# 3. 等待 ASR 识别
sleep 3

# 4. 检查识别结果
adb logcat -d | grep -i "asr\|skill\|AutoTest"
```

#### 预期结果
- ✅ ASR 正确识别语音内容
- ✅ 技能正确执行 (SkillEvaluator)
- ✅ 应用返回 IDLE 状态

## 🔧 LSPosed 配置指南

### ⚠️ 重要：必须完成此配置才能使 PhantomMic 工作

1. **打开 LSPosed Manager**
   ```bash
   # 如果没有安装 LSPosed，需要先安装 Magisk + LSPosed
   ```

2. **启用 PhantomMic 模块**
   - 进入 LSPosed Manager → 模块
   - 找到 "Phantom Mic"
   - 打开开关启用

3. **配置作用域**
   - 点击 Phantom Mic 模块
   - 选择 "应用作用域"
   - 勾选 `com.ai.voice` (Voice Assistant / dicio-android)
   - 保存设置

4. **重启应用**
   ```bash
   adb shell am force-stop com.ai.voice
   adb shell am start -n com.ai.voice/.ui.home.MainActivity
   ```

5. **首次运行配置**
   - 首次启动时，PhantomMic 会弹窗要求选择文件夹
   - 选择 `/sdcard/PhantomMic`
   - 授予存储权限

## 🧪 测试脚本使用

### 快速测试工具
```bash
cd /Users/user/AndroidStudioProjects/test_system
./quick_test.sh
```

**功能**:
- [1] 测试唤醒词识别
- [2] 测试 ASR 命令识别
- [3] 列出所有音频文件
- [4] 手动设置音频文件
- [5] 清空设置 (恢复真实麦克风)
- [6] 查看当前设置

### 完整自动化测试
```bash
cd /Users/user/AndroidStudioProjects/test_system
./phantom_mic_test.sh
```

**功能**:
- 批量测试所有唤醒词样本
- 批量测试所有 ASR 命令
- 统计测试通过率
- 生成测试报告

## 📊 测试结果

### 唤醒词测试结果

| 样本 | 文件 | 状态 | 备注 |
|------|------|------|------|
| Sample1-01 | voice sample1__01_hey_nudge_01.mp3 | ⏳ 待测试 | - |
| Sample1-02 | voice sample1__01_hey_nudge_02.mp3 | ⏳ 待测试 | - |
| ... | ... | ... | ... |

### ASR 命令测试结果

| 命令 | 文件 | 状态 | 识别结果 | 备注 |
|------|------|------|---------|------|
| Go Home | voice sample1__02_go_home_01.mp3 | ⏳ 待测试 | - | - |
| YouTube | voice sample1__06_youtube_01.mp3 | ⏳ 待测试 | - | - |
| Google | voice sample1__07_google_01.mp3 | ⏳ 待测试 | - | - |
| WiFi | voice sample1__08_wifi_01.mp3 | ⏳ 待测试 | - | - |
| ... | ... | ... | ... | ... |

## 🐛 调试技巧

### 1. 检查 PhantomMic 是否工作

```bash
# 查看 LSPosed 日志
adb logcat | grep LSPosed

# 查看 PhantomMic 日志
adb logcat | grep PhantomMic

# 查看 AudioRecord 日志
adb logcat | grep AudioRecord
```

### 2. 验证音频文件

```bash
# 检查文件是否存在
adb shell "ls -la /sdcard/PhantomMic/*.mp3"

# 检查控制文件内容
adb shell "cat /sdcard/PhantomMic/phantom.txt"
```

### 3. 检查应用权限

```bash
# 检查麦克风权限
adb shell "pm dump com.ai.voice | grep RECORD_AUDIO"

# 检查存储权限
adb shell "pm dump com.ai.voice | grep STORAGE"
```

### 4. 手动测试音频播放

```bash
# 在设备上播放音频文件验证
adb shell "am start -a android.intent.action.VIEW -d file:///sdcard/PhantomMic/voice%20sample1__01_hey_nudge_01.mp3 -t audio/mp3"
```

## 📝 常见问题

### Q1: PhantomMic 不工作，应用仍使用真实麦克风

**解决方案**:
1. 确认 LSPosed 已正确安装并启用
2. 确认 Phantom Mic 模块已启用
3. 确认已勾选 `com.ai.voice` 应用作用域
4. 重启应用或重启设备
5. 检查 `phantom.txt` 文件内容是否正确

### Q2: 找不到音频文件

**解决方案**:
```bash
# 音频文件名可以不含扩展名
adb shell "echo 'voice sample1__01_hey_nudge_01' > /sdcard/PhantomMic/phantom.txt"

# 或者包含扩展名
adb shell "echo 'voice sample1__01_hey_nudge_01.mp3' > /sdcard/PhantomMic/phantom.txt"
```

### Q3: 如何切换回真实麦克风

**解决方案**:
```bash
# 清空 phantom.txt
adb shell "echo '' > /sdcard/PhantomMic/phantom.txt"

# 或者删除文件
adb shell "rm /sdcard/PhantomMic/phantom.txt"
```

## 🎯 下一步操作

### 立即测试

1. **确认 LSPosed 配置** ⚠️
   - 打开 LSPosed Manager
   - 启用 Phantom Mic 模块
   - 勾选 com.ai.voice 作用域
   - 重启应用

2. **运行快速测试**
   ```bash
   cd /Users/user/AndroidStudioProjects/test_system
   ./quick_test.sh
   ```

3. **观察日志输出**
   ```bash
   # 新终端窗口
   adb logcat | grep -E "Wake|ASR|AutoTest|PhantomMic"
   ```

4. **测试唤醒词**
   - 当前已设置: `voice sample1__01_hey_nudge_01.mp3`
   - 启动应用观察是否自动检测到唤醒

5. **测试 ASR 命令**
   - 修改 phantom.txt 为命令音频
   - 触发 ASR 识别
   - 检查技能是否执行

## 📚 参考资源

- **PhantomMic GitHub**: https://github.com/Mino260806/PhantomMic
- **LSPosed GitHub**: https://github.com/LSPosed/LSPosed
- **测试脚本位置**: `/Users/user/AndroidStudioProjects/test_system/`
- **音频文件位置**: `/Users/user/AndroidStudioProjects/test_system/splits/`
- **设备路径**: `/sdcard/PhantomMic/`

---

**更新时间**: 2025-12-05 16:53  
**状态**: ✅ 环境配置完成，等待 LSPosed 配置后开始测试
