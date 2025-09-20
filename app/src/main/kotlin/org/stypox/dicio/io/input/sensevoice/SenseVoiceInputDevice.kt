/*
 * Dicio SenseVoice Input Device
 * 基于SenseVoice多语言ASR的语音输入设备实现
 */

package org.stypox.dicio.io.input.sensevoice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.Vad
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * SenseVoice语音输入设备
 * 直接使用SenseVoice进行语音识别，不依赖Vosk
 */
class SenseVoiceInputDevice(
    @ApplicationContext private val appContext: Context,
    private val localeManager: LocaleManager,
) : SttInputDevice {

    companion object {
        private const val TAG = "SenseVoiceInputDevice"
        
        // 音频录制配置 (参考demo的配置)
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC
        
        // VAD和录制控制参数
        private const val VAD_FRAME_SIZE = 512 // VAD处理帧大小 (32ms @ 16kHz)
        private const val SPEECH_TIMEOUT_MS = 3000L // 静音3秒后自动停止
        private const val MAX_RECORDING_DURATION_MS = 30000L // 最长录制时间30秒
        private const val MIN_SPEECH_DURATION_MS = 500L // 最短有效语音时间
    }

    // SenseVoice识别器和VAD
    private var senseVoiceRecognizer: SenseVoiceRecognizer? = null
    private var vad: Vad? = null
    
    // UI状态管理
    private val _uiState = MutableStateFlow<SttState>(SttState.NotInitialized)
    override val uiState: StateFlow<SttState> = _uiState.asStateFlow()
    
    // 控制标志
    private val isInitialized = AtomicBoolean(false)
    private val isListening = AtomicBoolean(false)
    private val isRecording = AtomicBoolean(false)
    
    // 音频录制相关
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var vadJob: Job? = null
    private var eventListener: ((InputEvent) -> Unit)? = null
    private var samplesChannel = Channel<FloatArray>(capacity = Channel.UNLIMITED)
    
    // VAD和语音检测状态
    private var speechDetected = false
    private var speechStartTime = 0L
    private var lastSpeechTime = 0L
    private var audioBuffer = arrayListOf<Float>()
    private var partialText = ""
    
    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    init {
        Log.d(TAG, "🎤 SenseVoice输入设备正在初始化...")
        
        // 异步初始化SenseVoice和VAD
        scope.launch {
            initializeComponents()
        }
    }
    
    /**
     * 初始化SenseVoice识别器和VAD
     */
    private suspend fun initializeComponents() {
        Log.d(TAG, "🔧 开始初始化SenseVoice和VAD组件...")
        _uiState.value = SttState.Loading(thenStartListening = false)
        
        try {
            // 检查SenseVoice模型可用性
            if (!SenseVoiceModelManager.isModelAvailable(appContext)) {
                Log.e(TAG, "❌ SenseVoice模型不可用")
                _uiState.value = SttState.ErrorLoading(Exception("SenseVoice模型不可用，请检查模型文件"))
                return
            }
            
            // 检查VAD模型可用性
            if (!VadModelManager.isVadModelAvailable(appContext)) {
                Log.w(TAG, "⚠️ VAD模型不可用，将使用简单能量检测")
            }
            
            // 创建SenseVoice识别器
            senseVoiceRecognizer = SenseVoiceRecognizer.create(appContext)
            if (senseVoiceRecognizer == null) {
                Log.e(TAG, "❌ SenseVoice识别器创建失败")
                _uiState.value = SttState.ErrorLoading(Exception("SenseVoice识别器创建失败"))
                return
            }
            
            // 暂时禁用VAD，避免模型兼容性问题导致崩溃
            Log.w(TAG, "⚠️ VAD暂时禁用，使用能量检测代替")
            vad = null
            
            /*
            // 创建VAD (如果可用)
            val vadConfig = VadModelManager.createVadConfig(appContext)
            if (vadConfig != null) {
                try {
                    val vadModelPaths = VadModelManager.getVadModelPaths(appContext)
                    vad = if (vadModelPaths?.isFromAssets == true) {
                        Vad(assetManager = appContext.assets, config = vadConfig)
                    } else {
                        Vad(config = vadConfig)
                    }
                    Log.d(TAG, "✅ VAD初始化成功")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ VAD初始化失败，将使用简单能量检测", e)
                    vad = null
                }
            }
            */
            
            Log.d(TAG, "✅ SenseVoice识别器初始化成功")
            isInitialized.set(true)
            _uiState.value = SttState.Loaded
            
            val senseVoiceInfo = SenseVoiceModelManager.getModelInfo(appContext)
            val vadInfo = VadModelManager.getVadModelInfo(appContext)
            Log.d(TAG, "📊 $senseVoiceInfo")
            Log.d(TAG, "📊 $vadInfo")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化组件异常", e)
            _uiState.value = SttState.ErrorLoading(e)
        }
    }
    
    override fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean {
        Log.d(TAG, "🚀 尝试加载并开始监听...")
        
        if (!isInitialized.get()) {
            Log.w(TAG, "⚠️ SenseVoice未初始化，无法开始监听")
            return false
        }
        
        if (isListening.get()) {
            Log.w(TAG, "⚠️ 已在监听中，停止当前监听")
            stopListening()
        }
        
        this.eventListener = thenStartListeningEventListener
        
        // 开始录制和识别
        return startListening()
    }
    
    override fun onClick(eventListener: (InputEvent) -> Unit) {
        Log.d(TAG, "🖱️ 点击开始语音输入...")
        
        if (isListening.get()) {
            // 如果正在监听，停止监听
            stopListening()
        } else {
            // 开始监听
            tryLoad(eventListener)
        }
    }
    
    override fun stopListening() {
        if (!isListening.get()) {
            return
        }
        
        Log.d(TAG, "🛑 停止语音监听...")
        isListening.set(false)
        
        // 停止录制
        stopRecording()
        
        // 取消VAD任务
        vadJob?.cancel()
        vadJob = null
        
        _uiState.value = SttState.Loaded
    }
    
    override suspend fun destroy() {
        Log.d(TAG, "🧹 销毁SenseVoice输入设备...")
        
        try {
            // 停止所有活动
            stopListening()
            
            // 关闭音频通道
            samplesChannel.close()
            
            // 释放SenseVoice识别器
            senseVoiceRecognizer?.release()
            senseVoiceRecognizer = null
            
            // 释放VAD资源
            try {
                vad?.release()
                vad = null
                Log.d(TAG, "✅ VAD资源已释放")
            } catch (e: Exception) {
                Log.w(TAG, "释放VAD资源失败", e)
            }
            
            // 取消协程作用域
            scope.cancel()
            
            // 重置所有状态
            resetVadState()
            
            // 清空事件监听器引用
            eventListener = null
            
            // 重置状态
            isInitialized.set(false)
            _uiState.value = SttState.NotInitialized
            
            Log.d(TAG, "✅ SenseVoice输入设备资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 销毁SenseVoice输入设备失败", e)
        }
    }
    
    /**
     * 开始监听
     */
    private fun startListening(): Boolean {
        if (!isInitialized.get() || senseVoiceRecognizer == null) {
            Log.e(TAG, "❌ SenseVoice未准备好，无法开始监听")
            return false
        }
        
        Log.d(TAG, "🎙️ 开始语音监听...")
        isListening.set(true)
        
        // 重置VAD和音频状态
        resetVadState()
        
        _uiState.value = SttState.Listening
        
        // 开始录制
        if (startRecording()) {
            // 启动超时监控任务
            vadJob = scope.launch {
                try {
                    delay(MAX_RECORDING_DURATION_MS)
                    // 达到最大录制时间，自动停止
                    Log.d(TAG, "⏰ 达到最大录制时间，自动停止")
                    stopListeningAndProcess()
                } catch (e: CancellationException) {
                    // 正常取消，不需要处理
                }
            }
            return true
        } else {
            Log.e(TAG, "❌ 启动录制失败")
            isListening.set(false)
            _uiState.value = SttState.ErrorLoading(Exception("启动录制失败"))
            return false
        }
    }
    
    /**
     * 开始录制音频 (参考demo的实现)
     */
    private fun startRecording(): Boolean {
        try {
            val bufferSizeInBytes = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            if (bufferSizeInBytes == AudioRecord.ERROR || bufferSizeInBytes == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "❌ 无法获取AudioRecord缓冲区大小")
                return false
            }
            
            audioRecord = AudioRecord(
                AUDIO_SOURCE,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSizeInBytes * 2
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "❌ AudioRecord初始化失败")
                cleanupAudioRecord()
                return false
            }
            
            // 重置状态已在resetVadState()中完成
            
            audioRecord?.startRecording()
            isRecording.set(true)
            
            Log.d(TAG, "🎵 开始录制音频...")
            
            // 启动音频采集协程 (参考demo)
            recordingJob = scope.launch(Dispatchers.IO) {
                recordAudioData()
            }
            
            // 启动音频处理协程
            vadJob = scope.launch(Dispatchers.Default) {
                processAudioForRecognition()
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动录制异常", e)
            cleanupAudioRecord()
            return false
        }
    }
    
    /**
     * 清理AudioRecord资源
     */
    private fun cleanupAudioRecord() {
        try {
            audioRecord?.let { record ->
                try {
                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            record.stop()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "停止AudioRecord时出错", e)
                } finally {
                    try {
                        record.release()
                    } catch (e: Exception) {
                        Log.w(TAG, "释放AudioRecord时出错", e)
                    }
                }
            }
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "❌ 清理AudioRecord资源失败", e)
        }
    }
    
    /**
     * 录制音频数据 (参考demo的实现)
     */
    private suspend fun recordAudioData() {
        Log.d(TAG, "🔄 开始音频数据录制...")
        
        val bufferSize = VAD_FRAME_SIZE // 使用VAD帧大小
        val buffer = ShortArray(bufferSize)
        
        while (isRecording.get() && !Thread.currentThread().isInterrupted) {
            try {
                val readSamples = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                
                if (readSamples > 0) {
                    // 转换为Float数组 (参考demo)
                    val samples = FloatArray(readSamples) { buffer[it] / 32768.0f }
                    samplesChannel.send(samples)
                    
                } else if (readSamples < 0) {
                    Log.e(TAG, "❌ 读取音频数据错误: $readSamples")
                    break
                }
                
                yield()
                
            } catch (e: Exception) {
                if (isRecording.get()) {
                    Log.e(TAG, "❌ 录制音频数据异常", e)
                }
                break
            }
        }
        
        // 发送空数组表示结束
        val samples = FloatArray(0)
        samplesChannel.send(samples)
        
        Log.d(TAG, "🏁 音频数据录制结束")
    }
    
    /**
     * 处理音频进行VAD检测和识别
     */
    private suspend fun processAudioForRecognition() {
        Log.d(TAG, "🧠 开始音频处理和VAD检测...")
        
        while (isListening.get()) {
            for (samples in samplesChannel) {
                if (samples.isEmpty()) {
                    Log.d(TAG, "收到空音频数据，处理结束")
                    break
                }
                
                // 添加到缓冲区
                audioBuffer.addAll(samples.toList())
                
                // VAD检测
                val isSpeech = detectSpeech(samples)
                val currentTime = System.currentTimeMillis()
                
                if (isSpeech) {
                    if (!speechDetected) {
                        // 语音开始
                        speechDetected = true
                        speechStartTime = currentTime
                        Log.d(TAG, "🎤 检测到语音开始")
                        
                        // 发送语音开始事件 (可选)
                        withContext(Dispatchers.Main) {
                            // eventListener?.invoke(InputEvent.Partial(""))
                        }
                    }
                    lastSpeechTime = currentTime
                    
                    // 进行实时识别 (每隔一定时间)
                    if (audioBuffer.size >= SAMPLE_RATE) { // 1秒的音频
                        performPartialRecognition()
                    }
                    
                } else if (speechDetected) {
                    // 检查是否静音超时
                    val silenceDuration = currentTime - lastSpeechTime
                    if (silenceDuration > SPEECH_TIMEOUT_MS) {
                        Log.d(TAG, "🔇 检测到静音超时，停止监听")
                        stopListeningAndProcess()
                        break
                    }
                }
                
                // 检查最大录制时间
                if (speechDetected && (currentTime - speechStartTime) > MAX_RECORDING_DURATION_MS) {
                    Log.d(TAG, "⏰ 达到最大录制时间，停止监听")
                    stopListeningAndProcess()
                    break
                }
            }
        }
        
        Log.d(TAG, "🏁 音频处理结束")
    }
    
    /**
     * VAD语音检测
     */
    private fun detectSpeech(audioSamples: FloatArray): Boolean {
        return if (vad != null) {
            try {
                // 使用SherpaOnnx VAD进行检测
                vad!!.acceptWaveform(audioSamples)
                val isSpeech = vad!!.isSpeechDetected()
                vad!!.clear() // 清除VAD状态，准备下一帧
                isSpeech
            } catch (e: Exception) {
                Log.w(TAG, "VAD检测异常，使用能量检测", e)
                detectSpeechByEnergy(audioSamples)
            }
        } else {
            // 降级到简单能量检测
            detectSpeechByEnergy(audioSamples)
        }
    }
    
    /**
     * 简单的能量检测（VAD降级方案）
     */
    private fun detectSpeechByEnergy(audioSamples: FloatArray): Boolean {
        if (audioSamples.isEmpty()) return false
        
        // 计算RMS能量
        var sum = 0.0
        for (sample in audioSamples) {
            sum += (sample * sample).toDouble()
        }
        val rms = kotlin.math.sqrt(sum / audioSamples.size)
        
        // 简单的阈值检测
        return rms > 0.01 // 可调整的阈值
    }
    
    /**
     * 执行部分识别（实时反馈）
     */
    private suspend fun performPartialRecognition() {
        try {
            val recognizer = senseVoiceRecognizer ?: return
            
            if (audioBuffer.size < SAMPLE_RATE / 2) { // 至少0.5秒的音频
                return
            }
            
            // 使用最近的音频进行识别
            val audioData = audioBuffer.takeLast(SAMPLE_RATE * 2).toFloatArray() // 最近2秒
            val newText = recognizer.recognize(audioData)
            
            if (newText.isNotBlank() && newText != partialText) {
                partialText = newText
                
                // 发送部分识别结果
                withContext(Dispatchers.Main) {
                    eventListener?.invoke(InputEvent.Partial(partialText))
                }
                
                DebugLogger.logRecognition(TAG, "部分识别: \"$partialText\"")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 部分识别异常", e)
        }
    }
    
    /**
     * 停止监听并处理最终结果
     */
    private suspend fun stopListeningAndProcess() {
        isListening.set(false)
        stopRecording()
        
        // 处理最终识别结果
        performFinalRecognition()
    }
    
    /**
     * 执行最终识别 (使用SenseVoice的方式)
     */
    private suspend fun performFinalRecognition() {
        try {
            val recognizer = senseVoiceRecognizer ?: return
            
            // 检查是否有足够的语音数据
            if (audioBuffer.isEmpty() || !speechDetected) {
                withContext(Dispatchers.Main) {
                    eventListener?.invoke(InputEvent.None)
                }
                _uiState.value = SttState.Loaded
                return
            }
            
            // 检查语音时长是否足够
            val speechDuration = System.currentTimeMillis() - speechStartTime
            if (speechDuration < MIN_SPEECH_DURATION_MS) {
                Log.d(TAG, "语音时长太短 (${speechDuration}ms)，忽略")
                withContext(Dispatchers.Main) {
                    eventListener?.invoke(InputEvent.None)
                }
                _uiState.value = SttState.Loaded
                return
            }
            
            Log.d(TAG, "🚀 开始最终识别，音频长度: ${audioBuffer.size}样本，语音时长: ${speechDuration}ms")
            
            // 使用SenseVoice的recognize方法进行最终识别
            val audioData = audioBuffer.toFloatArray()
            val finalText = recognizer.recognize(audioData)
            
            DebugLogger.logRecognition(TAG, "最终识别结果: \"$finalText\"")
            
            withContext(Dispatchers.Main) {
                if (finalText.isNotBlank()) {
                    eventListener?.invoke(InputEvent.Final(listOf(Pair(finalText, 1.0f))))
                } else {
                    eventListener?.invoke(InputEvent.None)
                }
            }
            
            _uiState.value = SttState.Loaded
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 最终识别异常", e)
            withContext(Dispatchers.Main) {
                eventListener?.invoke(InputEvent.Error(e))
            }
            _uiState.value = SttState.ErrorLoading(e)
        } finally {
            // 重置状态
            resetVadState()
        }
    }
    
    /**
     * 重置VAD和语音检测状态
     */
    private fun resetVadState() {
        speechDetected = false
        speechStartTime = 0L
        lastSpeechTime = 0L
        audioBuffer.clear()
        partialText = ""
        
        // 重置VAD状态
        try {
            vad?.reset()
        } catch (e: Exception) {
            Log.w(TAG, "重置VAD状态失败", e)
        }
    }
    
    /**
     * 停止录制音频
     */
    private fun stopRecording() {
        if (!isRecording.get()) {
            return
        }
        
        Log.d(TAG, "🔇 停止录制音频...")
        isRecording.set(false)
        
        // 取消录制协程
        recordingJob?.cancel()
        recordingJob = null
        
        cleanupAudioRecord()
    }
    
    /**
     * 获取设备信息
     */
    fun getDeviceInfo(): String {
        val recognizerInfo = senseVoiceRecognizer?.getInfo() ?: "未初始化"
        val bufferSize = audioBuffer.size
        val isActive = isListening.get()
        return "SenseVoiceDevice($recognizerInfo, 缓冲区:${bufferSize}样本, 活跃:$isActive)"
    }
}
