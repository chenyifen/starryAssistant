# Assets目录加密保护说明

## 概述

为了防止APK反编译后直接提取assets目录下的模型文件，我们实现了assets目录加密打包保护机制。

## 保护方案

### 1. Assets目录加密打包（构建时）

- **加密脚本**: `scripts/encrypt_assets.py`
- **加密算法**: AES-128-ECB
- **打包格式**: ZIP压缩包
- **加密流程**:
  1. 将整个assets目录打包成zip文件（排除备份目录）
  2. 使用AES-128加密zip文件
  3. 加密后的文件添加4字节魔数头（0x4D4F444C = "MODL"）
  4. 输出文件：`app/src/main/assets/encrypted_assets.dat`

### 2. 运行时解密并挂载（启动时）

- **加载器**: `SecureAssetLoader.kt`
- **挂载方式**: 使用反射调用 `AssetManager.addAssetPath()` 动态挂载
- **工作流程**:
  1. 应用启动时自动检测加密的assets文件 `encrypted_assets.dat`
  2. 如果存在，流式解密到临时zip文件（`filesDir/assets_temp.zip`）
  3. 使用反射调用 `AssetManager.addAssetPath()` 挂载解密后的zip
  4. 原有的 `AssetManager.open()` 可以直接访问，**无需修改任何代码**

### 3. Protobuf代码混淆保护

- **增强混淆规则**: 允许混淆Protobuf生成的类名，但保留必要的序列化方法
- **保护范围**: 
  - Protobuf核心运行时类（必须保留）
  - DataStore生成的类（允许混淆类名，保留方法）
  - Builder类（允许混淆类名，保留构建方法）
  - 枚举类（允许混淆类名，保留枚举值）

## 使用方法

### 构建时加密打包

#### 方式1：使用Shell脚本（推荐）

```bash
# 运行release构建脚本（自动加密）
./run_release.sh
```

#### 方式2：手动执行

```bash
# 安装Python依赖（如果还没有）
pip3 install pycryptodome

# 运行加密脚本
python3 scripts/encrypt_assets.py

# 加密后的文件会生成在: app/src/main/assets/encrypted_assets.dat
```

#### 方式3：使用Gradle任务

```bash
# 执行加密任务
./gradlew encryptAssets

# Release构建会自动执行加密
./gradlew assembleRelease
```

### 代码中使用

**无需修改任何代码！** 原有的 `AssetManager.open()` 可以直接访问：

```kotlin
// 原有的代码无需修改，直接使用
val inputStream = context.assets.open("sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/tokens.txt")

// sherpa-onnx库也无需修改，直接使用AssetManager
OfflineRecognizer(
    assetManager = context.assets,
    config = config
)
```

## 安全说明

1. **密钥保护**: 密钥通过字符串变换生成，不直接硬编码
2. **混淆保护**: 解密工具类允许混淆，增加反编译难度
3. **文件保护**: 加密文件在APK中，解密后的zip在应用私有目录
4. **向后兼容**: 如果未找到加密文件，自动使用原始assets目录，不影响现有功能
5. **性能优化**: 解密操作在后台线程执行，使用流式处理避免内存溢出

## 文件结构

```
app/src/main/
└── assets/                    # 原始assets目录（开发时使用）
    ├── models/
    ├── encrypted_assets.dat  # 加密后的压缩包（构建时生成）
    └── ...

应用运行时:
filesDir/
└── assets_temp.zip            # 解密后的临时zip文件（自动挂载到AssetManager）
```

## 工作原理

1. **打包阶段**: 将整个assets目录打包成zip，加密后保存为 `encrypted_assets.dat`
2. **启动阶段**: 
   - 检测 `encrypted_assets.dat` 是否存在
   - 如果存在，流式解密到 `filesDir/assets_temp.zip`
   - 使用反射调用 `AssetManager.addAssetPath(filesDir/assets_temp.zip)` 挂载
3. **运行时**: 
   - 所有 `AssetManager.open()` 调用会自动访问挂载的zip文件
   - 目录结构和文件路径与原始assets完全一致
   - 无需修改任何现有代码

## 优势

相比之前的方案：

1. **完全兼容**: 无需修改任何现有代码，`AssetManager.open()` 直接可用
2. **更安全**: assets内容加密，反编译无法直接提取
3. **更高效**: 使用系统级挂载，性能好
4. **更简单**: 不需要自定义AssetManager包装器或路径转换

## 注意事项

1. **首次使用**: 需要先运行加密脚本生成 `encrypted_assets.dat`
2. **构建流程**: 加密脚本已集成到Gradle构建流程和run_release.sh中
3. **性能影响**: 首次启动时需要解密（后台执行），后续启动直接使用临时zip
4. **存储空间**: 临时zip文件会占用应用私有存储空间（约350MB）
5. **测试验证**: 加密后需要充分测试，确保所有文件都能正常访问

## 技术细节

### 反射调用 addAssetPath

```kotlin
val assetManager = context.assets
val addAssetPathMethod: Method = AssetManager::class.java.getDeclaredMethod(
    "addAssetPath",
    String::class.java
)
addAssetPathMethod.isAccessible = true
val result = addAssetPathMethod.invoke(assetManager, zipFile.absolutePath) as Int
```

### 流式解密

使用16KB缓冲区，分块读取、解密、写入，避免一次性加载整个文件到内存。

### 排除备份目录

加密脚本会自动排除以下目录：
- `assets_encrypted`
- `assets_backup`
- `backup`
- `.backup`
- `.git`
- `__pycache__`
- 以及所有以 `_backup` 或 `_encrypted` 结尾的目录/文件

## 后续优化

1. 支持增量更新（只更新变化的文件）
2. 添加压缩包完整性校验
3. 支持更复杂的密钥生成算法
4. 在native层完成解密（进一步提高安全性）
