# 权限请求问题修复总结

## 修改日期
2025-11-28

## 问题描述
使用 `run.sh` 运行的 APK 无法获取权限，应用内也没有权限请求的入口。

根本原因：
1. **缺少位置权限**：`PermissionHelper.getBasicPermissions()` 中没有包含位置权限（`ACCESS_FINE_LOCATION` 和 `ACCESS_COARSE_LOCATION`），而这些权限是获取MAC地址进行设备激活所必需的。
2. 应用启动时会检查激活状态，但获取MAC地址需要位置权限，导致无法正常激活。

## 修改内容

### 1. PermissionHelper.kt - 添加位置权限到基础权限列表

**路径**: `app/src/main/kotlin/com/ai/voice/util/PermissionHelper.kt`

#### 修改点 1: 将 BASIC_PERMISSIONS 改为动态方法

**修改前**:
```kotlin
private val BASIC_PERMISSIONS = arrayOf(
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.POST_NOTIFICATIONS
)
```

**修改后**:
```kotlin
/**
 * 获取基础权限列表（根据Android版本动态生成）
 * 包括：录音、通知、位置（位置权限用于获取MAC地址进行设备激活）
 */
private fun getBasicPermissions(): Array<String> {
    return buildList {
        add(Manifest.permission.RECORD_AUDIO)
        
        // Android 13+ 需要通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // 位置权限（用于获取MAC地址进行设备激活）
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
    }.toTypedArray()
}
```

#### 修改点 2: 添加位置权限检查方法

**新增**:
```kotlin
/**
 * 检查是否具有位置权限（用于获取MAC地址进行设备激活）
 */
fun hasLocationPermission(context: Context): Boolean {
    if (isSystemApp(context)) return true
    val hasFineLocation = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    
    val hasCoarseLocation = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    
    return hasFineLocation || hasCoarseLocation
}
```

#### 修改点 3: 更新 hasAllBasicPermissions

**修改前**:
```kotlin
fun hasAllBasicPermissions(context: Context): Boolean {
    return hasRecordAudioPermission(context) && hasNotificationPermission(context)
}
```

**修改后**:
```kotlin
/**
 * 检查是否具有所有基础权限
 * 包括：录音、通知、位置（位置权限用于获取MAC地址进行设备激活）
 */
fun hasAllBasicPermissions(context: Context): Boolean {
    return hasRecordAudioPermission(context) && 
           hasNotificationPermission(context) && 
           hasLocationPermission(context)
}
```

#### 修改点 4: 更新权限描述

**修改前**:
```kotlin
fun getPermissionDescription(permission: String): String {
    return when (permission) {
        Manifest.permission.RECORD_AUDIO -> "录音权限：用于语音识别和语音指令"
        Manifest.permission.POST_NOTIFICATIONS -> "通知权限：用于显示语音助手服务状态"
        Manifest.permission.READ_EXTERNAL_STORAGE -> "存储权限：用于访问外部模型文件"
        else -> "未知权限"
    }
}
```

**修改后**:
```kotlin
fun getPermissionDescription(permission: String): String {
    return when (permission) {
        Manifest.permission.RECORD_AUDIO -> "录音权限：用于语音识别和语音指令"
        Manifest.permission.POST_NOTIFICATIONS -> "通知权限：用于显示语音助手服务状态"
        Manifest.permission.READ_EXTERNAL_STORAGE -> "存储权限：用于访问外部模型文件"
        Manifest.permission.ACCESS_FINE_LOCATION -> "精确位置权限：用于获取MAC地址进行设备激活"
        Manifest.permission.ACCESS_COARSE_LOCATION -> "大致位置权限：用于获取MAC地址进行设备激活"
        else -> "未知权限"
    }
}
```

#### 修改点 5: 更新 getMissingBasicPermissions

```kotlin
fun getMissingBasicPermissions(context: Context): Array<String> {
    if (isSystemApp(context)) return emptyArray()
    return getBasicPermissions().filter { permission ->
        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
    }.toTypedArray()
}
```

### 2. run.sh - 移除自动授权逻辑，添加提示信息

**路径**: `run.sh`

**修改**:
- 移除了所有使用 `adb shell pm grant` 和 `adb shell appops set` 的自动授权代码
- 添加了用户友好的提示信息，说明应用会自动请求权限
- 恢复步骤编号（6-8 而不是 6-9）

```bash
echo "✅ 安装成功"

# 启动应用（启动悬浮球启动器）
echo ""
echo "🎯 6. 启动应用（悬浮球启动器）..."
package_name="com.ai.voice"
activity_name="com.ai.voice.ui.floating.FloatingLauncherActivity"

echo "💡 提示: 应用启动后会自动请求必需权限，请在弹窗中授予："
echo "   - 录音权限（必需）"
echo "   - 通知权限（必需）"
echo "   - 位置权限（必需，用于设备激活）"
echo "   - 悬浮窗权限（必需）"
echo ""
```

### 3. grant_permissions.sh - 保留作为备用工具

**路径**: `grant_permissions.sh`

这个脚本保留作为开发/调试时的辅助工具，但不再在正常流程中使用。

## 权限请求流程

现在的权限请求流程如下：

1. **用户启动应用** (`run.sh` 或点击图标)
2. **FloatingLauncherActivity 启动**
3. **自动检查并请求权限**:
   - 第一步：检查基础权限（录音、通知、位置）
   - 如果缺少，系统会弹出标准的权限请求对话框
   - 第二步：检查存储权限（已跳过，因为模型内置）
   - 第三步：检查悬浮窗权限
   - 如果缺少，会显示说明对话框，然后跳转到设置页面
4. **所有权限授予后**:
   - 检查激活状态
   - 如果已激活：启动悬浮球服务
   - 如果未激活：跳转到激活管理界面

## 权限说明

### 必需权限列表

1. **RECORD_AUDIO** (录音权限)
   - 用途：语音识别和语音指令
   - 请求时机：应用启动时
   - 请求方式：系统权限对话框

2. **POST_NOTIFICATIONS** (通知权限，Android 13+)
   - 用途：显示语音助手服务状态
   - 请求时机：应用启动时
   - 请求方式：系统权限对话框

3. **ACCESS_FINE_LOCATION** (精确位置权限)
   - 用途：获取MAC地址进行设备激活
   - 请求时机：应用启动时
   - 请求方式：系统权限对话框
   - **重要**：这是本次修复的核心，之前缺少这个权限导致无法激活

4. **ACCESS_COARSE_LOCATION** (大致位置权限)
   - 用途：获取MAC地址进行设备激活（备用）
   - 请求时机：应用启动时
   - 请求方式：系统权限对话框

5. **SYSTEM_ALERT_WINDOW** (悬浮窗权限)
   - 用途：显示悬浮球界面
   - 请求时机：基础权限授予后
   - 请求方式：引导到设置页面

## 测试验证

### 测试步骤：

1. **卸载应用**（模拟全新安装）:
   ```bash
   adb uninstall com.ai.voice
   ```

2. **运行安装脚本**:
   ```bash
   ./run.sh
   ```

3. **验证权限请求**:
   - 应用启动后，系统应该弹出权限请求对话框
   - 依次请求：录音、通知（Android 13+）、位置权限
   - 点击"允许"授予权限

4. **验证悬浮窗权限**:
   - 基础权限授予后，应该显示悬浮窗权限说明对话框
   - 点击"前往设置"，在设置页面开启"显示在其他应用上层"

5. **验证激活流程**:
   - 所有权限授予后，应该自动检查激活状态
   - 如果未激活，应该跳转到激活管理界面（TV友好界面）
   - 可以输入激活码或通过MAC地址激活

### 预期结果：

- ✅ 应用启动时自动弹出权限请求对话框
- ✅ 位置权限请求包含在基础权限中
- ✅ 权限对话框中有清晰的说明（通过 `getPermissionDescription`）
- ✅ 所有权限授予后可以正常使用应用
- ✅ 激活功能可以正常获取MAC地址

## 相关文件

- `app/src/main/kotlin/com/ai/voice/util/PermissionHelper.kt` - 权限检查核心逻辑
- `app/src/main/kotlin/com/ai/voice/ui/floating/FloatingLauncherActivity.kt` - 权限请求流程
- `app/src/main/AndroidManifest.xml` - 权限声明
- `run.sh` - 安装和启动脚本
- `grant_permissions.sh` - 备用权限授予工具（开发用）

## 注意事项

1. **位置权限的重要性**：
   - Android 6.0+ 需要位置权限才能获取MAC地址
   - 这是设备激活的关键要求
   - 用户可能会疑惑为什么语音助手需要位置权限，建议在权限说明中清晰标注用途

2. **Android版本差异**：
   - Android 13+ 需要 `POST_NOTIFICATIONS` 权限
   - Android 11+ 存储权限管理方式不同（但我们已跳过，因为模型内置）

3. **系统应用特殊处理**：
   - 如果构建为系统应用（system flavor），所有权限检查会被跳过
   - `isSystemApp()` 方法会返回 `true`，`getMissingBasicPermissions()` 返回空数组

## 后续建议

1. **权限说明优化**：
   - 考虑在首次请求位置权限前，显示一个解释对话框
   - 说明为什么语音助手需要位置权限（用于设备识别和激活）

2. **权限被拒绝处理**：
   - 当前已有处理（引导到设置页面）
   - 可以考虑添加"跳过激活"选项（如果支持试用模式）

3. **权限状态监控**：
   - 考虑添加权限状态变化监听
   - 当权限被撤销时，及时通知用户

4. **隐私政策**：
   - 建议在应用中添加隐私政策页面
   - 详细说明各项权限的用途和数据使用情况

