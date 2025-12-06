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
                    return
                }
                val asrModelType = 15
                val asrRuleFsts: String? = null

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
            }
        }

        fun initVad(assetManager: AssetManager? = null) {
            synchronized(this) {
                if (_vad != null) {
                    return
                }
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
            return
        }
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

                // 使用内部 SimulateStreamingAsr 的 recognizer 和 vad
                val recognizer = SimulateStreamingAsr.recognizer
                val vad = SimulateStreamingAsr.vad
                
                if (recognizer == null || vad == null) {
                    Log.e(TAG, "❌ recognizer 或 VAD 未初始化")
                    return
                }

                vad.reset()

                CoroutineScope(Dispatchers.IO).launch {
                    val interval = 0.1
                    val bufferSize = (interval * SAMPLE_RATE_IN_HZ).toInt() // in samples
                    val buffer = ShortArray(bufferSize)

                    audioRecord?.let { it ->
                        it.startRecording()

                        while (isStarted) {
                            val ret = audioRecord?.read(buffer, 0, buffer.size)
                            ret?.let { n ->
                                if (n > 0) {
                                    lastAudioReceivedTime = System.currentTimeMillis()
                                    audioDataCount++
                                    if (audioDataCount % 50 == 0L) {
                                        AutoTestLogger.logAudioDataReceived(n, isStarted)
                                    }
                                }
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
                        for (s in samplesChannel) {
                            if (s.isEmpty()) {
                                break
                            }

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
                                } else {
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
                                val vadDetected = vad.isSpeechDetected()
                                AutoTestLogger.logVadDetected(vadDetected)
                                
                                if (!isSpeechStarted && vadDetected) {
                                    isSpeechStarted = true
                                    startTime = System.currentTimeMillis()
                                    lastVadActivityTime = System.currentTimeMillis()
                                    lastSpeechDetectedTime = System.currentTimeMillis()
                                    finalTriggeredForCurrentSegment = false
                                } else if (isSpeechStarted && vadDetected) {
                                    // 持续检测到语音，更新最后语音检测时间
                                    lastSpeechDetectedTime = System.currentTimeMillis()
                                    finalTriggeredForCurrentSegment = false
                                }
                                
                                // 检查静音超时：基于 VAD 检测到的实际静音时间
                                val currentTime = System.currentTimeMillis()
                                val silenceDuration = currentTime - lastSpeechDetectedTime
                                if (silenceDuration > SILENCE_TIMEOUT_MS && isStarted) {
                                    AutoTestLogger.logSilenceTimeoutTriggered(silenceDuration)
                                    // 先调用回调更新 UI 状态
                                    silenceTimeoutCallback?.invoke()
                                    // 然后停止 AsrHandler
                                    contextForStop?.let {
                                        stop(it)
                                    }
                                    break
                                }
                                
                                val timeSinceLastAudio = currentTime - lastAudioReceivedTime
                                if (isStarted && timeSinceLastAudio > 5000) {
                                    AutoTestLogger.logAsrStartedButNoAudio(timeSinceLastAudio)
                                }
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
                                    val currentTime = System.currentTimeMillis()
                                    val textChanged = lastText != lastPartialText
                                    
                                    if (textChanged) {
                                        lastPartialText = lastText
                                        lastPartialTextTime = currentTime
                                        finalTriggeredForCurrentSegment = false
                                    }
                                    
                                    if (!added || resultList.isEmpty()) {
                                        resultList.add(lastText)
                                        Log.d("chenyifen","resultList.add(lastText)  = ${lastText} ")
                                        added = true
                                    } else {
                                        resultList[resultList.size - 1] = lastText
                                        Log.d("chenyifen","set to list, Asr last text = ${lastText}")
                                    }
                                    
                                    if (!finalTriggeredForCurrentSegment && isSpeechStarted) {
                                        val silenceSinceLastPartial = currentTime - lastSpeechDetectedTime
                                        val stableSinceLastChange = currentTime - lastPartialTextTime
                                        
                                        if (silenceSinceLastPartial >= PARTIAL_STABLE_THRESHOLD_MS && 
                                            stableSinceLastChange >= PARTIAL_STABLE_THRESHOLD_MS) {
                                            finalTriggeredForCurrentSegment = true
                                            Log.d("chenyifen","invoke Final (stable): ${lastText}")
                                            finalResultCallback?.invoke(lastText)
                                        }
                                    }
                                }

                                startTime = System.currentTimeMillis()
                            }


                            while (!vad.empty()) {
                                isSpeechStarted = false
                                vad.pop()
                                
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
                                    Log.d("chenyifen","invoke Final: ${finalText}")
                                    finalResultCallback?.invoke(finalText)
                                }
                                
                                lastText = finalText
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

