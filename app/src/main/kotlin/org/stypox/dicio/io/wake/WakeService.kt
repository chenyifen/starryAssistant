package org.stypox.dicio.io.wake

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
import android.media.AudioFormat
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
import kotlinx.coroutines.launch
import org.stypox.dicio.MainActivity
import org.stypox.dicio.MainActivity.Companion.ACTION_WAKE_WORD
import org.stypox.dicio.R
import org.stypox.dicio.di.SttInputDeviceWrapper
import org.stypox.dicio.di.WakeDeviceWrapper
import org.stypox.dicio.eval.SkillEvaluator
import org.stypox.dicio.util.DebugLogger
import org.stypox.dicio.util.AudioDebugSaver
import org.stypox.dicio.io.wake.WakeWordCallbackManager
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@AndroidEntryPoint
class WakeService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    private val listening = AtomicBoolean(false)
    private val audioRecordPaused = AtomicBoolean(false) // 用于暂停AudioRecord以避免与ASR冲突
    private var currentAudioRecord: AudioRecord? = null // 当前的AudioRecord实例

    @Inject
    lateinit var skillEvaluator: SkillEvaluator
    @Inject
    lateinit var sttInputDevice: SttInputDeviceWrapper
    @Inject
    lateinit var wakeDevice: WakeDeviceWrapper

    private val handler = Handler(Looper.getMainLooper())
    private val releaseSttResourcesRunnable = Runnable {
        if (MainActivity.isCreated <= 0) {
            // if the main activity is neither visible nor in the background,
            // then unload the STT after a while because it would be using resources uselessly
            sttInputDevice.reinitializeToReleaseResources()
        }
    }

    private lateinit var notificationManager: NotificationManager

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        DebugLogger.logWakeWord(TAG, "🚀 WakeService onCreate")
        notificationManager = getSystemService(this, NotificationManager::class.java)!!

        scope.launch {
            // Recreate the notification so that it says the correct thing (i.e. there is a
            // different string for the "Hey Dicio" wake word and for a custom one).
            // Ignore the first one (i.e. the current value), which is handled in onStartCommand.
            wakeDevice.isHeyDicio.drop(1).collect { isHeyDicio ->
                DebugLogger.logWakeWord(TAG, "🔄 Wake word type changed: ${if (isHeyDicio) "Hey Dicio" else "Custom"}")
                createForegroundNotification(isHeyDicio)
            }
        }
        
        
        // 启动时清理旧的音频调试文件
        if (DebugLogger.isAudioSaveEnabled()) {
            AudioDebugSaver.cleanupOldAudioFiles(this, 50)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 只有明确的停止指令才停止服务
        if (intent?.action == ACTION_STOP_WAKE_SERVICE) {
            DebugLogger.logWakeWord(TAG, "🛑 Received explicit stop command")
            listening.set(false)
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
        
        // 主动触发模型加载
        if (wakeDevice.state.value == WakeState.NotLoaded) {
            DebugLogger.logWakeWord(TAG, "🔄 主动触发模型加载...")
            wakeDevice.download()
        }
        
        scope.launch {
            try {
                // 持续监听循环，只有明确停止才退出
                while (listening.get()) {
                    try {
                        listenForWakeWord()
                        
                        // 如果listenForWakeWord正常退出，等待一下再重启
                        if (listening.get()) {
                            DebugLogger.logWakeWord(TAG, "🔄 Wake word listening ended, restarting in 1s...")
                            delay(1000)
                        }
                    } catch (e: Exception) {
                        DebugLogger.logWakeWordError(TAG, "❌ Error in wake word listening, retrying in 3s...", e)
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
        
        // 通知回调：停止监听
        WakeWordCallbackManager.notifyListeningStopped()
        
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

    @SuppressLint("MissingPermission")
    private fun createOptimalAudioRecord(): AudioRecord? {
        // 尝试不同的音频源配置
        val audioSources = arrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
            MediaRecorder.AudioSource.MIC to "MIC",
            MediaRecorder.AudioSource.DEFAULT to "DEFAULT",
            MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION"
        )
        
        // 尝试不同的缓冲区大小
        val bufferSizes = arrayOf(6400, 3200, 1600, 8000)
        
        for ((source, sourceName) in audioSources) {
            for (bufferSize in bufferSizes) {
                try {
                    DebugLogger.logAudioProcessing(TAG, "🔧 Trying AudioRecord: source=$sourceName, bufferSize=$bufferSize")
                    
                    val ar = AudioRecord(
                        source,
                        16000,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )
                    
                    if (ar.state == AudioRecord.STATE_INITIALIZED) {
                        DebugLogger.logAudioProcessing(TAG, "✅ AudioRecord initialized: source=$sourceName, bufferSize=$bufferSize")
                        
                        // 测试录音功能
                        if (testAudioRecord(ar)) {
                            DebugLogger.logAudioProcessing(TAG, "🎵 AudioRecord test passed: source=$sourceName")
                            return ar
                        } else {
                            DebugLogger.logWakeWordError(TAG, "❌ AudioRecord test failed: source=$sourceName")
                            ar.release()
                        }
                    } else {
                        DebugLogger.logWakeWordError(TAG, "❌ AudioRecord not initialized: source=$sourceName, state=${ar.state}")
                        ar.release()
                    }
                } catch (e: Exception) {
                    DebugLogger.logWakeWordError(TAG, "❌ Exception creating AudioRecord: source=$sourceName", e)
                }
            }
        }
        
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

        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
    }

    private fun listenForWakeWord() {
        DebugLogger.logWakeWord(TAG, "🎤 Starting wake word listening...")
        DebugLogger.logWakeWord(TAG, "📊 Wake device state: ${wakeDevice.state.value}")
        DebugLogger.logWakeWord(TAG, "🔊 Wake word type: ${if (wakeDevice.isHeyDicio.value) "Hey Dicio" else "Custom"}")
        
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
                // 检查是否需要暂停AudioRecord以让ASR使用
                if (audioRecordPaused.get()) {
                    DebugLogger.logWakeWord(TAG, "⏸️ AudioRecord paused for ASR, waiting...")
                    while (audioRecordPaused.get() && listening.get()) {
                        Thread.sleep(50) // 短暂等待
                    }
                    if (!listening.get()) {
                        DebugLogger.logWakeWord(TAG, "🛑 Listening stopped while paused")
                        break
                    }
                    DebugLogger.logWakeWord(TAG, "▶️ AudioRecord resumed, continuing wake word detection")
                    
                    // 重新启动AudioRecord（如果之前被停止了）
                    if (ar.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        try {
                            ar.startRecording()
                            DebugLogger.logWakeWord(TAG, "🔄 AudioRecord restarted after ASR pause")
                        } catch (e: Exception) {
                            DebugLogger.logWakeWordError(TAG, "❌ Failed to restart AudioRecord after ASR", e)
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
                if (ar.recordingState == AudioRecord.RECORDSTATE_RECORDING && !audioRecordPaused.get()) {
                    val bytesRead = ar.read(audio, 0, audio.size)
                    frameCount++
                    
                    // 每100帧记录一次调试信息
                    if (frameCount % 100 == 0) {
                        DebugLogger.logAudioProcessing(TAG, "🔄 Frame #$frameCount, bytesRead=$bytesRead")
                    }
                    
                    if (bytesRead > 0) {
                        val wakeWordDetected = wakeDevice.processFrame(audio)
                        val now = Instant.now()
                        
                        if (wakeWordDetected) {
                            if (now > nextWakeWordAllowed) {
                                DebugLogger.logWakeWord(TAG, "🎯 WAKE WORD DETECTED! Frame #$frameCount")
                                nextWakeWordAllowed = now.plusMillis(WAKE_WORD_BACKOFF_MILLIS)
                                onWakeWordDetected()
                            } else {
                                val remainingMs = nextWakeWordAllowed.toEpochMilli() - now.toEpochMilli()
                                DebugLogger.logWakeWord(TAG, "⏳ Wake word detected but in backoff period (${remainingMs}ms remaining)")
                            }
                        }

                        lastHeard.set(now)
                        
                        // 每1000帧记录一次状态
                        if (frameCount % 1000 == 0) {
                            DebugLogger.logAudioProcessing(TAG, "📊 Processed $frameCount frames, still listening...")
                        }
                    } else if (bytesRead == 0) {
                        // 0字节可能是正常的，特别是在暂停/恢复期间
                        if (frameCount % 1000 == 0) {
                            DebugLogger.logWakeWord(TAG, "⚠️ AudioRecord read 0 bytes (frame #$frameCount)")
                        }
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
        }
    }

    private fun onWakeWordDetected() {
        DebugLogger.logWakeWord(TAG, "🎉 Wake word detected - processing...")
        
        // 通知所有注册的回调
        WakeWordCallbackManager.notifyWakeWordDetected()
        
        // 暂停WakeService的AudioRecord以让ASR使用
        pauseAudioRecordForASR()

        val intent = Intent(this, MainActivity::class.java)
        intent.setAction(ACTION_WAKE_WORD)
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK)
        DebugLogger.logWakeWord(TAG, "📱 Created MainActivity intent with ACTION_WAKE_WORD")

        // Start listening and pass STT events to the skill evaluator.
        // Note that this works even if the MainActivity is opened later!
        DebugLogger.logVoiceRecognition(TAG, "🎤 Starting STT input device...")
        
        // 直接启动ASR，不需要协调器检查
        val sttStarted = sttInputDevice.tryLoad(skillEvaluator::processInputEvent)
        DebugLogger.logVoiceRecognition(TAG, "STT device start result: $sttStarted")

        // 🔧 保持原有的资源释放机制作为备用，并在STT完成后恢复WakeService
        handler.removeCallbacks(releaseSttResourcesRunnable)
        val resumeWakeServiceRunnable = Runnable {
            if (MainActivity.isCreated <= 0) {
                // if the main activity is neither visible nor in the background,
                // then unload the STT after a while because it would be using resources uselessly
                sttInputDevice.reinitializeToReleaseResources()
            }
            // 恢复WakeService的AudioRecord
            resumeAudioRecordAfterASR()
        }
        handler.postDelayed(resumeWakeServiceRunnable, RELEASE_STT_RESOURCES_MILLIS)
        DebugLogger.logVoiceRecognition(TAG, "⏰ Scheduled STT resource release and WakeService resume in ${RELEASE_STT_RESOURCES_MILLIS}ms")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || MainActivity.isInForeground > 0) {
            // start the activity directly on versions prior to Android 10,
            // or if the MainActivity is already running in the foreground
            startActivity(intent)

        } else {
            // Android 10+ does not allow starting activities from the background,
            // so show a full-screen notification instead, which does actually result in starting
            // the activity from the background if the phone is off and Do Not Disturb is not active
            // Maybe we could also use the "Display over other apps" permission?

            val channel = NotificationChannel(
                TRIGGERED_NOTIFICATION_CHANNEL_ID,
                getString(R.string.wake_service_triggered_notification),
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = getString(R.string.wake_service_triggered_notification_summary)
            notificationManager.createNotificationChannel(channel)

            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(this, TRIGGERED_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_hearing_white)
                .setContentTitle(getString(R.string.wake_service_triggered_notification))
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    getString(R.string.wake_service_triggered_notification_summary)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setFullScreenIntent(pendingIntent, true)
                .build()

            notificationManager.cancel(TRIGGERED_NOTIFICATION_ID)
            notificationManager.notify(TRIGGERED_NOTIFICATION_ID, notification)
        }
    }
    
    /**
     * 暂停WakeService的AudioRecord以让ASR使用音频资源
     */
    private fun pauseAudioRecordForASR() {
        DebugLogger.logWakeWord(TAG, "⏸️ Pausing WakeService AudioRecord for ASR")
        audioRecordPaused.set(true)
        
        // 给AudioRecord一些时间停止
        scope.launch {
            delay(100) // 等待100ms让AudioRecord循环检查暂停状态
            currentAudioRecord?.let { ar ->
                try {
                    if (ar.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        ar.stop()
                        DebugLogger.logWakeWord(TAG, "🛑 WakeService AudioRecord stopped for ASR")
                    }
                } catch (e: Exception) {
                    DebugLogger.logWakeWordError(TAG, "❌ Error stopping AudioRecord for ASR", e)
                }
            }
        }
    }
    
    /**
     * 恢复WakeService的AudioRecord在ASR完成后
     */
    private fun resumeAudioRecordAfterASR() {
        DebugLogger.logWakeWord(TAG, "▶️ Resuming WakeService AudioRecord after ASR")
        audioRecordPaused.set(false)
        
        // 尝试重新启动AudioRecord（如果它被停止了）
        scope.launch {
            delay(200) // 给ASR一些时间完全释放音频资源
            currentAudioRecord?.let { ar ->
                try {
                    if (ar.recordingState != AudioRecord.RECORDSTATE_RECORDING && listening.get()) {
                        ar.startRecording()
                        DebugLogger.logWakeWord(TAG, "🔄 AudioRecord restarted after ASR completion")
                    }
                } catch (e: Exception) {
                    DebugLogger.logWakeWordError(TAG, "❌ Error restarting AudioRecord after ASR", e)
                }
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
            "org.stypox.dicio.io.wake.WakeService.FOREGROUND"
        private const val START_NOTIFICATION_CHANNEL_ID =
            "org.stypox.dicio.io.wake.WakeService.START"
        private const val TRIGGERED_NOTIFICATION_CHANNEL_ID =
            "org.stypox.dicio.io.wake.WakeService.TRIGGERED"
        private const val FOREGROUND_NOTIFICATION_ID = 19803672
        private const val START_NOTIFICATION_ID = 48019274
        private const val TRIGGERED_NOTIFICATION_ID = 601398647
        private const val WAKE_WORD_BACKOFF_MILLIS = 4000L
        private const val ACTION_STOP_WAKE_SERVICE =
            "org.stypox.dicio.io.wake.WakeService.ACTION_STOP"
        private const val RELEASE_STT_RESOURCES_MILLIS = 1000L * 10 // 10 seconds - 缩短时间以快速恢复WakeService
    }
}
