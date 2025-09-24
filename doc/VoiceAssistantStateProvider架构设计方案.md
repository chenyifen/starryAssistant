# VoiceAssistantStateProvider 架构设计方案

## 1. 设计目标

### 1.1 核心理念
- **UI与服务层解耦**：通过统一的状态提供者，让UI层只关注状态变化，不直接与服务层交互
- **多UI实例支持**：支持多个不同样式的悬浮球同时运行，共享相同的状态
- **简化复杂性**：使用极简的数据结构，避免过度设计
- **易于扩展**：新增UI类型或技能结果类型都很容易

### 1.2 架构优势
- **统一状态管理**：所有语音助手状态通过`VoiceAssistantStateProvider`统一管理
- **自动状态同步**：多个UI实例自动接收状态更新
- **便捷状态访问**：UI层可以随时获取当前状态，无需复杂的回调机制
- **技能结果统一**：所有技能执行结果使用统一的`SimpleResult`结构

## 2. 核心组件设计

### 2.1 VoiceAssistantFullState (完整状态)

```kotlin
/**
 * 语音助手完整状态 - 极简版
 */
data class VoiceAssistantFullState(
    // 基础状态
    val uiState: VoiceAssistantUIState,
    val displayText: String,
    val confidence: Float,
    val timestamp: Long,
    
    // 实时文本
    val asrText: String,           // 当前ASR识别文本
    val ttsText: String,           // 当前TTS播放文本
    
    // 技能结果 - 只保留最核心的
    val result: SimpleResult?
) {
    companion object {
        val IDLE = VoiceAssistantFullState(
            uiState = VoiceAssistantUIState.IDLE,
            displayText = "",
            confidence = 0f,
            timestamp = System.currentTimeMillis(),
            asrText = "",
            ttsText = "",
            result = null
        )
    }
}
```

**设计要点**：
- 包含所有UI需要的核心信息
- 结构简单，易于理解和使用
- 支持实时文本更新（ASR/TTS）
- 统一的技能结果表示

### 2.2 SimpleResult (简单结果)

```kotlin
/**
 * 简单结果 - 只包含必要信息
 */
data class SimpleResult(
    val title: String,              // 主标题
    val content: String,            // 内容文本
    val type: ResultType,           // 结果类型
    val success: Boolean,           // 是否成功
    val data: Map<String, String> = emptyMap()  // 额外数据（键值对）
)

/**
 * 结果类型 - 只保留主要分类
 */
enum class ResultType {
    INFO,       // 信息类（天气、新闻、知识等）
    ACTION,     // 操作类（打开应用、控制设备等）
    CALC,       // 计算类
    ERROR       // 错误类
}
```

**设计要点**：
- 极简结构，适配所有技能类型
- 使用枚举分类，便于UI差异化处理
- 支持额外数据传递
- 明确成功/失败状态

### 2.3 VoiceAssistantStateProvider (状态提供者)

```kotlin
/**
 * 语音助手状态提供者 - 极简版
 */
@Singleton
class VoiceAssistantStateProvider @Inject constructor(
    private val stateCoordinator: VoiceAssistantStateCoordinator
) {
    
    companion object {
        @Volatile
        private var INSTANCE: VoiceAssistantStateProvider? = null
        
        fun getInstance(): VoiceAssistantStateProvider {
            return INSTANCE ?: throw IllegalStateException("VoiceAssistantStateProvider not initialized")
        }
        
        internal fun initialize(instance: VoiceAssistantStateProvider) {
            INSTANCE = instance
        }
    }
    
    // 当前状态
    private var _currentState = VoiceAssistantFullState.IDLE
    
    // 状态监听器
    private val listeners = mutableSetOf<(VoiceAssistantFullState) -> Unit>()
    
    // 核心方法
    fun getCurrentState(): VoiceAssistantFullState
    fun addListener(listener: (VoiceAssistantFullState) -> Unit)
    fun removeListener(listener: (VoiceAssistantFullState) -> Unit)
    fun setResult(result: SimpleResult)
    fun setASRText(text: String)
    fun setTTSText(text: String)
    fun clearResult()
}
```

**设计要点**：
- 单例模式，全局访问
- 简单的监听器机制
- 与现有`VoiceAssistantStateCoordinator`集成
- 提供便捷的状态更新方法

### 2.4 BaseFloatingOrb (悬浮球基类)

```kotlin
/**
 * 悬浮球基类 - 极简版
 */
abstract class BaseFloatingOrb(protected val context: Context) {
    
    protected val stateProvider = VoiceAssistantStateProvider.getInstance()
    protected var currentState: VoiceAssistantFullState = stateProvider.getCurrentState()
    
    init {
        // 监听状态变化
        stateProvider.addListener { newState ->
            val oldState = currentState
            currentState = newState
            onStateChanged(newState, oldState)
        }
    }
    
    /**
     * 状态变化回调 - 子类实现
     */
    protected abstract fun onStateChanged(
        newState: VoiceAssistantFullState,
        oldState: VoiceAssistantFullState
    )
    
    // 便捷方法
    protected fun getCurrentResult(): SimpleResult?
    protected fun getCurrentDisplayText(): String
    protected fun getCurrentASRText(): String
    protected fun getCurrentTTSText(): String
    
    abstract fun show()
    abstract fun hide()
    open fun cleanup()
}
```

**设计要点**：
- 自动注册状态监听
- 提供便捷的状态访问方法
- 子类只需实现`onStateChanged`方法
- 统一的生命周期管理

## 3. 架构流程图

```
┌─────────────────────┐    ┌──────────────────────┐    ┌─────────────────────┐
│   WakeService       │    │  SttInputDevice      │    │   SkillEvaluator    │
│   (唤醒词检测)       │    │  (语音识别)          │    │   (技能处理)        │
└──────────┬──────────┘    └──────────┬───────────┘    └──────────┬──────────┘
           │                          │                           │
           └──────────────────────────┼───────────────────────────┘
                                      │
                              ┌───────▼────────┐
                              │ StateCoordinator│
                              │ (状态协调器)     │
                              └───────┬────────┘
                                      │
                              ┌───────▼────────┐
                              │ StateProvider  │
                              │ (状态提供者)    │
                              └───────┬────────┘
                                      │
                    ┌─────────────────┼─────────────────┐
                    │                 │                 │
            ┌───────▼────────┐ ┌──────▼──────┐ ┌───────▼────────┐
            │ FloatingOrb1   │ │ FloatingOrb2│ │ FloatingOrb3   │
            │ (经典Lottie)   │ │ (简约圆形)  │ │ (自定义样式)   │
            └────────────────┘ └─────────────┘ └────────────────┘
```

## 4. 数据流向

### 4.1 状态更新流程
1. **服务层事件** → `VoiceAssistantStateCoordinator`
2. **状态协调** → `VoiceAssistantStateProvider`
3. **状态广播** → 所有注册的悬浮球实例
4. **UI更新** → 各悬浮球根据状态更新显示

### 4.2 技能结果流程
1. **技能执行** → 生成`SimpleResult`
2. **结果设置** → `VoiceAssistantStateProvider.setResult()`
3. **状态更新** → 更新`VoiceAssistantFullState`
4. **UI响应** → 悬浮球显示结果

## 5. 使用示例

### 5.1 创建悬浮球
```kotlin
// 创建经典Lottie悬浮球
val classicOrb = ClassicLottieFloatingOrb(context, Pair(100f, 100f))
classicOrb.show()

// 创建简约圆形悬浮球
val minimalOrb = MinimalCircleFloatingOrb(context, Pair(300f, 100f))
minimalOrb.show()

// 两个悬浮球会自动同步显示相同的语音助手状态
```

### 5.2 设置技能结果
```kotlin
// 天气查询结果
val weatherResult = SimpleResultBuilder.weather("北京", 25, "晴天")
VoiceAssistantStateProvider.getInstance().setResult(weatherResult)

// 应用操作结果
val appResult = SimpleResultBuilder.appAction("微信", "打开", true)
VoiceAssistantStateProvider.getInstance().setResult(appResult)

// 所有悬浮球会自动显示这些结果
```

### 5.3 实现自定义悬浮球
```kotlin
class CustomFloatingOrb(context: Context) : BaseFloatingOrb(context) {
    
    override fun onStateChanged(
        newState: VoiceAssistantFullState,
        oldState: VoiceAssistantFullState
    ) {
        // 根据状态更新UI
        when (newState.uiState) {
            VoiceAssistantUIState.LISTENING -> showListeningUI()
            VoiceAssistantUIState.SPEAKING -> showSpeakingUI()
            // ...
        }
        
        // 处理技能结果
        newState.result?.let { result ->
            showResult(result)
        }
    }
    
    override fun show() { /* 显示逻辑 */ }
    override fun hide() { /* 隐藏逻辑 */ }
}
```

## 6. 实施优势

### 6.1 开发效率
- **新增悬浮球样式**：只需继承`BaseFloatingOrb`，实现`onStateChanged`方法
- **新增技能结果**：只需在`SimpleResultBuilder`中添加构建方法
- **状态访问**：随时通过`VoiceAssistantStateProvider.getInstance()`获取状态

### 6.2 维护性
- **代码简洁**：核心逻辑集中在状态提供者中
- **职责清晰**：UI层只负责显示，服务层只负责业务逻辑
- **易于调试**：状态变化有统一的入口和出口

### 6.3 扩展性
- **多UI支持**：可以同时运行任意数量的悬浮球
- **技能扩展**：新技能结果可以无缝集成
- **平台扩展**：架构可以扩展到其他UI形式（通知栏、桌面小部件等）

## 7. 注意事项

### 7.1 性能考虑
- 状态监听器数量不宜过多（建议<10个）
- 状态更新频率控制（避免过于频繁的UI刷新）
- 及时清理不再使用的监听器

### 7.2 线程安全
- `VoiceAssistantStateProvider`使用主线程更新状态
- UI更新操作确保在主线程执行
- 避免在状态回调中执行耗时操作

### 7.3 内存管理
- 悬浮球销毁时及时调用`cleanup()`
- 避免持有Context的强引用导致内存泄漏
- 合理控制状态历史记录的数量

## 8. 完整实施计划

### 8.1 VoiceAssistantStateProvider核心功能完善

#### 8.1.1 ASR实时文本监听 (state_provider_core_1)
```kotlin
// 监听STT设备的实时输出
scope.launch {
    sttInputDeviceWrapper.realtimeResults.collect { partialResult ->
        updateState(asrText = partialResult)
    }
}
```

#### 8.1.2 TTS文本和播放进度监听 (state_provider_core_2)
```kotlin
// 监听TTS播放状态和进度
scope.launch {
    speechOutputDeviceWrapper.playbackProgress.collect { progress ->
        updateState(ttsText = progress.currentText)
    }
}
```

#### 8.1.3 技能结果处理和转换 (state_provider_core_3)
```kotlin
// 将SkillEvaluator的输出转换为SimpleResult
private fun convertSkillOutputToSimpleResult(skillOutput: SkillOutput): SimpleResult {
    return when (skillOutput.skillId) {
        "weather" -> SimpleResultBuilder.weather(...)
        "app_control" -> SimpleResultBuilder.appAction(...)
        "calculation" -> SimpleResultBuilder.calculation(...)
        else -> SimpleResultBuilder.fromSkillOutput(...)
    }
}
```

#### 8.1.4 会话历史和上下文管理 (state_provider_core_4)
```kotlin
data class ConversationMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long,
    val confidence: Float = 0f
)

// 在VoiceAssistantFullState中添加
val conversationHistory: List<ConversationMessage>
```

### 8.2 完整语音流程实现

#### 8.2.1 唤醒流程 (voice_flow_1)
```
WakeService检测到唤醒词 → WakeWordCallback → VoiceAssistantStateCoordinator → VoiceAssistantStateProvider
状态变化: IDLE → WAKE_DETECTED → LISTENING
```

#### 8.2.2 ASR流程 (voice_flow_2)
```
STT设备开始监听 → 实时部分结果 → 最终结果 → VoiceAssistantStateProvider
状态变化: LISTENING → (实时更新asrText) → THINKING
```

#### 8.2.3 技能处理流程 (voice_flow_3)
```
SkillEvaluator处理输入 → 匹配技能 → 执行技能 → 生成结果 → VoiceAssistantStateProvider
状态变化: THINKING → (设置result) → SPEAKING
```

#### 8.2.4 TTS流程 (voice_flow_4)
```
TTS开始播放 → 播放进度更新 → 播放完成 → VoiceAssistantStateProvider
状态变化: SPEAKING → (更新ttsText) → IDLE
```

### 8.3 UI适配改造

#### 8.3.1 DraggableFloatingOrb改造 (ui_adaptation_1)
```kotlin
class DraggableFloatingOrb {
    private val stateProvider = VoiceAssistantStateProvider.getInstance()
    
    init {
        stateProvider.addListener { state ->
            updateUI(state)
        }
    }
    
    private fun updateUI(state: VoiceAssistantFullState) {
        // 根据state.uiState更新动画
        // 根据state.result显示技能结果
        // 根据state.asrText显示实时识别
    }
}
```

#### 8.3.2 LottieAnimationController改造 (ui_adaptation_2)
```kotlin
fun updateFromState(state: VoiceAssistantFullState) {
    when (state.uiState) {
        VoiceAssistantUIState.IDLE -> setIdle()
        VoiceAssistantUIState.LISTENING -> setActive("LISTENING")
        VoiceAssistantUIState.THINKING -> setLoading()
        VoiceAssistantUIState.SPEAKING -> setActive("SPEAKING")
        VoiceAssistantUIState.ERROR -> setActive("ERROR")
    }
}
```

### 8.4 技能集成

#### 8.4.1 现有技能集成 (skill_integration_1)
- 天气技能 → SimpleResultBuilder.weather()
- 时间技能 → SimpleResultBuilder.time()
- 计算技能 → SimpleResultBuilder.calculation()

#### 8.4.2 应用控制技能集成 (skill_integration_2)
- 打开应用 → SimpleResultBuilder.appAction()
- 设备控制 → SimpleResultBuilder.deviceControl()
- 音乐控制 → SimpleResultBuilder.musicControl()

### 8.5 多轮对话支持

#### 8.5.1 会话状态管理 (multi_turn_1)
```kotlin
data class ConversationSession(
    val sessionId: String,
    val startTime: Long,
    val isActive: Boolean,
    val context: Map<String, Any>
)
```

#### 8.5.2 对话历史管理 (multi_turn_2)
```kotlin
// 在VoiceAssistantStateProvider中
private val conversationHistory = mutableListOf<ConversationMessage>()

fun addUserMessage(text: String, confidence: Float) {
    conversationHistory.add(ConversationMessage(text, true, System.currentTimeMillis(), confidence))
    updateState(conversationHistory = conversationHistory.toList())
}
```

### 8.6 错误处理

#### 8.6.1 完整错误处理 (error_handling_1)
```kotlin
// 各个环节的错误状态传递
- WakeService错误 → ERROR状态
- STT设备错误 → ERROR状态 + 错误信息
- 技能执行错误 → SimpleResult(success=false)
- TTS播放错误 → ERROR状态
```

### 8.7 测试验证

#### 8.7.1 端到端测试 (testing_1)
1. 唤醒词检测 → UI状态变化
2. 语音识别 → 实时文本显示
3. 技能处理 → 结果显示
4. 语音回复 → TTS文本显示

#### 8.7.2 多轮对话测试 (testing_2)
1. 连续多次对话
2. 上下文保持验证
3. 会话历史记录

#### 8.7.3 错误恢复测试 (testing_3)
1. 网络错误恢复
2. 设备错误恢复
3. 技能执行失败处理

## 9. 实施优先级

### 第一阶段：核心功能完善
- ✅ VoiceAssistantFullState (已完成)
- ✅ SimpleResult & ResultType (已完成)
- ✅ VoiceAssistantStateProvider基础 (已完成)
- 🔄 完善StateProvider核心功能 (state_provider_core_1-4)

### 第二阶段：语音流程集成
- 🔄 完整语音流程实现 (voice_flow_1-4)
- 🔄 UI适配改造 (ui_adaptation_1-3)

### 第三阶段：技能和多轮对话
- 🔄 技能集成 (skill_integration_1-2)
- 🔄 多轮对话支持 (multi_turn_1-2)

### 第四阶段：错误处理和测试
- 🔄 错误处理 (error_handling_1)
- 🔄 完整测试验证 (testing_1-3)

## 10. 总结

这套完整的架构设计通过`VoiceAssistantStateProvider`实现了：

1. **完整的语音助手流程管理** - 从唤醒到回复的全链路状态管理
2. **与所有模型的完整交互** - WakeService、STT、SkillEvaluator、TTS的统一集成
3. **状态驱动的UI更新** - UI组件只需订阅状态变化，无需关心业务逻辑
4. **多轮对话和上下文管理** - 支持复杂的对话场景
5. **统一的错误处理机制** - 各个环节的错误都能正确传递和处理

这是一个既简单又强大的完整架构方案，能够支持语音助手的所有核心功能。
