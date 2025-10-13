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
    PCM,    // 原始PCM音频
    OPUS    // Opus压缩音频
}

/**
 * 音频质量设置
 */
enum class AudioQuality {
    HIGH_QUALITY,    // 高音质模式 (优先PCM)
    BALANCED,        // 平衡模式 (智能选择)
    LOW_BANDWIDTH    // 省流量模式 (优先Opus)
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
 * 根据设备性能、网络状况和用户设置动态选择最优的音频编解码器
 */
class AdaptiveAudioProcessor(
    private val context: Context
) {
    companion object {
        private const val TAG = "AdaptiveAudioProcessor"
    }

    // Opus编解码器实例
    private var opusCodec: OpusAudioCodec? = null
    
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
            
            // 根据设备性能和用户设置选择初始编解码器
            currentCodec = selectOptimalCodec()
            DebugLogger.logAudio(TAG, "🎵 选择音频编解码器: $currentCodec")
            
            // 如果选择Opus，初始化编解码器
            if (currentCodec == AudioCodecType.OPUS) {
                opusCodec = OpusAudioCodec().apply {
                    if (!initialize()) {
                        Log.e(TAG, "❌ Opus编解码器初始化失败，降级到PCM")
                        currentCodec = AudioCodecType.PCM
                        return@withContext true // 降级成功也算初始化成功
                    }
                }
            }
            
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
            when (currentCodec) {
                AudioCodecType.PCM -> {
                    // PCM模式：直接转换为字节数组
                    opusCodec?.pcmShortsToBytes(pcmData) ?: pcmShortsToBytes(pcmData)
                }
                AudioCodecType.OPUS -> {
                    // Opus模式：编码为Opus格式
                    opusCodec?.encode(pcmData) ?: run {
                        Log.e(TAG, "❌ Opus编码器不可用，降级到PCM")
                        fallbackToPCM()
                        pcmShortsToBytes(pcmData)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 音频编码失败: ${e.message}", e)
            handleEncodingFailure()
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
            when (currentCodec) {
                AudioCodecType.PCM -> {
                    // PCM模式：直接转换为ShortArray
                    opusCodec?.pcmBytesToShorts(audioData) ?: pcmBytesToShorts(audioData)
                }
                AudioCodecType.OPUS -> {
                    // Opus模式：解码Opus格式
                    opusCodec?.decode(audioData) ?: run {
                        Log.e(TAG, "❌ Opus解码器不可用，降级到PCM")
                        fallbackToPCM()
                        pcmBytesToShorts(audioData)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 音频解码失败: ${e.message}", e)
            handleDecodingFailure()
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
            
            // 重新选择最优编解码器
            val newCodec = selectOptimalCodec()
            if (newCodec != currentCodec) {
                DebugLogger.logAudio(TAG, "🔄 切换音频编解码器: $currentCodec -> $newCodec")
                // 切换编解码器需要在协程中执行，这里简化处理
                currentCodec = newCodec
                if (newCodec == AudioCodecType.PCM) {
                    opusCodec?.cleanup()
                    opusCodec = null
                }
            }
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
        return when (currentCodec) {
            AudioCodecType.PCM -> "PCM 16-bit 16kHz 单声道"
            AudioCodecType.OPUS -> opusCodec?.getEncoderInfo() ?: "Opus (未初始化)"
        }
    }

    /**
     * 获取压缩比信息
     */
    fun getCompressionInfo(): String {
        return when (currentCodec) {
            AudioCodecType.PCM -> "无压缩 (1:1)"
            AudioCodecType.OPUS -> {
                val ratio = opusCodec?.getCompressionRatio() ?: 1f
                "压缩比 ${String.format("%.1f", ratio)}:1"
            }
        }
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
     * 选择最优编解码器
     */
    private fun selectOptimalCodec(): AudioCodecType {
        return when (audioQuality) {
            AudioQuality.HIGH_QUALITY -> AudioCodecType.PCM
            AudioQuality.LOW_BANDWIDTH -> AudioCodecType.OPUS
            AudioQuality.BALANCED -> {
                when (devicePerformance) {
                    DevicePerformance.HIGH_END -> AudioCodecType.OPUS
                    DevicePerformance.MID_RANGE -> AudioCodecType.OPUS
                    DevicePerformance.LOW_END -> AudioCodecType.PCM
                }
            }
        }
    }

    /**
     * 切换编解码器
     */
    private suspend fun switchCodec(newCodec: AudioCodecType) = withContext(Dispatchers.IO) {
        try {
            when (newCodec) {
                AudioCodecType.OPUS -> {
                    if (opusCodec == null) {
                        opusCodec = OpusAudioCodec()
                        if (!opusCodec!!.initialize()) {
                            Log.e(TAG, "❌ 无法初始化Opus编解码器，保持PCM模式")
                            return@withContext
                        }
                    }
                }
                AudioCodecType.PCM -> {
                    // PCM模式不需要特殊初始化
                }
            }
            
            currentCodec = newCodec
            DebugLogger.logAudio(TAG, "✅ 成功切换到 $newCodec 编解码器")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 切换编解码器失败: ${e.message}", e)
        }
    }

    /**
     * 降级到PCM模式
     */
    private suspend fun fallbackToPCM() = withContext(Dispatchers.IO) {
        if (currentCodec != AudioCodecType.PCM) {
            DebugLogger.logAudio(TAG, "⬇️ 降级到PCM模式")
            currentCodec = AudioCodecType.PCM
        }
    }

    /**
     * 处理编码失败
     */
    private suspend fun handleEncodingFailure() {
        Log.e(TAG, "❌ 编码失败，尝试降级")
        if (currentCodec == AudioCodecType.OPUS) {
            fallbackToPCM()
        }
    }

    /**
     * 处理解码失败
     */
    private suspend fun handleDecodingFailure() {
        Log.e(TAG, "❌ 解码失败，尝试降级")
        if (currentCodec == AudioCodecType.OPUS) {
            fallbackToPCM()
        }
    }

    /**
     * PCM ShortArray转字节数组的备用实现
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
     * PCM字节数组转ShortArray的备用实现
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
        
        opusCodec?.cleanup()
        opusCodec = null
    }
}
