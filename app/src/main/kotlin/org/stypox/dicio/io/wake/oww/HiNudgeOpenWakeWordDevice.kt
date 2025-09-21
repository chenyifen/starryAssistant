package org.stypox.dicio.io.wake.oww

import android.content.Context
import android.util.Log
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
import org.stypox.dicio.util.AudioDebugSaver
import org.stypox.dicio.util.measureTimeAndLog
import java.io.File
import java.io.IOException

/**
 * 하이넛지 (Hi Nudge) 韩语唤醒词设备
 * 使用专门训练的韩语OpenWakeWord模型
 */
class HiNudgeOpenWakeWordDevice(
    @ApplicationContext private val appContext: Context,
) : WakeDevice {
    private val _state: MutableStateFlow<WakeState>
    override val state: StateFlow<WakeState>

    private val hiNudgeFolder = File(appContext.filesDir, "hiNudgeOpenWakeWord")
    private val melFile = File(hiNudgeFolder, "melspectrogram.tflite")
    private val embFile = File(hiNudgeFolder, "embedding.tflite")
    private val wakeFile = File(hiNudgeFolder, "wake.tflite")
    
    // 外部存储路径（优先级最高）
    private val externalModelDir = File("/storage/emulated/0/Dicio/models/openWakeWord")
    
    private val audio = FloatArray(OwwModel.MEL_INPUT_COUNT)
    private var model: OwwModel? = null
    private var frameCount = 0L

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
        
        // 自动复制模型文件
        scope.launch {
            if (!hasLocalModels() && hasExternalModels()) {
                DebugLogger.logModelManagement(TAG, "🔄 Auto-copying HiNudge models from external storage")
                val copySuccess = measureTimeAndLog(TAG, "Copy HiNudge models from external") {
                    copyModelsFromExternal()
                }
                
                if (copySuccess) {
                    DebugLogger.logModelManagement(TAG, "✅ Successfully copied models from external storage")
                    _state.value = WakeState.NotLoaded
                } else {
                    DebugLogger.logWakeWordError(TAG, "❌ Failed to copy models from external storage")
                }
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
        val extMel = File(externalModelDir, "melspectrogram.tflite")
        val extEmb = File(externalModelDir, "embedding.tflite")
        val extWake = File(externalModelDir, "wake.tflite")
        
        val hasAll = extMel.exists() && extEmb.exists() && extWake.exists()
        DebugLogger.logModelManagement(TAG, "📱 External models check: $hasAll")
        if (hasAll) {
            DebugLogger.logModelManagement(TAG, "📊 External model sizes: mel=${extMel.length()}, emb=${extEmb.length()}, wake=${extWake.length()}")
        }
        return hasAll
    }
    
    private fun copyModelsFromExternal(): Boolean {
        return try {
            hiNudgeFolder.mkdirs()
            
            val extMel = File(externalModelDir, "melspectrogram.tflite")
            val extEmb = File(externalModelDir, "embedding.tflite")
            val extWake = File(externalModelDir, "wake.tflite")
            
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
            
            DebugLogger.logModelManagement(TAG, "✅ HiNudge models copied successfully: $copySuccess")
            DebugLogger.logModelManagement(TAG, "📊 Local model sizes: mel=${melFile.length()}, emb=${embFile.length()}, wake=${wakeFile.length()}")
            
            copySuccess
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ Failed to copy models from external storage: ${e.message}")
            false
        }
    }

    override fun download() {
        if (hasModelsAvailable()) {
            DebugLogger.logWakeWord(TAG, "📦 Models already available, no download needed")
            _state.value = WakeState.NotLoaded
            return
        }
        
        DebugLogger.logWakeWordError(TAG, "❌ No HiNudge models available for download")
        DebugLogger.logWakeWordError(TAG, "💡 Please place Korean wake word models in: ${externalModelDir.absolutePath}")
        _state.value = WakeState.ErrorDownloading(IOException("HiNudge models not found"))
    }

    override fun processFrame(audio16bitPcm: ShortArray): Boolean {
        frameCount++
        
        if (audio16bitPcm.size != OwwModel.MEL_INPUT_COUNT) {
            DebugLogger.logWakeWordError(TAG, "❌ Invalid frame size: ${audio16bitPcm.size}, expected: ${OwwModel.MEL_INPUT_COUNT}")
            throw IllegalArgumentException(
                "HiNudgeOpenWakeWordDevice can only process audio frames of ${OwwModel.MEL_INPUT_COUNT} samples"
            )
        }

        if (model == null) {
            if (_state.value != WakeState.NotLoaded) {
                DebugLogger.logWakeWordError(TAG, "❌ Model not ready, current state: ${_state.value}")
                throw IOException("HiNudge model has not been loaded yet")
            }

            try {
                DebugLogger.logWakeWord(TAG, "🔄 Loading HiNudge OWW model...")
                _state.value = WakeState.Loading
                
                // 确保模型文件存在
                if (!hasLocalModels()) {
                    if (hasExternalModels()) {
                        DebugLogger.logWakeWord(TAG, "📱 Copying models from external storage...")
                        if (!copyModelsFromExternal()) {
                            throw IOException("Failed to copy models from external storage")
                        }
                    } else {
                        throw IOException("No HiNudge models available")
                    }
                }
                
                DebugLogger.logWakeWord(TAG, "📄 Model files: mel=${melFile.exists()}, emb=${embFile.exists()}, wake=${wakeFile.exists()}")
                
                model = measureTimeAndLog(TAG, "Load HiNudge OWW model") {
                    OwwModel(melFile, embFile, wakeFile)
                }
                
                _state.value = WakeState.Loaded
                DebugLogger.logWakeWord(TAG, "✅ HiNudge OWW model loaded successfully")
            } catch (t: Throwable) {
                DebugLogger.logWakeWordError(TAG, "❌ Failed to load HiNudge OWW model", t)
                _state.value = WakeState.ErrorLoading(t)
                return false
            }
        }

        // 转换音频数据
        for (i in 0..<OwwModel.MEL_INPUT_COUNT) {
            audio[i] = audio16bitPcm[i].toFloat() / 32768.0f
        }

        // 计算音频统计信息
        val amplitude = audio.maxOf { kotlin.math.abs(it) }
        val rms = kotlin.math.sqrt(audio.map { it * it }.average()).toFloat()
        
        val audioStats = AudioFrameStats(
            amplitude = amplitude,
            rms = rms,
            frameSize = audio.size,
            nonZeroSamples = audio.count { it != 0.0f },
            maxValue = audio.maxOrNull() ?: 0.0f,
            minValue = audio.minOrNull() ?: 0.0f
        )
        
        // 处理音频帧并获取置信度
        val confidence = measureTimeAndLog(TAG, "Process HiNudge audio frame") {
            model!!.processFrame(audio)
        }
        
        // 韩语唤醒词使用较低的阈值
        val threshold = 0.005f
        val detected = confidence > threshold
        
        // 详细的检测日志（每100帧记录一次详细信息，检测到时立即记录）
        if (detected || frameCount % 100L == 0L) {
            DebugLogger.logWakeWordDetection(TAG, confidence, threshold, detected)
            DebugLogger.logWakeWord(TAG, "🎵 HiNudge audio stats: amplitude=${String.format("%.4f", amplitude)}, rms=${String.format("%.4f", rms)}, nonZero=${audioStats.nonZeroSamples}/${audioStats.frameSize}")
            
            if (detected) {
                DebugLogger.logWakeWord(TAG, "🎯 하이넛지 DETECTED! Confidence=${String.format("%.6f", confidence)}, Threshold=${String.format("%.6f", threshold)}")
                DebugLogger.logWakeWord(TAG, "🎵 Detection audio stats: ${audioStats}")
            }
        }
        
        // 保存有音频信号的音频数据用于调试
        if (amplitude > 0.0f) {
            AudioDebugSaver.saveWakeAudio(appContext, audio16bitPcm, amplitude, confidence)
        }
        
        return detected
    }

    override fun frameSize(): Int {
        return OwwModel.MEL_INPUT_COUNT
    }

    override fun destroy() {
        model?.close()
        model = null
        scope.cancel()
    }

    override fun isHeyDicio(): Boolean = false // 这是韩语唤醒词，不是Hey Dicio

    companion object {
        val TAG = HiNudgeOpenWakeWordDevice::class.simpleName ?: "HiNudgeOpenWakeWordDevice"
    }
}

/**
 * 音频帧统计信息
 */
data class AudioFrameStats(
    val amplitude: Float,
    val rms: Float,
    val frameSize: Int,
    val nonZeroSamples: Int,
    val maxValue: Float,
    val minValue: Float
) {
    override fun toString(): String {
        return "AudioFrameStats(amp=${String.format("%.4f", amplitude)}, rms=${String.format("%.4f", rms)}, nonZero=$nonZeroSamples/$frameSize, range=[${String.format("%.4f", minValue)}, ${String.format("%.4f", maxValue)}])"
    }
}
