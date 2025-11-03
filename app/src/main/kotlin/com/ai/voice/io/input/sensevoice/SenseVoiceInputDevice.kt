/*
 * Dicio SenseVoice Input Device
 * 基于SenseVoice多语言ASR的语音输入设备实现
 */

package com.ai.voice.io.input.sensevoice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.k2fsa.sherpa.onnx.Vad
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.ai.voice.di.LocaleManager
import com.ai.voice.io.input.InputEvent
import com.ai.voice.io.input.SttInputDevice
import com.ai.voice.io.input.SttState
import com.ai.voice.util.DebugLogger
import com.ai.voice.io.AudioResourceManager
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
        private const val SPEECH_TIMEOUT_MS = 4000L // 🔄 静音4秒后由状态机处理回到待唤醒
        private const val MAX_RECORDING_DURATION_MS = 30000L // 🔥 最长录制时间15秒（给复杂命令更多时间）
        private const val MIN_SPEECH_DURATION_MS = 500L // 最短有效语音时间
        private const val INITIAL_GRACE_PERIOD_MS = 800L // 🆕 唤醒后初始缓冲期，避免唤醒词尾音误触发（增加到800ms）
        
        // 🆕 高分提前结束参数（优化版：更快响应）
        private const val MIN_TEXT_LENGTH_FOR_EARLY_STOP = 3  // 至少3个字才考虑提前结束
        private const val STABLE_COUNT_THRESHOLD = 3          // 🔥 Partial连续3次稳定即可（优化响应速度）
        private const val EARLY_STOP_CONFIRM_DELAY_MS = 200L  // 🔥 提前结束前等待200ms确认（优化响应速度）
        
        // 🆕 韩语模式参数
        private const val KOREAN_MODE_DEFAULT = true  // 默认启用韩语模式

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
    
    // 🔥 互斥锁保护recognizer的并发访问
    private val recognizerMutex = Mutex()
    
    // 音频录制相关
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var vadJob: Job? = null
    private var eventListener: ((InputEvent) -> Unit)? = null
    private var samplesChannel = Channel<FloatArray>(capacity = Channel.UNLIMITED)
    
    // 音频焦点管理（Android 15+ 必需）
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = AtomicBoolean(false)
    
    // VAD和语音检测状态
    private var speechDetected = false
    private var speechStartTime = 0L
    private var lastSpeechTime = 0L
    private var asrStartTime = 0L // 🆕 ASR启动时间，用于初始缓冲期
    // 🚀 使用高效的循环缓冲区，避免ArrayList的装箱开销和频繁GC
    private val audioBuffer = AudioBuffer(sampleRate = SAMPLE_RATE, maxDurationSeconds = 10.0f)
    private var partialText = ""
    private var lastPartialRecognitionTime = 0L
    private val PARTIAL_RECOGNITION_COOLDOWN_MS = 400L // 🔥 400ms触发间隔（平衡响应速度和准确率）
    private val PARTIAL_FIRST_MIN_AUDIO_DURATION_SEC = 0.5f // 🆕 首次识别：0.5秒（快速响应）
    private val PARTIAL_NORMAL_MIN_AUDIO_DURATION_SEC = 0.7f // 🆕 后续识别：0.7秒（提高准确率）
    private var isPartialResultAdded = false // 参考demo的结果管理策略
    private var partialRecognitionCount = 0 // 识别次数计数
    
    // 🆕 高分提前结束优化（方案1）
    private var lastStablePartialText = ""      // 上一次稳定的Partial文本
    private var partialStableCount = 0          // Partial稳定计数
    private var stablePartialConfirmTime = 0L   // 稳定Partial的确认时间
    private var isWaitingForEarlyStop = false   // 是否正在等待提前停止
    // 🆕 标记：静音超时且无识别内容时已发送None事件，避免重复触发
    private var hasEmittedNoneOnSilence = false
    
    // 协程作用域 - 使用可重新创建的作用域
    private var scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // TTS状态通过AudioResourceManager.canRecord()自动处理，无需监听器
    
    init {
        Log.d(TAG, "🎤 SenseVoice输入设备初始化中...")
        
        // 初始化 AudioManager（Android 15 音频焦点必需）
        audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        
        // 异步初始化SenseVoice和VAD
        scope.launch {
            val startTime = System.currentTimeMillis()
            initializeComponents()
            val duration = System.currentTimeMillis() - startTime
            if (isInitialized.get()) {
                Log.d(TAG, "✅ SenseVoice初始化完成，耗时: ${duration}ms")
            } else {
                Log.e(TAG, "❌ SenseVoice初始化失败，耗时: ${duration}ms")
            }
        }
    }
    
    /**
     * 初始化SenseVoice识别器和VAD
     */
    private suspend fun initializeComponents() {
        // 初始化SenseVoice和VAD组件
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
            
            // 🆕 创建SenseVoice识别器（默认启用韩语模式）
            senseVoiceRecognizer = SenseVoiceRecognizer.create(appContext, koreanMode = KOREAN_MODE_DEFAULT)
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
        
        // 🆕 如果未初始化，等待初始化完成（最多10秒，但快速轮询）
        if (!isInitialized.get()) {
            Log.w(TAG, "⚠️ SenseVoice未初始化，等待初始化完成...")
            
            // 使用runBlocking等待初始化，但设置超时
            val initSuccess = try {
                kotlinx.coroutines.runBlocking {
                    kotlinx.coroutines.withTimeoutOrNull(10000L) {
                        // 🔥 快速轮询：每10ms检查一次，最多1000次
                        var attempts = 0
                        while (!isInitialized.get() && attempts < 1000) {
                            kotlinx.coroutines.delay(10L)
                            attempts++
                        }
                        isInitialized.get()
                    } ?: false
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 等待初始化异常: ${e.message}")
                false
            }
            
            if (!initSuccess) {
                Log.e(TAG, "❌ SenseVoice初始化超时或失败，无法开始监听")
                // 触发重新初始化
                scope.launch {
                    Log.d(TAG, "🔄 尝试重新初始化...")
                    initializeComponents()
                }
                return false
            }
            
            Log.d(TAG, "✅ SenseVoice初始化完成，继续开始监听")
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
        
        // 释放麦克风资源
        scope.launch {
            try {
                AudioResourceManager.releaseMicrophone(AudioResourceManager.AudioOwner.ASR_DEVICE)
                Log.d(TAG, "✅ 已释放麦克风资源")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 释放麦克风资源失败", e)
            }
        }
        
        // TTS状态通过canRecord()自动处理，无需监听器
        
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
            // 停止所有活动（会自动释放资源）
            stopListening()
            
            // 🔥 等待VAD任务完成（最多等待2秒）
            vadJob?.let { job ->
                try {
                    withTimeoutOrNull(2000L) {
                        job.join()
                        Log.d(TAG, "✅ VAD任务已完成")
                    } ?: run {
                        Log.w(TAG, "⚠️ VAD任务等待超时，强制取消")
                        job.cancel()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "等待VAD任务异常", e)
                }
            }
            vadJob = null
            
            // 🔥 等待录制任务完成（最多等待1秒）
            recordingJob?.let { job ->
                try {
                    withTimeoutOrNull(1000L) {
                        job.join()
                        Log.d(TAG, "✅ 录制任务已完成")
                    } ?: run {
                        Log.w(TAG, "⚠️ 录制任务等待超时，强制取消")
                        job.cancel()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "等待录制任务异常", e)
                }
            }
            recordingJob = null
            
            // 确保麦克风资源被释放
            try {
                AudioResourceManager.releaseMicrophone(AudioResourceManager.AudioOwner.ASR_DEVICE)
                Log.d(TAG, "✅ 确保麦克风资源已释放")
            } catch (e: Exception) {
                Log.w(TAG, "释放麦克风资源异常", e)
            }
            
            // TTS状态通过canRecord()自动处理，无需监听器
            
            // 关闭音频通道
            samplesChannel.close()
            
            // 🔥 使用互斥锁保护recognizer释放（防止与识别任务并发冲突）
            recognizerMutex.withLock {
                senseVoiceRecognizer?.release()
                senseVoiceRecognizer = null
                Log.d(TAG, "✅ SenseVoice识别器已释放")
            }
            
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
        
        // 🆕 记录ASR启动时间，用于初始缓冲期
        asrStartTime = System.currentTimeMillis()
        Log.d(TAG, "⏰ ASR启动时间记录: ${asrStartTime}ms (初始缓冲期: ${INITIAL_GRACE_PERIOD_MS}ms)")
        
        // 确保协程作用域可用
        if (!scope.isActive) {
            recreateScope()
        }
        
        Log.d(TAG, "🎙️ 开始语音监听...")
        com.ai.voice.util.AutoTestLogger.logAsrListeningStarted()
        
        // 请求麦克风资源
        scope.launch {
            val granted = AudioResourceManager.requestMicrophone(
                AudioResourceManager.AudioOwner.ASR_DEVICE
            )
            
            if (!granted) {
                Log.w(TAG, "❌ 无法获取麦克风资源（TTS正在播放）")
                withContext(Dispatchers.Main) {
                    _uiState.value = SttState.ErrorLoading(Exception("音频资源被占用"))
                }
                return@launch
            }
            
            Log.d(TAG, "✅ 成功获取麦克风资源")
            
            isListening.set(true)
            
            // 重置VAD和音频状态
            hasEmittedNoneOnSilence = false
            resetVadState()
            
            withContext(Dispatchers.Main) {
                _uiState.value = SttState.Listening
            }
            
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
            } else {
                Log.e(TAG, "❌ 启动录制失败")
                isListening.set(false)
                
                // 释放资源
                AudioResourceManager.releaseMicrophone(AudioResourceManager.AudioOwner.ASR_DEVICE)
                
                withContext(Dispatchers.Main) {
                    _uiState.value = SttState.ErrorLoading(Exception("启动录制失败"))
                }
            }
        }
        return true
    }
    
    /**
     * 请求音频焦点（Android 15+ 必需）
     */
    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return true
        }
        
        val manager = audioManager ?: run {
            Log.e(TAG, "❌ AudioManager not initialized")
            return false
        }
        
        try {
            // 🔥 重要：ASR 使用 AUDIOFOCUS_GAIN（持续录音），不是 TRANSIENT（短暂）
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .build()
            
            audioFocusRequest = focusRequest
            
            val result = manager.requestAudioFocus(focusRequest)
            val success = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            
            hasAudioFocus.set(success)
            
            if (success) {
                Log.d(TAG, "✅ Audio focus granted (Android ${Build.VERSION.SDK_INT})")
            } else {
                Log.e(TAG, "❌ Audio focus request failed: result=$result (Android ${Build.VERSION.SDK_INT})")
            }
            
            return success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception requesting audio focus", e)
            return false
        }
    }
    
    /**
     * 释放音频焦点
     */
    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        
        if (!hasAudioFocus.get()) {
            return
        }
        
        try {
            val manager = audioManager
            val request = audioFocusRequest
            
            if (manager != null && request != null) {
                manager.abandonAudioFocusRequest(request)
                Log.d(TAG, "🔓 Audio focus released")
            }
            
            hasAudioFocus.set(false)
            audioFocusRequest = null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception releasing audio focus", e)
        }
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
            
            // Android 15+ 必须先请求音频焦点
            if (!requestAudioFocus()) {
                Log.e(TAG, "❌ Cannot create AudioRecord without audio focus (Android ${Build.VERSION.SDK_INT})")
                return false
            }
            
            // 单例模式下不需要资源锁
            // 确保先清理之前的资源
            cleanupAudioRecord()
            
            val minBufferSizeInBytes = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            if (minBufferSizeInBytes == AudioRecord.ERROR || minBufferSizeInBytes == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "❌ 无法获取AudioRecord缓冲区大小")
                releaseAudioFocus()
                return false
            }
            
            // 使用更大的缓冲区以避免缓冲区溢出，至少是最小缓冲区的4倍
            val actualBufferSize = maxOf(minBufferSizeInBytes * 4, VAD_FRAME_SIZE * 2 * 4) // 4倍安全边界
            
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
                                // Already stopped
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
                    record.release()
                } catch (e: Exception) {
                    Log.w(TAG, "释放AudioRecord时出错", e)
                }
            }
            
            audioRecord = null
            
            // 释放音频焦点（Android 15+ 必需）
            releaseAudioFocus()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 清理AudioRecord资源失败", e)
        }
    }
    
    /**
     * 录制音频数据 (修复缓冲区管理和并发问题)
     */
    private suspend fun recordAudioData() {
        // 开始音频数据录制
        
        // 使用合适的缓冲区大小，确保不超过AudioRecord的缓冲区
        val bufferSize = VAD_FRAME_SIZE // 512 samples = 1024 bytes
        val buffer = ShortArray(bufferSize)
        var consecutiveErrors = 0
        val maxConsecutiveErrors = 5
        
        try {
            while (isRecording.get() && !Thread.currentThread().isInterrupted && !currentCoroutineContext().job.isCancelled) {
                try {
                    // 检查是否可以录音（TTS播放时暂停）
                    if (!AudioResourceManager.canRecord()) {
                        // TTS正在播放，暂停录音但不退出循环
                        delay(50) // 等待50ms再检查
                        continue
                    }
                    
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
                            
                            // 🔧 转换为Float数组并创建新副本（修复数据引用bug）
                            // 之前的优化导致floatBuffer被重用，当直接使用引用时数据会被覆盖
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
                    // 不重新抛出，优雅退出即可
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
            
            // 音频数据录制结束
        }
    }
    
    /**
     * 处理音频进行VAD检测和识别
     */
    private suspend fun processAudioForRecognition() {
        // 开始音频处理和VAD检测
        
        try {
            while (isListening.get()) {
                for (samples in samplesChannel) {
                    if (samples.isEmpty()) {
                        break
                    }
                    
                    // 🚀 使用AudioBuffer添加音频块（自动管理循环缓冲）
                    audioBuffer.addAudioChunk(samples)
                    
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
                        
                        // 🆕 渐进式识别：首次快速响应（0.5秒），后续提高质量（0.7秒）
                        val elapsed = currentTime - lastPartialRecognitionTime
                        val minAudioDuration = if (partialRecognitionCount == 0) {
                            PARTIAL_FIRST_MIN_AUDIO_DURATION_SEC // 首次：快速响应
                        } else {
                            PARTIAL_NORMAL_MIN_AUDIO_DURATION_SEC // 后续：提高准确率
                        }
                        
                        if (elapsed > PARTIAL_RECOGNITION_COOLDOWN_MS && 
                            audioBuffer.hasMinimumAudio(minAudioDuration)) {
                            performPartialRecognition()
                        }
                        
                    } else if (speechDetected) {
                        // 🆕 优先检查：是否满足提前结束条件
                        if (isWaitingForEarlyStop && stablePartialConfirmTime > 0) {
                            val confirmElapsed = currentTime - stablePartialConfirmTime
                            if (confirmElapsed >= EARLY_STOP_CONFIRM_DELAY_MS) {
                                // 确认等待时间到了，再次检查是否还有语音
                                if (!hasRecentSpeech(EARLY_STOP_CONFIRM_DELAY_MS / 2)) {
                                    Log.i(TAG, "⚡️ 确认提前结束: Partial='$lastStablePartialText', 确认延迟=${confirmElapsed}ms")
                                    stopListeningAndProcess()
                                    break
                                } else {
                                    // 用户还在说话，取消提前结束
                                    Log.d(TAG, "⚠️ 取消提前结束: 用户还在说话")
                                    isWaitingForEarlyStop = false
                                    stablePartialConfirmTime = 0L
                                }
                            }
                        }
                        
                        // 检查是否静音超时（使用动态超时）
                        val silenceDuration = currentTime - lastSpeechTime
                        val timeoutMs = getDynamicTimeout()
                        if (silenceDuration > timeoutMs) {
                            if (partialText.isBlank()) {
                                // 无任何有效识别结果，静音超时交由状态机处理：仅发送None，不在设备层停止监听
                                if (!hasEmittedNoneOnSilence) {
                                    Log.d(TAG, "🔇 检测到静音超时(${timeoutMs}ms)，由状态机接管回到待唤醒 (partialText='')")
                                    withContext(Dispatchers.Main) {
                                        eventListener?.invoke(InputEvent.None)
                                    }
                                    hasEmittedNoneOnSilence = true
                                }
                                // 保持监听，由上层状态机决定是否停止与复位
                            } else {
                                // 已有部分识别内容，静音超时则结束监听并进行最终识别
                                Log.d(TAG, "🔇 静音超时(${timeoutMs}ms)，存在部分识别，停止监听并处理最终结果 (partialText='$partialText')")
                                stopListeningAndProcess()
                                break
                            }
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
            // 音频处理结束
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
     * 执行部分识别（实时反馈）- 渐进式快速响应策略
     */
    private suspend fun performPartialRecognition() {
        try {
            val currentTime = System.currentTimeMillis()
            lastPartialRecognitionTime = currentTime
            
            // 🆕 渐进式音频时长要求
            val minAudioDuration = if (partialRecognitionCount == 0) {
                PARTIAL_FIRST_MIN_AUDIO_DURATION_SEC
            } else {
                PARTIAL_NORMAL_MIN_AUDIO_DURATION_SEC
            }
            
            // 使用AudioBuffer获取累积的音频数据
            if (!audioBuffer.hasMinimumAudio(minAudioDuration)) {
                DebugLogger.logAudio(TAG, "⏭️ 音频不足${minAudioDuration}秒，跳过Partial识别")
                return
            }
            val audioData = audioBuffer.getAccumulatedAudio()
            
            // 🆕 添加音频质量检查
            val audioStats = audioBuffer.getAudioQualityStats()
            
            // 🔥 使用互斥锁保护recognizer访问
            val newText = recognizerMutex.withLock {
                val recognizer = senseVoiceRecognizer
                if (recognizer == null) {
                    Log.d(TAG, "⏭️ Recognizer不可用，跳过Partial识别")
                    return
                }
                recognizer.recognize(audioData)
            }
            
            // 🔥 优化过滤逻辑：保留韩文单字，但过滤纯标点
            val isMeaningful = newText.isNotBlank() && 
                               newText != "." && 
                               !newText.matches(Regex("^[.。,，!！?？]+$")) && // 纯标点
                               hasValidContent(newText) // 包含字母或韩文字符
            
            if (isMeaningful && newText != partialText) {
                val oldText = partialText
                partialText = newText
                partialRecognitionCount++ // 增加识别计数
                
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
                
                val audioDuration = audioData.size / SAMPLE_RATE.toFloat()
                Log.d(TAG, "🎯 部分识别更新 #${partialRecognitionCount}: '$oldText' → '$partialText' (音频: ${String.format("%.2f", audioDuration)}秒, 质量: ${audioStats})")
                
                // 🆕 检查稳定性，重置计数
                partialStableCount = 0
                lastStablePartialText = newText
                stablePartialConfirmTime = 0L
                isWaitingForEarlyStop = false
                
            } else if (isMeaningful && newText == partialText) {
                // 🆕 Partial文本稳定（连续相同）
                if (newText == lastStablePartialText) {
                    partialStableCount++
                    Log.d(TAG, "📊 Partial稳定: '$newText' (稳定次数: $partialStableCount/$STABLE_COUNT_THRESHOLD)")
                    
                    // 检查是否满足提前结束条件
                    if (shouldTriggerEarlyStop(newText)) {
                        if (!isWaitingForEarlyStop) {
                            // 首次达到条件，开始等待确认
                            isWaitingForEarlyStop = true
                            stablePartialConfirmTime = currentTime
                            Log.i(TAG, "⚡️ Partial稳定且符合条件，开始${EARLY_STOP_CONFIRM_DELAY_MS}ms确认等待: '$newText'")
                        }
                    }
                } else {
                    // 文本变化，重置
                    partialStableCount = 1
                    lastStablePartialText = newText
                    isWaitingForEarlyStop = false
                }
            } else if (!isMeaningful && newText.isNotBlank()) {
                // 过滤掉无意义输出，不发送事件
                val audioDuration = audioData.size / SAMPLE_RATE.toFloat()
                val reason = when {
                    newText == "." -> "单点"
                    newText.matches(Regex("^[.。,，!！?？]+$")) -> "纯标点"
                    !hasValidContent(newText) -> "无有效字符"
                    else -> "未知"
                }
                Log.d(TAG, "⏭️ 跳过无意义输出: '$newText' (原因: $reason, 音频: ${String.format("%.2f", audioDuration)}秒)")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 部分识别异常", e)
        }
    }
    
    /**
     * 🆕 检查文本是否包含有效内容（字母、韩文字符等）
     * 用于过滤纯标点的识别结果
     */
    private fun hasValidContent(text: String): Boolean {
        return text.any { char ->
            when {
                // 英语字母
                char in 'a'..'z' || char in 'A'..'Z' -> true
                // 韩语字符（Hangul Syllables）
                char in '\uAC00'..'\uD7A3' -> true
                // 韩语兼容字母
                char in '\u3131'..'\u318E' -> true
                char in '\u1100'..'\u11FF' -> true
                // 数字
                char in '0'..'9' -> true
                // 其他非标点字符
                else -> false
            }
        }
    }
    
    /**
     * 🆕 动态获取静音超时时间
     * 根据是否有识别结果，动态调整超时时间
     * 优化：韩语命令需要更长的超时时间
     */
    private fun getDynamicTimeout(): Long {
        val timeSinceStart = System.currentTimeMillis() - asrStartTime
        
        // 如果还在初始缓冲期内，使用长超时
        if (timeSinceStart < INITIAL_GRACE_PERIOD_MS) {
            return SPEECH_TIMEOUT_MS
        }
        
        // 🔥 优化：有识别结果后快速结束，提高响应速度
        return if (partialText.length >= 3) {
            1200L  // 有识别结果，1.2秒超时（快速响应）
        } else if (partialText.length >= 1) {
            1800L  // 有部分识别，1.8秒超时（中等响应）
        } else {
            SPEECH_TIMEOUT_MS  // 无识别结果，使用配置的超时时间（4秒）
        }
    }
    
    /**
     * 🆕 检查是否应该触发提前结束
     */
    private fun shouldTriggerEarlyStop(text: String): Boolean {
        // 条件1: 文本长度至少3个字
        if (text.length < MIN_TEXT_LENGTH_FOR_EARLY_STOP) {
            return false
        }
        
        // 条件2: Partial连续稳定次数达到阈值
        if (partialStableCount < STABLE_COUNT_THRESHOLD) {
            return false
        }
        
        return true
    }
    
    /**
     * 🆕 检查最近是否有语音（用于确认用户说完了）
     */
    private fun hasRecentSpeech(thresholdMs: Long): Boolean {
        val silenceDuration = System.currentTimeMillis() - lastSpeechTime
        return silenceDuration < thresholdMs
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
            // 检查是否有足够的语音数据
            if (!audioBuffer.hasMinimumAudio(0.1f) || !speechDetected) {
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
            
            val audioSamples = audioBuffer.getAccumulatedAudio().size
            Log.d(TAG, "🚀 开始最终识别，音频长度: ${audioSamples}样本，语音时长: ${speechDuration}ms")
            
            // 使用AudioBuffer获取累积的音频数据
            val audioData = audioBuffer.getAccumulatedAudio()
            
            // 🔥 使用互斥锁保护recognizer访问
            val finalText = recognizerMutex.withLock {
                val recognizer = senseVoiceRecognizer
                if (recognizer == null) {
                    Log.w(TAG, "⚠️ Recognizer不可用，跳过Final识别")
                    return
                }
                recognizer.recognize(audioData)
            }
            
            DebugLogger.logRecognition(TAG, "最终识别结果: \"$finalText\"")
            Log.d(TAG, "🔍 识别结果详情: 长度=${finalText.length}, 是否空白=${finalText.isBlank()}")
            
            // 🔥 过滤无意义的最终识别结果
            val isMeaningful = finalText.isNotBlank() && 
                               finalText != "." && 
                               finalText.length > 1 &&
                               !finalText.matches(Regex("^[.。,，!！?？]+$"))
            
            withContext(Dispatchers.Main) {
                if (isMeaningful) {
                    Log.d(TAG, "✅ 发送Final事件: \"$finalText\"")
                    eventListener?.invoke(InputEvent.Final(listOf(Pair(finalText, 1.0f))))
                } else {
                    if (finalText.isNotBlank()) {
                        Log.d(TAG, "⏭️ 跳过无意义的最终结果: \"$finalText\"，发送None事件")
                    } else {
                        Log.d(TAG, "⚠️ 识别结果为空，发送None事件")
                    }
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
            
            // 🔥 关键修复：释放麦克风资源
            // 之前这里缺少释放逻辑，导致连续测试时麦克风持有者状态混乱
            try {
                runBlocking {
                    AudioResourceManager.releaseMicrophone(AudioResourceManager.AudioOwner.ASR_DEVICE)
                }
                Log.d(TAG, "✅ 已释放麦克风资源（performFinalRecognition完成）")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ 释放麦克风资源失败", e)
            }
        }
    }
    
    /**
     * 重置VAD和语音检测状态
     */
    private fun resetVadState() {
        speechDetected = false
        speechStartTime = 0L
        lastSpeechTime = 0L
        
        // 🔥 修复：重置Partial识别时间戳（多轮对话bug）
        lastPartialRecognitionTime = 0L
        
        audioBuffer.clear()
        partialText = ""
        isPartialResultAdded = false // 重置结果管理标志
        partialRecognitionCount = 0 // 重置识别计数
        
        // 🆕 重置高分提前结束相关状态
        lastStablePartialText = ""
        partialStableCount = 0
        stablePartialConfirmTime = 0L
        isWaitingForEarlyStop = false
        
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
        
        // 取消录制协程，但不立即清理（等待协程优雅退出）
        recordingJob?.cancel()
        
        // 在协程中异步等待并清理
        scope.launch {
            try {
                // 等待录制协程完成（最多500ms）
                withTimeoutOrNull(500L) {
                    recordingJob?.join()
                }
            } catch (e: Exception) {
                Log.w(TAG, "等待录制协程退出异常: ${e.message}")
            } finally {
                recordingJob = null
                cleanupAudioRecord()
                Log.d(TAG, "✅ 录制资源已清理")
            }
        }
    }
    
    /**
     * 获取设备信息
     */
    fun getDeviceInfo(): String {
        val recognizerInfo = senseVoiceRecognizer?.getInfo() ?: "未初始化"
        val bufferInfo = audioBuffer.getBufferInfo()
        val isActive = isListening.get()
        return "SenseVoiceDevice($recognizerInfo, $bufferInfo, 活跃:$isActive)"
    }
}
