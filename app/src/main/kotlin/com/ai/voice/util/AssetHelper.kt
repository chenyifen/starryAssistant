package com.ai.voice.util

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Assets资源读取辅助类
 * 自动检测并解密加密的assets文件
 * 
 * 使用方式：
 * 1. 对于需要复制到文件系统的模型，使用 decryptAssetToFile()
 * 2. 对于需要直接读取的模型，使用 openAsset() 获取解密后的InputStream
 */
object AssetHelper {
    private const val TAG = "AssetHelper"
    
    /**
     * 打开assets文件（自动检测并解密）
     * @param context 上下文
     * @param assetPath assets中的文件路径
     * @return 解密后的InputStream，如果文件未加密则返回原始InputStream
     */
    fun openAsset(context: Context, assetPath: String): InputStream {
        val inputStream = context.assets.open(assetPath)
        
        // 检查文件是否加密（通过文件头判断）
        return if (AssetDecryptor.isEncrypted(assetPath, context)) {
            // 文件已加密，返回解密后的流
            Log.d(TAG, "🔓 检测到加密文件，正在解密: $assetPath")
            DecryptedInputStream(inputStream)
        } else {
            // 文件未加密，直接返回
            inputStream
        }
    }
    
    /**
     * 解密assets文件并保存到指定位置
     * @param context 上下文
     * @param assetPath assets中的文件路径
     * @param outputFile 输出文件
     * @return 是否成功
     */
    suspend fun decryptAssetToFile(
        context: Context,
        assetPath: String,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            outputFile.parentFile?.mkdirs()
            
            // 使用统一的解密方法
            if (AssetDecryptor.isEncrypted(assetPath, context)) {
                // 文件已加密，解密后保存
                AssetDecryptor.decryptAsset(context, assetPath, outputFile)
            } else {
                // 文件未加密，直接复制
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "✅ 复制未加密文件: $assetPath -> ${outputFile.absolutePath}")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 处理assets文件失败: $assetPath", e)
            false
        }
    }
    
    /**
     * 解密后的InputStream包装类
     * 读取时自动解密数据
     */
    private class DecryptedInputStream(
        private val encryptedStream: InputStream
    ) : InputStream() {
        private var decryptedData: ByteArray? = null
        private var position = 0
        
        override fun read(): Int {
            if (decryptedData == null) {
                // 首次读取，解密整个文件
                val encryptedData = encryptedStream.readBytes()
                decryptedData = try {
                    // 跳过4字节魔数头
                    val dataWithoutHeader = encryptedData.drop(4).toByteArray()
                    AssetDecryptor.decrypt(dataWithoutHeader)
                } catch (e: Exception) {
                    Log.e(TAG, "解密失败", e)
                    ByteArray(0)
                }
            }
            
            return if (position < decryptedData!!.size) {
                decryptedData!![position++].toInt() and 0xFF
            } else {
                -1
            }
        }
        
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (decryptedData == null) {
                // 首次读取，解密整个文件
                val encryptedData = encryptedStream.readBytes()
                decryptedData = try {
                    // 跳过4字节魔数头
                    val dataWithoutHeader = encryptedData.drop(4).toByteArray()
                    AssetDecryptor.decrypt(dataWithoutHeader)
                } catch (e: Exception) {
                    Log.e(TAG, "解密失败", e)
                    ByteArray(0)
                }
            }
            
            val available = decryptedData!!.size - position
            if (available <= 0) return -1
            
            val readLen = minOf(len, available)
            System.arraycopy(decryptedData!!, position, b, off, readLen)
            position += readLen
            return readLen
        }
        
        override fun close() {
            encryptedStream.close()
            decryptedData = null
            position = 0
        }
        
        override fun available(): Int {
            if (decryptedData == null) return 0
            return decryptedData!!.size - position
        }
    }
}

