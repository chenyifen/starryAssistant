# HeyNudge语音助手测试快速指南

## 启动识别
```bash
adb shell am broadcast -a org.stypox.dicio.AUTO_TEST_START
```

## 查看结果
```bash
adb logcat -s AutoTest:I | grep "ASR结果"
```

## 示例
```bash
# 终端1：监听结果
adb logcat -s AutoTest:I | grep "ASR结果"

# 终端2：触发测试
adb shell am broadcast -a org.stypox.dicio.AUTO_TEST_START

# 输出示例：
# I/AutoTest: ASR结果: 你好世界
```

详细文档见：[docs/AUTO_TEST_API.md](docs/AUTO_TEST_API.md)


