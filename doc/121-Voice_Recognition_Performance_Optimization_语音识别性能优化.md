# 语音识别性能优化方案

## 🎯 优化目标
让语音识别能够跟随说话人，实现实时响应，整体延迟 < 500ms

## 📊 当前流程分析

### 完整链路
```
用户点击悬浮球
  ↓ (~50ms)
ASR设备初始化
  ↓ (~100-200ms)
AudioRecord创建和启动
  ↓ (立即开始)
音频采集循环 (每16ms一帧)
  ↓
VAD检测语音段
  ↓ (~100ms首次检测)
实时识别 (每200ms)
  ↓ (~50-100ms per recognition)
VAD检测语音结束
  ↓
最终识别
  ↓ (~100-200ms)
技能匹配
  ↓ (~50-150ms)
技能执行
  ↓ (~10-50ms)
TTS播放
```

### 关键性能点

| 阶段 | 当前耗时 | 优化潜力 |
|------|---------|---------|
| 悬浮球点击响应 | ~50ms | ⭐ 低 |
| ASR设备加载 | ~100-200ms | ⭐⭐ 中 |
| AudioRecord启动 | ~50ms | ⭐ 低 |
| VAD首次检测 | ~100ms | ⭐⭐⭐ 高 |
| 实时识别间隔 | 200ms | ⭐⭐⭐⭐ 高 |
| 最终识别 | ~100-200ms | ⭐⭐ 中 |
| 技能匹配 | ~50-150ms | ⭐⭐⭐ 高 |
| 技能执行 | ~10-50ms | ⭐ 低 |

## 🚀 优化策略

### 1. **降低实时识别间隔** (优先级：⭐⭐⭐⭐⭐)

**当前：** 每200ms进行一次部分识别  
**优化：** 降低到100ms或50ms

```kotlin
// 当前
private const val RECOGNITION_INTERVAL_MS = 200L

// 优化
private const val RECOGNITION_INTERVAL_MS = 100L  // 或 50L
```

**影响：**
- ✅ 用户感知延迟降低一半
- ⚠️ CPU使用增加约2倍
- ⚠️ 可能增加部分识别的噪声

**测试建议：**
- 先测试100ms，观察CPU占用
- 如果设备性能足够，可以尝试50ms

---

### 2. **优化VAD参数** (优先级：⭐⭐⭐⭐)

**当前VAD配置：**
```kotlin
min_speech_duration = 0.05s (50ms)   // 最短语音
min_silence_duration = 0.1s (100ms)  // 静音判定
max_speech_duration = 10s            // 最长语音
```

**优化建议：**
```kotlin
min_speech_duration = 0.03s (30ms)   // 更快检测语音开始
min_silence_duration = 0.5s (500ms)  // 避免过早结束
```

**影响：**
- ✅ 语音开始检测更快 (~20ms提升)
- ✅ 减少因停顿导致的中断
- ⚠️ 可能增加误检率

---

### 3. **预加载ASR模型** (优先级：⭐⭐⭐⭐)

**当前：** 每次点击时加载模型  
**优化：** 应用启动时预加载并保持在内存

```kotlin
// 在Application启动时
SherpaOnnxManager.initOfflineRecognizer(context)
SherpaOnnxManager.initVad(context)
```

**影响：**
- ✅ 节省100-200ms的模型加载时间
- ⚠️ 增加约200-300MB常驻内存
- ✅ 首次使用体验大幅提升

---

### 4. **并行处理音频和识别** (优先级：⭐⭐⭐)

**当前：** 音频采集和识别在同一协程  
**优化：** 使用多个协程并行处理

```kotlin
// 协程1: 音频采集 (Dispatchers.IO)
// 协程2: VAD处理 (Dispatchers.Default)  
// 协程3: 实时识别 (Dispatchers.Default)
```

**影响：**
- ✅ 充分利用多核CPU
- ✅ 减少音频处理延迟
- ⚠️ 代码复杂度增加

---

### 5. **优化技能匹配算法** (优先级：⭐⭐⭐)

**当前：** 遍历所有技能进行匹配  
**优化：** 
- 使用优先级队列
- 缓存常用技能
- 并行评分

```kotlin
// 当前: 串行评分
skills.forEach { skill ->
    val score = skill.score(input)
}

// 优化: 并行评分
skills.parallelMap { skill ->
    skill.score(input)
}
```

**影响：**
- ✅ 节省30-50ms匹配时间
- ✅ 对多技能场景提升明显

---

### 6. **减少日志输出** (优先级：⭐⭐)

**当前：** 大量详细日志  
**优化：** 生产环境只记录关键节点

```kotlin
// 只在DEBUG模式输出详细日志
if (BuildConfig.DEBUG) {
    DebugLogger.logAudioProcessing(...)
}
```

**影响：**
- ✅ 减少5-10ms的I/O开销
- ✅ 降低logcat压力

---

## 📝 实施计划

### 阶段1：添加性能追踪 (安全，无风险)
```kotlin
data class PerformanceTracker(
    val clickTime: Long = 0,
    val asrStartTime: Long = 0,
    val firstAudioTime: Long = 0,
    val vadDetectedTime: Long = 0,
    val firstRecognitionTime: Long = 0,
    val finalRecognitionTime: Long = 0,
    val skillMatchTime: Long = 0,
    val skillExecuteTime: Long = 0
)
```

### 阶段2：低风险优化 (先实施这些)
1. ✅ 降低识别间隔到100ms
2. ✅ 调整VAD参数
3. ✅ 减少非必要日志
4. ✅ 预加载模型（可选）

### 阶段3：中等风险优化 (需要测试)
1. 并行音频处理
2. 优化技能匹配
3. 识别间隔进一步降低到50ms

### 阶段4：高级优化 (需要大量测试)
1. 使用更快的模型
2. GPU加速
3. 流式识别优化

---

## 🎯 预期效果

### 优化前
```
点击 → 识别开始: ~300ms
说话 → 首次反馈: ~500ms
说话 → 最终结果: ~2000ms
总延迟: ~2500ms
```

### 优化后（阶段2完成）
```
点击 → 识别开始: ~200ms   (↓100ms)
说话 → 首次反馈: ~250ms   (↓250ms)
说话 → 最终结果: ~1500ms  (↓500ms)
总延迟: ~1800ms             (↓700ms, 28%提升)
```

### 优化后（阶段3完成）
```
点击 → 识别开始: ~100ms   (↓200ms)
说话 → 首次反馈: ~150ms   (↓350ms)
说话 → 最终结果: ~1000ms  (↓1000ms)
总延迟: ~1200ms             (↓1300ms, 52%提升)
```

---

## ⚠️ 注意事项

1. **电池消耗**: 更快的识别意味着更高的CPU使用
2. **准确率**: 过快的识别可能降低准确率
3. **设备兼容性**: 低端设备可能无法支持50ms间隔
4. **内存占用**: 预加载模型会增加200-300MB内存

---

## 🧪 测试指标

### 性能指标
- [ ] 点击到首次反馈 < 300ms
- [ ] 说话到部分识别 < 200ms
- [ ] 整体完成时间 < 1500ms
- [ ] CPU占用 < 30%
- [ ] 内存增加 < 500MB

### 质量指标
- [ ] 识别准确率 > 95%
- [ ] 不出现明显卡顿
- [ ] 电池续航下降 < 10%
- [ ] 低端设备可用

---

## 📊 性能监控代码示例

```kotlin
class VoiceRecognitionPerformanceMonitor {
    private var startTime = 0L
    private val checkpoints = mutableMapOf<String, Long>()
    
    fun start() {
        startTime = System.currentTimeMillis()
    }
    
    fun checkpoint(name: String) {
        val elapsed = System.currentTimeMillis() - startTime
        checkpoints[name] = elapsed
        Log.d("Performance", "[$name] +${elapsed}ms")
    }
    
    fun report() {
        val total = System.currentTimeMillis() - startTime
        Log.d("Performance", "========== 性能报告 ==========")
        checkpoints.forEach { (name, time) ->
            Log.d("Performance", "$name: ${time}ms")
        }
        Log.d("Performance", "总耗时: ${total}ms")
        Log.d("Performance", "===============================")
    }
}
```

## 🔄 使用方式

```kotlin
// 悬浮球点击
val monitor = VoiceRecognitionPerformanceMonitor()
monitor.start()
monitor.checkpoint("点击响应")

// ASR启动
monitor.checkpoint("ASR启动")

// 首次音频
monitor.checkpoint("首帧音频")

// VAD检测
monitor.checkpoint("VAD检测")

// 首次识别
monitor.checkpoint("首次识别")

// 最终识别
monitor.checkpoint("最终识别")

// 技能匹配
monitor.checkpoint("技能匹配")

// 技能执行
monitor.checkpoint("技能执行")

// 生成报告
monitor.report()
```

---

## 🚀 快速开始

1. 先添加性能监控代码
2. 运行测试，收集基准数据
3. 实施阶段2的低风险优化
4. 对比优化前后的性能数据
5. 根据结果决定是否进行阶段3优化

