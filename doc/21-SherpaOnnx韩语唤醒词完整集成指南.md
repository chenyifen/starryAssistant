# SherpaOnnx 韩语唤醒词完整集成指南

## 概述

本文档详细说明了如何在 Dicio Android 项目中集成 SherpaOnnx KWS（关键词检测）技术，并实现韩语唤醒词"하이넛지"的定制。

## 1. 集成架构

### 1.1 多唤醒技术支持
- **OpenWakeWord**: 原有的唤醒技术
- **SherpaOnnx KWS**: 新集成的唤醒技术，支持多语言关键词检测

### 1.2 构建变体设计
参考 HandsFree 项目的实现，创建了两种构建变体：

#### withModels 变体
- 模型文件预打包在 APK 的 assets 目录中
- 无需用户手动下载模型
- APK 体积较大，但用户体验更好

#### noModels 变体  
- 模型文件需要从外部存储加载
- APK 体积较小
- 需要用户手动下载或推送模型文件到设备

## 2. 文件结构

### 2.1 SherpaOnnx Java 源文件
```
app/src/main/kotlin/com/k2fsa/sherpa/onnx/
├── KeywordSpotter.kt          # 关键词检测器
├── OnlineStream.kt            # 音频流处理
├── FeatureConfig.kt           # 特征配置
└── OnlineModelConfig.kt       # 模型配置
```

### 2.2 原生库文件
```
app/src/main/jniLibs/
├── arm64-v8a/
│   ├── libsherpa-onnx-jni.so  # SherpaOnnx JNI 库
│   └── libonnxruntime.so      # ONNX Runtime 库
└── armeabi-v7a/
    └── (其他架构的库文件)
```

### 2.3 模型文件（withModels 变体）
```
app/src/withModels/assets/models/sherpa_onnx_kws/
├── encoder-epoch-12-avg-2-chunk-16-left-64.onnx  # 编码器模型
├── decoder-epoch-12-avg-2-chunk-16-left-64.onnx  # 解码器模型
├── joiner-epoch-12-avg-2-chunk-16-left-64.onnx   # 连接器模型
├── tokens.txt                                     # 词汇表
└── keywords.txt                                   # 关键词列表
```

### 2.4 核心实现文件
```
app/src/main/kotlin/org/stypox/dicio/
├── io/wake/sherpa/
│   └── SherpaOnnxWakeDevice.kt        # SherpaOnnx 唤醒设备实现
├── util/
│   └── ModelVariantDetector.kt        # 构建变体检测器
├── settings/
│   └── MainSettingsScreen.kt          # 设置界面（已更新）
└── di/
    └── WakeDeviceWrapper.kt            # 唤醒设备工厂（已更新）
```

## 3. 关键词配置

### 3.1 支持的关键词
在 `keywords.txt` 文件中配置了以下关键词：
- `하이넛지` - 韩语唤醒词（主要目标）
- `小艺小艺` - 中文唤醒词
- `小爱同学` - 中文唤醒词
- `你好军哥` - 中文唤醒词
- `小米小米` - 中文唤醒词
- `你好问问` - 中文唤醒词
- `hey dicio` - 英文唤醒词
- `hello dicio` - 英文唤醒词

### 3.2 词汇表配置
`tokens.txt` 文件包含了支持多语言的词汇表，包括：
- 中文拼音音素
- 韩文字符：하, 이, 넛, 지
- 英文单词：hey, dicio, hello
- 特殊标记：`<blk>`, `<sos/eos>`, `<unk>`

## 4. 代码实现要点

### 4.1 构建变体检测
```kotlin
object ModelVariantDetector {
    fun shouldUseAssetManager(context: Context): Boolean {
        return BuildConfig.HAS_MODELS_IN_ASSETS
    }
    
    fun getSherpaKwsModelInfo(context: Context): ModelInfo {
        // 根据构建变体返回模型信息
    }
}
```

### 4.2 SherpaOnnx 设备实现
```kotlin
class SherpaOnnxWakeDevice(private val appContext: Context) : WakeDevice {
    private fun createKwsConfig(useAssetManager: Boolean): KeywordSpotterConfig {
        return if (useAssetManager) {
            // 使用 assets 路径
            KeywordSpotterConfig(
                keywordsFile = "models/sherpa_onnx_kws/keywords.txt",
                // ...
            )
        } else {
            // 使用外部存储路径
            KeywordSpotterConfig(
                keywordsFile = "/storage/emulated/0/Dicio/models/sherpa_onnx_kws/keywords.txt",
                // ...
            )
        }
    }
}
```

### 4.3 设置界面集成
在 `MainSettingsScreen.kt` 中添加了 SherpaOnnx 选项：
```kotlin
when (wakeDevice) {
    WakeDevice.WAKE_DEVICE_SHERPA_ONNX -> {
        item {
            SettingsItem(
                title = "SherpaOnnx Configuration",
                icon = Icons.Default.Tune,
                description = "Configure SherpaOnnx KWS parameters and wake words",
            )
        }
    }
}
```

## 5. 构建和部署

### 5.1 构建 withModels 变体
```bash
./gradlew assembleWithModelsDebug
```

### 5.2 构建 noModels 变体
```bash
./gradlew assembleNoModelsDebug
```

### 5.3 模型文件部署（noModels 变体）
对于 noModels 变体，需要将模型文件推送到设备：
```bash
adb push models/sherpa_onnx_kws/ /storage/emulated/0/Dicio/models/sherpa_onnx_kws/
```

## 6. 使用方法

### 6.1 启用 SherpaOnnx KWS
1. 打开 Dicio 应用
2. 进入设置 (Settings)
3. 找到 "Wake up model" 选项
4. 选择 "SherpaOnnx KWS (Keyword Spotting)"
5. 保存设置

### 6.2 测试韩语唤醒词
1. 确保应用处于监听状态
2. 清晰地说出 "하이넛지"
3. 观察应用是否响应唤醒

## 7. 调试和故障排除

### 7.1 日志标签
- `SherpaOnnxWakeDevice` - SherpaOnnx 设备相关日志
- `ModelVariantDetector` - 构建变体检测日志
- `WakeDeviceWrapper` - 唤醒设备工厂日志

### 7.2 常见问题

#### 问题1: 模型加载失败
**症状**: 日志显示 "SherpaOnnx model has not been loaded yet"
**解决方案**: 
- 检查构建变体是否正确
- 验证模型文件是否存在
- 检查文件权限

#### 问题2: 韩语唤醒词不响应
**症状**: 说出 "하이넛지" 但应用不响应
**解决方案**:
- 检查 keywords.txt 文件是否包含 "하이넛지"
- 验证发音是否准确
- 调整检测阈值 (keywordsThreshold)

#### 问题3: 编译错误
**症状**: 编译时出现 SherpaOnnx 相关错误
**解决方案**:
- 确保原生库文件已正确复制
- 检查 Java 源文件的包名和导入
- 验证构建配置

## 8. 性能优化

### 8.1 模型参数调优
- `keywordsThreshold`: 检测阈值，默认 0.25f
- `keywordsScore`: 关键词得分，默认 1.5f
- `numThreads`: 线程数，默认 1

### 8.2 音频处理优化
- 帧大小: 1600 samples (100ms at 16kHz)
- 采样率: 16000 Hz
- 特征维度: 80

## 9. 扩展和定制

### 9.1 添加新的关键词
1. 编辑 `keywords.txt` 文件
2. 如需要，更新 `tokens.txt` 文件
3. 重新训练模型（如果使用自定义词汇）

### 9.2 支持其他语言
1. 准备对应语言的训练数据
2. 训练新的 SherpaOnnx 模型
3. 更新词汇表和关键词列表

## 10. 参考资源

- [SherpaOnnx 官方文档](https://k2-fsa.github.io/sherpa/onnx/)
- [HandsFree 项目](https://github.com/starry-shivam/HandsFree)
- [Dicio 项目](https://github.com/Stypox/dicio-android)

## 11. 更新日志

- **v1.0** (2025-01-13): 初始版本，完成基本集成
- 支持韩语唤醒词 "하이넛지"
- 实现构建变体支持
- 集成 SherpaOnnx KWS 技术

---

*本文档将随着功能的完善和用户反馈持续更新。*
