package com.ai.voice.io.wake

import android.Manifest.permission.RECORD_AUDIO
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.datastore.core.DataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.ai.voice.MainActivity
import com.ai.voice.MainActivity.Companion.ACTION_WAKE_WORD
import com.ai.voice.R
import com.ai.voice.di.WakeDeviceWrapper
import com.ai.voice.eval.SkillEvaluator
import com.ai.voice.settings.datastore.UserSettings
import com.ai.voice.util.DebugLogger
import com.ai.voice.util.AudioDebugSaver
import com.ai.voice.io.wake.WakeWordCallbackManager
import com.ai.voice.util.ActivationChecker
import com.ai.voice.settings.datastore.UserSettingsEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@AndroidEntryPoint
class WakeService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    private val listening = AtomicBoolean(false)
    private val audioRecordPaused = AtomicBoolean(false) // 🔧 已废弃：不再使用，改用 AsrHandler.isStarted() 检查
    private var currentAudioRecord: AudioRecord? = null // 当前的AudioRecord实例
    
    // 音频焦点管理（Android 15+ 必需）
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = AtomicBoolean(false)

    @Inject
    lateinit var skillEvaluator: SkillEvaluator
    // @Inject lateinit var sttInputDevice: SttInputDeviceWrapper // 🔧 已禁用：不再使用，改用 AsrHandler
    @Inject
    lateinit var wakeDevice: WakeDeviceWrapper
    @Inject
    lateinit var dataStore: DataStore<UserSettings>
    @Inject
    lateinit var speechOutputDevice: com.ai.voice.di.SpeechOutputDeviceWrapper

    private val handler = Handler(Looper.getMainLooper())
    private val releaseSttResourcesRunnable = Runnable {
        // 🔧 已禁用：不再使用 sttInputDevice，改用 AsrHandler
        // if (MainActivity.isCreated <= 0) {
        //     // if the main activity is neither visible nor in the background,
        //     // then unload the STT after a while because it would be using resources uselessly
        //     sttInputDevice.reinitializeToReleaseResources()
        // }
    }

    private lateinit var notificationManager: NotificationManager

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        DebugLogger.logWakeWord(TAG, "🚀 WakeService onCreate")
        notificationManager = getSystemService(this, NotificationManager::class.java)!!
        
        // 初始化 AudioManager（Android 15 音频焦点必需）
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        scope.launch {
            // Recreate the notification so that it says the correct thing (i.e. there is a
            // different string for the "Hey Dicio" wake word and for a custom one).
            // Ignore the first one (i.e. the current value), which is handled in onStartCommand.
            wakeDevice.isHeyDicio.drop(1).collect { isHeyDicio ->
                DebugLogger.logWakeWord(TAG, "🔄 Wake word type changed: ${if (isHeyDicio) "Hey Dicio" else "Custom"}")
                createForegroundNotification(isHeyDicio)
            }
        }
        
        // 🔧 不再使用 AudioResourceManager，WakeService 直接管理自己的 AudioRecord
        
        // 启动时清理旧的音频调试文件
        if (DebugLogger.isAudioSaveEnabled()) {
            AudioDebugSaver.cleanupOldAudioFiles(this, 50)
        }
        
        // 🔧 临时调试：广播接收器已改为静态注册（WakeWordDebugBroadcastReceiver）
        // 不再需要动态注册，因为静态注册可以接收外部广播（adb shell am broadcast）
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 只有明确的停止指令才停止服务
        if (intent?.action == ACTION_STOP_WAKE_SERVICE) {
            DebugLogger.logWakeWord(TAG, "🛑 Received explicit stop command")
            listening.set(false)
            // AutoTest日志：退出唤醒监听状态
            com.ai.voice.util.AutoTestLogger.logWakeListeningStopped()
            return START_NOT_STICKY
        }

        try {
            createForegroundNotification(wakeDevice.isHeyDicio.value)
        } catch (t: Throwable) {
            stopWithMessage("could not create WakeService foreground notification", t)
            return START_NOT_STICKY
        }

        // 如果已经在监听，直接返回，保持持续监听
        if (listening.get()) {
            DebugLogger.logWakeWord(TAG, "🔄 Service already listening, maintaining persistent mode")
            return START_STICKY
        }

        if (ContextCompat.checkSelfPermission(this, RECORD_AUDIO) != PERMISSION_GRANTED) {
            DebugLogger.logWakeWordError(TAG, "❌ Microphone permission not granted")
            // 不停止服务，等待权限恢复
            return START_STICKY
        }

        // 启动持续监听
        startPersistentListening()
        return START_STICKY
    }
    
    /**
     * 启动持续监听模式
     */
    private fun startPersistentListening() {
        DebugLogger.logWakeWord(TAG, "🚀 Starting persistent wake word listening")
        listening.set(true)
        
        // 通知回调：开始监听
        WakeWordCallbackManager.notifyListeningStarted()
        
        // AutoTest日志：进入唤醒监听状态
        com.ai.voice.util.AutoTestLogger.logWakeListeningStarted()
        
        // 主动触发模型加载
        if (wakeDevice.state.value == WakeState.NotLoaded) {
            DebugLogger.logWakeWord(TAG, "🔄 主动触发模型加载...")
            wakeDevice.download()
        }
        
        scope.launch {
            try {
                var consecutiveErrors = 0
                val maxConsecutiveErrors = 3 // 连续失败3次后停止重试
                
                // 持续监听循环，只有明确停止才退出
                while (listening.get()) {
                    try {
                        // 检查模型状态，如果加载失败则停止重试
                        if (wakeDevice.state.value is WakeState.ErrorLoading) {
                            DebugLogger.logWakeWordError(TAG, "❌ 模型加载失败，唤醒词服务设置为不可用状态")
                            listening.set(false)
                            break
                        }
                        
                        listenForWakeWord()
                        
                        // 如果成功执行了一轮，重置错误计数
                        consecutiveErrors = 0
                        
                        // 如果listenForWakeWord正常退出，等待一下再重启
                        if (listening.get()) {
                            DebugLogger.logWakeWord(TAG, "🔄 Wake word listening ended, restarting in 1s...")
                            delay(1000)
                        }
                    } catch (e: Exception) {
                        consecutiveErrors++
                        DebugLogger.logWakeWordError(TAG, "❌ Error in wake word listening ($consecutiveErrors/$maxConsecutiveErrors), retrying in 3s...", e)
                        
                        if (consecutiveErrors >= maxConsecutiveErrors) {
                            DebugLogger.logWakeWordError(TAG, "❌ 连续失败 $maxConsecutiveErrors 次，停止唤醒词监听")
                            listening.set(false)
                            break
                        }
                        
                        delay(3000) // 错误时等待更长时间
                    }
                }
                DebugLogger.logWakeWord(TAG, "🏁 Persistent listening stopped")
            } catch (t: Throwable) {
                DebugLogger.logWakeWordError(TAG, "❌ Fatal error in persistent listening", t)
                stopWithMessage("Fatal error in persistent listening", t)
            }
        }
    }

    override fun onDestroy() {
        listening.set(false)
        
        // 🔧 临时调试：广播接收器已改为静态注册，无需注销
        
        // 通知回调：停止监听
        WakeWordCallbackManager.notifyListeningStopped()
        
        // AutoTest日志：退出唤醒监听状态
        com.ai.voice.util.AutoTestLogger.logWakeListeningStopped()
        
        // 释放音频焦点（Android 15+ 必需）
        releaseAudioFocus()
        
        // 🔧 不再使用 AudioResourceManager，WakeService 直接管理自己的 AudioRecord
        
        job.cancel()
        wakeDevice.reinitializeToReleaseResources()
        super.onDestroy()
    }

    private fun stopWithMessage(message: String = "", throwable: Throwable? = null) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()

        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else if (message.isNotEmpty()) {
            Log.e(TAG, message)
        }
    }

    /**
     * 请求音频焦点（Android 15+ 必需）
     * 
     * @return true=成功获取音频焦点，false=失败
     */
    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Android 8.0 以下不需要音频焦点
            return true
        }
        
        val manager = audioManager ?: run {
            DebugLogger.logWakeWordError(TAG, "❌ AudioManager not initialized")
            return false
        }
        
        try {
            // 创建音频焦点请求
            // 🔥 重要：使用 AUDIOFOCUS_GAIN（持续录音），而不是 TRANSIENT（短暂）
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener { focusChange ->
                    DebugLogger.logAudioProcessing(TAG, "🎧 Audio focus changed: $focusChange")
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            DebugLogger.logWakeWord(TAG, "⚠️ Audio focus lost permanently")
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            DebugLogger.logWakeWord(TAG, "✅ Audio focus regained")
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            DebugLogger.logWakeWord(TAG, "⚠️ Audio focus lost temporarily")
                        }
                    }
                }
                .build()
            
            audioFocusRequest = focusRequest
            
            val result = manager.requestAudioFocus(focusRequest)
            val success = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            
            hasAudioFocus.set(success)
            
            if (success) {
                DebugLogger.logAudioProcessing(TAG, "✅ Audio focus granted (Android ${Build.VERSION.SDK_INT})")
            } else {
                DebugLogger.logWakeWordError(TAG, "❌ Audio focus request failed: result=$result (Android ${Build.VERSION.SDK_INT})")
            }
            
            return success
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ Exception requesting audio focus", e)
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
                DebugLogger.logAudioProcessing(TAG, "🔓 Audio focus released")
            }
            
            hasAudioFocus.set(false)
            audioFocusRequest = null
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ Exception releasing audio focus", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun createOptimalAudioRecord(): AudioRecord? {
        // Android 15+ 必须先请求音频焦点
        if (!requestAudioFocus()) {
            DebugLogger.logWakeWordError(TAG, "❌ Cannot create AudioRecord without audio focus (Android ${Build.VERSION.SDK_INT})")
            return null
        }
        // 先检测常见采样率的支持情况（仅日志，便于定位 -22）
        val probeRates = intArrayOf(44100, 48000, 16000, 32000, 22050, 8000)
        runCatching {
            val sb = StringBuilder()
            sb.append("🎛️ Input support probe (CHANNEL_IN_MONO, PCM_16BIT): ")
            probeRates.forEach { rate ->
                val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                sb.append("$rate=")
                if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
                    sb.append("UNSUPPORTED; ")
                } else {
                    sb.append("minBuf=$minBuf; ")
                }
            }
            DebugLogger.logAudioProcessing(TAG, sb.toString())
        }

        // 音源优先顺序（更稳妥的顺序）：MIC → DEFAULT → VOICE_COMMUNICATION → VOICE_RECOGNITION
        val audioSources = arrayOf(
            MediaRecorder.AudioSource.MIC to "MIC",
            MediaRecorder.AudioSource.DEFAULT to "DEFAULT",
            MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION",
            MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
        )

        // 固定目标采样率为 16k（模型需求）；但如果 16k 不被支持，记录日志并放弃，避免后续流程失配
        val targetRate = 16000
        val minBufAt16k = AudioRecord.getMinBufferSize(targetRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBufAt16k == AudioRecord.ERROR || minBufAt16k == AudioRecord.ERROR_BAD_VALUE) {
            DebugLogger.logWakeWordError(TAG, "❌ Device does not support 16k mono PCM16 input (getMinBufferSize)! Likely cause of -22")
            // 重要：释放音频焦点，避免泄漏
            releaseAudioFocus()
            return null
        }

        // 尝试不同的缓冲放大倍率（在最小缓冲基础上放大）
        val bufferMultipliers = intArrayOf(1, 2, 4)
        for ((source, sourceName) in audioSources) {
            for (mult in bufferMultipliers) {
                val bufferSize = (minBufAt16k * mult).coerceAtLeast(minBufAt16k)
                try {
                    DebugLogger.logAudioProcessing(TAG, "🔧 Trying AudioRecord: source=$sourceName, sampleRate=$targetRate, bufferSize=$bufferSize")

                    val ar = AudioRecord(
                        source,
                        targetRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )

                    if (ar.state == AudioRecord.STATE_INITIALIZED) {
                        DebugLogger.logAudioProcessing(TAG, "✅ AudioRecord initialized: source=$sourceName, bufferSize=$bufferSize")

                        if (testAudioRecord(ar)) {
                            DebugLogger.logAudioProcessing(TAG, "🎵 AudioRecord test passed: source=$sourceName @ ${targetRate}Hz")
                            return ar
                        } else {
                            DebugLogger.logWakeWordError(TAG, "❌ AudioRecord test failed after init: source=$sourceName")
                            ar.release()
                        }
                    } else {
                        DebugLogger.logWakeWordError(TAG, "❌ AudioRecord not initialized: source=$sourceName, state=${ar.state}")
                        ar.release()
                    }
                } catch (e: Exception) {
                    DebugLogger.logWakeWordError(TAG, "❌ Exception creating AudioRecord: source=$sourceName, bufferSize=$bufferSize", e)
                }
            }
        }

        // 重要：全部失败时释放音频焦点
        releaseAudioFocus()
        return null
    }
    
    @SuppressLint("MissingPermission")
    private fun testAudioRecord(ar: AudioRecord): Boolean {
        return try {
            ar.startRecording()
            val testBuffer = ShortArray(160) // 10ms at 16kHz
            val bytesRead = ar.read(testBuffer, 0, testBuffer.size)
            ar.stop()
            
            DebugLogger.logAudioProcessing(TAG, "🧪 AudioRecord test: bytesRead=$bytesRead")
            
            if (bytesRead > 0) {
                // 检查是否有实际音频数据（非全零）
                val hasAudio = testBuffer.any { it != 0.toShort() }
                DebugLogger.logAudioProcessing(TAG, "🧪 Audio data present: $hasAudio")
                
                // 计算音频幅度
                val amplitude = testBuffer.maxOfOrNull { kotlin.math.abs(it.toFloat()) / 32768.0f } ?: 0.0f
                DebugLogger.logAudioProcessing(TAG, "🧪 Test amplitude: $amplitude")
                
                return bytesRead > 0 // 只要能读取数据就认为成功，即使幅度为0
            }
            
            false
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ AudioRecord test exception", e)
            false
        }
    }

    private fun createForegroundNotification(isHeyDicio: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FOREGROUND_NOTIFICATION_CHANNEL_ID,
                getString(R.string.wake_service_label),
                NotificationManager.IMPORTANCE_LOW,
            )
            channel.description = getString(R.string.wake_service_foreground_notification_summary)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, FOREGROUND_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hearing_white)
            .setContentTitle(
                getString(
                    if (isHeyDicio) R.string.wake_service_foreground_notification
                    else R.string.wake_custom_service_foreground_notification
                )
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(NotificationCompat.Action(
                R.drawable.ic_stop_circle_white,
                getString(R.string.stop),
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, WakeService::class.java)
                        .apply { action = ACTION_STOP_WAKE_SERVICE },
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            ))
            .build()

        // Android 15+ (targetSdk 36) 需要明确指定前台服务类型
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun listenForWakeWord() {
        DebugLogger.logWakeWord(TAG, "🎤 Starting wake word listening...")
        DebugLogger.logWakeWord(TAG, "📊 Wake device state: ${wakeDevice.state.value}")
        DebugLogger.logWakeWord(TAG, "🔊 Wake word type: ${if (wakeDevice.isHeyDicio.value) "Hey Dicio" else "Custom"}")
        
        // 🔧 不再使用 AudioResourceManager，直接创建 AudioRecord
        // WakeService 通过检查 AsrHandler.isStarted() 来决定是否继续监听
        
        // 等待模型加载完成，最多等待30秒
        var waitCount = 0
        val maxWaitCount = 300 // 30秒，每100ms检查一次
        while (wakeDevice.state.value != WakeState.Loaded && waitCount < maxWaitCount) {
            when (val currentState = wakeDevice.state.value) {
                WakeState.Loading -> {
                    if (waitCount % 50 == 0) { // 每5秒打印一次状态
                        DebugLogger.logWakeWord(TAG, "⏳ 等待模型加载完成... (${waitCount * 100}ms)")
                    }
                }
                WakeState.NotDownloaded -> {
                    DebugLogger.logWakeWordError(TAG, "❌ 模型未下载，尝试下载...")
                    wakeDevice.download()
                }
                is WakeState.ErrorLoading -> {
                    DebugLogger.logWakeWordError(TAG, "❌ 模型加载失败: ${currentState.throwable.message}")
                    return
                }
                WakeState.NotLoaded -> {
                    DebugLogger.logWakeWord(TAG, "🔄 模型未加载，尝试加载...")
                    wakeDevice.download()
                }
                else -> break
            }
            
            Thread.sleep(100) // 等待100ms
            waitCount++
            
            if (!listening.get()) {
                DebugLogger.logWakeWord(TAG, "🛑 在等待模型加载时停止了监听")
                return
            }
        }
        
        if (wakeDevice.state.value != WakeState.Loaded) {
            DebugLogger.logWakeWordError(TAG, "❌ 模型加载超时，无法开始监听")
            return
        }
        
        DebugLogger.logWakeWord(TAG, "✅ 模型已就绪，开始监听")
        DebugLogger.logWakeWord(TAG, "📏 Frame size: ${wakeDevice.frameSize()}")

        // 尝试多种AudioRecord配置以提高兼容性
        val ar = createOptimalAudioRecord() ?: run {
            DebugLogger.logWakeWordError(TAG, "❌ Failed to create any AudioRecord configuration")
            return
        }
        
        // 保存当前AudioRecord引用
        currentAudioRecord = ar
        DebugLogger.logAudioProcessing(TAG, "🎵 AudioRecord created successfully")

        var audio = ShortArray(0)
        var nextWakeWordAllowed = Instant.MIN
        var frameCount = 0

        try {
            ar.startRecording()
            DebugLogger.logWakeWord(TAG, "✅ AudioRecord started successfully")
            DebugLogger.logWakeWord(TAG, "🔄 Starting audio processing loop...")
            
            while (listening.get()) {
                // 🔧 检查 AsrHandler 是否正在运行，如果正在运行则暂停监听
                // 使用 AsrHandler.isStarted() 的取反值来决定是否继续监听
                if (com.ai.voice.util.AsrHandler.isStarted()) {
                    DebugLogger.logWakeWord(TAG, "⏸️ AsrHandler 正在运行，暂停唤醒词检测...")
                    // 暂停 AudioRecord
                    if (ar.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        try {
                            ar.stop()
                            DebugLogger.logWakeWord(TAG, "🛑 AudioRecord stopped for AsrHandler")
                        } catch (e: Exception) {
                            DebugLogger.logWakeWordError(TAG, "❌ Failed to stop AudioRecord for AsrHandler", e)
                        }
                    }
                    // 等待 AsrHandler 停止
                    while (com.ai.voice.util.AsrHandler.isStarted() && listening.get()) {
                        Thread.sleep(100) // 每100ms检查一次
                    }
                    if (!listening.get()) {
                        DebugLogger.logWakeWord(TAG, "🛑 Listening stopped while waiting for AsrHandler")
                        break
                    }
                    DebugLogger.logWakeWord(TAG, "▶️ AsrHandler 已停止，恢复唤醒词检测")
                    
                    // 重新启动AudioRecord
                    if (ar.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        try {
                            ar.startRecording()
                            DebugLogger.logWakeWord(TAG, "🔄 AudioRecord restarted after AsrHandler")
                        } catch (e: Exception) {
                            DebugLogger.logWakeWordError(TAG, "❌ Failed to restart AudioRecord after AsrHandler", e)
                            break
                        }
                    }
                }
                
                if (audio.size != wakeDevice.frameSize()) {
                    val oldSize = audio.size
                    audio = ShortArray(wakeDevice.frameSize())
                    DebugLogger.logAudioProcessing(TAG, "🔄 Audio buffer resized: $oldSize -> ${audio.size}")
                }

                // 只有在AudioRecord正在录制时才读取数据
                // 且 AsrHandler 未运行时才继续监听
                if (ar.recordingState == AudioRecord.RECORDSTATE_RECORDING && !com.ai.voice.util.AsrHandler.isStarted()) {
                    val bytesRead = ar.read(audio, 0, audio.size)
                    frameCount++
                    
                    // 注释掉常规帧日志，减少输出
                    // if (frameCount % 100 == 0 && bytesRead > 0) {
                    //     DebugLogger.logAudioProcessing(TAG, "🔄 Frame #$frameCount, bytesRead=$bytesRead")
                    // }
                    
                    if (bytesRead > 0) {
                        val wakeWordDetected = wakeDevice.processFrame(audio)
                        val now = Instant.now()
                        
                        if (wakeWordDetected) {
                            if (now > nextWakeWordAllowed) {
                                DebugLogger.logWakeWord(TAG, "🎯 WAKE WORD DETECTED! Frame #$frameCount")
                                // 🔒 关键日志：唤醒词检测成功（Release版本也输出）
                                DebugLogger.logWakeWordSuccess(TAG)
                                com.ai.voice.util.AutoTestLogger.logWakeupDetected()
                                nextWakeWordAllowed = now.plusMillis(WAKE_WORD_BACKOFF_MILLIS)
                                onWakeWordDetected()
                            } else {
                                val remainingMs = nextWakeWordAllowed.toEpochMilli() - now.toEpochMilli()
                                DebugLogger.logWakeWord(TAG, "⏳ Wake word detected but in backoff period (${remainingMs}ms remaining)")
                            }
                        }

                        lastHeard.set(now)
                        
                        // 注释掉状态日志，只在重要事件时输出
                        // if (frameCount % 1000 == 0) {
                        //     DebugLogger.logAudioProcessing(TAG, "📊 Processed $frameCount frames, still listening...")
                        // }
                    } else if (bytesRead == 0) {
                        // 0字节可能是正常的，特别是在暂停/恢复期间
                        // 不再记录日志，避免刷屏
                    } else {
                        DebugLogger.logWakeWordError(TAG, "❌ AudioRecord read failed: $bytesRead bytes")
                    }
                } else {
                    // AudioRecord不在录制状态或被暂停，短暂等待
                    Thread.sleep(10)
                }
            }
        } catch (e: Exception) {
            DebugLogger.logWakeWordError(TAG, "❌ Error in wake word listening", e)
            throw e
        } finally {
            DebugLogger.logWakeWord(TAG, "🛑 Stopping AudioRecord (processed $frameCount frames)")
            try {
                ar.stop()
                ar.release()
            } catch (e: Exception) {
                DebugLogger.logWakeWordError(TAG, "❌ Error releasing AudioRecord", e)
            }
            currentAudioRecord = null
            
            // 释放音频焦点（Android 15+ 必需）
            releaseAudioFocus()
            
            // 🔧 不再使用 AudioResourceManager，WakeService 直接管理自己的 AudioRecord
        }
    }

    private fun onWakeWordDetected() {
        DebugLogger.logWakeWord(TAG, "🎉 Wake word detected - processing...")
        
        // 🔒 检查激活状态
        val dataStore = try {
            EntryPointAccessors.fromApplication(
                this,
                UserSettingsEntryPoint::class.java
            ).userSettings()
        } catch (e: Exception) {
            null
        }
        
        val isActivated = ActivationChecker.isActivated(this, dataStore)
        if (!isActivated) {
            DebugLogger.logWakeWord(TAG, "❌ 应用未激活，无法使用")
            // 播放"Not Activated"提示
            try {
                speechOutputDevice.speak("Not Activated")
                DebugLogger.logWakeWord(TAG, "🔊 已播放: Not Activated")
            } catch (e: Exception) {
                DebugLogger.logWakeWordError(TAG, "❌ 播放TTS失败", e)
            }
            return
        }
        
        // 🔧 取消之前的恢复任务，避免重复唤醒导致状态混乱
        handler.removeCallbacks(releaseSttResourcesRunnable)
        
        // 通知所有注册的回调
        WakeWordCallbackManager.notifyWakeWordDetected()
        
        // ⚠️ 注意：用户唤醒时不播放TTS
        // 原因：
        // 1. 唤醒状态会播放提示音
        // 2. 马上要进入ASR状态，不应该被TTS打断
        // 3. 只有在ASR识别过程中检测到唤醒词时才播放TTS
        DebugLogger.logWakeWord(TAG, "⏭️ 跳过TTS播放（用户唤醒不需要TTS回复）")

        // 检查悬浮球服务是否正在运行
        val isFloatingServiceRunning = isServiceRunning(com.ai.voice.ui.floating.EnhancedFloatingWindowService::class.java)
        DebugLogger.logWakeWord(TAG, "🔍 悬浮球服务运行状态: $isFloatingServiceRunning")
        
        val intent = Intent(this, MainActivity::class.java)
        intent.setAction(ACTION_WAKE_WORD)
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK)
        DebugLogger.logWakeWord(TAG, "📱 Created MainActivity intent with ACTION_WAKE_WORD")

        // 🔧 不再使用 AudioResourceManager 释放资源
        // WakeService 通过检查 AsrHandler.isStarted() 来决定是否暂停监听
        // AsrHandler 和 WakeService 各自管理自己的 AudioRecord

        // 🔧 已禁用：不再使用 SenseVoiceInputDevice，改为使用 AsrHandler
        // ASR 现在由 EnhancedFloatingWindowService 通过 AsrHandler 管理
        // EnhancedFloatingWindowService.onWakeWordDetected() 会调用 AsrHandler.start()
        // WakeService 会在 listenForWakeWord() 循环中检查 AsrHandler.isStarted() 来决定是否暂停
        DebugLogger.logVoiceRecognition(TAG, "⏭️ 跳过 STT 输入设备启动（已改用 AsrHandler，由 EnhancedFloatingWindowService 管理）")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || MainActivity.isInForeground > 0) {
            // start the activity directly on versions prior to Android 10,
            // or if the MainActivity is already running in the foreground
            startActivity(intent)

        } else {
            // Android 10+ does not allow starting activities from the background
            // 如果悬浮球已经打开，就不显示通知，直接启动ASR
            if (isFloatingServiceRunning) {
                DebugLogger.logWakeWord(TAG, "✅ 悬浮球已打开，跳过通知，直接启动ASR")
                // ASR已经在上面启动了，这里不需要额外操作
            } else {
                // 悬浮球未打开，显示通知让用户点击打开
                DebugLogger.logWakeWord(TAG, "📱 悬浮球未打开，显示通知")
                
                // 修改intent的目标为启动悬浮球服务
                val floatingIntent = Intent(this, com.ai.voice.ui.floating.EnhancedFloatingWindowService::class.java)
                
                val channel = NotificationChannel(
                    TRIGGERED_NOTIFICATION_CHANNEL_ID,
                    getString(R.string.wake_service_triggered_notification),
                    NotificationManager.IMPORTANCE_HIGH
                )
                channel.description = getString(R.string.wake_service_triggered_notification_summary)
                notificationManager.createNotificationChannel(channel)

                val pendingIntent = PendingIntent.getService(
                    this,
                    0,
                    floatingIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                val notification = NotificationCompat.Builder(this, TRIGGERED_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_hearing_white)
                    .setContentTitle(getString(R.string.wake_service_triggered_notification))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(
                        getString(R.string.wake_service_triggered_notification_summary)))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

                notificationManager.cancel(TRIGGERED_NOTIFICATION_ID)
                notificationManager.notify(TRIGGERED_NOTIFICATION_ID, notification)
            }
        }
    }
    
    /**
     * 检查指定服务是否正在运行
     */
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
    
    /**
     * 暂停WakeService的AudioRecord以让ASR使用音频资源
     */
    private fun pauseAudioRecordForASR() {
        // 读取设置：是否需要在ASR时暂停唤醒服务
        val shouldPause = runBlocking { 
            dataStore.data.first().pauseWakeDuringAsr 
        }
        
        if (!shouldPause) {
            DebugLogger.logWakeWord(TAG, "⏭️ 跳过暂停WakeService（用户设置：持续运行）")
            return
        }
        
        // 🔧 添加状态检查，避免重复暂停
        if (audioRecordPaused.get()) {
            DebugLogger.logWakeWord(TAG, "⚠️ WakeService已经处于暂停状态，跳过重复暂停")
            return
        }
        
        DebugLogger.logWakeWord(TAG, "⏸️ Pausing WakeService AudioRecord for ASR")
        DebugLogger.logWakeWord(TAG, "📊 当前状态: listening=${listening.get()}, audioRecordPaused=${audioRecordPaused.get()}")
        audioRecordPaused.set(true)
        
        // 给AudioRecord一些时间停止
        scope.launch {
            delay(100) // 等待100ms让AudioRecord循环检查暂停状态
            currentAudioRecord?.let { ar ->
                try {
                    if (ar.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        ar.stop()
                        DebugLogger.logWakeWord(TAG, "🛑 WakeService AudioRecord stopped for ASR")
                    } else {
                        DebugLogger.logWakeWord(TAG, "📊 AudioRecord已经停止，状态: ${ar.recordingState}")
                    }
                } catch (e: Exception) {
                    DebugLogger.logWakeWordError(TAG, "❌ Error stopping AudioRecord for ASR", e)
                }
            } ?: run {
                DebugLogger.logWakeWord(TAG, "⚠️ currentAudioRecord为null，无法停止")
            }
        }
    }
    
    /**
     * 恢复WakeService的AudioRecord在ASR完成后
     */
    private fun resumeAudioRecordAfterASR() {
        // 读取设置：是否需要在ASR时暂停唤醒服务
        val shouldPause = runBlocking { 
            dataStore.data.first().pauseWakeDuringAsr 
        }
        
        if (!shouldPause) {
            DebugLogger.logWakeWord(TAG, "⏭️ 跳过恢复WakeService（用户设置：持续运行）")
            return
        }
        
        // 🔧 添加状态检查，避免不必要的恢复
        if (!audioRecordPaused.get()) {
            DebugLogger.logWakeWord(TAG, "⚠️ WakeService未处于暂停状态，跳过恢复")
            return
        }
        
        DebugLogger.logWakeWord(TAG, "▶️ Resuming WakeService AudioRecord after ASR")
        DebugLogger.logWakeWord(TAG, "📊 当前状态: listening=${listening.get()}, audioRecordPaused=${audioRecordPaused.get()}")
        audioRecordPaused.set(false)
        
        // 尝试重新启动AudioRecord（如果它被停止了）
        scope.launch {
            delay(200) // 给ASR一些时间完全释放音频资源
            currentAudioRecord?.let { ar ->
                try {
                    if (ar.recordingState != AudioRecord.RECORDSTATE_RECORDING && listening.get()) {
                        ar.startRecording()
                        DebugLogger.logWakeWord(TAG, "🔄 AudioRecord restarted after ASR completion")
                    } else {
                        DebugLogger.logWakeWord(TAG, "📊 AudioRecord状态: ${ar.recordingState}, listening: ${listening.get()}")
                    }
                } catch (e: Exception) {
                    DebugLogger.logWakeWordError(TAG, "❌ Error restarting AudioRecord after ASR", e)
                }
            } ?: run {
                DebugLogger.logWakeWord(TAG, "⚠️ currentAudioRecord为null，无法重新启动")
            }
        }
        
        DebugLogger.logWakeWord(TAG, "✅ WakeService AudioRecord resume scheduled")
    }

    companion object {
        /**
         * Starting from Android 11, it is not possible to start a foreground service
         * that accesses the microphone from a BOOT_COMPLETED broadcast. So we show a
         * notification instead, which starts the foreground service when clicked.
         * https://developer.android.com/about/versions/15/behavior-changes-15#fgs-boot-completed
         */
        @RequiresApi(Build.VERSION_CODES.R)
        fun createNotificationToStartLater(context: Context) {
            val notificationManager = getSystemService(context, NotificationManager::class.java)
                ?: return

            val channel = NotificationChannel(
                START_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.wake_service_start_notification),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = context.getString(R.string.wake_service_start_notification_summary)
            notificationManager.createNotificationChannel(channel)

            val pendingIntent = PendingIntent.getForegroundService(
                context,
                0,
                Intent(context, WakeService::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, START_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_hearing_white)
                .setContentTitle(context.getString(R.string.wake_service_start_notification))
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    context.getString(R.string.wake_service_start_notification_summary)))
                .setOngoing(false)
                .setShowWhen(false)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(START_NOTIFICATION_ID, notification)
        }

        /**
         * Start the service. Call this only from a foreground part of the app (e.g. the main
         * activity), or from BOOT_COMPLETED only before Android 11. For BOOT_COMPLETED on Android
         * 11+ use [createNotificationToStartLater] instead.
         */
        fun start(context: Context) {
            val intent = Intent(context, WakeService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            try {
                context.startService(Intent(context, WakeService::class.java)
                    .apply { action = ACTION_STOP_WAKE_SERVICE })
            } catch (_: IllegalStateException) {
                // Must not have been running. No problem with that.
            }
        }

        // Consider the service running if it processed any audio data within the past half second.
        fun isRunning(): Boolean = lastHeard.get()?.isAfter(Instant.now().minusMillis(500)) == true

        /**
         * On Android 10+ cancels any notification telling the user that the Dicio wake word was
         * triggered, which is not needed anymore after the main activity starts.
         */
        fun cancelTriggeredNotification(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                getSystemService(context, NotificationManager::class.java)
                    ?.cancel(TRIGGERED_NOTIFICATION_ID)
            }
        }

        private val lastHeard = AtomicReference<Instant>()

        private val TAG = WakeService::class.simpleName ?: "WakeService"
        private const val FOREGROUND_NOTIFICATION_CHANNEL_ID =
            "com.ai.voice.WakeService.FOREGROUND"
        private const val START_NOTIFICATION_CHANNEL_ID =
            "com.ai.voice.WakeService.START"
        private const val TRIGGERED_NOTIFICATION_CHANNEL_ID =
            "com.ai.voice.WakeService.TRIGGERED"
        private const val FOREGROUND_NOTIFICATION_ID = 19803672
        private const val START_NOTIFICATION_ID = 48019274
        private const val TRIGGERED_NOTIFICATION_ID = 601398647
        private const val WAKE_WORD_BACKOFF_MILLIS = 4000L
        private const val ACTION_STOP_WAKE_SERVICE =
            "com.ai.voice.WakeService.ACTION_STOP"
        private const val RELEASE_STT_RESOURCES_MILLIS = 1000L * 10 // 10 seconds - 缩短时间以快速恢复WakeService
        
        // 🔧 临时调试：广播 action，用于模拟唤醒词检测
        const val ACTION_DEBUG_WAKE_WORD =
            "com.ai.voice.WakeService.ACTION_DEBUG_WAKE_WORD"
    }
    
}
