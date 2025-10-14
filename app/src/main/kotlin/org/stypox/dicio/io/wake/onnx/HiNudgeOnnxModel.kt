package org.stypox.dicio.io.wake.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.FloatBuffer

/**
 * HiNudge ONNX模型 - 使用TFLite的mel+embedding预处理，ONNX的wake检测
 * 
 * 架构:
 * 1. Mel Spectrogram (TFLite) - 将音频转换为mel频谱图
 * 2. Embedding (TFLite) - 将mel频谱图转换为特征向量
 * 3. Wake Word (ONNX) - 从特征向量检测唤醒词
 */
class HiNudgeOnnxModel(
    melSpectrogramPath: File,
    embeddingPath: File,
    wakeWordOnnxPath: File
) : AutoCloseable {
    
    companion object {
        private const val TAG = "HiNudgeOnnxModel"
        
        // Mel模型参数 (与OwwModel相同)
        const val MEL_INPUT_COUNT = 1280  // 80ms @ 16kHz
        const val MEL_OUTPUT_COUNT = 5  // 计算出来的
        const val MEL_FEATURE_SIZE = 32
        
        // Embedding模型参数
        const val EMB_INPUT_COUNT = 76
        const val EMB_OUTPUT_COUNT = 1
        const val EMB_FEATURE_SIZE = 96
        
        // Wake Word模型参数
        const val WAKE_INPUT_COUNT = 16
    }
    
    // TFLite解释器 (用于mel和embedding)
    private val melInterpreter: Interpreter
    private val embInterpreter: Interpreter
    
    // ONNX Runtime (用于wake word检测)
    private val ortEnv: OrtEnvironment
    private val ortSession: OrtSession
    
    // 累积缓冲区
    private var accumulatedMelOutputs: Array<Array<FloatArray>> = Array(EMB_INPUT_COUNT) { arrayOf() }
    private var accumulatedEmbOutputs: Array<FloatArray> = Array(WAKE_INPUT_COUNT) { floatArrayOf() }
    
    private var isClosed: Boolean = false
    
    init {
        Log.d(TAG, "Initializing HiNudgeOnnxModel")
        Log.d(TAG, "Mel model: ${melSpectrogramPath.name}")
        Log.d(TAG, "Emb model: ${embeddingPath.name}")
        Log.d(TAG, "Wake model: ${wakeWordOnnxPath.name}")
        
        // 加载TFLite模型
        melInterpreter = loadTFLiteModel(melSpectrogramPath, intArrayOf(1, MEL_INPUT_COUNT))
        
        try {
            embInterpreter = loadTFLiteModel(embeddingPath)
        } catch (t: Throwable) {
            melInterpreter.close()
            throw t
        }
        
        // 加载ONNX模型
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setIntraOpNumThreads(2)
            sessionOptions.setInterOpNumThreads(1)
            
            ortSession = ortEnv.createSession(wakeWordOnnxPath.absolutePath, sessionOptions)
            
            Log.d(TAG, "ONNX model loaded successfully")
            Log.d(TAG, "Input names: ${ortSession.inputNames}")
            Log.d(TAG, "Output names: ${ortSession.outputNames}")
            
        } catch (t: Throwable) {
            melInterpreter.close()
            embInterpreter.close()
            throw t
        }
    }
    
    fun processFrame(audio: FloatArray): Float {
        synchronized(this) {
            if (isClosed) {
                Log.w(TAG, "Model is closed, returning 0.0")
                return 0.0f
            }
            
            if (audio.size != MEL_INPUT_COUNT) {
                throw IllegalArgumentException(
                    "HiNudgeOnnxModel can only process audio frames of $MEL_INPUT_COUNT samples, got ${audio.size}"
                )
            }
            
            // 1. Mel Spectrogram提取
            val melOutput = Array(MEL_OUTPUT_COUNT) { FloatArray(MEL_FEATURE_SIZE) }
            melInterpreter.run(arrayOf(audio), arrayOf(arrayOf(melOutput)))
            
            // 累积mel输出
            for (i in 0 until EMB_INPUT_COUNT) {
                accumulatedMelOutputs[i] = if (i < EMB_INPUT_COUNT - MEL_OUTPUT_COUNT) {
                    accumulatedMelOutputs[i + MEL_OUTPUT_COUNT]
                } else {
                    melOutput[i - EMB_INPUT_COUNT + MEL_OUTPUT_COUNT]
                        .map { floatArrayOf((it / 10.0f) + 2.0f) }
                        .toTypedArray()
                }
            }
            
            if (accumulatedMelOutputs[0].isEmpty()) {
                return 0.0f // 还未完全初始化
            }
            
            // 2. Embedding提取
            val embOutput = Array(EMB_OUTPUT_COUNT) { FloatArray(EMB_FEATURE_SIZE) }
            embInterpreter.run(arrayOf(accumulatedMelOutputs), arrayOf(arrayOf(embOutput)))
            
            // 累积embedding输出
            for (i in 0 until WAKE_INPUT_COUNT) {
                accumulatedEmbOutputs[i] = if (i < WAKE_INPUT_COUNT - EMB_OUTPUT_COUNT) {
                    accumulatedEmbOutputs[i + EMB_OUTPUT_COUNT]
                } else {
                    embOutput[i - WAKE_INPUT_COUNT + EMB_OUTPUT_COUNT]
                }
            }
            
            if (accumulatedEmbOutputs[0].isEmpty()) {
                return 0.0f // 还未完全初始化
            }
            
            // 3. Wake Word检测 (使用ONNX)
            return runOnnxInference()
        }
    }
    
    /**
     * 运行ONNX模型推理
     */
    private fun runOnnxInference(): Float {
        try {
            // 准备输入: [1, 16, 96] 的特征矩阵
            val inputShape = longArrayOf(1, WAKE_INPUT_COUNT.toLong(), EMB_FEATURE_SIZE.toLong())
            
            // 将accumulatedEmbOutputs转换为FloatBuffer
            val flattenedData = FloatArray(WAKE_INPUT_COUNT * EMB_FEATURE_SIZE)
            for (i in 0 until WAKE_INPUT_COUNT) {
                for (j in 0 until EMB_FEATURE_SIZE) {
                    flattenedData[i * EMB_FEATURE_SIZE + j] = accumulatedEmbOutputs[i][j]
                }
            }
            
            val inputBuffer = FloatBuffer.wrap(flattenedData)
            val inputTensor = OnnxTensor.createTensor(ortEnv, inputBuffer, inputShape)
            
            // 运行推理
            val inputName = ortSession.inputNames.iterator().next()
            val inputs = mapOf(inputName to inputTensor)
            val results = ortSession.run(inputs)
            
            // 获取输出
            val output = results[0].value as FloatArray
            val score = output[0]
            
            // 清理
            inputTensor.close()
            results.close()
            
            return score
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during ONNX inference", e)
            return 0.0f
        }
    }
    
    override fun close() {
        synchronized(this) {
            if (!isClosed) {
                isClosed = true
                Log.d(TAG, "Closing HiNudgeOnnxModel")
                
                melInterpreter.close()
                embInterpreter.close()
                ortSession.close()
                // 注意: 不要关闭ortEnv，它是全局单例
                
                Log.d(TAG, "Model closed successfully")
            }
        }
    }
    
    private fun loadTFLiteModel(modelPath: File, inputDims: IntArray? = null): Interpreter {
        val interpreter = Interpreter(modelPath)
        
        if (inputDims != null) {
            interpreter.resizeInput(0, inputDims)
        }
        
        interpreter.allocateTensors()
        return interpreter
    }
}

