# OTA更新系统设计文档

## 📋 概述

本文档描述系统预装应用的OTA（Over-The-Air）更新系统设计方案。由于应用预装在系统分区，需要特殊的更新机制来支持后续迭代。

## 🎯 设计目标

1. **自动化更新**：支持后台检查和下载更新
2. **安全性**：严格验证更新包签名和完整性
3. **可靠性**：支持更新失败回滚和断点续传
4. **用户体验**：静默更新或用户确认更新
5. **兼容性**：与现有激活系统集成

## 🏗️ 架构设计

### 整体架构

```
┌─────────────────────────────────────────────────────────┐
│                  OTA更新服务架构                          │
└─────────────────────────────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        ▼                   ▼                   ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│ 版本检查模块  │   │ 下载管理模块  │   │ 安装管理模块  │
│              │   │              │   │              │
│ - 检查更新   │   │ - 下载APK    │   │ - 验证签名   │
│ - 版本比较   │   │ - 断点续传   │   │ - 安装更新   │
│ - 更新策略   │   │ - 进度回调   │   │ - 回滚机制   │
└──────────────┘   └──────────────┘   └──────────────┘
        │                   │                   │
        └───────────────────┼───────────────────┘
                            │
                            ▼
                    ┌──────────────┐
                    │  OTA服务器   │
                    │              │
                    │ - 版本信息   │
                    │ - APK下载    │
                    │ - 更新日志   │
                    └──────────────┘
```

### 核心模块

#### 1. OtaUpdateManager（OTA更新管理器）

**职责**：
- 协调版本检查、下载和安装流程
- 管理更新状态和策略
- 提供更新进度回调

**位置**：`app/src/main/kotlin/com/ai/voice/ota/OtaUpdateManager.kt`

#### 2. VersionChecker（版本检查器）

**职责**：
- 定期检查服务器版本
- 比较本地版本和服务器版本
- 判断是否需要更新

**位置**：`app/src/main/kotlin/com/ai/voice/ota/VersionChecker.kt`

#### 3. UpdateDownloader（更新下载器）

**职责**：
- 下载更新APK
- 支持断点续传
- 显示下载进度

**位置**：`app/src/main/kotlin/com/ai/voice/ota/UpdateDownloader.kt`

#### 4. SystemInstaller（系统安装器）

**职责**：
- 验证APK签名
- 安装到系统分区
- 处理安装失败回滚

**位置**：`app/src/main/kotlin/com/ai/voice/ota/SystemInstaller.kt`

## 📡 API设计

### 版本检查API

**端点**：`GET /api/ota/check-update`

**请求参数**：
```json
{
  "packageName": "com.ai.voice",
  "currentVersion": "3.19.44",
  "versionCode": 1660,
  "deviceModel": "SM-G998B",
  "androidVersion": "13"
}
```

**响应**：
```json
{
  "hasUpdate": true,
  "updateInfo": {
    "versionName": "3.19.45",
    "versionCode": 1661,
    "downloadUrl": "https://ota.example.com/releases/VoiceAssistant-3.19.45-System-Release.apk",
    "fileSize": 15728640,
    "md5": "a1b2c3d4e5f6...",
    "sha256": "1a2b3c4d5e6f...",
    "releaseNotes": "修复了若干bug，优化了性能",
    "forceUpdate": false,
    "minAndroidVersion": 11,
    "updateStrategy": "silent" // silent, prompt, manual
  }
}
```

### 下载统计API

**端点**：`POST /api/ota/download-stats`

**请求**：
```json
{
  "packageName": "com.ai.voice",
  "versionCode": 1661,
  "downloadId": "uuid-1234",
  "status": "completed", // started, completed, failed
  "error": null
}
```

## 🔐 安全设计

### 1. 签名验证

**要求**：
- 更新包必须使用系统签名（starry keystore）
- 验证APK签名与预装版本一致
- 使用SHA-256校验和验证文件完整性

**实现**：
```kotlin
fun verifyApkSignature(apkPath: String): Boolean {
    val packageManager = context.packageManager
    val packageInfo = packageManager.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNATURES)
    
    // 验证签名与系统签名一致
    val signatures = packageInfo?.signatures
    return signatures?.any { signature ->
        verifySystemSignature(signature)
    } ?: false
}
```

### 2. 完整性校验

**要求**：
- 下载后验证MD5和SHA-256
- 防止中间人攻击和文件损坏

**实现**：
```kotlin
fun verifyFileIntegrity(filePath: String, expectedMd5: String, expectedSha256: String): Boolean {
    val actualMd5 = calculateMD5(filePath)
    val actualSha256 = calculateSHA256(filePath)
    
    return actualMd5 == expectedMd5 && actualSha256 == expectedSha256
}
```

### 3. HTTPS通信

**要求**：
- 所有API通信使用HTTPS
- 证书固定（Certificate Pinning）
- 防止中间人攻击

## 📥 更新流程

### 流程1：自动检查更新

```
1. 应用启动或定时任务触发
   ↓
2. VersionChecker.checkUpdate()
   - 读取本地版本号
   - 请求服务器版本信息
   ↓
3. 比较版本号
   - versionCode > 当前版本 → 需要更新
   ↓
4. 根据更新策略处理
   - silent: 自动下载并安装
   - prompt: 提示用户确认
   - manual: 仅通知用户
```

### 流程2：下载更新

```
1. UpdateDownloader.download()
   - 创建下载任务
   - 检查存储空间
   ↓
2. 开始下载
   - 支持断点续传
   - 显示下载进度
   ↓
3. 下载完成
   - 验证文件完整性（MD5/SHA-256）
   - 验证APK签名
   ↓
4. 保存到临时目录
   - /data/data/com.ai.voice/cache/ota/
```

### 流程3：安装更新

```
1. SystemInstaller.install()
   - 检查是否为系统应用
   - 验证APK签名
   ↓
2. 备份当前版本
   - 保存到 /data/data/com.ai.voice/backup/
   ↓
3. 安装新版本
   - 方法A：通过PackageInstaller（需要系统权限）
   - 方法B：通过系统更新包（推荐）
   ↓
4. 验证安装
   - 检查版本号
   - 检查签名
   ↓
5. 清理临时文件
```

## 🔄 更新策略

### 策略1：静默更新（Silent）

**适用场景**：
- 小版本更新（bug修复）
- 非强制更新
- 用户无感知

**流程**：
1. 后台检查更新
2. 自动下载
3. 空闲时安装
4. 重启后生效

### 策略2：提示更新（Prompt）

**适用场景**：
- 功能更新
- 需要用户确认
- 可延迟更新

**流程**：
1. 检查到更新
2. 显示更新通知
3. 用户点击后下载
4. 下载完成后提示安装
5. 用户确认后安装

### 策略3：强制更新（Force）

**适用场景**：
- 安全漏洞修复
- 重大版本更新
- 必须立即更新

**流程**：
1. 检查到强制更新
2. 显示强制更新对话框
3. 无法取消，必须更新
4. 下载并安装
5. 重启设备

## 🛠️ 实现细节

### 1. 版本检查频率

**配置**：
- 应用启动时检查一次
- 每24小时检查一次
- 手动触发检查

**实现**：
```kotlin
class OtaUpdateManager {
    private val checkInterval = 24.hours
    
    fun schedulePeriodicCheck() {
        WorkManager.getInstance(context)
            .enqueuePeriodicWork(
                PeriodicWorkRequestBuilder<OtaCheckWorker>(checkInterval)
                    .build()
            )
    }
}
```

### 2. 下载管理

**特性**：
- 支持断点续传
- 下载队列管理
- 网络状态监听
- 存储空间检查

**实现**：
```kotlin
class UpdateDownloader {
    fun download(url: String, callback: DownloadCallback) {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$downloaded-")
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                // 处理下载进度
                callback.onProgress(progress)
            }
        })
    }
}
```

### 3. 系统安装

**方法A：通过PackageInstaller（需要系统权限）**

```kotlin
fun installViaPackageInstaller(apkPath: String) {
    val packageInstaller = context.packageManager.packageInstaller
    val sessionParams = PackageInstaller.SessionParams(
        PackageInstaller.SessionParams.MODE_FULL_INSTALL
    )
    val sessionId = packageInstaller.createSession(sessionParams)
    val session = packageInstaller.openSession(sessionId)
    
    // 写入APK
    session.openWrite("base.apk", 0, -1).use { out ->
        File(apkPath).inputStream().use { input ->
            input.copyTo(out)
        }
    }
    
    // 提交安装
    session.commit(IntentSender)
}
```

**方法B：通过系统更新包（推荐）**

```kotlin
fun installViaSystemUpdate(apkPath: String) {
    // 1. 将APK推送到系统分区
    // 2. 设置权限
    // 3. 重启设备或通知系统扫描
}
```

### 4. 回滚机制

**触发条件**：
- 安装后应用无法启动
- 版本号验证失败
- 签名验证失败

**实现**：
```kotlin
class SystemInstaller {
    private fun backupCurrentVersion() {
        val currentApk = getCurrentApkPath()
        val backupPath = File(context.filesDir, "backup/${getVersionCode()}.apk")
        currentApk.copyTo(backupPath, overwrite = true)
    }
    
    fun rollback() {
        val backupApk = findLatestBackup()
        if (backupApk != null) {
            installApk(backupApk.absolutePath)
        }
    }
}
```

## 📊 状态管理

### 更新状态

```kotlin
enum class UpdateStatus {
    IDLE,              // 空闲
    CHECKING,          // 检查中
    UPDATE_AVAILABLE,  // 有更新可用
    DOWNLOADING,       // 下载中
    DOWNLOADED,        // 已下载
    INSTALLING,        // 安装中
    INSTALLED,         // 已安装
    FAILED,            // 失败
    ROLLBACK           // 回滚中
}
```

### 状态流转

```
IDLE → CHECKING → UPDATE_AVAILABLE → DOWNLOADING → DOWNLOADED → INSTALLING → INSTALLED
  │                                                                              │
  └──────────────────────────────────────────────────────────────────────────────┘
                                                                                  │
                                                                                  ▼
                                                                              FAILED
                                                                                  │
                                                                                  ▼
                                                                              ROLLBACK
```

## 🔌 与现有系统集成

### 1. 激活系统集成

**检查更新时机**：
- 激活验证成功后检查更新
- 避免在激活流程中干扰用户

**实现**：
```kotlin
class LicenseActivationManager {
    suspend fun activate(activationCode: String) {
        // ... 激活逻辑 ...
        
        // 激活成功后检查更新
        if (activationSuccess) {
            otaUpdateManager.checkUpdateAsync()
        }
    }
}
```

### 2. 版本管理集成

**使用现有VERSION文件**：
- 读取当前版本号
- 更新后更新VERSION文件

**实现**：
```kotlin
class VersionChecker {
    fun getCurrentVersion(): VersionInfo {
        val versionProps = Properties()
        File("VERSION").inputStream().use { versionProps.load(it) }
        return VersionInfo(
            versionName = versionProps.getProperty("VERSION_NAME"),
            versionCode = versionProps.getProperty("VERSION_CODE").toInt()
        )
    }
}
```

## 📱 用户界面

### 1. 更新通知

**静默更新**：
- 仅显示系统通知
- 不打断用户操作

**提示更新**：
- 显示更新对话框
- 显示更新日志
- 提供"立即更新"和"稍后提醒"选项

**强制更新**：
- 全屏对话框
- 无法取消
- 显示更新进度

### 2. 更新设置

**位置**：设置 → 关于 → 系统更新

**功能**：
- 手动检查更新
- 查看更新历史
- 配置更新策略（如果允许）
- 查看当前版本信息

## 🧪 测试策略

### 1. 单元测试

- 版本比较逻辑
- 签名验证逻辑
- 完整性校验逻辑

### 2. 集成测试

- 端到端更新流程
- 网络异常处理
- 安装失败回滚

### 3. 压力测试

- 并发下载
- 大文件下载
- 网络中断恢复

## 📝 配置示例

### OTA服务器配置

```kotlin
object OtaConfig {
    const val BASE_URL = "https://ota.example.com"
    const val CHECK_UPDATE_ENDPOINT = "/api/ota/check-update"
    const val DOWNLOAD_STATS_ENDPOINT = "/api/ota/download-stats"
    
    // 更新检查间隔（24小时）
    val CHECK_INTERVAL = 24.hours
    
    // 下载超时（10分钟）
    val DOWNLOAD_TIMEOUT = 10.minutes
    
    // 重试次数
    const val MAX_RETRY_COUNT = 3
}
```

### 更新策略配置

```kotlin
data class UpdateStrategy(
    val silent: Boolean = false,      // 是否静默更新
    val force: Boolean = false,       // 是否强制更新
    val prompt: Boolean = true,       // 是否提示用户
    val minBatteryLevel: Int = 20,    // 最低电量（%）
    val requireWifi: Boolean = false  // 是否需要WiFi
)
```

## 🚨 错误处理

### 常见错误及处理

1. **网络错误**
   - 重试机制（最多3次）
   - 显示错误提示
   - 记录错误日志

2. **存储空间不足**
   - 检查可用空间
   - 提示用户清理空间
   - 暂停下载

3. **签名验证失败**
   - 拒绝安装
   - 删除下载文件
   - 报告错误

4. **安装失败**
   - 自动回滚
   - 恢复备份版本
   - 报告错误

## 📈 监控和统计

### 监控指标

1. **更新检查成功率**
2. **下载成功率**
3. **安装成功率**
4. **平均下载时间**
5. **更新失败原因分布**

### 统计上报

```kotlin
data class OtaStats(
    val event: String,           // check, download, install
    val versionCode: Int,
    val status: String,          // success, failed
    val error: String?,
    val duration: Long,          // 耗时（毫秒）
    val fileSize: Long           // 文件大小（字节）
)
```

## 🔄 版本兼容性

### 向后兼容

- 支持从任意旧版本更新到新版本
- 处理数据迁移（如果需要）
- 保持激活状态

### 向前兼容

- 新版本API向后兼容
- 渐进式功能启用
- 特性开关（Feature Flags）

## 📚 相关文档

- [系统预装版本构建说明.md](./系统预装版本构建说明.md)
- [版本管理文档.md](./VERSION_MANAGEMENT.md)
- [激活流程使用说明.md](./激活流程使用说明.md)

---

**文档版本**: 1.0  
**最后更新**: 2025年11月28日  
**维护者**: Dicio开发团队

