# Sherpa-ONNX AAR 迁移文档

## 迁移日期
2025-10-16

## 迁移背景

之前项目使用手动管理的方式集成 Sherpa-ONNX：
- 手动复制 Kotlin 源码到 `app/src/main/java/com/k2fsa/`
- 手动复制 `.so` 文件到 `app/src/main/jniLibs/`
- 使用的是 `simulated_streaming_asr` 精简版，仅包含 ASR 功能，不含 TTS

这导致：
1. **TTS 功能缺失**：需要单独处理 TTS，降级到 Android 系统 TTS
2. **维护困难**：版本更新需要手动同步多个文件
3. **VAD 兼容性问题**：`silero_vad.onnx` 模型与精简版库不兼容

## 迁移目标

使用官方完整版 AAR，获得：
- ✅ **完整功能**：ASR、TTS、VAD、关键词检测等
- ✅ **易于管理**：单个 AAR 文件，版本统一
- ✅ **VAD 兼容**：完整版支持最新的 VAD 模型

## 迁移步骤

### 1. 编译完整版 Sherpa-ONNX AAR

```bash
cd /Users/user/code/sherpa-onnx

# 拉取最新代码
git pull origin master

# 下载预编译的 native 库
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.14/sherpa-onnx-v1.12.14-android.tar.bz2
tar xf sherpa-onnx-v1.12.14-android.tar.bz2

# 复制 .so 文件到 AAR 项目
cp -v jniLibs/arm64-v8a/* android/SherpaOnnxAar/sherpa_onnx/src/main/jniLibs/arm64-v8a/
cp -v jniLibs/armeabi-v7a/* android/SherpaOnnxAar/sherpa_onnx/src/main/jniLibs/armeabi-v7a/
cp -v jniLibs/x86/* android/SherpaOnnxAar/sherpa_onnx/src/main/jniLibs/x86/
cp -v jniLibs/x86_64/* android/SherpaOnnxAar/sherpa_onnx/src/main/jniLibs/x86_64/

# 编译 AAR
cd android/SherpaOnnxAar
export JAVA_HOME=/Users/user/Library/Java/JavaVirtualMachines/ms-17.0.15/Contents/Home
./gradlew :sherpa_onnx:assembleRelease

# 复制生成的 AAR
cp ./sherpa_onnx/build/outputs/aar/sherpa_onnx-release.aar ../../sherpa-onnx-1.12.14.aar
```

**生成的 AAR**：
- 文件名：`sherpa-onnx-1.12.14.aar`
- 大小：38MB
- 位置：`/Users/user/code/sherpa-onnx/sherpa-onnx-1.12.14.aar`

### 2. 集成到 dicio-android 项目

#### 2.1 复制 AAR 文件

```bash
cp /Users/user/code/sherpa-onnx/sherpa-onnx-1.12.14.aar \
   /Users/user/AndroidStudioProjects/dicio-android/app/libs/
```

#### 2.2 更新 build.gradle.kts

**修改前**：
```kotlin
// SherpaOnnx AAR (静态链接版本)    
// Sherpa-ONNX 1.12.14 - 使用jniLibs中的native库
// implementation(files("libs/sherpa-onnx-static-link-onnxruntime-1.12.4.aar"))
```

**修改后**：
```kotlin
// SherpaOnnx AAR (完整版 1.12.14)
// 包含所有功能：ASR、TTS、VAD等
implementation(files("libs/sherpa-onnx-1.12.14.aar"))
```

#### 2.3 清理手动管理的文件

删除手动复制的 Kotlin 源码：
```bash
rm -rf app/src/main/java/com/k2fsa/
```

删除手动复制的 `.so` 文件：
```bash
find app/src/main/jniLibs -name "libonnxruntime*.so" -o -name "libsherpa-onnx*.so" | xargs rm -f
```

保留的文件（非 Sherpa-ONNX 相关）：
- `libandroidx.graphics.path.so`（各架构）

#### 2.4 恢复 TTS 功能

**文件**：`app/src/main/kotlin/org/stypox/dicio/di/SpeechOutputDeviceWrapper.kt`

**修改前**：
```kotlin
private val defaultTtsFallbackChain = listOf(
    // TtsFallbackDevice.TTS_FALLBACK_DEVICE_SHERPA_ONNX,  // 暂时禁用，需要完整版sherpa-onnx
    TtsFallbackDevice.TTS_FALLBACK_DEVICE_ANDROID_TTS,
    TtsFallbackDevice.TTS_FALLBACK_DEVICE_TOAST,
    TtsFallbackDevice.TTS_FALLBACK_DEVICE_SNACKBAR
)
```

**修改后**：
```kotlin
private val defaultTtsFallbackChain = listOf(
    TtsFallbackDevice.TTS_FALLBACK_DEVICE_SHERPA_ONNX,  // 完整版AAR包含TTS
    TtsFallbackDevice.TTS_FALLBACK_DEVICE_ANDROID_TTS,
    TtsFallbackDevice.TTS_FALLBACK_DEVICE_TOAST,
    TtsFallbackDevice.TTS_FALLBACK_DEVICE_SNACKBAR
)
```

## 迁移后的架构

### 依赖关系

```
dicio-android/app
├── libs/
│   └── sherpa-onnx-1.12.14.aar  (38MB)
│       ├── classes.jar (Kotlin/Java 字节码)
│       │   └── com/k2fsa/sherpa/onnx/*.class
│       └── jni/ (Native 库)
│           ├── arm64-v8a/
│           │   ├── libsherpa-onnx-jni.so
│           │   ├── libsherpa-onnx-c-api.so
│           │   ├── libsherpa-onnx-cxx-api.so
│           │   ├── libonnxruntime.so
│           │   └── libonnxruntime4j_jni.so
│           ├── armeabi-v7a/
│           ├── x86/
│           └── x86_64/
│
├── src/main/assets/
│   ├── silero_vad.onnx  (VAD 模型)
│   └── sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09/
│       ├── model.int8.onnx  (ASR 模型)
│       └── tokens.txt
│
└── src/main/kotlin/org/stypox/dicio/
    ├── io/input/sherpa_simulate/
    │   ├── SherpaOnnxSimulateInputDevice.kt  (ASR + VAD)
    │   └── SherpaOnnxManager.kt  (单例管理)
    └── io/output/sherpa_onnx/
        └── SherpaOnnxTtsDevice.kt  (TTS)
```

### 功能对比

| 功能 | 精简版（simulated_streaming_asr） | 完整版 AAR |
|------|----------------------------------|-----------|
| **ASR（语音识别）** | ✅ SenseVoice | ✅ 所有模型 |
| **VAD（语音活动检测）** | ❌ 不兼容最新模型 | ✅ 完全支持 |
| **TTS（语音合成）** | ❌ 无 | ✅ 支持 |
| **关键词检测** | ❌ 无 | ✅ 支持 |
| **说话人识别** | ❌ 无 | ✅ 支持 |
| **文件大小** | ~20MB | 38MB |
| **维护方式** | 手动管理 | AAR 管理 |

## 验证清单

迁移完成后，请验证以下功能：

### ASR（语音识别）
- [ ] VAD 功能正常（不再出现 "Unsupported silero vad model" 错误）
- [ ] 实时语音识别工作正常
- [ ] 语音段检测准确
- [ ] 停止原因日志清晰

### TTS（语音合成）
- [ ] Sherpa-ONNX TTS 可以正常初始化
- [ ] 语音合成音质正常
- [ ] TTS 降级链工作正常（如果 Sherpa-ONNX TTS 失败，会降级到 Android TTS）

### 性能
- [ ] 应用启动时间正常（无 ANR）
- [ ] 内存使用合理
- [ ] 电池消耗正常

## 已知问题

1. **AAR 文件较大（38MB）**
   - 原因：包含所有功能和架构的 native 库
   - 解决方案：可以使用 APK splits 或 App Bundle 按架构分发

2. **首次初始化时间较长**
   - 原因：需要加载大量 ONNX 模型
   - 解决方案：已在 `SherpaOnnxManager` 中使用 IO 线程异步加载

## 回滚方案

如果迁移后出现问题，可以回滚：

1. 恢复旧版 AAR（如果有）：
   ```bash
   git checkout HEAD~1 -- app/libs/
   git checkout HEAD~1 -- app/build.gradle.kts
   ```

2. 恢复手动管理的文件：
   ```bash
   # 从之前的版本复制
   cp -r /path/to/backup/com/k2fsa app/src/main/java/
   cp -r /path/to/backup/jniLibs/* app/src/main/jniLibs/
   ```

## 参考资料

- [Sherpa-ONNX GitHub](https://github.com/k2-fsa/sherpa-onnx)
- [Sherpa-ONNX AAR 构建文档](https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnxAar)
- [项目内部文档](./SHERPA_ONNX_REFACTOR.md)

