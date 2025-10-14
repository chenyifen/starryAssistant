package org.stypox.dicio.io.wake.onnx

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.stypox.dicio.io.wake.WakeDevice
import org.stypox.dicio.io.wake.WakeState
import org.stypox.dicio.ui.util.Progress
import org.stypox.dicio.util.DebugLogger
import java.io.File
import java.io.IOException
import javax.inject.Inject
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.util.*

/**
 * HiNudge韩语唤醒词设备 - ONNX V8版本
 * 
 * 完全按照OpenwakewordforAndroid-main的流式处理实现
 * V8模型特点: 召回率100%, 精确率72%, F1=84%
 */
class HiNudgeOnnxV8WakeDevice @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : WakeDevice {

    companion object {
        private const val TAG = "HiNudgeOnnxV8WakeDevice"
        
        // 资源路径
        private const val ASSET_MODEL_DIR = "korean_hinudge_onnx"
        private const val MEL_FILE_NAME = "melspectrogram.onnx"
        private const val EMB_FILE_NAME = "embedding_model.onnx"
        private const val WAKE_FILE_NAME = "korean_wake_word_v8.onnx"
        
        // 音频参数
        private const val N_PREPARED_SAMPLES = 1280  // 80ms @ 16kHz
        private const val SAMPLE_RATE = 16000
        private const val MELSPECTROGRAM_MAX_LEN = 10 * 97
        private const val FEATURE_BUFFER_MAX_LEN = 120
        private const val BATCH_SIZE = 1
        
        // 检测阈值 - V8模型优化
        private const val DETECTION_THRESHOLD = 0.3f
    }

    private val _state: MutableStateFlow<WakeState>
    override val state: StateFlow<WakeState>

    // 模型文件路径
    private val modelFolder = File(appContext.filesDir, "hiNudgeOnnxV8")
    private val melFile = File(modelFolder, MEL_FILE_NAME)
    private val embFile = File(modelFolder, EMB_FILE_NAME)
    private val wakeFile = File(modelFolder, WAKE_FILE_NAME)
    
    // ONNX Runtime组件
    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var wakeSession: OrtSession? = null
    
    // 流式处理缓冲区
    private var featureBuffer: Array<FloatArray> = Array(0) { FloatArray(96) }
    private val rawDataBuffer = ArrayDeque<Float>(SAMPLE_RATE * 10)
    private var rawDataRemainder = floatArrayOf()
    private var melspectrogramBuffer: Array<FloatArray> = Array(76) { FloatArray(32) { 1.0f } }
    private var accumulatedSamples = 0

    private val scope = CoroutineScope(Dispatchers.IO)
    
    // 调试计数器
    private var frameCount = 0
    private var lastLogTime = System.currentTimeMillis()

    init {
        DebugLogger.logWakeWord(TAG, "=".repeat(60))
        DebugLogger.logWakeWord(TAG, "🇰🇷 Initializing HiNudgeOnnxV8WakeDevice (Streaming)")
        DebugLogger.logWakeWord(TAG, "=".repeat(60))
        DebugLogger.logWakeWord(TAG, "📁 Model folder: ${modelFolder.absolutePath}")
        DebugLogger.logWakeWord(TAG, "📄 Model files:")
        DebugLogger.logWakeWord(TAG, "  - ${melFile.name}: ${if (melFile.exists()) "EXISTS (${melFile.length()} bytes)" else "❌ MISSING"}")
        DebugLogger.logWakeWord(TAG, "  - ${embFile.name}: ${if (embFile.exists()) "EXISTS (${embFile.length()} bytes)" else "❌ MISSING"}")
        DebugLogger.logWakeWord(TAG, "  - ${wakeFile.name}: ${if (wakeFile.exists()) "EXISTS (${wakeFile.length()} bytes)" else "❌ MISSING"}")
        DebugLogger.logWakeWord(TAG, "⚙️ Detection Threshold: $DETECTION_THRESHOLD")
        DebugLogger.logWakeWord(TAG, "🎯 Expected Performance: Recall=100%, Precision=72%, F1=84%")

        val modelsAvailable = hasModelsAvailable()
        DebugLogger.logWakeWord(TAG, "✅ Models available: $modelsAvailable")

        _state = if (modelsAvailable) {
            MutableStateFlow(WakeState.NotLoaded)
        } else {
            MutableStateFlow(WakeState.NotDownloaded)
        }
        state = _state

        // 自动复制和加载模型
        scope.launch {
            try {
                val hasLocal = hasLocalModels()
                val hasAssets = hasModelsInAssets()

                DebugLogger.logModelManagement(TAG, "Local models: $hasLocal, Assets models: $hasAssets")

                if (!hasLocal && hasAssets) {
                    DebugLogger.logModelManagement(TAG, "🔄 Auto-copying HiNudge V8 models from assets")
                    val copySuccess = copyModelsFromAssets()

                    if (copySuccess) {
                        DebugLogger.logModelManagement(TAG, "✅ Successfully copied V8 models")
                        _state.value = WakeState.NotLoaded
                    } else {
                        DebugLogger.logWakeWordError(TAG, "❌ Failed to copy V8 models")
                        _state.value = WakeState.ErrorLoading(IOException("Failed to copy models"))
                        return@launch
                    }
                }

                // 自动加载模型
                if (hasLocalModels()) {
                    DebugLogger.logWakeWord(TAG, "🚀 Auto-loading V8 models...")
                    loadModel()
                } else {
                    DebugLogger.logWakeWordError(TAG, "❌ No V8 models available to load")
                    _state.value = WakeState.ErrorLoading(IOException("No models available"))
                }
            } catch (e: Exception) {
                DebugLogger.logWakeWordError(TAG, "❌ Failed to initialize V8 models", e)
                _state.value = WakeState.ErrorLoading(e)
            }
        }
    }

    private fun hasModelsAvailable(): Boolean = hasLocalModels() || hasModelsInAssets()
    
    private fun hasLocalModels(): Boolean {
        return melFile.exists() && embFile.exists() && wakeFile.exists()
    }

    private fun hasModelsInAssets(): Boolean {
        return try {
            val files = appContext.assets.list(ASSET_MODEL_DIR)
            files?.contains(MEL_FILE_NAME) == true &&
            files.contains(EMB_FILE_NAME) &&
            files.contains(WAKE_FILE_NAME)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check assets", e)
            false
        }
    }

    private fun copyModelsFromAssets(): Boolean {
        return try {
            modelFolder.mkdirs()
            
            appContext.assets.open("$ASSET_MODEL_DIR/$MEL_FILE_NAME").use { input ->
                melFile.outputStream().use { output -> input.copyTo(output) }
            }
            
            appContext.assets.open("$ASSET_MODEL_DIR/$EMB_FILE_NAME").use { input ->
                embFile.outputStream().use { output -> input.copyTo(output) }
            }
            
            appContext.assets.open("$ASSET_MODEL_DIR/$WAKE_FILE_NAME").use { input ->
                wakeFile.outputStream().use { output -> input.copyTo(output) }
            }
            
            DebugLogger.logModelManagement(TAG, "✅ Copied all V8 models:")
            DebugLogger.logModelManagement(TAG, "  - ${melFile.name}: ${melFile.length()} bytes")
            DebugLogger.logModelManagement(TAG, "  - ${embFile.name}: ${embFile.length()} bytes")
            DebugLogger.logModelManagement(TAG, "  - ${wakeFile.name}: ${wakeFile.length()} bytes")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy V8 models", e)
            false
        }
    }

    override fun download() {
        scope.launch {
            try {
                _state.value = WakeState.Downloading(Progress.UNKNOWN)

                if (hasModelsInAssets()) {
                    DebugLogger.logModelManagement(TAG, "📥 Copying HiNudge V8 models from assets...")
                    val copySuccess = copyModelsFromAssets()

                    if (copySuccess) {
                        loadModel()
                        return@launch
                    }
                    
                    Log.e(TAG, "Failed to copy V8 models")
                    _state.value = WakeState.ErrorDownloading(Exception("Failed to copy V8 models"))
                } else {
                    Log.e(TAG, "No V8 models available")
                    _state.value = WakeState.ErrorDownloading(Exception("No V8 models available"))
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error during V8 model download", e)
                _state.value = WakeState.ErrorDownloading(e)
            }
        }
    }

    private fun loadModel() {
        try {
            DebugLogger.logWakeWord(TAG, "=".repeat(60))
            DebugLogger.logWakeWord(TAG, "🔄 Loading HiNudge ONNX V8 models...")
            DebugLogger.logWakeWord(TAG, "=".repeat(60))
            _state.value = WakeState.Loading

            if (!hasLocalModels()) {
                val error = Exception("V8 Model files do not exist")
                DebugLogger.logWakeWordError(TAG, "❌ Cannot load V8 models", error)
                _state.value = WakeState.ErrorLoading(error)
                return
            }

            val loadStartTime = System.currentTimeMillis()
            
            // 加载wake word模型
            wakeSession = ortEnv.createSession(wakeFile.absolutePath)
            DebugLogger.logWakeWord(TAG, "✅ Wake word model loaded")
            
            // 初始化feature buffer (使用随机数据)
            featureBuffer = getEmbeddings(generateRandomFloatArray(SAMPLE_RATE * 4), 76, 8)
            DebugLogger.logWakeWord(TAG, "✅ Feature buffer initialized: ${featureBuffer.size} frames")
            
            val loadTime = System.currentTimeMillis() - loadStartTime

            _state.value = WakeState.Loaded
            DebugLogger.logWakeWord(TAG, "=".repeat(60))
            DebugLogger.logWakeWord(TAG, "✅ HiNudge ONNX V8 models loaded successfully in ${loadTime}ms")
            DebugLogger.logWakeWord(TAG, "=".repeat(60))
            DebugLogger.logWakeWord(TAG, "🎯 V8 Model Performance:")
            DebugLogger.logWakeWord(TAG, "  - Recall: 100% (不会漏检)")
            DebugLogger.logWakeWord(TAG, "  - Precision: 72% (28%误报率)")
            DebugLogger.logWakeWord(TAG, "  - F1 Score: 84%")
            DebugLogger.logWakeWord(TAG, "  - Detection Threshold: $DETECTION_THRESHOLD")
            DebugLogger.logWakeWord(TAG, "  - Frame Size: $N_PREPARED_SAMPLES samples (80ms @ 16kHz)")
            DebugLogger.logWakeWord(TAG, "=".repeat(60))
            DebugLogger.logWakeWord(TAG, "🎤 Ready to detect wake word: 하이넛지 (Hi Nudge)")
            DebugLogger.logWakeWord(TAG, "=".repeat(60))
            
        } catch (t: Throwable) {
            DebugLogger.logWakeWordError(TAG, "❌ Failed to load V8 models", t)
            _state.value = WakeState.ErrorLoading(t)
        }
    }

    override fun processFrame(audio16bitPcm: ShortArray): Boolean {
        frameCount++
        
        if (wakeSession == null) {
            if (frameCount % 1000 == 0) {
                DebugLogger.logWakeWordError(TAG, "❌ V8 Model not loaded (frame #$frameCount)")
            }
            return false
        }

        // 转换音频格式: Short[] -> Float[] (归一化)
        val audioFloat = FloatArray(audio16bitPcm.size)
        for (i in audio16bitPcm.indices) {
            audioFloat[i] = audio16bitPcm[i] / 32768.0f
        }

        // 计算音频能量
        val audioEnergy = audioFloat.map { it * it }.average()
        
        // 流式处理并预测
        val score = predictWakeWord(audioFloat)
        val detected = score > DETECTION_THRESHOLD
        
        // 记录日志
        val currentTime = System.currentTimeMillis()
        if (frameCount % 100 == 0 || detected || score > 0.1f) {
            val timeSinceLastLog = currentTime - lastLogTime
            DebugLogger.logWakeWord(TAG, "🎤 V8 Frame #$frameCount | Score: %.4f | Threshold: %.2f | Energy: %.6f | Detected: %s | Δt: %dms".format(
                score, DETECTION_THRESHOLD, audioEnergy, if (detected) "✅" else "❌", timeSinceLastLog
            ))
            lastLogTime = currentTime
        }
        
        if (detected) {
            DebugLogger.logWakeWord(TAG, "🎉🎉🎉 V8 WAKE WORD DETECTED! 🎉🎉🎉")
            DebugLogger.logWakeWord(TAG, "📊 Detection Details:")
            DebugLogger.logWakeWord(TAG, "  - Score: %.4f (%.1f%% above threshold)".format(
                score, ((score - DETECTION_THRESHOLD) / DETECTION_THRESHOLD) * 100
            ))
            DebugLogger.logWakeWord(TAG, "  - Threshold: %.2f".format(DETECTION_THRESHOLD))
            DebugLogger.logWakeWord(TAG, "  - Audio Energy: %.6f".format(audioEnergy))
            DebugLogger.logWakeWord(TAG, "  - Frame: #$frameCount")
        }
        
        return detected
    }

    /**
     * 预测唤醒词 - 按照demo的流式处理
     */
    private fun predictWakeWord(audioBuffer: FloatArray): Float {
        return try {
            streamingFeatures(audioBuffer)
            val features = getFeatures(16, -1)
            predictWakeWordFromFeatures(features)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in predictWakeWord", e)
            0.0f
        }
    }
    
    /**
     * 获取Mel频谱图 - 使用ONNX
     */
    private fun getMelSpectrogram(inputArray: FloatArray): Array<FloatArray> {
        var session: OrtSession? = null
        var inputTensor: OnnxTensor? = null
        
        return try {
            session = ortEnv.createSession(melFile.absolutePath)
            
            val samples = inputArray.size
            val floatBuffer = FloatBuffer.wrap(inputArray)
            inputTensor = OnnxTensor.createTensor(
                ortEnv, 
                floatBuffer, 
                longArrayOf(BATCH_SIZE.toLong(), samples.toLong())
            )
            
            val results = session.run(mapOf(session.inputNames.iterator().next() to inputTensor))
            val outputTensor = results[0].value as Array<Array<Array<FloatArray>>>
            
            val squeezed = squeeze(outputTensor)
            val transformed = applyMelSpecTransform(squeezed)
            
            results.close()
            transformed
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error generating mel spectrogram", e)
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
    
    /**
     * 生成Embeddings - 使用ONNX
     */
    private fun generateEmbeddings(input: Array<Array<Array<FloatArray>>>): Array<FloatArray> {
        var session: OrtSession? = null
        var inputTensor: OnnxTensor? = null
        
        return try {
            session = ortEnv.createSession(embFile.absolutePath)
            inputTensor = OnnxTensor.createTensor(ortEnv, input)
            
            val results = session.run(mapOf("input_1" to inputTensor))
            val rawOutput = results[0].value as Array<Array<Array<FloatArray>>>
            
            // 重塑输出
            val reshapedOutput = Array(rawOutput.size) { FloatArray(rawOutput[0][0][0].size) }
            for (i in rawOutput.indices) {
                System.arraycopy(rawOutput[i][0][0], 0, reshapedOutput[i], 0, rawOutput[i][0][0].size)
            }
            
            results.close()
            reshapedOutput
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error generating embeddings", e)
            arrayOf()
        } finally {
            inputTensor?.close()
            session?.close()
        }
    }
    
    private fun predictWakeWordFromFeatures(inputArray: Array<Array<FloatArray>>): Float {
        var inputTensor: OnnxTensor? = null
        
        return try {
            inputTensor = OnnxTensor.createTensor(ortEnv, inputArray)
            val results = wakeSession!!.run(mapOf(wakeSession!!.inputNames.iterator().next() to inputTensor))
            val result = results[0].value as Array<FloatArray>
            val score = result[0][0]
            
            results.close()
            score
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error predicting wake word", e)
            0.0f
        } finally {
            inputTensor?.close()
        }
    }
    
    private fun getFeatures(nFeatureFrames: Int, startNdx: Int): Array<Array<FloatArray>> {
        val actualStartNdx = if (startNdx != -1) startNdx 
            else maxOf(0, featureBuffer.size - nFeatureFrames)
        val endNdx = if (startNdx != -1) actualStartNdx + nFeatureFrames 
            else featureBuffer.size
        val length = endNdx - actualStartNdx
        
        val result = Array(1) { Array(length) { FloatArray(if (featureBuffer.isNotEmpty()) featureBuffer[0].size else 96) } }
        for (i in 0 until length) {
            if (actualStartNdx + i < featureBuffer.size) {
                System.arraycopy(featureBuffer[actualStartNdx + i], 0, result[0][i], 0, featureBuffer[actualStartNdx + i].size)
            }
        }
        return result
    }
    
    private fun getEmbeddings(x: FloatArray, windowSize: Int, stepSize: Int): Array<FloatArray> {
        val spec = getMelSpectrogram(x)
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
            generateEmbeddings(batch)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting embeddings", e)
            arrayOf()
        }
    }
    
    private fun generateRandomFloatArray(size: Int): FloatArray {
        val arr = FloatArray(size)
        val random = Random()
        for (i in 0 until size) {
            arr[i] = (random.nextInt(2000) - 1000).toFloat()
        }
        return arr
    }
    
    private fun bufferRawData(x: FloatArray) {
        while (rawDataBuffer.size + x.size > SAMPLE_RATE * 10) {
            rawDataBuffer.poll()
        }
        for (value in x) {
            rawDataBuffer.offer(value)
        }
    }
    
    private fun streamingMelSpectrogram(nSamples: Int) {
        if (rawDataBuffer.size < 400) return
        
        val tempArray = FloatArray(nSamples + 480)
        val rawDataArray = rawDataBuffer.toTypedArray()
        for (i in maxOf(0, rawDataArray.size - nSamples - 480) until rawDataArray.size) {
            tempArray[i - maxOf(0, rawDataArray.size - nSamples - 480)] = rawDataArray[i]
        }
        
        val newMelSpectrogram = getMelSpectrogram(tempArray)
        
        val combined = Array(melspectrogramBuffer.size + newMelSpectrogram.size) { FloatArray(32) }
        System.arraycopy(melspectrogramBuffer, 0, combined, 0, melspectrogramBuffer.size)
        System.arraycopy(newMelSpectrogram, 0, combined, melspectrogramBuffer.size, newMelSpectrogram.size)
        melspectrogramBuffer = combined
        
        if (melspectrogramBuffer.size > MELSPECTROGRAM_MAX_LEN) {
            val trimmed = Array(MELSPECTROGRAM_MAX_LEN) { FloatArray(32) }
            System.arraycopy(melspectrogramBuffer, melspectrogramBuffer.size - MELSPECTROGRAM_MAX_LEN, trimmed, 0, MELSPECTROGRAM_MAX_LEN)
            melspectrogramBuffer = trimmed
        }
    }
    
    private fun streamingFeatures(audioBuffer: FloatArray) {
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
                        val newFeatures = generateEmbeddings(x)
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
                        Log.e(TAG, "❌ Error in streaming features", e)
                    }
                }
            }
            processedSamples = accumulatedSamples
            accumulatedSamples = 0
        }
        
        if (featureBuffer.size > FEATURE_BUFFER_MAX_LEN) {
            val trimmedFeatureBuffer = Array(FEATURE_BUFFER_MAX_LEN) { FloatArray(featureBuffer[0].size) }
            for (i in 0 until FEATURE_BUFFER_MAX_LEN) {
                trimmedFeatureBuffer[i] = featureBuffer[featureBuffer.size - FEATURE_BUFFER_MAX_LEN + i]
            }
            featureBuffer = trimmedFeatureBuffer
        }
    }

    override fun frameSize(): Int = N_PREPARED_SAMPLES

    override fun destroy() {
        DebugLogger.logWakeWord(TAG, "🧹 Destroying HiNudgeOnnxV8WakeDevice")
        
        scope.launch {
            try {
                wakeSession?.close()
                wakeSession = null
                
                DebugLogger.logWakeWord(TAG, "✅ V8 Resources released")
            } catch (e: Exception) {
                Log.e(TAG, "Error during destroy", e)
            }
        }
    }

    override fun isHeyDicio(): Boolean = false
}
