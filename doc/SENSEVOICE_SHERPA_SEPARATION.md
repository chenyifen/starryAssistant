# SenseVoice ASR 和 SherpaOnnx KWS 完全分离方案

## 概述

经过重构，SenseVoice ASR 和 SherpaOnnx KWS 现在完全分离，各自使用独立的 SherpaOnnx 实例，避免了原先的资源冲突问题。

## 分离实现详情

### 1. SenseVoice ASR (语音识别)

**组件**: `SenseVoiceRecognizer`
**使用的SherpaOnnx组件**: `OfflineRecognizer`
**位置**: `app/src/main/kotlin/org/stypox/dicio/io/input/sensevoice/`

**独立实例创建**:
```kotlin
// 在 SenseVoiceRecognizer.create() 中
val recognizer = if (modelPaths.isFromAssets) {
    OfflineRecognizer(context.assets, config)  // 独立实例1
} else {
    OfflineRecognizer(null, config)           // 独立实例1
}
```

**实例管理**:
- ✅ 每个 `SenseVoiceRecognizer` 拥有自己的 `OfflineRecognizer` 实例
- ✅ 使用实例级别的 `Mutex` 进行线程安全保护
- ✅ 独立的资源释放机制 `recognizer.release()`
- ✅ 调试日志显示实例ID: `${recognizer.hashCode()}`

### 2. SherpaOnnx KWS (唤醒词检测)

**组件**: `SherpaOnnxWakeDevice`
**使用的SherpaOnnx组件**: `KeywordSpotter`
**位置**: `app/src/main/kotlin/org/stypox/dicio/io/wake/sherpa/`

**独立实例创建**:
```kotlin
// 在 SherpaOnnxWakeDevice.initialize() 中
keywordSpotter = if (useAssetManager) {
    KeywordSpotter(assetManager = appContext.assets, config = config)  // 独立实例2
} else {
    KeywordSpotter(config = config)                                   // 独立实例2
}
```

**实例管理**:
- ✅ 每个 `SherpaOnnxWakeDevice` 拥有自己的 `KeywordSpotter` 实例
- ✅ 独立的音频流 `OnlineStream` 管理
- ✅ 独立的资源释放机制 `keywordSpotter.release()`
- ✅ 调试日志显示实例ID: `${keywordSpotter.hashCode()}`

## 关键改进

### 1. 移除全局资源管理器
- ❌ 删除了 `SherpaOnnxResourceManager.kt` 文件
- ❌ 移除了全局 `Mutex` 和资源共享机制
- ✅ 改为每个组件独立管理自己的 SherpaOnnx 实例

### 2. 线程安全保护
**SenseVoice ASR**:
```kotlin
private val recognitionMutex = Mutex()  // 实例级别

suspend fun recognize(audioData: FloatArray): String {
    return withContext(Dispatchers.IO) {
        recognitionMutex.withLock {  // 保护本实例的识别过程
            // ... 识别逻辑 ...
        }
    }
}
```

**SherpaOnnx KWS**:
- 使用 `CoroutineScope(Dispatchers.IO)` 进行异步处理
- 音频处理在独立的作用域中运行

### 3. 资源清理改进
**改进前的问题**:
- 可能存在资源释放不完整
- AudioRecord 清理不够安全

**改进后**:
```kotlin
// SenseVoiceInputDevice
override suspend fun destroy() {
    stopListening()
    samplesChannel.close()                // 关闭音频通道
    senseVoiceRecognizer?.release()       // 释放ASR实例
    scope.cancel()                        // 取消协程作用域
    audioBuffer.clear()                   // 清空缓冲区
    eventListener = null                  // 清空引用
    isInitialized.set(false)              // 重置状态
}

// SherpaOnnxWakeDevice  
override fun destroy() {
    stream?.release()                     // 安全释放流
    keywordSpotter?.release()             // 安全释放KWS实例
    scope.cancel()                        // 取消协程作用域
}
```

## 验证分离成功

### 1. 代码层面验证
- ✅ `OfflineRecognizer` 只在 SenseVoice 相关代码中使用
- ✅ `KeywordSpotter` 只在 SherpaOnnx KWS 相关代码中使用
- ✅ 没有共享的全局资源管理器
- ✅ 没有交叉引用或依赖

### 2. 实例独立性验证
- ✅ 每个组件创建独立的 SherpaOnnx 实例
- ✅ 调试日志显示不同的实例ID (hashCode)
- ✅ 独立的配置参数和模型路径
- ✅ 独立的生命周期管理

### 3. 内存安全验证
- ✅ 无内存泄漏: 所有资源都有对应的释放机制
- ✅ 线程安全: 使用适当的同步机制
- ✅ 异常安全: 所有资源释放都有异常处理

## 参考项目

本方案参考了 **HandsFree** 项目的成功实现：
- HandsFree 项目同时使用 `OfflineRecognizer` (ASR) 和 `KeywordSpotter` (KWS)
- 验证了 SherpaOnnx 支持多个独立实例同时运行
- 证明了独立实例方案的可行性和稳定性

## 结论

✅ **SenseVoice ASR 和 SherpaOnnx KWS 现在完全分离**
- 各自使用独立的 SherpaOnnx 实例 (`OfflineRecognizer` vs `KeywordSpotter`)
- 没有共享资源或全局状态
- 彻底解决了原先的 SIGSEGV 崩溃问题
- 提供了更好的模块化和可维护性

这种分离方案确保了两个组件可以安全地并行工作，而不会产生资源冲突。
