# 脚本更新说明 - APK文件名格式变更

## 📋 更新概述

所有构建脚本已更新，适配新的APK文件名格式：`VoiceAssistant-版本号-commitHash-commitMessage-buildType.apk`

---

## 🔄 已更新的脚本

### 1. `run_release.sh`
- **更新前**: 查找固定文件名 `app-release.apk`
- **更新后**: 使用通配符查找 `VoiceAssistant-*-release.apk`
- **功能**: 构建、安装和启动Release版本

### 2. `run.sh`
- **更新前**: 查找固定文件名 `app-debug.apk`
- **更新后**: 使用通配符查找 `VoiceAssistant-*-debug.apk`
- **功能**: 构建、安装和启动Debug版本

### 3. `run_main.sh`
- **更新前**: 查找固定文件名 `app-debug.apk`
- **更新后**: 使用通配符查找 `VoiceAssistant-*-debug.apk`
- **功能**: 构建、安装和启动Debug版本（主Activity）

### 4. `run_before_test.sh`
- **更新前**: 查找固定文件名 `app-debug.apk`
- **更新后**: 使用通配符查找 `VoiceAssistant-*-debug.apk`
- **功能**: 测试前构建和安装Debug版本

### 5. `post-commit-hook.sh`
- **更新前**: 查找固定文件名 `app-debug.apk`
- **更新后**: 使用通配符查找 `VoiceAssistant-*-debug.apk`
- **功能**: Git commit后自动构建和保存APK

---

## 📝 APK文件名格式

### 新格式
```
VoiceAssistant-{版本号}-{commitHash}-{commitMessage}-{buildType}.apk
```

### 示例
- **Debug版本**: `VoiceAssistant-3.2.1635-8d155b9f-启用Release版本保护：代码混淆、日志优化、修复Protobuf混淆问题-debug.apk`
- **Release版本**: `VoiceAssistant-3.2.1635-8d155b9f-启用Release版本保护：代码混淆、日志优化、修复Protobuf混淆问题-release.apk`

### 组成部分
- `VoiceAssistant`: 固定前缀
- `3.2.1635`: 版本号（baseVersionName.commitCount）
- `8d155b9f`: Git commit hash（短格式，7位）
- `启用Release版本保护：代码混淆、日志优化、修复Protobuf混淆问题`: commit message（清理后，最多30字符）
- `debug`/`release`: 构建类型

---

## 🔍 脚本查找逻辑

所有脚本现在使用以下逻辑查找APK文件：

```bash
# 查找APK文件（新格式）
apk_dir="app/build/outputs/apk/debug"  # 或 release
apk_path=$(find "$apk_dir" -name "VoiceAssistant-*-debug.apk" -type f | head -1)

if [ -z "$apk_path" ] || [ ! -f "$apk_path" ]; then
    echo "❌ APK文件不存在"
    echo "查找目录: $apk_dir"
    echo "查找模式: VoiceAssistant-*-debug.apk"
    ls -la "$apk_dir" 2>/dev/null || echo "目录不存在"
    exit 1
fi
```

**特点**:
- 使用 `find` 命令匹配文件名模式
- 使用 `head -1` 获取第一个匹配的文件（通常是最新的）
- 如果找不到文件，显示详细的错误信息和目录内容

---

## 🚀 使用方法

### 构建Release版本
```bash
./run_release.sh
```

### 构建Debug版本
```bash
./run.sh
# 或
./run_main.sh
```

### 测试前构建
```bash
./run_before_test.sh
```

### Git Hook自动构建
```bash
git commit -m "你的提交信息"
# Hook会自动触发，查找并复制新格式的APK文件
```

---

## ⚠️ 注意事项

1. **文件名包含特殊字符**: commit message中的特殊字符会被替换为下划线
2. **文件名长度**: commit message最多保留30个字符
3. **多个APK文件**: 如果目录中有多个匹配的APK文件，脚本会选择第一个（通常是最新的）
4. **向后兼容**: 如果找不到新格式的APK，脚本会显示错误信息并列出目录内容

---

## 🔧 故障排查

### 问题1: 找不到APK文件

**症状**: 脚本报错"APK文件不存在"

**解决**:
1. 检查构建是否成功完成
2. 检查APK输出目录是否存在
3. 查看脚本输出的目录内容，确认文件名格式

### 问题2: 找到多个APK文件

**症状**: 目录中有多个匹配的APK文件

**解决**: 
- 脚本会自动选择第一个匹配的文件
- 如果需要特定版本，可以手动指定APK路径

### 问题3: 文件名包含特殊字符

**症状**: commit message包含特殊字符导致文件名异常

**解决**:
- 特殊字符会被自动替换为下划线
- commit message会被限制为30个字符

---

## 📚 相关文档

- `VERSION_MANAGEMENT.md` - 版本号管理说明
- `APK_SIZE_OPTIMIZATION.md` - APK大小优化文档
- `INTEGRATION_GUIDE.md` - 集成指南

---

**最后更新**: 2025-11-11  
**更新内容**: 所有run_*.sh脚本适配新的APK文件名格式

