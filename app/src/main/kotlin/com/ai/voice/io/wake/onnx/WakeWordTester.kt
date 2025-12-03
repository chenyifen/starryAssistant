package com.ai.voice.io.wake.onnx

import android.content.Context
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 唤醒词离线测试工具
 * 读取WAV文件测试HiNudgeOnnxV8WakeDevice
 */
class WakeWordTester(
    private val context: Context,
    private val wakeDevice: HiNudgeOnnxV8WakeDevice
) {
    companion object {
        private const val TAG = "WakeWordTester"
    }
    
    data class TestResult(
        val filePath: String,
        val maxScore: Float,
        val avgScore: Float,
        val frameCount: Int,
        val scores: List<Float>
    ) {
        fun isDetected(threshold: Float) = maxScore >= threshold
    }
    
    /**
     * 读取WAV文件（16-bit PCM, 16kHz）
     */
    fun readWavFile(file: File): ShortArray {
        val bytes = file.readBytes()
        
        // 跳过WAV头（44字节）
        val headerSize = 44
        val audioBytes = bytes.copyOfRange(headerSize, bytes.size)
        
        // 转换为short数组
        val audioShort = ShortArray(audioBytes.size / 2)
        ByteBuffer.wrap(audioBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(audioShort)
        
        return audioShort
    }
    
    /**
     * 测试单个WAV文件
     */
    fun testWavFile(file: File, threshold: Float = 0.6f): TestResult {
        Log.d(TAG, "========================================")
        Log.d(TAG, "测试文件: ${file.name}")
        
        wakeDevice.reset()
        
        val audio = readWavFile(file)
        val scores = mutableListOf<Float>()
        val frameSize = 1280
        
        var frameIdx = 0
        var i = 0
        while (i < audio.size) {
            val frame = ShortArray(frameSize)
            val copyLen = minOf(frameSize, audio.size - i)
            System.arraycopy(audio, i, frame, 0, copyLen)
            // 补零
            if (copyLen < frameSize) {
                for (j in copyLen until frameSize) {
                    frame[j] = 0
                }
            }
            
            val detected = wakeDevice.processFrame(frame)
            val score = wakeDevice.getLastScore()
            scores.add(score)
            
            if (detected) {
                Log.d(TAG, "  Frame #$frameIdx: 检测到唤醒词!")
            }
            
            i += frameSize
            frameIdx++
        }
        
        val maxScore = scores.maxOrNull() ?: 0.0f
        val avgScore = scores.average().toFloat()
        
        val result = TestResult(file.absolutePath, maxScore, avgScore, scores.size, scores)
        
        Log.d(TAG, "结果: maxScore=$maxScore, avgScore=$avgScore, frames=${scores.size}, detected=${result.isDetected(threshold)}")
        Log.d(TAG, "========================================")
        
        return result
    }
    
    /**
     * 批量测试目录
     */
    fun testDirectory(dir: File, maxSamples: Int = 20, threshold: Float = 0.6f): Map<String, Any> {
        val wavFiles = dir.listFiles { f -> f.name.endsWith(".wav") }?.sortedArray() ?: emptyArray()
        val testCount = minOf(maxSamples, wavFiles.size)
        
        Log.i(TAG, "========================================")
        Log.i(TAG, "测试目录: ${dir.absolutePath}")
        Log.i(TAG, "样本总数: ${wavFiles.size}, 测试数量: $testCount")
        Log.i(TAG, "========================================")
        
        var detected = 0
        val results = mutableListOf<TestResult>()
        
        for (i in 0 until testCount) {
            val result = testWavFile(wavFiles[i], threshold)
            results.add(result)
            if (result.isDetected(threshold)) {
                detected++
            }
        }
        
        return mapOf(
            "total" to testCount,
            "detected" to detected,
            "rate" to (detected.toFloat() / testCount * 100),
            "results" to results
        )
    }
}

