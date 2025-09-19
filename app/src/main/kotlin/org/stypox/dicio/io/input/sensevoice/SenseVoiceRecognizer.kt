package org.stypox.dicio.io.input.sensevoice

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.stypox.dicio.util.DebugLogger

/**
 * SenseVoice多语言ASR识别器
 * 基于SherpaOnnx OfflineRecognizer实现的离线识别
 */
class SenseVoiceRecognizer private constructor(
    private val recognizer: OfflineRecognizer,
    private val modelInfo: SenseVoiceModelManager.SenseVoiceModelPaths
) {
    
    companion object {
        private const val TAG = "SenseVoiceRecognizer"
        
        // 模型参数
        private const val SAMPLE_RATE = 16000
        
        /**
         * 创建SenseVoice识别器实例
         */
        suspend fun create(context: Context): SenseVoiceRecognizer? {
            return withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "🔧 SenseVoiceRecognizer.create() 开始执行...")
                    
                    val modelPaths = SenseVoiceModelManager.getModelPaths(context)
                    if (modelPaths == null) {
                        Log.e(TAG, "❌ 无法获取SenseVoice模型路径")
                        return@withContext null
                    }
                    
                    Log.d(TAG, "✅ 模型路径获取成功:")
                    Log.d(TAG, "   📂 模型: ${modelPaths.modelPath}")
                    Log.d(TAG, "   📄 Tokens: ${modelPaths.tokensPath}")
                    Log.d(TAG, "   🔧 量化: ${modelPaths.isQuantized}")
                    Log.d(TAG, "   📱 来源: ${if (modelPaths.isFromAssets) "Assets" else "文件系统"}")
                    
                    // 验证文件存在性
                    if (!modelPaths.isFromAssets) {
                        val modelFile = java.io.File(modelPaths.modelPath)
                        val tokensFile = java.io.File(modelPaths.tokensPath)
                        Log.d(TAG, "📋 文件验证:")
                        Log.d(TAG, "   - 模型文件存在: ${modelFile.exists()} (${if(modelFile.exists()) "${modelFile.length()/1024/1024}MB" else "N/A"})")
                        Log.d(TAG, "   - Tokens文件存在: ${tokensFile.exists()} (${if(tokensFile.exists()) "${tokensFile.length()/1024}KB" else "N/A"})")
                    }
                    
                    // 按照HandsFree的正确方式创建SenseVoice配置
                    Log.d(TAG, "🔧 创建SenseVoice配置...")
                    val config = OfflineRecognizerConfig(
                        modelConfig = OfflineModelConfig(
                            senseVoice = OfflineSenseVoiceModelConfig(
                                model = modelPaths.modelPath
                            ),
                            tokens = modelPaths.tokensPath,
                            numThreads = 2,
                            provider = "cpu",
                            debug = false
                        ),
                        decodingMethod = "greedy_search",
                        maxActivePaths = 4
                    )
                    Log.d(TAG, "   ✅ SenseVoice配置: model=${modelPaths.modelPath}")
                    Log.d(TAG, "   ✅ 配置: threads=2, provider=cpu, decodingMethod=greedy_search")
                    
                    // 根据模型来源创建识别器
                    Log.d(TAG, "🚀 创建OfflineRecognizer实例...")
                    val recognizer = if (modelPaths.isFromAssets) {
                        Log.d(TAG, "   📂 使用AssetManager加载模型")
                        OfflineRecognizer(context.assets, config)
                    } else {
                        Log.d(TAG, "   💾 从文件系统加载模型")
                        OfflineRecognizer(null, config)
                    }
                    
                    Log.d(TAG, "✅ OfflineRecognizer创建成功！")
                    Log.d(TAG, "🎉 SenseVoice识别器初始化完成")
                    
                    SenseVoiceRecognizer(recognizer, modelPaths)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 创建SenseVoice识别器失败", e)
                    Log.e(TAG, "💡 错误详情: ${e.message}")
                    Log.e(TAG, "🔍 堆栈跟踪: ${e.stackTraceToString()}")
                    null
                }
            }
        }
    }
    
    // 添加同步锁确保线程安全
    private val recognitionMutex = Mutex()
    
    /**
     * 识别音频数据
     * @param audioData PCM 16kHz单声道音频数据
     * @return 识别结果文本
     */
    suspend fun recognize(audioData: FloatArray): String {
        return withContext(Dispatchers.IO) {
            recognitionMutex.withLock {
                try {
                    if (audioData.isEmpty()) {
                        return@withLock ""
                    }
                    
                    val durationSeconds = String.format("%.2f", audioData.size.toFloat() / SAMPLE_RATE)
                    DebugLogger.logAudio(TAG, "开始SenseVoice识别，音频长度: ${audioData.size} (${durationSeconds}秒)")
                    
                    // 创建音频流
                    val stream = try {
                        recognizer.createStream()
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ 创建stream失败", e)
                        return@withLock ""
                    }
                    
                    if (stream == null) {
                        Log.e(TAG, "❌ Stream创建返回null")
                        return@withLock ""
                    }
                    
                    try {
                        // 输入音频数据
                        DebugLogger.logAudio(TAG, "向stream输入音频数据...")
                        stream.acceptWaveform(audioData, SAMPLE_RATE)
                        DebugLogger.logAudio(TAG, "音频数据输入完成")
                        
                        // 运行识别 (注意：无需调用inputFinished，参考HandsFree实现)
                        DebugLogger.logAudio(TAG, "开始解码识别...")
                        try {
                            recognizer.decode(stream)
                            DebugLogger.logAudio(TAG, "解码完成，准备获取结果...")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ 解码过程失败", e)
                            return@withLock ""
                        }
                        
                        // 获取结果 - 按照SherpaOnnx demo的标准方式
                        DebugLogger.logAudio(TAG, "获取识别结果...")
                        val result = recognizer.getResult(stream)
                        val resultText = result.text.trim()
                        
                        DebugLogger.logRecognition(TAG, "SenseVoice识别结果: \"$resultText\"")
                        
                        // 返回识别文本
                        resultText
                    } finally {
                        // 安全释放流资源
                        try {
                            stream.release()
                            DebugLogger.logAudio(TAG, "Stream资源已释放")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ 释放stream资源失败", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "SenseVoice识别过程异常", e)
                    ""
                }
            }
        }
    }
    
    /**
     * 获取识别器信息
     */
    fun getInfo(): String {
        return "SenseVoice (${if (modelInfo.isQuantized) "量化" else "普通"})"
    }
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            recognizer.release()
            DebugLogger.logModelManagement(TAG, "SenseVoice识别器资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放SenseVoice识别器资源失败", e)
        }
    }
}
