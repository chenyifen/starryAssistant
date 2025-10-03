# 激活码模块 (Activation Module)

## 📋 概述

这是一个**独立的**、**可选的**设备激活模块,参考 `py-xiaozhi-main` 的激活流程实现。

用于生成设备指纹、序列号、HMAC 签名,支持与服务器进行设备激活验证。

## 🎯 功能特性

- ✅ 设备指纹生成 (基于硬件信息)
- ✅ 序列号生成 (SN-XXXXXXXX-macaddress 格式)
- ✅ HMAC-SHA256 签名生成
- ✅ 激活状态管理
- ✅ 完全独立,易于移除
- ✅ 所有信息通过 Log 输出,无 UI 侵入

## 📂 文件结构

```
activation/
├── README.md                      # 本文档
├── DeviceFingerprint.kt           # 设备指纹收集器
├── ActivationCodeGenerator.kt    # 激活码生成器  
└── ActivationManager.kt           # 激活管理器 (统一入口)
```

**配置文件**: `activation_efuse.json` (自动生成于 `context.filesDir`)

## 🚀 使用方式

### 1. 初始化 (应用启动时)

已集成到 `App.kt`:

```kotlin
// App.kt
override fun onCreate() {
    super.onCreate()
    ActivationManager.initialize(this) // 初始化激活模块
    // ...
}
```

### 2. 自动处理激活流程

已集成到 `WebSocketProtocol.kt`,当服务器返回激活要求时自动处理:

服务器响应示例:
```json
{
  "type": "hello",
  "activation": {
    "code": "123456",
    "challenge": "random_challenge_string",
    "message": "请在控制面板输入验证码"
  }
}
```

客户端自动:
1. 打印激活码到 Logcat
2. 生成 HMAC 签名
3. 打印完整的激活请求 Payload

### 3. 查看激活信息

通过 Logcat 查看激活信息 (过滤标签: `🔐[Activation]`):

```bash
adb logcat | grep "🔐\[Activation\]"
```

输出示例:
```
🔐[Activation]: ═══════════════════════════════════════
🔐[Activation]: 📱 设备身份信息:
🔐[Activation]:    序列号: SN-A1B2C3D4-aabbccddeeff
🔐[Activation]:    HMAC密钥: 1234567890abcdef...
🔐[Activation]:    激活状态: 未激活
🔐[Activation]:    MAC地址: aa:bb:cc:dd:ee:ff
🔐[Activation]: ═══════════════════════════════════════

🔐[Activation]: ╔════════════════════════════════════════════════╗
🔐[Activation]: ║         🔐 设备激活 - 验证码                    ║
🔐[Activation]: ╠════════════════════════════════════════════════╣
🔐[Activation]: ║   验证码: 1 2 3 4 5 6                          ║
🔐[Activation]: ║   请在控制面板输入验证码                        ║
🔐[Activation]: ╚════════════════════════════════════════════════╝
```

### 4. 手动调用 API

如果需要手动操作:

```kotlin
// 获取设备信息
val serialNumber = ActivationManager.getSerialNumber(context)
val isActivated = ActivationManager.isActivated(context)

// 生成 HMAC 签名
val signature = ActivationManager.generateHmacSignature(context, challenge)

// 构建激活请求
val payload = ActivationManager.buildActivationRequest(context, challenge)

// 标记为已激活
ActivationManager.markAsActivated(context)

// 打印设备信息 (调试)
ActivationManager.printDeviceInfo(context)

// 重置激活状态 (调试/测试)
ActivationManager.resetActivation(context)
```

## 🔐 安全机制

### 设备指纹生成

基于以下硬件信息生成唯一标识:
- Hostname
- MAC 地址 (Android 6.0+ 使用 Android ID 生成伪 MAC)
- Android ID
- 设备型号

### 序列号格式

```
SN-<8位MD5哈希>-<12位MAC地址>
例如: SN-A1B2C3D4-aabbccddeeff
```

### HMAC 签名

使用 `HMAC-SHA256` 算法:
```
HMAC-SHA256(key=硬件哈希, message=服务器challenge)
```

## 📝 激活流程

### 完整流程图

```
1. 应用启动
   ↓
2. ActivationManager.initialize()
   - 生成/加载设备指纹
   - 生成/加载序列号和 HMAC 密钥
   ↓
3. WebSocket 连接到服务器
   - 发送 hello 消息 (包含设备ID)
   ↓
4. 服务器检查设备
   ├─ 已激活 → 返回配置信息
   └─ 未激活 → 返回激活数据
                ↓
5. 客户端收到激活数据
   - 打印验证码到 Logcat
   - 生成 HMAC 签名
   - 打印激活请求 Payload
   ↓
6. 用户在控制面板输入验证码
   ↓
7. 服务器验证
   - 验证 HMAC 签名
   - 验证用户输入的验证码
   ↓
8. 激活成功
   - 服务器返回 status: "success"
   - 客户端标记为已激活
```

### 协议格式

#### 激活请求 Payload

```json
{
  "Payload": {
    "algorithm": "hmac-sha256",
    "serial_number": "SN-A1B2C3D4-aabbccddeeff",
    "challenge": "server_challenge_string",
    "hmac": "generated_hmac_signature"
  }
}
```

#### 激活响应

成功:
```json
{
  "type": "activation",
  "status": "success"
}
```

等待:
```json
{
  "type": "activation",
  "status": "pending"
}
```

失败:
```json
{
  "type": "activation",
  "status": "failed",
  "error": "错误信息"
}
```

## 🗑️ 如何移除

如果不需要激活功能,按以下步骤完全移除:

### 1. 删除 activation package

```bash
rm -rf app/src/main/kotlin/org/stypox/dicio/activation/
```

### 2. 删除初始化调用

从 `App.kt` 中删除:
```kotlin
// 删除这行
ActivationManager.initialize(this)
```

### 3. 从 WebSocketProtocol.kt 移除集成

删除以下代码:
```kotlin
// 删除 import
import org.stypox.dicio.activation.ActivationManager

// 删除 handleActivationRequired() 方法
// 删除 handleActivationMessage() 方法

// 在 handleTextMessage() 的 HELLO 分支中删除:
if (json.has("activation")) {
    val activationData = json.getJSONObject("activation")
    handleActivationRequired(activationData)
}

// 删除 "activation" -> case
```

### 4. 删除配置文件 (可选)

```bash
adb shell rm /data/data/org.stypox.dicio.master/files/activation_efuse.json
```

### 5. 移除 context 参数 (可选)

如果不需要 context 参数,可以从 `WebSocketProtocol` 构造函数中移除。

## 🐛 调试和测试

### 查看设备信息

```kotlin
ActivationManager.printDeviceInfo(context)
```

### 重置激活状态

```kotlin
ActivationManager.resetActivation(context)
```

或通过 adb:
```bash
adb shell rm /data/data/org.stypox.dicio.master/files/activation_efuse.json
# 然后重启应用
```

### 手动测试 HMAC 签名

```kotlin
val challenge = "test_challenge_string"
val signature = ActivationCodeGenerator.generateHmacSignature(context, challenge)
Log.d("Test", "Signature: $signature")
```

### 验证签名

```kotlin
val isValid = ActivationCodeGenerator.verifyHmacSignature(
    context, 
    challenge, 
    expectedSignature
)
Log.d("Test", "Signature valid: $isValid")
```

## 📚 参考

- 原始实现: `py-xiaozhi-main/src/utils/device_fingerprint.py`
- 原始实现: `py-xiaozhi-main/src/utils/device_activator.py`
- 文档: `py-xiaozhi-main/documents/docs/guide/设备激活流程.md`

## ⚠️ 注意事项

1. **隐私**: 设备指纹和序列号基于硬件信息生成,请确保用户知情并同意
2. **安全**: HMAC 密钥存储在本地文件,建议在生产环境中使用 Android Keystore
3. **兼容性**: Android 6.0+ 无法获取真实 MAC 地址,使用 Android ID 生成伪 MAC
4. **调试**: 所有敏感信息都通过 Log 输出,在生产环境中建议禁用详细日志

## 📄 许可

与主项目保持一致。


