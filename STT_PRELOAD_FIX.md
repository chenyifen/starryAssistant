# STT预加载修复方案

## 📋 问题描述

### 原始问题
从日志分析发现，唤醒词检测成功后，尝试启动STT录音时失败：

```
01-13 12:47:47.853  3134  3237 D SenseVoiceInputDevice: 🚀 尝试加载并开始监听...
01-13 12:47:47.853  3134  3237 W SenseVoiceInputDevice: ⚠️ SenseVoice未初始化，无法开始监听
01-13 12:47:47.853  3134  3237 D 🎤[WakeService]: STT device start result: false
```

### 根本原因
1. **异步初始化延迟**：`SenseVoiceInputDevice` 在构造函数中使用 `scope.launch` 异步初始化
2. **首次使用延迟**：模型加载需要时间（通常1-3秒）
3. **时序问题**：唤醒词触发时，初始化可能还未完成
4. **用户体验差**：首次唤醒或点击悬浮球时无响应

## ✅ 解决方案

### 方案1：等待初始化完成（已实现）

**文件**: `SenseVoiceInputDevice.kt`

在 `tryLoad()` 方法中添加等待逻辑：

```kotlin
override fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean {
    // 🆕 如果未初始化，等待初始化完成（最多3秒）
    if (!isInitialized.get()) {
        Log.w(TAG, "⚠️ SenseVoice未初始化，等待初始化完成...")
        
        val initSuccess = try {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(3000L) {
                    // 等待初始化完成
                    var attempts = 0
                    while (!isInitialized.get() && attempts < 30) {
                        kotlinx.coroutines.delay(100L)
                        attempts++
                    }
                    isInitialized.get()
                } ?: false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 等待初始化异常: ${e.message}")
            false
        }
        
        if (!initSuccess) {
            Log.e(TAG, "❌ SenseVoice初始化超时或失败，无法开始监听")
            // 触发重新初始化
            scope.launch {
                Log.d(TAG, "🔄 尝试重新初始化...")
                initializeComponents()
            }
            return false
        }
        
        Log.d(TAG, "✅ SenseVoice初始化完成，继续开始监听")
    }
    
    // ... 继续原有逻辑
}
```

**优点**：
- ✅ 确保初始化完成后才开始录音
- ✅ 有超时保护，避免无限等待
- ✅ 失败时自动触发重新初始化
- ✅ 改动最小，风险低

**缺点**：
- ⚠️ 首次使用仍有3秒延迟
- ⚠️ 阻塞调用线程（但有超时）

### 方案2：应用启动时预加载（已实现）

**新增文件**: `SttPreloader.kt`

创建专门的预加载管理器：

```kotlin
@Singleton
class SttPreloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Settings>,
    private val localeManager: LocaleManager
) {
    fun preload() {
        scope.launch {
            // 读取用户设置的输入设备
            val settings = dataStore.data.first()
            val inputDevice = settings.inputDevice
            
            when (inputDevice) {
                INPUT_DEVICE_SENSEVOICE,
                INPUT_DEVICE_UNSET,
                UNRECOGNIZED -> {
                    preloadSenseVoice()
                }
                INPUT_DEVICE_TWO_PASS -> {
                    preloadSenseVoice()
                }
                // ... 其他设备
            }
        }
    }
    
    private fun preloadSenseVoice() {
        // 获取单例，触发初始化
        val instance = SenseVoiceInputDevice.getInstance(context, localeManager)
        // 初始化将在后台进行
    }
}
```

**修改文件**: `App.kt`

在应用启动时调用预加载：

```kotlin
@HiltAndroidApp
class App : Application() {
    
    @Inject
    lateinit var sttPreloader: SttPreloader
    
    override fun onCreate() {
        super.onCreate()
        
        // ... 其他初始化
        
        // 🆕 预加载STT输入设备
        preloadSttDevice()
    }
    
    private fun preloadSttDevice() {
        try {
            Log.d(TAG, "🎯 开始预加载STT输入设备...")
            sttPreloader.preload()
        } catch (e: Exception) {
            Log.e(TAG, "❌ 预加载STT设备失败: ${e.message}", e)
        }
    }
}
```

**优点**：
- ✅ 应用启动时就开始初始化
- ✅ 唤醒词触发时大概率已完成初始化
- ✅ 用户体验最佳，无感知延迟
- ✅ 根据用户设置智能预加载
- ✅ 支持多种输入设备

**缺点**：
- ⚠️ 增加应用启动时间（但在后台异步进行）
- ⚠️ 如果用户不使用语音功能，会浪费资源

## 🎯 最终方案

**双重保险**：同时实现方案1和方案2

1. **应用启动时预加载**（方案2）
   - 大部分情况下，唤醒词触发时已完成初始化
   - 用户体验最佳

2. **等待机制作为后备**（方案1）
   - 如果预加载失败或被中断
   - 首次使用时仍能正常工作
   - 最多等待3秒

## 📊 预期效果

### 修复前
```
唤醒词触发 → 尝试启动STT → 失败（未初始化） → 10秒超时 → 重新启动唤醒
```

### 修复后
```
应用启动 → 后台预加载SenseVoice → 初始化完成
↓
唤醒词触发 → 尝试启动STT → 成功（已初始化） → 开始录音
```

或者（预加载失败的情况）：
```
唤醒词触发 → 尝试启动STT → 等待初始化（最多3秒） → 成功 → 开始录音
```

## 🧪 测试方法

### 1. 使用测试脚本

```bash
./test_stt_preload.sh
```

### 2. 手动测试步骤

1. **安装并启动应用**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n org.stypox.dicio.debug/com.ai.voice.ui.floating.FloatingLauncherActivity
   ```

2. **检查预加载日志**
   ```bash
   adb logcat | grep -E "SttPreloader|SenseVoice初始化"
   ```
   
   预期看到：
   ```
   D SttPreloader: 🚀 开始预加载STT输入设备...
   D SttPreloader: 📋 当前配置的输入设备: INPUT_DEVICE_SENSEVOICE
   D SttPreloader: 🎤 开始预加载SenseVoice...
   D SenseVoiceInputDevice: 🎤 SenseVoice输入设备正在初始化...
   D SenseVoiceInputDevice: ✅ SenseVoice初始化完成，耗时: 1234ms
   D SttPreloader: ✅ SenseVoice初始化完成，已就绪
   ```

3. **测试唤醒词**
   - 说唤醒词
   - 观察是否立即响应
   - 检查日志中是否有"等待初始化"

4. **测试点击悬浮球**
   - 点击悬浮球
   - 观察是否立即开始录音
   - 不应该看到"出现错误"

### 3. 验证日志

**成功的日志模式**：
```
# 应用启动
D SttPreloader: 🚀 开始预加载STT输入设备...
D SenseVoiceInputDevice: 🎤 SenseVoice输入设备正在初始化...
D SenseVoiceInputDevice: ✅ SenseVoice初始化完成，耗时: XXXXms

# 唤醒词触发（几秒后）
D WakeService: 🎯 WAKE WORD DETECTED!
D SenseVoiceInputDevice: 🚀 尝试加载并开始监听...
D SenseVoiceInputDevice: ✅ SenseVoice识别器初始化成功  # 不应该看到"等待初始化"
```

**失败的日志模式**（修复前）：
```
D WakeService: 🎯 WAKE WORD DETECTED!
D SenseVoiceInputDevice: 🚀 尝试加载并开始监听...
W SenseVoiceInputDevice: ⚠️ SenseVoice未初始化，无法开始监听  # ❌ 问题
D WakeService: STT device start result: false
```

## 📝 相关文件

### 修改的文件
- `app/src/main/kotlin/com/ai/voice/io/input/sensevoice/SenseVoiceInputDevice.kt`
  - 添加等待初始化逻辑
  - 改进初始化日志

- `app/src/main/kotlin/com/ai/voice/App.kt`
  - 添加预加载调用

### 新增的文件
- `app/src/main/kotlin/com/ai/voice/io/input/SttPreloader.kt`
  - STT预加载管理器

- `test_stt_preload.sh`
  - 预加载测试脚本

- `STT_PRELOAD_FIX.md`
  - 本文档

## 🔍 技术细节

### 初始化时序

```
App.onCreate()
  ↓
preloadSttDevice()
  ↓
SttPreloader.preload()
  ↓
读取用户设置 (DataStore)
  ↓
根据设置选择设备
  ↓
SenseVoiceInputDevice.getInstance()
  ↓
创建单例 (如果不存在)
  ↓
init { scope.launch { initializeComponents() } }
  ↓
后台加载模型 (1-3秒)
  ↓
isInitialized.set(true)
```

### 等待机制

```kotlin
// 最多等待3秒，每100ms检查一次
withTimeoutOrNull(3000L) {
    var attempts = 0
    while (!isInitialized.get() && attempts < 30) {
        delay(100L)
        attempts++
    }
    isInitialized.get()
}
```

### 依赖注入

使用Hilt进行依赖注入：
- `@Singleton` 确保SttPreloader只有一个实例
- `@Inject` 自动注入依赖
- `@ApplicationContext` 提供Application Context

## 🚀 后续优化建议

1. **添加预加载进度通知**
   - 在通知栏显示"正在初始化语音识别..."
   - 完成后自动消失

2. **智能预加载**
   - 根据使用频率决定是否预加载
   - 低电量时跳过预加载

3. **延迟预加载**
   - 应用启动后延迟5秒再预加载
   - 避免影响应用启动速度

4. **预加载状态UI**
   - 在悬浮球上显示初始化状态
   - 未就绪时点击显示"正在初始化..."

## 📚 参考

- [Android后台任务最佳实践](https://developer.android.com/guide/background)
- [Kotlin协程官方文档](https://kotlinlang.org/docs/coroutines-overview.html)
- [Hilt依赖注入](https://developer.android.com/training/dependency-injection/hilt-android)

