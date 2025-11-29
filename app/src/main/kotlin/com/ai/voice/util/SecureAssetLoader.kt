package com.ai.voice.util

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Method
import java.util.zip.ZipFile
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 安全的Assets加载器
 * 
 * 方案：加密ZIP + 动态挂载 addAssetPath
 * 
 * 工作流程：
 * 1. 打包阶段：将整个assets目录打包成zip，加密后保存为 encrypted_assets.dat
 * 2. 启动时：解密 encrypted_assets.dat 到临时zip文件
 * 3. 使用反射调用 AssetManager.addAssetPath() 挂载解密后的zip
 * 4. 这样原有的 AssetManager.open() 就能直接访问，无需修改代码
 * 
 * 优势：
 * - 安全：assets内容加密，反编译无法直接提取
 * - 兼容：无需修改现有代码，AssetManager接口保持不变
 * - 高效：使用系统级挂载，性能好
 */
object SecureAssetLoader {
    private const val TAG = "SecureAssetLoader"
    private const val ENCRYPTED_ASSET_FILE = "encrypted_assets.dat"
    private const val TEMP_ASSET_ZIP = "assets_temp.zip"
    private const val TEMP_ASSET_DIR = "assets_extracted"
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/ECB/PKCS5Padding"
    
    // 魔数头：0x4D4F444C = "MODL" (Model)
    private val MAGIC_HEADER = byteArrayOf(0x4D.toByte(), 0x4F.toByte(), 0x44.toByte(), 0x4C.toByte())
    
    /**
     * 初始化并挂载加密的assets
     * @param context 上下文
     * @return 是否成功挂载
     */
    suspend fun init(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val filesDir = context.filesDir
            val tempZipFile = File(filesDir, TEMP_ASSET_ZIP)
            val tempAssetDir = File(filesDir, TEMP_ASSET_DIR)
            
            // 优先尝试直接挂载ZIP文件（如果已存在）
            if (tempZipFile.exists() && tempZipFile.length() > 0) {
                Log.d(TAG, "✅ 临时assets zip已存在，尝试直接挂载: ${tempZipFile.absolutePath}")
                val zipMountSuccess = mountAssetZip(context, tempZipFile)
                if (zipMountSuccess) {
                    verifyMountedAssets(context)
                    return@withContext true
                }
                Log.w(TAG, "⚠️  ZIP文件挂载失败，将尝试解压方案")
            }
            
            // 如果ZIP挂载失败，检查是否已经解压过（临时目录存在且非空）
            if (tempAssetDir.exists() && tempAssetDir.listFiles()?.isNotEmpty() == true) {
                Log.d(TAG, "✅ 临时assets目录已存在，使用解压目录方案")
                // 解压目录无法通过addAssetPath挂载，需要创建包装类
                // 这里先标记为成功，实际读取会在包装类中处理
                return@withContext true
            }
            
            // 检查是否有加密的assets文件
            val hasEncryptedAssets = try {
                context.assets.open(ENCRYPTED_ASSET_FILE).use { input ->
                    val header = ByteArray(4)
                    val bytesRead = input.read(header)
                    bytesRead == 4 && header.contentEquals(MAGIC_HEADER)
                }
            } catch (e: Exception) {
                false
            }
            
            if (!hasEncryptedAssets) {
                Log.d(TAG, "ℹ️  未检测到加密的Assets文件，使用原始Assets目录")
                return@withContext true // 没有加密文件，使用原始assets
            }
            
            Log.d(TAG, "🔓 开始解密并挂载Assets...")
            
            // 流式解密：读取加密文件，解密后写入临时zip文件
            context.assets.open(ENCRYPTED_ASSET_FILE).use { encryptedInput ->
                // 检查魔数头
                val header = ByteArray(4)
                val bytesRead = encryptedInput.read(header)
                if (bytesRead < 4 || !header.contentEquals(MAGIC_HEADER)) {
                    Log.w(TAG, "⚠️  Assets文件未加密或格式错误")
                    return@withContext false
                }
                
                // 流式解密：分块读取、解密、写入
                Log.d(TAG, "📦 开始流式解密（避免内存溢出）...")
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val key = SecretKeySpec(getDecryptionKey(), ALGORITHM)
                cipher.init(Cipher.DECRYPT_MODE, key)
                
                FileOutputStream(tempZipFile).use { output ->
                    val buffer = ByteArray(16384) // 16KB缓冲区（AES块大小的倍数）
                    var totalRead = 0
                    
                    while (true) {
                        val bytesRead = encryptedInput.read(buffer)
                        if (bytesRead == -1) break
                        
                        // 解密数据块（使用update，最后一块用doFinal）
                        val decrypted = cipher.update(buffer, 0, bytesRead)
                        if (decrypted != null && decrypted.isNotEmpty()) {
                            output.write(decrypted)
                        }
                        
                        totalRead += bytesRead
                        if (totalRead % (1024 * 1024) == 0) {
                            Log.d(TAG, "   已处理: ${totalRead / 1024 / 1024} MB")
                        }
                    }
                    
                    // 处理最后一块（包含填充）
                    try {
                        val finalBlock = cipher.doFinal()
                        if (finalBlock.isNotEmpty()) {
                            output.write(finalBlock)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "处理最后一块数据失败", e)
                        return@withContext false
                    }
                }
                
                Log.d(TAG, "✅ 解密完成，临时zip文件: ${tempZipFile.absolutePath} (${tempZipFile.length() / 1024 / 1024} MB)")
            }
            
            // 验证ZIP文件格式
            if (!verifyZipFile(tempZipFile)) {
                Log.e(TAG, "❌ ZIP文件格式验证失败")
                return@withContext false
            }
            
            // 优先尝试直接挂载ZIP文件
            Log.d(TAG, "📦 尝试直接挂载ZIP文件...")
            val zipMountSuccess = mountAssetZip(context, tempZipFile)
            
            if (zipMountSuccess) {
                // ZIP文件挂载成功，验证访问
                verifyMountedAssets(context)
                return@withContext true
            }
            
            // ZIP文件挂载失败，解压到目录作为备用方案
            Log.w(TAG, "⚠️  ZIP文件挂载失败，使用解压目录方案...")
            Log.d(TAG, "📦 开始解压ZIP文件到临时目录...")
            if (!extractZipFile(tempZipFile, tempAssetDir)) {
                Log.e(TAG, "❌ ZIP文件解压失败")
                return@withContext false
            }
            
            // 解压目录无法通过addAssetPath挂载，返回true表示解压成功
            // 实际文件读取需要通过自定义包装类处理
            Log.d(TAG, "✅ ZIP文件已解压到目录，文件读取将通过包装类处理")
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Assets解密挂载失败", e)
            false
        }
    }
    
    /**
     * 验证ZIP文件格式
     */
    private fun verifyZipFile(zipFile: File): Boolean {
        return try {
            ZipFile(zipFile).use { zip ->
                val entries = mutableListOf<java.util.zip.ZipEntry>()
                zip.entries().asIterator().forEach { entries.add(it) }
                Log.d(TAG, "📋 ZIP文件包含 ${entries.size} 个文件")
                
                // 列出前10个文件作为示例
                entries.take(10).forEach { entry ->
                    Log.d(TAG, "   - ${entry.name} (${entry.size} bytes)")
                }
                if (entries.size > 10) {
                    Log.d(TAG, "   ... 还有 ${entries.size - 10} 个文件")
                }
                
                // 检查关键文件是否存在
                val keyFiles = listOf(
                    "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/tokens.txt",
                    "models/sherpa_onnx_kws/tokens.txt"
                )
                keyFiles.forEach { keyFile ->
                    val found = entries.any { it.name == keyFile }
                    if (found) {
                        Log.d(TAG, "✅ 找到关键文件: $keyFile")
                    } else {
                        Log.w(TAG, "⚠️  未找到关键文件: $keyFile")
                    }
                }
                
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ ZIP文件验证失败", e)
            false
        }
    }
    
    /**
     * 解压ZIP文件到临时目录
     */
    private fun extractZipFile(zipFile: File, targetDir: File): Boolean {
        return try {
            // 创建目标目录
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            targetDir.mkdirs()
            
            ZipFile(zipFile).use { zip ->
                val entries = mutableListOf<java.util.zip.ZipEntry>()
                zip.entries().asIterator().forEach { entries.add(it) }
                
                var extractedCount = 0
                entries.forEach { entry ->
                    val outputFile = File(targetDir, entry.name)
                    
                    // 创建父目录
                    outputFile.parentFile?.mkdirs()
                    
                    // 如果是目录，创建目录
                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        // 解压文件
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outputFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        extractedCount++
                        if (extractedCount % 100 == 0) {
                            Log.d(TAG, "   已解压: $extractedCount 个文件...")
                        }
                    }
                }
                
                Log.d(TAG, "✅ ZIP文件解压完成: ${extractedCount} 个文件解压到 ${targetDir.absolutePath}")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ ZIP文件解压失败", e)
            false
        }
    }
    
    /**
     * 使用反射挂载ZIP文件到AssetManager
     */
    private fun mountAssetZip(context: Context, zipFile: File): Boolean {
        return try {
            val assetManager = context.assets
            val addAssetPathMethod: Method = AssetManager::class.java.getDeclaredMethod(
                "addAssetPath",
                String::class.java
            )
            addAssetPathMethod.isAccessible = true
            
            val result = addAssetPathMethod.invoke(assetManager, zipFile.absolutePath) as Int
            if (result > 0) {
                Log.d(TAG, "✅ Assets ZIP文件已成功挂载: ${zipFile.absolutePath}")
                Log.d(TAG, "   返回码: $result")
                true
            } else {
                Log.w(TAG, "⚠️  Assets ZIP文件挂载失败，返回码: $result")
                Log.w(TAG, "   addAssetPath可能不支持普通ZIP文件，将使用解压目录方案")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️  反射调用 addAssetPath 失败: ${e.message}")
            false
        }
    }
    
    /**
     * 验证挂载后是否能访问文件
     */
    private fun verifyMountedAssets(context: Context) {
        try {
            val testFiles = listOf(
                "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/tokens.txt",
                "models/sherpa_onnx_kws/tokens.txt"
            )
            
            Log.d(TAG, "🔍 验证挂载后的Assets访问...")
            testFiles.forEach { fileName ->
                try {
                    context.assets.open(fileName).use { input ->
                        val size = input.available()
                        Log.d(TAG, "✅ 可以访问: $fileName (${size} bytes)")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️  无法访问: $fileName - ${e.message}")
                }
            }
            
            // 列出assets根目录的文件
            try {
                val rootFiles = context.assets.list("")
                Log.d(TAG, "📂 Assets根目录包含 ${rootFiles?.size ?: 0} 个文件/目录")
                rootFiles?.take(10)?.forEach { file ->
                    Log.d(TAG, "   - $file")
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️  无法列出Assets根目录: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 验证Assets访问失败", e)
        }
    }
    
    /**
     * 检查解压目录是否存在
     */
    fun hasExtractedAssets(context: Context): Boolean {
        val tempAssetDir = File(context.filesDir, TEMP_ASSET_DIR)
        return tempAssetDir.exists() && tempAssetDir.listFiles()?.isNotEmpty() == true
    }
    
    /**
     * 从解压目录打开文件（如果存在）
     */
    fun openFromExtractedAssets(context: Context, fileName: String): java.io.InputStream? {
        return try {
            val tempAssetDir = File(context.filesDir, TEMP_ASSET_DIR)
            val file = File(tempAssetDir, fileName)
            if (file.exists() && file.isFile) {
                java.io.FileInputStream(file)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "从解压目录读取文件失败: $fileName", e)
            null
        }
    }
    
    /**
     * 获取解密密钥
     */
    private fun getDecryptionKey(): ByteArray {
        val keyString = "V0!c3@55!5t@nt#2024"
        val keyBytes = keyString.toByteArray(Charsets.UTF_8)
        return keyBytes.copyOf(16) // AES-128需要16字节密钥
    }
    
    /**
     * 清理临时文件（应用退出时调用）
     */
    fun cleanup(context: Context) {
        try {
            val tempZipFile = File(context.filesDir, TEMP_ASSET_ZIP)
            if (tempZipFile.exists()) {
                tempZipFile.delete()
                Log.d(TAG, "🗑️  已清理临时assets zip文件")
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理临时文件失败", e)
        }
    }
}

