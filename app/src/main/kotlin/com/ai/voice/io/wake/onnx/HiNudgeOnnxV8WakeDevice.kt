package com.ai.voice.io.wake.onnx

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.ai.voice.io.wake.WakeDevice
import com.ai.voice.io.wake.WakeState
import com.ai.voice.ui.util.Progress
import com.ai.voice.util.DebugLogger
import java.io.File
import java.io.IOException
import javax.inject.Inject
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.util.*

/**
 * HiNudge韩语唤醒词设备 - ONNX V8版本 (使用V41模型)
 * 
 * 完全按照WakeWordDetector.java的v41模型调用方式实现
 * V41模型特点: 高精度，召回93%，精确95%，F1=94%，误报率仅2%
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
        private const val WAKE_FILE_NAME = "korean_wake_word_v41.onnx"
        
        // 音频参数
        private const val N_PREPARED_SAMPLES = 1280  // 80ms @ 16kHz
        private const val SAMPLE_RATE = 16000
        private const val WAKE_INPUT_FRAMES = 16
        private const val MELSPECTROGRAM_MAX_LEN = 10 * 97
        private const val FEATURE_BUFFER_MAX_LEN = 120
        private const val BATCH_SIZE = 1
        
        // 检测阈值 - V41模型推荐阈值 (召回93%，精确95%，F1=94%)
        private const val DETECTION_THRESHOLD = 0.6f
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
    private var melSession: OrtSession? = null
    private var embSession: OrtSession? = null
    private var wakeSession: OrtSession? = null
    
    // 流式处理缓冲区
    private var featureBuffer: Array<FloatArray> = Array(0) { FloatArray(96) }
    private val rawDataBuffer = ArrayDeque<Float>(SAMPLE_RATE * 10)
    private var rawDataRemainder = floatArrayOf()
    private var melspectrogramBuffer: Array<FloatArray> = Array(76) { FloatArray(32) { 1.0f } }
    private var accumulatedSamples = 0
    private var realDataFrames = 0

    private val scope = CoroutineScope(Dispatchers.IO)
    
    // 调试计数器
    private var frameCount = 0
    private var lastLogTime = System.currentTimeMillis()
    
    // 最新预测分数（用于测试）
    @Volatile
    private var lastScore: Float = 0.0f

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
        DebugLogger.logWakeWord(TAG, "🎯 Using  Model: " + WAKE_FILE_NAME)

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
            
            // 使用传统路径
            DebugLogger.logModelManagement(TAG, "📥 Copying V8 models from $ASSET_MODEL_DIR...")
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
            
            // 🔧 改为重用session（与Java测试代码一致）
            melSession = ortEnv.createSession(melFile.absolutePath)
            DebugLogger.logWakeWord(TAG, "✅ Mel spectrogram model loaded")
            
            embSession = ortEnv.createSession(embFile.absolutePath)
            DebugLogger.logWakeWord(TAG, "✅ Embedding model loaded")
            
            wakeSession = ortEnv.createSession(wakeFile.absolutePath)
            DebugLogger.logWakeWord(TAG, "✅ Wake word model loaded")
            
            // 🔧 v41模型: 使用随机音频预热feature buffer (与WakeWordDetector.java一致)
            try {
                val initAudio = generateInitAudio(SAMPLE_RATE * 4)
                featureBuffer = getEmbeddings(initAudio, 76, 8)
                DebugLogger.logWakeWord(TAG, "✅ Feature buffer pre-warmed with ${featureBuffer.size} frames")
            } catch (e: Exception) {
                DebugLogger.logWakeWordError(TAG, "⚠️ Failed to pre-warm feature buffer, using empty", e)
                featureBuffer = Array(0) { FloatArray(96) }
            }
            realDataFrames = 0
            
            val loadTime = System.currentTimeMillis() - loadStartTime

            _state.value = WakeState.Loaded
            DebugLogger.logWakeWord(TAG, "=".repeat(60))
            DebugLogger.logWakeWord(TAG, "✅ HiNudge ONNX V8 models loaded successfully in ${loadTime}ms")
            DebugLogger.logWakeWord(TAG, "=".repeat(60))
            DebugLogger.logWakeWord(TAG, "🎯 V41 Model Performance:")
            DebugLogger.logWakeWord(TAG, "  - Recall: 93.04% (不会漏检)")
            DebugLogger.logWakeWord(TAG, "  - Precision: 94.77% (误报率2.08%)")
            DebugLogger.logWakeWord(TAG, "  - F1 Score: 93.90%")
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
        
        DebugLogger.logWakeWord(TAG, "▶▶▶ processFrame #$frameCount START ◀◀◀")
        DebugLogger.logWakeWord(TAG, "[Frame#$frameCount] 输入音频大小: ${audio16bitPcm.size} samples")
        
        if (wakeSession == null) {
            DebugLogger.logWakeWordError(TAG, "[Frame#$frameCount] ❌ wakeSession is NULL")
            return false
        }

        // 转换音频格式: Short[] -> Float[]
        val audioFloat = FloatArray(audio16bitPcm.size)
        for (i in audio16bitPcm.indices) {
            audioFloat[i] = audio16bitPcm[i].toFloat()
        }
        
        // 统计输入音频
        val audioStats = getAudioStats(audioFloat)
        DebugLogger.logWakeWord(TAG, "[Frame#$frameCount] 音频统计: min=${audioStats.min}, max=${audioStats.max}, mean=${audioStats.mean}, std=${audioStats.std}, zeros=${audioStats.zeros}")
        
        // 前10帧显示详细样本值
        if (frameCount <= 10) {
            val sampleStr = audioFloat.take(20).joinToString(", ") { "%.1f".format(it) }
            DebugLogger.logWakeWord(TAG, "[Frame#$frameCount] 前20个样本: $sampleStr")
        }

        // 预测前状态
        DebugLogger.logWakeWord(TAG, "[Frame#$frameCount] 预测前状态: featureBuffer.size=${featureBuffer.size}, realDataFrames=$realDataFrames, rawDataBuffer.size=${rawDataBuffer.size}")
        
        val score = predictWakeWord(audioFloat)
        
        DebugLogger.logWakeWord(TAG, "[Frame#$frameCount] ⭐ 预测得分: $score (阈值: $DETECTION_THRESHOLD)")
        
        val detected = score >= DETECTION_THRESHOLD
        
        if (detected) {
            DebugLogger.logWakeWord(TAG, "[Frame#$frameCount] 🎉🎉🎉 检测到唤醒词! Score=$score 🎉🎉🎉")
        } else {
            val percentage = (score / DETECTION_THRESHOLD * 100).toInt()
            DebugLogger.logWakeWord(TAG, "[Frame#$frameCount] 未唤醒: Score=$score (${percentage}% of threshold)")
        }
        
        DebugLogger.logWakeWord(TAG, "▶▶▶ processFrame #$frameCount END (detected=$detected) ◀◀◀\n")
        
        return detected
    }

    private fun predictWakeWord(audioBuffer: FloatArray): Float {
        return try {
            DebugLogger.logWakeWord(TAG, "  [predictWakeWord] 输入音频大小: ${audioBuffer.size}")
            
            streamingFeatures(audioBuffer)
            
            DebugLogger.logWakeWord(TAG, "  [predictWakeWord] streamingFeatures后: realDataFrames=$realDataFrames, featureBuffer.size=${featureBuffer.size}")
            
            if (realDataFrames < WAKE_INPUT_FRAMES) {
                DebugLogger.logWakeWord(TAG, "  [predictWakeWord] ⏳ 数据不足: realDataFrames=$realDataFrames < $WAKE_INPUT_FRAMES, 返回0.0")
                return 0.0f
            }
            
            DebugLogger.logWakeWord(TAG, "  [predictWakeWord] 开始获取特征: nFrames=$WAKE_INPUT_FRAMES")
            val features = getFeatures(WAKE_INPUT_FRAMES, -1)
            DebugLogger.logWakeWord(TAG, "  [predictWakeWord] 特征形状: [${features.size}, ${features[0].size}, ${features[0][0].size}]")
            
            // 特征统计
            val featStats = getFeatureStats(features)
            DebugLogger.logWakeWord(TAG, "  [predictWakeWord] 特征统计: min=${featStats.min}, max=${featStats.max}, mean=${featStats.mean}, std=${featStats.std}")
            
            val score = predictWakeWordFromFeatures(features)
            DebugLogger.logWakeWord(TAG, "  [predictWakeWord] 最终得分: $score")
            
            score
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "  [predictWakeWord] ❌ 异常", e)
            0.0f
        }
    }
    
    /**
     * 获取音频数据统计信息
     */
    private fun getAudioStats(audio: FloatArray): AudioStats {
        if (audio.isEmpty()) {
            return AudioStats(0f, 0f, 0f, 0f, 0)
        }
        val min = audio.minOrNull() ?: 0f
        val max = audio.maxOrNull() ?: 0f
        val mean = audio.average().toFloat()
        val variance = audio.map { (it - mean) * (it - mean) }.average().toFloat()
        val std = kotlin.math.sqrt(variance)
        val zeros = audio.count { kotlin.math.abs(it) < 1e-6f }
        return AudioStats(min, max, mean, std, zeros)
    }
    
    /**
     * 获取特征数据统计信息
     */
    private fun getFeatureStats(features: Array<Array<FloatArray>>): FeatureStats {
        if (features.isEmpty() || features[0].isEmpty() || features[0][0].isEmpty()) {
            return FeatureStats(0f, 0f, 0f, 0f)
        }
        val allValues = mutableListOf<Float>()
        for (i in features.indices) {
            for (j in features[i].indices) {
                for (k in features[i][j].indices) {
                    allValues.add(features[i][j][k])
                }
            }
        }
        val min = allValues.minOrNull() ?: 0f
        val max = allValues.maxOrNull() ?: 0f
        val mean = allValues.average().toFloat()
        val variance = allValues.map { (it - mean) * (it - mean) }.average().toFloat()
        val std = kotlin.math.sqrt(variance)
        return FeatureStats(min, max, mean, std)
    }
    
    /**
     * 音频统计信息数据类
     */
    private data class AudioStats(
        val min: Float,
        val max: Float,
        val mean: Float,
        val std: Float,
        val zeros: Int
    )
    
    /**
     * 特征统计信息数据类
     */
    private data class FeatureStats(
        val min: Float,
        val max: Float,
        val mean: Float,
        val std: Float
    )
    
    /**
     * 获取Mel频谱图 - 使用ONNX (重用Session)
     */
    private fun getMelSpectrogram(inputArray: FloatArray): Array<FloatArray> {
        var session: OrtSession? = null
        var inputTensor: OnnxTensor? = null
        
        return try {
            DebugLogger.logWakeWord(TAG, "    [getMelSpectrogram] 输入大小: ${inputArray.size}")
            
            if (inputArray.isEmpty()) {
                DebugLogger.logWakeWordError(TAG, "    [getMelSpectrogram] ❌ 输入为空")
                return Array(76) { FloatArray(32) { 1.0f } }
            }
            
            val samples = inputArray.size
            if (samples <= 0) {
                DebugLogger.logWakeWordError(TAG, "    [getMelSpectrogram] ❌ 样本数无效: $samples")
                return Array(76) { FloatArray(32) { 1.0f } }
            }
            
            val audioStats = getAudioStats(inputArray)
            DebugLogger.logWakeWord(TAG, "    [getMelSpectrogram] 输入音频: samples=$samples, min=${audioStats.min}, max=${audioStats.max}, mean=${audioStats.mean}")
            
            val modelInputStream = appContext.assets.open("melspectrogram.onnx")
            val modelBytes = ByteArray(modelInputStream.available())
            modelInputStream.read(modelBytes)
            modelInputStream.close()
            session = ortEnv.createSession(modelBytes)
            DebugLogger.logWakeWord(TAG, "    [getMelSpectrogram] Mel session创建成功")
            
            val floatBuffer = FloatBuffer.wrap(inputArray)
            inputTensor = OnnxTensor.createTensor(
                ortEnv, 
                floatBuffer, 
                longArrayOf(BATCH_SIZE.toLong(), samples.toLong())
            )
            DebugLogger.logWakeWord(TAG, "    [getMelSpectrogram] 输入tensor创建: shape=[1, $samples]")
            
            val results = session.run(mapOf(session.inputNames.iterator().next() to inputTensor))
            val outputTensor = results[0].value as Array<Array<Array<FloatArray>>>
            DebugLogger.logWakeWord(TAG, "    [getMelSpectrogram] 原始输出: shape=[${outputTensor.size}, ${outputTensor[0].size}, ${outputTensor[0][0].size}, ${outputTensor[0][0][0].size}]")
            
            val squeezed = squeeze(outputTensor)
            DebugLogger.logWakeWord(TAG, "    [getMelSpectrogram] squeeze后: shape=[${squeezed.size}, ${squeezed[0].size}]")
            
            val transformed = applyMelSpecTransform(squeezed)
            val transStats = transformed.flatMap { it.toList() }
            val transMin = transStats.minOrNull() ?: 0f
            val transMax = transStats.maxOrNull() ?: 0f
            val transMean = transStats.average().toFloat()
            DebugLogger.logWakeWord(TAG, "    [getMelSpectrogram] 变换后统计: min=$transMin, max=$transMax, mean=$transMean")
            
            results.close()
            transformed
            
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "    [getMelSpectrogram] ❌ 异常", e)
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
     * 生成Embeddings - 严格按照OpenwakewordforAndroid-main实现
     */
    private fun generateEmbeddings(input: Array<Array<Array<FloatArray>>>): Array<FloatArray> {
        var session: OrtSession? = null
        var inputTensor: OnnxTensor? = null
        
        return try {
            DebugLogger.logWakeWord(TAG, "      [generateEmbeddings] 输入shape: [${input.size}, ${input[0].size}, ${input[0][0].size}, ${input[0][0][0].size}]")
            
            if (input.isEmpty()) {
                DebugLogger.logWakeWordError(TAG, "      [generateEmbeddings] ❌ 输入为空")
                return arrayOf()
            }
            
            // 统计输入
            val inputVals = input.flatMap { it.flatMap { it.flatMap { it.toList() } } }
            val inMin = inputVals.minOrNull() ?: 0f
            val inMax = inputVals.maxOrNull() ?: 0f
            val inMean = inputVals.average().toFloat()
            DebugLogger.logWakeWord(TAG, "      [generateEmbeddings] 输入统计: min=$inMin, max=$inMax, mean=$inMean, count=${inputVals.size}")
            
            val modelInputStream = appContext.assets.open("embedding_model.onnx")
            val modelBytes = ByteArray(modelInputStream.available())
            modelInputStream.read(modelBytes)
            modelInputStream.close()
            session = ortEnv.createSession(modelBytes)
            DebugLogger.logWakeWord(TAG, "      [generateEmbeddings] Emb session创建成功")
            
            inputTensor = OnnxTensor.createTensor(ortEnv, input)
            
            val results = session.run(mapOf("input_1" to inputTensor))
            val rawOutput = results[0].value as Array<Array<Array<FloatArray>>>
            DebugLogger.logWakeWord(TAG, "      [generateEmbeddings] 原始输出shape: [${rawOutput.size}, ${rawOutput[0].size}, ${rawOutput[0][0].size}, ${rawOutput[0][0][0].size}]")
            
            val reshapedOutput = Array(rawOutput.size) { FloatArray(rawOutput[0][0][0].size) }
            for (i in rawOutput.indices) {
                System.arraycopy(rawOutput[i][0][0], 0, reshapedOutput[i], 0, rawOutput[i][0][0].size)
            }
            DebugLogger.logWakeWord(TAG, "      [generateEmbeddings] reshape后: [${reshapedOutput.size}, ${reshapedOutput[0].size}]")
            
            // 统计输出
            val outVals = reshapedOutput.flatMap { it.toList() }
            val outMin = outVals.minOrNull() ?: 0f
            val outMax = outVals.maxOrNull() ?: 0f
            val outMean = outVals.average().toFloat()
            DebugLogger.logWakeWord(TAG, "      [generateEmbeddings] 输出统计: min=$outMin, max=$outMax, mean=$outMean, count=${outVals.size}")
            
            results.close()
            reshapedOutput
            
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "      [generateEmbeddings] ❌ 异常", e)
            arrayOf()
        } finally {
            inputTensor?.close()
            session?.close()
        }
    }
    
    private fun predictWakeWordFromFeatures(inputArray: Array<Array<FloatArray>>): Float {
        var inputTensor: OnnxTensor? = null
        
        return try {
            DebugLogger.logWakeWord(TAG, "    [predictWakeWordFromFeatures] 输入shape: [${inputArray.size}, ${inputArray[0].size}, ${inputArray[0][0].size}]")
            
            if (inputArray.isEmpty() || inputArray[0].isEmpty()) {
                DebugLogger.logWakeWordError(TAG, "    [predictWakeWordFromFeatures] ❌ 输入为空")
                return 0.0f
            }
            
            val inputValues = inputArray.flatMap { it.flatMap { it.toList() } }
            val inputMin = inputValues.minOrNull() ?: 0f
            val inputMax = inputValues.maxOrNull() ?: 0f
            val inputMean = inputValues.average().toFloat()
            val inputStd = kotlin.math.sqrt(inputValues.map { (it - inputMean) * (it - inputMean) }.average().toFloat())
            
            DebugLogger.logWakeWord(TAG, "    [predictWakeWordFromFeatures] 输入统计: min=$inputMin, max=$inputMax, mean=$inputMean, std=$inputStd, count=${inputValues.size}")
            
            // 显示部分输入值
            val sample = inputValues.take(10).joinToString(", ") { "%.3f".format(it) }
            DebugLogger.logWakeWord(TAG, "    [predictWakeWordFromFeatures] 前10个值: $sample")
            
            // 检查是否有NaN或Inf
            val hasNaN = inputValues.any { it.isNaN() }
            val hasInf = inputValues.any { it.isInfinite() }
            DebugLogger.logWakeWord(TAG, "    [predictWakeWordFromFeatures] hasNaN=$hasNaN, hasInf=$hasInf")
            
            // 检查全0或全相同的异常情况
            val allSame = inputValues.distinct().size == 1
            val allZero = inputValues.all { kotlin.math.abs(it) < 1e-6f }
            DebugLogger.logWakeWord(TAG, "    [predictWakeWordFromFeatures] allSame=$allSame, allZero=$allZero, distinct=${inputValues.distinct().size}")
            
            try {
                inputTensor = OnnxTensor.createTensor(ortEnv, inputArray)
                DebugLogger.logWakeWord(TAG, "    [predictWakeWordFromFeatures] Tensor创建成功")
            } catch (e: Exception) {
                DebugLogger.logWakeWordError(TAG, "    [predictWakeWordFromFeatures] ❌ Tensor创建失败", e)
                return 0.0f
            }
            
            val inputName = wakeSession!!.inputNames.iterator().next()
            DebugLogger.logWakeWord(TAG, "    [predictWakeWordFromFeatures] 运行推理, inputName=$inputName")
            
            val results = wakeSession!!.run(mapOf(inputName to inputTensor))
            
            val outputValue = results[0].value
            DebugLogger.logWakeWord(TAG, "    [predictWakeWordFromFeatures] 输出类型: ${outputValue.javaClass.simpleName}")
            
            val rawScore = when (outputValue) {
                is Array<*> -> {
                    val result = outputValue as Array<FloatArray>
                    DebugLogger.logWakeWord(TAG, "    [predictWakeWordFromFeatures] 输出Array: [${result.size}, ${result[0].size}]")
                    if (result.isNotEmpty() && result[0].isNotEmpty()) {
                        val s = result[0][0]
                        DebugLogger.logWakeWord(TAG, "    [predictWakeWordFromFeatures] 提取rawScore: $s")
                        s
                    } else {
                        DebugLogger.logWakeWord(TAG, "    [predictWakeWordFromFeatures] ⚠️ 输出Array为空")
                        0.0f
                    }
                }
                is FloatArray -> {
                    DebugLogger.logWakeWord(TAG, "    [predictWakeWordFromFeatures] 输出FloatArray: size=${outputValue.size}")
                    if (outputValue.isNotEmpty()) {
                        val s = outputValue[0]
                        DebugLogger.logWakeWord(TAG, "    [predictWakeWordFromFeatures] 提取rawScore: $s")
                        s
                    } else {
                        DebugLogger.logWakeWord(TAG, "    [predictWakeWordFromFeatures] ⚠️ 输出FloatArray为空")
                        0.0f
                    }
                }
                else -> {
                    DebugLogger.logWakeWordError(TAG, "    [predictWakeWordFromFeatures] ❌ 未知输出类型: ${outputValue.javaClass.name}")
                    0.0f
                }
            }
            
            DebugLogger.logWakeWord(TAG, "    [predictWakeWordFromFeatures] 原始输出rawScore: $rawScore")
            
            // 🔧 检查是否需要sigmoid：如果出现负数或>1的值，说明是logits
            val needsSigmoid = rawScore < 0.0f || rawScore > 1.0f
            val score = if (needsSigmoid) {
                // 应用sigmoid: σ(x) = 1 / (1 + e^(-x))
                val sigmoid = 1.0f / (1.0f + kotlin.math.exp(-rawScore))
                DebugLogger.logWakeWord(TAG, "    [predictWakeWordFromFeatures] ⚠️ 检测到logits输出，应用sigmoid: $rawScore -> $sigmoid")
                sigmoid
            } else {
                rawScore
            }
            
            DebugLogger.logWakeWord(TAG, "    [predictWakeWordFromFeatures] ⭐⭐⭐ 最终Score: $score ⭐⭐⭐")
            
            results.close()
            lastScore = score
            score
            
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "    [predictWakeWordFromFeatures] ❌ 异常", e)
            0.0f
        } finally {
            inputTensor?.close()
        }
    }
    
    private fun getFeatures(nFeatureFrames: Int, startNdx: Int): Array<Array<FloatArray>> {
        DebugLogger.logWakeWord(TAG, "    [getFeatures] nFeatureFrames=$nFeatureFrames, startNdx=$startNdx, featureBuffer.size=${featureBuffer.size}, realDataFrames=$realDataFrames")
        
        // 🔧 完全按照Java测试代码：直接取最后nFeatureFrames帧，不跳过预热
        val actualStartNdx = if (startNdx != -1) startNdx 
            else maxOf(0, featureBuffer.size - nFeatureFrames)
        val endNdx = if (startNdx != -1) actualStartNdx + nFeatureFrames 
            else featureBuffer.size
        val length = endNdx - actualStartNdx
        
        DebugLogger.logWakeWord(TAG, "    [getFeatures] actualStartNdx=$actualStartNdx, endNdx=$endNdx, length=$length (含预热帧)")
        
        val result = Array(1) { Array(length) { FloatArray(if (featureBuffer.isNotEmpty()) featureBuffer[0].size else 96) } }
        for (i in 0 until length) {
            if (actualStartNdx + i < featureBuffer.size) {
                System.arraycopy(featureBuffer[actualStartNdx + i], 0, result[0][i], 0, featureBuffer[actualStartNdx + i].size)
            }
        }
        
        DebugLogger.logWakeWord(TAG, "    [getFeatures] 返回特征: shape=[${result.size}, ${result[0].size}, ${result[0][0].size}]")
        return result
    }
    
    private fun getEmbeddings(x: FloatArray, windowSize: Int, stepSize: Int): Array<FloatArray> {
        DebugLogger.logWakeWord(TAG, "      [getEmbeddings] x.size=${x.size}, windowSize=$windowSize, stepSize=$stepSize")
        
        val spec = getMelSpectrogram(x)
        DebugLogger.logWakeWord(TAG, "      [getEmbeddings] spec shape: [${spec.size}, ${spec[0].size}]")
        
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
        DebugLogger.logWakeWord(TAG, "      [getEmbeddings] 创建${windows.size}个windows")
        
        val batch = Array(windows.size) { Array(windowSize) { Array(spec[0].size) { FloatArray(1) } } }
        for (i in windows.indices) {
            for (j in 0 until windowSize) {
                for (k in 0 until spec[0].size) {
                    batch[i][j][k][0] = windows[i][j][k]
                }
            }
        }
        DebugLogger.logWakeWord(TAG, "      [getEmbeddings] batch shape: [${batch.size}, ${batch[0].size}, ${batch[0][0].size}, ${batch[0][0][0].size}]")
        
        return try {
            val embeddings = generateEmbeddings(batch)
            DebugLogger.logWakeWord(TAG, "      [getEmbeddings] 返回embeddings: [${embeddings.size}, ${if (embeddings.isNotEmpty()) embeddings[0].size else 0}]")
            embeddings
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "      [getEmbeddings] ❌ 异常", e)
            arrayOf()
        }
    }
    
    private fun generateRandomFloatArray(size: Int): FloatArray {
        val arr = FloatArray(size)
        val random = Random()
        for (i in 0 until size) {
            // 生成归一化的音频数据范围 [-1.0, 1.0]
            arr[i] = (random.nextFloat() * 2.0f - 1.0f) * 0.1f  // 小幅度的随机噪声
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
        DebugLogger.logWakeWord(TAG, "      [streamingMelSpectrogram] nSamples=$nSamples, rawDataBuffer.size=${rawDataBuffer.size}")
        
        if (rawDataBuffer.size < 400) {
            DebugLogger.logWakeWord(TAG, "      [streamingMelSpectrogram] ⚠️ rawDataBuffer太小: ${rawDataBuffer.size} < 400, 跳过")
            return
        }
        
        val tempArray = FloatArray(nSamples + 480)
        val rawDataArray = rawDataBuffer.toTypedArray()
        val startIdx = maxOf(0, rawDataArray.size - nSamples - 480)
        DebugLogger.logWakeWord(TAG, "      [streamingMelSpectrogram] 提取音频: startIdx=$startIdx, length=${rawDataArray.size - startIdx}, tempArray.size=${tempArray.size}")
        
        for (i in startIdx until rawDataArray.size) {
            tempArray[i - startIdx] = rawDataArray[i]
        }
        
        val audioStats = getAudioStats(tempArray)
        DebugLogger.logWakeWord(TAG, "      [streamingMelSpectrogram] 提取音频统计: min=${audioStats.min}, max=${audioStats.max}, mean=${audioStats.mean}")
        
        val newMelSpectrogram = getMelSpectrogram(tempArray)
        DebugLogger.logWakeWord(TAG, "      [streamingMelSpectrogram] 新mel频谱图: shape=[${newMelSpectrogram.size}, ${newMelSpectrogram[0].size}]")
        
        val oldSize = melspectrogramBuffer.size
        val combined = Array(melspectrogramBuffer.size + newMelSpectrogram.size) { FloatArray(32) }
        System.arraycopy(melspectrogramBuffer, 0, combined, 0, melspectrogramBuffer.size)
        System.arraycopy(newMelSpectrogram, 0, combined, melspectrogramBuffer.size, newMelSpectrogram.size)
        melspectrogramBuffer = combined
        DebugLogger.logWakeWord(TAG, "      [streamingMelSpectrogram] 合并后: $oldSize + ${newMelSpectrogram.size} = ${melspectrogramBuffer.size}")
        
        if (melspectrogramBuffer.size > MELSPECTROGRAM_MAX_LEN) {
            val beforeTrim = melspectrogramBuffer.size
            val trimmed = Array(MELSPECTROGRAM_MAX_LEN) { FloatArray(32) }
            System.arraycopy(melspectrogramBuffer, melspectrogramBuffer.size - MELSPECTROGRAM_MAX_LEN, trimmed, 0, MELSPECTROGRAM_MAX_LEN)
            melspectrogramBuffer = trimmed
            DebugLogger.logWakeWord(TAG, "      [streamingMelSpectrogram] 裁剪: $beforeTrim -> ${melspectrogramBuffer.size}")
        }
    }
    
    private fun streamingFeatures(audioBuffer: FloatArray) {
        DebugLogger.logWakeWord(TAG, "    [streamingFeatures] START: audioBuffer.size=${audioBuffer.size}")
        DebugLogger.logWakeWord(TAG, "    [streamingFeatures] 初始状态: featureBuffer.size=${featureBuffer.size}, realDataFrames=$realDataFrames, rawDataRemainder.size=${rawDataRemainder.size}")
        
        val audioStats = getAudioStats(audioBuffer)
        DebugLogger.logWakeWord(TAG, "    [streamingFeatures] 音频统计: min=${audioStats.min}, max=${audioStats.max}, mean=${audioStats.mean}, std=${audioStats.std}")
        
        var processedSamples = 0
        accumulatedSamples = 0
        
        val fullBuffer = if (rawDataRemainder.isNotEmpty()) {
            DebugLogger.logWakeWord(TAG, "    [streamingFeatures] 合并remainder: ${rawDataRemainder.size} + ${audioBuffer.size}")
            val concatenated = FloatArray(rawDataRemainder.size + audioBuffer.size)
            System.arraycopy(rawDataRemainder, 0, concatenated, 0, rawDataRemainder.size)
            System.arraycopy(audioBuffer, 0, concatenated, rawDataRemainder.size, audioBuffer.size)
            rawDataRemainder = floatArrayOf()
            concatenated
        } else {
            audioBuffer
        }
        DebugLogger.logWakeWord(TAG, "    [streamingFeatures] fullBuffer.size=${fullBuffer.size}")
        
        if (accumulatedSamples + fullBuffer.size >= 1280) {
            val remainder = (accumulatedSamples + fullBuffer.size) % 1280
            DebugLogger.logWakeWord(TAG, "    [streamingFeatures] 检测到足够数据: total=${accumulatedSamples + fullBuffer.size}, remainder=$remainder")
            
            if (remainder != 0) {
                val xEvenChunks = FloatArray(fullBuffer.size - remainder)
                System.arraycopy(fullBuffer, 0, xEvenChunks, 0, fullBuffer.size - remainder)
                bufferRawData(xEvenChunks)
                accumulatedSamples += xEvenChunks.size
                rawDataRemainder = FloatArray(remainder)
                System.arraycopy(fullBuffer, fullBuffer.size - remainder, rawDataRemainder, 0, remainder)
                DebugLogger.logWakeWord(TAG, "    [streamingFeatures] 处理: evenChunks=${xEvenChunks.size}, newRemainder=$remainder")
            } else {
                bufferRawData(fullBuffer)
                accumulatedSamples += fullBuffer.size
                DebugLogger.logWakeWord(TAG, "    [streamingFeatures] 处理完整块: ${fullBuffer.size}")
            }
        } else {
            accumulatedSamples += fullBuffer.size
            bufferRawData(fullBuffer)
            DebugLogger.logWakeWord(TAG, "    [streamingFeatures] 数据不足1280: accumulated=$accumulatedSamples")
        }
        
        DebugLogger.logWakeWord(TAG, "    [streamingFeatures] rawDataBuffer.size=${rawDataBuffer.size}, accumulatedSamples=$accumulatedSamples")
        
        if (accumulatedSamples >= 1280 && accumulatedSamples % 1280 == 0) {
            DebugLogger.logWakeWord(TAG, "    [streamingFeatures] ✅ 开始处理mel和embedding: accumulatedSamples=$accumulatedSamples")
            
            streamingMelSpectrogram(accumulatedSamples)
            DebugLogger.logWakeWord(TAG, "    [streamingFeatures] mel处理后: melspectrogramBuffer.size=${melspectrogramBuffer.size}")
            
            val x = Array(1) { Array(76) { Array(32) { FloatArray(1) } } }
            
            val numChunks = accumulatedSamples / 1280
            DebugLogger.logWakeWord(TAG, "    [streamingFeatures] 处理chunks数量: $numChunks")
            
            for (i in numChunks - 1 downTo 0) {
                val ndx = if (-8 * i == 0) melspectrogramBuffer.size else -8 * i
                val start = maxOf(0, ndx - 76)
                val end = ndx
                
                DebugLogger.logWakeWord(TAG, "    [streamingFeatures] chunk[$i]: ndx=$ndx, start=$start, end=$end")
                
                for (j in start until end) {
                    for (w in 0 until 32) {
                        x[0][j - start][w][0] = melspectrogramBuffer[j][w]
                    }
                }
                
                if (x[0].size == 76) {
                    try {
                        val melValues = x[0].flatMap { it.flatMap { it.toList() } }
                        val melMin = melValues.minOrNull() ?: 0f
                        val melMax = melValues.maxOrNull() ?: 0f
                        val melMean = melValues.average().toFloat()
                        DebugLogger.logWakeWord(TAG, "    [streamingFeatures] Mel准备送入embedding: shape=[${x.size}, ${x[0].size}, ${x[0][0].size}, ${x[0][0][0].size}], min=$melMin, max=$melMax, mean=$melMean")
                        
                        val newFeatures = generateEmbeddings(x)
                        
                        if (newFeatures.isNotEmpty()) {
                            val featValues = newFeatures.flatMap { it.toList() }
                            val featMin = featValues.minOrNull() ?: 0f
                            val featMax = featValues.maxOrNull() ?: 0f
                            val featMean = featValues.average().toFloat()
                            DebugLogger.logWakeWord(TAG, "    [streamingFeatures] 新特征生成: count=${newFeatures.size}, dim=${newFeatures[0].size}, min=$featMin, max=$featMax, mean=$featMean")
                            
                            realDataFrames += newFeatures.size
                            DebugLogger.logWakeWord(TAG, "    [streamingFeatures] realDataFrames更新: $realDataFrames")
                        } else {
                            DebugLogger.logWakeWordError(TAG, "    [streamingFeatures] ⚠️ newFeatures为空")
                        }
                        
                        if (featureBuffer.isEmpty()) {
                            featureBuffer = newFeatures
                            DebugLogger.logWakeWord(TAG, "    [streamingFeatures] 初始化featureBuffer: size=${newFeatures.size}")
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
                            DebugLogger.logWakeWord(TAG, "    [streamingFeatures] featureBuffer合并: ${featureBuffer.size - newFeatures.size} + ${newFeatures.size} = ${featureBuffer.size}")
                        }
                    } catch (e: Exception) {
                        DebugLogger.logWakeWordError(TAG, "    [streamingFeatures] ❌ 处理异常", e)
                    }
                }
            }
            processedSamples = accumulatedSamples
            accumulatedSamples = 0
            DebugLogger.logWakeWord(TAG, "    [streamingFeatures] 处理完成: processedSamples=$processedSamples")
        }
        
        if (featureBuffer.size > FEATURE_BUFFER_MAX_LEN) {
            val oldSize = featureBuffer.size
            val trimmedFeatureBuffer = Array(FEATURE_BUFFER_MAX_LEN) { FloatArray(featureBuffer[0].size) }
            for (i in 0 until FEATURE_BUFFER_MAX_LEN) {
                trimmedFeatureBuffer[i] = featureBuffer[featureBuffer.size - FEATURE_BUFFER_MAX_LEN + i]
            }
            featureBuffer = trimmedFeatureBuffer
            DebugLogger.logWakeWord(TAG, "    [streamingFeatures] featureBuffer裁剪: $oldSize -> ${featureBuffer.size}")
        }
        
        DebugLogger.logWakeWord(TAG, "    [streamingFeatures] END: featureBuffer.size=${featureBuffer.size}, realDataFrames=$realDataFrames")
    }

    /**
     * 生成随机音频数据用于feature buffer预热
     * 与WakeWordDetector.java保持一致
     */
    private fun generateInitAudio(size: Int): FloatArray {
        val arr = FloatArray(size)
        val random = java.util.Random(42)  // 固定种子，确保可重复
        for (i in 0 until size) {
            arr[i] = (random.nextInt(2000) - 1000).toFloat()  // [-1000, 1000]
        }
        return arr
    }

    override fun frameSize(): Int = N_PREPARED_SAMPLES

    override fun reset() {
        DebugLogger.logWakeWord(TAG, "🔄 Resetting HiNudgeOnnxV8WakeDevice state (frame #$frameCount)")
        
        // 清空缓冲区
        rawDataBuffer.clear()
        rawDataRemainder = floatArrayOf()
        melspectrogramBuffer = Array(76) { FloatArray(32) { 1.0f } }
        accumulatedSamples = 0
        
        // 🔧 v41模型: 使用随机音频预热feature buffer
        try {
            val initAudio = generateInitAudio(SAMPLE_RATE * 4)
            featureBuffer = getEmbeddings(initAudio, 76, 8)
            DebugLogger.logWakeWord(TAG, "✅ Feature buffer pre-warmed with ${featureBuffer.size} frames")
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "⚠️ Failed to pre-warm feature buffer in reset", e)
            featureBuffer = Array(0) { FloatArray(96) }
        }
        
        realDataFrames = 0
        DebugLogger.logWakeWord(TAG, "✅ HiNudgeOnnxV8WakeDevice state reset complete")
    }
    
    /**
     * 获取最新预测分数（用于测试）
     */
    fun getLastScore(): Float = lastScore

    override fun destroy() {
        DebugLogger.logWakeWord(TAG, "🧹 Destroying HiNudgeOnnxV8WakeDevice")
        
        scope.launch {
            try {
                // 🔧 关闭所有ONNX Session
                melSession?.close()
                melSession = null
                
                embSession?.close()
                embSession = null
                
                wakeSession?.close()
                wakeSession = null
                
                DebugLogger.logWakeWord(TAG, "✅ V8 Resources released (mel, emb, wake)")
            } catch (e: Exception) {
                Log.e(TAG, "Error during destroy", e)
            }
        }
    }

    override fun isHeyDicio(): Boolean = false
}
