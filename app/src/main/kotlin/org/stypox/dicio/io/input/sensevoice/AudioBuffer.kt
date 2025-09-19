package org.stypox.dicio.io.input.sensevoice

import android.util.Log
import org.stypox.dicio.util.DebugLogger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * 两阶段识别的音频缓冲区
 * 管理实时音频数据的累积和获取
 */
class AudioBuffer(
    private val sampleRate: Int = 16000,
    private val maxDurationSeconds: Float = 30.0f // 最大缓存30秒音频
) {
    companion object {
        private const val TAG = "AudioBuffer"
    }
    
    private val lock = ReentrantReadWriteLock()
    private val maxSamples = (sampleRate * maxDurationSeconds).toInt()
    private val audioData = mutableListOf<Float>()
    
    // 统计信息
    private var totalSamplesAdded = 0L
    private var totalChunks = 0
    
    /**
     * 添加音频数据块
     */
    fun addAudioChunk(chunk: FloatArray) {
        if (chunk.isEmpty()) return
        
        lock.write {
            // 添加新数据
            for (sample in chunk) {
                audioData.add(sample)
            }
            
            // 限制缓冲区大小，移除旧数据
            while (audioData.size > maxSamples) {
                audioData.removeAt(0)
            }
            
            totalSamplesAdded += chunk.size
            totalChunks++
            Unit
        }
        
        DebugLogger.logAudio(TAG, "添加音频块: ${chunk.size}样本, 缓冲区总计: ${audioData.size}样本")
    }
    
    /**
     * 获取累积的音频数据副本
     */
    fun getAccumulatedAudio(): FloatArray {
        return lock.read {
            audioData.toFloatArray()
        }
    }
    
    /**
     * 获取最近指定时长的音频数据
     */
    fun getRecentAudio(durationSeconds: Float): FloatArray {
        val samplesNeeded = (sampleRate * durationSeconds).toInt()
        
        return lock.read {
            if (audioData.size <= samplesNeeded) {
                audioData.toFloatArray()
            } else {
                val startIndex = audioData.size - samplesNeeded
                FloatArray(samplesNeeded) { i ->
                    audioData[startIndex + i]
                }
            }
        }
    }
    
    /**
     * 清空缓冲区
     */
    fun clear() {
        lock.write {
            val previousSize = audioData.size
            audioData.clear()
            DebugLogger.logAudio(TAG, "清空音频缓冲区，之前大小: $previousSize")
            Unit
        }
    }
    
    /**
     * 获取当前缓冲区信息
     */
    fun getBufferInfo(): String {
        return lock.read {
            val currentDurationSeconds = audioData.size.toFloat() / sampleRate
            "AudioBuffer(${audioData.size}samples/${String.format("%.2f", currentDurationSeconds)}s)"
        }
    }
    
    /**
     * 检查缓冲区是否有足够的音频数据
     */
    fun hasMinimumAudio(minDurationSeconds: Float = 0.5f): Boolean {
        val minSamples = (sampleRate * minDurationSeconds).toInt()
        return lock.read {
            audioData.size >= minSamples
        }
    }
    
    /**
     * 获取音频质量统计
     */
    fun getAudioQualityStats(): AudioQualityStats {
        return lock.read {
            if (audioData.isEmpty()) {
                return AudioQualityStats()
            }
            
            val samples = audioData.toFloatArray()
            
            // 计算RMS (均方根)
            val rms = kotlin.math.sqrt(
                samples.map { it * it }.average()
            ).toFloat()
            
            // 计算峰值
            val peak = samples.maxOf { kotlin.math.abs(it) }
            
            // 计算零交叉率
            var zeroCrossings = 0
            for (i in 1 until samples.size) {
                if ((samples[i-1] >= 0 && samples[i] < 0) || 
                    (samples[i-1] < 0 && samples[i] >= 0)) {
                    zeroCrossings++
                }
            }
            val zeroCrossingRate = zeroCrossings.toFloat() / samples.size
            
            AudioQualityStats(
                rms = rms,
                peak = peak,
                zeroCrossingRate = zeroCrossingRate,
                signalToNoiseRatio = if (rms > 0) 20 * kotlin.math.log10(peak / rms) else 0f
            )
        }
    }
    
    /**
     * 音频质量统计数据类
     */
    data class AudioQualityStats(
        val rms: Float = 0f,
        val peak: Float = 0f,
        val zeroCrossingRate: Float = 0f,
        val signalToNoiseRatio: Float = 0f
    ) {
        fun isGoodQuality(): Boolean {
            return rms > 0.001f && // 有足够的信号强度
                   peak < 0.95f && // 没有剪切
                   zeroCrossingRate > 0.01f && // 有语音内容
                   zeroCrossingRate < 0.5f // 不是噪音
        }
        
        override fun toString(): String {
            return "AudioQuality(" +
                    "RMS=${String.format("%.4f", rms)}, " +
                    "Peak=${String.format("%.4f", peak)}, " +
                    "ZCR=${String.format("%.4f", zeroCrossingRate)}, " +
                    "SNR=${String.format("%.2f", signalToNoiseRatio)}dB" +
                    ")"
        }
    }
    
    /**
     * 应用音频预处理
     */
    fun getProcessedAudio(): FloatArray {
        return lock.read {
            if (audioData.isEmpty()) {
                return floatArrayOf()
            }
            
            val samples = audioData.toFloatArray()
            
            // 应用高通滤波器去除低频噪音
            val filtered = applyHighPassFilter(samples)
            
            // 应用音量归一化
            val normalized = normalizeVolume(filtered)
            
            normalized
        }
    }
    
    /**
     * 简单高通滤波器（去除直流分量和低频噪音）
     */
    private fun applyHighPassFilter(input: FloatArray, alpha: Float = 0.95f): FloatArray {
        if (input.isEmpty()) return input
        
        val filtered = FloatArray(input.size)
        filtered[0] = input[0]
        
        for (i in 1 until input.size) {
            filtered[i] = alpha * (filtered[i-1] + input[i] - input[i-1])
        }
        
        return filtered
    }
    
    /**
     * 音量归一化
     */
    private fun normalizeVolume(input: FloatArray, targetRms: Float = 0.1f): FloatArray {
        if (input.isEmpty()) return input
        
        // 计算当前RMS
        val currentRms = kotlin.math.sqrt(
            input.map { it * it }.average()
        ).toFloat()
        
        if (currentRms < 1e-6f) {
            return input // 避免除零
        }
        
        // 计算增益
        val gain = targetRms / currentRms
        val maxGain = 10.0f // 限制最大增益
        val actualGain = kotlin.math.min(gain, maxGain)
        
        // 应用增益
        return input.map { sample ->
            kotlin.math.max(-1.0f, kotlin.math.min(1.0f, sample * actualGain))
        }.toFloatArray()
    }
}