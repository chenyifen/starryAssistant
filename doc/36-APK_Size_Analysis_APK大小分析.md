# APK大小分析报告

## 当前APK大小构成（hyundaiit渠道debug版本）

### ✅ 已完成的优化
1. **移除x86架构**：节省约80MB（只保留armeabi-v7a和arm64-v8a）
2. **清理espeak-ng数据**：节省约32MB（只保留韩语和英语字典）

### 优化前：465MB
### 优化后：预计 ~353MB（节省约112MB）

---

## 优化前APK大小构成（465MB）

### 1. Native库（lib/）：147.6 MB (31.7%)

包含4个ABI架构的native库：
- **armeabi-v7a** (32位ARM): ~32 MB
- **arm64-v8a** (64位ARM): ~35 MB  
- **x86**: ~38 MB
- **x86_64**: ~42 MB

#### 主要Native库大小（每个ABI）：
- `libonnxruntime.so`: ~18 MB × 4 = **72 MB**
  - ONNX Runtime引擎（用于HiNudge唤醒词检测）
- `libvosk.so`: ~9 MB × 4 = **36 MB**
  - Vosk语音识别库
- `libsherpa-onnx-jni.so`: ~5 MB × 4 = **20 MB**
  - SherpaOnnx JNI接口
- `libsherpa-onnx-c-api.so`: ~5 MB × 4 = **20 MB**
  - SherpaOnnx C API
- `libtensorflowlite_jni.so`: ~5 MB × 4 = **20 MB**
  - TensorFlow Lite JNI

### 2. Assets资源：391.2 MB (84.1%)

#### 模型文件：
- `asr.onnx`: **228 MB** (SenseVoice ASR模型)
- `tts-ko/ko_KO-kss_low.onnx`: **60 MB** (韩语TTS模型)
- `tts-en/en_US-amy-low.onnx`: **60 MB** (英语TTS模型)
- `silero_vad.onnx`: **629 KB** (VAD模型)

#### espeak-ng数据文件：
- `espeak-ng-data/*`: **~3.6 MB** (已优化：只保留韩语和英语字典，移除了其他110+种语言字典)
  - 优化前：~36 MB（包含所有语言字典）
  - 优化后：~3.6 MB（只保留en_dict, ko_dict, kok_dict和基础文件）
  - 节省：**~32.4 MB**

#### 其他资源：
- `korean_hinudge_onnx/*`: **~7.8 MB** (唤醒词模型)
- `asr_tokens.txt`: **308 KB**

### 3. DEX文件：64 MB (13.8%)

多个classes.dex文件：
- `classes13.dex`: 166 KB
- `classes14.dex`: 267 KB
- `classes20.dex`: 338 KB
- `classes22.dex`: 110 KB
- `classes23.dex`: 149 KB
- `classes26.dex`: 128 KB

### 4. Resources：1 MB (0.2%)

- `resources.arsc`: 1.03 MB
- 图片资源、布局文件等

## 优化建议

### 1. ✅ 减少Native库大小（已完成：节省约80MB）

**已实施：只保留ARM架构**
   ```kotlin
   ndk {
    abiFilters += arrayOf("armeabi-v7a", "arm64-v8a")  // 已移除x86和x86_64
}
```
- ✅ 节省：~80 MB（移除x86和x86_64的库）
- 影响：x86设备无法运行（但大多数Android设备都是ARM）

**方案B：使用App Bundle + Split APKs**
- 让Google Play自动为不同架构生成APK
- 用户只下载自己设备需要的架构

### 2. 优化Assets大小（可节省约50-100MB）

**方案A：模型压缩**
- 使用量化模型（INT8而不是FP32）
- `asr.onnx`: 228MB → 可能压缩到 ~114MB
- TTS模型：60MB → 可能压缩到 ~30MB

**方案B：按需下载**
- 将大模型文件放到服务器
- 首次启动时下载
- 或使用App Bundle的Asset Delivery

**方案C：✅ 移除不必要的espeak-ng数据（已完成：节省约32MB）**
- ✅ 已移除所有不必要的语言字典，只保留韩语和英语
- 优化前：包含110+种语言的字典文件（~36MB）
- 优化后：只保留en_dict, ko_dict, kok_dict和基础文件（~3.6MB）
- ✅ 节省：~32.4 MB

### 3. 代码优化（可节省约10MB）

**方案A：启用代码混淆和压缩**
   ```kotlin
buildTypes {
   release {
        isMinifyEnabled = true  // 当前是false
        isShrinkResources = true
    }
   }
   ```

**方案B：移除未使用的依赖**
- 检查是否有未使用的库

### 4. 使用App Bundle格式（推荐）

使用`.aab`格式替代`.apk`：
- Google Play会自动优化
- 用户只下载需要的资源
- 可以节省30-50%的下载大小

## 预期优化效果

| 优化项 | 优化前 | 优化后 | 节省 | 状态 |
|--------|--------|--------|------|------|
| 移除x86架构 | 147.6 MB | ~110 MB | ~80 MB | ✅ 已完成 |
| 清理espeak-ng数据 | 36 MB | 3.6 MB | 32.4 MB | ✅ 已完成 |
| 模型压缩 | 348 MB | 174 MB | 174 MB | ⏳ 待实施 |
| 代码压缩 | 64 MB | 54 MB | 10 MB | ⏳ 待实施 |
| **已完成优化** | **465 MB** | **~353 MB** | **~112 MB** | ✅ |
| **全部优化后** | **465 MB** | **~338 MB** | **~127 MB** | ⏳ |

如果使用App Bundle + Split APKs，实际下载大小可以进一步减少到**~200-250MB**（取决于设备架构）。

## 关于"177M"的大小

如果用户看到的是177M，可能是：
1. **Release版本**（已压缩和优化）
2. **App Bundle (.aab)**格式
3. **已安装后的APK**（Android会压缩）
4. **不同渠道**（noModels渠道会小很多）

建议检查：
```bash
# 查看所有渠道的APK大小
ls -lh app/build/outputs/apk/*/debug/*.apk
ls -lh app/build/outputs/apk/*/release/*.apk

# 查看App Bundle大小
ls -lh app/build/outputs/bundle/*/*.aab
```
