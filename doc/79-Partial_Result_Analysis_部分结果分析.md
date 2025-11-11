# Partial识别结果分析报告

## 🔍 问题重新定位

用户指出的核心问题：
- **错误修复方向**：不应该添加 `구.`, `아.`, `화.` 这样的片段到技能配置
- **真正原因**：这些是 **Partial 识别结果**（中间状态），不是完整命令
- **根本问题**：VAD过早判断静音，导致只识别了部分内容

---

## 📊 Partial vs Final 识别分析

### 日志证据1：化이트보드命令（成功）

#### Partial识别过程
```
00:32:39.357  Partial: "화."              ← 只识别出1个字
00:32:39.720  Partial: "<|Speech|>화이트드."  ← 识别出部分
00:32:40.034  Partial: "<|Speech|>화이트보드 ."  ← 继续识别
00:32:40.361  Partial: "화이트보드 실행죠."     ← 完整识别
```

#### Final结果
```
00:32:41.761  Final: "화이트보드 실행해죠."   ← 最终正确结果
✅ 技能执行成功
```

**分析**：
- Partial结果 `화.` 是识别的**早期阶段**
- 系统继续收集音频，最终识别出完整命令
- **`화.` 不应该作为有效命令，而应该等待更多音频**

---

### 日志证据2：Google命令（失败）

#### Partial识别过程
```
00:33:10.034  Partial: "그."              ← 只识别出1个字
00:33:10.340  Partial: "<|Speech|>구을."   ← 识别为"구을"
00:33:12.017  静音超时(1000ms)，停止监听
```

#### Final结果
```
00:33:12.445  Final: "국을 연결해줘."       ← 最终结果（部分正确）
⚠️ 但用户实际说的可能是"구글을 연결해줘"
```

**分析**：
- Partial结果 `그.` → `구을.` 是识别的**中间过程**
- **动态超时1秒过短**，在用户说完"구글"之前就触发了停止
- 识别只得到了 `구을`（发音相似但不完整）

---

### 日志证据3：WiFi命令（失败）

#### Partial识别过程
```
00:34:54.264  Partial: "아."              ← 只识别出1个字
00:35:00.791  Partial: "화."              ← 又识别出1个字
00:35:00.799  静音超时，停止监听
```

#### Final结果
```
❌ 无Final结果
⚠️ 技能执行: text, 结果: 이해하지 못했습니다
```

**分析**：
- 连续出现两个单字识别 `아.` → `화.`
- 这表明识别过程**非常碎片化**
- 可能是音频质量问题或VAD过于敏感

---

## 🎯 真正的问题根源

### 问题1: Partial结果被用于技能匹配

#### 当前代码逻辑
```kotlin
// SkillEvaluator.kt
override suspend fun onPartialInputReceived(event: InputEvent.Partial) {
    val text = event.words
    Log.d(TAG, "🔍 [Partial] 尝试匹配技能: '$text'")
    
    // ❌ 问题：即使是 "화." 这样的片段也会尝试匹配
    val result = skillRanker.getBest(text)
    // ...
}
```

#### 问题表现
- `화.`, `구.`, `아.` 这样的片段被送去技能匹配
- 虽然匹配不到（分数0.0），但浪费了CPU资源
- 更严重的是，可能会触发"继续监听"的逻辑

---

### 问题2: 动态超时过短导致识别中断

#### 当前超时设置（修改前）
```kotlin
private fun getDynamicTimeout(): Long {
    return if (partialText.length >= 3) {
        1000L  // ← 这里！识别出3个字后只给1秒
    } else {
        2000L
    }
}
```

#### 韩语命令特点
```
"구글을 연결해줘" - 8个字符
 └─ 发音时长约 2-3秒

如果识别过程是：
0.5s: "구" (1字)
1.0s: "구을" (2字) 
1.5s: "구을" (识别稳定，length>=3)
      ↓
      触发动态超时：1秒倒计时
      ↓
2.5s: 超时！停止录制
      但用户还在说"글을 연결해줘"的后半部分！
```

**结论**：1秒超时对韩语多音节命令不够

---

### 问题3: VAD判断过于敏感

#### 日志证据
```
00:32:39.357  识别: "화."      (音频长度: 2.336秒)
00:32:39.720  识别: "화이트드."  (音频长度: 2.656秒)
```

**分析**：
- 从 `화.` 到 `화이트드.` 只过了 **363ms**
- 这说明VAD在 `화.` 之后检测到了短暂静音
- 但用户只是换气或停顿，并未说完

#### VAD参数问题
```kotlin
private const val SPEECH_TIMEOUT_MS = 2000L  // 静音超时

// VAD检测逻辑
val silenceDuration = currentTime - lastSpeechTime
if (silenceDuration > getDynamicTimeout()) {
    stopListening()  // ← 过早停止
}
```

---

## ✅ 正确的修复方案

### 方案1: 过滤无意义的Partial结果

```kotlin
override suspend fun onPartialInputReceived(event: InputEvent.Partial) {
    val text = event.words
    
    // 🆕 过滤规则
    if (shouldIgnorePartialResult(text)) {
        Log.d(TAG, "⏭️ [Partial] 跳过无意义片段: '$text'")
        return
    }
    
    Log.d(TAG, "🔍 [Partial] 尝试匹配技能: '$text'")
    // ... 继续处理
}

private fun shouldIgnorePartialResult(text: String): Boolean {
    // 规则1: 长度太短（<3个字符）
    if (text.length < 3) return true
    
    // 规则2: 只包含单个字+标点
    if (text.matches(Regex("^[가-힣]\\.?$"))) return true
    
    // 规则3: 包含识别标记
    if (text.contains("<|Speech|>")) return true
    
    return false
}
```

---

### 方案2: 优化动态超时逻辑（已实施但需调整）

#### 当前修改（已完成）
```kotlin
private const val SPEECH_TIMEOUT_MS = 3000L  // 2秒 → 3秒 ✅

private fun getDynamicTimeout(): Long {
    return if (partialText.length >= 3) {
        1800L  // 1秒 → 1.8秒 ✅
    } else {
        SPEECH_TIMEOUT_MS
    }
}
```

#### 建议进一步优化
```kotlin
private fun getDynamicTimeout(): Long {
    val timeSinceStart = System.currentTimeMillis() - asrStartTime
    
    // 初始缓冲期：给足够时间让识别启动
    if (timeSinceStart < INITIAL_GRACE_PERIOD_MS) {
        return 3000L
    }
    
    // 🆕 考虑识别进度，而不仅仅是长度
    return when {
        partialText.isEmpty() -> 3000L                    // 无识别：3秒
        partialText.length <= 2 -> 2500L                  // 短片段：2.5秒（给更多时间）
        partialText.length <= 5 -> 2000L                  // 短命令：2秒
        hasRecentPartialUpdate() -> 2000L                 // 🆕 最近有更新：2秒
        else -> 1800L                                     // 识别稳定：1.8秒
    }
}

// 🆕 检查最近是否有Partial更新
private var lastPartialUpdateTime = 0L
private fun hasRecentPartialUpdate(): Boolean {
    val timeSinceUpdate = System.currentTimeMillis() - lastPartialUpdateTime
    return timeSinceUpdate < 500L  // 500ms内有更新
}
```

---

### 方案3: 调整VAD参数（可选）

```kotlin
// SherpaOnnx VAD配置
private fun createVadConfig(): VadModelConfig {
    return VadModelConfig(
        sileroVad = SileroVadModelConfig(
            model = vadModelPath,
            threshold = 0.5f,           // 降低阈值，减少误判
            minSilenceDuration = 0.5f,  // 增加静音判断时长（500ms）
            minSpeechDuration = 0.3f,   // 减少语音最小时长
            maxSpeechDuration = 15.0f   // 增加最大时长
        )
    )
}
```

---

## 📋 修复清单

### 立即移除的无效变体
```yaml
# ❌ 不应该添加这些
google:
  - 구      # 单字
  - 구.     # 单字+标点
  - 아      # 单字
  - 아.     # 单字+标点

wifi_connect:
  - 화      # 单字
  - 화.     # 单字+标点
  - 아      # 单字

red_pen:
  - 간      # 单字
  - 간.     # 单字+标点

whiteboard:
  - 화보.   # 带句号的不应该作为命令
```

### ✅ 应该保留的变体
```yaml
google:
  - 구을          # 这是真实的误识别（"구글"→"구을"）
  - 구을 연결해줘
  - 국을 연결해줘   # 这也是真实的误识别（"구글"→"국"）

whiteboard:
  - 화보           # 简称（不带句号）
  - 화보 실행해줘

red_pen:
  - 빨간색 팬      # 同音字（팬 vs 펜）
```

---

## 🎯 总结

### 核心问题
1. **Partial结果不应该作为命令变体**
   - `구.`, `화.`, `아.` 是识别的中间状态
   - 应该过滤掉，而不是添加到配置

2. **动态超时需要更智能**
   - 不能仅根据长度判断
   - 需要考虑识别进度和更新频率

3. **VAD参数需要针对韩语优化**
   - 韩语多音节，需要更长的容错时间
   - 减少对短暂停顿的敏感度

### 正确的方向
- ✅ 添加真实的误识别变体（如 `구을`, `국을`, `화보`）
- ✅ 添加同音字变体（如 `팬` vs `펜`）
- ❌ 不添加Partial片段（如 `구.`, `화.`）
- ✅ 优化VAD和超时参数，而不是依赖词汇库补丁

---

**报告日期**: 2025-10-19  
**分析结论**: 需要从根本上优化ASR的状态转换和超时机制，而不是简单地添加词汇变体

