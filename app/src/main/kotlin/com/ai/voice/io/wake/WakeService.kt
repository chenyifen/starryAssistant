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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.ai.voice.R
import com.ai.voice.di.WakeDeviceWrapper
import com.ai.voice.eval.SkillEvaluator
import com.ai.voice.util.DebugLogger
import com.ai.voice.util.AudioDebugSaver
import com.ai.voice.io.wake.WakeWordCallbackManager
import com.ai.voice.util.ActivationChecker
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@AndroidEntryPoint
class WakeService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    private val listening = AtomicBoolean(false)
    private var currentAudioRecord: AudioRecord? = null
    private var permissionCheckJob: kotlinx.coroutines.Job? = null
    
    // 音频焦点管理（Android 15+ 必需）
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = AtomicBoolean(false)

    @Inject
    lateinit var skillEvaluator: SkillEvaluator
    @Inject
    lateinit var wakeDevice: WakeDeviceWrapper
    @Inject
    lateinit var speechOutputDevice: com.ai.voice.di.SpeechOutputDeviceWrapper

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var notificationManager: NotificationManager

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🚀 WakeService onCreate")
        notificationManager = getSystemService(this, NotificationManager::class.java)!!
        
        // 初始化 AudioManager（Android 15 音频焦点必需）
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // 启动时清理旧的音频调试文件
        if (DebugLogger.isAudioSaveEnabled()) {
            AudioDebugSaver.cleanupOldAudioFiles(this, 50)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_WAKE_SERVICE) {
            listening.set(false)
            com.ai.voice.util.AutoTestLogger.logWakeListeningStopped()
            return START_NOT_STICKY
        }

        try {
            createForegroundNotification()
        } catch (t: Throwable) {
            Log.e(TAG, "❌ Failed to create foreground notification", t)
            stopWithMessage("could not create WakeService foreground notification", t)
            return START_NOT_STICKY
        }

        if (listening.get()) {
            return START_STICKY
        }

        val hasPermission = ContextCompat.checkSelfPermission(this, RECORD_AUDIO) == PERMISSION_GRANTED
        Log.i(TAG, "📊 onStartCommand: 权限=$hasPermission, listening=${listening.get()}")
        if (!hasPermission) {
            Log.e(TAG, "❌ Microphone permission not granted, starting permission check loop")
            startPermissionCheckLoop()
            return START_STICKY
        }

        Log.i(TAG, "✅ 权限已授予，启动持续监听")
        startPersistentListening()
        return START_STICKY
    }
    
    /**
     * 启动权限检查循环，当权限被授予时自动启动监听
     */
    private fun startPermissionCheckLoop() {
        permissionCheckJob?.cancel()
        Log.i(TAG, "🔄 启动权限检查循环")
        permissionCheckJob = scope.launch {
            while (!listening.get()) {
                val hasPermission = ContextCompat.checkSelfPermission(this@WakeService, RECORD_AUDIO) == PERMISSION_GRANTED
                Log.i(TAG, "🔍 权限检查: $hasPermission")
                if (hasPermission) {
                    Log.i(TAG, "✅ Permission granted, starting persistent listening")
                    startPersistentListening()
                    break
                }
                delay(5000)
            }
        }
    }
    
    /**
     * 启动持续监听模式
     */
    private fun startPersistentListening() {
        Log.i(TAG, "🚀 启动持续监听模式")
        listening.set(true)
        WakeWordCallbackManager.notifyListeningStarted()
        com.ai.voice.util.AutoTestLogger.logWakeListeningStarted()
        
        val currentState = wakeDevice.state.value
        Log.i(TAG, "📊 当前模型状态: $currentState")
        if (currentState == WakeState.NotLoaded) {
            Log.i(TAG, "🔄 模型未加载，开始下载")
            wakeDevice.download()
        }
        
        scope.launch {
            try {
                var consecutiveErrors = 0
                val maxConsecutiveErrors = 3 // 连续失败3次后停止重试
                
                // 持续监听循环，只有明确停止才退出
                while (listening.get()) {
                    try {
                        if (wakeDevice.state.value is WakeState.ErrorLoading) {
                            Log.e(TAG, "❌ 模型加载失败，停止唤醒词监听")
                            listening.set(false)
                            break
                        }
                        
                        listenForWakeWord()
                        consecutiveErrors = 0
                        
                        if (listening.get()) {
                            delay(1000)
                        }
                    } catch (e: Exception) {
                        consecutiveErrors++
                        if (consecutiveErrors >= maxConsecutiveErrors) {
                            Log.e(TAG, "❌ 连续失败 $maxConsecutiveErrors 次，停止唤醒词监听", e)
                            listening.set(false)
                            break
                        }
                        delay(3000)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "❌ Fatal error in persistent listening", t)
                stopWithMessage("Fatal error in persistent listening", t)
            }
        }
    }

    override fun onDestroy() {
        listening.set(false)
        permissionCheckJob?.cancel()
        permissionCheckJob = null
        
        // 通知回调：停止监听
        WakeWordCallbackManager.notifyListeningStopped()
        
        // AutoTest日志：退出唤醒监听状态
        com.ai.voice.util.AutoTestLogger.logWakeListeningStopped()
        
        releaseAudioFocus()
        job.cancel()
        wakeDevice.destroy()
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
            Log.e(TAG, "❌ AudioManager not initialized")
            return false
        }
        
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener { }
                .build()
            
            audioFocusRequest = focusRequest
            
            val result = manager.requestAudioFocus(focusRequest)
            val success = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            
            hasAudioFocus.set(success)
            
            if (!success) {
                Log.e(TAG, "❌ Audio focus request failed: result=$result")
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
            }
            
            hasAudioFocus.set(false)
            audioFocusRequest = null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception releasing audio focus", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun createOptimalAudioRecord(): AudioRecord? {
        if (!requestAudioFocus()) {
            Log.e(TAG, "❌ Cannot create AudioRecord without audio focus")
            return null
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
            Log.e(TAG, "❌ Device does not support 16k mono PCM16 input")
            releaseAudioFocus()
            return null
        }

        val bufferMultipliers = intArrayOf(1, 2, 4)
        for ((source, sourceName) in audioSources) {
            for (mult in bufferMultipliers) {
                val bufferSize = (minBufAt16k * mult).coerceAtLeast(minBufAt16k)
                try {
                    val ar = AudioRecord(
                        source,
                        targetRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )

                    if (ar.state == AudioRecord.STATE_INITIALIZED) {
                        if (testAudioRecord(ar)) {
                            return ar
                        } else {
                            ar.release()
                        }
                    } else {
                        ar.release()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception creating AudioRecord: source=$sourceName", e)
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
            val testBuffer = ShortArray(160)
            val bytesRead = ar.read(testBuffer, 0, testBuffer.size)
            ar.stop()
            return bytesRead > 0
        } catch (e: Exception) {
            Log.e(TAG, "❌ AudioRecord test exception", e)
            false
        }
    }

    private fun createForegroundNotification() {
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
            .setContentTitle(getString(R.string.wake_service_foreground_notification))
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
        var waitCount = 0
        val maxWaitCount = 300
        while (wakeDevice.state.value != WakeState.Loaded && waitCount < maxWaitCount) {
            when (val currentState = wakeDevice.state.value) {
                WakeState.NotDownloaded, WakeState.NotLoaded -> {
                    wakeDevice.download()
                }
                is WakeState.ErrorLoading -> {
                    Log.e(TAG, "❌ 模型加载失败: ${currentState.throwable.message}")
                    return
                }
                else -> break
            }
            
            Thread.sleep(100)
            waitCount++
            
            if (!listening.get()) {
                return
            }
        }
        
        if (wakeDevice.state.value != WakeState.Loaded) {
            Log.e(TAG, "❌ 模型加载超时")
            return
        }

        val ar = createOptimalAudioRecord() ?: run {
            Log.e(TAG, "❌ Failed to create AudioRecord")
            return
        }
        
        currentAudioRecord = ar

        var audio = ShortArray(0)
        var nextWakeWordAllowed = Instant.MIN
        var frameCount = 0

        try {
            ar.startRecording()
            
            while (listening.get()) {
                if (com.ai.voice.util.AsrHandler.isStarted()) {
                    if (ar.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        try {
                            ar.stop()
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Failed to stop AudioRecord", e)
                        }
                    }
                    while (com.ai.voice.util.AsrHandler.isStarted() && listening.get()) {
                        Thread.sleep(100)
                    }
                    if (!listening.get()) {
                        break
                    }
                    
                    if (ar.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        try {
                            ar.startRecording()
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Failed to restart AudioRecord", e)
                            break
                        }
                    }
                }
                
                if (audio.size != wakeDevice.frameSize()) {
                    audio = ShortArray(wakeDevice.frameSize())
                }

                val isRecording = ar.recordingState == AudioRecord.RECORDSTATE_RECORDING
                val asrStarted = com.ai.voice.util.AsrHandler.isStarted()
                
                if (isRecording && !asrStarted) {
                    val bytesRead = ar.read(audio, 0, audio.size)
                    frameCount++
                    
                    if (bytesRead > 0) {
                        val wakeWordDetected = wakeDevice.processFrame(audio)
                        val now = Instant.now()
                        
                        if (wakeWordDetected) {
                            if (now > nextWakeWordAllowed) {
                                DebugLogger.logWakeWordSuccess(TAG)
                                com.ai.voice.util.AutoTestLogger.logWakeupDetected()
                                nextWakeWordAllowed = now.plusMillis(WAKE_WORD_BACKOFF_MILLIS)
                                onWakeWordDetected()
                            }
                        }

                        lastHeard.set(now)
                    } else if (bytesRead < 0) {
                        Log.e(TAG, "❌ AudioRecord read failed: $bytesRead")
                    }
                } else {
                    Thread.sleep(10)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in wake word listening", e)
            throw e
        } finally {
            try {
                ar.stop()
                ar.release()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error releasing AudioRecord", e)
            }
            currentAudioRecord = null
            releaseAudioFocus()
        }
    }

    private fun onWakeWordDetected() {
        WakeWordCallbackManager.notifyWakeWordDetected()
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
        private const val ACTION_STOP_WAKE_SERVICE = "com.ai.voice.WakeService.ACTION_STOP"
    }
    
}
