# 悬浮窗智能语音助手使用指南

## 📋 概述

本指南详细说明如何使用新开发的悬浮窗智能语音助手系统。该系统专为大交互平板设计，提供了完整的悬浮球→半屏展开的交互体验。

## 🏗️ 系统架构

### 核心组件

```
悬浮窗智能语音助手系统
├── FloatingAssistantManager (统一管理器)
├── EnhancedFloatingWindowService (增强版悬浮窗服务)
├── AssistantUIController (UI状态控制器)
├── DraggableFloatingOrb (可拖拽悬浮球)
├── HalfScreenAssistantActivity (半屏助手Activity)
├── VoiceWakeIntegration (语音唤醒集成)
├── AnimationSystem (动画系统)
├── WindowManagerOptimizer (窗口管理优化)
└── DragTouchHandler (拖拽触摸处理)
```

### 状态转换流程

```
悬浮球待机 → 点击/语音唤醒 → 展开动画 → 半屏界面 → 交互完成 → 收缩动画 → 悬浮球待机
```

## 🚀 快速开始

### 1. 基本集成

在您的Activity中集成悬浮助手管理器：

```kotlin
@AndroidEntryPoint
class MainActivity : BaseActivity() {
    
    @Inject lateinit var skillEvaluator: SkillEvaluator
    @Inject lateinit var sttInputDevice: SttInputDeviceWrapper
    @Inject lateinit var wakeDevice: WakeDeviceWrapper
    @Inject lateinit var skillContext: SkillContextInternal
    
    private lateinit var floatingAssistantManager: FloatingAssistantManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化悬浮助手管理器
        floatingAssistantManager = FloatingAssistantManager.getInstance(
            context = this,
            skillEvaluator = skillEvaluator,
            sttInputDevice = sttInputDevice,
            wakeDevice = wakeDevice,
            skillContext = skillContext
        )
        
        // 检查权限并启动
        if (floatingAssistantManager.checkAndRequestPermissions()) {
            floatingAssistantManager.startFloatingAssistant()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 更新权限状态
        floatingAssistantManager.updatePermissionStatus()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        floatingAssistantManager.stopFloatingAssistant()
    }
}
```

### 2. 权限管理

系统需要以下权限：

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

检查和请求权限：

```kotlin
// 检查权限状态
val status = floatingAssistantManager.getAssistantStatus()
if (!status.hasOverlayPermission) {
    // 请求悬浮窗权限
    floatingAssistantManager.checkAndRequestPermissions()
}

// 监听权限状态变化
lifecycleScope.launch {
    floatingAssistantManager.hasOverlayPermission.collect { hasPermission ->
        if (hasPermission) {
            // 权限已授予，可以启动悬浮助手
            floatingAssistantManager.startFloatingAssistant()
        }
    }
}
```

## 🎯 功能特性

### 1. 悬浮球交互

- **点击**：展开半屏助手界面
- **长按**：进入拖拽模式
- **拖拽**：移动悬浮球位置
- **自动吸附**：拖拽结束后自动吸附到屏幕边缘

### 2. 语音唤醒

系统自动集成现有的语音唤醒功能：

```kotlin
// 手动触发唤醒（测试用）
floatingAssistantManager.triggerManualWake("Hi Nudge")

// 监听唤醒事件
voiceWakeIntegration.wakeWordEvents.collect { wakeEvent ->
    println("检测到唤醒词: ${wakeEvent.wakeWord}, 置信度: ${wakeEvent.confidence}")
}
```

### 3. 半屏界面

半屏界面包含：
- 语音波形动画
- 实时识别文本显示
- 助手回复卡片
- 快捷命令按钮
- 关闭按钮

### 4. 动画效果

- **展开动画**：悬浮球放大并移动到屏幕中心
- **收缩动画**：从半屏收缩回悬浮球
- **唤醒动画**：语音唤醒时的脉冲效果
- **拖拽动画**：平滑的位置过渡

## ⚙️ 配置选项

### 助手配置

```kotlin
val config = AssistantConfig(
    enableAutoStart = true,           // 自动启动
    enableWakeAnimation = true,       // 启用唤醒动画
    enableHapticFeedback = true,      // 启用触觉反馈
    orbSize = 60,                     // 悬浮球大小(dp)
    snapToEdge = true,                // 自动吸附边缘
    dimBackground = true,             // 半屏时背景变暗
    autoHideDelay = 5000L             // 自动隐藏延迟(ms)
)
```

### 语音唤醒配置

```kotlin
val wakeConfig = VoiceWakeConfig(
    enableAutoExpansion = true,       // 唤醒后自动展开
    minimumConfidence = 0.5f,         // 最小置信度
    duplicateFilterMs = 500L,         // 重复过滤时间
    enableWakeAnimation = true,       // 启用唤醒动画
    autoStartListening = true,        // 自动开始语音识别
    supportedWakeWords = setOf(       // 支持的唤醒词
        WakeWordType.HEY_DICIO,
        WakeWordType.HI_NUDGE
    )
)
```

## 🔧 高级用法

### 1. 自定义UI组件

创建自定义的半屏UI组件：

```kotlin
@Composable
fun CustomHalfScreenContent(
    uiState: HalfScreenUiState,
    onVoiceInput: () -> Unit,
    onDismiss: () -> Unit
) {
    // 自定义UI实现
    Column {
        // 自定义语音交互区域
        CustomVoiceInteractionArea(uiState, onVoiceInput)
        
        // 自定义快捷命令
        CustomQuickCommands()
    }
}
```

### 2. 扩展动画效果

```kotlin
class CustomAnimationSystem(context: Context) : AnimationSystem(context) {
    
    fun customExpandAnimation(view: View) {
        // 自定义展开动画
        val animator = ObjectAnimator.ofFloat(view, "rotation", 0f, 360f)
        animator.duration = 500L
        animator.start()
    }
}
```

### 3. 性能监控

```kotlin
// 获取性能统计
val stats = floatingAssistantManager.getPerformanceStats()
println("唤醒事件总数: ${stats.totalWakeEvents}")
println("平均响应时间: ${stats.averageResponseTime}ms")
println("内存使用: ${stats.memoryUsage}MB")
```

## 🐛 调试和测试

### 1. 启用调试日志

在`DebugLogger`中启用UI相关日志：

```kotlin
DebugLogger.logUI("TAG", "Debug message")
```

### 2. 测试唤醒功能

```kotlin
// 手动触发唤醒测试
floatingAssistantManager.triggerManualWake("test")

// 检查唤醒状态
val isListening = voiceWakeIntegration.isListening()
println("语音唤醒监听状态: $isListening")
```

### 3. 性能测试

```kotlin
// 测试动画性能
val startTime = System.currentTimeMillis()
animationSystem.animateOrbToHalfScreen(orbView, startPosition) {
    val duration = System.currentTimeMillis() - startTime
    println("动画耗时: ${duration}ms")
}
```

## 📱 适配建议

### 1. 大屏平板优化

- 悬浮球大小适配屏幕密度
- 半屏高度根据屏幕尺寸调整
- 考虑横屏和竖屏模式

### 2. 多用户场景

- 会议模式：降低干扰，快速收起
- 教学模式：增加可见性，延长显示时间
- 演示模式：禁用拖拽，固定位置

### 3. 性能优化

- 使用硬件加速
- 合理管理内存
- 优化动画帧率

## 🔍 故障排除

### 常见问题

1. **悬浮球不显示**
   - 检查悬浮窗权限
   - 确认服务是否启动
   - 查看日志错误信息

2. **语音唤醒不工作**
   - 检查麦克风权限
   - 确认唤醒模型已加载
   - 测试手动唤醒功能

3. **动画卡顿**
   - 启用硬件加速
   - 减少动画复杂度
   - 检查内存使用情况

4. **触摸事件冲突**
   - 检查WindowManager参数
   - 确认FLAG_NOT_FOCUSABLE设置
   - 调整触摸阈值

### 日志分析

关键日志标签：
- `AssistantUIController`: UI状态控制
- `DragTouchHandler`: 触摸事件处理
- `VoiceWakeIntegration`: 语音唤醒
- `AnimationSystem`: 动画系统
- `WindowManagerOptimizer`: 窗口管理

## 📚 API参考

### FloatingAssistantManager

| 方法 | 描述 |
|------|------|
| `startFloatingAssistant()` | 启动悬浮助手 |
| `stopFloatingAssistant()` | 停止悬浮助手 |
| `checkAndRequestPermissions()` | 检查并请求权限 |
| `getAssistantStatus()` | 获取助手状态 |
| `triggerManualWake()` | 手动触发唤醒 |

### AssistantUIController

| 方法 | 描述 |
|------|------|
| `onOrbClick()` | 处理悬浮球点击 |
| `onWakeWordDetected()` | 处理语音唤醒 |
| `dismissHalfScreen()` | 关闭半屏界面 |
| `updateOrbPosition()` | 更新悬浮球位置 |

## 🎉 总结

悬浮窗智能语音助手系统提供了完整的大屏交互解决方案，具有以下优势：

- ✅ **无干扰设计**：悬浮球不抢焦点，不影响主屏操作
- ✅ **平滑动画**：精心设计的过渡动画，提升用户体验
- ✅ **语音集成**：无缝集成现有语音唤醒和识别系统
- ✅ **高度可定制**：支持自定义UI、动画和配置
- ✅ **性能优化**：针对大屏设备优化，流畅运行
- ✅ **多场景适配**：适用于会议、教学、演示等多种场景

通过本系统，您可以在大交互平板上实现专业级的语音助手体验，既保持了便捷性，又避免了对主要工作流程的干扰。
