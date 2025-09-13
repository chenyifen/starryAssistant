# TTS问题诊断与解决方案

## 问题概述

根据日志分析，Dicio应用遇到了TTS（文本转语音）引擎的问题：

```
2023-01-13 21:04:30.528  AndroidTtsSpeechDevice  E  TTS error: -1
2023-01-13 21:04:55.223  TextToSpeech            W  stop failed: not bound to TTS engine
```

## 错误分析

### 1. TTS Error -1

**错误代码**: `TTS error: -1`  
**对应常量**: `TextToSpeech.ERROR`  
**含义**: TTS引擎初始化完全失败

从源码 `AndroidTtsSpeechDevice.kt` 第49行可以看到：
```kotlin
} else {
    Log.e(TAG, "TTS error: $status")
    handleInitializationError(R.string.android_tts_error)
}
```

### 2. Not Bound to TTS Engine

**错误信息**: `stop failed: not bound to TTS engine`  
**含义**: TTS引擎未成功绑定，导致无法执行停止操作

## 可能原因分析

### 1. 系统TTS引擎问题
- **缺少TTS引擎**: 设备可能没有安装或启用TTS引擎
- **TTS引擎损坏**: 系统TTS引擎可能损坏或配置错误
- **权限问题**: 应用可能缺少访问TTS引擎的权限

### 2. 语言支持问题
从源码可以看到TTS初始化时会设置语言：
```kotlin
val errorCode = setLanguage(locale)
if (errorCode >= 0) { // errors are -1 or -2
    initializedCorrectly = true
} else {
    Log.e(TAG, "Unsupported language: $errorCode")
    handleInitializationError(R.string.android_tts_unsupported_language)
}
```

可能的语言相关问题：
- **不支持的语言**: 当前语言不被TTS引擎支持
- **语言数据缺失**: TTS引擎缺少对应语言的语音数据

### 3. 当前语言设置分析

根据代码分析，当前可能的语言设置：

#### 支持的应用语言（language.proto）
```
LANGUAGE_SYSTEM = 0    // 跟随系统
LANGUAGE_CS = 1        // 捷克语
LANGUAGE_DE = 2        // 德语  
LANGUAGE_EN = 3        // 英语
LANGUAGE_EN_IN = 4     // 印度英语
LANGUAGE_ES = 5        // 西班牙语
LANGUAGE_EL = 6        // 希腊语
LANGUAGE_FR = 7        // 法语
LANGUAGE_IT = 8        // 意大利语
LANGUAGE_PL = 12       // 波兰语
LANGUAGE_RU = 9        // 俄语
LANGUAGE_SL = 10       // 斯洛文尼亚语
LANGUAGE_SV = 13       // 瑞典语
LANGUAGE_UK = 11       // 乌克兰语
```

**注意**: 配置中没有中文和韩语选项，但我们预打包了这些语言的Vosk模型。

#### 语言解析逻辑
```kotlin
private fun getSentencesLocale(language: Language): LocaleUtils.LocaleResolutionResult {
    return try {
        LocaleUtils.resolveSupportedLocale(
            getAvailableLocalesFromLanguage(language),
            Sentences.languages
        )
    } catch (e: LocaleUtils.UnsupportedLocaleException) {
        Log.w(TAG, "Current locale is not supported, defaulting to English", e)
        LocaleUtils.LocaleResolutionResult(
            availableLocale = Locale.ENGLISH,
            supportedLocaleString = "en",
        )
    }
}
```

如果当前语言不支持，会默认回退到英语。

## 诊断步骤

### 1. 检查设备TTS状态
```bash
# 检查默认TTS引擎
adb shell "settings get secure tts_default_synth"

# 检查TTS相关包
adb shell "pm list packages | grep -i tts"

# 检查TTS引擎状态
adb shell "dumpsys activity service TextToSpeechService"
```

### 2. 检查应用语言设置
```bash
# 检查应用数据存储
adb shell "run-as org.stypox.dicio.master ls -la /data/data/org.stypox.dicio.master/files/"

# 检查SharedPreferences或DataStore
adb shell "run-as org.stypox.dicio.master cat /data/data/org.stypox.dicio.master/files/datastore/user_settings.pb"
```

### 3. 检查系统语言
```bash
# 检查系统语言设置
adb shell "getprop ro.product.locale"
adb shell "getprop persist.sys.locale"
```

## 解决方案

### 1. 立即解决方案 - 切换到Toast输出

修改应用默认使用Toast而不是TTS：

```kotlin
// 在SpeechOutputDeviceWrapper.kt中
wrappedSpeechDevice = when (setting) {
    null,
    UNRECOGNIZED,
    SPEECH_OUTPUT_DEVICE_UNSET -> ToastSpeechDevice(context) // 改为Toast
    SPEECH_OUTPUT_DEVICE_ANDROID_TTS -> AndroidTtsSpeechDevice(context, locale)
    SPEECH_OUTPUT_DEVICE_NOTHING -> NothingSpeechDevice()
    SPEECH_OUTPUT_DEVICE_TOAST -> ToastSpeechDevice(context)
    SPEECH_OUTPUT_DEVICE_SNACKBAR -> snackbarSpeechDevice
}
```

### 2. 改进TTS错误处理

增强TTS初始化的错误处理和重试机制：

```kotlin
class AndroidTtsSpeechDevice(private var context: Context, locale: Locale) : SpeechOutputDevice {
    private var retryCount = 0
    private val maxRetries = 3
    
    init {
        initializeTts(locale)
    }
    
    private fun initializeTts(locale: Locale) {
        textToSpeech = TextToSpeech(context) { status: Int ->
            when (status) {
                TextToSpeech.SUCCESS -> {
                    handleSuccessfulInit(locale)
                }
                TextToSpeech.ERROR -> {
                    Log.e(TAG, "TTS initialization failed with ERROR")
                    if (retryCount < maxRetries) {
                        retryCount++
                        Log.i(TAG, "Retrying TTS initialization ($retryCount/$maxRetries)")
                        Handler(Looper.getMainLooper()).postDelayed({
                            initializeTts(locale)
                        }, 1000 * retryCount) // 递增延迟
                    } else {
                        handleInitializationError(R.string.android_tts_error)
                    }
                }
                else -> {
                    Log.e(TAG, "TTS error: $status")
                    handleInitializationError(R.string.android_tts_error)
                }
            }
        }
    }
    
    private fun handleSuccessfulInit(locale: Locale) {
        textToSpeech?.run {
            val errorCode = setLanguage(locale)
            when {
                errorCode >= 0 -> {
                    initializedCorrectly = true
                    setupUtteranceListener()
                }
                errorCode == TextToSpeech.LANG_MISSING_DATA -> {
                    Log.w(TAG, "Language data missing for: $locale")
                    // 尝试使用英语作为后备
                    tryFallbackLanguage()
                }
                errorCode == TextToSpeech.LANG_NOT_SUPPORTED -> {
                    Log.w(TAG, "Language not supported: $locale")
                    tryFallbackLanguage()
                }
                else -> {
                    Log.e(TAG, "Unsupported language: $errorCode")
                    handleInitializationError(R.string.android_tts_unsupported_language)
                }
            }
        }
    }
    
    private fun tryFallbackLanguage() {
        textToSpeech?.run {
            val fallbackCode = setLanguage(Locale.ENGLISH)
            if (fallbackCode >= 0) {
                Log.i(TAG, "Using English as fallback language")
                initializedCorrectly = true
                setupUtteranceListener()
            } else {
                Log.e(TAG, "Even English is not supported")
                handleInitializationError(R.string.android_tts_unsupported_language)
            }
        }
    }
}
```

### 3. 添加TTS可用性检测

在应用启动时检测TTS可用性：

```kotlin
object TtsUtils {
    fun isTtsAvailable(context: Context): Boolean {
        val intent = Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
        val activities = context.packageManager.queryIntentActivities(intent, 0)
        return activities.isNotEmpty()
    }
    
    fun getAvailableTtsEngines(context: Context): List<TextToSpeech.EngineInfo> {
        val tts = TextToSpeech(context, null)
        val engines = tts.engines
        tts.shutdown()
        return engines
    }
}
```

### 4. 用户友好的错误提示

改进错误处理，提供更好的用户体验：

```kotlin
private fun handleInitializationError(@StringRes errorString: Int) {
    // 显示详细的错误信息和解决建议
    val message = when (errorString) {
        R.string.android_tts_error -> {
            context.getString(R.string.android_tts_error) + "\n\n" +
            context.getString(R.string.tts_troubleshooting_tips)
        }
        R.string.android_tts_unsupported_language -> {
            context.getString(R.string.android_tts_unsupported_language) + "\n\n" +
            context.getString(R.string.tts_language_tips)
        }
        else -> context.getString(errorString)
    }
    
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    
    // 自动切换到Toast输出作为后备
    Log.i(TAG, "Switching to Toast output as fallback")
    cleanup()
}
```

### 5. 添加TTS设置快捷方式

提供快速访问系统TTS设置的功能：

```kotlin
fun openTtsSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e(TAG, "Cannot open TTS settings", e)
        Toast.makeText(context, "Please check TTS settings manually", Toast.LENGTH_LONG).show()
    }
}
```

## 当前唤醒词和ASR语言

### 唤醒词设置
根据源码分析：
- **默认唤醒词**: "Hey Dicio"
- **模型文件**: `wake.tflite` (来自 `hey_dicio_v6.0.tflite`)
- **支持自定义**: 用户可以上传自定义唤醒词模型 (`userwake.tflite`)

### ASR语言设置
根据当前配置：
- **预打包语言**: 中文(cn)、英语(en)、韩语(ko)
- **默认语言**: 如果当前语言不支持，会回退到英语
- **语言检测**: 基于应用语言设置和系统语言

## 建议的修复步骤

1. **立即修复**: 将默认语音输出改为Toast，避免TTS错误
2. **增强错误处理**: 实现TTS重试和后备机制
3. **添加语言支持**: 在language.proto中添加中文和韩语选项
4. **用户指导**: 提供TTS设置指导和故障排除提示
5. **测试验证**: 在不同设备和语言环境下测试TTS功能

## 总结

TTS问题主要由于系统TTS引擎初始化失败导致。通过改进错误处理、添加后备机制和提供用户指导，可以显著提升应用的稳定性和用户体验。
