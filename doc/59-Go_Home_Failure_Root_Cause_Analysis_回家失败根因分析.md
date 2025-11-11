# Go Home 测试失败根本原因分析

## 问题概述

通过分析日志，发现**Partial ASR阶段意外触发fallback技能执行**，导致状态转换混乱和用户体验问题。

---

## 🔴 核心问题

### 问题1：Partial阶段意外执行fallback技能

**日志证据：**
```log
01:20:54.741 D 🎤[SenseVoiceRecognizer]: SenseVoice识别结果: "어."
01:20:54.746 D 🎨[VoiceAssistantStateCoordinator]: 💬 New skill output generated
01:20:54.749 D 🎨[VoiceAssistantStateCoordinator]: 🔄 UI state changed: LISTENING → SPEAKING
01:20:54.750 D 🎨[VoiceAssistantStateProvider]: 🗣️ [DEBUG] getSpeechOutput() 返回: '다시 말씀해 주시겠어요?'
```

**问题分析：**
- Partial结果 "어." 只有2个字符，明显不是完整指令
- 但系统仍然触发了fallback技能（text），播放了TTS："다시 말씀해 주시겠어요?"
- 这导致用户正在说话时，系统突然开始播放TTS，打断用户输入

**代码问题定位：**

`SkillEvaluator.kt` 第158-197行：
```kotlin
is InputEvent.Partial -> {
    if (utterance.isNotEmpty()) {
        val result = skillRanker.getBest(skillContext, utterance)
        
        if (result != null) {
            if (score >= 0.5f) {
                evaluateMatchingSkill(listOf(utterance))  // ❌ 问题在这里
            }
        }
    }
}
```

`evaluateMatchingSkill()` 第217-219行：
```kotlin
val (chosenInput, chosenSkill) = try {
    utterances.firstNotNullOfOrNull { ... }
    ?: run {
        Pair(utterances[0], skillRanker.getFallbackSkill(...))  // ❌ 兜底调用fallback
    }
}
```

**问题本质：**
- 当`getBest()`返回null（无匹配技能）时，Partial处理逻辑不应该继续
- 但是调用`evaluateMatchingSkill()`会强制执行fallback技能
- Partial阶段应该**只匹配高分技能**，**不应该执行fallback**

---

### 问题2：阈值不一致

**SkillEvaluator Partial阈值：**
```kotlin
if (score >= 0.5f) {  // 第170行
    evaluateMatchingSkill(...)
}
```

**SkillRanker三轮评估阈值：**
```kotlin
HIGH_THRESHOLD_1 = 0.85f     // 第一轮High
MEDIUM_THRESHOLD_2 = 0.90f   // 第二轮Medium
HIGH_THRESHOLD_2 = 0.80f     // 第二轮High
LOW_THRESHOLD_3 = 0.90f      // 第三轮Low
MEDIUM_THRESHOLD_3 = 0.80f   // 第三轮Medium
HIGH_THRESHOLD_3 = 0.70f     // 第三轮High（最低阈值）
```

**问题：**
- Partial阈值0.5 < SkillRanker最低阈值0.7
- 导致分数在0.5-0.7之间的技能会在Partial阶段被执行
- 但这些技能在Final阶段会被SkillRanker拒绝

**日志证据：**
```log
01:20:42.824 D SkillRanker:     📝 device_control: 0.49382713
01:20:42.825 D SkillRanker: ❌ 所有轮次都未通过阈值检查
01:20:42.826 I AutoTest: 技能执行: text, 结果: 다시 말씀해 주시겠어요?
```
分数0.49低于所有阈值，但仍然执行了fallback技能。

---

### 问题3：状态转换混乱

**连续状态切换日志：**
```log
01:20:54.746 💬 New skill output generated (Partial "어.")
01:20:54.749 🔄 UI state changed: LISTENING → SPEAKING
01:20:54.749 🏁 TTS playback finished
01:20:54.749 🔄 UI state changed: SPEAKING → IDLE
01:20:55.458 💬 New skill output generated (Partial "윈.")
01:20:55.460 🔄 UI state changed: IDLE → LISTENING
01:20:55.460 🔄 UI state changed: LISTENING → SPEAKING
01:20:55.460 🏁 TTS playback finished
01:20:55.460 🔄 UI state changed: SPEAKING → IDLE
```

**问题：**
- 每次Partial更新都触发技能执行和状态切换
- `LISTENING → SPEAKING → IDLE` 快速循环
- 多次看到 `🛡️ 防止状态覆盖` 日志，说明有并发状态更新冲突

**根本原因：**
- Partial阶段不应该触发SPEAKING状态
- 只有Final结果才应该触发完整的技能执行和状态转换

---

## 🔍 第一次唤醒失败分析

**用户指令：** "총나무원으로 이동해줘." (去松树园)

**识别过程：**
1. Partial: "총나원으로." → device_control 分数: 0.33
2. Partial: "송나무원 으로 이동해요." → device_control 分数: 0.33
3. Partial: "총나무원으로 이동해줘." → device_control 分数: 0.49
4. **Final: "총나무원으로 이동해줘." → device_control 分数: 0.49**

**失败原因：**
- device_control技能匹配分数只有0.49
- 低于SkillRanker最低阈值0.70
- 被拒绝后fallback到text技能
- 回复："다시 말씀해 주시겠어요?" (能再说一遍吗？)

**这是技能匹配问题，不是状态转换问题。**

---

## ✅ 第二次唤醒成功分析

**用户指令：** "윈도우 모드로 바꿔죠." (切换到窗口模式)

**识别过程：**
1. Partial: "어." → 无匹配 → **触发fallback技能！**
2. Partial: "윈." → 无匹配 → **触发fallback技能！**
3. Partial: "윈도 모드로." → device_control 分数: 0.79 → **立即执行**
4. Final: "윈도우 모드로 바꿔죠." → 跳过（Partial已执行）

**成功原因：**
- Partial "윈도 모드로." 匹配分数0.79，超过阈值0.7
- Partial立即执行成功，发送window_mode广播
- Final结果跳过重复执行

**但有严重问题：**
- 前两次Partial（"어.", "윈."）触发了fallback技能
- 导致TTS重复播放 "다시 말씀해 주시겠어요?"
- 造成状态混乱和用户体验问题

---

## 🎯 修复方案

### 修复1：Partial阶段不应该调用evaluateMatchingSkill()

**问题代码：**
```kotlin
if (score >= 0.5f) {
    evaluateMatchingSkill(listOf(utterance))  // ❌ 会触发fallback
}
```

**修复方案A（推荐）：** 直接执行匹配技能，跳过fallback逻辑
```kotlin
if (score >= 0.7f) {  // 使用与SkillRanker一致的阈值
    // 🔥 直接执行技能，不调用evaluateMatchingSkill()
    partialExecutionMutex.withLock {
        if (!partialSkillExecuted) {
            partialSkillExecuted = true
            executeSkillDirectly(result, utterance)  // 新方法：不含fallback逻辑
        }
    }
}
```

**修复方案B（保守）：** 在evaluateMatchingSkill()中添加参数控制fallback
```kotlin
private suspend fun evaluateMatchingSkill(
    utterances: List<String>,
    allowFallback: Boolean = true  // 新参数
) {
    val (chosenInput, chosenSkill) = try {
        utterances.firstNotNullOfOrNull { ... }
        ?: run {
            if (allowFallback) {  // 只有Final阶段才允许fallback
                Pair(utterances[0], skillRanker.getFallbackSkill(...))
            } else {
                return  // Partial阶段无匹配则直接返回
            }
        }
    }
}

// Partial调用
evaluateMatchingSkill(listOf(utterance), allowFallback = false)

// Final调用
evaluateMatchingSkill(utterances, allowFallback = true)
```

---

### 修复2：统一阈值

**修改 SkillEvaluator.kt 第170行：**
```kotlin
// 修改前
if (score >= 0.5f) {

// 修改后
if (score >= 0.7f) {  // 与SkillRanker的HIGH_THRESHOLD_3一致
```

---

### 修复3：防止Partial阶段状态混乱

**VoiceAssistantStateCoordinator.kt 第196-225行：**

添加检查，防止Partial阶段的技能输出触发SPEAKING状态：

```kotlin
private fun handleSkillEvaluatorState(interactionLog: InteractionLog) {
    val pendingQuestion = interactionLog.pendingQuestion
    val lastInteraction = interactionLog.interactions.lastOrNull()
    val lastAnswer = lastInteraction?.questionsAnswers?.lastOrNull()?.answer
    
    when {
        pendingQuestion?.skillBeingEvaluated != null -> {
            updateUIState(VoiceAssistantUIState.THINKING, "")
        }
        
        lastAnswer != null -> {
            // 🆕 检查是否在ASR监听阶段
            val isSttListening = sttInputDeviceWrapper.uiState.value is SttState.Listening
            
            if (isSttListening) {
                // 🔥 如果还在监听，说明是Partial触发的fallback
                // 不应该播放TTS，忽略这个输出
                Log.w(TAG, "⚠️ Ignoring skill output during STT listening (Partial fallback)")
                return
            }
            
            // 正常处理Final结果
            val speechOutput = lastAnswer.getSpeechOutput(skillContext)
            if (speechOutput.isNotBlank()) {
                updateUIState(VoiceAssistantUIState.SPEAKING, "SPEAKING")
                setupTTSCallback(speechOutput)
            }
        }
    }
}
```

---

## 📊 影响范围

**受影响的场景：**
1. ✅ 所有需要连续语音输入的场景（如导航、设备控制）
2. ✅ Partial阶段意外播放TTS，打断用户输入
3. ✅ 状态转换混乱，UI显示异常
4. ✅ 日志显示大量防止状态覆盖警告

**不受影响的场景：**
1. ❌ Final结果正常匹配的场景（如"윈도 모드로"）
2. ❌ 高分匹配的短指令（如"停止"、"暂停"）

---

## ✅ 验证方法

### 1. 验证Partial不触发fallback
```bash
# 测试用例：说一半的指令
唤醒 → "가..." （只说一个字）
预期：不应该播放TTS，继续监听
```

### 2. 验证阈值统一
```bash
# 测试用例：中等分数的指令
唤醒 → "总..."（模糊指令）
预期：分数<0.7不执行，等待Final结果
```

### 3. 验证状态转换正常
```bash
# 测试用例：完整指令
唤醒 → "총나무원으로 이동해줘"
预期：IDLE → WAKE_DETECTED → LISTENING → THINKING → SPEAKING → IDLE
不应该出现快速的LISTENING↔SPEAKING循环
```

---

## 🎯 总结

**根本原因：**
1. **Partial阶段调用evaluateMatchingSkill()会强制执行fallback技能**
2. **阈值不一致导致低分技能在Partial阶段被执行**
3. **fallback技能的TTS播放导致状态转换混乱**

**修复优先级：**
1. 🔴 高优先级：Partial阶段禁止fallback（修复方案A或B）
2. 🟡 中优先级：统一阈值为0.7
3. 🟢 低优先级：添加状态检查防护（作为兜底保护）

**预期效果：**
- Partial阶段只在高分匹配时执行，不会触发fallback
- 用户说话时不会被TTS打断
- 状态转换更加清晰和稳定
- 日志中不再出现大量状态覆盖警告

