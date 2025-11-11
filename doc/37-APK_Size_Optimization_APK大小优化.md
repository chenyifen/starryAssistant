# APK大小分析与优化方案

## 📊 当前APK大小分析

### 快速分析命令

```bash
# 使用分析脚本
./analyze_apk_size.sh app/build/outputs/apk/release/app-release.apk

# 或手动分析
unzip -l app/build/outputs/apk/release/app-release.apk | sort -k1 -rn | head -50
```

---

## 🔍 APK大小组成（预估）

基于项目配置和已有分析文档，Release版本APK大小组成如下：

### 1. Assets资源（最大部分，约70-80%）

#### 模型文件
| 文件 | 大小 | 说明 | 优化方案 |
|------|------|------|----------|
| `sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/model.int8.onnx` | ~228 MB | ASR模型（INT8量化） | ✅ 已优化为INT8 |
| `tts-ko/ko_KO-kss_low.onnx` | ~60 MB | 韩语TTS模型 | 🔄 可进一步量化 |
| `tts-en/en_US-amy-low.onnx` | ~60 MB | 英语TTS模型 | 🔄 可进一步量化 |
| `silero_vad.onnx` | ~629 KB | VAD模型 | ✅ 已优化 |
| `korean_hinudge_onnx/*.onnx` | ~8 MB | 唤醒词模型 | ✅ 已优化 |

#### espeak-ng数据
| 目录 | 大小 | 说明 | 状态 |
|------|------|------|------|
| `tts-ko/espeak-ng-data/` | ~1.8 MB | 韩语字典 | ✅ 已优化 |
| `tts-en/espeak-ng-data/` | ~1.8 MB | 英语字典 | ✅ 已优化 |
| **优化前** | ~36 MB | 包含110+种语言 | ✅ 已移除 |

**Assets总计**: ~360 MB

### 2. Native库（约20-25%）

#### 当前配置（仅ARM架构）
```kotlin
ndk {
    abiFilters += arrayOf("armeabi-v7a", "arm64-v8a")  // ✅ 已移除x86
}
```

| 架构 | 大小 | 主要库 |
|------|------|--------|
| `armeabi-v7a/` | ~35 MB | libonnxruntime.so (~18MB), libvosk.so (~9MB), libsherpa-onnx-jni.so (~5MB) |
| `arm64-v8a/` | ~38 MB | 同上（64位版本） |
| **总计** | **~73 MB** | |

**已优化**: 移除x86和x86_64架构，节省约80MB

### 3. DEX文件（约5-10%）

| 文件 | 大小 | 说明 |
|------|------|------|
| `classes.dex` | ~5-10 MB | 主DEX文件 |
| `classes2.dex` | ~2-5 MB | 多DEX文件（如果启用） |
| **总计** | **~10-15 MB** | |

**优化**: Release版本已启用ProGuard/R8混淆，可减少约20-30%

### 4. Resources（约1-2%）

| 文件 | 大小 | 说明 |
|------|------|------|
| `resources.arsc` | ~1-2 MB | 资源索引表 |
| `res/` | ~500 KB | 图片、布局等资源 |
| **总计** | **~2 MB** | |

---

## 🎯 优化方案

### ✅ 已完成的优化

1. **移除x86架构** - 节省约80MB
2. **清理espeak-ng数据** - 节省约32MB
3. **启用代码混淆** - 减少DEX大小约20-30%
4. **资源压缩** - 启用`isShrinkResources = true`

### 🔄 可进一步优化的方案

#### 方案1: 模型量化（推荐，可节省约100-150MB）

**目标**: 将FP32模型转换为INT8量化模型

**影响**:
- ASR模型: 228MB → ~114MB（节省50%）
- TTS模型: 60MB × 2 → ~30MB × 2（节省50%）

**实施步骤**:
```bash
# 1. 使用ONNX Runtime量化工具
python -m onnxruntime.quantization.quantize \
    --input model_fp32.onnx \
    --output model_int8.onnx \
    --quantization_overrides '{"activation_type": "QInt8", "weight_type": "QInt8"}'

# 2. 验证量化模型精度
# 3. 替换assets中的模型文件
```

**预期节省**: ~120MB

#### 方案2: 按需下载模型（推荐用于生产环境）

**方案A: App Bundle + Asset Delivery**
```kotlin
// build.gradle.kts
android {
    bundle {
        language {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }
}
```

**方案B: 首次启动下载**
- 将大模型文件放到CDN
- 首次启动时检查并下载
- 使用`DownloadManager`或`OkHttp`

**预期节省**: 初始APK减少~300MB，用户按需下载

#### 方案3: 移除未使用的依赖

**检查命令**:
```bash
# 使用Gradle依赖分析
./gradlew app:dependencies > dependencies.txt

# 检查未使用的依赖
./gradlew app:analyzeReleaseDependencies
```

**可能移除的依赖**:
- 未使用的库
- 重复的库（如多个JSON库）
- 测试依赖（确保不在release中）

**预期节省**: ~5-10MB

#### 方案4: 使用WebP格式图片

**当前**: PNG格式图片
**优化**: 转换为WebP格式（可减少30-50%大小）

```bash
# 批量转换
find app/src/main/res -name "*.png" -exec cwebp {} -o {}.webp \;
```

**预期节省**: ~500KB-1MB

#### 方案5: 启用DEX压缩

```kotlin
android {
    buildTypes {
        release {
            // 启用DEX压缩
            dexOptions {
                preDexLibraries = false
                maxProcessCount = 8
            }
        }
    }
}
```

**预期节省**: ~2-5MB

---

## 📈 优化效果预估

| 优化项 | 当前大小 | 优化后 | 节省 | 优先级 | 状态 |
|--------|----------|--------|------|--------|------|
| **当前APK** | **~450 MB** | - | - | - | ✅ |
| 移除x86架构 | 147 MB | 73 MB | 74 MB | P0 | ✅ 已完成 |
| 清理espeak-ng | 36 MB | 3.6 MB | 32.4 MB | P0 | ✅ 已完成 |
| 代码混淆 | 15 MB | 12 MB | 3 MB | P0 | ✅ 已完成 |
| **当前优化后** | **~360 MB** | - | **~110 MB** | - | ✅ |
| 模型量化 | 348 MB | 174 MB | 174 MB | P1 | 🔄 待实施 |
| 按需下载 | 348 MB | 48 MB | 300 MB | P1 | 🔄 待实施 |
| 移除未使用依赖 | 360 MB | 355 MB | 5 MB | P2 | 🔄 待实施 |
| WebP图片 | 2 MB | 1.5 MB | 0.5 MB | P2 | 🔄 待实施 |
| **全部优化后** | **~360 MB** | **~230 MB** | **~130 MB** | - | 🔄 |

**使用App Bundle + 按需下载**: 初始APK可降至 **~50-80 MB**

---

## 🛠️ 实施建议

### 短期优化（立即实施）

1. ✅ **已完成**: 移除x86架构、清理espeak-ng、启用混淆
2. 🔄 **建议**: 检查并移除未使用的依赖
3. 🔄 **建议**: 图片转换为WebP格式

### 中期优化（1-2周）

1. **模型量化**: 
   - 测试INT8量化模型精度
   - 如果精度可接受，替换模型文件
   - 预期节省: ~120MB

### 长期优化（1个月+）

1. **按需下载方案**:
   - 设计模型下载机制
   - 实现CDN存储
   - 添加下载进度和错误处理
   - 预期节省: 初始APK减少~300MB

---

## 📝 优化检查清单

- [x] 移除x86架构native库
- [x] 清理不必要的espeak-ng语言数据
- [x] 启用ProGuard/R8代码混淆
- [x] 启用资源压缩
- [ ] 检查并移除未使用的依赖
- [ ] 图片转换为WebP格式
- [ ] 模型量化（INT8）
- [ ] 实现按需下载模型
- [ ] 使用App Bundle格式
- [ ] 启用DEX压缩

---

## 🔍 分析工具

### 使用分析脚本

```bash
# 分析Release APK
./analyze_apk_size.sh app/build/outputs/apk/release/app-release.apk

# 分析Debug APK
./analyze_apk_size.sh app/build/outputs/apk/debug/app-debug.apk
```

### 使用Android Studio

1. Build → Analyze APK
2. 选择APK文件
3. 查看详细大小组成

### 使用命令行工具

```bash
# 列出APK内容（按大小排序）
unzip -l app-release.apk | sort -k1 -rn | head -50

# 查看特定目录大小
unzip -l app-release.apk | grep "assets/" | awk '{sum+=$1} END {print sum/1024/1024 " MB"}'

# 查看native库大小
unzip -l app-release.apk | grep "lib/" | awk '{sum+=$1} END {print sum/1024/1024 " MB"}'
```

---

## 📚 参考资源

- [Android App Bundle](https://developer.android.com/guide/app-bundle)
- [ONNX Runtime Quantization](https://onnxruntime.ai/docs/performance/model-optimizations/quantization.html)
- [WebP格式](https://developers.google.com/speed/webp)
- [ProGuard优化](https://www.guardsquare.com/manual/configuration/usage)

---

**最后更新**: 2025-11-11  
**当前APK大小**: ~360 MB (Release版本)  
**优化目标**: < 250 MB (不使用按需下载) 或 < 80 MB (使用按需下载)

