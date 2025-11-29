# 激活系统修改总结

## 修改日期
2025-11-28

## 问题描述
原系统存在15天试用期逻辑，即使设备未激活也能使用15天。这不符合安全要求。

## 修改内容

### 1. ActivationChecker.kt - 核心激活检查逻辑
**路径**: `app/src/main/kotlin/com/ai/voice/util/ActivationChecker.kt`

**修改前**:
- 基于首次安装时间计算试用期（15天）
- 即使未激活也能使用15天
- 检查失败时默认返回 `true`（允许使用）

**修改后**:
- 检查真实的激活状态，支持两种激活方式：
  1. `LicenseActivationManager` - 基于MAC地址和授权码
  2. `ActivationManager` - 基于设备指纹
- 只有通过以上任一方式激活才能使用
- 检查失败时返回 `false`（不允许使用）
- 移除了所有试用期相关代码

### 2. App.kt - 应用启动检查
**路径**: `app/src/main/kotlin/com/ai/voice/App.kt`

**新增功能**:
- 启动时强制检查激活状态
- 未激活时显示 Toast 提示："❌ 应用未激活，请先激活后再使用"
- 未激活时不初始化 AsrHandler
- 详细的日志输出，便于调试

### 3. AsrHandler.kt - ASR处理器检查
**路径**: `app/src/main/kotlin/com/ai/voice/util/AsrHandler.kt`

**修改**:
- 更新日志信息，移除"15天试用期"描述
- `initialize()` 和 `start()` 方法中的激活检查逻辑保持不变
- 错误提示从"试用期已过期"改为"应用未激活"

### 4. EnhancedFloatingWindowService.kt - 浮窗服务
**路径**: `app/src/main/kotlin/com/ai/voice/ui/floating/EnhancedFloatingWindowService.kt`

**修改**:
- 更新注释，移除"15天试用期"描述
- 错误提示从"试用期已过期"改为"应用未激活"

### 5. WakeService.kt - 唤醒服务
**路径**: `app/src/main/kotlin/com/ai/voice/io/wake/WakeService.kt`

**修改**:
- 更新注释，移除"15天试用期"描述
- 错误提示从"试用期已过期"改为"应用未激活"

### 6. user_settings.proto - 配置文件
**路径**: `app/src/main/proto/user_settings.proto`

**修改**:
- 保留 `first_install_timestamp` 字段（向后兼容）
- 更新注释：从"用于15天试用期"改为"已废弃，不再使用"

## 激活方式

应用现在支持两种激活方式（任一激活即可使用）：

### 方式一：授权码激活 (LicenseActivationManager)
1. 获取设备MAC地址
2. 使用授权码或MAC白名单激活
3. 服务器验证并保存激活状态

### 方式二：设备指纹激活 (ActivationManager)
1. 生成设备序列号和HMAC密钥
2. 通过激活码完成设备激活
3. 本地保存激活状态

## 测试验证

### 测试步骤：
1. **清除激活数据**（模拟未激活设备）：
   ```kotlin
   // 清除 LicenseActivationManager 激活数据
   LicenseActivationManager.getInstance().clearActivation()
   
   // 清除 ActivationManager 激活数据
   ActivationManager.resetActivation(context)
   ```

2. **重新启动应用**：
   - 应看到 Toast 提示："❌ 应用未激活，请先激活后再使用"
   - 日志应显示："❌❌❌ 应用未激活，无法使用 ❌❌❌"
   - AsrHandler 不应初始化

3. **激活应用**（使用任一方式）：
   ```kotlin
   // 方式一：使用授权码激活
   LicenseActivationManager.getInstance().activateWithLicense(licenseKey)
   
   // 方式二：使用设备指纹激活
   ActivationManager.markAsActivated(context)
   ```

4. **重新启动应用**：
   - 不应看到未激活提示
   - 日志应显示："✅ 激活检查完成，应用已激活"
   - AsrHandler 应正常初始化

### 预期日志输出（未激活时）：
```
D 🔐[Activation]: 初始化激活模块...
D 🔐[Activation]: 初始化设备指纹...
I 🔐[Activation]: 📱 设备身份信息:
I 🔐[Activation]:    激活状态: 未激活
W ActivationChecker: ❌ 应用未激活，请进行设备激活
E App: ❌❌❌ 应用未激活，无法使用 ❌❌❌
W App: ⚠️ 应用未激活，跳过 AsrHandler 初始化
```

### 预期日志输出（已激活时）：
```
D 🔐[Activation]: 初始化激活模块...
I ActivationChecker: ✅ 应用已激活（通过授权码/MAC白名单）
I App: ✅ 激活检查完成，应用已激活
I App: 🚀 应用已激活，开始初始化 AsrHandler...
```

## 安全改进

1. **强制激活**: 移除试用期逻辑，未激活绝对无法使用
2. **用户提示**: 启动时明确告知用户激活状态
3. **错误处理**: 激活检查失败时返回未激活状态（安全优先）
4. **双重验证**: 支持两种独立的激活系统，增强灵活性

## 注意事项

1. **向后兼容**: 保留了 `first_install_timestamp` 字段，旧版本升级不会出现数据兼容问题
2. **用户体验**: 清晰的Toast提示和详细的日志，便于用户和开发者了解激活状态
3. **安全优先**: 所有异常情况都返回未激活状态，确保安全性

## 相关文件
- `ActivationChecker.kt` - 激活检查核心逻辑
- `App.kt` - 应用启动检查
- `AsrHandler.kt` - ASR初始化检查
- `EnhancedFloatingWindowService.kt` - 浮窗服务检查
- `WakeService.kt` - 唤醒服务检查
- `user_settings.proto` - 配置定义

## 后续建议

1. 建议在激活管理界面添加激活状态显示
2. 可以考虑添加激活剩余天数显示（如果使用有期限的授权）
3. 建议添加激活失败时的详细错误信息展示

