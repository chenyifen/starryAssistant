# HiNudge ONNX 韩语唤醒词使用指南

## 概述

HiNudgeOpenWakeWordDevice 是基于 OpenWakewordforAndroid-main 完全重新实现的韩语唤醒词设备，使用 ONNX Runtime 进行推理。

## 特性

- ✅ 完全基于 OpenWakewordforAndroid-main 的实现
- ✅ 使用 ONNX Runtime 进行高效推理
- ✅ 支持外部存储模型文件优先级
- ✅ 自动模型复制和加载
- ✅ 完整的音频流处理管道
- ✅ 独立的韩语唤醒词检测

## 模型文件要求

### 必需的 ONNX 模型文件

1. **melspectrogram.onnx** - Mel频谱图生成模型
2. **embedding_model.onnx** - 特征嵌入模型  
3. **hey_nugget_new.onnx** - 韩语唤醒词检测模型

### 文件放置位置

**优先级1：外部存储（推荐）**
```
/storage/emulated/0/Dicio/models/openWakeWord/
├── melspectrogram.onnx
├── embedding_model.onnx
└── hey_nugget_new.onnx
```

**优先级2：应用内部存储（自动复制）**
```
/data/user/0/org.stypox.dicio.master/files/hiNudgeOpenWakeWord/
├── melspectrogram.onnx
├── embedding_model.onnx
└── hey_nugget_new.onnx
```

## 安装步骤

### 1. 准备模型文件

确保你有有效的 ONNX 格式模型文件：
- 文件大小应该合理（通常几MB到几十MB）
- 文件格式为 Protocol Buffers（ONNX标准格式）

### 2. 推送模型文件到设备

```bash
# 创建目录
adb shell mkdir -p /storage/emulated/0/Dicio/models/openWakeWord

# 推送模型文件
adb push melspectrogram.onnx /storage/emulated/0/Dicio/models/openWakeWord/
adb push embedding_model.onnx /storage/emulated/0/Dicio/models/openWakeWord/
adb push hey_nugget_new.onnx /storage/emulated/0/Dicio/models/openWakeWord/
```

### 3. 验证模型文件

运行验证脚本：
```bash
./validate_hinudge_onnx_models.sh
```

### 4. 在应用中选择 HiNudge 设备

1. 打开 Dicio 应用
2. 进入设置
3. 选择唤醒设备为 "하이넛지 (Hi Nudge Korean)"

## 技术实现

### 音频处理流程

```
音频输入 (1280 samples, 16kHz)
    ↓
转换为浮点数 (-1.0 ~ 1.0)
    ↓
流式特征提取
    ↓
Mel频谱图生成 (melspectrogram.onnx)
    ↓
特征嵌入 (embedding_model.onnx)
    ↓
唤醒词预测 (hey_nugget_new.onnx)
    ↓
阈值检测 (> 0.05)
    ↓
唤醒词检测结果
```

### 核心组件

1. **HiNudgeOpenWakeWordDevice** - 主要设备类
2. **ONNXModelRunner** - ONNX 模型运行器
3. **Model** - 音频处理和特征提取
4. **CustomAssetManager** - 文件系统模型加载

### 缓冲区管理

- **原始音频缓冲区**: 10秒音频数据
- **Mel频谱图缓冲区**: 970帧 (10 * 97)
- **特征缓冲区**: 120帧特征向量
- **滑动窗口**: 76帧，步长8帧

## 故障排除

### 常见问题

1. **模型加载失败**
   ```
   ❌ Failed to load HiNudge model
   ```
   - 检查模型文件是否存在
   - 验证文件格式是否正确
   - 确认文件权限

2. **ONNX Runtime 错误**
   ```
   ❌ Error generating mel spectrogram
   ```
   - 检查模型文件完整性
   - 验证输入数据格式
   - 确认内存是否充足

3. **唤醒词不响应**
   - 检查阈值设置（默认 0.05）
   - 验证音频输入质量
   - 确认模型训练数据匹配

### 调试工具

1. **模型验证脚本**
   ```bash
   ./validate_hinudge_onnx_models.sh
   ```

2. **日志查看**
   ```bash
   adb logcat | grep -E "(HiNudge|ONNXModelRunner|HiNudgeModel)"
   ```

3. **清理损坏文件**
   ```bash
   adb shell rm -rf /data/user/0/org.stypox.dicio.master/files/hiNudgeOpenWakeWord/*
   ```

## 性能优化

### 推荐设置

- **采样率**: 16kHz
- **帧大小**: 1280 samples (80ms)
- **检测阈值**: 0.05 (可调)
- **缓冲区大小**: 适中，避免内存溢出

### 内存管理

- 自动清理过期缓冲区
- 限制特征缓冲区大小
- 及时释放 ONNX 会话

## 与原版 OpenWakeWord 的差异

### 优势

1. **更好的集成**: 完全集成到 Dicio 架构中
2. **自动管理**: 自动模型复制和状态管理
3. **错误处理**: 完善的错误处理和恢复机制
4. **调试支持**: 详细的日志和调试信息

### 兼容性

- 模型格式: 完全兼容原版 ONNX 模型
- 音频格式: 16kHz, 16-bit PCM
- 预测结果: 相同的阈值和输出格式

## 开发者信息

### 依赖项

- ONNX Runtime Android: 1.16.0 (compileOnly)
- SherpaOnnx: 提供原生库支持
- Kotlin Coroutines: 异步操作
- Dagger Hilt: 依赖注入

### 扩展点

1. **自定义阈值**: 修改 `threshold` 变量
2. **模型路径**: 自定义 `externalModelDir`
3. **缓冲区大小**: 调整各种 `MAX_LEN` 常量
4. **音频参数**: 修改采样率和帧大小

## 许可证

本实现基于 OpenWakewordforAndroid-main，遵循相应的开源许可证。

## 支持

如有问题，请：
1. 运行验证脚本检查模型文件
2. 查看应用日志获取详细错误信息
3. 确认设备兼容性和权限设置
