package org.stypox.dicio.io.wake.onnx

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.sin

/**
 * HiNudge V8 ONNX模型Android端测试
 * 
 * 运行方式:
 * ./gradlew connectedAndroidTest --tests "org.stypox.dicio.io.wake.onnx.HiNudgeOnnxV8WakeDeviceTest"
 */
@RunWith(AndroidJUnit4::class)
class HiNudgeOnnxV8WakeDeviceTest {

    private lateinit var context: Context
    private lateinit var wakeDevice: HiNudgeOnnxV8WakeDevice
    
    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        wakeDevice = HiNudgeOnnxV8WakeDevice(context)
    }

    @Test
    fun testModelsAvailability() {
        println("====================================================================")
        println("🧪 测试1: 模型文件可用性")
        println("====================================================================")
        
        val modelFolder = File(context.filesDir, "hiNudgeOnnxV8")
        val melFile = File(modelFolder, "melspectrogram.onnx")
        val embFile = File(modelFolder, "embedding_model.onnx")
        val wakeFile = File(modelFolder, "korean_wake_word_v8.onnx")
        
        println("📁 模型目录: ${modelFolder.absolutePath}")
        println("📄 模型文件检查:")
        println("  - melspectrogram.onnx: ${if (melFile.exists()) "✅ EXISTS (${melFile.length()} bytes)" else "❌ MISSING"}")
        println("  - embedding_model.onnx: ${if (embFile.exists()) "✅ EXISTS (${embFile.length()} bytes)" else "❌ MISSING"}")
        println("  - korean_wake_word_v8.onnx: ${if (wakeFile.exists()) "✅ EXISTS (${wakeFile.length()} bytes)" else "❌ MISSING"}")
        
        // 检查Assets
        try {
            val assetFiles = context.assets.list("korean_hinudge_onnx")
            println("\n📦 Assets中的文件:")
            assetFiles?.forEach { println("  - $it") }
        } catch (e: Exception) {
            println("⚠️  无法列出Assets: ${e.message}")
        }
        
        println("====================================================================")
    }

    @Test
    fun testWakeDeviceInitialization() {
        println("====================================================================")
        println("🧪 测试2: WakeDevice初始化")
        println("====================================================================")
        
        // 等待初始化完成
        Thread.sleep(2000)
        
        val state = wakeDevice.state.value
        println("📊 当前状态: $state")
        
        when (state) {
            is org.stypox.dicio.io.wake.WakeState.Loaded -> {
                println("✅ 模型已加载成功!")
            }
            is org.stypox.dicio.io.wake.WakeState.Loading -> {
                println("⏳ 模型正在加载中...")
            }
            is org.stypox.dicio.io.wake.WakeState.NotLoaded -> {
                println("⚠️  模型未加载")
            }
            is org.stypox.dicio.io.wake.WakeState.NotDownloaded -> {
                println("❌ 模型未下载")
            }
            is org.stypox.dicio.io.wake.WakeState.ErrorLoading -> {
                println("❌ 加载错误: ${state.throwable.message}")
                state.throwable.printStackTrace()
            }
            else -> {
                println("🤷 未知状态: $state")
            }
        }
        
        println("====================================================================")
    }

    @Test
    fun testSyntheticAudioProcessing() {
        println("====================================================================")
        println("🧪 测试3: 合成音频处理")
        println("====================================================================")
        
        // 等待初始化
        Thread.sleep(3000)
        
        // 生成测试音频 (1280 samples = 80ms @ 16kHz)
        val frameSize = wakeDevice.frameSize()
        val testAudio = generateTestAudio(frameSize)
        
        println("🔊 测试音频:")
        println("  - Frame size: $frameSize samples")
        println("  - Duration: ${frameSize * 1000 / 16000}ms @ 16kHz")
        println("  - Min amplitude: ${testAudio.minOrNull()}")
        println("  - Max amplitude: ${testAudio.maxOrNull()}")
        
        // 处理10帧
        println("\n🔄 处理测试音频...")
        repeat(10) { i ->
            val detected = wakeDevice.processFrame(testAudio)
            println("  Frame ${i + 1}: ${if (detected) "✅ DETECTED" else "❌ Not detected"}")
            
            if (detected) {
                println("\n🎉 在合成音频上检测到唤醒词!")
                println("   (这可能是误报，因为使用的是随机音频)")
            }
        }
        
        println("====================================================================")
    }

    @Test
    fun testRealAudioFile() {
        println("====================================================================")
        println("🧪 测试4: 真实音频文件处理")
        println("====================================================================")
        
        // 等待初始化
        Thread.sleep(3000)
        
        // 尝试从assets加载测试音频
        val testAudioFiles = listOf(
            "test_audio/korean_hinudge_test.wav",
            "test_audio/positive_sample.wav",
            "test_audio/test.wav"
        )
        
        var audioLoaded = false
        
        for (audioPath in testAudioFiles) {
            try {
                println("📼 尝试加载: $audioPath")
                val audioStream = context.assets.open(audioPath)
                val audioBytes = audioStream.readBytes()
                audioStream.close()
                
                println("✅ 成功加载音频: ${audioBytes.size} bytes")
                
                // 简单解析WAV (跳过44字节头)
                if (audioBytes.size > 44) {
                    val frameSize = wakeDevice.frameSize()
                    val samplesNeeded = frameSize * 2 // 16-bit PCM
                    
                    if (audioBytes.size >= 44 + samplesNeeded) {
                        // 提取PCM数据并转换为Short数组
                        val audioData = ShortArray(frameSize) { i ->
                            val byteIndex = 44 + i * 2
                            val low = audioBytes[byteIndex].toInt() and 0xFF
                            val high = audioBytes[byteIndex + 1].toInt() and 0xFF
                            ((high shl 8) or low).toShort()
                        }
                        
                        println("🔊 音频数据:")
                        println("  - Samples: ${audioData.size}")
                        println("  - Min: ${audioData.minOrNull()}")
                        println("  - Max: ${audioData.maxOrNull()}")
                        
                        // 处理音频
                        println("\n🔄 处理真实音频...")
                        val detected = wakeDevice.processFrame(audioData)
                        println("🎯 检测结果: ${if (detected) "✅ 检测到唤醒词!" else "❌ 未检测到"}")
                        
                        audioLoaded = true
                        break
                    }
                }
            } catch (e: Exception) {
                println("⚠️  无法加载 $audioPath: ${e.message}")
            }
        }
        
        if (!audioLoaded) {
            println("\n⚠️  未找到测试音频文件，建议:")
            println("   1. 在 app/src/androidTest/assets/test_audio/ 放置测试音频")
            println("   2. 音频格式: 16kHz, 16-bit PCM, Mono WAV")
        }
        
        println("====================================================================")
    }

    @Test
    fun testMultipleFrames() {
        println("====================================================================")
        println("🧪 测试5: 连续帧处理")
        println("====================================================================")
        
        // 等待初始化
        Thread.sleep(3000)
        
        val frameSize = wakeDevice.frameSize()
        var detectionCount = 0
        val totalFrames = 100
        
        println("🔄 处理 $totalFrames 帧...")
        println("   每帧: $frameSize samples (${frameSize * 1000 / 16000}ms)")
        
        val startTime = System.currentTimeMillis()
        
        repeat(totalFrames) { i ->
            val testAudio = generateTestAudio(frameSize)
            val detected = wakeDevice.processFrame(testAudio)
            
            if (detected) {
                detectionCount++
                println("  Frame ${i + 1}: ✅ DETECTED")
            }
        }
        
        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        val avgTimePerFrame = totalTime.toFloat() / totalFrames
        
        println("\n📊 性能统计:")
        println("  - 总帧数: $totalFrames")
        println("  - 检测次数: $detectionCount")
        println("  - 检测率: ${detectionCount * 100 / totalFrames}%")
        println("  - 总耗时: ${totalTime}ms")
        println("  - 平均每帧: ${avgTimePerFrame}ms")
        println("  - 实时处理能力: ${if (avgTimePerFrame < 80) "✅ YES" else "❌ NO"}")
        
        println("====================================================================")
    }

    @Test
    fun testModelInputOutput() {
        println("====================================================================")
        println("🧪 测试6: 模型输入输出验证")
        println("====================================================================")
        
        // 这个测试需要访问内部方法，可能需要反射或修改代码
        // 暂时只检查基本信息
        
        val frameSize = wakeDevice.frameSize()
        println("📊 模型参数:")
        println("  - Frame size: $frameSize samples")
        println("  - Expected: 1280 samples (80ms @ 16kHz)")
        
        assertEquals("Frame size应该是1280", 1280, frameSize)
        
        println("✅ Frame size验证通过")
        println("====================================================================")
    }

    // ==================== 辅助方法 ====================

    /**
     * 生成测试音频
     * 混合正弦波，模拟语音
     */
    private fun generateTestAudio(samples: Int): ShortArray {
        val audio = ShortArray(samples)
        for (i in 0 until samples) {
            // 混合多个频率的正弦波
            val freq1 = 440.0 // A4
            val freq2 = 880.0 // A5
            val t = i.toDouble() / 16000.0
            val value = (sin(2 * Math.PI * freq1 * t) * 0.3 + 
                        sin(2 * Math.PI * freq2 * t) * 0.2) * 32767
            audio[i] = value.toInt().toShort()
        }
        return audio
    }

    /**
     * 生成静音
     */
    private fun generateSilence(samples: Int): ShortArray {
        return ShortArray(samples) { 0 }
    }
}

