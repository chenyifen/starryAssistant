# HiNudge Korean Wake Word - ONNX Models

韩语唤醒词 "嗨努济" (Hi Nudge) 的ONNX模型文件

## 模型文件

### 1. melspectrogram.tflite (1.0MB)
- **用途**: 音频特征提取 - 将原始音频转换为Mel频谱图
- **输入**: 16-bit PCM audio (16kHz)
- **输出**: Mel spectrogram features (76 x 32)
- **来源**: OpenWakeWord标准模型

### 2. embedding.tflite (1.3MB)
- **用途**: 特征embedding - 将Mel频谱图转换为高层特征向量
- **输入**: Mel spectrogram (76 x 32)
- **输出**: Feature embeddings (16 x 96)
- **来源**: OpenWakeWord标准模型

### 3. korean_wake_word.onnx (322KB)
- **用途**: 唤醒词检测 - 从特征向量中检测是否说了唤醒词
- **输入**: Feature embeddings (16 x 96)
- **输出**: 检测分数 (0-1之间，越高越可能是唤醒词)
- **来源**: 自定义训练（使用CosyVoice生成的韩语数据）

## 模型Pipeline

```
原始音频 (16-bit PCM, 16kHz)
    ↓
melspectrogram.tflite
    ↓
Mel频谱图 (76 x 32)
    ↓
embedding.tflite
    ↓
特征向量 (16 x 96)
    ↓
korean_wake_word.onnx
    ↓
检测分数 (0-1)
```

## 训练信息

- **训练日期**: 2025-10-12
- **训练数据**: CosyVoice生成的韩语音频
- **数据量**: 
  - 正样本: 50个
  - 负样本: 150个
- **训练框架**: OpenWakeWord
- **模型类型**: 端到端唤醒词检测
- **版本**: 临时版本 (无ACAV100M大规模负样本)

## 性能指标

### 临时版本性能
- **准确率**: 25%
- **召回率**: 100% (能检测到所有唤醒词)
- **误报率**: 100% (任何声音都可能触发)
- **适用场景**: 流程验证，不建议实际使用

### 预期完整版本性能 (ACAV100M训练后)
- **准确率**: 60-80%
- **召回率**: 80-95%
- **误报率**: <10%
- **适用场景**: 实际使用

## 使用方法

在Dicio Android应用中:
1. 进入设置 → 输入输出方法
2. 选择"唤醒词识别方法" → "HiNudge Korean (ONNX)"
3. 点击下载模型（会自动从assets复制）
4. 说"嗨努济"测试唤醒功能

## 模型位置

- **Assets路径**: `app/src/main/assets/korean_hinudge_onnx/`
- **运行时路径**: `/data/data/org.stypox.dicio/files/hiNudgeOnnx/`

## 技术细节

### 音频处理参数
- **采样率**: 16000 Hz
- **帧大小**: 80ms (1280 samples)
- **特征窗口**: 16帧 (1.28秒)
- **检测阈值**: 0.5

### 模型架构
- **类型**: 全连接神经网络 (DNN)
- **层数**: 24层
- **训练步数**: 3000步
- **优化器**: Adam

## 未来改进

1. **完整版训练** (进行中)
   - 添加ACAV100M大规模负样本 (16GB, 2000小时)
   - 增加正样本数量 (50 → 200+)
   - 添加背景噪声和混响增强

2. **模型优化**
   - 量化模型以减小文件大小
   - 优化推理速度
   - 降低误报率

3. **多语言支持**
   - 支持中文唤醒词
   - 支持英文唤醒词
   - 多唤醒词同时检测

## 参考资源

- **OpenWakeWord**: https://github.com/dscripka/openWakeWord
- **训练报告**: `/Users/user/AndroidStudioProjects/openWakeWord/korean_training_analysis/FINAL_SUMMARY.md`
- **测试报告**: `test_report_20251012_163210.txt`

---

**创建时间**: 2025-10-12
**模型版本**: v1.0-temp (临时版本)
**状态**: 流程验证通过，等待完整版训练

