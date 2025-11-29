# 版本号管理说明

## 📋 概述

项目使用 `VERSION` 文件来统一管理版本号，避免在多个地方硬编码版本号。

## 📁 文件结构

- `VERSION` - 版本号文件（格式：`主版本.次版本.修订号`，例如：`3.19.43`）
- `scripts/update_version.sh` - 版本号更新脚本
- `.git/hooks/post-commit` - Git hook，在commit后自动更新版本号

## 🔢 版本号规则

版本号格式：`主版本.次版本.修订号`（例如：`3.19.43`）

计算规则：
- **修订号**：`git commit count % 100`（0-99循环）
- **次版本号增量**：`(git commit count / 100) % 100`（每100次commit +1）
- **主版本号增量**：`git commit count / 10000`（每10000次commit +1）

示例：
- commit 0-99: `3.3.0` - `3.3.99`
- commit 100-199: `3.4.0` - `3.4.99`
- commit 10000+: `4.x.x`

## 🚀 使用方法

### 1. 手动更新版本号

```bash
./scripts/update_version.sh
```

### 2. 自动更新（推荐）

Git hook会在每次commit后自动更新版本号。如果hook未安装，可以手动安装：

```bash
chmod +x .git/hooks/post-commit
```

### 3. 读取版本号

#### Gradle (build.gradle.kts)
```kotlin
val versionFile = File(rootProject.projectDir, "VERSION")
val finalVersionName = versionFile.readText().trim()
```

#### Shell脚本 (run.sh)
```bash
VERSION=$(cat VERSION | tr -d '\n\r')
```

## 📝 注意事项

1. **VERSION文件应提交到Git**：确保团队成员使用相同的版本号
2. **Git hook是本地配置**：每个开发者需要手动安装hook（或使用团队工具）
3. **版本号更新时机**：在commit后自动更新，确保版本号与git commit数量同步

## 🔧 故障排除

### 问题：版本号未更新

**解决方案**：
1. 检查 `.git/hooks/post-commit` 是否存在且可执行
2. 手动运行 `./scripts/update_version.sh`
3. 检查 `VERSION` 文件权限

### 问题：Gradle读取失败

**解决方案**：
1. 确保 `VERSION` 文件在项目根目录
2. 检查文件内容格式（应该是纯文本，例如：`3.19.43`）
3. 查看Gradle构建日志中的警告信息

### 问题：run.sh找不到APK

**解决方案**：
1. 确保 `VERSION` 文件存在
2. 检查APK文件名格式是否正确：`VoiceAssistant-${VERSION}-Debug.apk`
3. 运行 `./scripts/update_version.sh` 更新版本号

