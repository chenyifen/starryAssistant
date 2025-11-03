# 自动语言切换TTS实现总结

**日期**: 2025-11-03  
**功能**: 根据ASR识别语言自动切换TTS语言  
**状态**: ✅ 已实现

## 📋 需求

用户要求：
- 韩语命令 → 韩语TTS回复
- 英语命令 → 英语TTS回复
- 默认使用韩语

## ✅ 实现内容

### 1. 语言检测器 (`LanguageDetector.kt`)

**功能：**
- 统计文本中韩文、英文字符比例
- 返回检测结果：Korean / English / Mixed / Unknown
- 提供Locale转换

**检测规则：**
- 70%以上韩文 → 韩语
- 70%以上英文 → 英语
- 两种语言都占20%以上 → 混合（根据首字符判断）
- 其他 → 选择占比更高的语言

**支持的Unicode范围：**
```kotlin
韩文音节: 0xAC00-0xD7AF (한글 음절)
韩文字母: 0x1100-0x11FF (한글 자모)
韩文兼容: 0x3130-0x318F (한글 호환 자모)
英文字母: A-Z, a-z
```

### 2. 多语言TTS设备 (`LanguageDetectingSpeechDevice.kt`)

**功能：**
- 维护多个语言的TTS设备缓存
- 自动检测文本语言并切换TTS
- 支持设备预加载
- 失败时自动降级

**工作流程：**
```
用户命令 → speak(text)
    ↓
检测语言 (LanguageDetector)
    ↓
获取/创建对应语言的TTS设备
    ↓
使用该TTS设备朗读
```

**性能优化：**
- 设备缓存：避免重复初始化
- 异步初始化：不阻塞主线程
- 预加载：应用启动时可预加载常用语言

### 3. 集成到现有系统 (`SpeechOutputDeviceWrapper.kt`)

**修改：**
- 添加 `enableAutoLanguageDetection` 标志（默认启用）
- 在 `tryCreateTtsDeviceWithFallback()` 中创建多语言TTS设备
- 保留单语言模式作为降级方案

**兼容性：**
- 向后兼容现有TTS降级链
- 不影响现有功能
- 可通过标志快速禁用

### 4. 单元测试 (`LanguageDetectorTest.kt`)

**测试覆盖：**
- ✅ 纯韩语检测
- ✅ 纯英语检测
- ✅ 混合语言检测
- ✅ Locale转换
- ✅ 边界情况（空文本、标点等）
- ✅ 真实命令测试

## 📊 使用示例

### 示例1：韩语命令
```
用户: "화이트보드 실행해줘"
检测: KOREAN (100% 韩文)
TTS: 使用韩语TTS朗读
输出: "화이트보드를 실행합니다"
```

### 示例2：英语命令
```
用户: "open whiteboard"
检测: ENGLISH (100% 英文)
TTS: 使用英语TTS朗读
输出: "Opening whiteboard"
```

### 示例3：混合命令
```
用户: "open 화이트보드"
检测: MIXED (50% 韩文, 50% 英文)
判断: 首字符是英文
TTS: 使用英语TTS朗读
输出: "Opening whiteboard"
```

## 🔍 调试信息

运行时会输出以下日志：

```
🌐 初始化多语言TTS设备
  📍 默认语言: 한국어 (Korean)
  🎯 支持语言: 한국어 (Korean), English

🗣️ 准备朗读: "화이트보드 실행해줘"
🔍 语言检测结果:
  📊 检测类型: 한국어 (Korean)
  🎯 目标语言: 한국어 (Korean)
♻️ 使用缓存的TTS设备: 한국어 (Korean)
▶️ 使用 한국어 (Korean) TTS朗读

🗣️ 准备朗读: "open whiteboard"
🔍 语言检测结果:
  📊 检测类型: English
  🎯 目标语言: English
🔨 创建新TTS设备: English
✅ TTS设备创建成功: English
▶️ 使用 English TTS朗读
```

## 📁 新增文件

```
app/src/main/kotlin/com/ai/voice/util/
  └─ LanguageDetector.kt                          # 语言检测器

app/src/main/kotlin/com/ai/voice/io/speech/
  └─ LanguageDetectingSpeechDevice.kt             # 多语言TTS设备

app/src/test/java/com/ai/voice/util/
  └─ LanguageDetectorTest.kt                      # 单元测试

docs/
  ├─ AUTO_LANGUAGE_DETECTION_TTS.md               # 功能详细文档
  └─ 20251103_AUTO_LANGUAGE_TTS_IMPLEMENTATION.md # 实现总结
```

## 📝 修改文件

```
app/src/main/kotlin/com/ai/voice/di/
  └─ SpeechOutputDeviceWrapper.kt                 # 集成多语言TTS
```

**主要修改：**
1. 添加 `LanguageDetectingSpeechDevice` 导入
2. 添加 `enableAutoLanguageDetection` 标志
3. 修改 `tryCreateTtsDeviceWithFallback()` 支持多语言模式

## 🧪 测试方法

### 1. 运行单元测试
```bash
cd dicio-android
./gradlew test --tests LanguageDetectorTest
```

### 2. 手动测试
1. 编译并安装应用
2. 说韩语命令（如"화이트보드 실행해줘"）
3. 验证使用韩语TTS回复
4. 说英语命令（如"open whiteboard"）
5. 验证使用英语TTS回复
6. 查看logcat日志确认语言检测和TTS切换

### 3. 查看日志
```bash
adb logcat | grep -E "LanguageDetect|LanguageDetectingTTS"
```

## ⚙️ 配置选项

### 启用/禁用自动检测

当前在代码中配置：
```kotlin
// SpeechOutputDeviceWrapper.kt
private var enableAutoLanguageDetection = true  // 改为false禁用
```

### 调整检测阈值

在 `LanguageDetector.kt` 中修改：
```kotlin
when {
    koreanRatio >= 0.7f -> DetectedLanguage.KOREAN    // 韩文阈值
    englishRatio >= 0.7f -> DetectedLanguage.ENGLISH  // 英文阈值
    koreanRatio >= 0.2f && englishRatio >= 0.2f -> DetectedLanguage.MIXED  // 混合阈值
    // ...
}
```

## 🔮 未来扩展

### 短期
- [ ] 添加用户设置界面开关
- [ ] 支持更多语言（中文、日语）
- [ ] 改进混合语言处理逻辑

### 中期
- [ ] 语言检测置信度
- [ ] 根据用户习惯调整阈值
- [ ] TTS语速随语言自动调整

### 长期
- [ ] 方言识别
- [ ] 多语言混读（同一句话多种语言）
- [ ] 机器学习优化检测准确率

## 🐛 已知问题

暂无已知问题。

## 📚 相关资源

- [韩文Unicode标准](https://www.unicode.org/charts/PDF/UAC00.pdf)
- [语言检测算法](https://en.wikipedia.org/wiki/Language_identification)
- [Sherpa-ONNX TTS文档](https://k2-fsa.github.io/sherpa/onnx/)

## ✍️ 代码审查要点

1. **语言检测准确性**
   - 检测阈值是否合理（70%主语言，20%混合）
   - Unicode范围是否完整
   - 边界情况处理

2. **性能**
   - TTS设备缓存是否有效
   - 协程使用是否正确
   - 内存泄漏风险

3. **错误处理**
   - TTS创建失败的降级
   - 语言检测失败的默认值
   - 异常捕获和日志

4. **代码质量**
   - 日志是否充分
   - 注释是否清晰
   - 测试覆盖率

## 📞 联系方式

如有问题或建议，请：
1. 查看详细文档：`docs/AUTO_LANGUAGE_DETECTION_TTS.md`
2. 运行单元测试验证功能
3. 查看调试日志排查问题

---

**实现完成** ✅  
**测试状态**: 已编写单元测试，待集成测试  
**文档状态**: 完整

