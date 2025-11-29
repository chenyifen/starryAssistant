package com.ai.voice.license

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.KeyStore

/**
 * 安全存储类 - 使用Android Keystore进行加密
 * 防止Root设备直接修改激活状态
 */
class SecureStorage(private val context: Context) {
    
    companion object {
        private const val TAG = "SecureStorage"
        private const val KEY_ALIAS = "activation_encryption_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }
    
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }
    
    /**
     * 获取或创建加密密钥
     */
    private fun getOrCreateSecretKey(): SecretKey {
        // 检查密钥是否已存在
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null)
            if (entry is KeyStore.SecretKeyEntry) {
                return entry.secretKey
            }
        }
        
        // 创建新密钥
        Log.d(TAG, "创建新的加密密钥...")
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
        
        // Android 6.0+ 支持用户认证保护
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setUserAuthenticationRequired(false)
        }
        
        keyGenerator.init(builder.build())
        val key = keyGenerator.generateKey()
        Log.d(TAG, "✅ 加密密钥创建成功")
        return key
    }
    
    /**
     * 加密数据
     * @param data 要加密的明文数据
     * @return Base64编码的加密数据（包含IV）
     */
    fun encrypt(data: String): String {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            
            // 获取IV（初始化向量）
            val iv = cipher.iv
            if (iv.size != GCM_IV_LENGTH) {
                Log.w(TAG, "⚠️ IV长度不正确: ${iv.size}, 期望: $GCM_IV_LENGTH")
            }
            
            // 加密数据
            val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            
            // 将IV和加密数据组合: [IV(12字节) | 加密数据 | 认证标签]
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
            
            val result = Base64.encodeToString(combined, Base64.NO_WRAP)
            Log.d(TAG, "✅ 数据加密成功 (长度: ${data.length} -> ${result.length})")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "❌ 数据加密失败", e)
            throw SecurityException("数据加密失败: ${e.message}", e)
        }
    }
    
    /**
     * 解密数据
     * @param encryptedData Base64编码的加密数据（包含IV）
     * @return 解密后的明文数据
     */
    fun decrypt(encryptedData: String): String {
        try {
            // Base64解码
            val combined = Base64.decode(encryptedData, Base64.NO_WRAP)
            
            if (combined.size < GCM_IV_LENGTH) {
                throw IllegalArgumentException("加密数据长度不足，可能已损坏")
            }
            
            // 提取IV和加密数据
            val iv = ByteArray(GCM_IV_LENGTH)
            val encrypted = ByteArray(combined.size - GCM_IV_LENGTH)
            
            System.arraycopy(combined, 0, iv, 0, iv.size)
            System.arraycopy(combined, iv.size, encrypted, 0, encrypted.size)
            
            // 解密
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
            
            val decrypted = cipher.doFinal(encrypted)
            val result = String(decrypted, Charsets.UTF_8)
            
            Log.d(TAG, "✅ 数据解密成功 (长度: ${encryptedData.length} -> ${result.length})")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "❌ 数据解密失败", e)
            throw SecurityException("数据解密失败，可能数据已被篡改: ${e.message}", e)
        }
    }
    
    /**
     * 检查是否可以正常加解密（用于测试）
     */
    fun testEncryption(): Boolean {
        return try {
            val testData = "test_${System.currentTimeMillis()}"
            val encrypted = encrypt(testData)
            val decrypted = decrypt(encrypted)
            val success = testData == decrypted
            
            if (success) {
                Log.d(TAG, "✅ 加密测试通过")
            } else {
                Log.e(TAG, "❌ 加密测试失败: 数据不匹配")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ 加密测试异常", e)
            false
        }
    }
    
    /**
     * 删除密钥（慎用，会导致已加密数据无法解密）
     */
    fun deleteKey() {
        try {
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
                Log.d(TAG, "✅ 加密密钥已删除")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 删除密钥失败", e)
        }
    }
}

