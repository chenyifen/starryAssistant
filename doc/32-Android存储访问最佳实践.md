# Android 存储访问最佳实践与问题解决方案

## 📋 问题分析

### 当前问题

1. **Native 库无法访问 `/sdcard/` 路径**
   - Android 10+ Scoped Storage 限制
   - SELinux 策略阻止 native 代码访问外部存储
   - 即使 `File.exists()` 返回 `true`,native 库仍然可能无法读取

2. **路径访问不一致**
   - 不同 Android 版本的存储路径不同
   - 不同设备厂商的路径实现不同
   - 权限检查不完整

3. **文件复制失败**
   - 权限问题
   - SELinux 上下文问题
   - 跨分区复制性能问题

## 🎯 Android 存储模型

### 1. 应用私有存储 (Internal Storage)

**路径**: `Context.filesDir` (例如: `/data/data/org.stypox.dicio.master/files/`)

**特点**:
- ✅ **无需权限** - 应用私有,始终可访问
- ✅ **Native 安全** - Native 库可以无障碍访问
- ✅ **SELinux 兼容** - 正确的 SELinux 上下文
- ❌ **用户不可见** - 普通用户无法通过文件管理器访问
- ❌ **卸载清除** - 应用卸载时会被删除

**适用场景**:
- ✅ Native 库加载的模型文件 (如 ONNX)
- ✅ 运行时缓存
- ✅ 临时文件

### 2. 应用私有外部存储 (App-Specific External Storage)

**路径**: `Context.getExternalFilesDir(null)` (例如: `/sdcard/Android/data/org.stypox.dicio.master/files/`)

**特点**:
- ✅ **无需权限** (Android 10+) - 应用私有外部存储
- ✅ **用户可见** (通过文件管理器)
- ⚠️ **Native 访问有限** - 部分设备/系统版本可能限制
- ❌ **卸载清除** - 应用卸载时会被删除

**适用场景**:
- ✅ 用户可下载的模型文件
- ✅ 大文件存储 (不占用内部存储空间)
- ⚠️ 需要复制到内部存储后供 Native 使用

### 3. 共享存储 (Shared Storage)

**路径**: `/sdcard/` 或 `/storage/emulated/0/`

**特点**:
- ❌ **需要权限** (Android 10 以下需要 `READ_EXTERNAL_STORAGE`)
- ❌ **Scoped Storage** (Android 10+ 限制直接路径访问)
- ❌ **Native 访问受限** - SELinux 和 Scoped Storage 限制
- ✅ **持久化** - 应用卸载后仍然保留
- ✅ **跨应用共享** - 可被其他应用访问

**适用场景**:
- ⚠️ **不推荐用于 Native 库** - 访问受限,不可靠
- ⚠️ 仅用于用户手动管理的文件

### 4. MediaStore API (Android 10+)

**特点**:
- ✅ **Scoped Storage 兼容** - 正确的方式访问媒体文件
- ✅ **无需权限** (自己的文件)
- ❌ **复杂性高** - 需要使用 ContentResolver
- ❌ **仅适用于媒体文件**

**适用场景**:
- ✅ 图片、视频、音频文件
- ❌ 模型文件 (不适用)

## 🔧 推荐解决方案

### 方案一: 混合存储策略 (★★★★★ 推荐)

```kotlin
object StorageStrategy {
    /**
     * 1. 用户下载/管理区域: App-Specific External Storage
     *    - 用户通过 adb push 或下载放置模型
     *    - 路径: /sdcard/Android/data/pkg/files/models/
     */
    fun getUserModelsPath(context: Context): File {
        return File(context.getExternalFilesDir("models")!!)
    }
    
    /**
     * 2. Native 运行时区域: Internal Storage
     *    - 自动从用户区复制到此处
     *    - 路径: /data/data/pkg/files/models/
     */
    fun getNativeModelsPath(context: Context): File {
        return File(context.filesDir, "models")
    }
    
    /**
     * 3. 模型加载流程
     */
    fun prepareModelForNative(
        context: Context,
        modelType: String, // e.g., "kws", "tts", "asr"
        onProgress: (String) -> Unit = {}
    ): File? {
        val userPath = File(getUserModelsPath(context), modelType)
        val nativePath = File(getNativeModelsPath(context), modelType)
        
        // 检查用户区是否有模型
        if (!userPath.exists() || !userPath.isDirectory) {
            onProgress("❌ 模型不存在: ${userPath.absolutePath}")
            return null
        }
        
        // 检查 Native 区是否已有最新版本
        if (nativePath.exists() && isUpToDate(userPath, nativePath)) {
            onProgress("✅ 使用已缓存的模型: ${nativePath.absolutePath}")
            return nativePath
        }
        
        // 复制到 Native 区
        return try {
            onProgress("📦 复制模型到内部存储...")
            copyDirectory(userPath, nativePath)
            onProgress("✅ 模型准备完成: ${nativePath.absolutePath}")
            nativePath
        } catch (e: Exception) {
            onProgress("❌ 复制失败: ${e.message}")
            null
        }
    }
    
    private fun isUpToDate(source: File, dest: File): Boolean {
        // 简单版本: 比较修改时间和文件数量
        if (!dest.exists()) return false
        
        val sourceFiles = source.listFiles()?.size ?: 0
        val destFiles = dest.listFiles()?.size ?: 0
        
        return sourceFiles == destFiles && 
               source.lastModified() <= dest.lastModified()
    }
    
    private fun copyDirectory(source: File, dest: File) {
        if (!dest.exists()) {
            dest.mkdirs()
        }
        
        source.listFiles()?.forEach { file ->
            val destFile = File(dest, file.name)
            if (file.isDirectory) {
                copyDirectory(file, destFile)
            } else {
                file.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}
```

**优势**:
- ✅ 用户友好 (可通过文件管理器或 adb 访问)
- ✅ Native 安全 (运行时使用内部存储)
- ✅ 权限简单 (无需存储权限)
- ✅ 兼容性好 (所有 Android 版本)

### 方案二: 纯 Assets 打包 (★★★★☆)

**适用场景**: 模型文件较小,不需要用户自定义

```kotlin
// 在 build.gradle.kts 中
android {
    sourceSets {
        getByName("withModels") {
            assets.srcDirs("path/to/models")
        }
    }
}
```

**优势**:
- ✅ 零配置,开箱即用
- ✅ Native 直接访问 (通过 AssetManager)
- ✅ 无权限问题

**劣势**:
- ❌ APK 体积大
- ❌ 无法动态更新模型
- ❌ 无法用户自定义

### 方案三: 云端下载 + Internal Storage (★★★☆☆)

```kotlin
class ModelDownloader(private val context: Context) {
    suspend fun downloadModel(
        modelUrl: String,
        modelType: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val destFile = File(context.filesDir, "models/$modelType")
        destFile.parentFile?.mkdirs()
        
        try {
            // 使用 OkHttp 或 Retrofit 下载
            val response = client.newCall(Request.Builder().url(modelUrl).build()).execute()
            val body = response.body ?: return@withContext null
            
            body.byteStream().use { input ->
                destFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var totalRead = 0L
                    var read: Int
                    
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        totalRead += read
                        onProgress((totalRead * 100 / body.contentLength()).toInt())
                    }
                }
            }
            
            destFile
        } catch (e: Exception) {
            destFile.delete()
            null
        }
    }
}
```

## 📝 具体实施建议

### 1. 重构 ModelPathManager

```kotlin
object ModelPathManager {
    /**
     * 获取用户可访问的模型目录 (用于 adb push 或下载)
     */
    fun getUserModelsDir(context: Context, modelType: String): File {
        return File(context.getExternalFilesDir("models"), modelType)
    }
    
    /**
     * 获取 Native 运行时模型目录
     */
    fun getNativeModelsDir(context: Context, modelType: String): File {
        return File(context.filesDir, "models/$modelType")
    }
    
    /**
     * 准备模型供 Native 使用
     */
    fun prepareNativeModel(
        context: Context,
        modelType: String
    ): Result<File> {
        val userDir = getUserModelsDir(context, modelType)
        val nativeDir = getNativeModelsDir(context, modelType)
        
        return try {
            when {
                // 1. Native 目录已有最新模型
                nativeDir.exists() && isUpToDate(userDir, nativeDir) -> {
                    Log.d(TAG, "✅ 使用缓存的模型: $nativeDir")
                    Result.success(nativeDir)
                }
                
                // 2. 用户目录有模型,需要复制
                userDir.exists() && userDir.isDirectory -> {
                    Log.d(TAG, "📦 复制模型: $userDir -> $nativeDir")
                    copyDirectory(userDir, nativeDir)
                    Result.success(nativeDir)
                }
                
                // 3. 都没有模型
                else -> {
                    Result.failure(
                        FileNotFoundException(
                            "模型不存在,请通过以下方式提供模型:\n" +
                            "1. adb push models/* ${userDir.absolutePath}/\n" +
                            "2. 使用文件管理器复制到 ${userDir.absolutePath}/\n" +
                            "3. 使用 withModels 变体"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun isUpToDate(source: File, dest: File): Boolean {
        if (!source.exists() || !dest.exists()) return false
        
        // 比较文件数量和最后修改时间
        val sourceCount = source.walkTopDown().filter { it.isFile }.count()
        val destCount = dest.walkTopDown().filter { it.isFile }.count()
        
        return sourceCount == destCount && 
               source.lastModified() <= dest.lastModified()
    }
    
    private fun copyDirectory(source: File, dest: File) {
        if (!dest.exists()) dest.mkdirs()
        
        source.listFiles()?.forEach { file ->
            val destFile = File(dest, file.name)
            when {
                file.isDirectory -> copyDirectory(file, destFile)
                file.isFile -> file.copyTo(destFile, overwrite = true)
            }
        }
    }
    
    /**
     * 获取用户友好的模型推送说明
     */
    fun getModelSetupInstructions(context: Context, modelType: String): String {
        val userDir = getUserModelsDir(context, modelType)
        return """
            📋 模型设置说明 ($modelType):
            
            方法 1️⃣: 使用 adb push
            adb push models/$modelType/* ${userDir.absolutePath}/
            
            方法 2️⃣: 使用文件管理器
            1. 将模型文件复制到手机
            2. 使用文件管理器打开: ${userDir.absolutePath}/
            3. 粘贴模型文件
            
            方法 3️⃣: 使用 withModels 变体
            - 安装包含预置模型的 APK 版本
        """.trimIndent()
    }
}
```

### 2. 更新 SherpaOnnxWakeDevice

```kotlin
override fun tryLoad() {
    scope.launch(Dispatchers.IO) {
        try {
            _state.value = WakeState.Loading(false)
            
            // 1. 优先使用 Assets (withModels 变体)
            if (checkAssetsModelsAvailable()) {
                Log.d(TAG, "✅ 使用 Assets 中的模型")
                keywordSpotter = KeywordSpotter(
                    assetManager = appContext.assets,
                    config = createKwsConfig(useAssetManager = true)
                )
            } else {
                // 2. 使用文件系统模型 (noModels 变体)
                Log.d(TAG, "📂 使用文件系统模型")
                
                // 准备模型到 Native 可访问的位置
                val modelResult = ModelPathManager.prepareNativeModel(
                    context = appContext,
                    modelType = "sherpa_onnx_kws"
                )
                
                val modelDir = modelResult.getOrElse { error ->
                    _state.value = WakeState.ErrorLoading(error)
                    Log.e(TAG, "❌ 模型准备失败", error)
                    Log.e(TAG, ModelPathManager.getModelSetupInstructions(
                        appContext, "sherpa_onnx_kws"
                    ))
                    return@launch
                }
                
                // 使用内部存储路径创建 KeywordSpotter
                keywordSpotter = KeywordSpotter(
                    config = createKwsConfigWithPath(modelDir.absolutePath)
                )
            }
            
            stream = keywordSpotter?.createStream()
            
            if (keywordSpotter != null && stream != null) {
                _state.value = WakeState.Loaded
                Log.d(TAG, "✅ SherpaOnnx KWS 模型加载成功")
            } else {
                _state.value = WakeState.ErrorLoading(
                    IOException("KeywordSpotter or stream initialization failed")
                )
            }
        } catch (e: Exception) {
            _state.value = WakeState.ErrorLoading(e)
            Log.e(TAG, "❌ 模型加载失败", e)
        }
    }
}
```

### 3. 统一错误处理和用户提示

```kotlin
sealed class ModelError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotFound(val instructions: String) : ModelError(
        "模型文件未找到\n\n$instructions"
    )
    
    class CopyFailed(cause: Throwable) : ModelError(
        "模型复制失败,请检查存储空间",
        cause
    )
    
    class LoadFailed(cause: Throwable) : ModelError(
        "模型加载失败,可能是文件损坏",
        cause
    )
    
    class PermissionDenied : ModelError(
        "缺少必要权限,请在设置中授予存储权限"
    )
}
```

## 📊 对比表格

| 方案 | 用户体验 | Native 访问 | 权限需求 | 维护成本 | 推荐度 |
|------|---------|------------|---------|---------|-------|
| 混合存储 (App-Specific + Internal) | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ★★★★★ |
| 纯 Assets 打包 | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ★★★★☆ |
| 云端下载 + Internal | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ★★★☆☆ |
| 纯共享存储 (/sdcard) | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐ | ⭐⭐ | ★☆☆☆☆ |

## ✅ 最终推荐

采用 **混合存储策略** (方案一):

1. **用户管理区**: `/sdcard/Android/data/pkg/files/models/`
   - 用户通过 adb push 或文件管理器放置模型
   - 无需特殊权限 (Android 10+)

2. **Native 运行区**: `/data/data/pkg/files/models/`
   - 应用自动从用户管理区复制
   - Native 库安全访问
   - SELinux 兼容

3. **Assets 后备**: `assets/models/` (withModels 变体)
   - 开箱即用
   - 零配置

这个方案结合了所有优点,是最可靠和用户友好的解决方案!

