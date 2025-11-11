# 自动语言检测TTS功能

## 📋 概述

实现了智能语言检测功能，根据ASR识别出的文本自动选择对应语言的TTS进行回复。

**功能特性：**
- 🌐 自动检测韩语/英语
- 🔄 动态切换TTS语言
- 🎯 默认使用韩语
- ⚡ 缓存多语言TTS设备，提高性能

## 🎯 使用场景

### 场景1：纯韩语命令
```
用户：「화이트보드 실행해줘」（打开白板）
系统：检测为韩语 → 使用韩语TTS回复「화이트보드를 실행합니다」
```

### 场景2：纯英语命令
```
用户：「open whiteboard」
系统：检测为英语 → 使用英语TTS回复「Opening whiteboard」
```

### 场景3：混合语言
```
用户：「open 화이트보드」
系统：检测为混合 → 根据首字判断，使用英语TTS
```

## 🏗️ 架构设计

### 核心组件

#### 1. `LanguageDetector` - 语言检测器
位置：`com.ai.voice.util.LanguageDetector`

**功能：**
- 检测文本中韩文、英文字符的比例
- 返回检测结果：Korean / English / Mixed / Unknown
- 提供Locale转换功能

**检测逻辑：**
```kotlin
// 统计字符类型
for (char in text) {
    when {
        isKoreanCharacter(char) -> koreanCount++
        isEnglishCharacter(char) -> englishCount++
    }
}

// 判断主要语言
val koreanRatio = koreanCount / totalCount
val englishRatio = englishCount / totalCount

when {
    koreanRatio >= 0.7f -> KOREAN
    englishRatio >= 0.7f -> ENGLISH
    koreanRatio >= 0.2f && englishRatio >= 0.2f -> MIXED
    else -> 选择占比更高的语言
}
```

**Unicode范围：**
- 韩文音节：`0xAC00-0xD7AF`
- 韩文字母：`0x1100-0x11FF`
- 韩文兼容字母：`0x3130-0x318F`
- 英文字母：`A-Z`, `a-z`

#### 2. `LanguageDetectingSpeechDevice` - 多语言TTS设备
位置：`com.ai.voice.io.speech.LanguageDetectingSpeechDevice`

**功能：**
- 维护多个语言的TTS设备缓存
- 根据文本自动检测语言
- 选择对应的TTS设备朗读
- 支持设备预加载

**工作流程：**
```
speak(text) 调用
    ↓
检测文本语言 (LanguageDetector)
    ↓
根据语言选择Locale (Korean/English)
    ↓
获取或创建对应的TTS设备
    ↓
使用该TTS设备朗读
```

**设备缓存：**
```kotlin
private val ttsDevices = mutableMapOf<Locale, SpeechOutputDevice>()

// 首次使用时创建并缓存
getOrCreateTtsDevice(locale)
```

#### 3. `SpeechOutputDeviceWrapper` - TTS设备包装器
位置：`com.ai.voice.di.SpeechOutputDeviceWrapper`

**功能：**
- 根据设置创建单语言或多语言TTS
- 管理TTS降级链
- 处理TTS初始化失败

**初始化逻辑：**
```kotlin
if (enableAutoLanguageDetection) {
    // 创建多语言TTS设备
    LanguageDetectingSpeechDevice(...)
} else {
    // 创建单语言TTS设备
    createTtsDevice(locale)
}
```

## 🔧 配置选项

### 启用/禁用自动语言检测

在 `SpeechOutputDeviceWrapper.kt` 中：
```kotlin
// 默认启用
private var enableAutoLanguageDetection = true
```

未来可以添加到用户设置中：
```protobuf
message UserSettings {
    bool enable_auto_language_detection = XX;  // 待添加
}
```

## 📊 性能优化

### 1. 设备缓存
首次创建的TTS设备会被缓存，避免重复初始化：
```kotlin
// 第一次：创建新设备
val device = deviceFactory(context, Locale.KOREAN)
ttsDevices[Locale.KOREAN] = device

// 第二次：使用缓存
ttsDevices[Locale.KOREAN]  // 直接返回
```

### 2. 预加载
可以在应用启动时预加载常用语言的TTS：
```kotlin
// 预加载韩语和英语TTS
suspend fun preloadAllLanguages() {
    preloadLanguage(Locale.KOREAN)
    preloadLanguage(Locale.ENGLISH)
}
```

### 3. 异步初始化
TTS设备初始化在协程中异步进行，不阻塞主线程：
```kotlin
scope.launch {
    val device = deviceFactory(context, locale)
    // 初始化完成后缓存
}
```

## 🔍 调试日志

### 语言检测日志
```
🔍 语言检测结果:
  📊 检测类型: 한국어 (Korean)
  🎯 目标语言: 한국어 (Korean)
```

### TTS切换日志
```
🌐 初始化多语言TTS设备
  📍 默认语言: 한국어 (Korean)
  🎯 支持语言: 한국어 (Korean), English

▶️ 使用 English TTS朗读
```

### 设备缓存日志
```
♻️ 使用缓存的TTS设备: English
🔨 创建新TTS设备: 한국어 (Korean)
✅ TTS设备创建成功: 한국어 (Korean)
```

## 🧪 测试场景

### 1. 韩语命令
```kotlin
// 测试韩语识别
@Test
fun testKoreanCommand() {
    val text = "화이트보드 실행해줘"
    val language = LanguageDetector.detectLanguage(text)
    assertEquals(DetectedLanguage.KOREAN, language)
}
```

### 2. 英语命令
```kotlin
// 测试英语识别
@Test
fun testEnglishCommand() {
    val text = "open whiteboard"
    val language = LanguageDetector.detectLanguage(text)
    assertEquals(DetectedLanguage.ENGLISH, language)
}
```

### 3. 混合语言
```kotlin
// 测试混合语言
@Test
fun testMixedLanguage() {
    val text = "open 화이트보드 please"
    val language = LanguageDetector.detectLanguage(text)
    assertEquals(DetectedLanguage.MIXED, language)
}
```

### 4. TTS切换
```kotlin
// 测试TTS自动切换
@Test
fun testTtsSwitching() = runBlocking {
    val device = LanguageDetectingSpeechDevice(...)
    
    // 韩语
    device.speak("안녕하세요")  // 应该使用韩语TTS
    
    // 英语
    device.speak("Hello")  // 应该使用英语TTS
}
```

## 🐛 故障排查

### 问题1：语言检测不准确

**症状：**
- 韩语被识别为英语
- 英语被识别为韩语

**排查：**
```kotlin
// 添加详细日志
Log.d(TAG, "文本分析: 韩文${koreanCount}个, 英文${englishCount}个")
Log.d(TAG, "比例: 韩文${koreanRatio}, 英文${englishRatio}")
```

**解决：**
- 调整检测阈值（当前70%）
- 检查Unicode范围是否完整
- 考虑标点符号的影响

### 问题2：TTS设备创建失败

**症状：**
- 多语言TTS无法初始化
- 降级到Toast显示

**排查：**
```kotlin
// 检查TTS模型是否存在
val modelConfig = TtsModelManager.getTtsModelConfig(context, locale)
if (modelConfig == null) {
    Log.e(TAG, "未找到TTS模型: $locale")
}
```

**解决：**
- 确保对应语言的TTS模型已安装
- 检查TTS降级链配置
- 查看设备可用性日志

### 问题3：TTS切换不及时

**症状：**
- 使用错误语言的TTS朗读
- TTS设备未切换

**排查：**
```kotlin
// 检查设备缓存
Log.d(TAG, "当前TTS设备: ${currentSpeakingDevice}")
Log.d(TAG, "缓存的设备: ${ttsDevices.keys}")
```

**解决：**
- 确保设备正确创建并缓存
- 检查协程调度
- 验证Locale比较逻辑

## 📝 TODO & 未来改进

### 短期（已实现）
- ✅ 语言检测器
- ✅ 多语言TTS设备
- ✅ 自动切换功能
- ✅ 设备缓存

### 中期（待实现）
- ⬜ 添加用户设置开关
- ⬜ 支持更多语言（中文、日语等）
- ⬜ 改进混合语言处理
- ⬜ 添加语言检测置信度

### 长期（规划中）
- ⬜ 根据用户习惯自动调整检测阈值
- ⬜ 支持方言识别
- ⬜ TTS语速根据语言调整
- ⬜ 多语言混读（同一句话用多种语言）

## 🔗 相关文件

### 核心实现
- `/app/src/main/kotlin/com/ai/voice/util/LanguageDetector.kt`
- `/app/src/main/kotlin/com/ai/voice/io/speech/LanguageDetectingSpeechDevice.kt`
- `/app/src/main/kotlin/com/ai/voice/di/SpeechOutputDeviceWrapper.kt`

### ASR相关
- `/app/src/main/kotlin/com/ai/voice/util/AsrTextNormalizer.kt` - ASR文本标准化
- `/app/src/main/kotlin/com/ai/voice/io/input/sensevoice/` - SenseVoice ASR

### TTS相关
- `/app/src/main/kotlin/com/ai/voice/io/speech/SherpaOnnxTtsSpeechDevice.kt`
- `/app/src/main/kotlin/com/ai/voice/io/speech/AndroidTtsSpeechDevice.kt`
- `/app/src/main/kotlin/com/ai/voice/io/speech/TtsModelManager.kt`

## 📖 参考资料

### Unicode标准
- [韩文Unicode区块](https://www.unicode.org/charts/PDF/UAC00.pdf)
- [韩文字母Unicode区块](https://www.unicode.org/charts/PDF/U1100.pdf)

### 语言检测算法
- N-gram based language detection
- Character set detection
- Statistical language identification

---
**创建日期**: 2025-11-03  
**作者**: AI Assistant  
**状态**: ✅ 已实现并测试

