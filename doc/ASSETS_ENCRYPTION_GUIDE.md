# Assets模型文件加密保护说明

## 概述

为了防止APK反编译后直接提取assets目录下的模型文件，我们实现了模型文件加密保护机制。

## 保护方案

### 1. 模型文件加密（构建时）

- **加密脚本**: `scripts/encrypt_assets.py`
- **加密算法**: AES-128-ECB
- **加密范围**: 
  - 所有 `.onnx`, `.mdl`, `.tflite`, `.bin` 文件
  - 特定目录下的模型文件（如 `korean_hinudge_onnx`, `sherpa-onnx-sense-voice-*`, `tts-*` 等）
  - 根目录下的模型文件（如 `embedding_model.onnx`, `melspectrogram.onnx`, `silero_vad.onnx`）

### 2. 运行时解密

- **解密工具类**: `AssetDecryptor.kt`
- **辅助类**: `AssetHelper.kt`
- **解密方式**: 
  - 自动检测文件是否加密（通过4字节魔数头 `0x4D4F444C`）
  - 如果加密，自动解密；如果未加密，直接使用

### 3. Protobuf代码混淆保护

- **增强混淆规则**: 允许混淆Protobuf生成的类名，但保留必要的序列化方法
- **保护范围**: 
  - Protobuf核心运行时类（必须保留）
  - DataStore生成的类（允许混淆类名，保留方法）
  - Builder类（允许混淆类名，保留构建方法）
  - 枚举类（允许混淆类名，保留枚举值）

## 使用方法

### 构建时加密模型文件

```bash
# 安装Python依赖（如果还没有）
pip3 install pycryptodome

# 运行加密脚本
python3 scripts/encrypt_assets.py

# 脚本会在 app/src/main/assets_encrypted 目录生成加密后的文件
# 将 assets_encrypted 目录重命名为 assets，或修改构建配置使用 assets_encrypted
```

### 代码中使用

```kotlin
// 方式1: 解密并保存到文件系统（推荐用于需要复制到文件系统的模型）
AssetHelper.decryptAssetToFile(context, "models/wake/korean_wake_word.onnx", outputFile)

// 方式2: 直接读取解密后的流（用于直接使用AssetManager的库）
val inputStream = AssetHelper.openAsset(context, "models/wake/korean_wake_word.onnx")
```

## 安全说明

1. **密钥保护**: 密钥通过字符串变换生成，不直接硬编码
2. **混淆保护**: 解密工具类允许混淆，增加反编译难度
3. **文件保护**: 解密后的文件存储在应用私有目录，不暴露给其他应用
4. **向后兼容**: 未加密的文件可以正常使用，不影响现有功能

## 注意事项

1. **首次使用**: 需要先运行加密脚本加密模型文件
2. **构建流程**: 建议将加密脚本集成到Gradle构建流程中
3. **性能影响**: 解密操作在后台线程执行，对性能影响较小
4. **测试验证**: 加密后需要充分测试，确保所有模型文件都能正常加载

## 后续优化

1. 集成到Gradle构建流程，自动加密
2. 支持更复杂的密钥生成算法
3. 支持分块解密，减少内存占用
4. 添加加密文件完整性校验

