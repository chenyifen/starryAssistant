# SherpaOnnx TTS 集成状态报告

## 🎯 项目目标
为Dicio语音助手集成SherpaOnnx TTS引擎，支持中文、韩语、英文的离线语音合成功能。

## ✅ 已完成的工作

### 1. 核心架构设计
- ✅ 创建 `SherpaOnnxTtsSpeechDevice` 类
- ✅ 设计多语言TTS支持架构
- ✅ 集成到现有的 `SpeechOutputDeviceWrapper` 系统

### 2. 依赖集成
- ✅ 添加 SherpaOnnx TTS 库依赖 (v1.10.30)
- ✅ 更新 `gradle/libs.versions.toml`
- ✅ 更新 `app/build.gradle.kts`

### 3. 设置界面
- ✅ 添加 `SPEECH_OUTPUT_DEVICE_SHERPA_ONNX_TTS` 枚举值
- ✅ 更新设置界面选项列表
- ✅ 添加中文字符串资源
- ✅ 设为默认TTS引擎

### 4. 语言映射
- ✅ 实现 `mapToSherpaCompatibleLocale()` 方法
- ✅ 支持 cn→zh, ko→ko 语言映射
- ✅ 集成到 `LocaleManager` 语言切换流程

### 5. 模型管理
- ✅ 创建 `download_tts_models.sh` 下载脚本
- ✅ 支持中文、韩语、英文模型自动下载
- ✅ 模型文件结构设计

## 🔧 技术实现细节

### SherpaOnnxTtsSpeechDevice 特性
```kotlin
class SherpaOnnxTtsSpeechDevice(
    private val context: Context,
    private val inputLocale: Locale
) : SpeechOutputDevice
```

**核心功能:**
- 🎵 离线TTS音频生成
- 🔊 AudioTrack音频播放
- 🌐 多语言支持 (中/韩/英)
- ⚡ 异步处理 (Coroutines)
- 🛡️ 错误处理和回退机制

### 支持的模型
| 语言 | 模型类型 | 说话人 | 大小 | 质量 |
|------|----------|--------|------|------|
| 中文 | VITS | 多说话人 | ~50MB | 高 |
| 英文 | Piper | Amy女声 | ~15MB | 中 |
| 韩语 | Kokoro | 单说话人 | ~30MB | 高 |

## 📋 待办事项

### 🔴 高优先级
1. **编译错误修复**
   - [ ] 修复 `SherpaOnnxTtsSpeechDevice` 编译错误
   - [ ] 解决依赖冲突问题
   - [ ] 验证 proto 文件生成

2. **模型文件集成**
   - [ ] 运行 `./download_tts_models.sh` 下载模型
   - [ ] 验证模型文件路径和结构
   - [ ] 测试模型加载功能

3. **基础功能测试**
   - [ ] 测试TTS初始化
   - [ ] 测试音频生成和播放
   - [ ] 测试语言切换

### 🟡 中优先级
4. **性能优化**
   - [ ] 优化模型加载时间
   - [ ] 实现模型缓存机制
   - [ ] 优化内存使用

5. **用户体验**
   - [ ] 添加TTS设置页面
   - [ ] 支持语速调节
   - [ ] 支持说话人选择

6. **错误处理**
   - [ ] 完善错误提示信息
   - [ ] 添加模型下载进度显示
   - [ ] 实现自动重试机制

### 🟢 低优先级
7. **扩展功能**
   - [ ] 支持更多语言模型
   - [ ] 支持自定义模型
   - [ ] 添加音效处理

## 🚀 部署指南

### 环境要求
- Android API 21+
- 至少 200MB 存储空间
- 网络连接 (首次下载模型)

### 构建步骤
```bash
# 1. 下载TTS模型
./download_tts_models.sh

# 2. 构建withModels变体
./withModels.sh

# 3. 安装到设备
adb install app/build/outputs/apk/withModels/debug/app-withModels-debug.apk
```

### 配置说明
1. 打开应用设置
2. 选择 "语音输出方式"
3. 选择 "SherpaOnnx TTS (离线多语言)"
4. 切换应用语言测试TTS

## 🐛 已知问题

### 编译问题
- `SherpaOnnxTtsSpeechDevice` 可能存在导入错误
- Proto文件需要重新生成
- 依赖版本可能需要调整

### 运行时问题
- 模型文件路径可能需要调整
- AudioTrack权限可能需要配置
- 内存使用需要优化

## 📊 测试计划

### 单元测试
- [ ] TTS初始化测试
- [ ] 语言映射测试
- [ ] 音频生成测试

### 集成测试
- [ ] 语言切换测试
- [ ] 设置界面测试
- [ ] 错误处理测试

### 用户测试
- [ ] 中文TTS质量测试
- [ ] 韩语TTS质量测试
- [ ] 英文TTS质量测试
- [ ] 性能基准测试

## 🔗 相关资源

### 文档链接
- [SherpaOnnx TTS官方文档](https://k2-fsa.github.io/sherpa/onnx/tts/)
- [SherpaOnnx Android示例](https://github.com/k2-fsa/sherpa-onnx/tree/master/android)
- [预训练TTS模型](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models)

### 代码文件
- `app/src/main/kotlin/org/stypox/dicio/io/speech/SherpaOnnxTtsSpeechDevice.kt`
- `app/src/main/kotlin/org/stypox/dicio/di/SpeechOutputDeviceWrapper.kt`
- `app/src/main/proto/speech_output_device.proto`
- `download_tts_models.sh`

## 📈 下一步计划

1. **立即执行** (今天)
   - 修复编译错误
   - 下载并测试模型
   - 基础功能验证

2. **短期目标** (本周)
   - 完成基础TTS功能
   - 多语言测试
   - 性能优化

3. **长期目标** (下个版本)
   - 用户界面完善
   - 更多语言支持
   - 高级功能开发

---

**更新时间:** 2025年1月14日  
**状态:** 🟡 开发中 - 需要修复编译错误并完成测试
