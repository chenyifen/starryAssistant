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
 * SenseVoice语音输入设备 - 单例模式
 * 直接使用SenseVoice进行语音识别，不依赖Vosk
 * 使用单例模式避免多实例冲突
 */
class SenseVoiceInputDevice private constructor(
    private val appContext: Context,
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
        private const val SPEECH_TIMEOUT_MS = 4000L // 静音8秒后自动停止，给用户更多思考时间
        private const val MAX_RECORDING_DURATION_MS = 30000L // 最长录制时间30秒
        private const val MIN_SPEECH_DURATION_MS = 500L // 最短有效语音时间

        // 单例实例
        @Volatile
        private var INSTANCE: SenseVoiceInputDevice? = null
        

        /**
         * 获取单例实例
         */
        fun getInstance(appContext: Context, localeManager: LocaleManager): SenseVoiceInputDevice {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SenseVoiceInputDevice(appContext, localeManager).also { 
                    INSTANCE = it
                    Log.d(TAG, "🏗️ 创建SenseVoiceInputDevice单例实例")
                }
            }
        }

        /**
         * 重置单例实例（用于测试或重新初始化）
         */
        fun resetInstance() {
            synchronized(this) {
                INSTANCE?.let { instance ->
                    Log.d(TAG, "🔄 重置SenseVoiceInputDevice单例实例")
                    // 清理当前实例 - 使用协程
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                        instance.destroy()
                    }
                }
                INSTANCE = null
            }
        }
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
    // 参考SherpaOnnxSimulateAsr使用ArrayList进行高效缓冲管理
    private val audioBuffer = arrayListOf<Float>()
    private var bufferOffset = 0
    private val maxBufferSize = SAMPLE_RATE * 10 // 最多存储10秒音频
    private var partialText = ""
    private var lastPartialRecognitionTime = 0L
    private val PARTIAL_RECOGNITION_COOLDOWN_MS = 200L // 参考demo改为200ms触发间隔
    private var isPartialResultAdded = false // 参考demo的结果管理策略
    
    // 协程作用域 - 使用可重新创建的作用域
    private var scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
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
        
        // 确保协程作用域可用
        if (!scope.isActive) {
            recreateScope()
        }
        
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
    
    /**
     * 强制停止录制（单例模式下的停止方法）
     */
    fun forceStop() {
        Log.w(TAG, "⚠️ 单例实例被强制停止")
        isListening.set(false)
        isRecording.set(false)
        cleanupAudioRecord()
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
            
            // 不取消协程作用域，保持单例可重用
            // scope.cancel() // 注释掉，单例模式下保持作用域活跃
            
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
     * 重新创建协程作用域（用于单例重用）
     */
    private fun recreateScope() {
        if (scope.isActive) {
            scope.cancel()
        }
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        Log.d(TAG, "🔄 重新创建协程作用域")
    }

    /**
     * 开始监听
     */
    private fun startListening(): Boolean {
        if (!isInitialized.get() || senseVoiceRecognizer == null) {
            Log.e(TAG, "❌ SenseVoice未准备好，无法开始监听")
            return false
        }
        
        // 防止重复启动
        if (isListening.get()) {
            Log.w(TAG, "⚠️ 已在监听中，忽略重复启动请求")
            return true
        }
        
        // 确保协程作用域可用
        if (!scope.isActive) {
            recreateScope()
        }
        
        Log.d(TAG, "🎙️ 开始语音监听...")
        isListening.set(true)
        
        // 重置VAD和音频状态
        resetVadState()
        
        _uiState.value = SttState.Listening
        
        // 开始录制 (使用协程)
        scope.launch {
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
            } else {
                Log.e(TAG, "❌ 启动录制失败")
                isListening.set(false)
                _uiState.value = SttState.ErrorLoading(Exception("启动录制失败"))
            }
        }
        return true
    }
    
    /**
     * 开始录制音频 (修复缓冲区管理问题和并发访问)
     */
    private suspend fun startRecording(): Boolean {
        try {
            // 防止同一实例重复启动录制
            if (isRecording.get()) {
                Log.w(TAG, "⚠️ 实例 ${this.hashCode()} 已在录制中，忽略重复启动")
                return true
            }
            
            // 单例模式下不需要资源锁
            Log.d(TAG, "🎵 单例实例开始录制音频...")
            
            // 确保先清理之前的资源
            cleanupAudioRecord()
            
            val minBufferSizeInBytes = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            if (minBufferSizeInBytes == AudioRecord.ERROR || minBufferSizeInBytes == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "❌ 无法获取AudioRecord缓冲区大小")
                return false
            }
            
            // 使用更大的缓冲区以避免缓冲区溢出，至少是最小缓冲区的4倍
            val actualBufferSize = maxOf(minBufferSizeInBytes * 4, VAD_FRAME_SIZE * 2 * 4) // 4倍安全边界
            
            Log.d(TAG, "🔧 实例 ${this.hashCode()} 音频缓冲区配置: 最小=${minBufferSizeInBytes}字节, 实际=${actualBufferSize}字节")
            
            audioRecord = AudioRecord(
                AUDIO_SOURCE,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                actualBufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "❌ AudioRecord初始化失败，状态: ${audioRecord?.state}")
                cleanupAudioRecord()
                return false
            }
            
            // 检查录制状态
            if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_STOPPED) {
                Log.w(TAG, "⚠️ AudioRecord不在停止状态: ${audioRecord?.recordingState}")
            }
            
            // 开始录制
            audioRecord?.startRecording()
            
            // 验证录制状态
            if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "❌ AudioRecord启动录制失败，状态: ${audioRecord?.recordingState}")
                cleanupAudioRecord()
                return false
            }
            
            isRecording.set(true)
            
            Log.d(TAG, "🎵 实例 ${this.hashCode()} 开始录制音频，缓冲区大小: ${actualBufferSize}字节")
            
            // 启动音频采集协程 (使用IO调度器)
            recordingJob = scope.launch(Dispatchers.IO) {
                recordAudioData()
            }
            
            // 启动音频处理协程 (使用Default调度器)
            vadJob = scope.launch(Dispatchers.Default) {
                processAudioForRecognition()
            }
            
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ 录音权限不足", e)
            cleanupAudioRecord()
            return false
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动录制异常", e)
            cleanupAudioRecord()
            return false
        }
    }
    
    /**
     * 清理AudioRecord资源 (修复资源泄漏和状态管理)
     */
    private fun cleanupAudioRecord() {
        try {
            // 先停止录制标志
            isRecording.set(false)
            
            // 取消录制协程
            recordingJob?.cancel()
            recordingJob = null
            
            // 取消VAD协程
            vadJob?.cancel()
            vadJob = null
            
            // 关闭样本通道
            try {
                samplesChannel.close()
                // 重新创建通道以供下次使用
                samplesChannel = Channel(capacity = Channel.UNLIMITED)
            } catch (e: Exception) {
                Log.w(TAG, "关闭样本通道失败", e)
            }
            
            // 清理AudioRecord
            audioRecord?.let { record ->
                try {
                    // 检查并停止录制
                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        when (record.recordingState) {
                            AudioRecord.RECORDSTATE_RECORDING -> {
                                Log.d(TAG, "🛑 实例 ${this.hashCode()} 停止AudioRecord录制")
                                record.stop()
                                
                                // 等待停止完成
                                var attempts = 0
                                while (record.recordingState == AudioRecord.RECORDSTATE_RECORDING && attempts < 10) {
                                    Thread.sleep(10)
                                    attempts++
                                }
                                
                                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                                    Log.w(TAG, "⚠️ AudioRecord停止超时")
                                }
                            }
                            AudioRecord.RECORDSTATE_STOPPED -> {
                                Log.d(TAG, "✅ AudioRecord已停止")
                            }
                            else -> {
                                Log.w(TAG, "⚠️ AudioRecord状态异常: ${record.recordingState}")
                            }
                        }
                    } else {
                        Log.w(TAG, "⚠️ AudioRecord状态不是INITIALIZED: ${record.state}")
                    }
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "停止AudioRecord时状态异常", e)
                } catch (e: Exception) {
                    Log.w(TAG, "停止AudioRecord时出错", e)
                }
                
                // 释放资源
                try {
                    Log.d(TAG, "🗑️ 实例 ${this.hashCode()} 释放AudioRecord资源")
                    record.release()
                } catch (e: Exception) {
                    Log.w(TAG, "释放AudioRecord时出错", e)
                }
            }
            
            audioRecord = null
            
            Log.d(TAG, "✅ 单例实例 AudioRecord资源清理完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 清理AudioRecord资源失败", e)
        }
    }
    
    /**
     * 录制音频数据 (修复缓冲区管理和并发问题)
     */
    private suspend fun recordAudioData() {
        Log.d(TAG, "🔄 开始音频数据录制...")
        
        // 使用合适的缓冲区大小，确保不超过AudioRecord的缓冲区
        val bufferSize = VAD_FRAME_SIZE // 512 samples = 1024 bytes
        val buffer = ShortArray(bufferSize)
        var consecutiveErrors = 0
        val maxConsecutiveErrors = 5
        
        try {
            while (isRecording.get() && !Thread.currentThread().isInterrupted && !currentCoroutineContext().job.isCancelled) {
                try {
                    val currentAudioRecord = audioRecord
                    if (currentAudioRecord == null) {
                        Log.w(TAG, "⚠️ AudioRecord为null，停止录制")
                        break
                    }
                    
                    // 检查AudioRecord状态
                    if (currentAudioRecord.state != AudioRecord.STATE_INITIALIZED) {
                        Log.e(TAG, "❌ AudioRecord状态异常: ${currentAudioRecord.state}")
                        break
                    }
                    
                    if (currentAudioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        Log.e(TAG, "❌ AudioRecord录制状态异常: ${currentAudioRecord.recordingState}")
                        break
                    }
                    
                    // 读取音频数据，使用同步方式避免缓冲区问题
                    val readSamples = currentAudioRecord.read(buffer, 0, buffer.size)
                    
                    when {
                        readSamples > 0 -> {
                            // 成功读取数据，重置错误计数
                            consecutiveErrors = 0
                            
                            // 转换为Float数组 (归一化到 -1.0 到 1.0)
                            val samples = FloatArray(readSamples) { i -> 
                                buffer[i].toFloat() / 32768.0f 
                            }
                            
                            // 发送到处理通道
                            if (!samplesChannel.isClosedForSend) {
                                samplesChannel.send(samples)
                            } else {
                                Log.w(TAG, "⚠️ 样本通道已关闭")
                                break
                            }
                        }
                        
                        readSamples == 0 -> {
                            // 没有数据可读，稍微等待
                            delay(1)
                        }
                        
                        readSamples == AudioRecord.ERROR_INVALID_OPERATION -> {
                            Log.e(TAG, "❌ AudioRecord无效操作错误")
                            consecutiveErrors++
                        }
                        
                        readSamples == AudioRecord.ERROR_BAD_VALUE -> {
                            Log.e(TAG, "❌ AudioRecord参数错误")
                            consecutiveErrors++
                        }
                        
                        readSamples == AudioRecord.ERROR_DEAD_OBJECT -> {
                            Log.e(TAG, "❌ AudioRecord对象已死亡")
                            break
                        }
                        
                        readSamples < 0 -> {
                            Log.e(TAG, "❌ AudioRecord读取错误: $readSamples")
                            consecutiveErrors++
                        }
                    }
                    
                    // 如果连续错误太多，停止录制
                    if (consecutiveErrors >= maxConsecutiveErrors) {
                        Log.e(TAG, "❌ 连续错误过多($consecutiveErrors)，停止录制")
                        // 确保状态正确重置
                        isListening.set(false)
                        isRecording.set(false)
                        _uiState.value = SttState.ErrorLoading(Exception("连续音频错误过多"))
                        break
                    }
                    
                    // 让出CPU时间
                    yield()
                    
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "❌ AudioRecord状态异常", e)
                    // 确保状态正确重置
                    isListening.set(false)
                    isRecording.set(false)
                    _uiState.value = SttState.ErrorLoading(e)
                    break
                } catch (e: kotlinx.coroutines.CancellationException) {
                    Log.d(TAG, "🛑 录制协程被取消")
                    // 确保状态正确重置
                    isListening.set(false)
                    isRecording.set(false)
                    _uiState.value = SttState.Loaded
                    throw e // 重新抛出取消异常
                } catch (e: Exception) {
                    if (isRecording.get()) {
                        Log.e(TAG, "❌ 录制音频数据异常", e)
                        consecutiveErrors++
                        if (consecutiveErrors >= maxConsecutiveErrors) {
                            // 确保状态正确重置
                            isListening.set(false)
                            isRecording.set(false)
                            _uiState.value = SttState.ErrorLoading(Exception("连续音频异常过多"))
                            break
                        }
                    } else {
                        // 正常停止，不记录错误
                        break
                    }
                }
            }
        } finally {
            // 发送空数组表示结束
            try {
                if (!samplesChannel.isClosedForSend) {
                    samplesChannel.send(FloatArray(0))
                }
            } catch (e: Exception) {
                Log.w(TAG, "发送结束信号失败", e)
            }
            
            Log.d(TAG, "🏁 音频数据录制结束")
        }
    }
    
    /**
     * 处理音频进行VAD检测和识别
     */
    private suspend fun processAudioForRecognition() {
        Log.d(TAG, "🧠 开始音频处理和VAD检测...")
        
        try {
            while (isListening.get()) {
                for (samples in samplesChannel) {
                    if (samples.isEmpty()) {
                        Log.d(TAG, "收到空音频数据，处理结束")
                        break
                    }
                    
                    // 参考SherpaOnnxSimulateAsr的高效缓冲管理
                    synchronized(audioBuffer) {
                        audioBuffer.addAll(samples.toList())
                        // 如果缓冲区太大，移除旧数据
                        while (audioBuffer.size > maxBufferSize) {
                            audioBuffer.removeAt(0)
                            if (bufferOffset > 0) bufferOffset--
                        }
                    }
                    
                    // VAD检测
                    val isSpeech = detectSpeech(samples)
                    val currentTime = System.currentTimeMillis()
                    
                    if (isSpeech) {
                        if (!speechDetected) {
                            // 语音开始
                            speechDetected = true
                            speechStartTime = currentTime
                            Log.d(TAG, "🎤 检测到语音开始")
                            
                            // 不发送状态文本，避免干扰真实的ASR结果显示
                            // 语音开始事件由UI状态管理器处理
                        }
                        lastSpeechTime = currentTime
                        
                        // 参考SherpaOnnxSimulateAsr每200ms进行实时识别
                        val elapsed = currentTime - lastPartialRecognitionTime
                        if (elapsed > PARTIAL_RECOGNITION_COOLDOWN_MS && audioBuffer.size >= SAMPLE_RATE / 2) {
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d(TAG, "🛑 音频处理协程被取消")
            // 正常的协程取消，不需要记录为错误
            throw e // 重新抛出取消异常
        } catch (e: Exception) {
            Log.e(TAG, "❌ 音频处理异常", e)
            // 设置错误状态
            _uiState.value = SttState.ErrorLoading(e)
        } finally {
            Log.d(TAG, "🏁 音频处理结束")
        }
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
     * 执行部分识别（实时反馈）- 参考SherpaOnnxSimulateAsr优化
     */
    private suspend fun performPartialRecognition() {
        try {
            val recognizer = senseVoiceRecognizer ?: return
            
            val currentTime = System.currentTimeMillis()
            lastPartialRecognitionTime = currentTime
            
            // 参考SherpaOnnxSimulateAsr的缓冲管理方式
            val audioData = synchronized(audioBuffer) {
                if (audioBuffer.size < SAMPLE_RATE / 4) return // 至少0.25秒音频
                audioBuffer.toFloatArray()
            }
            val newText = recognizer.recognize(audioData)
            
            if (newText.isNotBlank() && newText != partialText) {
                val oldText = partialText
                partialText = newText
                
                // 参考SherpaOnnxSimulateAsr的结果管理策略
                withContext(Dispatchers.Main) {
                    if (!isPartialResultAdded) {
                        // 首次添加部分结果
                        eventListener?.invoke(InputEvent.Partial(partialText))
                        isPartialResultAdded = true
                    } else {
                        // 更新现有部分结果
                        eventListener?.invoke(InputEvent.Partial(partialText))
                    }
                }
                
                Log.d(TAG, "🎯 部分识别更新: '$oldText' → '$partialText' (音频长度: ${audioData.size / SAMPLE_RATE.toFloat()}秒)")
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
            
            // 安全地从队列中获取所有音频数据
            val bufferList = audioBuffer.toList()
            val audioData = bufferList.toFloatArray()
            val finalText = recognizer.recognize(audioData)
            
            DebugLogger.logRecognition(TAG, "最终识别结果: \"$finalText\"")
            Log.d(TAG, "🔍 识别结果详情: 长度=${finalText.length}, 是否空白=${finalText.isBlank()}")
            
            withContext(Dispatchers.Main) {
                if (finalText.isNotBlank()) {
                    Log.d(TAG, "✅ 发送Final事件: \"$finalText\"")
                    eventListener?.invoke(InputEvent.Final(listOf(Pair(finalText, 1.0f))))
                } else {
                    Log.d(TAG, "⚠️ 识别结果为空，发送None事件")
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
        synchronized(audioBuffer) {
            audioBuffer.clear()
            bufferOffset = 0
        }
        partialText = ""
        isPartialResultAdded = false // 重置结果管理标志
        
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
