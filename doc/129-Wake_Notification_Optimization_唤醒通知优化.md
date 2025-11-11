# 唤醒通知优化总结

## 需求

1. 修改唤醒通知文本: "Dicio wake word triggered" → "Hey Nudge wake word triggered"
2. 优化通知行为:
   - 如果悬浮球未打开: 显示通知,点击后启动悬浮球服务
   - 如果悬浮球已打开: 不显示通知,直接启动ASR

## 实现方案

### 1. 修改通知文本

#### 英文 (values/strings.xml)
```xml
<string name="wake_service_triggered_notification">Hey Nudge wake word triggered, tap to open</string>
```

#### 中文 (values-zh-rCN/strings.xml)
```xml
<string name="wake_service_triggered_notification">触发了 Hey Nudge 唤醒词，轻按打开</string>
```

### 2. 优化通知逻辑

#### WakeService.kt 修改

**添加服务状态检查方法**:
```kotlin
/**
 * 检查指定服务是否正在运行
 */
private fun isServiceRunning(serviceClass: Class<*>): Boolean {
    val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    @Suppress("DEPRECATION")
    for (service in manager.getRunningServices(Int.MAX_VALUE)) {
        if (serviceClass.name == service.service.className) {
            return true
        }
    }
    return false
}
```

**修改唤醒检测逻辑**:
```kotlin
// 检查悬浮球服务是否正在运行
val isFloatingServiceRunning = isServiceRunning(
    com.ai.voice.ui.floating.EnhancedFloatingWindowService::class.java
)

if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || MainActivity.isInForeground > 0) {
    // Android 10以下或MainActivity在前台，直接启动Activity
    startActivity(intent)
} else {
    // Android 10+
    if (isFloatingServiceRunning) {
        // 悬浮球已打开，跳过通知，直接启动ASR
        DebugLogger.logWakeWord(TAG, "✅ 悬浮球已打开，跳过通知，直接启动ASR")
    } else {
        // 悬浮球未打开，显示通知
        DebugLogger.logWakeWord(TAG, "📱 悬浮球未打开，显示通知")
        
        // 创建启动悬浮球服务的Intent
        val floatingIntent = Intent(
            this, 
            com.ai.voice.ui.floating.EnhancedFloatingWindowService::class.java
        )
        
        val pendingIntent = PendingIntent.getService(
            this,
            0,
            floatingIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        
        val notification = NotificationCompat.Builder(this, TRIGGERED_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hearing_white)
            .setContentTitle(getString(R.string.wake_service_triggered_notification))
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                getString(R.string.wake_service_triggered_notification_summary)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)  // 点击后自动取消通知
            .build()
        
        notificationManager.notify(TRIGGERED_NOTIFICATION_ID, notification)
    }
}
```

## 关键改进

### 1. 智能通知显示
- **之前**: 每次唤醒都显示通知
- **现在**: 只在悬浮球未打开时显示通知

### 2. 通知点击行为优化
- **之前**: 点击通知启动MainActivity
- **现在**: 点击通知启动悬浮球服务 (EnhancedFloatingWindowService)

### 3. 通知自动取消
- 添加 `.setAutoCancel(true)`: 点击后自动取消通知
- 使用 `PendingIntent.getService()` 替代 `PendingIntent.getActivity()`

### 4. 用户体验提升
- **场景1**: 悬浮球已打开
  - 用户说唤醒词 → ASR直接启动 → 无通知打扰 ✅
  
- **场景2**: 悬浮球未打开
  - 用户说唤醒词 → 显示通知 → 点击通知 → 启动悬浮球 → ASR启动 ✅

## 技术细节

### 服务状态检查
使用 `ActivityManager.getRunningServices()` 检查服务是否运行:
```kotlin
val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
for (service in manager.getRunningServices(Int.MAX_VALUE)) {
    if (serviceClass.name == service.service.className) {
        return true
    }
}
```

**注意**: 
- 此方法在Android 8.0+已被标记为deprecated
- 但仍可用于检查自己应用的服务状态
- 添加了 `@Suppress("DEPRECATION")` 注解

### PendingIntent类型变更
- **之前**: `PendingIntent.getActivity()` - 启动Activity
- **现在**: `PendingIntent.getService()` - 启动Service

### 通知优先级
保持 `PRIORITY_HIGH` 以确保通知能够及时显示

## 测试场景

### 场景1: 悬浮球已打开
1. 启动悬浮球服务
2. 说唤醒词 "Hey Nudge"
3. **预期**: 不显示通知,ASR直接启动

### 场景2: 悬浮球未打开
1. 确保悬浮球服务未运行
2. 说唤醒词 "Hey Nudge"
3. **预期**: 显示通知 "Hey Nudge wake word triggered, tap to open"
4. 点击通知
5. **预期**: 悬浮球服务启动,通知自动消失

### 场景3: MainActivity在前台
1. 打开MainActivity
2. 说唤醒词 "Hey Nudge"
3. **预期**: 直接启动ASR,不显示通知

## 修改文件列表

1. `app/src/main/res/values/strings.xml` - 英文通知文本
2. `app/src/main/res/values-zh-rCN/strings.xml` - 中文通知文本
3. `app/src/main/kotlin/com/ai/voice/io/wake/WakeService.kt` - 核心逻辑实现

## 后续优化建议

1. **考虑使用更现代的服务检查方法**:
   - 可以考虑在EnhancedFloatingWindowService中维护一个静态状态标志
   - 避免使用已废弃的 `getRunningServices()` API

2. **添加用户设置**:
   - 允许用户选择是否在悬浮球打开时也显示通知
   - 添加通知样式自定义选项

3. **通知内容优化**:
   - 可以根据悬浮球状态显示不同的通知内容
   - 添加更多操作按钮(如"取消"、"稍后")

4. **日志监控**:
   - 添加更多日志以监控通知显示/隐藏的决策过程
   - 便于调试和优化用户体验

