# AI语音助手应用集成指南

## 📋 文档概述

本文档面向设备供应商，提供将AI语音助手应用集成到Android系统固件中的完整指南，包括签名配置、权限设置、系统集成等关键步骤。

---

## 1. 应用基本信息

### 1.1 应用标识
- **包名**: `com.ai.voice`
- **应用名称**: VoiceAssistant
- **当前版本**: 3.2 (versionCode: 16)
- **最低Android版本**: Android 8.0 (API 26)
- **目标Android版本**: Android 14 (API 36)

### 1.2 核心功能
- ✅ 语音唤醒（Hey Nudget唤醒词检测）
- ✅ 语音识别（ASR）
- ✅ 语音合成（TTS）
- ✅ 设备控制（通过Hyundai IT API）
- ✅ 开机自启动
- ✅ 悬浮窗UI

---

## 2. 系统集成要求

### 2.1 系统应用配置

#### 2.1.1 sharedUserId配置
应用已配置为系统应用：
```xml
<manifest android:sharedUserId="android.uid.system">
```

**重要说明**：
- 必须使用**平台密钥（platform key）**签名
- 建议安装到 `/system/priv-app/` 目录
- 修改`sharedUserId`会导致已安装版本无法升级，需要卸载并清除数据

#### 2.1.2 安装位置
```bash
# 将APK复制到系统分区
adb root
adb remount
adb push app-release.apk /system/priv-app/VoiceAssistant/VoiceAssistant.apk
adb shell chmod 644 /system/priv-app/VoiceAssistant/VoiceAssistant.apk
adb reboot
```

### 2.2 权限配置

#### 2.2.1 必需权限列表
应用需要以下权限：

| 权限名称 | 类型 | 说明 |
|---------|------|------|
| `RECORD_AUDIO` | 危险权限 | 录音权限，用于语音识别和唤醒 |
| `POST_NOTIFICATIONS` | 危险权限 | 通知权限（Android 13+） |
| `SYSTEM_ALERT_WINDOW` | AppOps | 悬浮窗权限 |
| `FOREGROUND_SERVICE` | 普通权限 | 前台服务 |
| `FOREGROUND_SERVICE_MICROPHONE` | 普通权限 | 麦克风前台服务 |
| `RECEIVE_BOOT_COMPLETED` | 普通权限 | 开机自启动 |
| `QUERY_ALL_PACKAGES` | 签名权限 | 查询所有应用（用于技能） |

#### 2.2.2 权限默认授予配置

**方法1: 使用privapp-permissions.xml（推荐）**

在设备固件的 `/system/etc/permissions/` 目录创建 `privapp-permissions.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<permissions>
    <privapp-permissions package="com.ai.voice">
        <permission name="android.permission.RECEIVE_BOOT_COMPLETED"/>
        <permission name="android.permission.FOREGROUND_SERVICE"/>
        <permission name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>
        <permission name="android.permission.QUERY_ALL_PACKAGES"/>
        <permission name="android.permission.USE_FULL_SCREEN_INTENT"/>
    </privapp-permissions>
</permissions>
```

**方法2: 使用default-permissions.xml**

在 `/system/etc/default-permissions/` 目录创建 `default-permissions-com.ai.voice.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<exceptions>
    <exception package="com.ai.voice">
        <permission name="android.permission.RECORD_AUDIO" fixed="true"/>
        <permission name="android.permission.POST_NOTIFICATIONS" fixed="true"/>
    </exception>
</exceptions>
```

**方法3: 使用init脚本（临时方案）**

在设备启动脚本中添加：
```bash
# 授予录音权限
pm grant com.ai.voice android.permission.RECORD_AUDIO

# 授予通知权限（Android 13+）
pm grant com.ai.voice android.permission.POST_NOTIFICATIONS

# 授予悬浮窗权限（AppOps）
appops set com.ai.voice SYSTEM_ALERT_WINDOW allow
```

---

## 3. 签名配置

### 3.1 平台密钥签名（必需）

作为系统应用，必须使用设备制造商的**平台密钥（platform key）**签名。

#### 3.1.1 平台密钥位置
通常位于：
- `/build/target/product/security/platform.pk8` (私钥)
- `/build/target/product/security/platform.x509.pem` (证书)

#### 3.1.2 签名命令

**使用signapk工具签名**：
```bash
# 使用平台密钥签名
java -jar signapk.jar \
    platform.x509.pem \
    platform.pk8 \
    app-release-unsigned.apk \
    app-release-signed.apk
```

**使用apksigner签名**：
```bash
# 将平台密钥转换为JKS格式（如果使用apksigner）
openssl pkcs8 -inform DER -nocrypt -in platform.pk8 -out platform.key
openssl pkcs12 -export -in platform.x509.pem -inkey platform.key -out platform.p12 -name platform
keytool -importkeystore -destkeystore platform.jks -srckeystore platform.p12 -srcstoretype PKCS12

# 使用apksigner签名
apksigner sign \
    --ks platform.jks \
    --ks-key-alias platform \
    --ks-pass pass:your_password \
    --key-pass pass:your_password \
    app-release-unsigned.apk
```

### 3.2 当前签名配置

项目中的签名配置（`app/build.gradle.kts`）：
```kotlin
signingConfigs {
    create("release") {
        storeFile = file("release.keystore")
        storePassword = "android123"  // ⚠️ 仅用于开发测试
        keyAlias = "release"
        keyPassword = "android123"
    }
    create("pad") {
        storeFile = file("/Users/user/tool/docsample/pad/pad.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
    }
}
```

**⚠️ 重要**：
- 上述配置仅用于开发测试
- **生产环境必须使用平台密钥签名**
- 请将签名配置替换为您的平台密钥

---

## 4. 构建Release版本

### 4.1 构建步骤

```bash
# 1. 设置Java环境（Java 17）
export JAVA_HOME="/path/to/java17"
export PATH="$JAVA_HOME/bin:$PATH"

# 2. 清理构建
./gradlew clean

# 3. 构建Release APK
./gradlew assembleRelease

# 4. 输出位置
# app/build/outputs/apk/release/app-release.apk
```

### 4.2 使用平台密钥重新签名

```bash
# 使用平台密钥重新签名
java -jar signapk.jar \
    platform.x509.pem \
    platform.pk8 \
    app/build/outputs/apk/release/app-release.apk \
    VoiceAssistant-signed.apk
```

### 4.3 验证签名

```bash
# 验证APK签名
apksigner verify --verbose VoiceAssistant-signed.apk

# 查看签名信息
unzip -p VoiceAssistant-signed.apk META-INF/MANIFEST.MF | head -20
```

---

## 5. 系统集成步骤

### 5.1 准备工作

1. **准备平台密钥**
   - 从设备固件构建系统获取平台密钥
   - 确保密钥安全保管

2. **准备APK文件**
   - 构建Release版本APK
   - 使用平台密钥签名

3. **准备权限配置文件**
   - 创建 `privapp-permissions.xml`
   - 创建 `default-permissions.xml`（可选）

### 5.2 集成到固件

#### 步骤1: 创建应用目录
```bash
# 在固件源码中创建应用目录
mkdir -p device/your_vendor/your_device/prebuilt/VoiceAssistant
```

#### 步骤2: 复制APK和权限配置
```bash
# 复制APK
cp VoiceAssistant-signed.apk device/your_vendor/your_device/prebuilt/VoiceAssistant/VoiceAssistant.apk

# 复制权限配置
cp privapp-permissions.xml device/your_vendor/your_device/prebuilt/VoiceAssistant/
```

#### 步骤3: 创建Android.mk或Android.bp

**Android.mk示例**：
```makefile
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := VoiceAssistant
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_BUILT_MODULE_STEM := package.apk
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_PRIVILEGED_MODULE := true
LOCAL_CERTIFICATE := platform
LOCAL_SRC_FILES := VoiceAssistant.apk
include $(BUILD_PREBUILT)
```

**Android.bp示例**：
```blueprint
android_app_prebuilt {
    name: "VoiceAssistant",
    privileged: true,
    certificate: "platform",
    src: "VoiceAssistant.apk",
}
```

#### 步骤4: 配置权限文件

在设备配置中添加权限文件：
```makefile
# Android.mk
PRODUCT_COPY_FILES += \
    device/your_vendor/your_device/prebuilt/VoiceAssistant/privapp-permissions.xml:system/etc/permissions/privapp-permissions-com.ai.voice.xml
```

### 5.3 编译固件

```bash
# 编译固件（包含应用）
source build/envsetup.sh
lunch your_device-userdebug  # 或 user
make -j8
```

---

## 6. 安装和验证

### 6.1 手动安装（测试）

```bash
# 1. 获取root权限
adb root
adb remount

# 2. 创建应用目录
adb shell mkdir -p /system/priv-app/VoiceAssistant

# 3. 推送APK
adb push VoiceAssistant-signed.apk /system/priv-app/VoiceAssistant/VoiceAssistant.apk

# 4. 设置权限
adb shell chmod 644 /system/priv-app/VoiceAssistant/VoiceAssistant.apk
adb shell chown system:system /system/priv-app/VoiceAssistant/VoiceAssistant.apk

# 5. 推送权限配置
adb push privapp-permissions.xml /system/etc/permissions/privapp-permissions-com.ai.voice.xml
adb shell chmod 644 /system/etc/permissions/privapp-permissions-com.ai.voice.xml

# 6. 授予运行时权限
adb shell pm grant com.ai.voice android.permission.RECORD_AUDIO
adb shell pm grant com.ai.voice android.permission.POST_NOTIFICATIONS
adb shell appops set com.ai.voice SYSTEM_ALERT_WINDOW allow

# 7. 重启设备
adb reboot
```

### 6.2 验证安装

```bash
# 检查应用是否安装
adb shell pm list packages | grep com.ai.voice

# 检查应用UID（应为system: 1000）
adb shell dumpsys package com.ai.voice | grep userId

# 检查权限状态
adb shell dumpsys package com.ai.voice | grep -A 5 "granted=true"

# 检查服务状态
adb shell dumpsys activity services | grep WakeService
adb shell dumpsys activity services | grep EnhancedFloatingWindowService
```

### 6.3 功能测试

1. **开机自启动测试**
   ```bash
   # 重启设备
   adb reboot
   
   # 等待设备启动后检查服务
   adb shell ps | grep com.ai.voice
   adb shell dumpsys activity services | grep WakeService
   ```

2. **唤醒词测试**
   - 说出唤醒词，检查日志：
   ```bash
   adb logcat | grep "唤醒词检测成功"
   ```

3. **语音识别测试**
   - 唤醒后说话，检查识别结果：
   ```bash
   adb logcat | grep "ASR识别结果"
   ```

4. **命令执行测试**
   - 说出命令，检查执行日志：
   ```bash
   adb logcat | grep "命令执行"
   ```

---

## 7. 关键日志说明

### 7.1 Release版本保留的日志

应用在Release版本会保留以下关键日志（使用Log.i级别）：

| 日志类型 | 日志标签 | 示例 |
|---------|---------|------|
| ASR识别结果 | `🎤[Tag]` | `ASR识别结果: "打开空调" (置信度: 0.95)` |
| 唤醒词检测成功 | `🔊[Tag]` | `✅ 唤醒词检测成功` |
| 命令执行 | `✅[Tag]` | `命令执行: [device_control] "打开空调" -> 已执行` |
| 错误日志 | `🔊[Tag]` / `🎤[Tag]` | 所有错误日志（Log.e级别） |

### 7.2 日志过滤命令

```bash
# 查看ASR识别结果
adb logcat | grep "ASR识别结果"

# 查看唤醒词检测
adb logcat | grep "唤醒词检测成功"

# 查看命令执行
adb logcat | grep "命令执行"

# 查看所有关键日志
adb logcat | grep -E "(ASR识别结果|唤醒词检测成功|命令执行)"
```

---

## 8. 常见问题排查

### 8.1 应用无法启动

**症状**: 应用安装后无法启动

**排查步骤**:
1. 检查签名是否匹配：
   ```bash
   adb shell dumpsys package com.ai.voice | grep signatures
   ```

2. 检查UID是否正确：
   ```bash
   adb shell dumpsys package com.ai.voice | grep userId
   # 应该显示: userId=1000 (system)
   ```

3. 检查日志：
   ```bash
   adb logcat | grep -E "(FATAL|AndroidRuntime|com.ai.voice)"
   ```

### 8.2 权限问题

**症状**: 录音权限被拒绝

**解决方案**:
1. 确认权限配置文件已正确安装
2. 手动授予权限：
   ```bash
   adb shell pm grant com.ai.voice android.permission.RECORD_AUDIO
   ```

### 8.3 开机自启动失败

**症状**: 设备重启后应用未自动启动

**排查步骤**:
1. 检查BootBroadcastReceiver是否注册：
   ```bash
   adb shell dumpsys package com.ai.voice | grep BootBroadcastReceiver
   ```

2. 检查开机广播日志：
   ```bash
   adb logcat | grep BootBroadcastReceiver
   ```

3. 确认RECEIVE_BOOT_COMPLETED权限已授予

### 8.4 Protobuf运行时错误

**症状**: 应用启动时崩溃，错误信息包含"Field theme_ not found"

**解决方案**:
- 确保ProGuard规则正确配置
- 检查 `app/proguard-rules.pro` 中是否包含Protobuf保护规则
- 重新构建Release版本

---

## 9. 版本更新

### 9.1 更新流程

1. **构建新版本**
   ```bash
   ./gradlew clean assembleRelease
   ```

2. **使用平台密钥签名**
   ```bash
   java -jar signapk.jar platform.x509.pem platform.pk8 \
       app-release.apk VoiceAssistant-v3.3.apk
   ```

3. **更新固件中的APK**
   - 替换固件中的APK文件
   - 重新编译固件

### 9.2 版本兼容性

**⚠️ 重要**: 
- 如果修改了`sharedUserId`，需要卸载旧版本并清除数据
- 如果修改了包名，需要卸载旧版本
- Protobuf数据结构变更可能导致数据迁移问题

---

## 10. 技术支持

### 10.1 日志收集

收集完整日志用于问题排查：
```bash
# 收集所有相关日志
adb logcat -d > voice_assistant_log.txt

# 收集崩溃日志
adb logcat -d | grep -A 50 "FATAL EXCEPTION" > crash_log.txt

# 收集关键功能日志
adb logcat -d | grep -E "(ASR识别结果|唤醒词检测成功|命令执行|FATAL|Exception)" > key_logs.txt
```

### 10.2 联系信息

如有集成问题，请提供：
1. 设备型号和Android版本
2. 完整的错误日志
3. 权限配置情况
4. 签名信息（不包含密钥）

---

## 附录A: 文件清单

### A.1 必需文件
- `VoiceAssistant.apk` - 应用APK文件
- `privapp-permissions.xml` - 权限配置文件
- `platform.x509.pem` - 平台证书（用于签名）
- `platform.pk8` - 平台私钥（用于签名）

### A.2 可选文件
- `default-permissions-com.ai.voice.xml` - 默认权限配置
- `Android.mk` 或 `Android.bp` - 构建配置文件

---

## 附录B: 签名密钥管理

### B.1 密钥安全

**⚠️ 安全警告**：
- 平台密钥是设备安全的核心，必须严格保密
- 不要将密钥提交到版本控制系统
- 使用密钥管理系统（KMS）存储密钥
- 定期轮换密钥

### B.2 密钥备份

```bash
# 备份平台密钥（加密存储）
tar -czf platform-keys-backup-$(date +%Y%m%d).tar.gz \
    platform.x509.pem platform.pk8 \
    --encrypt --password=your_secure_password
```

---

## 附录C: 构建配置示例

### C.1 完整Android.mk示例

```makefile
LOCAL_PATH := $(call my-dir)

# VoiceAssistant应用
include $(CLEAR_VARS)
LOCAL_MODULE := VoiceAssistant
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_BUILT_MODULE_STEM := package.apk
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_PRIVILEGED_MODULE := true
LOCAL_CERTIFICATE := platform
LOCAL_SRC_FILES := VoiceAssistant.apk
LOCAL_OVERRIDES_PACKAGES := 
include $(BUILD_PREBUILT)

# 权限配置文件
include $(CLEAR_VARS)
LOCAL_MODULE := privapp-permissions-com.ai.voice.xml
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_PATH := $(TARGET_OUT_ETC)/permissions
LOCAL_SRC_FILES := privapp-permissions.xml
include $(BUILD_PREBUILT)
```

### C.2 设备配置集成

在 `device.mk` 中添加：
```makefile
# VoiceAssistant应用
PRODUCT_PACKAGES += \
    VoiceAssistant \
    privapp-permissions-com.ai.voice.xml
```

---

**文档版本**: 1.0  
**最后更新**: 2025-11-11  
**维护者**: AI Voice Team

