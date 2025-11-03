# 设备控制技能拆分状态

## ✅ 已完成的工作

### 1. YML文件拆分 (韩语版)
- ✅ `power_control.yml` - 电源和音量控制
- ✅ `input_source_control.yml` - 输入源切换
- ✅ `app_launcher.yml` - 应用启动
- ✅ `whiteboard_tools.yml` - 白板工具
- ✅ `system_navigation.yml` - 系统导航和功能

### 2. Skill类创建
- ✅ `BaseDeviceControlSkill.kt` - 基类（包含所有执行方法）
- ✅ `PowerControlSkill.kt`
- ⚠️  `InputSourceSkill.kt` - 需要改名为 `InputSourceControlSkill.kt`
- ✅ `AppLauncherSkill.kt`
- ✅ `WhiteboardToolsSkill.kt`
- ✅ `SystemNavigationSkill.kt`

### 3. Info类创建
- ✅ `PowerControlInfo.kt`
- ⚠️  需要创建其他4个Info类

### 4. 配置文件
- ✅ `skill_definitions.yml` - 已更新（修复了input_source命名冲突）
- ✅ 旧的 `DeviceControlSkill.kt` 和 `DeviceControlInfo.kt` 已备份

## ⚠️ 待完成的工作

### 1. 修复命名和创建缺失文件

```bash
# 1. 重命名InputSourceSkill
mv app/src/main/kotlin/com/ai/voice/skills/input_source/InputSourceSkill.kt \
   app/src/main/kotlin/com/ai/voice/skills/input_source_control/InputSourceControlSkill.kt

# 2. 更新InputSourceControlSkill.kt中的类名和导入
```

### 2. 创建缺失的Info类

需要创建：
- `InputSourceControlInfo.kt`
- `AppLauncherInfo.kt`
- `WhiteboardToolsInfo.kt`
- `SystemNavigationInfo.kt`

参考 `PowerControlInfo.kt` 的模式。

### 3. 修复BaseDeviceControlSkill.kt的导入

需要添加：
```kotlin
import org.dicio.skill.output.StringOutput
```

并修复HyundaiIT SDK的API调用（去掉context参数）：
```kotlin
// 错误：
SystemHelper.getInstance(ctx.android).powerOff()

// 正确：
SystemHelper.getInstance().powerOff()
```

### 4. 注册新技能到SkillHandler

在 `SkillHandler.kt` 中：
- 删除对 `DeviceControlInfo` 的引用
- 添加5个新技能的引用：
  - `PowerControlInfo`
  - `InputSourceControlInfo`
  - `AppLauncherInfo`
  - `WhiteboardToolsInfo`
  - `SystemNavigationInfo`

### 5. 创建英文和中文版yml文件

需要为每个技能创建：
- `en/power_control.yml`
- `en/input_source_control.yml`
- `en/app_launcher.yml`
- `en/whiteboard_tools.yml`
- `en/system_navigation.yml`
- `cn/power_control.yml`
- `cn/input_source_control.yml`
- `cn/app_launcher.yml`
- `cn/whiteboard_tools.yml`
- `cn/system_navigation.yml`

可以使用简化版本，只包含核心命令。

### 6. 删除旧的device_control.yml

还需要删除：
- `en/device_control.yml`
- `cn/device_control.yml`

## 🔧 快速修复脚本

```bash
cd /Users/user/AndroidStudioProjects/dicio-android

# 1. 创建input_source_control目录并移动文件
mkdir -p app/src/main/kotlin/com/ai/voice/skills/input_source_control
mv app/src/main/kotlin/com/ai/voice/skills/input_source/InputSourceSkill.kt \
   app/src/main/kotlin/com/ai/voice/skills/input_source_control/InputSourceControlSkill.kt

# 2. 删除旧目录
rmdir app/src/main/kotlin/com/ai/voice/skills/input_source

# 3. 删除旧的英文和中文device_control.yml
rm app/src/main/sentences/en/device_control.yml
rm app/src/main/sentences/cn/device_control.yml
```

## 📝 为什么拆分？

原始的 `device_control.yml` 包含1346行，有500+个命令变体，导致生成的Java方法超过64KB字节码限制：

```
错误: Method too large: com/ai/voice/sentences/Sentences$DeviceControl$Companion.languageToData$lambda$2
```

拆分成5个小技能后，每个技能的方法大小都在限制之内。

## ⏱️ 预估完成时间

- 修复命名和导入：5分钟
- 创建4个Info类：10分钟
- 更新SkillHandler：5分钟
- 创建英文/中文yml文件（简化版）：15分钟
- 测试编译：5分钟

**总计：约40分钟**

## 🎯 最终目标

拆分后的技能结构更清晰，每个技能专注于一个功能域，避免了JVM方法大小限制，同时保持了原有的所有功能。

