# 音频状态转换规则

## 📊 状态转换矩阵

```
From \ To         │ IDLE │ WAKE_LISTENING │ ASR_RECORDING │ TTS_PLAYING
──────────────────┼──────┼────────────────┼───────────────┼─────────────
IDLE              │  ✓   │       ✓        │      ✓        │     ✗
WAKE_LISTENING    │  ✓   │       ✓        │      ✓        │     ✗
ASR_RECORDING     │  ✓   │       ✗        │      ✓        │     ✓
TTS_PLAYING       │  ✓   │       ✓        │      ✓        │     ✓

✓ = 允许转换
✗ = 禁止转换
```

## 🎯 使用场景

### 1. 手动启动录音（点击悬浮球）
```
IDLE → ASR_RECORDING
```
用户主动点击悬浮球，直接进入录音状态。

### 2. 唤醒词触发
```
IDLE → WAKE_LISTENING → ASR_RECORDING
```
或者（WakeService释放资源后）：
```
WAKE_LISTENING → IDLE → ASR_RECORDING
```

### 3. 多轮对话（需要用户澄清）
```
ASR_RECORDING → TTS_PLAYING → ASR_RECORDING
```

**示例对话**：
```
用户: "打开音乐"
系统: ASR_RECORDING → 识别完成
系统: TTS_PLAYING → "您要打开哪个音乐应用？"
用户: ASR_RECORDING → "网易云音乐"
系统: TTS_PLAYING → "正在打开网易云音乐"
```

### 4. 连续对话
```
TTS_PLAYING → WAKE_LISTENING → ASR_RECORDING
```
TTS播放完成后，自动回到唤醒监听状态，等待下一次唤醒。

### 5. 正常结束流程
```
ASR_RECORDING → TTS_PLAYING → IDLE
```
或
```
ASR_RECORDING → IDLE
```

## 🚫 禁止的转换

### 1. IDLE → TTS_PLAYING ✗
**原因**：不能在空闲状态直接播放TTS，必须先经过ASR识别。

### 2. WAKE_LISTENING → TTS_PLAYING ✗
**原因**：唤醒监听状态不能直接播放TTS，必须先进行ASR识别。

### 3. ASR_RECORDING → WAKE_LISTENING ✗
**原因**：ASR录音中不能直接回到唤醒监听，必须先完成录音（到IDLE或TTS）。

## 🔄 状态转换流程图

### 完整对话流程
```
┌─────────────────────────────────────────────────┐
│                                                 │
│  ┌──────┐  点击悬浮球  ┌──────────────┐         │
│  │ IDLE │─────────────→│ASR_RECORDING │         │
│  └──┬───┘              └──────┬───────┘         │
│     │                         │                 │
│     │ 唤醒词                   │ 识别完成          │
│     ↓                         ↓                 │
│  ┌────────────────┐      ┌────────────┐        │
│  │WAKE_LISTENING  │      │TTS_PLAYING │        │
│  └────────┬───────┘      └─────┬──────┘        │
│           │                    │               │
│           │ 检测到唤醒词         │ 需要澄清        │
│           ↓                    │               │
│      ┌──────────────┐          │               │
│      │ASR_RECORDING │←─────────┘               │
│      └──────────────┘                          │
│                                                 │
└─────────────────────────────────────────────────┘
```

### 多轮对话示例
```
用户点击悬浮球
    ↓
[IDLE] → [ASR_RECORDING]
    ↓ (用户说话)
识别: "打开音乐"
    ↓
[ASR_RECORDING] → [TTS_PLAYING]
    ↓
播放: "您要打开哪个音乐应用？"
    ↓
[TTS_PLAYING] → [ASR_RECORDING]  ← 🆕 允许直接转换
    ↓ (用户回答)
识别: "网易云音乐"
    ↓
[ASR_RECORDING] → [TTS_PLAYING]
    ↓
播放: "正在打开网易云音乐"
    ↓
[TTS_PLAYING] → [IDLE]
```

## 🎨 与UI状态的映射

```
AudioState              → VoiceAssistantUIState
─────────────────────────────────────────────────
IDLE                    → IDLE
WAKE_LISTENING          → IDLE (后台监听，用户无感知)
ASR_RECORDING           → LISTENING (显示"正在听...")
TTS_PLAYING             → SPEAKING (显示"正在说...")
```

## 🔧 实现细节

### 状态转换验证
```kotlin
private fun isValidTransition(from: AudioState, to: AudioState): Boolean {
    return when (from) {
        AudioState.IDLE -> to in setOf(
            AudioState.IDLE,
            AudioState.WAKE_LISTENING,
            AudioState.ASR_RECORDING  // ✅ 用户手动启动
        )
        
        AudioState.WAKE_LISTENING -> to in setOf(
            AudioState.IDLE,
            AudioState.WAKE_LISTENING,
            AudioState.ASR_RECORDING  // ✅ 唤醒词触发
        )
        
        AudioState.ASR_RECORDING -> to in setOf(
            AudioState.IDLE,
            AudioState.ASR_RECORDING,
            AudioState.TTS_PLAYING  // ✅ 识别完成，开始回复
        )
        
        AudioState.TTS_PLAYING -> to in setOf(
            AudioState.IDLE,
            AudioState.WAKE_LISTENING,
            AudioState.ASR_RECORDING,  // ✅ 需要用户澄清
            AudioState.TTS_PLAYING
        )
    }
}
```

### 关键修改
1. **IDLE → ASR_RECORDING**：支持手动启动录音
2. **TTS_PLAYING → ASR_RECORDING**：支持多轮对话和澄清场景

## 📝 设计原则

1. **灵活性**：支持多种交互方式（手动、唤醒词、多轮对话）
2. **安全性**：禁止不合理的状态转换
3. **用户体验**：流畅的对话流程，无需手动干预
4. **可扩展性**：易于添加新的状态和转换规则

## 🚀 未来优化

1. **自动重新监听**：TTS播放完成后自动回到WAKE_LISTENING
2. **超时处理**：ASR录音超时自动回到IDLE
3. **错误恢复**：异常情况下自动重置到IDLE
4. **状态持久化**：应用重启后恢复上次状态

---

**最后更新**：2025-01-18  
**状态**：✅ 已实现并测试通过

