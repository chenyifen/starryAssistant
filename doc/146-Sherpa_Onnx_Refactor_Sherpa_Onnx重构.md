# Sherpa-ONNX 重构说明

## 重构日期
2025-10-16

## 问题描述
之前的实现在协程中异步初始化 Sherpa-ONNX 的 `OfflineRecognizer` 和 `Vad`，导致以下问题：
- `FORTIFY: pthread_mutex_lock called on a destroyed mutex` 崩溃
- Native 层线程安全问题
- 与官方demo的实现方式不一致

## 解决方案
参考官方demo (`SherpaOnnxSimulateStreamingAsr`)，采用单例模式管理 Sherpa-ONNX 组件。

## 核心改动

### 1. 新增文件
**`SherpaOnnxManager.kt`** - 单例管理器
- 负责集中管理 `OfflineRecognizer` 和 `Vad` 的初始化
- 提供线程安全的初始化方法
- 使用 `@Synchronized` 确保线程安全
- 参考官方demo的同步初始化方式

主要方法：
```kotlin
object SherpaOnnxManager {
    fun initOfflineRecognizer(context: Context): Boolean
    fun initVad(assetManager: AssetManager): Boolean
    fun isInitialized(): Boolean
    fun release()
}
```

### 2. 修改文件
**`SherpaOnnxSimulateInputDevice.kt`**
- 移除本地的 `recognizer` 和 `vad` 变量
- 改为使用 `SherpaOnnxManager.recognizer` 和 `SherpaOnnxManager.vad`
- 简化初始化逻辑，在主线程同步调用
- 在 `destroy()` 中不再释放单例资源

### 3. VAD模型位置
- VAD模型文件：`app/src/main/assets/silero_vad.onnx`
- 配置路径：`"silero_vad.onnx"`（直接文件名，与官方demo一致）

## 初始化流程

### 当前实现
```kotlin
init {
    scope.launch {
        initializeComponents()  // 在 Dispatchers.Main 中
    }
}

private suspend fun initializeComponents() = withContext(Dispatchers.Main) {
    val recognizerOk = SherpaOnnxManager.initOfflineRecognizer(appContext)
    val vadOk = SherpaOnnxManager.initVad(appContext.assets)
    // ...
}
```

### 官方demo参考
```kotlin
// MainActivity.onCreate()
SimulateStreamingAsr.initOfflineRecognizer(this.assets, this.application)
SimulateStreamingAsr.initVad(this.assets)
```

## 关键差异对比

| 方面 | 重构前 | 重构后 |
|------|--------|--------|
| 初始化线程 | Dispatchers.IO（协程） | Dispatchers.Main（同步） |
| 实例管理 | 每个InputDevice独立 | 单例统一管理 |
| 生命周期 | 随InputDevice | 应用级单例 |
| 线程安全 | 可能存在竞争 | @Synchronized保护 |

## 优势

1. **稳定性提升**
   - 避免 native 层的 mutex 错误
   - 与官方demo保持一致的实现方式
   - 线程安全保证

2. **资源管理**
   - 单例模式避免重复初始化
   - 统一的资源释放管理
   - 减少内存占用

3. **可维护性**
   - 代码结构更清晰
   - 初始化逻辑集中管理
   - 更容易调试和追踪问题

## 注意事项

1. **生命周期管理**
   - 单例在整个应用生命周期中存在
   - 多个InputDevice实例共享同一个recognizer和vad
   - 需要注意并发访问

2. **资源释放**
   - 不在InputDevice的destroy中释放单例资源
   - 应在Application退出时释放（如需要）

3. **线程要求**
   - 初始化建议在主线程进行
   - 使用时注意线程安全

## 测试建议

1. 测试正常启动和识别流程
2. 测试多次启动/停止识别
3. 测试应用前后台切换
4. 观察是否还有 mutex 相关崩溃

## 参考文档
- 官方demo: `SherpaOnnxSimulateStreamingAsr`
- Sherpa-ONNX官方文档
- 相关issue和崩溃日志

