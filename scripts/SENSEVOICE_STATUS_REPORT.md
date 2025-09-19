# SenseVoice双识别功能状态报告

## 📋 功能概述

已为Dicio-Android集成完整的双识别模式（Two-Pass Recognition），结合Vosk实时识别和SenseVoice高精度识别，提供更准确的语音识别体验。

## ✅ 已完成的功能

### 1. 核心架构
- ✅ **TwoPassInputDevice**: 双识别主控制器
- ✅ **SenseVoiceRecognizer**: SenseVoice ONNX推理封装
- ✅ **SenseVoiceModelManager**: 模型路径管理和验证
- ✅ **AudioBuffer**: 音频缓冲和预处理

### 2. 模型集成
- ✅ **模型文件**: 从HandsFree项目成功复制239MB量化SenseVoice模型
  - `model.int8.onnx` (239MB)
  - `tokens.txt` (25,055个tokens)
  - 位置: `app/src/withModels/assets/models/asr/sensevoice/`

### 3. 设置界面
- ✅ **设置选项**: "Two-Pass Recognition" 已添加到语音输入方法
- ✅ **用户界面**: 完整的中英文字符串资源
- ✅ **设备注册**: 在SttInputDeviceWrapper中正确注册

### 4. 详细日志系统
- ✅ **初始化日志**: 详细的SenseVoice初始化步骤记录
- ✅ **模型加载日志**: 模型路径、文件大小、来源信息
- ✅ **识别过程日志**: 音频质量、处理时间、结果对比
- ✅ **错误诊断日志**: 异常情况的详细堆栈跟踪

## 🔧 技术特性

### 工作流程
1. **第一阶段 (Vosk)**:
   - 提供实时语音转文本反馈
   - 低延迟用户体验
   - 同时缓存音频数据

2. **第二阶段 (SenseVoice)**:
   - 延迟500ms触发
   - 使用完整音频进行高精度识别
   - 智能结果选择和融合

### 音频处理
- **质量检测**: RMS、峰值、零交叉率分析
- **预处理**: 高通滤波、音量归一化
- **缓冲管理**: 最大30秒音频缓存
- **最小时长检查**: 至少1秒音频才触发第二阶段

### 智能回退
- **模型检测**: 自动检测SenseVoice可用性
- **优雅降级**: SenseVoice不可用时自动使用纯Vosk模式
- **错误恢复**: 异常情况的自动处理

## 📱 使用方法

### 启用双识别模式
1. 打开Dicio应用
2. 进入设置 → 语音输入方法
3. 选择 "Two-Pass Recognition"
4. 描述: "Real-time Vosk + High-accuracy SenseVoice"

### 支持的版本
- **withModels版本**: 模型已内置，直接可用
- **noModels版本**: 需要将SenseVoice模型放置到 `/storage/emulated/0/Dicio/models/sensevoice/`

## 🛠️ 调试和监控

### 监控脚本
```bash
# 实时监控SenseVoice日志
./monitor_sensevoice.sh

# 功能测试验证
./test_sensevoice.sh

# 快速状态检查
adb logcat -s TwoPassInputDevice | grep -E '✅|❌|🎯|🚀'
```

### 关键日志标签
- `TwoPassInputDevice`: 双识别主流程
- `SenseVoiceRecognizer`: SenseVoice推理过程
- `SenseVoiceModelManager`: 模型管理
- `AudioBuffer`: 音频缓冲处理

## 📊 日志示例

### 成功初始化
```
TwoPassInputDevice: 🔄 开始初始化SenseVoice识别器...
TwoPassInputDevice: 📋 步骤1: 检查SenseVoice模型可用性...
TwoPassInputDevice: 📋 模型检查结果: ✅ 可用
SenseVoiceRecognizer: 🔧 SenseVoiceRecognizer.create() 开始执行...
SenseVoiceRecognizer: ✅ 模型路径获取成功:
SenseVoiceRecognizer:    📂 模型: models/asr/sensevoice/model.int8.onnx
SenseVoiceRecognizer:    📄 Tokens: models/asr/sensevoice/tokens.txt
SenseVoiceRecognizer: ✅ OfflineRecognizer创建成功！
TwoPassInputDevice: ✅ SenseVoice识别器初始化成功！
TwoPassInputDevice: 🎯 双识别模式已激活 (Vosk + SenseVoice)
```

### 双识别过程
```
TwoPassInputDevice: 🎯 触发第二阶段识别
TwoPassInputDevice:    📝 第一阶段结果: "hello world" (置信度: 0.850)
TwoPassInputDevice: 📊 音频缓冲区状态: AudioBuffer(32000samples/2.00s)
TwoPassInputDevice: ✅ SenseVoice识别器可用，开始第二阶段识别
TwoPassInputDevice: 🧠 执行SenseVoice推理...
TwoPassInputDevice: 第二阶段识别完成: "Hello, world!" (1500ms)
TwoPassInputDevice: 识别对比 - 第一阶段: "hello world" -> 第二阶段: "Hello, world!" (改进)
```

## 🎯 预期效果

### 性能指标
- **第一阶段延迟**: < 100ms (Vosk实时)
- **第二阶段延迟**: 1000-2000ms (SenseVoice离线)
- **准确率提升**: 相比单一Vosk识别提高10-20%
- **内存占用**: 额外~300MB (SenseVoice模型)

### 用户体验
- **即时反馈**: Vosk提供实时识别结果
- **最终优化**: SenseVoice提供更准确的最终结果
- **透明切换**: 用户无感知的智能模式切换

## 🔍 验证清单

要确认SenseVoice功能是否正常工作，请检查：

1. ✅ **应用启动**: 无崩溃，正常加载
2. ✅ **设置可见**: "Two-Pass Recognition" 选项存在
3. ✅ **模型检测**: 日志显示 "SenseVoice模型可用"
4. ✅ **识别器创建**: 日志显示 "SenseVoice识别器初始化成功"
5. ✅ **双识别触发**: 语音输入时看到两阶段日志
6. ✅ **结果对比**: 日志中显示第一阶段和第二阶段结果对比

## 🚀 下一步

SenseVoice双识别功能已完整实现并可用。用户可以立即在Dicio中体验更高精度的语音识别效果！

---

**报告生成时间**: 2025年9月19日  
**状态**: ✅ 功能完整，可投入使用  
**版本**: withModels (内置SenseVoice模型)
