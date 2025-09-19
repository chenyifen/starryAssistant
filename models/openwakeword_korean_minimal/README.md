# 韩语唤醒词 "하이넛지" OpenWakeWord 模型

## 文件说明

这些是为Dicio Android应用创建的韩语唤醒词TFLite模型文件：

### 模型文件

#### melspectrogram.tflite
- **功能**: Mel spectrogram feature extraction model
- **输入形状**: [1, 1152]
- **输出形状**: [1, 5, 32]

#### embedding.tflite
- **功能**: Feature embedding extraction model
- **输入形状**: [1, 76, 32, 1]
- **输出形状**: [1, 1, 1, 96]

#### wake.tflite
- **功能**: Wake word classification model
- **输入形状**: [1, 16, 96]
- **输出形状**: [1, 1]


## 使用方法

### 在Dicio中集成

1. 将以下三个文件复制到Dicio项目：
   ```
   app/src/withModels/assets/models/openWakeWord/
   ├── melspectrogram.tflite
   ├── embedding.tflite
   └── wake.tflite
   ```

2. 重新构建Dicio应用：
   ```bash
   ./gradlew assembleWithModelsDebug
   ```

3. 在应用设置中：
   - 选择 "OpenWakeWord offline audio processing" 作为唤醒词识别方法
   - 应用会自动使用这些模型文件

### 技术规格

- **唤醒词**: 하이넛지 (Hi Nutji)
- **语言**: 韩语 (ko-KR)  
- **采样率**: 16kHz
- **音频长度**: 72ms (1152 samples)
- **特征维度**: 32 mel bins
- **嵌入维度**: 96
- **时序长度**: 16 frames

### 模型架构

这些模型遵循OpenWakeWord的三阶段架构：

1. **melspectrogram.tflite**: 将原始音频转换为mel频谱图特征
2. **embedding.tflite**: 从mel特征提取深度嵌入特征
3. **wake.tflite**: 对嵌入特征进行时序建模，输出唤醒词检测概率

### 注意事项

⚠️ **重要**: 这些是简化的模型文件，主要用于测试和集成验证。
对于生产使用，建议：

1. 使用更多训练数据
2. 进行模型量化优化
3. 在真实设备上测试性能
4. 收集真人语音数据进行微调

## 开发信息

- **生成时间**: {"timestamp": "2025-09-15"}
- **训练数据**: TTS生成的韩语语音样本
- **框架**: TensorFlow Lite
- **目标平台**: Android ARM64

## 故障排除

如果在使用过程中遇到问题：

1. **模型加载失败**:
   - 确认文件路径正确
   - 检查文件权限
   - 查看Dicio日志输出

2. **唤醒词识别不准确**:
   - 调整检测阈值
   - 确保发音清晰
   - 减少背景噪音

3. **性能问题**:
   - 监控CPU和内存使用
   - 考虑模型量化
   - 优化音频预处理

## 更多资源

- [OpenWakeWord项目](https://github.com/dscripka/openWakeWord)
- [Dicio项目](https://github.com/Stypox/dicio-android)
- [TensorFlow Lite文档](https://www.tensorflow.org/lite)
