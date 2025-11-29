# APK重签名脚本使用说明

## 功能

- 自动对齐APK文件
- 使用指定keystore签名
- 自动验证签名并显示证书信息
- 支持命令行参数和交互式输入

## 使用方法

### 命令行参数

```bash
./resign_apk.sh -a <APK路径> -k <keystore路径> -s <密码> -n <别名> [-o <输出路径>]
```

### 交互式输入

缺少参数时自动进入交互模式：

```bash
./resign_apk.sh
```

## 参数

| 参数 | 说明 | 必需 |
|------|------|------|
| `-a, --apk` | APK文件路径 | ✅ |
| `-k, --keystore` | Keystore文件路径 | ✅ |
| `-s, --storepass` | Keystore密码 | ✅ |
| `-p, --keypass` | Key密码（默认使用storepass） | ❌ |
| `-n, --alias` | Key别名 | ✅ |
| `-o, --output` | 输出APK路径（默认添加-signed后缀） | ❌ |

## 示例

```bash
# 使用示例keystore（仅用于测试）
./resign_apk.sh \
  -a app-release.apk \
  -k example.keystore \
  -s example123 \
  -n example

# 使用自己的keystore
./resign_apk.sh \
  -a app.apk \
  -k my-release.keystore \
  -s mypassword \
  -n mykey \
  -o app-signed.apk

# 使用长参数
./resign_apk.sh \
  --apk app.apk \
  --keystore my-release.keystore \
  --storepass mypassword \
  --keypass mypassword \
  --alias mykey \
  --output app-signed.apk
```

## 执行流程

1. 对齐APK（zipalign）
2. 签名APK（apksigner）
3. 验证签名并显示证书信息
4. 清理临时文件

## 注意事项

- 脚本会自动查找Android SDK工具（zipalign、apksigner）
- 如果输出文件已存在，会被自动覆盖
- 密码通过命令行传递，注意命令行历史安全
- **请使用您自己的keystore文件进行正式签名，example.keystore仅用于测试**

## 常见问题

**找不到Android SDK工具？**

设置环境变量：
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
```

**签名验证失败？**

检查密码、别名是否正确：
```bash
keytool -list -v -keystore your.keystore -storepass your_password
```

**如何创建自己的keystore？**

```bash
keytool -genkeypair -v \
  -keystore my-release.keystore \
  -alias mykey \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass mypassword \
  -keypass mypassword \
  -dname "CN=Your Name, OU=Development, O=Your Company, L=City, ST=State, C=CN"
```
