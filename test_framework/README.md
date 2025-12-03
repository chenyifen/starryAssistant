# 压力测试框架使用说明

## 📋 概述

这是一个完整的压力测试框架，用于测试语音助手应用的稳定性和性能。

## 🚀 快速开始

### 1. 环境准备

```bash
# 安装Python依赖
pip install -r requirements.txt

# 确保ADB可用
adb version

# 连接Android设备
adb devices
```

### 2. 准备测试音频

```bash
# 创建音频目录
mkdir -p audio

# 准备唤醒词音频
# wake_word.wav - 唤醒词音频文件

# 准备命令音频
# command1.wav, command2.wav - 各种命令的音频文件
```

### 3. 配置测试

复制 `test_config.json.example` 为 `test_config.json` 并修改配置：

```json
{
  "devices": [
    {
      "device_id": "device1",
      "adb_serial": "你的设备序列号",
      "device_model": "设备型号",
      "android_version": "Android版本"
    }
  ],
  "audio_source": {
    "wake_word_file": "audio/wake_word.wav",
    "command_files": ["audio/command1.wav"],
    "playback_device": "PC"
  }
}
```

### 4. 运行测试

```bash
# 运行完整测试套件
python3 stress_test_runner.py --config test_config.json

# 运行特定设备
python3 stress_test_runner.py --config test_config.json --device ABC123
```

## 📊 测试报告

测试报告保存在 `test_reports/` 目录下，格式为JSON。

## 🔧 高级用法

### 自定义测试用例

编辑 `test_config.json` 中的 `test_suite.asr_accuracy.test_cases`：

```json
{
  "test_cases": [
    {
      "name": "自定义测试1",
      "audio_file": "audio/custom1.wav",
      "expected_text": "期望的识别结果"
    }
  ]
}
```

### 多设备测试

在配置文件中添加多个设备：

```json
{
  "devices": [
    {"device_id": "device1", "adb_serial": "ABC123"},
    {"device_id": "device2", "adb_serial": "DEF456"}
  ]
}
```

## 📝 注意事项

1. 确保设备已连接并启用USB调试
2. 确保应用已安装并授予必要权限
3. 音频文件格式建议使用WAV格式
4. 长时间测试建议连接电源


