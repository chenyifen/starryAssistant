# 韩语唤醒词集成测试指南

## 🎯 功能概述

我已经成功为您集成了韩语唤醒词"하이넛지"到Dicio项目中，实现了以下功能：

### ✅ 已完成的功能

1. **韩语唤醒模型集成**
   - 将您训练的`openwakeword_korean_minimal`模型集成到项目中
   - 模型文件已复制到`app/src/withModels/assets/models/openWakeWord/`

2. **外部存储优先级系统** 🆕
   - 支持从外部存储`/storage/emulated/0/Dicio/models/openWakeWord/`加载模型
   - 优先级：外部存储 > Assets > 失败
   - 可以通过推送新模型到外部存储来更新唤醒词

3. **语言自动切换功能**
   - 创建了`LanguageWakeWordManager`来管理语言相关的唤醒词
   - 修改了`LocaleManager`，当语言切换到韩语时自动使用韩语唤醒词
   - 其他语言使用默认的"Hey Nudget"唤醒词

4. **UI设置界面**
   - 更新了唤醒词设置界面，支持显示当前语言对应的唤醒词
   - 韩语时显示"하이넛지"选项，其他语言显示默认选项

## 🧪 测试步骤

### 1. 推送韩语模型到外部存储 🆕

```bash
# 推送韩语唤醒词模型到外部存储（优先级最高）
./push_korean_models.sh
```

这会将模型文件推送到：
- `/storage/emulated/0/Dicio/models/openWakeWord/melspectrogram.tflite`
- `/storage/emulated/0/Dicio/models/openWakeWord/embedding.tflite`
- `/storage/emulated/0/Dicio/models/openWakeWord/wake.tflite`

### 2. 构建和安装应用

```bash
# 设置正确的JAVA_HOME（根据您的系统调整）
export JAVA_HOME=/Users/user/Library/Java/JavaVirtualMachines/ms-17.0.15/Contents/Home

# 构建包含韩语模型的版本
./gradlew assembleWithModelsDebug

# 安装到设备
adb install -r app/build/outputs/apk/withModels/debug/app-withModels-debug.apk
```

### 3. 应用内测试

1. **启动应用**
   ```bash
   adb shell am start -n org.stypox.dicio/.ui.main.MainActivity
   ```

2. **切换到韩语**
   - 进入设置 → 语言 → 选择"한국어"
   - 应用会自动切换界面语言并设置韩语唤醒词
   - **关键**：观察日志中是否显示"📱 Found Korean wake word in external storage"

3. **启用唤醒功能**
   - 进入设置 → 唤醒方法 → 选择"OpenWakeWord offline audio processing"
   - 确认唤醒词设置显示"하이넛지"

4. **测试唤醒功能**
   - 说"하이넛지"来测试唤醒
   - 观察应用是否响应并启动语音识别

### 4. 外部存储优先级测试 🆕

使用专门的测试脚本：
```bash
./test_external_korean_wake_word.sh
```

这个脚本会：
- 验证外部存储中的模型文件
- 测试优先级机制（外部存储 > Assets）
- 提供实时日志监控
- 支持优先级切换测试

### 5. 日志监控

```bash
# 监控外部存储优先级日志 🆕
adb logcat | grep -E "(LanguageWakeWordManager|External.*Korean|Found Korean wake word)"

# 监控唤醒词相关日志
adb logcat | grep -E "(WakeWord|Korean|하이넛지|LocaleManager|LanguageWakeWordManager)"

# 监控OpenWakeWord设备日志
adb logcat -s "OpenWakeWordDevice:D"

# 监控语言管理器日志
adb logcat -s "LocaleManager:D"
```

**关键日志标识：**
- `📱 Found Korean wake word in external storage` - 检测到外部存储模型
- `✅ Korean wake word copied from external storage` - 从外部存储复制成功
- `📦 Found Korean wake word in assets` - 回退到Assets模型
- `📱 Source: /storage/emulated/0/Dicio/models/openWakeWord/wake.tflite` - 确认源路径

### 6. 验证模型文件

```bash
# 检查外部存储中的模型文件 🆕
adb shell ls -la /storage/emulated/0/Dicio/models/openWakeWord/

# 检查应用内部的模型文件
adb shell ls -la /data/data/org.stypox.dicio/files/openWakeWord/

# 检查用户自定义唤醒词文件（应该是从外部存储复制的）
adb shell ls -la /data/data/org.stypox.dicio/files/openWakeWord/userwake.tflite
```

## 🔧 技术实现细节

### 核心组件

1. **LanguageWakeWordManager**
   - 位置：`app/src/main/kotlin/org/stypox/dicio/util/LanguageWakeWordManager.kt`
   - 功能：根据语言自动设置对应的唤醒词模型

2. **LocaleManager (修改)**
   - 位置：`app/src/main/kotlin/org/stypox/dicio/di/LocaleManager.kt`
   - 新增：语言切换时自动调用唤醒词设置

3. **LanguageWakeWordSettings**
   - 位置：`app/src/main/kotlin/org/stypox/dicio/ui/settings/KoreanWakeWordSettings.kt`
   - 功能：显示当前语言对应的唤醒词设置界面

### 模型文件位置

- **Assets中的韩语模型**：`app/src/withModels/assets/models/openWakeWord/`
  - `melspectrogram.tflite` - Mel频谱图提取模型
  - `embedding.tflite` - 特征嵌入模型  
  - `wake.tflite` - 韩语唤醒词检测模型

- **运行时模型位置**：`/data/data/org.stypox.dicio/files/openWakeWord/`
  - `userwake.tflite` - 当前使用的自定义唤醒词模型

## 🐛 故障排除

### 常见问题

1. **唤醒词不响应**
   - 检查麦克风权限是否已授予
   - 确认唤醒功能已启用
   - 查看日志中的置信度分数

2. **语言切换后唤醒词未自动切换**
   - 检查LocaleManager日志
   - 确认LanguageWakeWordManager是否正常工作

3. **模型加载失败**
   - 确认模型文件是否正确复制到assets
   - 检查文件权限和完整性

### 调试命令

```bash
# 完整的唤醒词测试脚本
./test_korean_wake_word.sh

# 手动检查模型文件
adb shell "ls -la /data/data/org.stypox.dicio/files/openWakeWord/ && file /data/data/org.stypox.dicio/files/openWakeWord/*.tflite"

# 重置唤醒词设置
adb shell "rm -f /data/data/org.stypox.dicio/files/openWakeWord/userwake.tflite"
```

## 📊 测试结果验证

成功的测试应该显示：

1. ✅ 语言切换到韩语时，日志显示"Wake word setup successful for language: LANGUAGE_KO"
2. ✅ 唤醒词设置界面显示"하이넛지 (Hi Nutji Korean)"
3. ✅ 说"하이넛지"时应用响应并启动语音识别
4. ✅ 切换回其他语言时自动恢复"Hey Nudget"

## 🚀 下一步优化

如果基本功能正常，可以考虑：

1. **调整检测阈值**：根据实际测试效果调整置信度阈值
2. **添加更多语言**：扩展支持其他语言的自定义唤醒词
3. **性能优化**：监控CPU和内存使用情况
4. **用户体验**：添加唤醒词训练和自定义功能

---

**准备测试时请告诉我，我会协助您监控和调试！** 🎯
