# APK重签名脚本使用说明

## 简介

`resign_apk.sh` 是一个独立的APK重签名工具，可以方便地对APK文件进行重新签名。

## 功能特性

- ✅ 自动对齐APK文件（zipalign）
- ✅ 使用指定keystore签名APK
- ✅ 自动验证签名
- ✅ 显示证书信息
- ✅ 支持命令行参数和交互式输入
- ✅ 自动查找Android SDK工具

## 使用方法

### 方式1: 命令行参数（推荐）

```bash
./scripts/resign_apk.sh \
  -a <APK路径> \
  -k <Keystore路径> \
  -s <Keystore密码> \
  -n <Key别名> \
  [-p <Key密码>] \
  [-o <输出路径>]
```

### 方式2: 交互式输入

如果缺少必需参数，脚本会自动进入交互式模式：

```bash
./scripts/resign_apk.sh
```

## 参数说明

| 参数 | 长参数 | 必需 | 说明 |
|------|--------|------|------|
| `-a` | `--apk` | ✅ | APK文件路径 |
| `-k` | `--keystore` | ✅ | Keystore文件路径 |
| `-s` | `--storepass` | ✅ | Keystore密码 |
| `-p` | `--keypass` | ❌ | Key密码（默认使用storepass） |
| `-n` | `--alias` | ✅ | Key别名 |
| `-o` | `--output` | ❌ | 输出APK路径（默认添加-signed后缀） |
| `-h` | `--help` | ❌ | 显示帮助信息 |

## 使用示例

### 示例1: 使用release keystore签名

```bash
./scripts/resign_apk.sh \
  -a app/build/outputs/apk/release/app-release.apk \
  -k app/release.keystore \
  -s android123 \
  -n release
```

### 示例2: 使用starry keystore签名并指定输出路径

```bash
./scripts/resign_apk.sh \
  -a VoiceAssistant-3.19.39-Release.apk \
  -k app/starry.keystore \
  -s starry \
  -p starry \
  -n starry \
  -o VoiceAssistant-3.19.39-Release-signed.apk
```

### 示例3: 使用长参数格式

```bash
./scripts/resign_apk.sh \
  --apk app.apk \
  --keystore release.keystore \
  --storepass android123 \
  --keypass android123 \
  --alias release \
  --output app-signed.apk
```

### 示例4: 交互式输入

```bash
./scripts/resign_apk.sh
# 然后按提示输入各个参数
```

## 输出说明

脚本执行后会：

1. **对齐APK**: 使用zipalign对齐到4字节边界
2. **签名APK**: 使用指定的keystore进行签名
3. **验证签名**: 自动验证签名并显示证书信息
4. **清理临时文件**: 删除对齐过程中产生的临时文件

最终会在指定位置（或默认位置）生成签名后的APK文件。

## 证书信息

签名完成后，脚本会显示以下证书信息：

- 证书主体（DN）
- SHA-256指纹
- SHA-1指纹
- MD5指纹

## 注意事项

1. **Android SDK**: 脚本会自动查找Android SDK中的`zipalign`和`apksigner`工具
   - 优先查找 `$HOME/Library/Android/sdk`
   - 其次查找 `$ANDROID_HOME` 和 `$ANDROID_SDK_ROOT`
   - 使用最新版本的build-tools

2. **输出文件**: 如果未指定输出路径，会在原APK同目录下生成带`-signed`后缀的文件

3. **密码安全**: 密码会通过命令行参数传递，请注意命令行历史记录的安全性

4. **文件覆盖**: 如果输出文件已存在，会被自动覆盖

## 错误处理

脚本会在以下情况退出并显示错误：

- APK文件不存在
- Keystore文件不存在
- 缺少必需参数
- 对齐失败
- 签名失败
- 签名验证失败
- 找不到Android SDK工具

## 常见问题

### Q: 找不到Android SDK工具？

A: 请设置`ANDROID_HOME`环境变量，或将Android SDK安装在默认位置：
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
```

### Q: 签名验证失败？

A: 检查：
- Keystore密码是否正确
- Key别名是否正确
- Key密码是否正确（如果与storepass不同）

### Q: 如何查看keystore中的别名？

A: 使用keytool命令：
```bash
keytool -list -v -keystore your.keystore -storepass your_password
```

## 相关文件

- `scripts/resign_apk.sh` - 重签名脚本
- `app/release.keystore` - Release签名文件
- `app/starry.keystore` - Starry签名文件

