# 统一状态管理方案

## 一、设计目标

### 1.1 核心问题
当前系统中存在多个状态管理点：
- `AsrHandler.isStarted`：ASR运行状态
- `VoiceAssistantUIState`：UI状态（IDLE/LISTENING）
- `WakeService.listening`：唤醒服务监听状态
- `LottieAnimationState`：动画状态

这些状态分散管理，容易出现不一致问题：
- WakeService根据AsrHandler.isStarted轮询检查，响应延迟
- 悬浮球动画状态与ASR实际状态不同步
- 静音超时、技能执行完成等事件的状态转换分散在各处

### 1.2 解决方案
创建统一的核心状态管理器 `VoiceAssistantCoreState`，作为唯一的状态源（Single Source of Truth）：
- **核心状态**：IDLE / LISTENING（对应 `AsrHandler.isStarted`）
- **线程安全**：使用 `StateFlow` 提供响应式状态订阅
- **统一转换**：所有状态转换都通过统一接口进行
- **及时响应**：所有组件订阅状态变化，自动响应

**与现有 VoiceAssistantStateProvider 的关系：**
- `VoiceAssistantCoreState`：管理核心功能状态（IDLE/LISTENING），对应 `AsrHandler.isStarted`
- `VoiceAssistantStateProvider`：管理UI完整状态（包含显示文本、ASR文本、TTS文本等）
- 两者需要保持同步：`VoiceAssistantCoreState` 状态变化时，同步更新 `VoiceAssistantStateProvider` 的 `uiState`

## 二、架构设计

### 2.1 核心组件

```
VoiceAssistantCoreState (单例)
├── 状态定义：IDLE / LISTENING
├── StateFlow<VoiceAssistantCoreState.State>
├── 状态转换方法：
│   ├── transitionToListening(reason: String)
│   ├── transitionToIdle(reason: String)
│   └── getCurrentState(): State
└── 状态变化监听器注册

订阅者：
├── WakeService：根据状态启动/停止唤醒词检测
├── AsrHandler：状态变化时同步内部isStarted
├── DraggableFloatingOrb：根据状态更新动画
└── EnhancedFloatingWindowService：协调其他组件
```

### 2.2 状态流转图

```
IDLE (待机)
  │
  │ [唤醒词检测成功]
  │ [手动点击悬浮球]
  │
  ▼
LISTENING (监听中)
  │
  │ [静音超时]
  │ [技能执行完成]
  │ [手动停止]
  │
  ▼
IDLE (待机)
```

### 2.3 状态转换触发点

| 转换 | 触发条件 | 触发位置 |
|------|---------|---------|
| IDLE → LISTENING | 唤醒词检测成功 | `WakeService.onWakeWordDetected()` |
| IDLE → LISTENING | 手动点击悬浮球 | `EnhancedFloatingWindowService.handleOrbClick()` |
| LISTENING → IDLE | 静音超时（8秒） | `AsrHandler` 静音检测逻辑 |
| LISTENING → IDLE | 技能执行完成 | `SkillHandler` 执行完成回调 |
| LISTENING → IDLE | 手动停止 | `EnhancedFloatingWindowService.stopVoiceRecognition()` |

## 三、实现方案

### 3.1 VoiceAssistantCoreState 类设计

```kotlin
package com.ai.voice.ui.floating.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference
import android.util.Log

/**
 * 语音助手核心状态管理器（单例）
 * 
 * 职责：
 * 1. 管理核心状态（IDLE / LISTENING）
 * 2. 提供线程安全的状态访问和订阅
 * 3. 统一所有状态转换入口
 * 4. 确保状态一致性
 */
object VoiceAssistantCoreState {
    private const val TAG = "VoiceAssistantCoreState"
    
    /**
     * 核心状态枚举
     */
    enum class State {
        IDLE,        // 待机状态：ASR未运行，WakeService监听唤醒词
        LISTENING    // 监听状态：ASR运行中，WakeService暂停监听
    }
    
    // 当前状态（使用StateFlow提供响应式订阅）
    private val _state = MutableStateFlow<State>(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()
    
    // 状态转换互斥锁（防止并发转换）
    private val stateTransitionLock = Any()
    
    // 状态变化原因记录（用于调试）
    private val lastTransitionReason = AtomicReference<String>("初始化")
    
    /**
     * 获取当前状态（同步方法，用于快速检查）
     */
    fun getCurrentState(): State = _state.value
    
    /**
     * 检查是否处于监听状态
     */
    fun isListening(): Boolean = _state.value == State.LISTENING
    
    /**
     * 检查是否处于待机状态
     */
    fun isIdle(): Boolean = _state.value == State.IDLE
    
    /**
     * 转换到监听状态
     * @param reason 转换原因（用于日志和调试）
     * @return 是否成功转换（如果已经在LISTENING状态则返回false）
     */
    fun transitionToListening(reason: String): Boolean {
        synchronized(stateTransitionLock) {
            val currentState = _state.value
            if (currentState == State.LISTENING) {
                Log.d(TAG, "⚠️ 已在LISTENING状态，忽略转换请求: $reason")
                return false
            }
            
            _state.value = State.LISTENING
            lastTransitionReason.set(reason)
            Log.i(TAG, "🔄 IDLE → LISTENING: $reason")
            return true
        }
    }
    
    /**
     * 转换到待机状态
     * @param reason 转换原因（用于日志和调试）
     * @return 是否成功转换（如果已经在IDLE状态则返回false）
     */
    fun transitionToIdle(reason: String): Boolean {
        synchronized(stateTransitionLock) {
            val currentState = _state.value
            if (currentState == State.IDLE) {
                Log.d(TAG, "⚠️ 已在IDLE状态，忽略转换请求: $reason")
                return false
            }
            
            _state.value = State.IDLE
            lastTransitionReason.set(reason)
            Log.i(TAG, "🔄 LISTENING → IDLE: $reason")
            return true
        }
    }
    
    /**
     * 获取最后一次状态转换的原因
     */
    fun getLastTransitionReason(): String = lastTransitionReason.get()
}
```

### 3.2 WakeService 集成

**修改点：**
1. 订阅 `VoiceAssistantCoreState.state`
2. 根据状态自动启动/停止唤醒词检测
3. 移除对 `AsrHandler.isStarted()` 的轮询检查

```kotlin
// WakeService.kt

class WakeService : Service() {
    private var stateObserverJob: Job? = null
    
    override fun onCreate() {
        super.onCreate()
        // ... 其他初始化代码 ...
        
        // 订阅核心状态变化
        stateObserverJob = scope.launch {
            VoiceAssistantCoreState.state.collect { state ->
                when (state) {
                    VoiceAssistantCoreState.State.IDLE -> {
                        // IDLE状态：启动唤醒词检测
                        if (!listening.get()) {
                            startPersistentListening()
                        }
                    }
                    VoiceAssistantCoreState.State.LISTENING -> {
                        // LISTENING状态：暂停唤醒词检测（但保持listening=true，以便快速恢复）
                        // 注意：不停止listening，只是暂停检测循环
                    }
                }
            }
        }
    }
    
    private fun listenForWakeWord() {
        // ... 模型加载代码 ...
        
        while (listening.get()) {
            // 检查核心状态：如果处于LISTENING，暂停唤醒词检测
            val coreState = VoiceAssistantCoreState.getCurrentState()
            if (coreState == VoiceAssistantCoreState.State.LISTENING) {
                // 等待状态变为IDLE
                while (VoiceAssistantCoreState.getCurrentState() == VoiceAssistantCoreState.State.LISTENING 
                       && listening.get()) {
                    Thread.sleep(50)
                }
                if (!listening.get()) break
                continue
            }
            
            // IDLE状态：正常进行唤醒词检测
            // ... 原有的唤醒词检测逻辑 ...
        }
    }
    
    private fun onWakeWordDetected() {
        // 转换到LISTENING状态（这会触发AsrHandler启动）
        VoiceAssistantCoreState.transitionToListening("唤醒词检测成功")
        WakeWordCallbackManager.notifyWakeWordDetected()
    }
    
    override fun onDestroy() {
        stateObserverJob?.cancel()
        // ... 其他清理代码 ...
    }
}
```

### 3.3 AsrHandler 集成

**修改点：**
1. 订阅 `VoiceAssistantCoreState.state`
2. 状态变化时自动启动/停止ASR
3. 静音超时时调用 `VoiceAssistantCoreState.transitionToIdle()`
4. `isStarted()` 方法改为读取 `VoiceAssistantCoreState.getCurrentState()`

```kotlin
// AsrHandler.kt

object AsrHandler {
    private var stateObserverJob: Job? = null
    private var contextForStateObserver: Context? = null
    
    /**
     * 初始化状态订阅（需要在有Context时调用）
     */
    fun initializeStateObserver(context: Context) {
        if (stateObserverJob != null) return
        
        contextForStateObserver = context.applicationContext
        stateObserverJob = CoroutineScope(Dispatchers.IO).launch {
            VoiceAssistantCoreState.state.collect { state ->
                val ctx = contextForStateObserver ?: return@collect
                
                when (state) {
                    VoiceAssistantCoreState.State.LISTENING -> {
                        // LISTENING状态：启动ASR（如果未启动）
                        if (!isStarted) {
                            startInternal(ctx)
                        }
                    }
                    VoiceAssistantCoreState.State.IDLE -> {
                        // IDLE状态：停止ASR（如果正在运行）
                        if (isStarted) {
                            stopInternal(ctx)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 启动ASR（内部方法，由状态订阅调用）
     */
    private fun startInternal(context: Context) {
        // 原有的start()逻辑，但不调用状态转换
        // ... 原有代码 ...
        isStarted = true
        doAsr(context)
    }
    
    /**
     * 停止ASR（内部方法，由状态订阅调用）
     */
    private fun stopInternal(context: Context) {
        // 原有的stop()逻辑，但不调用状态转换
        // ... 原有代码 ...
        isStarted = false
        doAsr(context)
    }
    
    /**
     * 公开的start()方法：通过状态转换触发
     */
    fun start(context: Context): Boolean {
        contextForStop = context
        return VoiceAssistantCoreState.transitionToListening("AsrHandler.start()")
    }
    
    /**
     * 公开的stop()方法：通过状态转换触发
     */
    fun stop(context: Context) {
        VoiceAssistantCoreState.transitionToIdle("AsrHandler.stop()")
    }
    
    /**
     * 检查是否正在识别（读取核心状态）
     */
    fun isStarted(): Boolean {
        return VoiceAssistantCoreState.getCurrentState() == VoiceAssistantCoreState.State.LISTENING
    }
    
    // 静音超时处理（在doAsr内部）
    private fun handleSilenceTimeout() {
        if (silenceDuration > SILENCE_TIMEOUT_MS && isStarted) {
            Log.i(TAG, "⏱️ 静音超时，转换到IDLE状态")
            silenceTimeoutCallback?.invoke()
            // 统一通过核心状态管理器转换状态
            VoiceAssistantCoreState.transitionToIdle("静音超时")
        }
    }
}
```

### 3.4 DraggableFloatingOrb 集成

**修改点：**
1. 订阅 `VoiceAssistantCoreState.state`
2. 根据状态自动更新动画

```kotlin
// DraggableFloatingOrb.kt

class DraggableFloatingOrb {
    private var stateObserverJob: Job? = null
    
    fun show(context: Context) {
        // ... 初始化代码 ...
        
        // 订阅核心状态变化
        stateObserverJob = CoroutineScope(Dispatchers.Main).launch {
            VoiceAssistantCoreState.state.collect { state ->
                val currentText = animationStateManager.displayText.value
                when (state) {
                    VoiceAssistantCoreState.State.IDLE -> {
                        animationStateManager.setIdle(currentText)
                    }
                    VoiceAssistantCoreState.State.LISTENING -> {
                        animationStateManager.setListening("正在听取...")
                    }
                }
            }
        }
    }
    
    fun hide() {
        stateObserverJob?.cancel()
        // ... 其他清理代码 ...
    }
}
```

### 3.5 EnhancedFloatingWindowService 集成

**修改点：**
1. 手动触发状态转换的方法统一调用 `VoiceAssistantCoreState`
2. 技能执行完成时转换到IDLE状态
3. 同步更新 `VoiceAssistantStateProvider` 的UI状态

```kotlin
// EnhancedFloatingWindowService.kt

class EnhancedFloatingWindowService {
    
    init {
        // 订阅核心状态变化，同步更新VoiceAssistantStateProvider
        serviceScope.launch {
            VoiceAssistantCoreState.state.collect { coreState ->
                val uiState = when (coreState) {
                    VoiceAssistantCoreState.State.IDLE -> VoiceAssistantUIState.IDLE
                    VoiceAssistantCoreState.State.LISTENING -> VoiceAssistantUIState.LISTENING
                }
                voiceAssistantStateProvider.transitionToState(uiState, "核心状态同步: ${coreState.name}")
            }
        }
    }
    
    private fun handleOrbClick() {
        val currentState = VoiceAssistantCoreState.getCurrentState()
        when (currentState) {
            VoiceAssistantCoreState.State.IDLE -> {
                VoiceAssistantCoreState.transitionToListening("手动点击悬浮球")
            }
            VoiceAssistantCoreState.State.LISTENING -> {
                VoiceAssistantCoreState.transitionToIdle("手动停止")
            }
        }
    }
    
    private fun startVoiceRecognition() {
        // 不再直接调用AsrHandler.start()
        // 而是通过状态转换触发
        VoiceAssistantCoreState.transitionToListening("启动语音识别")
    }
    
    private fun stopVoiceRecognition() {
        VoiceAssistantCoreState.transitionToIdle("停止语音识别")
    }
    
    // 技能执行完成回调
    private fun onSkillExecutionComplete() {
        VoiceAssistantCoreState.transitionToIdle("技能执行完成")
    }
    
    // 移除原有的状态监听逻辑，统一使用VoiceAssistantCoreState
}
```

## 四、迁移步骤

### 4.1 第一阶段：创建核心状态管理器
1. 创建 `VoiceAssistantCoreState.kt`
2. 实现基本的状态管理和转换方法
3. 添加日志记录

### 4.2 第二阶段：WakeService 集成
1. 订阅 `VoiceAssistantCoreState.state`
2. 修改 `listenForWakeWord()` 逻辑
3. `onWakeWordDetected()` 调用状态转换
4. 测试唤醒词检测功能

### 4.3 第三阶段：AsrHandler 集成
1. 订阅 `VoiceAssistantCoreState.state`
2. 状态变化时自动启动/停止ASR
3. 静音超时调用状态转换
4. 测试ASR启动/停止功能

### 4.4 第四阶段：UI组件集成
1. `DraggableFloatingOrb` 订阅状态
2. `EnhancedFloatingWindowService` 使用状态转换
3. 移除原有的状态管理逻辑
4. 测试UI动画同步

### 4.5 第五阶段：清理和优化
1. 移除 `VoiceAssistantStateProvider` 中的冗余状态管理
2. 统一所有状态转换入口
3. 添加状态转换日志和监控
4. 性能测试和优化

## 五、注意事项

### 5.1 线程安全
- `VoiceAssistantCoreState` 使用 `StateFlow` 保证线程安全
- 状态转换使用 `synchronized` 防止并发转换
- 所有订阅者使用协程 `collect` 自动处理线程切换

### 5.2 向后兼容
- 保留 `AsrHandler.isStarted()` 方法用于向后兼容
- 但内部实现改为读取 `VoiceAssistantCoreState.getCurrentState()`
- 逐步迁移所有调用点

### 5.3 状态一致性
- 所有状态转换必须通过 `VoiceAssistantCoreState`
- 禁止直接修改 `AsrHandler.isStarted`
- 禁止绕过状态管理器直接操作组件

### 5.4 错误处理
- 状态转换失败时记录日志但不抛出异常
- 如果组件订阅失败，不影响其他组件
- 添加状态转换超时检测（防止死锁）

## 六、测试验证

### 6.1 功能测试
- [ ] 唤醒词检测成功 → 状态转换为LISTENING → ASR自动启动 → 动画更新
- [ ] 静音超时 → 状态转换为IDLE → ASR自动停止 → WakeService恢复监听 → 动画更新
- [ ] 手动点击悬浮球 → 状态转换 → 所有组件同步响应
- [ ] 技能执行完成 → 状态转换为IDLE → 所有组件同步响应

### 6.2 并发测试
- [ ] 快速连续触发状态转换，确保不会出现中间状态
- [ ] 多个组件同时订阅状态变化，确保都能收到通知
- [ ] 状态转换过程中组件异常，不影响其他组件

### 6.3 性能测试
- [ ] 状态转换延迟 < 50ms
- [ ] 状态订阅响应延迟 < 100ms
- [ ] 内存占用无明显增加

## 七、预期效果

### 7.1 解决的问题
1. ✅ 状态不一致问题：所有组件统一读取同一状态源
2. ✅ 响应延迟问题：使用Flow响应式订阅，及时响应
3. ✅ 状态转换分散问题：统一通过VoiceAssistantCoreState转换
4. ✅ 代码维护问题：状态逻辑集中管理，易于维护

### 7.2 代码质量提升
1. 单一职责：VoiceAssistantCoreState只负责状态管理
2. 依赖倒置：组件依赖抽象状态，不直接依赖其他组件
3. 响应式编程：使用Flow提供声明式的状态订阅
4. 易于测试：状态管理逻辑独立，易于单元测试

## 八、实施时间估算

- 第一阶段（核心状态管理器）：2小时
- 第二阶段（WakeService集成）：3小时
- 第三阶段（AsrHandler集成）：3小时
- 第四阶段（UI组件集成）：4小时
- 第五阶段（清理优化）：2小时
- 测试验证：4小时

**总计：约18小时**
