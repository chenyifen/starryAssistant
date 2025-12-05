# Phantom Mic 使用指南 - 自动化语音测试

## 📋 项目介绍

**Phantom Mic** 是一个 LSPosed (Xposed/Edxposed) 模块，可以模拟麦克风输入，从预录的音频文件播放，非常适合用于自动化测试语音助手应用。

**GitHub**: https://github.com/Mino260806/PhantomMic

## 🎯 用途

- ✅ 自动化测试语音助手（如 dicio-android）
- ✅ 测试 ASR 识别准确性
- ✅ 模拟不同场景的语音输入
- ✅ 无需真人说话，可重复测试

## 📋 系统要求

### 必需条件

1. **Android 7+**
2. **Root 权限**
3. **LSPosed 框架** (或 Edxposed)
   - 推荐使用 LSPosed (更现代，维护更好)
   - Magisk + LSPosed 模块

### 可选（无 Root）

- **LSPatch** (理论上可用，但未经测试)

## 🔧 安装步骤

### 1. 安装 LSPosed 框架

```bash
# 如果你已经有 Magisk，从 Magisk Manager 安装 LSPosed
# 或者从 GitHub 下载：
# https://github.com/LSPosed/LSPosed/releases
```

### 2. 下载并安装 Phantom Mic

```bash
# 从 GitHub Releases 下载最新版本 (2.0)
# https://github.com/Mino260806/PhantomMic/releases
```

### 3. 在 LSPosed 中启用模块

1. 打开 **LSPosed Manager**
2. 进入 **模块** 页面
3. 启用 **Phantom Mic**
4. 选择作用域 → 勾选你的目标应用（如 `com.ai.voice`）
5. 重启目标应用

## 📝 使用步骤

### 第一步：准备音频文件

准备测试用的音频文件，支持格式：
- ✅ `.mp3`
- ✅ `.wav`
- ✅ `.m4a`
- ✅ 其他常见音频格式

**示例音频文件**：
```
test_volume_up.mp3      # "볼륨 올려줘" (韩语：提高音量)
test_weather.wav        # "오늘 날씨 어때?" (韩语：今天天气怎么样)
test_time.mp3           # "지금 몇 시야?" (韩语：现在几点)
```

### 第二步：设置模块文件夹

1. **首次打开目标应用**
   - 启动你的语音助手应用（如 dicio-android）
   - Phantom Mic 会提示选择文件夹

2. **选择存储位置**
   - 建议路径：`/sdcard/PhantomMic/`
   - 或者：`/sdcard/Android/data/com.ai.voice/files/PhantomMic/`

### 第三步：复制音频文件

```bash
# 将音频文件复制到选择的文件夹
adb push test_volume_up.mp3 /sdcard/PhantomMic/
adb push test_weather.wav /sdcard/PhantomMic/
adb push test_time.mp3 /sdcard/PhantomMic/
```

### 第四步：创建控制文件

在模块文件夹中创建 `phantom.txt` 文件：

```bash
# 创建控制文件
adb shell "echo 'test_volume_up' > /sdcard/PhantomMic/phantom.txt"
```

**`phantom.txt` 内容规则**：
- 写入音频文件名（可以不带扩展名）
- 例如：`test_volume_up` 或 `test_volume_up.mp3` 都可以
- 留空 = 使用真实麦克风（正常模式）

### 第五步：测试

1. 打开目标应用
2. 触发麦克风录音（如说唤醒词）
3. Phantom Mic 会自动播放 `phantom.txt` 中指定的音频文件
4. 应用会接收到模拟的音频输入

## 🎯 用于 dicio-android 的测试场景

### 场景 1: 测试唤醒词识别

```bash
# 准备唤醒词音频
adb push wake_word_hinudge.mp3 /sdcard/PhantomMic/

# 设置控制文件
adb shell "echo 'wake_word_hinudge' > /sdcard/PhantomMic/phantom.txt"

# 启动应用，WakeService 会接收到模拟的唤醒词
# 检查 logcat 是否检测到唤醒
adb logcat | grep WakeService
```

### 场景 2: 测试 ASR Final 识别

```bash
# 准备命令音频
adb push command_volume_up.mp3 /sdcard/PhantomMic/      # "볼륨 올려줘"
adb push command_weather.mp3 /sdcard/PhantomMic/        # "오늘 날씨 어때?"

# 测试音量控制
adb shell "echo 'command_volume_up' > /sdcard/PhantomMic/phantom.txt"

# 触发 ASR（说唤醒词后，或直接启动 ASR）
# 检查技能是否正确执行
adb logcat | grep "SkillEvaluator"
```

### 场景 3: 自动化测试多个命令

创建测试脚本：

```bash
#!/bin/bash
# test_all_commands.sh

commands=(
    "command_volume_up"
    "command_volume_down"
    "command_weather"
    "command_time"
)

for cmd in "${commands[@]}"; do
    echo "Testing: $cmd"
    
    # 设置音频
    adb shell "echo '$cmd' > /sdcard/PhantomMic/phantom.txt"
    
    # 等待 2 秒
    sleep 2
    
    # 触发 ASR (这里需要配合 AutoTest 或其他方式)
    # adb shell am broadcast ...
    
    # 检查结果
    adb logcat -d | grep "AutoTest" | tail -5
    
    sleep 3
done
```

### 场景 4: 测试静音超时

```bash
# 准备静音音频（无声或极低音量）
adb push silence_10s.mp3 /sdcard/PhantomMic/

# 设置
adb shell "echo 'silence_10s' > /sdcard/PhantomMic/phantom.txt"

# 触发 ASR，检查是否在 10 秒后触发静音超时
adb logcat | grep "SILENCE_TIMEOUT"
```

## 📊 配合 AutoTest 使用

### 集成测试脚本

```bash
#!/bin/bash
# phantom_mic_autotest.sh

# 1. 设置 Phantom Mic 音频
set_phantom_audio() {
    local audio_name=$1
    adb shell "echo '$audio_name' > /sdcard/PhantomMic/phantom.txt"
    echo "✅ Phantom Mic 已设置: $audio_name"
}

# 2. 触发 ASR 识别
trigger_asr() {
    # 使用 AutoTest HTTP 接口
    curl -X POST http://192.168.2.8:8080/trigger_asr \
         -H "Content-Type: application/json" \
         -d '{"text": "auto"}'
}

# 3. 检查测试结果
check_result() {
    local expected=$1
    adb logcat -d | grep "AutoTest" | grep "$expected"
}

# 执行测试
echo "=== 开始 Phantom Mic 自动化测试 ==="

# 测试 1: 音量控制
set_phantom_audio "command_volume_up"
trigger_asr
sleep 3
check_result "volume_up"

# 测试 2: 天气查询
set_phantom_audio "command_weather"
trigger_asr
sleep 3
check_result "weather"

echo "=== 测试完成 ==="
```

## ⚠️ 注意事项

### 1. 应用兼容性

**Phantom Mic 可能不工作的情况**：
- 应用直接录音到文件（而不是实时流）
- 应用使用了特殊的音频源（而不是 `AudioRecord`）

**已测试可用的应用**：
- ✅ Facebook Messenger
- ✅ Discord
- ✅ Telegram
- ✅ WhatsApp
- ✅ Google Chrome
- ❓ dicio-android（需要测试）

### 2. 调试技巧

如果模块不工作：

```bash
# 1. 检查 LSPosed 日志
adb logcat | grep LSPosed

# 2. 检查 Phantom Mic 是否已启用
# 在 LSPosed Manager 中确认

# 3. 检查音频文件路径
adb shell ls -la /sdcard/PhantomMic/

# 4. 检查 phantom.txt 内容
adb shell cat /sdcard/PhantomMic/phantom.txt

# 5. 查看应用日志
adb logcat | grep AudioRecord
```

### 3. 切换回真实麦克风

```bash
# 清空 phantom.txt 内容
adb shell "echo '' > /sdcard/PhantomMic/phantom.txt"

# 或者删除文件
adb shell rm /sdcard/PhantomMic/phantom.txt
```

## 🔥 高级用法

### 1. 动态切换音频文件

使用 MacroDroid 或 Tasker 自动化：

```
触发器: HTTP 请求接收
动作: 写入文件 /sdcard/PhantomMic/phantom.txt
内容: %http_param1
```

### 2. 批量测试脚本

```python
# batch_test.py
import subprocess
import time

test_cases = [
    {"audio": "test_1", "expected": "volume_up"},
    {"audio": "test_2", "expected": "weather"},
    {"audio": "test_3", "expected": "time"},
]

for case in test_cases:
    # 设置音频
    subprocess.run([
        "adb", "shell", 
        f"echo '{case['audio']}' > /sdcard/PhantomMic/phantom.txt"
    ])
    
    time.sleep(1)
    
    # 触发测试
    # ... 你的触发逻辑
    
    # 验证结果
    # ... 你的验证逻辑
```

## 📚 相关资源

- **GitHub**: https://github.com/Mino260806/PhantomMic
- **XDA Thread**: (查看 GitHub README)
- **LSPosed**: https://github.com/LSPosed/LSPosed
- **LSPatch** (无 Root): https://github.com/LSPosed/LSPatch

## 🎉 总结

Phantom Mic 是一个强大的自动化测试工具，特别适合测试语音助手应用。通过模拟麦克风输入，你可以：

- ✅ 重复测试相同的语音命令
- ✅ 测试边缘情况（静音、噪音等）
- ✅ 自动化 CI/CD 测试流程
- ✅ 无需人工说话，节省时间

**下一步**：
1. 安装 LSPosed + Phantom Mic
2. 准备测试音频文件
3. 配置 `phantom.txt`
4. 开始测试你的语音助手！

---

**文档版本**: v1.0  
**最后更新**: 2025-12-05 16:45
