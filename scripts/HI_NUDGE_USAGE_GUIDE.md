# HiNudge 韩语唤醒词使用指南

## 概述

HiNudge是一个独立的韩语唤醒词设备，基于OpenWakeWord技术，专门用于韩语语音唤醒。

## 架构设计

### 1. 独立设备架构
- **HiNudgeOpenWakeWordDevice**: 独立的韩语唤醒词设备
- **与语言无关**: 不再依赖系统语言设置，用户可以手动选择
- **标准化接口**: 实现WakeDevice接口，与其他唤醒设备平等

### 2. 模型管理
- **外部存储优先**: `/storage/emulated/0/Dicio/models/openWakeWord/`
- **自动复制**: 从外部存储自动复制到应用内部存储
- **文件验证**: 自动验证模型文件完整性

### 3. 设备选择
通过应用设置界面选择唤醒方法:
- Hey Dicio (OpenWakeWord)
- SherpaOnnx KWS  
- **하이넛지 (Hi Nudge Korean)** ← 新增的韩语设备
- 禁用

## 使用步骤

### 1. 准备模型文件
确保韩语模型文件存在于项目目录:
```
models/openwakeword_korean_minimal/
├── melspectrogram.tflite
├── embedding.tflite
└── wake.tflite
```

### 2. 运行测试脚本
```bash
./test_hi_nudge_wake_word.sh
```

该脚本会自动:
- 推送模型到外部存储
- 编译并安装应用
- 启动应用
- 监控相关日志

### 3. 应用内设置
1. 打开Dicio应用
2. 进入设置 → 语音输入 → 唤醒方法
3. 选择 "하이넛지 (Hi Nudge Korean)"
4. 返回主界面开始测试

### 4. 测试唤醒
- 播放韩语训练音频
- 或者说出韩语唤醒词
- 观察应用响应和日志输出

## 技术特性

### 1. 优化的检测阈值
- 韩语唤醒词使用较低阈值: `0.005f`
- 提高韩语语音的检测敏感度

### 2. 详细的调试日志
- 音频帧统计信息
- 检测置信度记录
- 模型加载状态跟踪

### 3. 外部存储优先级
1. 检查外部存储: `/storage/emulated/0/Dicio/models/openWakeWord/`
2. 如果存在，自动复制到内部存储
3. 如果不存在，显示错误提示

## 日志监控

关键日志标签:
- `HiNudgeOpenWakeWordDevice`: HiNudge设备相关
- `WakeDeviceWrapper`: 设备切换管理
- `WakeService`: 唤醒服务状态

检测成功日志示例:
```
🎯 하이넛지 DETECTED! Confidence=0.012345, Threshold=0.005000
```

## 故障排除

### 1. 模型文件问题
- 确保模型文件完整且格式正确
- 检查外部存储权限
- 验证文件大小和完整性

### 2. 检测不敏感
- 检查音频输入质量
- 确认选择了正确的唤醒设备
- 查看置信度日志调整阈值

### 3. 应用崩溃
- 检查模型文件兼容性
- 查看详细错误日志
- 确认TensorFlow Lite版本匹配

## 与之前版本的区别

### 移除的功能
- ❌ `LanguageWakeWordManager`: 语言自动适配管理器
- ❌ `KoreanWakeWordManager`: 韩语专用管理器  
- ❌ `KoreanWakeWordSettings`: 韩语设置UI
- ❌ `LocaleManager`中的语言切换逻辑

### 新增的功能
- ✅ `HiNudgeOpenWakeWordDevice`: 独立韩语设备
- ✅ 标准设置界面集成
- ✅ 外部存储优先级管理
- ✅ 简化的架构设计

## 开发者注意事项

1. **数据类去重**: 移除了重复的`AudioFrameStats`定义
2. **清理调试代码**: 简化了OpenWakeWordDevice的调试逻辑
3. **标准化接口**: 所有唤醒设备使用相同的WakeDevice接口
4. **独立性**: HiNudge设备完全独立，不依赖其他组件

这种设计使得韩语唤醒词功能更加稳定、可维护，并且易于扩展到其他语言。
