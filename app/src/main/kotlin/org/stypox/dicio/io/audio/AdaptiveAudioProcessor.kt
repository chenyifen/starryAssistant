package org.stypox.dicio.io.audio

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.stypox.dicio.util.DebugLogger

/**
 * 音频编解码器类型枚举
 */
enum class AudioCodecType {
    PCM     // 原始PCM音频
}

/**
 * 音频质量设置
 */
enum class AudioQuality {
    HIGH_QUALITY,    // 高音质模式
    BALANCED,        // 平衡模式
    LOW_BANDWIDTH    // 省流量模式
}

/**
 * 设备性能等级
 */
enum class DevicePerformance {
    HIGH_END,    // 高端设备
    MID_RANGE,   // 中端设备
    LOW_END      // 低端设备
}

/**
 * 自适应音频处理器
 * 根据设备性能、网络状况和用户设置动态选择最优的音频处理方式
 */
class AdaptiveAudioProcessor(
    private val context: Context
) {
    companion object {
        private const val TAG = "AdaptiveAudioProcessor"
    }
    
    // 当前使用的编解码器类型
    private var currentCodec = AudioCodecType.PCM
    
    // 用户设置的音频质量
    private var audioQuality = AudioQuality.BALANCED
    
    // 设备性能等级
    private val devicePerformance: DevicePerformance by lazy { detectDevicePerformance() }

    /**
     * 初始化自适应音频处理器
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            DebugLogger.logAudio(TAG, "🚀 初始化自适应音频处理器")
            
            // 检测设备性能
            val performance = detectDevicePerformance()
            DebugLogger.logAudio(TAG, "📱 设备性能等级: $performance")
            
            DebugLogger.logAudio(TAG, "🎵 使用音频编解码器: $currentCodec")
            
            DebugLogger.logAudio(TAG, "✅ 自适应音频处理器初始化完成")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 自适应音频处理器初始化失败: ${e.message}", e)
            false
        }
    }

    /**
     * 编码音频数据
     * @param pcmData PCM音频数据
     * @return 编码后的音频数据
     */
    suspend fun encodeAudio(pcmData: ShortArray): ByteArray? = withContext(Dispatchers.IO) {
        try {
            // PCM模式：直接转换为字节数组
            pcmShortsToBytes(pcmData)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 音频编码失败: ${e.message}", e)
            null
        }
    }

    /**
     * 解码音频数据
     * @param audioData 编码的音频数据
     * @return PCM音频数据
     */
    suspend fun decodeAudio(audioData: ByteArray): ShortArray? = withContext(Dispatchers.IO) {
        try {
            // PCM模式：直接转换为ShortArray
            pcmBytesToShorts(audioData)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 音频解码失败: ${e.message}", e)
            null
        }
    }

    /**
     * 设置音频质量偏好
     */
    fun setAudioQuality(quality: AudioQuality) {
        if (audioQuality != quality) {
            DebugLogger.logAudio(TAG, "🎛️ 音频质量设置变更: $audioQuality -> $quality")
            audioQuality = quality
        }
    }

    /**
     * 获取当前使用的编解码器类型
     */
    fun getCurrentCodec(): AudioCodecType = currentCodec

    /**
     * 获取编解码器信息
     */
    fun getCodecInfo(): String {
        return "PCM 16-bit 16kHz 单声道"
    }

    /**
     * 获取压缩比信息
     */
    fun getCompressionInfo(): String {
        return "无压缩 (1:1)"
    }

    /**
     * 检测设备性能等级
     */
    private fun detectDevicePerformance(): DevicePerformance {
        return try {
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory() / (1024 * 1024) // MB
            val processors = runtime.availableProcessors()
            val sdkVersion = Build.VERSION.SDK_INT
            
            DebugLogger.logAudio(TAG, "📊 设备信息: 内存=${maxMemory}MB, CPU核心=${processors}, SDK=${sdkVersion}")
            
            when {
                maxMemory >= 512 && processors >= 8 && sdkVersion >= Build.VERSION_CODES.O -> DevicePerformance.HIGH_END
                maxMemory >= 256 && processors >= 4 && sdkVersion >= Build.VERSION_CODES.M -> DevicePerformance.MID_RANGE
                else -> DevicePerformance.LOW_END
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 设备性能检测失败，使用默认值", e)
            DevicePerformance.MID_RANGE
        }
    }

    /**
     * PCM ShortArray转字节数组的实现
     */
    private fun pcmShortsToBytes(shorts: ShortArray): ByteArray {
        val bytes = ByteArray(shorts.size * 2)
        for (i in shorts.indices) {
            val value = shorts[i].toInt()
            bytes[i * 2] = (value and 0xFF).toByte()
            bytes[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    /**
     * PCM字节数组转ShortArray的实现
     */
    private fun pcmBytesToShorts(bytes: ByteArray): ShortArray {
        val shorts = ShortArray(bytes.size / 2)
        for (i in shorts.indices) {
            val low = bytes[i * 2].toInt() and 0xFF
            val high = bytes[i * 2 + 1].toInt() and 0xFF
            shorts[i] = (high shl 8 or low).toShort()
        }
        return shorts
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        DebugLogger.logAudio(TAG, "🧹 清理自适应音频处理器资源")
    }
}
