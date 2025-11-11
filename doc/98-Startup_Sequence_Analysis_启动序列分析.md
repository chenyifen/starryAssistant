# 启动顺序分析

## 📊 完整启动时间线（10:51:01开始）

```
时间            事件                                    说明
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
10:51:01.803   🔐 权限检查开始                         FloatingLauncher
10:51:01.809   ✅ 存储权限检查完成                      
10:51:01.811   ✅ 悬浮窗权限检查完成                    
10:51:01.811   🚀 启动悬浮球服务                        开始启动 Service
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
10:51:02.192   🏗️ SttInputDeviceWrapper 初始化         +381ms 
10:51:02.197   📝 读取DataStore配置                    +5ms
10:51:02.198   🎙️ 获取SenseVoiceInputDevice单例       +1ms
10:51:02.216   🎤 SenseVoice开始初始化                 +18ms
10:51:02.217   ✅ SttInputDevice构建完成                +1ms
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
10:51:02.262   📥 开始拷贝 asr.onnx (异步)             +45ms
10:51:02.536   🎨 EnhancedFloatingWindowService创建    +320ms
10:51:02.594   🔧 初始化组件                            +58ms
10:51:02.679   🎈 悬浮球显示                            +85ms ⭐ 用户可见
10:51:02.804   📥 Service启动命令接收                  +125ms
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
10:51:03.967   📥 拷贝 asr_tokens.txt                  +1.7秒
10:51:03.973   ✅ 模型文件拷贝完成                      +1.76秒
10:51:03.998   🔧 SenseVoiceRecognizer.create()       +25ms
10:51:04.020   🚀 创建OfflineRecognizer实例           +22ms
10:51:04.020   💾 从文件系统加载模型                    开始加载ONNX
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
???            ✅ OfflineRecognizer创建成功            等待中...
```

## 🎯 关键发现

### 1. SenseVoice初始化在权限之后 ✅

```
权限授予完成 → 381ms → SenseVoice开始初始化
```

**为什么？**
- `SttInputDeviceWrapper` 通过 Hilt 注入到 `EnhancedFloatingWindowService`
- Hilt 在 Service onCreate 之前创建依赖
- `SttInputDeviceWrapper` 的 `init` 块会立即构建 `SttInputDevice`
- 这触发了 `SenseVoiceInputDevice.getInstance()`

### 2. 初始化是同步阻塞的

```kotlin
// SttInputDeviceWrapper.kt
init {
    inputDeviceSetting = firstSettings.first  // 🔴 同步读取 DataStore
    sttInputDevice = buildInputDevice(inputDeviceSetting)  // 🔴 同步构建
    // 异步启动状态监听
    scope.launch { restartUiStateJob() }
}
```

**问题：**
- `buildInputDevice()` 是同步的
- 虽然 SenseVoice 内部是异步加载，但创建单例是同步的
- 这会稍微延迟 Service 的创建

### 3. 悬浮球显示很快 ✅

```
从权限完成 → 878ms → 悬浮球显示
```

这很好！用户很快就能看到界面。

## 📊 时间分解（权限后）

| 阶段 | 时间 | 占比 |
|------|------|------|
| 权限检查到SenseVoice初始化 | 381ms | 13.3% |
| SenseVoice创建单例 | 19ms | 0.7% |
| Service创建到悬浮球显示 | 143ms | 5.0% |
| 异步拷贝模型文件 | 1.7s | 59.4% |
| ONNX加载准备 | 25ms | 0.9% |
| **ONNX模型加载** | **???** | **???** |

## 🔍 线程数8的效果（等待验证）

我们已经将 `numThreads` 从 2 改为 8。

**预期效果：**
- 如果算子初始化可并行：26.6s → 6-7s ✅
- 如果部分串行：26.6s → 13-15s ⚠️
- 如果完全串行：26.6s（无变化）❌

**等待日志：**
```
10:51:04.020 开始加载 → ??? 完成
```

需要查看完整日志才能知道实际效果。

## 💡 优化建议

### 短期优化（无需改代码）
1. ✅ 增加线程数到8 - 已完成，等待验证
2. 显示加载进度 - UX改善

### 中期优化（需要改代码）
3. 使用INT8量化模型 - 模型替换
4. 延迟初始化 - 架构调整

### 长期优化（需要重构）
5. 预加载机制 - 在Application中提前加载
6. NNAPI加速 - 需要测试兼容性

