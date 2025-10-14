# HiNudge ONNX V8 Wake Word 模型配置指南

## 问题诊断

您遇到的错误：
```
java.lang.NullPointerException: Attempt to invoke virtual method 
'float[][] com.example.openwakeword.ONNXModelRunner.get_mel_spectrogram(float[])' 
on a null object reference
```

**原因**：缺少必要的前置模型文件。

## OpenWakeWord模型架构

OpenWakeWord使用三阶段流水线：

```
音频输入 → [Mel谱图提取] → [特征嵌入] → [唤醒词检测] → 结果
         melspectrogram.onnx  embedding_model.onnx  korean_wake_word_v8.onnx
```

### 各模型说明：

1. **melspectrogram.onnx**
   - 功能：将原始音频转换为Mel频谱图
   - 输入：音频PCM数据 (Float32[1, samples])
   - 输出：Mel谱图 (Float32[1, frames, 32])
   - **通用模型**：所有语言共用

2. **embedding_model.onnx**
   - 功能：将Mel谱图编码成特征向量
   - 输入：Mel谱图窗口 (Float32[batch, 76, 32, 1])
   - 输出：特征向量 (Float32[batch, 96])
   - **通用模型**：所有语言共用

3. **korean_wake_word_v8.onnx**
   - 功能：基于特征向量检测唤醒词
   - 输入：特征序列 (Float32[1, n_frames, 96])
   - 输出：检测分数 (Float32[1, 1])
   - **您已训练**：针对韩语"하이넛지"

## 解决方案

### 方案A：使用OpenWakeWord官方通用模型（推荐）

从OpenWakeWord官方仓库获取通用模型：

```bash
# 1. 克隆OpenWakeWord仓库
git clone https://github.com/dscripka/openWakeWord.git
cd openWakeWord

# 2. 复制通用模型
# melspectrogram.onnx 位置：openwakeword/resources/models/
# embedding_model.onnx 位置：openwakeword/resources/models/
```

**下载链接**：
- Mel谱图：https://github.com/dscripka/openWakeWord/raw/main/openwakeword/resources/models/melspectrogram.onnx
- Embedding：https://github.com/dscripka/openWakeWord/raw/main/openwakeword/resources/models/embedding_model.onnx

### 方案B：从Python训练完整流水线

如果需要完全自定义，需要导出三个模型：

```python
from openwakeword.model import Model
import onnx

# 1. 加载预训练的通用模型
model = Model()

# 2. 导出Mel谱图模型
mel_model = model.melspectrogram_model
# 保存为ONNX

# 3. 导出Embedding模型  
emb_model = model.embedding_model
# 保存为ONNX

# 4. 训练并导出您的Wake Word模型（已完成）
```

## Android集成步骤

### 1. 准备模型文件

将三个模型放入Android项目：

```
app/src/main/assets/korean_hinudge_onnx/
├── melspectrogram.onnx      # 通用Mel谱图提取器
├── embedding_model.onnx      # 通用特征嵌入器
└── korean_wake_word_v8.onnx  # 您的韩语唤醒词模型
```

### 2. 验证模型

使用ADB推送到测试设备：

```bash
adb push melspectrogram.onnx /sdcard/Dicio/models/korean_hinudge_onnx/
adb push embedding_model.onnx /sdcard/Dicio/models/korean_hinudge_onnx/
adb push korean_wake_word_v8.onnx /sdcard/Dicio/models/korean_hinudge_onnx/
```

### 3. 检查日志

启动应用后查看日志：

```bash
adb logcat | grep "HiNudgeOnnxV8WakeDevice"
```

期望看到：
```
HiNudgeOnnxV8WakeDevice: 📄 Model files:
  - melspectrogram.onnx: EXISTS (XXX bytes)
  - embedding_model.onnx: EXISTS (XXX bytes)
  - korean_wake_word_v8.onnx: EXISTS (XXX bytes)
HiNudgeOnnxV8WakeDevice: ✅ Wake word model loaded
HiNudgeOnnxV8WakeDevice: ✅ Feature buffer initialized
```

## 模型尺寸参考

- **melspectrogram.onnx**: ~1.5 MB
- **embedding_model.onnx**: ~15 MB
- **korean_wake_word_v8.onnx**: 您的模型大小

## Python测试脚本

验证模型兼容性：

```python
import onnxruntime as ort
import numpy as np

# 1. 测试Mel谱图
mel_session = ort.InferenceSession("melspectrogram.onnx")
audio = np.random.randn(1, 16000).astype(np.float32)  # 1秒音频
mel_output = mel_session.run(None, {mel_session.get_inputs()[0].name: audio})
print(f"Mel output shape: {mel_output[0].shape}")  # 应该是 (1, frames, 32)

# 2. 测试Embedding
emb_session = ort.InferenceSession("embedding_model.onnx")
mel_window = np.random.randn(1, 76, 32, 1).astype(np.float32)
emb_output = emb_session.run(None, {emb_session.get_inputs()[0].name: mel_window})
print(f"Embedding output shape: {emb_output[0].shape}")  # 应该是 (1, 96)

# 3. 测试Wake Word
wake_session = ort.InferenceSession("korean_wake_word_v8.onnx")
features = np.random.randn(1, 120, 96).astype(np.float32)
wake_output = wake_session.run(None, {wake_session.get_inputs()[0].name: features})
print(f"Wake word output shape: {wake_output[0].shape}")  # 应该是 (1, 1)
print(f"Detection score: {wake_output[0][0][0]}")
```

## 常见问题

### Q1: 我只有wake word模型，没有mel和embedding模型

**A**: 使用OpenWakeWord官方的通用模型（方案A）。这两个模型是语言无关的，可以直接用于您的韩语唤醒词。

### Q2: 模型输入输出shape不匹配

**A**: 检查您的Python训练代码：
- Mel谱图输出必须是 `(batch, frames, 32)`
- Embedding输入必须是 `(batch, 76, 32, 1)`
- Wake Word输入必须是 `(batch, n_frames, 96)`

### Q3: 检测不准确

**A**: 调整 `DETECTION_THRESHOLD`：
- 降低阈值：提高召回率，但增加误报
- 提高阈值：降低误报，但可能漏检
- 当前V8阈值：0.3f

## 下一步

1. ✅ 获取mel和embedding模型
2. ✅ 放入assets目录
3. ✅ 编译并安装应用
4. ✅ 测试唤醒词检测
5. ✅ 根据效果调整阈值

## 联系方式

如有问题，请提供：
1. 完整的logcat日志
2. 模型文件大小
3. Python训练代码（如果自定义）

