package com.ai.voice.util

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.ai.voice.util.ActivationChecker
import com.ai.voice.util.AutoTestLogger
import com.ai.voice.util.DebugLogger
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
import kotlinx.coroutines.delay

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
    // 🔒 线程安全：使用 @Volatile 确保多线程可见性
    @Volatile
    private var isStarted = false
    
    // ASR 结果列表，参考原有 Home.kt 的实现方式
    private val resultList: MutableList<String> = mutableListOf()
    
    // 静音超时配置
    private const val SILENCE_TIMEOUT_MS = 6000L // 静音超时
    // Final识别触发条件：需要持续静音至少500ms（平衡响应速度和误触发）
    private const val FINAL_SILENCE_THRESHOLD_MS = 800L
    // Partial识别稳定后触发Final识别的时间阈值
    private const val PARTIAL_STABLE_THRESHOLD_MS = 500L
    private var lastSpeechDetectedTime = System.currentTimeMillis()
    private var contextForStop: Context? = null
    // 🔒 线程安全：回调变量使用 @Volatile
    @Volatile
    private var silenceTimeoutCallback: (() -> Unit)? = null
    
    // Final识别结果回调（用于触发技能识别）
    @Volatile
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
    
    private var lastAudioReceivedTime: Long = 0
    private var audioDataCount: Long = 0
    
    /**
     * 重置VAD静音超时时间（在每次唤醒后调用）
     */
    fun resetSilenceTimeout() {
        lastSpeechDetectedTime = System.currentTimeMillis()
        lastAudioReceivedTime = System.currentTimeMillis()
        audioDataCount = 0
        AutoTestLogger.logSilenceTimeoutReset()
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
                    Log.i(TAG, "⚠️ ASR Recognizer 已初始化，跳过")
                    return
                }
                Log.i(TAG, "🚀 开始初始化 ASR Recognizer (type=15)...")
                val asrModelType = 15
                val asrRuleFsts: String? = null

                val useHr = false
                val hr = com.k2fsa.sherpa.onnx.HomophoneReplacerConfig(
                    lexicon = "lexicon.txt",
                    ruleFsts = "replace.fst",
                )

                Log.i(TAG, "📋 调用 getOfflineModelConfig(type=$asrModelType)...")
                val modelConfig = getOfflineModelConfig(type = asrModelType)
                if (modelConfig == null) {
                    Log.e(TAG, "❌ getOfflineModelConfig(type=$asrModelType) 返回 null")
                    Log.e(TAG, "❌ 可能原因：1) 模型文件不存在 2) type=$asrModelType 未配置 3) 模型路径配置错误")
                    throw IllegalStateException("getOfflineModelConfig 返回 null，请检查模型文件是否存在")
                }
                
                Log.i(TAG, "✅ getOfflineModelConfig 返回配置成功")
                Log.i(TAG, "📦 模型配置信息: modelConfig=$modelConfig")
                
                val config = OfflineRecognizerConfig(
                    modelConfig = modelConfig,
                )
                
                Log.i(TAG, "📦 OfflineRecognizerConfig 创建成功: numThreads=${config.modelConfig.numThreads}")

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
                Log.i(TAG, "✅ ASR Recognizer 初始化成功")
            }
        }

        fun initVad(assetManager: AssetManager? = null) {
            synchronized(this) {
                if (_vad != null) {
                    Log.i(TAG, "⚠️ ASR VAD 已初始化，跳过")
                    return
                }
                Log.i(TAG, "🚀 开始初始化 ASR VAD (type=0)...")
                val type = 0
                val config = getVadModelConfig(type)
                if (config == null) {
                    Log.e(TAG, "❌ getVadModelConfig(type=$type) 返回 null")
                    throw IllegalStateException("getVadModelConfig 返回 null，请检查 VAD 模型文件是否存在")
                }

                _vad = Vad(
                    assetManager = assetManager,
                    config = config,
                )
                Log.i(TAG, "✅ ASR VAD 初始化成功")
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
        return try {
            val application = context.applicationContext as? Application
            if (application == null) {
                Log.e(TAG, "❌ 无法获取 Application 实例")
                return false
            }
            
            SimulateStreamingAsr.initOfflineRecognizer(context.assets, application)
            SimulateStreamingAsr.initVad(context.assets)
            val recognizerInitialized = SimulateStreamingAsr.isRecognizerInitialized()
            val vadInitialized = SimulateStreamingAsr.isVadInitialized()
            Log.i(TAG, "✅ AsrHandler 初始化完成: recognizer=$recognizerInitialized, vad=$vadInitialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ AsrHandler 初始化失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 开始 ASR 识别
     * @param context 上下文，用于权限检查
     * @return 是否成功启动
     */
    // 🔒 线程安全：使用 synchronized 防止竞态条件
    @Synchronized
    fun start(context: Context): Boolean {
        if (isStarted) {
            return true
        }
        
        val isActivated = ActivationChecker.isActivated(context)
        if (!isActivated) {
            Log.e(TAG, "❌ 应用试用期已过期")
            return false
        }
        
        if (ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "❌ 没有录音权限")
            return false
        }
        
        if (!SimulateStreamingAsr.isRecognizerInitialized() || !SimulateStreamingAsr.isVadInitialized()) {
            Log.e(TAG, "❌ recognizer 或 VAD 未初始化")
            return false
        }
        
        contextForStop = context
        resetSilenceTimeout()
        isStarted = true
        lastAudioReceivedTime = System.currentTimeMillis()
        audioDataCount = 0
        DebugLogger.logVoiceRecognition(TAG, "ASR 启动成功")
        AutoTestLogger.logAsrListeningStarted()
        doAsr(context)
        return true
    }
    
    /**
     * 停止 ASR 识别
     * @param context 上下文，用于执行停止逻辑
     */
    // 🔒 线程安全：使用 synchronized 防止竞态条件
    @Synchronized
    fun stop(context: Context) {
        if (!isStarted) {
            DebugLogger.logRecognition(TAG, "ASR 已停止，跳过")
            return
        }
        DebugLogger.logVoiceRecognition(TAG, "ASR 停止")
        isStarted = false
        contextForStop = null
        lastAudioReceivedTime = 0
        audioDataCount = 0
        AutoTestLogger.logAsrListeningStopped()
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
                return
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

                val audioRecordState = audioRecord?.state
                DebugLogger.logVoiceRecognition(TAG, "AudioRecord 创建完成: state=$audioRecordState, bufferSize=${numBytes * 2}")
                
                if (audioRecordState != AudioRecord.STATE_INITIALIZED) {
                    DebugLogger.logVoiceRecognitionError(TAG, "AudioRecord 初始化失败: state=$audioRecordState", null)
                    isStarted = false
                    audioRecord?.release()
                    audioRecord = null
                    return
                }

                // 使用内部 SimulateStreamingAsr 的 recognizer 和 vad
                val recognizer = SimulateStreamingAsr.recognizer
                val vad = SimulateStreamingAsr.vad
                
                if (recognizer == null || vad == null) {
                    DebugLogger.logVoiceRecognitionError(TAG, "recognizer 或 VAD 未初始化: recognizer=${recognizer != null}, vad=${vad != null}", null)
                    return
                }
                DebugLogger.logVoiceRecognition(TAG, "✅ recognizer 和 VAD 已初始化，开始ASR处理")

                vad.reset()
                DebugLogger.logVoiceRecognition(TAG, "开始录音，准备接收音频数据")

                CoroutineScope(Dispatchers.IO).launch {
                    val interval = 0.1
                    val bufferSize = (interval * SAMPLE_RATE_IN_HZ).toInt() // in samples
                    val buffer = ShortArray(bufferSize)

                    audioRecord?.let { it ->
                        try {
                        it.startRecording()
                            DebugLogger.logVoiceRecognition(TAG, "AudioRecord.startRecording() 调用成功")
                            
                            var consecutiveErrors = 0
                            var consecutiveZeros = 0

                        while (isStarted) {
                            val ret = audioRecord?.read(buffer, 0, buffer.size)
                                
                                when {
                                    ret == null -> {
                                        consecutiveErrors++
                                        if (consecutiveErrors == 1 || consecutiveErrors % 10 == 0) {
                                            DebugLogger.logVoiceRecognitionError(TAG, "AudioRecord.read() 返回 null (连续 $consecutiveErrors 次)", null)
                                        }
                                        if (consecutiveErrors >= 50) {
                                            DebugLogger.logVoiceRecognitionError(TAG, "AudioRecord.read() 连续失败 50 次，停止录音", null)
                                            break
                                        }
                                        delay(100)
                                    }
                                    ret < 0 -> {
                                        consecutiveErrors++
                                        val errorMsg = when (ret) {
                                            AudioRecord.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION"
                                            AudioRecord.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE"
                                            else -> "未知错误码: $ret"
                                        }
                                        if (consecutiveErrors == 1 || consecutiveErrors % 10 == 0) {
                                            DebugLogger.logVoiceRecognitionError(TAG, "AudioRecord.read() 错误: $errorMsg (连续 $consecutiveErrors 次)", null)
                                        }
                                        if (consecutiveErrors >= 50) {
                                            DebugLogger.logVoiceRecognitionError(TAG, "AudioRecord.read() 连续错误 50 次，停止录音", null)
                                            break
                                        }
                                        delay(100)
                                    }
                                    ret == 0 -> {
                                        consecutiveZeros++
                                        consecutiveErrors = 0
                                        if (consecutiveZeros == 1 || consecutiveZeros % 100 == 0) {
                                            DebugLogger.logRecognition(TAG, "AudioRecord.read() 返回 0 (无数据，连续 $consecutiveZeros 次)")
                                        }
                                        delay(10)
                                    }
                                    else -> {
                                        consecutiveErrors = 0
                                        consecutiveZeros = 0
                                    lastAudioReceivedTime = System.currentTimeMillis()
                                    audioDataCount++
                                        if (audioDataCount == 1L || audioDataCount % 50 == 0L) {
                                            DebugLogger.logRecognition(TAG, "音频数据接收: $ret 字节 (总计: $audioDataCount)")
                                            AutoTestLogger.logAudioDataReceived(ret, isStarted)
                                }
                                        val samples = FloatArray(ret) { buffer[it] / 32768.0f }
                                samplesChannel.send(samples)
                                    }
                            }
                        }
                        val samples = FloatArray(0)
                        samplesChannel.send(samples)
                        } catch (e: Exception) {
                            DebugLogger.logVoiceRecognitionError(TAG, "AudioRecord 操作异常", e)
                        }
                    } ?: run {
                        DebugLogger.logVoiceRecognitionError(TAG, "AudioRecord 为 null，无法启动录音", null)
                    }
                }

                // 重置静音检测时间
                lastSpeechDetectedTime = System.currentTimeMillis()
                lastAudioReceivedTime = System.currentTimeMillis()
                
                val asrScope = CoroutineScope(Dispatchers.Default)
                asrScope.launch {
                    val noAudioCheckJob = launch {
                        while (isStarted) {
                            delay(2000)
                            val currentTime = System.currentTimeMillis()
                            if (isStarted && lastAudioReceivedTime > 0) {
                                val timeSinceLastAudio = currentTime - lastAudioReceivedTime
                                if (timeSinceLastAudio > 3000) {
                                    AutoTestLogger.logAsrStartedButNoAudio(timeSinceLastAudio)
                                }
                            }
                        }
                    }
                    var buffer = arrayListOf<Float>()
                    var offset = 0
                    val windowSize = 512
                    var isSpeechStarted = false
                    var startTime = System.currentTimeMillis()
                    var lastText = ""
                    var added = false
                    var lastPartialText = ""
                    var lastPartialTextTime = 0L
                    var finalTriggeredForCurrentSegment = false
                    // 最大 buffer 大小：限制为 10 秒的音频数据（防止内存溢出）
                    val maxBufferSize = SAMPLE_RATE_IN_HZ * 10
                    // 当 buffer 超过这个大小且 offset 已经处理了很多数据时，清理已处理的数据
                    val cleanupThreshold = maxBufferSize / 2
                    // 超时时间：如果超过 5 秒没有检测到语音结束，强制清空 buffer
                    val timeoutMs = 5000L
                    var lastVadActivityTime = System.currentTimeMillis()
                    // 音频接收监控
                    var lastAudioReceivedTime = System.currentTimeMillis()
                    var audioSampleCount = 0

                    while (isStarted) {
                        var hasNewAudio = false
                        for (s in samplesChannel) {
                            if (s.isEmpty()) {
                                break
                            }

                            hasNewAudio = true
                            // 记录音频数据接收
                            audioSampleCount++
                            lastAudioReceivedTime = System.currentTimeMillis()
                            if (audioSampleCount % 20 == 0) {
                                AutoTestLogger.logAudioDataReceived(s.size, isStarted)
                            }

                            buffer.addAll(s.toList())
                            
                            // 防止 buffer 无限增长：如果超过最大大小，移除最旧的数据
                            if (buffer.size > maxBufferSize) {
                                val removeCount = buffer.size - maxBufferSize
                                if (offset >= removeCount) {
                                    buffer = ArrayList(buffer.subList(removeCount, buffer.size))
                                    offset -= removeCount
                                    DebugLogger.logRecognition(TAG, "Buffer清理: 移除 $removeCount samples, offset: ${offset + removeCount} -> $offset, bufferSize: ${buffer.size + removeCount} -> ${buffer.size}")
                                } else {
                                    DebugLogger.logRecognition(TAG, "Buffer清理: offset($offset) < removeCount($removeCount), 清空buffer并重置")
                                    buffer = arrayListOf()
                                    offset = 0
                                    isSpeechStarted = false
                                    vad.reset()
                                    lastVadActivityTime = System.currentTimeMillis()
                                    continue
                                }
                            }
                            
                            // 如果 buffer 接近上限且 offset 无法继续增长，提前清理已处理的数据
                            if (buffer.size >= maxBufferSize * 0.9 && offset + windowSize >= buffer.size) {
                                val removeCount = cleanupThreshold
                                if (offset >= removeCount) {
                                    buffer = ArrayList(buffer.subList(removeCount, buffer.size))
                                    offset -= removeCount
                                    DebugLogger.logRecognition(TAG, "Buffer提前清理: offset=$offset, bufferSize=${buffer.size}, 移除 $removeCount samples")
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
                                val vadDetected = vad.isSpeechDetected()
                                
                                if (!isSpeechStarted && vadDetected) {
                                    isSpeechStarted = true
                                    startTime = System.currentTimeMillis()
                                    lastVadActivityTime = System.currentTimeMillis()
                                    lastSpeechDetectedTime = System.currentTimeMillis()
                                    finalTriggeredForCurrentSegment = false
                                    DebugLogger.logRecognition(TAG, "VAD 检测到语音开始 (offset=$offset, bufferSize=${buffer.size})")
                                } else if (isSpeechStarted && vadDetected) {
                                    lastSpeechDetectedTime = System.currentTimeMillis()
                                    lastVadActivityTime = System.currentTimeMillis()
                                    finalTriggeredForCurrentSegment = false
                                } else if (isSpeechStarted && !vadDetected) {
                                    val timeSinceLastVad = System.currentTimeMillis() - lastVadActivityTime
                                    if (timeSinceLastVad > 1000 && offset % (windowSize * 10) == 0) {
                                        DebugLogger.logRecognition(TAG, "VAD 未检测到语音 (offset=$offset, bufferSize=${buffer.size}, isSpeechStarted=$isSpeechStarted, 距上次VAD活动=${timeSinceLastVad}ms)")
                                    }
                                }
                            }
                            
                            val timeSinceLastAudio = System.currentTimeMillis() - lastAudioReceivedTime
                            if (isStarted && timeSinceLastAudio > 5000) {
                                AutoTestLogger.logAsrStartedButNoAudio(timeSinceLastAudio)
                            }
                        }
                        
                        // 检查静音超时：在主循环中检查，确保即使没有音频数据也能触发
                        val currentTime = System.currentTimeMillis()
                        val silenceDuration = currentTime - lastSpeechDetectedTime
                        if (silenceDuration > SILENCE_TIMEOUT_MS && isStarted) {
                            DebugLogger.logVoiceRecognition(TAG, "静音超时触发: ${silenceDuration}ms (阈值: ${SILENCE_TIMEOUT_MS}ms)")
                            AutoTestLogger.logSilenceTimeoutTriggered(silenceDuration)
                            silenceTimeoutCallback?.invoke()
                            contextForStop?.let {
                                stop(it)
                            }
                            break
                        } else if (silenceDuration > SILENCE_TIMEOUT_MS / 2) {
                            DebugLogger.logRecognition(TAG, "静音时长: ${silenceDuration}ms / ${SILENCE_TIMEOUT_MS}ms")
                        }
                        
                        if (!hasNewAudio) {
                            delay(100)
                        }
                        
                        if (offset > cleanupThreshold && buffer.size > cleanupThreshold) {
                            val removeCount = cleanupThreshold
                            buffer = ArrayList(buffer.subList(removeCount, buffer.size))
                            offset -= removeCount
                        }
                        
                        val timeSinceLastActivity = System.currentTimeMillis() - lastVadActivityTime
                        if (isSpeechStarted && timeSinceLastActivity > timeoutMs) {
                            buffer = arrayListOf()
                            offset = 0
                            isSpeechStarted = false
                            vad.reset()
                            lastVadActivityTime = System.currentTimeMillis()
                        }

                        val elapsed = System.currentTimeMillis() - startTime
                        if (isSpeechStarted) {
                            DebugLogger.logRecognition(TAG, "ASR检查: isSpeechStarted=true, elapsed=${elapsed}ms, offset=$offset, bufferSize=${buffer.size}, startTime=$startTime")
                        }
                        if (isSpeechStarted && elapsed > 200) {
                                // Run ASR every 0.2 seconds == 200 milliseconds
                                // You can change it to some other value
                                DebugLogger.logRecognition(TAG, "开始ASR识别: elapsed=${elapsed}ms, offset=$offset, bufferSize=${buffer.size}, startTime=$startTime")
                                val stream = recognizer.createStream()
                                val audioDataSize = minOf(offset, buffer.size)
                                val audioData = buffer.subList(0, audioDataSize).toFloatArray()
                                DebugLogger.logRecognition(TAG, "ASR音频数据长度: ${audioData.size} samples (offset=$offset, bufferSize=${buffer.size})")
                                stream.acceptWaveform(
                                    audioData,
                                    SAMPLE_RATE_IN_HZ
                                )
                                recognizer.decode(stream)
                                val result = recognizer.getResult(stream)
                                stream.release()

                                lastText = result.text

                                if (lastText.isNotBlank()) {
                                    DebugLogger.logRecognition(TAG, "ASR Partial 识别结果: \"$lastText\"")
                                    val currentTime = System.currentTimeMillis()
                                    val textChanged = lastText != lastPartialText
                                    
                                    if (textChanged) {
                                        lastPartialText = lastText
                                        lastPartialTextTime = currentTime
                                        finalTriggeredForCurrentSegment = false
                                    }
                                    
                                    if (!added || resultList.isEmpty()) {
                                        resultList.add(lastText)
                                        DebugLogger.logRecognition(TAG, "添加识别结果到列表: \"$lastText\"")
                                        added = true
                                    } else {
                                        resultList[resultList.size - 1] = lastText
                                        DebugLogger.logRecognition(TAG, "更新识别结果: \"$lastText\"")
                                    }
                                    
                                    if (!finalTriggeredForCurrentSegment && isSpeechStarted) {
                                        val silenceSinceLastPartial = currentTime - lastSpeechDetectedTime
                                        val stableSinceLastChange = currentTime - lastPartialTextTime
                                        
                                        if (silenceSinceLastPartial >= PARTIAL_STABLE_THRESHOLD_MS && 
                                            stableSinceLastChange >= PARTIAL_STABLE_THRESHOLD_MS) {
                                            finalTriggeredForCurrentSegment = true
                                            DebugLogger.logVoiceRecognition(TAG, "Final 识别触发 (稳定): \"$lastText\"")
                                            finalResultCallback?.invoke(lastText)
                                        }
                                    }
                        } else {
                            DebugLogger.logRecognition(TAG, "ASR Partial 识别结果为空 (offset=$offset, bufferSize=${buffer.size}, elapsed=${elapsed}ms)")
                        }

                                startTime = System.currentTimeMillis()
                            } else if (isSpeechStarted && elapsed <= 200) {
                                DebugLogger.logRecognition(TAG, "ASR等待elapsed超过200ms: elapsed=${elapsed}ms, offset=$offset, bufferSize=${buffer.size}, startTime=$startTime")
                            } else if (!isSpeechStarted && offset > windowSize * 20) {
                                DebugLogger.logRecognition(TAG, "ASR等待VAD检测: offset=$offset, bufferSize=${buffer.size}, elapsed=${elapsed}ms")
                            }

                            while (!vad.empty()) {
                                isSpeechStarted = false
                                vad.pop()
                                DebugLogger.logRecognition(TAG, "VAD 检测到语音结束")
                                
                                val finalText = if (lastText.isNotBlank()) {
                                    lastText
                                } else if (resultList.isNotEmpty()) {
                                    resultList.last()
                                } else {
                                    ""
                                }
                                
                                buffer = arrayListOf()
                                offset = 0
                                lastVadActivityTime = System.currentTimeMillis()
                                
                                if (finalText.isNotBlank()) {
                                    if (added && resultList.isNotEmpty()) {
                                        resultList[resultList.size - 1] = finalText
                                    } else {
                                        resultList.add(finalText)
                                    }
                                    added = false
                                DebugLogger.logVoiceRecognition(TAG, "Final 识别触发 (VAD结束): \"$finalText\"")
                                DebugLogger.logAsrResult(TAG, finalText)
                                    finalResultCallback?.invoke(finalText)
                            } else {
                                DebugLogger.logRecognition(TAG, "Final 识别触发但文本为空")
                                }
                                
                                lastText = finalText
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

