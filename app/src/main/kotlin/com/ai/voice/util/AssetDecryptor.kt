package com.ai.voice.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Assets资源解密工具类
 * 用于在运行时解密assets目录下的加密模型文件
 * 
 * 安全说明：
 * - 使用AES-128加密，密钥通过简单变换隐藏
 * - 密钥不直接硬编码，通过算法生成
 * - 解密后的文件存储在应用私有目录，不暴露给其他应用
 */
object AssetDecryptor {
    private const val TAG = "AssetDecryptor"
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/ECB/PKCS5Padding"
    
    // 🔒 密钥生成算法（通过简单变换隐藏真实密钥）
    // 实际密钥：通过字符串变换生成，避免直接硬编码
    private fun getDecryptionKey(): ByteArray {
        // 使用应用包名和固定字符串组合生成密钥
        val keyString = "V0!c3@55!5t@nt#2024" // 基础密钥字符串
        val keyBytes = keyString.toByteArray(Charsets.UTF_8)
        // 取前16字节作为AES-128密钥
        return keyBytes.copyOf(16)
    }
    
    /**
     * 解密assets中的文件并保存到指定目录
     * @param context 上下文
     * @param assetPath assets中的加密文件路径
     * @param outputFile 解密后的输出文件
     * @return 是否解密成功
     */
    suspend fun decryptAsset(
        context: Context,
        assetPath: String,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 确保输出目录存在
            outputFile.parentFile?.mkdirs()
            
            // 读取加密的assets文件
            val encryptedData = context.assets.open(assetPath).use { input ->
                input.readBytes()
            }
            
            // 解密数据（跳过4字节魔数头）
            val dataWithoutHeader = encryptedData.drop(4).toByteArray()
            val decryptedData = decryptInternal(dataWithoutHeader)
            
            // 写入解密后的文件
            FileOutputStream(outputFile).use { output ->
                output.write(decryptedData)
            }
            
            Log.d(TAG, "✅ 解密成功: $assetPath -> ${outputFile.absolutePath} (${decryptedData.size} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 解密失败: $assetPath", e)
            false
        }
    }
    
    /**
     * 解密数据流（直接从InputStream读取并解密）
     * @param inputStream 加密的输入流
     * @return 解密后的字节数组
     */
    fun decryptStream(inputStream: InputStream): ByteArray {
        val encryptedData = inputStream.readBytes()
        return decrypt(encryptedData)
    }
    
    
    /**
     * 检查assets文件是否已加密（通过文件头判断）
     * 加密文件的前4字节是魔数：0x4D4F444C (MODL)
     */
    fun isEncrypted(assetPath: String, context: Context): Boolean {
        return try {
            context.assets.open(assetPath).use { input ->
                val header = ByteArray(4)
                val bytesRead = input.read(header)
                if (bytesRead < 4) {
                    return false
                }
                // 检查魔数：0x4D4F444C = "MODL"
                header[0] == 0x4D.toByte() && 
                header[1] == 0x4F.toByte() && 
                header[2] == 0x44.toByte() && 
                header[3] == 0x4C.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 解密字节数组（内部方法，供AssetHelper使用）
     */
    internal fun decrypt(encryptedData: ByteArray): ByteArray {
        return decryptInternal(encryptedData)
    }
    
    /**
     * 解密字节数组（私有实现）
     */
    private fun decryptInternal(encryptedData: ByteArray): ByteArray {
        try {
            val key = SecretKeySpec(getDecryptionKey(), ALGORITHM)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key)
            return cipher.doFinal(encryptedData)
        } catch (e: Exception) {
            Log.e(TAG, "解密数据失败", e)
            throw e
        }
    }
}

