package com.ai.voice.util

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.getOfflineModelConfig
import com.k2fsa.sherpa.onnx.getVadModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * ASR 处理工具类（单例）
 * 基于官方 demo Home.kt 的实现，提取 ASR 核心逻辑
 * 提供 start/stop 方法进行语音识别
 */
object AsrHandler {
    private const val TAG = "AsrHandler"
    
    private const val SAMPLE_RATE_IN_HZ = 16000
    // 限制 channel 容量，避免内存溢出
    private var samplesChannel = Channel<FloatArray>(capacity = 100)
    
    private var audioRecord: AudioRecord? = null
    private var isStarted = false
    
    // ASR 结果列表，参考原有 Home.kt 的实现方式
    private val resultList: MutableList<String> = mutableListOf()
    
    // 静音超时配置
    private const val SILENCE_TIMEOUT_MS = 4000L // 静音超时
    private var lastSpeechDetectedTime = System.currentTimeMillis()
    private var contextForStop: Context? = null
    private var silenceTimeoutCallback: (() -> Unit)? = null
    
    // Final识别结果回调（用于触发技能识别）
    private var finalResultCallback: ((String) -> Unit)? = null
    
    /**
     * 设置静音超时回调（当检测到连续静音10秒时调用，用于更新 UI 状态）
     * 注意：AsrHandler 会在内部自动调用 stop()，回调只用于 UI 状态更新
     */
    fun setSilenceTimeoutCallback(callback: (() -> Unit)?) {
        silenceTimeoutCallback = callback
    }
    
    /**
     * 设置Final识别结果回调（当Final识别完成时调用，用于触发技能识别）
     * @param callback 回调函数，参数为Final识别结果的文本
     */
    fun setFinalResultCallback(callback: ((String) -> Unit)?) {
        finalResultCallback = callback
    }
    
    /**
     * 内部单例对象，完全按照 home.kt 中的 SimulateStreamingAsr 实现
     * 用于管理 recognizer 和 VAD
     */
    private object SimulateStreamingAsr {
        private var _recognizer: OfflineRecognizer? = null
        val recognizer: OfflineRecognizer?
            get() {
                return _recognizer
            }
        
        fun isRecognizerInitialized(): Boolean {
            return _recognizer != null
        }

        private var _vad: Vad? = null
        val vad: Vad?
            get() {
                return _vad
            }
        
        fun isVadInitialized(): Boolean {
            return _vad != null
        }

        fun initOfflineRecognizer(assetManager: AssetManager? = null, application: Application) {
            synchronized(this) {
                if (_recognizer != null) {
                    return
                }
                Log.i(TAG, "Initializing sherpa-onnx offline recognizer")
                // Please change getOfflineModelConfig() to add new models
                // See https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html
                // for a list of available models
                val asrModelType = 15
                val asrRuleFsts: String? = null
                Log.i(TAG, "Select model type $asrModelType for ASR")

                val useHr = false
                val hr = com.k2fsa.sherpa.onnx.HomophoneReplacerConfig(
                    // Used only when useHr is true
                    // Please download the following 2 files from
                    // https://github.com/k2-fsa/sherpa-onnx/releases/tag/hr-files
                    //
                    // lexicon.txt can be shared by different apps
                    //
                    // replace.fst is specific for an app
                    lexicon = "lexicon.txt",
                    ruleFsts = "replace.fst",
                )

                val modelConfig = getOfflineModelConfig(type = asrModelType)
                if (modelConfig == null) {
                    Log.e(TAG, "❌ getOfflineModelConfig(type=$asrModelType) 返回 null")
                    throw IllegalStateException("getOfflineModelConfig 返回 null，请检查模型文件是否存在")
                }
                
                val config = OfflineRecognizerConfig(
                    modelConfig = modelConfig,
                )

                if (config.modelConfig.numThreads == 1) {
                    config.modelConfig.numThreads = 2
                }

                if (asrRuleFsts != null) {
                    config.ruleFsts = asrRuleFsts
                }

                if (useHr) {
                    config.hr = hr
                }

                _recognizer = OfflineRecognizer(
                    assetManager = assetManager,
                    config = config,
                )

                Log.i(TAG, "sherpa-onnx offline recognizer initialized")
            }
        }

        fun initVad(assetManager: AssetManager? = null) {
            synchronized(this) {
                if (_vad != null) {
                    return
                }
                val type = 0
                Log.i(TAG, "Select VAD model type $type")
                val config = getVadModelConfig(type)
                if (config == null) {
                    Log.e(TAG, "❌ getVadModelConfig(type=$type) 返回 null")
                    throw IllegalStateException("getVadModelConfig 返回 null，请检查 VAD 模型文件是否存在")
                }

                _vad = Vad(
                    assetManager = assetManager,
                    config = config,
                )
                Log.i(TAG, "sherpa-onnx vad initialized")
            }
        }
    }
    
    /**
     * 获取 ASR 结果列表（供 UI 类获取并显示）
     */
    fun getResultList(): List<String> {
        return resultList.toList()
    }
    
    /**
     * 清空结果列表
     */
    fun clearResults() {
        resultList.clear()
    }
    
    /**
     * 初始化 recognizer 和 VAD（在应用启动时调用）
     * @param context 上下文，用于获取 assets 和 Application
     * @return 是否成功初始化
     */
    fun initialize(context: Context): Boolean {
        Log.i(TAG, "🚀 开始初始化 AsrHandler (应用启动时)...")
        
        return try {
            val application = context.applicationContext as? Application
            if (application == null) {
                Log.e(TAG, "❌ 无法获取 Application 实例")
                return false
            }
            
            Log.i(TAG, "🔧 开始初始化 recognizer...")
            SimulateStreamingAsr.initOfflineRecognizer(context.assets, application)
            Log.i(TAG, "✅ recognizer 初始化成功")
            
            Log.i(TAG, "🔧 开始初始化 VAD...")
            SimulateStreamingAsr.initVad(context.assets)
            Log.i(TAG, "✅ VAD 初始化成功")
            
            Log.i(TAG, "✅ AsrHandler 初始化完成")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ AsrHandler 初始化失败: ${e.message}", e)
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 开始 ASR 识别
     * @param context 上下文，用于权限检查
     * @return 是否成功启动
     */
    fun start(context: Context): Boolean {
        Log.i(TAG, "🚀 开始启动 AsrHandler...")
        
        if (isStarted) {
            Log.w(TAG, "⚠️ ASR 已在运行中")
            return false
        }
        
        // 检查权限
        if (ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "❌ 没有录音权限")
            return false
        }
        Log.d(TAG, "✅ 录音权限检查通过")
        
        // 检查 recognizer 和 vad 是否已初始化
        if (!SimulateStreamingAsr.isRecognizerInitialized() || !SimulateStreamingAsr.isVadInitialized()) {
            Log.e(TAG, "❌ recognizer 或 VAD 未初始化，请先调用 AsrHandler.initialize()")
            Log.e(TAG, "💡 建议在 Application.onCreate() 中调用 AsrHandler.initialize(this)")
            return false
        }
        Log.d(TAG, "✅ recognizer 和 VAD 已初始化，可以直接使用")
        
        // 保存 context 用于静音超时后调用 stop
        contextForStop = context
        
        isStarted = true
        Log.i(TAG, "✅ 启动 doAsr...")
        doAsr(context)
        Log.i(TAG, "✅ AsrHandler 启动成功")
        return true
    }
    
    /**
     * 停止 ASR 识别
     * @param context 上下文，用于执行停止逻辑
     */
    fun stop(context: Context) {
        isStarted = false
        contextForStop = null
        doAsr(context)
    }
    
    /**
     * 检查是否正在识别
     */
    fun isStarted(): Boolean {
        return isStarted
    }
    
    /**
     * ASR 处理逻辑，完全按照原有 Home.kt 的 onRecordingButtonClick 实现
     */
    fun doAsr(context: Context) {
        if (isStarted) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.i(TAG, "Recording is not allowed")
            } else {
                // recording is allowed
                val audioSource = MediaRecorder.AudioSource.MIC
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val numBytes =
                    AudioRecord.getMinBufferSize(SAMPLE_RATE_IN_HZ, channelConfig, audioFormat)
                audioRecord = AudioRecord(
                    audioSource,
                    SAMPLE_RATE_IN_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    numBytes * 2 // a sample has two bytes as we are using 16-bit PCM
                )

                // 使用内部 SimulateStreamingAsr 的 recognizer 和 vad
                val recognizer = SimulateStreamingAsr.recognizer
                val vad = SimulateStreamingAsr.vad
                
                if (recognizer == null || vad == null) {
                    Log.e(TAG, "❌ recognizer 或 VAD 未初始化")
                    return
                }

                vad.reset()

                CoroutineScope(Dispatchers.IO).launch {
                    Log.i(TAG, "processing samples")
                    val interval = 0.1 // i.e., 100 ms
                    val bufferSize = (interval * SAMPLE_RATE_IN_HZ).toInt() // in samples
                    val buffer = ShortArray(bufferSize)

                    audioRecord?.let { it ->
                        it.startRecording()

                        while (isStarted) {
                            val ret = audioRecord?.read(buffer, 0, buffer.size)
                            ret?.let { n ->
                                val samples = FloatArray(n) { buffer[it] / 32768.0f }
                                samplesChannel.send(samples)
                            }
                        }
                        val samples = FloatArray(0)
                        samplesChannel.send(samples)
                    }
                }

                // 重置静音检测时间
                lastSpeechDetectedTime = System.currentTimeMillis()
                
                CoroutineScope(Dispatchers.Default).launch {
                    var buffer = arrayListOf<Float>()
                    var offset = 0
                    val windowSize = 512
                    var isSpeechStarted = false
                    var startTime = System.currentTimeMillis()
                    var lastText = ""
                    var added = false
                    // 最大 buffer 大小：限制为 10 秒的音频数据（防止内存溢出）
                    val maxBufferSize = SAMPLE_RATE_IN_HZ * 10
                    // 当 buffer 超过这个大小且 offset 已经处理了很多数据时，清理已处理的数据
                    val cleanupThreshold = maxBufferSize / 2
                    // 超时时间：如果超过 5 秒没有检测到语音结束，强制清空 buffer
                    val timeoutMs = 5000L
                    var lastVadActivityTime = System.currentTimeMillis()

                    while (isStarted) {
                        for (s in samplesChannel) {
                            if (s.isEmpty()) {
                                break
                            }

                            buffer.addAll(s.toList())
                            
                            // 防止 buffer 无限增长：如果超过最大大小，移除最旧的数据
                            if (buffer.size > maxBufferSize) {
                                val removeCount = buffer.size - maxBufferSize
                                if (offset >= removeCount) {
                                    // 移除已处理的数据
                                    buffer = ArrayList(buffer.subList(removeCount, buffer.size))
                                    offset -= removeCount
                                    Log.w(TAG, "Buffer cleanup: removed $removeCount samples, new size: ${buffer.size}")
                                } else {
                                    // 如果 offset 还没处理那么多数据，强制清理并重置
                                    Log.w(TAG, "Buffer too large, forcing reset")
                                    buffer = arrayListOf()
                                    offset = 0
                                    isSpeechStarted = false
                                    vad.reset()
                                    lastVadActivityTime = System.currentTimeMillis()
                                    continue
                                }
                            }
                            
                            while (offset + windowSize < buffer.size) {
                                vad.acceptWaveform(
                                    buffer.subList(
                                        offset,
                                        offset + windowSize
                                    ).toFloatArray()
                                )
                                offset += windowSize
                                
                                // VAD 检测逻辑
                                if (!isSpeechStarted && vad.isSpeechDetected()) {
                                    isSpeechStarted = true
                                    startTime = System.currentTimeMillis()
                                    lastVadActivityTime = System.currentTimeMillis()
                                    // VAD 检测到语音，更新最后语音检测时间
                                    lastSpeechDetectedTime = System.currentTimeMillis()
                                    Log.d(TAG, "🗣️ VAD 检测到语音，重置静音计时")
                                } else if (isSpeechStarted && vad.isSpeechDetected()) {
                                    // 持续检测到语音，更新最后语音检测时间
                                    lastSpeechDetectedTime = System.currentTimeMillis()
                                }
                                
                                // 检查静音超时：基于 VAD 检测到的实际静音时间
                                val currentTime = System.currentTimeMillis()
                                val silenceDuration = currentTime - lastSpeechDetectedTime
                                if (silenceDuration > SILENCE_TIMEOUT_MS && isStarted) {
                                    Log.i(TAG, "⏰ 检测到连续静音超过10秒（基于VAD），停止 AsrHandler")
                                    // 先调用回调更新 UI 状态
                                    silenceTimeoutCallback?.invoke()
                                    // 然后停止 AsrHandler
                                    contextForStop?.let {
                                        stop(it)
                                    }
                                    break
                                }
                            }
                            
                            // 定期清理已处理的数据，避免 buffer 过大
                            if (offset > cleanupThreshold && buffer.size > cleanupThreshold) {
                                val removeCount = cleanupThreshold
                                buffer = ArrayList(buffer.subList(removeCount, buffer.size))
                                offset -= removeCount
                                Log.d(TAG, "Periodic cleanup: removed $removeCount samples")
                            }
                            
                            // 超时保护：如果长时间没有检测到语音结束，清空 buffer
                            val timeSinceLastActivity = System.currentTimeMillis() - lastVadActivityTime
                            if (isSpeechStarted && timeSinceLastActivity > timeoutMs) {
                                Log.w(TAG, "VAD timeout, resetting buffer")
                                buffer = arrayListOf()
                                offset = 0
                                isSpeechStarted = false
                                vad.reset()
                                lastVadActivityTime = System.currentTimeMillis()
                            }

                            val elapsed = System.currentTimeMillis() - startTime
                            if (isSpeechStarted && elapsed > 200) {
                                // Run ASR every 0.2 seconds == 200 milliseconds
                                // You can change it to some other value
                                val stream = recognizer.createStream()
                                stream.acceptWaveform(
                                    buffer.subList(0, offset).toFloatArray(),
                                    SAMPLE_RATE_IN_HZ
                                )
                                recognizer.decode(stream)
                                val result = recognizer.getResult(stream)
                                stream.release()

                                lastText = result.text

                                if (lastText.isNotBlank()) {
                                    if (!added || resultList.isEmpty()) {
                                        resultList.add(lastText)
                                        added = true
                                    } else {
                                        resultList[resultList.size - 1] = lastText
                                    }
                                }

                                startTime = System.currentTimeMillis()
                            }


                            while (!vad.empty()) {
                                val stream = recognizer.createStream()
                                // 🔥 使用完整的buffer进行Final识别
                                val accumulatedAudio = if (buffer.isNotEmpty()) {
                                    buffer.subList(0, buffer.size).toFloatArray()
                                } else {
                                    // buffer为空时降级使用VAD段
                                    vad.front().samples
                                }
                                stream.acceptWaveform(
                                    accumulatedAudio,
                                    SAMPLE_RATE_IN_HZ
                                )
                                recognizer.decode(stream)
                                val result = recognizer.getResult(stream)
                                stream.release()

                                isSpeechStarted = false
                                vad.pop()

                                buffer = arrayListOf()
                                offset = 0
                                lastVadActivityTime = System.currentTimeMillis()
                                if (result.text.isNotBlank()) {
                                    if (added && resultList.isNotEmpty()) {
                                        resultList[resultList.size - 1] = result.text
                                    } else {
                                        resultList.add(result.text)
                                    }
                                    added = false
                                }
                                // 🔥 更新lastText为Final识别结果
                                lastText = result.text
                                
                                // 🔥 Final识别完成，触发技能识别
                                if (result.text.isNotBlank()) {
                                    Log.d(TAG, "🎯 Final识别完成，触发技能识别: ${result.text}")
                                    finalResultCallback?.invoke(result.text)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        }
    }
}

