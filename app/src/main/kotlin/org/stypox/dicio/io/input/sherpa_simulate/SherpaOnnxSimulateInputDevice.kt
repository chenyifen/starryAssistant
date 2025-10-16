/*
 * Dicio Sherpa-ONNX Simulate Streaming ASR Input Device
 * 完全基于官方demo实现的模拟流式识别
 * 
 * 参考：SherpaOnnxSimulateStreamingAsr/Home.kt
 * 设计：VAD语音检测 + 200ms间隔实时识别
 */

package org.stypox.dicio.io.input.sherpa_simulate

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.stypox.dicio.di.LocaleManager
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.io.input.SttInputDevice
import org.stypox.dicio.io.input.SttState
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 停止语音识别的原因
 */
enum class StopReason(val displayName: String) {
    USER_REQUESTED("用户主动停止"),
    SPEECH_END_DETECTED("检测到语音结束"),
    SILENCE_TIMEOUT("静音超时"),
    MAX_DURATION_REACHED("达到最大录音时长"),
    ERROR("发生错误"),
    DEVICE_DESTROY("设备销毁"),
    TOGGLE_OFF("切换关闭")
}

/**
 * Sherpa-ONNX模拟流式识别输入设备
 * 
 * 完全照搬官方demo的实现逻辑：
 * 1. 双协程架构：音频采集(IO) + 音频处理(Default)
 * 2. VAD实时检测语音段
 * 3. 检测到语音后每200ms对累积数据进行识别
 * 4. 语音结束后对完整语音段进行最终识别
 */
class SherpaOnnxSimulateInputDevice(
    private val appContext: Context,
    private val localeManager: LocaleManager,
) : SttInputDevice {

    companion object {
        private const val TAG = "SherpaSimulate"
        private const val AUTO_TEST_TAG = "AutoTest"
        
        // 自动化测试广播
        const val ACTION_AUTO_TEST_START = "org.stypox.dicio.AUTO_TEST_START"
        const val ACTION_AUTO_TEST_RESULT = "org.stypox.dicio.AUTO_TEST_RESULT"
        const val EXTRA_RESULT_TEXT = "result_text"
        
        // 音频配置 (与官方demo一致)
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC
        
        // VAD和识别参数 (与官方demo一致)
        private const val VAD_WINDOW_SIZE = 512  // 32ms @ 16kHz
        private const val RECOGNITION_INTERVAL_MS = 200L  // 200ms间隔识别
    }

    // ========== 硬件资源 ==========
    // recognizer 和 vad 由 SherpaOnnxManager 单例管理
    private var audioRecord: AudioRecord? = null
    
    // ========== 状态管理 ==========
    private val isInitialized = AtomicBoolean(false)
    private val isRecording = AtomicBoolean(false)
    private var isAutoTestMode = true  // 自动化测试模式
    
    private val _uiState = MutableStateFlow<SttState>(SttState.NotInitialized)
    override val uiState: StateFlow<SttState> = _uiState.asStateFlow()
    
    // ========== 通信Channel ==========
    private var samplesChannel = Channel<FloatArray>(capacity = Channel.UNLIMITED)
    
    // ========== 协程作用域 ==========
    private var scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // ========== 事件监听 ==========
    private var eventListener: ((InputEvent) -> Unit)? = null
    
    // ========== 音频缓冲和VAD状态（完全照搬demo）==========
    private var buffer = arrayListOf<Float>()
    private var offset = 0
    private var isSpeechStarted = false
    private var startTime = 0L
    private var lastText = ""
    private var added = false
    
    // ========== 耗时统计 ==========
    private var recordingStartTime = 0L
    private var speechDetectedTime = 0L

    init {
        Log.d(TAG, "🏗️ SherpaOnnxSimulateInputDevice 初始化")
        scope.launch {
            initializeComponents()
        }
    }
    
    /**
     * 初始化识别器和VAD
     * 在IO线程执行耗时操作，避免阻塞主线程导致ANR
     */
    private suspend fun initializeComponents() {
        try {
            Log.d(TAG, "🔧 开始初始化组件...")
            
            // 在IO线程执行耗时的模型加载操作（避免ANR）
            val (recognizerOk, vadOk) = withContext(Dispatchers.IO) {
                val recOk = SherpaOnnxManager.initOfflineRecognizer(appContext)
                val vadOk = SherpaOnnxManager.initVad(appContext)
                Pair(recOk, vadOk)
            }
            
            // 切回主线程更新UI状态
            withContext(Dispatchers.Main) {
                if (!recognizerOk || !vadOk) {
                    Log.e(TAG, "❌ 初始化失败 (recognizer=$recognizerOk, vad=$vadOk)")
                    _uiState.value = SttState.NotAvailable
                    return@withContext
                }
                
                // 验证单例管理器状态
                if (!SherpaOnnxManager.isInitialized()) {
                    Log.e(TAG, "❌ SherpaOnnxManager 初始化状态异常")
                    _uiState.value = SttState.NotAvailable
                    return@withContext
                }
                
                Log.d(TAG, "✅ 所有组件初始化成功")
                isInitialized.set(true)
                _uiState.value = SttState.Loaded
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化失败", e)
            Log.e(TAG, "💡 错误详情: ${e.message}")
            withContext(Dispatchers.Main) {
                _uiState.value = SttState.ErrorLoading(e)
            }
        }
    }
    
    /**
     * 启动语音识别
     */
    override fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean {
        Log.d(TAG, "🚀 启动语音识别 (VAD: ${if (SherpaOnnxManager.vad != null) "启用" else "禁用"})")
        
        if (!isInitialized.get() || !SherpaOnnxManager.isInitialized()) {
            Log.e(TAG, "❌ 识别器或VAD未初始化完成 (initialized=${isInitialized.get()}, manager=${SherpaOnnxManager.isInitialized()})")
            return false
        }
        
        if (!isRecording.compareAndSet(false, true)) {
            Log.w(TAG, "⚠️ 已在录制中")
            return true
        }
        
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            Log.d(TAG, "🔄 重新创建协程作用域")
        }
        
        this.eventListener = thenStartListeningEventListener
        resetRecordingState()
        _uiState.value = SttState.Listening
        
        // 记录录音开始时间
        recordingStartTime = System.currentTimeMillis()
        Log.d(TAG, "⏱️ [T0] 录音开始时间: $recordingStartTime")
        
        // 启动音频采集协程 (IO)
        scope.launch(Dispatchers.IO) {
            recordAudio()
        }
        
        // 启动音频处理协程 (Default)
        scope.launch(Dispatchers.Default) {
            processAudio()
        }
        
        return true
    }
    
    /**
     * 点击事件
     */
    override fun onClick(eventListener: (InputEvent) -> Unit) {
        Log.d(TAG, "🖱️ 点击事件")
        
        if (isRecording.get()) {
            stopListeningWithReason(StopReason.USER_REQUESTED)
        } else {
            tryLoad(eventListener)
        }
    }
    
    /**
     * 停止语音识别
     * @param reason 停止原因
     */
    override fun stopListening() {
        stopListeningWithReason(StopReason.USER_REQUESTED)
    }
    
    /**
     * 停止语音识别（内部方法，带原因）
     * @param reason 停止原因
     */
    private fun stopListeningWithReason(reason: StopReason) {
        if (!isRecording.get()) {
            return
        }
        
        Log.i(TAG, "🛑 停止语音识别 - 原因: ${reason.displayName}")
        isRecording.set(false)
        
        // 立即停止AudioRecord
        audioRecord?.let {
            try {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                    Log.d(TAG, "   ✓ AudioRecord已停止")
                }
            } catch (e: Exception) {
                Log.w(TAG, "   ✗ 停止AudioRecord失败", e)
            }
        }
    }
    
    /**
     * 销毁设备
     */
    override suspend fun destroy() {
        Log.d(TAG, "🧹 销毁设备...")
        
        try {
            stopListeningWithReason(StopReason.DEVICE_DESTROY)
            
            samplesChannel.close()
            
            // recognizer 和 vad 由 SherpaOnnxManager 单例管理，不在此处释放
            
            eventListener = null
            isInitialized.set(false)
            _uiState.value = SttState.NotInitialized
            
            Log.d(TAG, "✅ 设备已销毁")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 销毁失败", e)
        }
    }
    
    /**
     * 重置录制状态
     */
    private fun resetRecordingState() {
        buffer.clear()
        offset = 0
        isSpeechStarted = false
        startTime = 0L
        lastText = ""
        added = false
        speechDetectedTime = 0L
        
        // 只有在VAD已初始化时才重置
        SherpaOnnxManager.vad?.let {
            try {
                it.reset()
                Log.d(TAG, "✅ VAD已重置")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ VAD重置失败", e)
            }
        }
        
        samplesChannel.close()
        samplesChannel = Channel(capacity = Channel.UNLIMITED)
        
        Log.d(TAG, "🔄 状态已重置")
    }
    
    /**
     * 音频采集协程 (完全照搬demo)
     * 运行在 IO Dispatcher
     */
    private suspend fun recordAudio() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🎵 启动音频采集")
            
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            audioRecord = AudioRecord(
                AUDIO_SOURCE,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )
            
            val interval = 0.1  // 100ms
            val bufferSampleSize = (interval * SAMPLE_RATE).toInt()
            val audioBuffer = ShortArray(bufferSampleSize)
            
            audioRecord?.startRecording()
            Log.d(TAG, "🎙️ AudioRecord 开始录音")
            
            var frameCount = 0
            var totalSamples = 0
            var maxAmplitude = 0f
            
            while (isRecording.get()) {
                val ret = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: -1
                if (ret > 0) {
                    frameCount++
                    totalSamples += ret
                    
                    val samples = FloatArray(ret) { audioBuffer[it] / 32768.0f }
                    
                    // 计算当前帧的最大振幅和能量
                    val amplitude = samples.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
                    val energy = samples.map { it * it }.average()
                    
                    if (amplitude > maxAmplitude) {
                        maxAmplitude = amplitude
                    }
                    
                    // 每50帧（约5秒）打印一次统计
                    if (frameCount % 50 == 0) {
                        Log.d(TAG, "🎵 音频采集统计 - Frame: $frameCount | Samples: $totalSamples | MaxAmp: %.4f | Energy: %.6f".format(maxAmplitude, energy))
                    }
                    
                    // 如果检测到音频，打印详细信息
                    if (amplitude > 0.01f) {
                        Log.d(TAG, "🔊 检测到音频信号 - Frame: $frameCount | Amp: %.4f | Energy: %.6f | Samples: $ret".format(amplitude, energy))
                    }
                    
                    samplesChannel.send(samples)
                } else if (ret == 0) {
                    Log.w(TAG, "⚠️ AudioRecord读取返回0字节")
                } else {
                    Log.e(TAG, "❌ AudioRecord读取失败: $ret")
                }
            }
            
            // 发送空数组作为结束信号
            samplesChannel.send(FloatArray(0))
            Log.d(TAG, "🎵 音频采集结束 - 总帧数: $frameCount, 总样本: $totalSamples, 最大振幅: %.4f".format(maxAmplitude))
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 音频采集失败", e)
        } finally {
            audioRecord?.let {
                try {
                    it.stop()
                    it.release()
                } catch (e: Exception) {
                    Log.w(TAG, "释放AudioRecord失败", e)
                }
            }
            audioRecord = null
        }
    }
    
    /**
     * 音频处理协程 (完全照搬demo)
     * 运行在 Default Dispatcher
     */
    private suspend fun processAudio() = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "🔄 启动音频处理")
            
            // 从单例管理器获取实例引用
            val vadInstance = SherpaOnnxManager.vad ?: run {
                Log.e(TAG, "❌ VAD未初始化，无法处理音频")
                return@withContext
            }
            val recognizerInstance = SherpaOnnxManager.recognizer ?: run {
                Log.e(TAG, "❌ 识别器未初始化，无法处理音频")
                return@withContext
            }
            
            Log.i(TAG, "✅ VAD和识别器已就绪，开始处理音频流")
            
            var receivedFrames = 0
            var processedVadFrames = 0
            
            while (isRecording.get()) {
                for (samples in samplesChannel) {
                    if (samples.isEmpty()) {
                        Log.d(TAG, "🔚 收到空样本（结束信号）")
                        break
                    }
                    
                    receivedFrames++
                    
                    // 每10帧打印一次接收状态
                    if (receivedFrames % 10 == 0) {
                        Log.d(TAG, "📦 已接收音频帧: $receivedFrames | Buffer大小: ${buffer.size} | Offset: $offset")
                    }
                    
                    // 添加样本到缓冲区
                    buffer.addAll(samples.toList())
                    
                    Log.d(TAG, "📥 接收到音频样本 - Frame: $receivedFrames | Samples: ${samples.size} | Buffer: ${buffer.size}")
                    
                    // VAD处理
                    while (offset + VAD_WINDOW_SIZE < buffer.size) {
                        try {
                            val vadFrame = buffer.subList(offset, offset + VAD_WINDOW_SIZE).toFloatArray()
                            
                            // 计算音频能量（调试用）
                            val energy = vadFrame.map { it * it }.average()
                            
                            vadInstance.acceptWaveform(vadFrame)
                            offset += VAD_WINDOW_SIZE
                            
                            val speechDetected = vadInstance.isSpeechDetected()
                            
                            // 每10帧打印一次VAD状态
                            if (offset % (VAD_WINDOW_SIZE * 10) == 0) {
                                Log.d(TAG, "🎤 VAD Frame - offset: $offset | buffer: ${buffer.size} | energy: %.6f | speech: %s | started: %s".format(
                                    energy, if (speechDetected) "✅" else "❌", if (isSpeechStarted) "YES" else "NO"
                                ))
                            }
                            
                            // 检测语音开始
                            if (!isSpeechStarted && speechDetected) {
                                isSpeechStarted = true
                                startTime = System.currentTimeMillis()
                                
                                // 记录语音检测时间和延迟
                                speechDetectedTime = System.currentTimeMillis()
                                val detectDelay = speechDetectedTime - recordingStartTime
                                Log.i(TAG, "🎯 语音开始检测!")
                                Log.d(TAG, "⏱️ [T1] 语音检测时间: ${speechDetectedTime}ms (检测延迟: ${detectDelay}ms)")
                                Log.d(TAG, "📊 VAD状态 - buffer: ${buffer.size} | offset: $offset | energy: %.6f".format(energy))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ VAD处理失败", e)
                            Log.e(TAG, "   错误详情: ${e.message}")
                            Log.e(TAG, "   堆栈: ${e.stackTraceToString()}")
                            stopListeningWithReason(StopReason.ERROR)
                            return@withContext
                        }
                    }
                    
                    // 实时识别（每200ms，照搬demo逻辑）
                    val elapsed = System.currentTimeMillis() - startTime
                    if (isSpeechStarted) {
                        Log.d(TAG, "🔍 语音进行中 - elapsed: ${elapsed}ms, threshold: ${RECOGNITION_INTERVAL_MS}ms")
                        if (elapsed > RECOGNITION_INTERVAL_MS) {
                            Log.d(TAG, "🎯 开始实时识别 - buffer size: ${buffer.size}, offset: $offset")
                            performPartialRecognition(recognizerInstance)
                            startTime = System.currentTimeMillis()
                        }
                    }
                    
                    // 处理VAD队列中的完整语音段（照搬demo逻辑）
                    try {
                        while (!vadInstance.empty()) {
                            Log.d(TAG, "🔚 检测到语音段结束 - 开始最终识别")
                            performFinalRecognition(vadInstance, recognizerInstance)
                            
                            vadInstance.pop()
                            isSpeechStarted = false
                            buffer = arrayListOf()
                            offset = 0
                            Log.d(TAG, "✅ 语音段处理完成，等待下一段语音...")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ VAD队列处理失败", e)
                    }
                }
            }
            
            Log.d(TAG, "🔄 音频处理结束")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 音频处理失败", e)
        }
    }
    
    /**
     * 实时部分识别 (完全照搬demo)
     */
    private suspend fun performPartialRecognition(recognizerInstance: OfflineRecognizer) {
        try {
            Log.d(TAG, "🔍 [Partial Recognition] 开始实时识别...")
            val recognitionStart = System.currentTimeMillis()
            
            val audioData = buffer.subList(0, offset).toFloatArray()
            Log.d(TAG, "   音频数据: ${audioData.size} 样本 (${audioData.size / SAMPLE_RATE.toFloat()} 秒)")
            
            // 计算音频能量
            val energy = audioData.map { it * it }.average()
            Log.d(TAG, "   音频能量: %.6f".format(energy))
            
            // 创建stream并识别
            val stream = recognizerInstance.createStream()
            Log.d(TAG, "   ✓ Stream 已创建")
            
            stream.acceptWaveform(audioData, SAMPLE_RATE)
            Log.d(TAG, "   ✓ 音频数据已提交")
            
            recognizerInstance.decode(stream)
            Log.d(TAG, "   ✓ 解码完成")
            
            val result = recognizerInstance.getResult(stream)
            stream.release()
            
            val recognitionTime = System.currentTimeMillis() - recognitionStart
            
            lastText = result?.text ?: ""
            
            Log.d(TAG, "   识别结果: \"$lastText\" (${lastText.length} 字符)")
            
            if (lastText.isNotBlank()) {
                val totalTime = System.currentTimeMillis() - recordingStartTime
                val detectDelay = if (speechDetectedTime > 0) {
                    speechDetectedTime - recordingStartTime
                } else {
                    0L
                }
                
                // 打印详细耗时统计
                Log.i(TAG, "✅ [Partial] 识别成功!")
                Log.d(TAG, "   📊 总耗时: ${totalTime}ms")
                Log.d(TAG, "   📊 检测延迟: ${detectDelay}ms")
                Log.d(TAG, "   📊 识别耗时: ${recognitionTime}ms")
                Log.d(TAG, "   📝 文本: \"$lastText\"")
                
                // 照搬demo的结果管理逻辑
                withContext(Dispatchers.Main) {
                    if (!added) {
                        eventListener?.invoke(InputEvent.Partial(lastText))
                        added = true
                        Log.i(TAG, "📤 [Partial] 首次发送: $lastText")
                    } else {
                        eventListener?.invoke(InputEvent.Partial(lastText))
                        Log.i(TAG, "📤 [Partial] 更新结果: $lastText")
                    }
                }
            } else {
                Log.w(TAG, "⚠️ [Partial] 识别结果为空 (耗时: ${recognitionTime}ms)")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 实时识别失败", e)
            Log.e(TAG, "   错误详情: ${e.message}")
            Log.e(TAG, "   堆栈: ${e.stackTraceToString()}")
        }
    }
    
    /**
     * 最终识别 (完全照搬demo)
     */
    private suspend fun performFinalRecognition(vadInstance: Vad, recognizerInstance: OfflineRecognizer) {
        try {
            Log.d(TAG, "🏁 [Final Recognition] 开始最终识别...")
            val recognitionStart = System.currentTimeMillis()
            
            // 从VAD队列获取完整语音段
            val speechSegment = vadInstance.front()
            Log.d(TAG, "   语音段数据: ${speechSegment.samples.size} 样本 (${speechSegment.samples.size / SAMPLE_RATE.toFloat()} 秒)")
            Log.d(TAG, "   语音段起始: ${speechSegment.start} 秒")
            
            // 计算语音段能量
            val energy = speechSegment.samples.map { it * it }.average()
            Log.d(TAG, "   音频能量: %.6f".format(energy))
            
            val stream = recognizerInstance.createStream()
            Log.d(TAG, "   ✓ Stream 已创建")
            
            stream.acceptWaveform(speechSegment.samples, SAMPLE_RATE)
            Log.d(TAG, "   ✓ 音频数据已提交")
            
            recognizerInstance.decode(stream)
            Log.d(TAG, "   ✓ 解码完成")
            
            val result = recognizerInstance.getResult(stream)
            stream.release()
            
            val recognitionTime = System.currentTimeMillis() - recognitionStart
            val totalTime = System.currentTimeMillis() - recordingStartTime
            
            val finalText = result?.text ?: ""
            
            Log.d(TAG, "   识别结果: \"$finalText\" (${finalText.length} 字符)")
            
            // 打印详细最终识别耗时
            Log.i(TAG, "🏁 [Final] 识别完成!")
            Log.d(TAG, "   📊 总耗时: ${totalTime}ms")
            Log.d(TAG, "   📊 识别耗时: ${recognitionTime}ms")
            Log.d(TAG, "   📝 文本: \"$finalText\"")
            Log.d(TAG, "   🔄 上一次部分结果: \"$lastText\"")
            
            // 照搬demo的结果管理逻辑
            if (lastText.isNotBlank()) {
                withContext(Dispatchers.Main) {
                    if (added) {
                        // 已有部分结果，发送最终结果
                        eventListener?.invoke(InputEvent.Final(listOf(finalText to 1.0f)))
                        Log.i(TAG, "📤 [Final] 更新为最终结果: $finalText")
                    } else {
                        // 没有部分结果，直接添加
                        eventListener?.invoke(InputEvent.Final(listOf(finalText to 1.0f)))
                        Log.i(TAG, "📤 [Final] 直接添加最终结果: $finalText")
                    }
                    added = false
                }
            } else {
                Log.w(TAG, "⚠️ [Final] 没有部分识别结果，跳过最终结果发送")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 最终识别失败", e)
            Log.e(TAG, "   错误详情: ${e.message}")
            Log.e(TAG, "   堆栈: ${e.stackTraceToString()}")
        }
    }
}


