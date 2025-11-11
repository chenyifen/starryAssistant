# APK版本号自动迭代配置说明

## 📋 功能概述

已实现自动版本号迭代和自定义APK文件名格式功能：

1. **自动版本号迭代**：每次git commit时，版本号自动递增
2. **自定义APK文件名**：格式为 `VoiceAssistant-版本号-commitHash-commitMessage-buildType.apk`

---

## 🔧 配置说明

### 1. 版本号计算逻辑

**基础版本号**（在`app/build.gradle.kts`中配置）：
```kotlin
val baseVersionCode = 16      // 基础版本代码
val baseVersionName = "3.2"   // 基础版本名称
```

**最终版本号**（自动计算）：
```kotlin
val gitCommitCount = getGitCommitCount()  // Git commit总数
val finalVersionCode = baseVersionCode + gitCommitCount
val finalVersionName = "${baseVersionName}.${gitCommitCount}"
```

**示例**：
- 当前commit数：1635
- versionCode = 16 + 1635 = **1651**
- versionName = "3.2.1635"

### 2. APK文件名格式

**格式**：`VoiceAssistant-版本号-commitHash-commitMessage-buildType.apk`

**示例**：
- Debug版本：`VoiceAssistant-3.2.1635-8d155b9f-启用Release版本保护：代码混淆、日志优化、修复Protobuf混淆问题-debug.apk`
- Release版本：`VoiceAssistant-3.2.1635-8d155b9f-启用Release版本保护：代码混淆、日志优化、修复Protobuf混淆问题-release.apk`

**文件名组成部分**：
- `VoiceAssistant`：固定前缀
- `3.2.1635`：版本号（baseVersionName.commitCount）
- `8d155b9f`：Git commit hash（短格式，7位）
- `启用Release版本保护：代码混淆、日志优化、修复Protobuf混淆问题`：commit message（清理后，最多30字符）
- `debug`/`release`：构建类型

---

## 🚀 使用方法

### 手动构建

```bash
# 构建Debug版本
./gradlew assembleDebug

# 构建Release版本
./gradlew assembleRelease
```

**输出位置**：
- Debug: `app/build/outputs/apk/debug/VoiceAssistant-3.2.1635-xxx-xxx-debug.apk`
- Release: `app/build/outputs/apk/release/VoiceAssistant-3.2.1635-xxx-xxx-release.apk`

### 自动构建（Git Hook）

已配置`post-commit` hook，每次git commit后自动：
1. 计算新的版本号
2. 编译Debug版本APK
3. 使用新版本号命名APK文件

**触发方式**：
```bash
git commit -m "你的提交信息"
# Hook会自动触发编译
```

---

## 📝 版本号更新规则

### 版本号递增规则

| 操作 | versionCode变化 | versionName变化 | 说明 |
|------|----------------|----------------|------|
| 首次构建 | 16 + 0 = 16 | 3.2.0 | 基础版本 |
| 第1次commit | 16 + 1 = 17 | 3.2.1 | 自动递增 |
| 第100次commit | 16 + 100 = 116 | 3.2.100 | 自动递增 |
| 当前（1635次commit） | 16 + 1635 = 1651 | 3.2.1635 | 当前版本 |

### 修改基础版本号

如果需要修改基础版本号，编辑`app/build.gradle.kts`：

```kotlin
// 修改基础版本号
val baseVersionCode = 20      // 新的基础版本代码
val baseVersionName = "4.0"   // 新的基础版本名称
```

**注意**：修改基础版本号后，所有后续版本号都会基于新基础计算。

---

## 🔍 验证版本号

### 查看当前版本信息

```bash
# 查看Git commit数量
git rev-list --count HEAD

# 查看当前commit hash
git rev-parse --short HEAD

# 查看最新commit message
git log -1 --pretty=%B
```

### 构建时查看版本信息

构建时Gradle会输出版本信息，可以在构建日志中查看：
```
> Task :app:generateDebugBuildConfig
Version Code: 1651
Version Name: 3.2.1635
```

---

## ⚙️ 配置位置

### 主要配置文件

1. **`app/build.gradle.kts`**
   - Git版本信息获取函数
   - 版本号计算逻辑
   - APK文件名自定义配置

2. **`.git/hooks/post-commit`**
   - Git commit后自动构建脚本
   - 自动使用新版本号编译APK

---

## 🐛 故障排查

### 问题1: 版本号不更新

**原因**：Git命令执行失败

**解决**：
```bash
# 检查Git是否可用
git rev-list --count HEAD

# 检查build.gradle.kts中的Git命令路径
# 确保Git在PATH中
```

### 问题2: APK文件名格式不正确

**原因**：`applicationVariants`配置未生效

**解决**：
1. 检查`app/build.gradle.kts`中的`applicationVariants.all`配置
2. 确保使用正确的API（`BaseVariantOutputImpl`）
3. 清理并重新构建：`./gradlew clean assembleDebug`

### 问题3: Git Hook不执行

**原因**：Hook文件权限或路径问题

**解决**：
```bash
# 检查Hook文件是否存在
ls -la .git/hooks/post-commit

# 确保Hook有执行权限
chmod +x .git/hooks/post-commit

# 手动测试Hook
.git/hooks/post-commit
```

---

## 📚 相关文件

- `app/build.gradle.kts` - 版本号配置和APK文件名设置
- `.git/hooks/post-commit` - Git commit后自动构建脚本
- `analyze_apk_size.sh` - APK大小分析脚本
- `APK_SIZE_OPTIMIZATION.md` - APK大小优化文档

---

## 💡 最佳实践

1. **版本号管理**
   - 基础版本号（baseVersionCode/baseVersionName）应该手动维护
   - commit数量自动递增，无需手动修改

2. **Commit Message规范**
   - 使用清晰的commit message，会出现在APK文件名中
   - 避免使用特殊字符，会被替换为下划线

3. **构建类型**
   - Debug版本：用于开发和测试
   - Release版本：用于生产发布

4. **版本号重置**
   - 如果需要重置版本号，修改基础版本号即可
   - 不建议频繁修改基础版本号

---

**最后更新**: 2025-11-11  
**当前版本**: 3.2.1635 (versionCode: 1651)

