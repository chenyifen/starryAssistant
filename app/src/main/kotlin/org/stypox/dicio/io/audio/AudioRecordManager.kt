package org.stypox.dicio.io.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

/**
 * AudioRecord 全局管理器
 * 
 * 解决 WakeService 和 SenseVoiceInputDevice 的 AudioRecord 资源冲突
 * 同一时间只允许一个 owner 持有 AudioRecord 实例
 */
object AudioRecordManager {
    private const val TAG = "AudioRecordManager"
    
    private var currentOwner: String? = null
    private var currentRecord: AudioRecord? = null
    private val lock = Any()
    
    /**
     * 获取 AudioRecord 实例
     * 如果已被其他 owner 占用，会先释放旧的实例
     * 
     * @param owner 所有者标识（如 "WakeService", "SenseVoice"）
     * @param config 音频配置
     * @return AudioRecord 实例，失败返回 null
     */
    @Synchronized
    fun acquire(owner: String, config: AudioConfig): AudioRecord? {
        synchronized(lock) {
            Log.d(TAG, "📱 $owner 请求 AudioRecord")
            
            // 如果已被占用，先释放
            if (currentOwner != null && currentOwner != owner) {
                Log.w(TAG, "⚠️ AudioRecord 被 $currentOwner 占用，强制释放")
                releaseInternal(currentOwner!!)
            }
            
            // 如果是同一个 owner 重复请求，返回现有实例
            if (currentOwner == owner && currentRecord != null) {
                Log.d(TAG, "✅ $owner 复用现有 AudioRecord")
                return currentRecord
            }
            
            // 创建新的 AudioRecord
            try {
                val bufferSize = AudioRecord.getMinBufferSize(
                    config.sampleRate,
                    config.channelConfig,
                    config.audioFormat
                )
                
                if (bufferSize <= 0) {
                    Log.e(TAG, "❌ 无效的 bufferSize: $bufferSize")
                    return null
                }
                
                currentRecord = AudioRecord(
                    config.audioSource,
                    config.sampleRate,
                    config.channelConfig,
                    config.audioFormat,
                    bufferSize * 2
                )
                
                if (currentRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "❌ AudioRecord 初始化失败")
                    currentRecord?.release()
                    currentRecord = null
                    return null
                }
                
                currentOwner = owner
                Log.d(TAG, "✅ AudioRecord 创建成功，owner: $owner, bufferSize: $bufferSize")
                
                return currentRecord
            } catch (e: Exception) {
                Log.e(TAG, "❌ AudioRecord 创建异常", e)
                currentRecord = null
                currentOwner = null
                return null
            }
        }
    }
    
    /**
     * 释放 AudioRecord
     * 只有当前 owner 才能释放
     * 
     * @param owner 所有者标识
     */
    @Synchronized
    fun release(owner: String) {
        synchronized(lock) {
            if (currentOwner == owner) {
                releaseInternal(owner)
            } else {
                Log.w(TAG, "⚠️ $owner 尝试释放不属于它的 AudioRecord (当前owner: $currentOwner)")
            }
        }
    }
    
    /**
     * 内部释放方法
     */
    private fun releaseInternal(owner: String) {
        Log.d(TAG, "🗑️ 释放 AudioRecord，owner: $owner")
        
        currentRecord?.let {
            try {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                    Log.d(TAG, "🛑 AudioRecord 已停止")
                }
                it.release()
                Log.d(TAG, "✅ AudioRecord 已释放")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ 释放 AudioRecord 时出现异常", e)
            }
        }
        
        currentRecord = null
        currentOwner = null
    }
    
    /**
     * 获取当前 owner
     */
    @Synchronized
    fun getCurrentOwner(): String? {
        synchronized(lock) {
            return currentOwner
        }
    }
    
    /**
     * 检查指定 owner 是否持有 AudioRecord
     */
    @Synchronized
    fun isOwnedBy(owner: String): Boolean {
        synchronized(lock) {
            return currentOwner == owner
        }
    }
    
    /**
     * 强制释放所有资源（用于清理）
     */
    @Synchronized
    fun releaseAll() {
        synchronized(lock) {
            currentOwner?.let { releaseInternal(it) }
        }
    }
}

/**
 * 音频配置
 */
data class AudioConfig(
    val audioSource: Int = MediaRecorder.AudioSource.MIC,
    val sampleRate: Int = 16000,
    val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
)

