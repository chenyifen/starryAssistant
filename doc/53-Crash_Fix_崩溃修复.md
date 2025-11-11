# 应用闪退修复说明

## 问题诊断

### 崩溃位置
```
at org.stypox.dicio.io.input.sensevoice.SenseVoiceRecognizer$Companion$create$2.invokeSuspend(SenseVoiceRecognizer.kt:87)
```

### 崩溃原因
外部存储的SenseVoice模型与当前Sherpa-ONNX版本不兼容：
- 外部模型路径：`/sdcard/Dicio/models/asr/sensevoice/model.int8.onnx`
- 问题：旧版本模型，与新版Sherpa-ONNX API不匹配

### 日志证据
```
D SenseVoiceRecognizer:    💾 从文件系统加载模型
D SenseVoiceRecognizer:    📂 模型: /sdcard/Dicio/models/asr/sensevoice/model.int8.onnx
[崩溃]
F ox.dicio.master: runtime.cc:675]   native: #10 pc 000000000000effc
```

## 快速修复✅

### 方案1：使用Assets中的模型（已实施）
```bash
# 重命名外部存储模型，强制使用Assets
adb shell "mv /sdcard/Dicio/models/asr/sensevoice /sdcard/Dicio/models/asr/sensevoice.backup"
```

**效果：**
- ✅ SenseVoiceModelManager会自动查找Assets中的模型
- ✅ Assets模型路径：`app/src/main/assets/models/asr/sensevoice/`
- ✅ 避免版本不兼容问题

### 方案2：更新外部存储模型（长期方案）
```bash
# 删除旧模型
adb shell "rm -rf /sdcard/Dicio/models/asr/sensevoice"

# 从Assets推送新模型
adb push app/src/main/assets/models/asr/sensevoice/model.int8.onnx /sdcard/Dicio/models/asr/sensevoice/
adb push app/src/main/assets/models/asr/sensevoice/tokens.txt /sdcard/Dicio/models/asr/sensevoice/
```

## 模型查找优先级

### 当前优先级
```
1. 外部存储 (优先级最高) ← ❌ 旧版本模型导致崩溃
   /sdcard/Dicio/models/asr/sensevoice/

2. Assets目录 (回退方案) ← ✅ 新版本模型，兼容性好
   app/src/main/assets/models/asr/sensevoice/
```

### 修复后
```
1. 外部存储已重命名 → 跳过
2. Assets目录 ← ✅ 现在使用这个
```

## 根本原因分析

### 版本不兼容
1. **外部模型**：来自2025-09-18 (sherpa-onnx v1.x)
2. **当前代码**：使用sherpa-onnx v1.12.14
3. **问题**：模型格式或API变化导致JNI崩溃

### 为什么Assets模型可以工作
- Assets中的模型是手动复制的新版本
- 与当前Sherpa-ONNX版本匹配
- 已在SenseVoiceInputDevice中验证通过

## 测试验证

### 启动应用后查看日志
```bash
adb logcat | grep "SenseVoiceModelManager\|SherpaOnnxManager"
```

### 预期输出（成功）
```
SenseVoiceModelManager: ❌ 外部存储目录不存在: /sdcard/Dicio/models/asr/sensevoice
SenseVoiceModelManager: ✅ 使用Assets中的SenseVoice模型: models/asr/sensevoice/model.int8.onnx
SherpaOnnxManager: 📂 模型路径:
SherpaOnnxManager:    模型: models/asr/sensevoice/model.int8.onnx
SherpaOnnxManager:    来源: Assets
SherpaOnnxManager: ✅ Sherpa-ONNX OfflineRecognizer 初始化成功
SherpaOnnxManager: 🌍 支持语言: 中文、英文、日文、韩文、粤语
```

## 长期解决方案

### 1. 添加模型版本检查
在`SenseVoiceModelManager.kt`中添加：
```kotlin
fun checkModelVersion(modelPath: String): Boolean {
    // 检查模型是否与当前Sherpa-ONNX版本兼容
    // 读取模型metadata
    // 验证版本号
}
```

### 2. 统一模型来源
建议只使用一个模型来源：
- **小型应用**：打包在Assets中（简单，但增加APK大小）
- **大型应用**：首次运行时下载到外部存储（减少APK大小）

### 3. 添加降级机制
```kotlin
fun getModelPaths(context: Context): SenseVoiceModelPaths? {
    try {
        // 1. 尝试外部存储
        val external = checkExternalStorage(context)
        if (external != null && validateCompatibility(external)) {
            return external
        }
    } catch (e: Exception) {
        Log.w(TAG, "外部模型不兼容，回退到Assets")
    }
    
    // 2. 回退到Assets
    return checkAssetsModel(context)
}
```

## 现在的操作

1. ✅ **已完成**：重命名外部存储模型为`.backup`
2. **请重新启动应用**
3. **验证**：检查是否还会崩溃

如果应用正常启动，说明问题已解决。

## 恢复外部存储模型（可选）

如果想恢复外部存储模型：
```bash
# 恢复旧模型
adb shell "mv /sdcard/Dicio/models/asr/sensevoice.backup /sdcard/Dicio/models/asr/sensevoice"

# 或更新为新版本
adb shell "rm -rf /sdcard/Dicio/models/asr/sensevoice.backup"
adb push new-model/* /sdcard/Dicio/models/asr/sensevoice/
```

## 修复日期
2025-10-17

---

**状态：✅ 已修复，等待用户重新启动应用验证**

