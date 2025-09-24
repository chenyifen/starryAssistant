package org.stypox.dicio.io.wake.oww

import android.content.Context
import android.content.res.AssetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.stypox.dicio.io.wake.WakeDevice
import org.stypox.dicio.io.wake.WakeState
import org.stypox.dicio.util.DebugLogger
import org.stypox.dicio.util.measureTimeAndLog
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.*
import kotlin.collections.ArrayList
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession

/**
 * 하이넛지 (Hi Nudge) 韩语唤醒词设备
 * 完全基于OpenWakewordforAndroid-main的实现，使用ONNX Runtime进行推理
 */
class HiNudgeOpenWakeWordDevice(
    @ApplicationContext private val appContext: Context,
) : WakeDevice {
    
    companion object {
        private const val TAG = "HiNudgeOpenWakeWordDevice"
        
        // 音频参数 - 完全按照demo
        private const val SAMPLE_RATE = 16000
        private const val N_PREPARED_SAMPLES = 1280  // 80ms at 16kHz
        private const val MELSPECTROGRAM_MAX_LEN = 10 * 97
        private const val FEATURE_BUFFER_MAX_LEN = 120
        private const val WINDOW_SIZE = 76
        private const val STEP_SIZE = 8
        
        // 模型参数
        private const val MEL_FRAMES = 76
        private const val MEL_BINS = 32
        private const val EMBEDDING_SIZE = 96
        private const val FEATURE_FRAMES = 16
        private const val BATCH_SIZE = 1
    }
    
    private val _state: MutableStateFlow<WakeState>
    override val state: StateFlow<WakeState>
    
    // 调试计数器
    private var frameCount = 0
    
    // 模型文件路径
    private val hiNudgeFolder = File(appContext.filesDir, "hiNudgeOpenWakeWord")
    private val melFile = File(hiNudgeFolder, "melspectrogram.onnx")
    private val embFile = File(hiNudgeFolder, "embedding_model.onnx")
    private val wakeFile = File(hiNudgeFolder, "hey_nugget_new.onnx")
    
    // 外部存储路径（优先级最高）
    private val externalModelDir = File("/storage/emulated/0/Dicio/models/openWakeWord")
    
    // ONNX Runtime 组件
    private var modelRunner: ONNXModelRunner? = null
    private var model: Model? = null
    
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        DebugLogger.logWakeWord(TAG, "🇰🇷 Initializing HiNudgeOpenWakeWordDevice")
        DebugLogger.logWakeWord(TAG, "📁 HiNudge folder: ${hiNudgeFolder.absolutePath}")
        DebugLogger.logWakeWord(TAG, "📱 External model dir: ${externalModelDir.absolutePath}")
        
        val modelsAvailable = hasModelsAvailable()
        DebugLogger.logWakeWord(TAG, "✅ Models available: $modelsAvailable")
        
        _state = if (modelsAvailable) {
            MutableStateFlow(WakeState.NotLoaded)
        } else {
            MutableStateFlow(WakeState.NotDownloaded)
        }
        state = _state
        
        DebugLogger.logStateMachine(TAG, "Initial state: ${_state.value}")
        
        // 自动复制和加载模型
        scope.launch {
            try {
                // 确保模型文件可用
                if (!hasLocalModels() && hasExternalModels()) {
                    DebugLogger.logModelManagement(TAG, "🔄 Auto-copying HiNudge models from external storage")
                    if (copyModelsFromExternal()) {
                        DebugLogger.logModelManagement(TAG, "✅ Successfully copied models from external storage")
                    } else {
                        DebugLogger.logWakeWordError(TAG, "❌ Failed to copy models from external storage")
                        _state.value = WakeState.ErrorLoading(IOException("Failed to copy models"))
                        return@launch
                    }
                }
                
                // 加载模型
                if (hasLocalModels()) {
                    DebugLogger.logWakeWord(TAG, "🚀 Loading HiNudge models...")
                    _state.value = WakeState.Loading
                    
                    loadModels()
                    
                    _state.value = WakeState.Loaded
                    DebugLogger.logWakeWord(TAG, "✅ HiNudge models loaded successfully")
                } else {
                    DebugLogger.logWakeWordError(TAG, "❌ No HiNudge models available")
                    _state.value = WakeState.ErrorLoading(IOException("No models available"))
                }
            } catch (e: Exception) {
                DebugLogger.logWakeWordError(TAG, "❌ Failed to initialize HiNudge models", e)
                _state.value = WakeState.ErrorLoading(e)
            }
        }
    }
    
    private fun hasModelsAvailable(): Boolean {
        return hasLocalModels() || hasExternalModels()
    }
    
    private fun hasLocalModels(): Boolean {
        return melFile.exists() && embFile.exists() && wakeFile.exists()
    }
    
    private fun hasExternalModels(): Boolean {
        val extMel = File(externalModelDir, "melspectrogram.onnx")
        val extEmb = File(externalModelDir, "embedding_model.onnx")
        val extWake = File(externalModelDir, "hey_nugget_new.onnx")
        return extMel.exists() && extEmb.exists() && extWake.exists()
    }
    
    private fun copyModelsFromExternal(): Boolean {
        return try {
            hiNudgeFolder.mkdirs()
            
            val extMel = File(externalModelDir, "melspectrogram.onnx")
            val extEmb = File(externalModelDir, "embedding_model.onnx")
            val extWake = File(externalModelDir, "hey_nugget_new.onnx")
            
            // 验证外部模型文件
            if (!extMel.exists() || !extEmb.exists() || !extWake.exists()) {
                DebugLogger.logWakeWordError(TAG, "❌ External model files missing")
                return false
            }
            
            // 复制文件
            extMel.copyTo(melFile, overwrite = true)
            extEmb.copyTo(embFile, overwrite = true)
            extWake.copyTo(wakeFile, overwrite = true)
            
            // 验证复制结果
            val copySuccess = hasLocalModels() && 
                            melFile.length() == extMel.length() &&
                            embFile.length() == extEmb.length() &&
                            wakeFile.length() == extWake.length()
            
            DebugLogger.logModelManagement(TAG, "✅ HiNudge models copied: $copySuccess")
            DebugLogger.logModelManagement(TAG, "📊 Sizes: mel=${melFile.length()}, emb=${embFile.length()}, wake=${wakeFile.length()}")
            
            copySuccess
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ Failed to copy models from external storage", e)
            false
        }
    }
    
    private fun loadModels() {
        DebugLogger.logWakeWord(TAG, "🔄 Loading ONNX models...")
        
        try {
            // 创建自定义AssetManager来访问文件
            val customAssetManager = CustomAssetManager(hiNudgeFolder)
            modelRunner = ONNXModelRunner(customAssetManager)
            model = Model(modelRunner!!)
            
            DebugLogger.logWakeWord(TAG, "✅ All HiNudge models loaded successfully")
            
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ Failed to load HiNudge models", e)
            throw e
        }
    }
    
    override fun download() {
        if (hasModelsAvailable()) {
            DebugLogger.logWakeWord(TAG, "📦 Models already available")
            return
        }
        
        DebugLogger.logWakeWordError(TAG, "❌ No HiNudge models available for download")
        DebugLogger.logWakeWordError(TAG, "💡 Please place models in: ${externalModelDir.absolutePath}")
        DebugLogger.logWakeWordError(TAG, "📋 Required files: melspectrogram.onnx, embedding_model.onnx, hey_nugget_new.onnx")
        _state.value = WakeState.ErrorDownloading(IOException("HiNudge models not found"))
    }

    override fun processFrame(audio16bitPcm: ShortArray): Boolean {
        // 添加调试日志确认方法被调用
        frameCount++
        if (frameCount % 100 == 0) {
            DebugLogger.logWakeWord(TAG, "🔄 HiNudge processing frame #$frameCount, size: ${audio16bitPcm.size}")
        }
        
        if (audio16bitPcm.size != N_PREPARED_SAMPLES) {
            DebugLogger.logWakeWordError(TAG, "❌ Invalid frame size: ${audio16bitPcm.size}, expected: $N_PREPARED_SAMPLES")
            throw IllegalArgumentException("HiNudgeOpenWakeWordDevice expects $N_PREPARED_SAMPLES samples per frame")
        }

        if (_state.value != WakeState.Loaded) {
            DebugLogger.logWakeWordError(TAG, "❌ Model not ready, current state: ${_state.value}")
            return false
        }

        try {
            val startTime = System.currentTimeMillis()
            
            // 转换音频数据 - 完全按照demo
            val audioFloat = FloatArray(audio16bitPcm.size) { i ->
                audio16bitPcm[i].toFloat() / 32768.0f
            }
            
            // 预测唤醒词 - 完全按照demo
            val result = model?.predictWakeWord(audioFloat) ?: "0.0"
            val score = result.toFloatOrNull() ?: 0.0f
            
            val processingTime = System.currentTimeMillis() - startTime
            
            // 每1000帧记录一次性能统计
            if (frameCount % 1000 == 0) {
                DebugLogger.logWakeWord(TAG, "⚡ Frame processing time: ${processingTime}ms")
            }
            
            // 每1000帧记录一次处理状态
            if (frameCount % 1000 == 0) {
                DebugLogger.logWakeWord(TAG, "📊 HiNudge processed $frameCount frames, latest score: $score")
            }
            
            // 检查是否检测到唤醒词（阈值按照demo设置）
            val threshold = 0.05f
            val detected = score > threshold
            
            if (detected) {
                DebugLogger.logWakeWord(TAG, "🎯 Wake word detected! Score: $score (threshold: $threshold)")
            } else if (score > 0.01f) { // 记录接近阈值的分数
                DebugLogger.logWakeWord(TAG, "🔍 Wake word score: $score (below threshold: $threshold)")
            }
            
            return detected
            
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ Error processing audio frame", e)
            return false
        }
    }

    override fun frameSize(): Int = N_PREPARED_SAMPLES

    override fun destroy() {
        DebugLogger.logWakeWord(TAG, "🗑️ Releasing HiNudge resources...")
        
        try {
            modelRunner?.close()
            modelRunner = null
            model = null
            
            scope.cancel()
            
            DebugLogger.logWakeWord(TAG, "✅ HiNudge resources released")
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ Error releasing resources", e)
        }
    }

    override fun isHeyDicio(): Boolean = false // HiNudge is a custom Korean wake word
}

/**
 * 自定义AssetManager，用于从文件系统加载模型
 */
private class CustomAssetManager(private val modelDir: File) {
    fun open(filename: String): InputStream {
        val file = File(modelDir, filename)
        if (!file.exists()) {
            throw IOException("Model file not found: ${file.absolutePath}")
        }
        return file.inputStream()
    }
}

/**
 * ONNX模型运行器 - 完全按照demo实现
 */
private class ONNXModelRunner(private val assetManager: CustomAssetManager) {
    companion object {
        private const val TAG = "ONNXModelRunner"
        private const val BATCH_SIZE = 1
    }
    
    private val heyNudgetEnv = OrtEnvironment.getEnvironment()
    private val heyNudgetSession: OrtSession
    
    init {
        try {
            heyNudgetSession = heyNudgetEnv.createSession(readModelFile(assetManager, "hey_nugget_new.onnx"))
            DebugLogger.logWakeWord(TAG, "✅ Hey Nudget session created")
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ Failed to create Hey Nudget session", e)
            throw e
        }
    }

    fun getMelSpectrogram(inputArray: FloatArray): Array<FloatArray> {
        var session: OrtSession? = null
        var inputTensor: OnnxTensor? = null
        
        return try {
            assetManager.open("melspectrogram.onnx").use { modelInputStream ->
                val modelBytes = modelInputStream.readBytes()
                session = OrtEnvironment.getEnvironment().createSession(modelBytes)
            }
            
            val samples = inputArray.size
            val floatBuffer = FloatBuffer.wrap(inputArray)
            inputTensor = OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), floatBuffer, longArrayOf(BATCH_SIZE.toLong(), samples.toLong()))

            session!!.run(Collections.singletonMap(session!!.inputNames.iterator().next(), inputTensor)).use { results ->
                val outputTensor = results.get(0).value as Array<Array<Array<FloatArray>>>
                val squeezed = squeeze(outputTensor)
                applyMelSpecTransform(squeezed)
            }
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ Error generating mel spectrogram", e)
            Array(76) { FloatArray(32) { 1.0f } }
        } finally {
            inputTensor?.close()
            session?.close()
        }
    }

    private fun squeeze(originalArray: Array<Array<Array<FloatArray>>>): Array<FloatArray> {
        val squeezedArray = Array(originalArray[0][0].size) { FloatArray(originalArray[0][0][0].size) }
        for (i in originalArray[0][0].indices) {
            for (j in originalArray[0][0][0].indices) {
                squeezedArray[i][j] = originalArray[0][0][i][j]
            }
        }
        return squeezedArray
    }

    private fun applyMelSpecTransform(array: Array<FloatArray>): Array<FloatArray> {
        val transformedArray = Array(array.size) { FloatArray(array[0].size) }
        for (i in array.indices) {
            for (j in array[i].indices) {
                transformedArray[i][j] = array[i][j] / 10.0f + 2.0f
            }
        }
        return transformedArray
    }

    fun generateEmbeddings(input: Array<Array<Array<FloatArray>>>): Array<FloatArray> {
        var session: OrtSession? = null
        var inputTensor: OnnxTensor? = null
        
        return try {
            val env = OrtEnvironment.getEnvironment()
            assetManager.open("embedding_model.onnx").use { inputStream ->
                val model = inputStream.readBytes()
                session = env.createSession(model)
            }
            
            inputTensor = OnnxTensor.createTensor(env, input)
            session!!.run(Collections.singletonMap("input_1", inputTensor)).use { results ->
                val rawOutput = results.get(0).value as Array<Array<Array<FloatArray>>>
                
                // 重塑输出 (41, 1, 1, 96) -> (41, 96)
                val reshapedOutput = Array(rawOutput.size) { FloatArray(rawOutput[0][0][0].size) }
                for (i in rawOutput.indices) {
                    System.arraycopy(rawOutput[i][0][0], 0, reshapedOutput[i], 0, rawOutput[i][0][0].size)
                }
                reshapedOutput
            }
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ Error generating embeddings", e)
            arrayOf()
        } finally {
            inputTensor?.close()
            session?.close()
        }
    }

    fun predictWakeWord(inputArray: Array<Array<FloatArray>>): String {
        var inputTensor: OnnxTensor? = null
        
        return try {
            inputTensor = OnnxTensor.createTensor(heyNudgetEnv, inputArray)
            val outputs = heyNudgetSession.run(Collections.singletonMap(heyNudgetSession.inputNames.iterator().next(), inputTensor))
            val result = outputs.get(0).value as Array<FloatArray>
            String.format("%.5f", result[0][0].toDouble())
        } catch (e: OrtException) {
            DebugLogger.logWakeWordError(TAG, "❌ Error predicting wake word", e)
            "0.00000"
        } finally {
            inputTensor?.close()
        }
    }

    private fun readModelFile(assetManager: CustomAssetManager, filename: String): ByteArray {
        return assetManager.open(filename).use { it.readBytes() }
    }
    
    fun close() {
        try {
            heyNudgetSession.close()
            DebugLogger.logWakeWord(TAG, "✅ ONNX sessions closed")
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ Error closing ONNX sessions", e)
        }
    }
}

/**
 * 模型类 - 完全按照demo实现
 */
private class Model(private val modelRunner: ONNXModelRunner) {
    companion object {
        private const val TAG = "HiNudgeModel"
    }
    
    private val nPreparedSamples = 1280
    private val sampleRate = 16000
    private val melspectrogramMaxLen = 10 * 97
    private val featureBufferMaxLen = 120
    
    private var featureBuffer: Array<FloatArray>
    private val rawDataBuffer = ArrayDeque<Float>(sampleRate * 10)
    private var rawDataRemainder = floatArrayOf()
    private var melspectrogramBuffer: Array<FloatArray>
    private var accumulatedSamples = 0

    init {
        melspectrogramBuffer = Array(76) { FloatArray(32) { 1.0f } }
        
        try {
            featureBuffer = getEmbeddings(generateRandomIntArray(sampleRate * 4), 76, 8)
            DebugLogger.logWakeWord(TAG, "✅ Model initialized with feature buffer size: ${featureBuffer.size}")
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ Error initializing model", e)
            featureBuffer = arrayOf()
        }
    }

    fun predictWakeWord(audioBuffer: FloatArray): String {
        streamingFeatures(audioBuffer)
        val res = getFeatures(16, -1)
        return modelRunner.predictWakeWord(res)
    }

    private fun getFeatures(nFeatureFrames: Int, startNdx: Int): Array<Array<FloatArray>> {
        val actualStartNdx = if (startNdx != -1) startNdx else maxOf(0, featureBuffer.size - nFeatureFrames)
        val endNdx = if (startNdx != -1) actualStartNdx + nFeatureFrames else featureBuffer.size
        val length = endNdx - actualStartNdx

        val result = Array(1) { Array(length) { FloatArray(featureBuffer[0].size) } }
        for (i in 0 until length) {
            System.arraycopy(featureBuffer[actualStartNdx + i], 0, result[0][i], 0, featureBuffer[actualStartNdx + i].size)
        }
        return result
    }

    private fun getEmbeddings(x: FloatArray, windowSize: Int, stepSize: Int): Array<FloatArray> {
        val spec = modelRunner.getMelSpectrogram(x)
        val windows = ArrayList<Array<FloatArray>>()

        for (i in 0..spec.size - windowSize step stepSize) {
            val window = Array(windowSize) { FloatArray(spec[0].size) }
            for (j in 0 until windowSize) {
                System.arraycopy(spec[i + j], 0, window[j], 0, spec[0].size)
            }
            if (window.size == windowSize) {
                windows.add(window)
            }
        }

        val batch = Array(windows.size) { Array(windowSize) { Array(spec[0].size) { FloatArray(1) } } }
        for (i in windows.indices) {
            for (j in 0 until windowSize) {
                for (k in 0 until spec[0].size) {
                    batch[i][j][k][0] = windows[i][j][k]
                }
            }
        }

        return try {
            modelRunner.generateEmbeddings(batch)
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ Error getting embeddings", e)
            arrayOf()
        }
    }

    private fun generateRandomIntArray(size: Int): FloatArray {
        val arr = FloatArray(size)
        val random = Random()
        for (i in 0 until size) {
            arr[i] = (random.nextInt(2000) - 1000).toFloat()
        }
        return arr
    }

    private fun bufferRawData(x: FloatArray) {
        while (rawDataBuffer.size + x.size > sampleRate * 10) {
            rawDataBuffer.poll()
        }
        for (value in x) {
            rawDataBuffer.offer(value)
        }
    }

    private fun streamingMelSpectrogram(nSamples: Int) {
        if (rawDataBuffer.size < 400) {
            throw IllegalArgumentException("The number of input frames must be at least 400 samples @ 16kHz (25 ms)!")
        }

        val tempArray = FloatArray(nSamples + 480)
        val rawDataArray = rawDataBuffer.toTypedArray()
        for (i in maxOf(0, rawDataArray.size - nSamples - 480) until rawDataArray.size) {
            tempArray[i - maxOf(0, rawDataArray.size - nSamples - 480)] = rawDataArray[i]
        }

        val newMelSpectrogram = try {
            modelRunner.getMelSpectrogram(tempArray)
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ Error in streaming mel spectrogram", e)
            return
        }

        val combined = Array(melspectrogramBuffer.size + newMelSpectrogram.size) { FloatArray(32) }
        System.arraycopy(melspectrogramBuffer, 0, combined, 0, melspectrogramBuffer.size)
        System.arraycopy(newMelSpectrogram, 0, combined, melspectrogramBuffer.size, newMelSpectrogram.size)
        melspectrogramBuffer = combined

        if (melspectrogramBuffer.size > melspectrogramMaxLen) {
            val trimmed = Array(melspectrogramMaxLen) { FloatArray(32) }
            System.arraycopy(melspectrogramBuffer, melspectrogramBuffer.size - melspectrogramMaxLen, trimmed, 0, melspectrogramMaxLen)
            melspectrogramBuffer = trimmed
        }
    }

    private fun streamingFeatures(audioBuffer: FloatArray): Int {
        var processedSamples = 0
        accumulatedSamples = 0
        
        val fullBuffer = if (rawDataRemainder.isNotEmpty()) {
            val concatenated = FloatArray(rawDataRemainder.size + audioBuffer.size)
            System.arraycopy(rawDataRemainder, 0, concatenated, 0, rawDataRemainder.size)
            System.arraycopy(audioBuffer, 0, concatenated, rawDataRemainder.size, audioBuffer.size)
            rawDataRemainder = floatArrayOf()
            concatenated
        } else {
            audioBuffer
        }

        if (accumulatedSamples + fullBuffer.size >= 1280) {
            val remainder = (accumulatedSamples + fullBuffer.size) % 1280
            if (remainder != 0) {
                val xEvenChunks = FloatArray(fullBuffer.size - remainder)
                System.arraycopy(fullBuffer, 0, xEvenChunks, 0, fullBuffer.size - remainder)
                bufferRawData(xEvenChunks)
                accumulatedSamples += xEvenChunks.size
                rawDataRemainder = FloatArray(remainder)
                System.arraycopy(fullBuffer, fullBuffer.size - remainder, rawDataRemainder, 0, remainder)
            } else {
                bufferRawData(fullBuffer)
                accumulatedSamples += fullBuffer.size
                rawDataRemainder = floatArrayOf()
            }
        } else {
            accumulatedSamples += fullBuffer.size
            bufferRawData(fullBuffer)
        }

        if (accumulatedSamples >= 1280 && accumulatedSamples % 1280 == 0) {
            streamingMelSpectrogram(accumulatedSamples)

            val x = Array(1) { Array(76) { Array(32) { FloatArray(1) } } }

            for (i in (accumulatedSamples / 1280) - 1 downTo 0) {
                val ndx = if (-8 * i == 0) melspectrogramBuffer.size else -8 * i
                val start = maxOf(0, ndx - 76)
                val end = ndx

                for (j in start until end) {
                    for (w in 0 until 32) {
                        x[0][j - start][w][0] = melspectrogramBuffer[j][w]
                    }
                }
                
                if (x[0].size == 76) {
                    try {
                        val newFeatures = modelRunner.generateEmbeddings(x)
                        if (featureBuffer.isEmpty()) {
                            featureBuffer = newFeatures
                        } else {
                            val totalRows = featureBuffer.size + newFeatures.size
                            val numColumns = featureBuffer[0].size
                            val updatedBuffer = Array(totalRows) { FloatArray(numColumns) }

                            for (l in featureBuffer.indices) {
                                System.arraycopy(featureBuffer[l], 0, updatedBuffer[l], 0, featureBuffer[l].size)
                            }

                            for (k in newFeatures.indices) {
                                System.arraycopy(newFeatures[k], 0, updatedBuffer[k + featureBuffer.size], 0, newFeatures[k].size)
                            }

                            featureBuffer = updatedBuffer
                        }
                    } catch (e: Exception) {
                        DebugLogger.logWakeWordError(TAG, "❌ Error in streaming features", e)
                    }
                }
            }
            processedSamples = accumulatedSamples
            accumulatedSamples = 0
        }
        
        if (featureBuffer.size > featureBufferMaxLen) {
            val trimmedFeatureBuffer = Array(featureBufferMaxLen) { FloatArray(featureBuffer[0].size) }
            for (i in 0 until featureBufferMaxLen) {
                trimmedFeatureBuffer[i] = featureBuffer[featureBuffer.size - featureBufferMaxLen + i]
            }
            featureBuffer = trimmedFeatureBuffer
        }
        
        return if (processedSamples != 0) processedSamples else accumulatedSamples
    }
}