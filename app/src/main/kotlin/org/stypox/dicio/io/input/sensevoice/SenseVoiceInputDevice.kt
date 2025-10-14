/*
 * Dicio SenseVoice Input Device - Refactored
 * 基于SenseVoice多语言ASR的语音输入设备实现
 * 
 * 重构说明：
 * - 参考SherpaOnnxSimulateStreamingAsr官方demo的设计模式
 * - 使用状态驱动而非Job管理
 * - 简化协程生命周期管理
 * - 修复时序混乱和协程取消问题
 */

package org.stypox.dicio.io.input.sensevoice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.Vad
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.stypox.dicio.di.LocaleManager
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.io.input.SttInputDevice
import org.stypox.dicio.io.input.SttState
import org.stypox.dicio.util.DebugLogger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SenseVoice语音输入设备 - 单例模式
 * 
 * 设计原则：
 * 1. 状态驱动：使用 isRecording 标志控制流程，而非Job引用
 * 2. 两个独立协程：音频采集(IO) + 音频处理(Default)
 * 3. Channel通信：协程间通过Channel传递音频数据
 * 4. 自动清理：资源在finally块中自动释放
 */
class SenseVoiceInputDevice private constructor(
    private val appContext: Context,
    private val localeManager: LocaleManager,
) : SttInputDevice {

    companion object {
        private const val TAG = "SenseVoiceInputDevice"
        
        // 音频配置 (与官方demo保持一致)
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC
        
        // VAD和识别参数
        private const val VAD_WINDOW_SIZE = 512                    // VAD窗口大小 (32ms @ 16kHz)
        private const val RECOGNITION_INTERVAL_MS = 200L           // 实时识别间隔 (与demo一致)
        private const val SPEECH_TIMEOUT_MS = 6000L                // 静音超时 (3秒)
        private const val MAX_RECORDING_DURATION_MS = 30000L       // 最大录制时长 (30秒)
        private const val MIN_SPEECH_DURATION_MS = 500L            // 最短有效语音

        // 单例实例
        @Volatile
        private var INSTANCE: SenseVoiceInputDevice? = null

        fun getInstance(appContext: Context, localeManager: LocaleManager): SenseVoiceInputDevice {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SenseVoiceInputDevice(appContext, localeManager).also { 
                    INSTANCE = it
                    Log.d(TAG, "🏗️ 创建SenseVoiceInputDevice单例实例")
                }
            }
        }

        fun resetInstance() {
            synchronized(this) {
                INSTANCE?.let { instance ->
                    Log.d(TAG, "🔄 重置SenseVoiceInputDevice单例实例")
                    CoroutineScope(Dispatchers.Default).launch {
                        instance.destroy()
                    }
                }
                INSTANCE = null
            }
        }
    }

    // ========== 硬件资源 ==========
    private var senseVoiceRecognizer: SenseVoiceRecognizer? = null
    private var vad: Vad? = null
    private var audioRecord: AudioRecord? = null
    
    // ========== 状态管理 ==========
    private val isInitialized = AtomicBoolean(false)
    private val isRecording = AtomicBoolean(false)  // 主控制标志
    
    private val _uiState = MutableStateFlow<SttState>(SttState.NotInitialized)
    override val uiState: StateFlow<SttState> = _uiState.asStateFlow()
    
    // ========== 通信Channel ==========
    private var samplesChannel = Channel<FloatArray>(capacity = Channel.UNLIMITED)
    
    // ========== 协程作用域 ==========
    private var scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // ========== 事件监听 ==========
    private var eventListener: ((InputEvent) -> Unit)? = null
    
    // ========== 音频缓冲 ==========
    // 使用两个缓冲区：一个用于VAD检测，一个用于累积已检测到的语音
    private val vadBuffer = ArrayDeque<Float>(VAD_WINDOW_SIZE * 2)  // VAD处理的滑动窗口
    private val speechBuffer = arrayListOf<Float>()  // 检测到语音后累积的音频数据
    
    // ========== VAD状态 ==========
    private var isSpeechDetected = false
    private var speechStartTime = 0L
    private var lastRecognitionTime = 0L
    private var lastSpeechTime = 0L  // 最后一次检测到语音的时间
    private var lastEnergyLogTime = 0L  // 能量日志时间戳
    private var lastText = ""
    private var added = false  // 参考demo的结果管理

    init {
        Log.d(TAG, "🏗️ [INIT] SenseVoiceInputDevice构造函数开始")
        Log.d(TAG, "🎤 SenseVoice输入设备正在初始化...")
        Log.d(TAG, "🚀 [INIT] 启动协程初始化组件")
        scope.launch {
            Log.d(TAG, "🔄 [COROUTINE] initializeComponents()协程开始执行")
            initializeComponents()
            Log.d(TAG, "✅ [COROUTINE] initializeComponents()协程执行完成")
        }
        Log.d(TAG, "✅ [INIT] SenseVoiceInputDevice构造函数完成")
    }
    
    /**
     * 初始化识别器和VAD
     */
    private suspend fun initializeComponents() {
        Log.d(TAG, "🔧 开始初始化组件...")
        _uiState.value = SttState.Loading(thenStartListening = false)
        
        try {
            // 检查模型可用性
            if (!SenseVoiceModelManager.isModelAvailable(appContext)) {
                Log.e(TAG, "❌ SenseVoice模型不可用")
                _uiState.value = SttState.ErrorLoading(Exception("SenseVoice模型不可用"))
                return
            }
            
            // 创建识别器
            senseVoiceRecognizer = SenseVoiceRecognizer.create(appContext)
            if (senseVoiceRecognizer == null) {
                Log.e(TAG, "❌ 识别器创建失败")
                _uiState.value = SttState.ErrorLoading(Exception("识别器创建失败"))
                return
            }
            
            // 初始化VAD
            if (VadModelManager.isVadModelAvailable(appContext)) {
                val vadConfig = VadModelManager.createVadConfig(appContext)
                val modelPaths = VadModelManager.getVadModelPaths(appContext)
                if (vadConfig != null && modelPaths != null) {
                    try {
                        // 🔧 修复：根据模型来源选择正确的构造函数
                        // Vad构造函数签名: Vad(assetManager: AssetManager?, config: VadModelConfig)
                        vad = if (modelPaths.isFromAssets) {
                            Log.d(TAG, "🔧 从Assets加载VAD模型")
                            Vad(appContext.assets, vadConfig)
                        } else {
                            Log.d(TAG, "🔧 从文件系统加载VAD模型: ${modelPaths.modelPath}")
                            Vad(null, vadConfig)  // assetManager传null，使用配置中的文件路径
                        }
                        Log.d(TAG, "✅ VAD初始化成功")
                        Log.d(TAG, "📊 ${VadModelManager.getVadModelInfo(appContext)}")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ VAD初始化失败，回退到能量检测", e)
                        vad = null
                    }
                } else {
                    Log.w(TAG, "⚠️ VAD配置创建失败，使用能量检测")
                    vad = null
                }
            } else {
                Log.w(TAG, "⚠️ VAD模型不可用，使用能量检测")
                vad = null
            }
            
            isInitialized.set(true)
            _uiState.value = SttState.Loaded
            
            Log.d(TAG, "✅ 初始化完成")
            Log.d(TAG, "📊 ${SenseVoiceModelManager.getModelInfo(appContext)}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化异常", e)
            _uiState.value = SttState.ErrorLoading(e)
        }
    }
    
    /**
     * 启动语音识别
     * 参考官方demo的简洁设计
     */
    override fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean {
        Log.d(TAG, "🚀 启动语音识别")
        
        // 检查初始化状态
        if (!isInitialized.get() || senseVoiceRecognizer == null) {
            Log.e(TAG, "❌ 识别器未初始化")
            return false
        }
        
        // 使用CAS确保原子性操作
        if (!isRecording.compareAndSet(false, true)) {
            Log.w(TAG, "⚠️ 已在录制中")
            return true
        }
        
        // 确保协程作用域可用
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            Log.d(TAG, "🔄 重新创建协程作用域")
        }
        
        // 保存事件监听器
        this.eventListener = thenStartListeningEventListener
        
        // 重置状态
        resetRecordingState()
        
        // 更新UI状态
        _uiState.value = SttState.Listening
        
        // 启动音频采集协程 (IO Dispatcher)
        scope.launch(Dispatchers.IO) {
            recordAudio()
        }
        
        // 启动音频处理协程 (Default Dispatcher)
        scope.launch(Dispatchers.Default) {
            processAudio()
        }
        
        return true
    }
    
    /**
     * 点击事件处理
     */
    override fun onClick(eventListener: (InputEvent) -> Unit) {
        Log.d(TAG, "🖱️ 点击事件")
        
        if (isRecording.get()) {
            stopListening()
        } else {
            tryLoad(eventListener)
        }
    }
    
    /**
     * 停止语音识别
     * 简单设置标志，协程会自然结束
     */
    override fun stopListening() {
        if (!isRecording.get()) {
            return
        }
        
        Log.d(TAG, "🛑 停止语音识别")
        isRecording.set(false)
        
        // 注意：不需要手动取消协程或清理资源
        // 协程会通过 isRecording 标志自然结束
        // 资源会在 finally 块中自动清理
    }
    
    /**
     * 销毁设备
     */
    override suspend fun destroy() {
        Log.d(TAG, "🧹 销毁设备...")
        
        try {
            stopListening()
            
            samplesChannel.close()
            
            senseVoiceRecognizer?.release()
            senseVoiceRecognizer = null
            
            vad?.release()
            vad = null
            
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
        vadBuffer.clear()
        speechBuffer.clear()
        isSpeechDetected = false
        speechStartTime = 0L
        lastRecognitionTime = 0L
        lastSpeechTime = 0L
        lastEnergyLogTime = 0L
        lastText = ""
        added = false
        vad?.reset()
        
        // 重新创建Channel
        samplesChannel.close()
        samplesChannel = Channel(capacity = Channel.UNLIMITED)
        
        Log.d(TAG, "🔄 状态已重置")
    }
    
    /**
     * 音频采集协程 - 参考官方demo
     * 运行在 IO Dispatcher
     */
    private suspend fun recordAudio() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🎵 启动音频采集")
            
            // 创建AudioRecord
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
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "❌ AudioRecord初始化失败")
                isRecording.set(false)
                return@withContext
            }
            
            audioRecord?.startRecording()
            Log.d(TAG, "✅ 录制已启动")
            
            // 音频采集缓冲区 (100ms)
            val interval = 0.1
            val frameSize = (interval * SAMPLE_RATE).toInt()
            val buffer = ShortArray(frameSize)
            
            // 持续采集直到停止标志
            var totalSamplesRead = 0
            while (isRecording.get()) {
                val ret = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                
                when {
                    ret > 0 -> {
                        // 转换为Float并归一化
                        val samples = FloatArray(ret) { i ->
                            buffer[i].toFloat() / 32768.0f
                        }
                        
                        // 添加音频数据诊断 (前几次或定期)
                        totalSamplesRead++
                        if (totalSamplesRead <= 3 || totalSamplesRead % 50 == 0) {
                            val maxSample = samples.maxOrNull() ?: 0f
                            val minSample = samples.minOrNull() ?: 0f
                            val avgSample = samples.average()
                            Log.v(TAG, "📊 音频数据#$totalSamplesRead: size=$ret, max=${"%.4f".format(maxSample)}, min=${"%.4f".format(minSample)}, avg=${"%.6f".format(avgSample)}")
                        }
                        
                        samplesChannel.send(samples)
                    }
                    ret == 0 -> {
                        delay(1)
                    }
                    ret < 0 -> {
                        Log.e(TAG, "❌ 音频读取错误: $ret")
                        break
                    }
                }
            }
            
            // 发送结束信号
            samplesChannel.send(FloatArray(0))
            Log.d(TAG, "🏁 音频采集结束")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 音频采集异常", e)
        } finally {
            // 清理AudioRecord
            cleanupAudioRecord()
        }
    }
    
    /**
     * 清理AudioRecord资源
     */
    private fun cleanupAudioRecord() {
        audioRecord?.let {
            try {
                if (it.state == AudioRecord.STATE_INITIALIZED) {
                    it.stop()
                }
                it.release()
            } catch (e: Exception) {
                Log.w(TAG, "清理AudioRecord失败", e)
            }
        }
        audioRecord = null
        Log.d(TAG, "✅ AudioRecord已清理")
    }
    
    /**
     * 音频处理协程 - 参考官方demo
     * 运行在 Default Dispatcher
     */
    private suspend fun processAudio() = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "🔄 启动音频处理")
            
            val startTime = System.currentTimeMillis()
            
            while (isRecording.get()) {
                for (samples in samplesChannel) {
                    // 检查结束信号
                    if (samples.isEmpty()) {
                        Log.d(TAG, "📥 收到结束信号")
                        break
                    }
                    
                    // 处理新的音频样本
                    val hasSpeech = processNewSamples(samples)
                    
                    // 检查最大录制时长
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed > MAX_RECORDING_DURATION_MS) {
                        Log.d(TAG, "⏰ 达到最大录制时间")
                        break
                    }
                    
                    // 实时识别（只有检测到语音后才执行）
                    if (isSpeechDetected) {
                        performPartialRecognition()
                    }
                    
                    // 检查静音超时
                    if (isSpeechDetected && !hasSpeech) {
                        val currentTime = System.currentTimeMillis()
                        val silenceDuration = currentTime - lastSpeechTime
                        if (silenceDuration > SPEECH_TIMEOUT_MS) {
                            Log.d(TAG, "🔇 检测到静音超时 (${silenceDuration}ms)")
                            break
                        }
                    }
                }
                
                // 退出for循环，说明需要停止
                break
            }
            
            Log.d(TAG, "🎯 开始最终识别")
            performFinalRecognition()
            
        } catch (e: CancellationException) {
            Log.d(TAG, "🛑 音频处理被取消")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ 音频处理异常", e)
            withContext(Dispatchers.Main) {
                eventListener?.invoke(InputEvent.Error(e))
            }
        } finally {
            isRecording.set(false)
            _uiState.value = SttState.Loaded
            Log.d(TAG, "🏁 音频处理结束")
        }
    }
    
    /**
     * 处理新的音频样本并进行VAD检测
     * 返回true表示当前帧包含语音
     */
    private fun processNewSamples(samples: FloatArray): Boolean {
        var hasSpeech = false
        val currentTime = System.currentTimeMillis()
        
        // 将新样本添加到VAD缓冲区
        for (sample in samples) {
            vadBuffer.addLast(sample)
            
            // 如果已经检测到语音，也添加到语音缓冲区
            if (isSpeechDetected) {
                speechBuffer.add(sample)
            }
        }
        
        // 当VAD缓冲区达到窗口大小时进行检测
        while (vadBuffer.size >= VAD_WINDOW_SIZE) {
            // 取出一个窗口的数据进行VAD检测
            val vadWindow = FloatArray(VAD_WINDOW_SIZE) { i -> vadBuffer.elementAt(i) }
            
            // 使用VAD或能量检测判断是否有语音
            val speechDetected = if (vad != null) {
                vad!!.acceptWaveform(vadWindow)
                vad!!.isSpeechDetected()
            } else {
                detectSpeechByEnergy(vadWindow)
            }
            
            if (speechDetected) {
                hasSpeech = true
                lastSpeechTime = currentTime
                
                // 如果之前未检测到语音，现在检测到了
                if (!isSpeechDetected) {
                    isSpeechDetected = true
                    speechStartTime = currentTime
                    // 将VAD缓冲区中的所有数据也加入到语音缓冲区（包括语音开始前的一小段）
                    for (sample in vadBuffer) {
                        speechBuffer.add(sample)
                    }
                    DebugLogger.logRecognition(TAG, "🎙️ 检测到语音开始")
                }
            }
            
            // 移除已处理的样本（滑动窗口，步长为窗口大小的1/4以提高检测灵敏度）
            repeat(VAD_WINDOW_SIZE / 4) {
                if (vadBuffer.isNotEmpty()) {
                    vadBuffer.removeFirst()
                }
            }
        }
        
        return hasSpeech
    }
    
    /**
     * 简单能量检测 (VAD降级方案)
     * 提高阈值以减少误报
     */
    private fun detectSpeechByEnergy(samples: FloatArray): Boolean {
        if (samples.isEmpty()) return false
        
        var sum = 0.0
        for (sample in samples) {
            sum += (sample * sample).toDouble()
        }
        val rms = kotlin.math.sqrt(sum / samples.size)
        
        // 提高阈值到0.01，避免太多噪音被误检测为语音
        // 0.003太低了，会把背景噪音也当作语音
        val threshold = 0.01
        val detected = rms > threshold
        
        // 添加调试日志 - 帮助诊断问题
        if (detected && !isSpeechDetected) {
            DebugLogger.logRecognition(TAG, "🔊 能量检测触发: RMS=${"%.6f".format(rms)} > threshold=${"%.6f".format(threshold)}")
        } else if (System.currentTimeMillis() - lastEnergyLogTime > 2000) {
            // 每2秒记录一次能量值，避免日志过多
            Log.v(TAG, "🔊 音频能量: RMS=${"%.6f".format(rms)}, 阈值=${"%.6f".format(threshold)}, 已检测=$isSpeechDetected")
            lastEnergyLogTime = System.currentTimeMillis()
        }
        
        return detected
    }
    
    /**
     * 实时部分识别 - 参考官方demo
     */
    private suspend fun performPartialRecognition() {
        if (!isSpeechDetected) {
            Log.v(TAG, "⏭️ 跳过实时识别 - 未检测到语音")
            return
        }
        
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastRecognitionTime
        
        // 每200ms执行一次识别，且语音数据要足够长（至少0.5秒）
        if (elapsed >= RECOGNITION_INTERVAL_MS && speechBuffer.size >= SAMPLE_RATE / 2) {
            val recognizer = senseVoiceRecognizer ?: return
            
            DebugLogger.logRecognition(TAG, "🔄 开始实时识别 (${speechBuffer.size}样本)")
            
            // 使用累积的语音数据进行识别
            val audioData = speechBuffer.toFloatArray()
            val text = recognizer.recognize(audioData)
            
            lastText = text
            lastRecognitionTime = currentTime
            
            if (text.isNotBlank()) {
                // 参考demo的结果管理
                withContext(Dispatchers.Main) {
                    if (!added) {
                        eventListener?.invoke(InputEvent.Partial(text))
                        added = true
                        DebugLogger.logRecognition(TAG, "📤 首次发送: $text")
                    } else {
                        eventListener?.invoke(InputEvent.Partial(text))
                        DebugLogger.logRecognition(TAG, "📤 更新结果: $text")
                    }
                }
            }
        }
    }
    
    /**
     * 最终识别 - 使用 NonCancellable 确保不被中断
     */
    private suspend fun performFinalRecognition() = withContext(NonCancellable) {
        try {
            val recognizer = senseVoiceRecognizer ?: return@withContext
            
            // 检查是否有有效音频
            if (speechBuffer.isEmpty() || !isSpeechDetected) {
                Log.d(TAG, "⚠️ 没有有效语音数据")
                withContext(Dispatchers.Main) {
                    eventListener?.invoke(InputEvent.None)
                }
                return@withContext
            }
            
            // 检查时长
            val duration = System.currentTimeMillis() - speechStartTime
            if (duration < MIN_SPEECH_DURATION_MS) {
                Log.d(TAG, "⚠️ 语音时长太短: ${duration}ms")
                withContext(Dispatchers.Main) {
                    eventListener?.invoke(InputEvent.None)
                }
                return@withContext
            }
            
            val audioDurationSec = speechBuffer.size.toFloat() / SAMPLE_RATE
            Log.d(TAG, "🚀 执行最终识别，音频: ${speechBuffer.size}样本 (${String.format("%.2f", audioDurationSec)}秒)")
            
            // 执行识别
            val audioData = speechBuffer.toFloatArray()
            val text = recognizer.recognize(audioData)
            
            DebugLogger.logRecognition(TAG, "✅ 最终结果: \"$text\"")
            
            // 发送结果
            withContext(Dispatchers.Main) {
                if (text.isNotBlank()) {
                    // 参考demo：如果有部分结果，更新它；否则添加新结果
                    if (lastText.isNotBlank() && added) {
                        eventListener?.invoke(InputEvent.Final(listOf(Pair(text, 1.0f))))
                    } else {
                        eventListener?.invoke(InputEvent.Final(listOf(Pair(text, 1.0f))))
                    }
                    added = false
                } else {
                    eventListener?.invoke(InputEvent.None)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 最终识别异常", e)
            withContext(Dispatchers.Main) {
                eventListener?.invoke(InputEvent.Error(e))
            }
        }
    }
    
    /**
     * 获取设备信息
     */
    fun getDeviceInfo(): String {
        val recognizerInfo = senseVoiceRecognizer?.getInfo() ?: "未初始化"
        val bufferSize = speechBuffer.size
        val vadBufferSize = vadBuffer.size
        val isActive = isRecording.get()
        return "SenseVoiceDevice($recognizerInfo, 语音缓冲:${bufferSize}样本, VAD缓冲:${vadBufferSize}样本, 活跃:$isActive)"
    }
}
