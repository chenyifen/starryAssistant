package org.stypox.dicio.io.audio

import android.util.Log

/**
 * Opus Native JNI绑定类
 * 提供与native Opus库的接口
 */
object OpusNative {
    private const val TAG = "OpusNative"
    
    init {
        try {
            System.loadLibrary("opus_jni")
            Log.d(TAG, "✅ Opus JNI库加载成功")
            Log.d(TAG, "🔖 Opus版本: ${getVersion()}")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "❌ Opus JNI库加载失败: ${e.message}", e)
        }
    }
    
    /**
     * 创建Opus编码器
     * @param sampleRateInHz 采样率 (8000, 12000, 16000, 24000, 48000)
     * @param channelConfig 通道数 (1=单声道, 2=立体声)
     * @param complexity 复杂度 (0-10, 推荐8)
     * @param bitrate 比特率 (6000-510000)
     * @return 编码器指针，失败返回0
     */
    external fun createEncoder(
        sampleRateInHz: Int,
        channelConfig: Int,
        complexity: Int,
        bitrate: Int
    ): Long
    
    /**
     * 创建Opus解码器
     * @param sampleRateInHz 采样率
     * @param channelConfig 通道数
     * @return 解码器指针，失败返回0
     */
    external fun createDecoder(sampleRateInHz: Int, channelConfig: Int): Long
    
    /**
     * 编码PCM数据为Opus
     * @param pOpusEnc 编码器指针
     * @param samples PCM样本数据
     * @param frameSize 帧大小（样本数）
     * @param bytes 输出缓冲区
     * @return 编码后的字节数，失败返回负数
     */
    external fun encode(
        pOpusEnc: Long,
        samples: ShortArray,
        frameSize: Int,
        bytes: ByteArray
    ): Int
    
    /**
     * 解码Opus数据为PCM
     * @param pOpusDec 解码器指针
     * @param bytes Opus数据
     * @param bytesLength 数据长度
     * @param samples 输出PCM缓冲区
     * @param frameSize 期望的帧大小
     * @return 解码后的样本数，失败返回负数
     */
    external fun decode(
        pOpusDec: Long,
        bytes: ByteArray,
        bytesLength: Int,
        samples: ShortArray,
        frameSize: Int
    ): Int
    
    /**
     * 销毁编码器
     */
    external fun destroyEncoder(pOpusEnc: Long)
    
    /**
     * 销毁解码器
     */
    external fun destroyDecoder(pOpusDec: Long)
    
    /**
     * 获取Opus版本信息
     */
    external fun getVersion(): String
    
    /**
     * 获取编码器大小
     */
    external fun getEncoderSize(channels: Int): Int
    
    /**
     * 获取解码器大小
     */
    external fun getDecoderSize(channels: Int): Int
}
