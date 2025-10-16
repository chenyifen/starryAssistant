package org.stypox.dicio.io.input.sensevoice

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
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
                                model = modelPaths.modelPath,
                                language = "auto",  // 使用自动语言检测模式
                                useInverseTextNormalization = true // 逆文本规范化 - 关键修复！
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
                    Log.d(TAG, "   🌍 语言支持: SenseVoice自动多语言检测")
                    Log.d(TAG, "   📝 逆文本规范化: 启用")
                    
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
                    Log.d(TAG, "🔗 实例ID: ${recognizer.hashCode()}")
                    
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
            // 使用实例级别的互斥锁确保线程安全
            recognitionMutex.withLock {
                    try {
                        if (audioData.isEmpty()) {
                            return@withLock ""
                        }
                        
                        // 验证recognizer和配置有效性
                        DebugLogger.logAudio(TAG, "✅ Recognizer ID: ${recognizer.hashCode()}")
                        DebugLogger.logAudio(TAG, "🔧 模型路径: ${modelInfo.modelPath}")
                        DebugLogger.logAudio(TAG, "📄 Tokens路径: ${modelInfo.tokensPath}")
                        DebugLogger.logAudio(TAG, "🗂️ 来源: ${if (modelInfo.isFromAssets) "Assets" else "文件系统"}")
                        
                        // 验证音频数据
                        val durationSeconds = String.format("%.2f", audioData.size.toFloat() / SAMPLE_RATE)
                        val audioMin = audioData.minOrNull() ?: 0f
                        val audioMax = audioData.maxOrNull() ?: 0f
                        DebugLogger.logAudio(TAG, "开始SenseVoice识别，音频长度: ${audioData.size} (${durationSeconds}秒)")
                        DebugLogger.logAudio(TAG, "🎵 音频范围: [$audioMin, $audioMax]")
                        
                        // 创建音频数据副本以确保数据完整性
                        val audioDataCopy = audioData.copyOf()
                        DebugLogger.logAudio(TAG, "📋 音频数据已复制: ${audioDataCopy.size} samples")
                        
                        // 创建音频流 - 添加更多安全检查
                        DebugLogger.logAudio(TAG, "准备创建stream...")
                        val stream = try {
                            val createdStream = recognizer.createStream()
                            DebugLogger.logAudio(TAG, "✅ Stream创建成功: ${createdStream.hashCode()}")
                            createdStream
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ 创建stream失败", e)
                            return@withLock ""
                        }
                        
                        try {
                            // 完全按照SherpaOnnxSimulateStreamingAsr官方示例的精确模式
                            DebugLogger.logAudio(TAG, "向stream输入音频数据...")
                            stream.acceptWaveform(audioDataCopy, SAMPLE_RATE)
                            DebugLogger.logAudio(TAG, "音频数据输入完成")
                            
                            DebugLogger.logAudio(TAG, "开始解码识别...")
                            recognizer.decode(stream)
                            DebugLogger.logAudio(TAG, "解码完成，准备获取结果...")
                            
                            DebugLogger.logAudio(TAG, "获取识别结果...")
                            val result = recognizer.getResult(stream)
                            
                            // 立即释放stream资源 - 与官方示例保持一致
                            stream.release()
                            DebugLogger.logAudio(TAG, "stream资源已释放")
                            
                            val resultText = result.text.trim()
                            DebugLogger.logRecognition(TAG, "SenseVoice识别结果: \"$resultText\"")
                            
                            resultText
                        } catch (e: Exception) {
                            // 确保在异常情况下也释放stream
                            try {
                                stream.release()
                                DebugLogger.logAudio(TAG, "异常情况下释放stream资源")
                            } catch (releaseException: Exception) {
                                Log.e(TAG, "释放stream时发生异常", releaseException)
                            }
                            throw e // 重新抛出原始异常
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
            // 使用实例级别的互斥锁确保不会在释放时还有正在进行的识别操作
            runBlocking {
                recognitionMutex.withLock {
                    recognizer.release()
                    DebugLogger.logModelManagement(TAG, "SenseVoice识别器资源已释放")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "释放SenseVoice识别器资源失败", e)
        }
    }
}
