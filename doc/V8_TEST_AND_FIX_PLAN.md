# HiNudge V8模型测试与修复计划

**日期**: 2025-10-14  
**目标**: 解决V8模型在Android端的集成问题  
**状态**: 🔄 进行中

---

## 📋 问题诊断

### 当前错误
```
java.lang.NullPointerException: Attempt to invoke virtual method 
'float[][] com.example.openwakeword.ONNXModelRunner.get_mel_spectrogram(float[])' 
on a null object reference
```

### 根本原因

**HiNudgeOnnxV8WakeDevice需要3个模型文件**:

1. ❌ **melspectrogram.onnx** - 缺失
   - 功能: 将PCM音频转为Mel谱图
   - 输入: Float32[1, samples]
   - 输出: Float32[1, frames, 32]

2. ❌ **embedding_model.onnx** - 缺失
   - 功能: 将Mel谱图编码为特征向量
   - 输入: Float32[batch, 76, 32, 1]
   - 输出: Float32[batch, 96]

3. ✅ **korean_wake_word_v8.onnx** - 已有
   - 功能: 基于特征检测唤醒词
   - 输入: Float32[1, 22, 96]
   - 输出: Float32[1, 1]

### 代码位置

`HiNudgeOnnxV8WakeDevice.kt`:
```kotlin
// 第233行 - 初始化时调用
featureBuffer = getEmbeddings(generateRandomFloatArray(SAMPLE_RATE * 4), 76, 8)

// 第322行 - getMelSpectrogram() 需要melspectrogram.onnx
// 第443行 - getEmbeddings() 需要embedding_model.onnx
```

---

## 🎯 解决方案

### 方案A: 使用OpenWakeWord官方通用模型（推荐）⭐⭐⭐⭐⭐

**优点**:
- ✅ 快速解决
- ✅ 模型经过验证
- ✅ 语言无关，适用于所有唤醒词
- ✅ 与V8模型完全兼容

**步骤**:

1. **下载官方模型**:
   ```bash
   # Mel谱图模型
   wget https://github.com/dscripka/openWakeWord/raw/main/openwakeword/resources/models/melspectrogram.onnx
   
   # Embedding模型
   wget https://github.com/dscripka/openWakeWord/raw/main/openwakeword/resources/models/embedding_model.onnx
   ```

2. **放入Android项目**:
   ```bash
   cp melspectrogram.onnx dicio-android/app/src/main/assets/korean_hinudge_onnx/
   cp embedding_model.onnx dicio-android/app/src/main/assets/korean_hinudge_onnx/
   ```

3. **验证**:
   ```bash
   ls -lh app/src/main/assets/korean_hinudge_onnx/
   # 应该看到3个文件:
   # - melspectrogram.onnx (~1.5 MB)
   # - embedding_model.onnx (~15 MB)
   # - korean_wake_word_v8.onnx (~205 KB)
   ```

4. **重新测试**:
   ```bash
   ./gradlew connectedAndroidTest --tests "HiNudgeOnnxV8WakeDeviceTest"
   ```

---

### 方案B: 修改代码直接使用Wake Word模型（如果模型是端到端的）

**适用情况**: 如果您的V8模型**已经内置了特征提取**，直接接受原始音频输入

**需要验证**: V8模型的输入是什么？

**步骤**:

1. **检查模型输入**:
   ```python
   import onnxruntime as ort
   
   session = ort.InferenceSession("korean_wake_word_v8.onnx")
   input_info = session.get_inputs()[0]
   print(f"输入名称: {input_info.name}")
   print(f"输入shape: {input_info.shape}")
   print(f"输入类型: {input_info.type}")
   ```

2. **如果输入是原始音频** (例如 [1, 20480]):
   - 修改 `HiNudgeOnnxV8WakeDevice.kt`
   - 跳过Mel和Embedding步骤
   - 直接feed音频到wake word模型

3. **如果输入是特征** (例如 [1, 22, 96]):
   - 保持当前架构
   - 必须使用方案A获取Mel和Embedding模型

---

### 方案C: 从Python导出完整模型（耗时较长）

如果需要完全自定义，从头训练：

1. **使用OpenWakeWord训练工具**
2. **导出所有3个模型**
3. **确保兼容性**

**不推荐**: 方案A更快更可靠

---

## 🧪 测试计划

### 测试1: 模型文件可用性 ✅
- 检查所有模型文件是否存在
- 验证文件大小

### 测试2: WakeDevice初始化 🔄
- 检查初始化状态
- 捕获加载错误

### 测试3: 合成音频处理 ⏳
- 使用正弦波测试
- 验证processFrame()

### 测试4: 真实音频文件 ⏳
- 加载WAV文件
- 完整流程测试

### 测试5: 连续帧处理 ⏳
- 性能测试
- 实时处理能力

### 测试6: 模型输入输出验证 ⏳
- 验证数据格式
- 检查维度匹配

---

## 📊 预期测试结果

### 如果缺少模型（当前状态）:
```
❌ 测试1: 模型文件可用性 - FAILED
   - melspectrogram.onnx: MISSING
   - embedding_model.onnx: MISSING
   - korean_wake_word_v8.onnx: EXISTS

❌ 测试2: 初始化 - FAILED
   - Error: NullPointerException in getMelSpectrogram()

❌ 测试3-6: 全部跳过
```

### 如果模型完整（方案A完成后）:
```
✅ 测试1: 模型文件可用性 - PASSED
   - melspectrogram.onnx: EXISTS (1.5 MB)
   - embedding_model.onnx: EXISTS (15 MB)
   - korean_wake_word_v8.onnx: EXISTS (205 KB)

✅ 测试2: 初始化 - PASSED
   - State: Loaded

✅ 测试3: 合成音频 - PASSED
   - 可能有误报（正常，因为是随机音频）

⚠️  测试4: 真实音频 - 取决于测试数据
   - 需要准备16kHz WAV文件

✅ 测试5: 性能 - PASSED
   - 平均处理时间 < 80ms

✅ 测试6: 输入输出 - PASSED
   - Frame size: 1280 samples
```

---

## 🔧 修复步骤

### 立即执行（方案A）:

```bash
# 1. 创建临时目录
mkdir -p /tmp/openwakeword_models

# 2. 下载模型
cd /tmp/openwakeword_models
curl -L -O https://github.com/dscripka/openWakeWord/raw/main/openwakeword/resources/models/melspectrogram.onnx
curl -L -O https://github.com/dscripka/openWakeWord/raw/main/openwakeword/resources/models/embedding_model.onnx

# 3. 复制到项目
cp melspectrogram.onnx /Users/user/AndroidStudioProjects/dicio-android/app/src/main/assets/korean_hinudge_onnx/
cp embedding_model.onnx /Users/user/AndroidStudioProjects/dicio-android/app/src/main/assets/korean_hinudge_onnx/

# 4. 验证
ls -lh /Users/user/AndroidStudioProjects/dicio-android/app/src/main/assets/korean_hinudge_onnx/

# 5. 重新测试
cd /Users/user/AndroidStudioProjects/dicio-android
./gradlew connectedAndroidTest --tests "HiNudgeOnnxV8WakeDeviceTest"
```

---

## 📈 迭代计划

### 第1轮: 下载模型并测试
- [ ] 下载melspectrogram.onnx
- [ ] 下载embedding_model.onnx
- [ ] 运行测试
- [ ] 生成测试报告

### 第2轮: 修复问题（如果有）
- [ ] 分析测试失败原因
- [ ] 调整代码或配置
- [ ] 重新测试

### 第3轮: 性能优化
- [ ] 测试实时性能
- [ ] 调整阈值
- [ ] 减少误报

### 第4轮: 真实场景验证
- [ ] 使用真实韩语音频
- [ ] 测试召回率
- [ ] 测试精确率

---

## 🎯 成功标准

1. ✅ 所有测试通过
2. ✅ processFrame() < 80ms
3. ✅ 召回率 ≥ 95%
4. ✅ 精确率 ≥ 70%
5. ✅ 无崩溃或ANR

---

**下一步**: 等待测试结果，然后执行方案A修复

