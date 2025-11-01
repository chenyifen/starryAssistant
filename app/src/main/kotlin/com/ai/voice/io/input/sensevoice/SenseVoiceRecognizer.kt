package com.ai.voice.io.input.sensevoice

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
import com.ai.voice.util.DebugLogger

/**
 * SenseVoice多语言ASR识别器
 * 基于SherpaOnnx OfflineRecognizer实现的离线识别
 */
class SenseVoiceRecognizer private constructor(
    private val recognizer: OfflineRecognizer,
    private val modelInfo: SenseVoiceModelManager.SenseVoiceModelPaths,
    private val koreanMode: Boolean  // 🆕 韩语模式标志
) {
    
    companion object {
        private const val TAG = "SenseVoiceRecognizer"
        
        // 模型参数
        private const val SAMPLE_RATE = 16000
        
        /**
         * 创建SenseVoice识别器实例
         * @param koreanMode 是否启用韩语模式（过滤中文字符）
         */
        suspend fun create(context: Context, koreanMode: Boolean = true): SenseVoiceRecognizer? {
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
                    // 🆕 使用自动检测模式，支持英语和韩语双语优先
                    val languageMode = "auto"  // 始终使用auto模式，通过后处理过滤
                    val config = OfflineRecognizerConfig(
                        modelConfig = OfflineModelConfig(
                            senseVoice = OfflineSenseVoiceModelConfig(
                                model = modelPaths.modelPath,
                                language = languageMode,  // 🆕 韩语模式或自动检测
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
                    Log.d(TAG, "   🌍 语言模式: 英语韩语双语优先 (auto)")
                    Log.d(TAG, "   🚫 语言过滤: 英语优先，韩语次之，过滤中文/日语等其他语言")
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
                    
                    SenseVoiceRecognizer(recognizer, modelPaths, koreanMode)
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
     * 🆕 过滤非英语韩语字符，优先保留英语和韩语，过滤其他语言
     * 英语优先，韩语次之，过滤中文等其他语言
     */
    private fun filterToEnglishKorean(text: String): String {
        if (!koreanMode) return text  // 非韩语模式，不过滤
        
        return text.filter { char ->
            when {
                // 英语字母（最高优先级）
                char in 'a'..'z' || char in 'A'..'Z' -> true
                // 韩语字符（Hangul Syllables: U+AC00 ~ U+D7A3）
                char in '\uAC00'..'\uD7A3' -> true
                // 韩语兼容字母（Hangul Jamo: U+3131 ~ U+318E）
                char in '\u3131'..'\u318E' -> true
                // 韩语兼容字母扩展（U+1100 ~ U+11FF）
                char in '\u1100'..'\u11FF' -> true
                // 数字（通用）
                char in '0'..'9' -> true
                // 标点和空格（通用）
                char.isWhitespace() || char in ".,!?;:()[]{}\"'`-–—" -> true
                // 中文字符（CJK Unified Ideographs: U+4E00 ~ U+9FFF）- 过滤掉
                char in '\u4E00'..'\u9FFF' -> false
                // 中文标点（U+3000 ~ U+303F）- 过滤掉
                char in '\u3000'..'\u303F' -> false
                // 日语平假名（U+3040 ~ U+309F）- 过滤掉
                char in '\u3040'..'\u309F' -> false
                // 日语片假名（U+30A0 ~ U+30FF）- 过滤掉
                char in '\u30A0'..'\u30FF' -> false
                // 其他Unicode字符 - 保守保留ASCII范围
                else -> char.code < 128
            }
        }.trim()
    }
    
    /**
     * 识别音频数据
     * @param audioData PCM 16kHz单声道音频数据
     * @return 识别结果文本（如果启用韩语模式，中文字符会被过滤）
     */
    suspend fun recognize(audioData: FloatArray): String {
        return withContext(Dispatchers.IO) {
            // 使用实例级别的互斥锁确保线程安全
            recognitionMutex.withLock {
                    try {
                        if (audioData.isEmpty()) {
                            return@withLock ""
                        }
                        
                        // 创建音频数据副本以确保数据完整性
                        val audioDataCopy = audioData.copyOf()
                        
                        // 创建音频流 - 添加更多安全检查
                        // 🔥 检查recognizer是否仍然有效（防止在destroy过程中被释放）
                        val currentRecognizer = recognizer
                        if (currentRecognizer == null) {
                            Log.w(TAG, "⚠️ Recognizer已被释放，跳过识别")
                            return@withLock ""
                        }
                        
                        val stream = try {
                            val createdStream = currentRecognizer.createStream()
                            createdStream
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ 创建stream失败 (多轮对话后可能资源泄漏)", e)
                            Log.e(TAG, "   音频数据长度: ${audioDataCopy.size}")
                            e.printStackTrace()
                            return@withLock ""
                        }
                        
                        try {
                            // 完全按照SherpaOnnxSimulateStreamingAsr官方示例的精确模式
                            stream.acceptWaveform(audioDataCopy, SAMPLE_RATE)
                            currentRecognizer.decode(stream)
                            val result = currentRecognizer.getResult(stream)
                            
                            // 立即释放stream资源 - 与官方示例保持一致
                            stream.release()
                            
                            val rawText = result.text.trim()
                            val filteredText = filterToEnglishKorean(rawText)  // 🆕 应用英语韩语双语过滤
                            
                            if (koreanMode && rawText != filteredText) {
                                DebugLogger.logRecognition(TAG, "SenseVoice识别 (原始): \"$rawText\"")
                                DebugLogger.logRecognition(TAG, "SenseVoice识别 (英韩过滤): \"$filteredText\"")
                            } else {
                                DebugLogger.logRecognition(TAG, "SenseVoice识别结果: \"$filteredText\"")
                            }
                            
                            filteredText
                        } catch (e: Exception) {
                            // 确保在异常情况下也释放stream
                            try {
                                stream.release()
                            } catch (releaseException: Exception) {
                                Log.e(TAG, "释放stream时发生异常", releaseException)
                            }
                            throw e // 重新抛出原始异常
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ SenseVoice识别过程异常", e)
                        Log.e(TAG, "   音频数据长度: ${audioData.size}")
                        Log.e(TAG, "   Recognizer状态: ${recognizer != null}")
                        e.printStackTrace()
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
