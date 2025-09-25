# SenseVoice SIGSEGV 崩溃问题解决方案

## 问题分析

### 原始问题
```
01-13 12:59:09.264  3406  3440 F libc    : Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0 in tid 3440 (DefaultDispatch), pid 3406 (ox.dicio.master)
```

### 根本原因
项目同时使用了两个SherpaOnnx组件：
1. **SherpaOnnxWakeDevice** - 使用 `KeywordSpotter` 进行唤醒词检测
2. **SenseVoiceInputDevice** - 使用 `OfflineRecognizer` 进行语音识别

这导致了native资源冲突和内存访问冲突。

## 解决方案

### 方案选择：独立多实例
基于HandsFree项目的成功实践，选择独立多实例方案：
- HandsFree同时使用`KwsManager`和`AsrManager`
- 每个组件创建独立的SherpaOnnx实例
- 避免全局资源锁带来的复杂性

### 实现要点

#### 1. 独立实例创建
```kotlin
// 唤醒词检测器 - 独立的KeywordSpotter实例
val keywordSpotter = KeywordSpotter(assetManager, config)

// 语音识别器 - 独立的OfflineRecognizer实例  
val recognizer = OfflineRecognizer(assetManager, config)
```

#### 2. 实例级别的线程安全
```kotlin
class SenseVoiceRecognizer {
    // 每个实例有自己的互斥锁
    private val recognitionMutex = Mutex()
    
    suspend fun recognize(audioData: FloatArray): String {
        return recognitionMutex.withLock {
            // 安全的识别操作
        }
    }
}
```

#### 3. 调试支持
添加实例ID调试信息确保实例独立：
```kotlin
Log.d(TAG, "🔗 OfflineRecognizer实例ID: ${recognizer.hashCode()}")
Log.d(TAG, "🔗 KeywordSpotter实例ID: ${keywordSpotter.hashCode()}")
```

### 关键改进

#### 1. 移除复杂的全局资源管理
- 删除 `SherpaOnnxResourceManager`
- 避免全局锁导致的性能问题

#### 2. 简化并发处理
- 移除过度复杂的`ConcurrentLinkedQueue`
- 保持简单的`ArrayList`音频缓冲区
- 确保单线程音频处理

#### 3. 资源生命周期管理
```kotlin
// 创建独立流，避免跨实例共享
val stream = recognizer.createStream()
try {
    // 使用流进行识别
} finally {
    // 确保释放流资源
    stream.release()
}
```

## 验证方法

### 1. 日志监控
查看以下日志确认独立实例：
```
SenseVoiceRecognizer: 🔗 实例ID: xxxxxxxx
SherpaOnnxWakeDevice: 🔗 KeywordSpotter实例ID: yyyyyyyy
```

### 2. 功能测试
1. 启动应用 - 确认两个组件都能正常初始化
2. 唤醒词检测 - 说"你好小布"触发唤醒
3. 语音识别 - 点击麦克风按钮进行识别
4. 同时测试 - 确认两个功能可以独立工作

### 3. 稳定性测试
- 长时间运行不崩溃
- 多次切换唤醒/识别模式
- 内存使用稳定

## 参考实现

基于 `/Users/user/AndroidStudioProjects/HandsFree/` 项目：
- `KwsManager.kt` - 唤醒词检测管理
- `AsrManager.kt` - 语音识别管理  
- `VoiceEngine.kt` - 协调两个组件

## 结论

通过使用独立的SherpaOnnx实例，而不是全局资源同步，成功解决了SIGSEGV崩溃问题。这种方案：
- ✅ 简单可靠
- ✅ 性能良好
- ✅ 易于维护
- ✅ 有成功先例（HandsFree）

修复后的系统应该能够稳定运行，同时支持唤醒词检测和SenseVoice语音识别功能。
