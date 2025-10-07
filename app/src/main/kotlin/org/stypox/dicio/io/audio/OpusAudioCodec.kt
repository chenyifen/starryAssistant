package org.stypox.dicio.io.audio

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Opus音频编解码器封装类（真正的Opus实现）
 * 提供PCM与Opus格式之间的转换功能
 */
class OpusAudioCodec(
    private val sampleRate: Int = 16000,
    private val channels: Int = 1,
    private val frameSize: Int = 960, // 60ms at 16kHz
    private val bitRate: Int = 32000, // 32kbps
    private val complexity: Int = 8 // 0-10, 8为高质量
) {
    companion object {
        private const val TAG = "OpusAudioCodec"
        
        // 支持的采样率
        val SUPPORTED_SAMPLE_RATES = arrayOf(8000, 12000, 16000, 24000, 48000)
        
        // 支持的帧大小 (samples per channel)
        val SUPPORTED_FRAME_SIZES = mapOf(
            8000 to arrayOf(120, 240, 480, 960),     // 15, 30, 60, 120ms
            12000 to arrayOf(120, 240, 480, 960),    // 10, 20, 40, 80ms
            16000 to arrayOf(160, 320, 640, 960),    // 10, 20, 40, 60ms
            24000 to arrayOf(240, 480, 960, 1440),   // 10, 20, 40, 60ms
            48000 to arrayOf(480, 960, 1920, 2880)   // 10, 20, 40, 60ms
        )
    }

    private var encoderPtr: Long = 0L
    private var decoderPtr: Long = 0L
    private var isInitialized = false

    /**
     * 初始化编解码器
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 验证参数
            if (!isValidConfiguration()) {
                Log.e(TAG, "❌ 无效的Opus配置: sampleRate=$sampleRate, channels=$channels, frameSize=$frameSize")
                return@withContext false
            }

            // 创建编码器
            encoderPtr = OpusNative.createEncoder(sampleRate, channels, complexity, bitRate)
            if (encoderPtr == 0L) {
                Log.e(TAG, "❌ Opus编码器创建失败")
                return@withContext false
            }

            // 创建解码器
            decoderPtr = OpusNative.createDecoder(sampleRate, channels)
            if (decoderPtr == 0L) {
                Log.e(TAG, "❌ Opus解码器创建失败")
                OpusNative.destroyEncoder(encoderPtr)
                encoderPtr = 0L
                return@withContext false
            }

            isInitialized = true
            Log.d(TAG, "✅ Opus编解码器初始化成功: ${sampleRate}Hz, ${channels}ch, ${frameSize}samples, ${bitRate}bps")
            Log.d(TAG, "🔖 Opus版本: ${OpusNative.getVersion()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Opus编解码器初始化失败: ${e.message}", e)
            cleanup()
            false
        }
    }

    /**
     * 编码PCM数据为Opus格式
     * @param pcmData PCM音频数据 (16-bit signed integers)
     * @return Opus编码后的字节数组，失败返回null
     */
    suspend fun encode(pcmData: ShortArray): ByteArray? = withContext(Dispatchers.IO) {
        if (!isInitialized || encoderPtr == 0L) {
            Log.e(TAG, "❌ 编码器未初始化")
            return@withContext null
        }

        if (pcmData.size != frameSize * channels) {
            Log.e(TAG, "❌ PCM数据大小不匹配: 期望${frameSize * channels}, 实际${pcmData.size}")
            return@withContext null
        }

        try {
            // 创建输出缓冲区，Opus最大包大小约4000字节
            val opusBuffer = ByteArray(4000)
            val encodedSize = OpusNative.encode(encoderPtr, pcmData, frameSize, opusBuffer)
            
            if (encodedSize < 0) {
                Log.e(TAG, "❌ Opus编码失败: $encodedSize")
                return@withContext null
            }
            
            val result = opusBuffer.copyOf(encodedSize)
            Log.v(TAG, "🎵 PCM编码: ${pcmData.size} samples -> ${result.size} bytes (压缩比: ${String.format("%.1f", (pcmData.size * 2).toFloat() / result.size)}:1)")
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Opus编码失败: ${e.message}", e)
            null
        }
    }

    /**
     * 解码Opus数据为PCM格式
     * @param opusData Opus编码的字节数组
     * @return PCM音频数据，失败返回null
     */
    suspend fun decode(opusData: ByteArray): ShortArray? = withContext(Dispatchers.IO) {
        if (!isInitialized || decoderPtr == 0L) {
            Log.e(TAG, "❌ 解码器未初始化")
            return@withContext null
        }

        try {
            // 创建输出缓冲区
            val pcmBuffer = ShortArray(frameSize * channels)
            val decodedSamples = OpusNative.decode(decoderPtr, opusData, opusData.size, pcmBuffer, frameSize)
            
            if (decodedSamples < 0) {
                Log.e(TAG, "❌ Opus解码失败: $decodedSamples")
                return@withContext null
            }
            
            val result = if (decodedSamples == pcmBuffer.size) {
                pcmBuffer
            } else {
                pcmBuffer.copyOf(decodedSamples)
            }
            
            Log.v(TAG, "🎵 Opus解码: ${opusData.size} bytes -> ${result.size} samples")
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Opus解码失败: ${e.message}", e)
            null
        }
    }

    /**
     * 批量编码PCM数据流
     * @param pcmStream PCM数据流
     * @return Opus编码后的数据流
     */
    suspend fun encodeStream(pcmStream: List<ShortArray>): List<ByteArray> = withContext(Dispatchers.IO) {
        val opusStream = mutableListOf<ByteArray>()
        var successCount = 0
        
        for (pcmFrame in pcmStream) {
            val opusFrame = encode(pcmFrame)
            if (opusFrame != null) {
                opusStream.add(opusFrame)
                successCount++
            } else {
                Log.w(TAG, "⚠️ 跳过编码失败的帧")
            }
        }
        
        Log.d(TAG, "📊 批量编码完成: ${successCount}/${pcmStream.size} 帧成功")
        opusStream
    }

    /**
     * 批量解码Opus数据流
     * @param opusStream Opus数据流
     * @return PCM解码后的数据流
     */
    suspend fun decodeStream(opusStream: List<ByteArray>): List<ShortArray> = withContext(Dispatchers.IO) {
        val pcmStream = mutableListOf<ShortArray>()
        var successCount = 0
        
        for (opusFrame in opusStream) {
            val pcmFrame = decode(opusFrame)
            if (pcmFrame != null) {
                pcmStream.add(pcmFrame)
                successCount++
            } else {
                Log.w(TAG, "⚠️ 跳过解码失败的帧")
            }
        }
        
        Log.d(TAG, "📊 批量解码完成: ${successCount}/${opusStream.size} 帧成功")
        pcmStream
    }

    /**
     * 转换PCM字节数组为ShortArray
     */
    fun pcmBytesToShorts(pcmBytes: ByteArray): ShortArray {
        val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        val shorts = ShortArray(pcmBytes.size / 2)
        for (i in shorts.indices) {
            if (buffer.remaining() >= 2) {
                shorts[i] = buffer.short
            }
        }
        return shorts
    }

    /**
     * 转换ShortArray为PCM字节数组
     */
    fun pcmShortsToBytes(pcmShorts: ShortArray): ByteArray {
        val buffer = ByteBuffer.allocate(pcmShorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (short in pcmShorts) {
            buffer.putShort(short)
        }
        return buffer.array()
    }

    /**
     * 验证配置是否有效
     */
    private fun isValidConfiguration(): Boolean {
        // 检查采样率
        if (sampleRate !in SUPPORTED_SAMPLE_RATES) {
            Log.e(TAG, "❌ 不支持的采样率: $sampleRate")
            return false
        }

        // 检查通道数
        if (channels !in 1..2) {
            Log.e(TAG, "❌ 不支持的通道数: $channels")
            return false
        }

        // 检查帧大小
        val supportedFrameSizes = SUPPORTED_FRAME_SIZES[sampleRate]
        if (supportedFrameSizes == null || frameSize !in supportedFrameSizes) {
            Log.e(TAG, "❌ 不支持的帧大小: $frameSize (采样率: $sampleRate)")
            return false
        }

        // 检查比特率
        if (bitRate < 6000 || bitRate > 510000) {
            Log.e(TAG, "❌ 不支持的比特率: $bitRate")
            return false
        }

        return true
    }

    /**
     * 获取编码器信息
     */
    fun getEncoderInfo(): String {
        return "Opus编解码器 - 采样率:${sampleRate}Hz, 通道:${channels}, 帧大小:${frameSize}, 比特率:${bitRate}bps, 复杂度:${complexity}"
    }

    /**
     * 获取每帧的持续时间（毫秒）
     */
    fun getFrameDurationMs(): Float {
        return (frameSize.toFloat() * 1000f) / sampleRate
    }

    /**
     * 获取每秒的帧数
     */
    fun getFramesPerSecond(): Float {
        return sampleRate.toFloat() / frameSize
    }

    /**
     * 估算压缩比
     */
    fun getCompressionRatio(): Float {
        val pcmBitRate = sampleRate * channels * 16 // 16-bit PCM
        return pcmBitRate.toFloat() / bitRate
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        try {
            if (encoderPtr != 0L) {
                OpusNative.destroyEncoder(encoderPtr)
                encoderPtr = 0L
            }
            if (decoderPtr != 0L) {
                OpusNative.destroyDecoder(decoderPtr)
                decoderPtr = 0L
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 清理Opus编解码器资源时出现异常: ${e.message}")
        } finally {
            isInitialized = false
            Log.d(TAG, "🧹 Opus编解码器资源已清理")
        }
    }

    /**
     * 检查编解码器是否可用
     */
    fun isReady(): Boolean {
        return isInitialized && encoderPtr != 0L && decoderPtr != 0L
    }
}
