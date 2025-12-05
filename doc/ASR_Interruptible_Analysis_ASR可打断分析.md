# ASR过程中可被打断（唤醒词打断）功能分析与实现方案

## 📋 当前架构分析

### 1. 当前工作流程

```
WakeService (持续监听)
    ↓ 检测到唤醒词
WakeWordCallbackManager.notifyWakeWordDetected()
    ↓
EnhancedFloatingWindowService.onWakeWordDetected()
    ↓
AsrHandler.start() → 创建新的 AudioRecord
    ↓
WakeService 检测到 AsrHandler.isStarted() == true
    ↓
WakeService 暂停 AudioRecord，等待 ASR 停止
    ↓
ASR 停止后，WakeService 恢复监听
```

### 2. 当前代码位置

**WakeService.kt (532-564行)**:
```kotlin
// 🔧 检查 AsrHandler 是否正在运行，如果正在运行则暂停监听
if (com.ai.voice.util.AsrHandler.isStarted()) {
    DebugLogger.logWakeWord(TAG, "⏸️ AsrHandler 正在运行，暂停唤醒词检测...")
    // 暂停 AudioRecord
    if (ar.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
        ar.stop()
    }
    // 等待 AsrHandler 停止
    while (com.ai.voice.util.AsrHandler.isStarted() && listening.get()) {
        Thread.sleep(100)
    }
    // 恢复 AudioRecord
    ar.startRecording()
}
```

**问题**: WakeService 在 ASR 运行时完全暂停，无法检测新的唤醒词，因此无法实现打断功能。

---

## 🎯 实现方案对比

### 方案1: WakeService 继续监听（不推荐）

**思路**: WakeService 在 ASR 运行时继续运行，使用独立的 AudioRecord

**问题**:
- Android 系统可能不支持同时使用两个 AudioRecord（取决于设备）
- 资源浪费（两个录音流）
- 可能产生音频冲突

**结论**: ❌ 不推荐

---

### 方案2: 在 ASR 循环中集成唤醒词检测（推荐⭐）

**思路**: 在 `AsrHandler.doAsr()` 的音频读取循环中，同时进行唤醒词检测

**优点**:
- ✅ 只使用一个 AudioRecord，资源高效
- ✅ 唤醒词检测和 ASR 共享同一音频流，延迟最低
- ✅ 可以实现实时打断
- ✅ 不需要修改 WakeService 的暂停逻辑

**实现步骤**:

1. **修改 AsrHandler.doAsr()**:
   - 在音频读取循环中，将音频数据同时发送给：
     - ASR 处理（现有逻辑）
     - 唤醒词检测（新增）

2. **集成唤醒词检测**:
   - 在 `AsrHandler` 中注入 `WakeDevice` 实例
   - 每读取一帧音频，同时调用 `wakeDevice.processFrame()`
   - 如果检测到唤醒词，立即停止当前 ASR 并触发新的唤醒

3. **处理打断逻辑**:
   - 检测到唤醒词时，调用 `AsrHandler.stop()` 停止当前 ASR
   - 通过 `WakeWordCallbackManager.notifyWakeWordDetected()` 通知新的唤醒
   - `EnhancedFloatingWindowService` 收到通知后启动新的 ASR

**代码结构**:
```kotlin
// AsrHandler.kt
object AsrHandler {
    // 注入 WakeDevice（通过依赖注入或参数传递）
    private var wakeDevice: WakeDevice? = null
    
    fun setWakeDevice(device: WakeDevice) {
        wakeDevice = device
    }
    
    fun doAsr(context: Context) {
        // ... 现有初始化代码 ...
        
        while (isStarted) {
            val bytesRead = audioRecord.read(audioBuffer, 0, bufferSize)
            
            if (bytesRead > 0) {
                // 1. ASR 处理（现有逻辑）
                val samples = FloatArray(bytesRead / 2) { ... }
                samplesChannel.send(samples)
                
                // 2. 唤醒词检测（新增）
                wakeDevice?.let { device ->
                    val wakeWordDetected = device.processFrame(audioBuffer)
                    if (wakeWordDetected) {
                        Log.i(TAG, "🎯 ASR过程中检测到唤醒词，打断当前ASR")
                        // 停止当前 ASR
                        stop(context)
                        // 通知新的唤醒
                        WakeWordCallbackManager.notifyWakeWordDetected()
                        return // 退出循环
                    }
                }
            }
        }
    }
}
```

**注意事项**:
- 需要确保 `WakeDevice` 的 `processFrame()` 方法线程安全
- 唤醒词检测不应该影响 ASR 的性能（可以降低检测频率）
- 需要处理唤醒词检测的防抖（避免频繁触发）

---

### 方案3: 共享 AudioRecord 数据流（备选）

**思路**: WakeService 和 ASR 共享同一个 AudioRecord，通过数据分发实现

**实现**:
- 创建一个音频数据分发器（AudioDataDispatcher）
- WakeService 和 ASR 都订阅这个分发器
- 当 ASR 启动时，WakeService 不停止，而是继续从分发器获取数据

**问题**:
- 需要重构现有架构
- 复杂度较高
- 可能存在数据同步问题

**结论**: ⚠️ 备选方案，如果方案2不可行再考虑

---

## 🚀 推荐实现方案（方案2）

### 详细实现步骤

#### 步骤1: 修改 AsrHandler，添加唤醒词检测支持

```kotlin
// AsrHandler.kt
object AsrHandler {
    private var wakeDevice: WakeDevice? = null
    private var wakeWordDetectionEnabled = false
    
    /**
     * 设置唤醒词检测设备（用于ASR过程中打断）
     */
    fun setWakeDevice(device: WakeDevice?) {
        wakeDevice = device
        wakeWordDetectionEnabled = device != null
        Log.d(TAG, "🔧 唤醒词检测设备已${if (device != null) "设置" else "清除"}")
    }
    
    fun doAsr(context: Context) {
        // ... 现有初始化代码 ...
        
        var wakeWordFrameCount = 0
        val wakeWordCheckInterval = 5 // 每5帧检测一次唤醒词（降低CPU占用）
        
        while (isStarted) {
            val bytesRead = audioRecord.read(audioBuffer, 0, bufferSize)
            
            if (bytesRead > 0) {
                // ASR 处理（现有逻辑）
                val samples = FloatArray(bytesRead / 2) { ... }
                samplesChannel.send(samples)
                
                // 唤醒词检测（新增，降低频率）
                if (wakeWordDetectionEnabled && wakeWordFrameCount % wakeWordCheckInterval == 0) {
                    wakeDevice?.let { device ->
                        // 转换为 ShortArray（唤醒词检测需要的格式）
                        val shortArray = ShortArray(bytesRead / 2)
                        // ... 转换逻辑 ...
                        
                        val wakeWordDetected = device.processFrame(shortArray)
                        if (wakeWordDetected) {
                            Log.i(TAG, "🎯 ASR过程中检测到唤醒词，打断当前ASR")
                            // 停止当前 ASR
                            stop(context)
                            // 通知新的唤醒（延迟一小段时间，确保stop完成）
                            Handler(Looper.getMainLooper()).postDelayed({
                                WakeWordCallbackManager.notifyWakeWordDetected()
                            }, 100)
                            return // 退出循环
                        }
                    }
                }
                wakeWordFrameCount++
            }
        }
    }
}
```

#### 步骤2: 在 EnhancedFloatingWindowService 中设置 WakeDevice

```kotlin
// EnhancedFloatingWindowService.kt
override fun onCreate() {
    // ... 现有代码 ...
    
    // 设置 AsrHandler 的唤醒词检测设备
    // 注意：需要从 WakeService 获取 WakeDevice 实例
    // 可以通过依赖注入或单例模式获取
    val wakeDevice = WakeService.getWakeDevice() // 需要实现这个方法
    AsrHandler.setWakeDevice(wakeDevice)
}

override fun onWakeWordDetected(confidence: Float, wakeWord: String) {
    // ... 现有代码 ...
    
    // 如果 ASR 正在运行，先停止它（打断）
    if (AsrHandler.isStarted()) {
        Log.i(TAG, "🛑 唤醒词打断当前ASR")
        AsrHandler.stop(this)
        // 等待一小段时间确保停止完成
        Handler(Looper.getMainLooper()).postDelayed({
            startVoiceRecognition()
        }, 200)
    } else {
        startVoiceRecognition()
    }
}
```

#### 步骤3: 处理 WakeDevice 的防抖

```kotlin
// AsrHandler.kt
private var lastWakeWordTime = 0L
private val WAKE_WORD_MIN_INTERVAL_MS = 1000L // 最小间隔1秒

fun doAsr(context: Context) {
    // ...
    
    while (isStarted) {
        // ...
        
        if (wakeWordDetected) {
            val now = System.currentTimeMillis()
            if (now - lastWakeWordTime < WAKE_WORD_MIN_INTERVAL_MS) {
                Log.d(TAG, "⏳ 唤醒词检测过于频繁，忽略")
                continue
            }
            lastWakeWordTime = now
            
            // ... 打断逻辑 ...
        }
    }
}
```

---

## ⚠️ 注意事项

### 1. 性能考虑
- 唤醒词检测会增加 CPU 占用，建议降低检测频率（每N帧检测一次）
- 可以考虑在 ASR 的静音超时期间暂停唤醒词检测

### 2. 线程安全
- 确保 `WakeDevice.processFrame()` 线程安全
- `AsrHandler.stop()` 和唤醒词检测的调用需要同步

### 3. 状态管理
- ASR 被打断时，需要清理当前状态（清空 buffer、重置 VAD 等）
- 确保新的 ASR 启动时状态是干净的

### 4. 用户体验
- 打断应该立即响应，延迟应该 < 200ms
- 需要清晰的视觉反馈（UI 状态变化）

---

## 📊 测试场景

1. **基础打断**: ASR 运行中，说唤醒词，应该立即停止当前 ASR 并启动新的
2. **快速连续唤醒**: 短时间内多次唤醒，应该正确处理防抖
3. **静音超时打断**: ASR 静音超时期间，唤醒词应该仍然有效
4. **性能测试**: 确保唤醒词检测不影响 ASR 的实时性

---

## 🔄 迁移计划

1. **阶段1**: 实现方案2的基础功能（唤醒词检测集成）
2. **阶段2**: 添加防抖和性能优化
3. **阶段3**: 完善测试和错误处理
4. **阶段4**: 优化用户体验（UI反馈、延迟优化）

---

## 📝 总结

**推荐方案**: 方案2 - 在 ASR 循环中集成唤醒词检测

**优点**:
- ✅ 资源高效（单 AudioRecord）
- ✅ 延迟最低（共享音频流）
- ✅ 实现相对简单
- ✅ 不需要大幅重构

**关键点**:
- 需要将 `WakeDevice` 注入到 `AsrHandler`
- 需要处理唤醒词检测的频率和防抖
- 需要确保线程安全和状态清理


