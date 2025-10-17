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
import javax.inject.Inject

/**
 * HiNudge韩语唤醒词设备 - ONNX版本
 * 
 * 使用OpenWakeWord架构的3阶段模型:
 * 1. melspectrogram.tflite - Mel频谱提取
 * 2. embedding.tflite - 特征embedding
 * 3. korean_wake_word.onnx - 唤醒词检测 (自定义训练)
 */
class HiNudgeOnnxWakeDevice @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : WakeDevice {

    companion object {
        private const val TAG = "HiNudgeOnnxWakeDevice"
        
        // 资源路径
        private const val ASSET_MODEL_DIR = "korean_hinudge_onnx"
        private const val MEL_FILE_NAME = "melspectrogram.tflite"
        private const val EMB_FILE_NAME = "embedding.tflite"
        private const val WAKE_FILE_NAME = "korean_wake_word.onnx"
        
        // 检测阈值
        private const val DETECTION_THRESHOLD = 0.5f
    }

    private val _state: MutableStateFlow<WakeState>
    override val state: StateFlow<WakeState>

    // 模型文件路径
    private val modelFolder = File(appContext.filesDir, "hiNudgeOnnx")
    private val melFile = File(modelFolder, MEL_FILE_NAME)
    private val embFile = File(modelFolder, EMB_FILE_NAME)
    private val wakeFile = File(modelFolder, WAKE_FILE_NAME)
    
    // 模型实例
    private var model: HiNudgeOnnxModel? = null
    
    // 音频处理
    private val audio = FloatArray(HiNudgeOnnxModel.MEL_INPUT_COUNT)

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        DebugLogger.logWakeWord(TAG, "🇰🇷 Initializing HiNudgeOnnxWakeDevice")
        DebugLogger.logWakeWord(TAG, "📁 Model folder: ${modelFolder.absolutePath}")
        DebugLogger.logWakeWord(TAG, "📄 Model files:")
        DebugLogger.logWakeWord(TAG, "  - ${melFile.name}: ${if (melFile.exists()) "EXISTS" else "MISSING"}")
        DebugLogger.logWakeWord(TAG, "  - ${embFile.name}: ${if (embFile.exists()) "EXISTS" else "MISSING"}")
        DebugLogger.logWakeWord(TAG, "  - ${wakeFile.name}: ${if (wakeFile.exists()) "EXISTS" else "MISSING"}")

        val modelsAvailable = hasModelsAvailable()
        DebugLogger.logWakeWord(TAG, "✅ Models available: $modelsAvailable")

        _state = if (modelsAvailable) {
            MutableStateFlow(WakeState.NotLoaded)
        } else {
            MutableStateFlow(WakeState.NotDownloaded)
        }
        state = _state

        DebugLogger.logStateMachine(TAG, "Initial state: ${_state.value}")

        // 如果assets中有模型但本地没有，自动复制
        scope.launch {
            val hasLocal = hasLocalModels()
            val hasAssets = hasModelsInAssets()

            DebugLogger.logModelManagement(TAG, "Local models: $hasLocal, Assets models: $hasAssets")

            if (!hasLocal && hasAssets) {
                DebugLogger.logModelManagement(TAG, "🔄 Auto-copying HiNudge models from assets on init")
                val copySuccess = copyModelsFromAssets()

                if (copySuccess) {
                    DebugLogger.logModelManagement(TAG, "✅ Successfully copied models from assets")
                    _state.value = WakeState.NotLoaded
                } else {
                    DebugLogger.logWakeWordError(TAG, "❌ Failed to copy models from assets")
                }
            }
        }
    }

    private fun hasModelsAvailable(): Boolean {
        return hasLocalModels() || hasModelsInAssets()
    }
    
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
            
            // 复制mel模型
            appContext.assets.open("$ASSET_MODEL_DIR/$MEL_FILE_NAME").use { input ->
                melFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // 复制embedding模型
            appContext.assets.open("$ASSET_MODEL_DIR/$EMB_FILE_NAME").use { input ->
                embFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // 复制wake模型
            appContext.assets.open("$ASSET_MODEL_DIR/$WAKE_FILE_NAME").use { input ->
                wakeFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            DebugLogger.logModelManagement(TAG, "✅ Copied all models:")
            DebugLogger.logModelManagement(TAG, "  - ${melFile.name}: ${melFile.length()} bytes")
            DebugLogger.logModelManagement(TAG, "  - ${embFile.name}: ${embFile.length()} bytes")
            DebugLogger.logModelManagement(TAG, "  - ${wakeFile.name}: ${wakeFile.length()} bytes")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy models from assets", e)
            false
        }
    }

    override fun download() {
        scope.launch {
            try {
                _state.value = WakeState.Downloading(Progress.UNKNOWN)

                // 从assets复制模型
                if (hasModelsInAssets()) {
                    DebugLogger.logModelManagement(TAG, "📥 Copying HiNudge models from assets...")
                    val copySuccess = copyModelsFromAssets()

                    if (copySuccess) {
                        // 立即加载模型
                        loadModel()
                        return@launch
                    }
                    
                    Log.e(TAG, "Failed to copy models from assets")
                    _state.value = WakeState.ErrorDownloading(Exception("Failed to copy models from assets"))
                } else {
                    Log.e(TAG, "No models available in assets")
                    _state.value = WakeState.ErrorDownloading(Exception("No models available"))
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error during model download", e)
                _state.value = WakeState.ErrorDownloading(e)
            }
        }
    }

    /**
     * 加载模型到内存
     */
    private fun loadModel() {
        try {
            DebugLogger.logWakeWord(TAG, "🔄 Loading HiNudge ONNX models...")
            _state.value = WakeState.Loading

            // 检查所有文件是否存在
            if (!hasLocalModels()) {
                val error = Exception("Model files do not exist")
                DebugLogger.logWakeWordError(TAG, "❌ Cannot load models", error)
                _state.value = WakeState.ErrorLoading(error)
                return
            }

            DebugLogger.logWakeWord(TAG, "📄 Model files found:")
            DebugLogger.logWakeWord(TAG, "  - ${melFile.name}: ${melFile.length()} bytes")
            DebugLogger.logWakeWord(TAG, "  - ${embFile.name}: ${embFile.length()} bytes")
            DebugLogger.logWakeWord(TAG, "  - ${wakeFile.name}: ${wakeFile.length()} bytes")

            // 创建模型实例
            model = HiNudgeOnnxModel(melFile, embFile, wakeFile)

            _state.value = WakeState.Loaded
            DebugLogger.logWakeWord(TAG, "✅ HiNudge ONNX models loaded successfully")
            
        } catch (t: Throwable) {
            DebugLogger.logWakeWordError(TAG, "❌ Failed to load models", t)
            _state.value = WakeState.ErrorLoading(t)
        }
    }

    override fun processFrame(audio16bitPcm: ShortArray): Boolean {
        val currentModel = model
        if (currentModel == null) {
            DebugLogger.logWakeWordError(TAG, "❌ Model not loaded, cannot process frame")
            return false
        }

        // 转换音频格式: Short[] -> Float[] (归一化)
        for (i in audio16bitPcm.indices) {
            audio[i] = audio16bitPcm[i] / 32768.0f
        }

        // 处理音频帧并获取分数
        val score = currentModel.processFrame(audio)
        
        // 判断是否检测到唤醒词
        val detected = score > DETECTION_THRESHOLD
        
        if (detected) {
            DebugLogger.logWakeWordDetection(TAG, score, DETECTION_THRESHOLD, detected = true)
        }
        
        return detected
    }

    override fun frameSize(): Int {
        return HiNudgeOnnxModel.MEL_INPUT_COUNT
    }

    override fun destroy() {
        DebugLogger.logWakeWord(TAG, "🧹 Destroying HiNudgeOnnxWakeDevice")
        
        scope.launch {
            try {
                model?.close()
                model = null
                
                DebugLogger.logWakeWord(TAG, "✅ Resources released")
            } catch (e: Exception) {
                Log.e(TAG, "Error during destroy", e)
            }
        }
    }

    override fun isHeyDicio(): Boolean {
        return false  // 这是韩语唤醒词，不是"Hey Dicio"
    }
}

