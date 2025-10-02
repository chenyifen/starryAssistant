package org.stypox.dicio.di

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.datastore.core.DataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.stypox.dicio.R
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.io.input.SttInputDevice
import org.stypox.dicio.io.input.SttState
import org.stypox.dicio.io.input.external_popup.ExternalPopupInputDevice
import org.stypox.dicio.io.input.vosk.VoskInputDevice
import org.stypox.dicio.io.input.TwoPassInputDevice
import org.stypox.dicio.io.input.sensevoice.SenseVoiceInputDevice
import org.stypox.dicio.io.input.websocket.WebSocketInputDevice
import org.stypox.dicio.settings.datastore.InputDevice
import org.stypox.dicio.settings.datastore.InputDevice.INPUT_DEVICE_NOTHING
import org.stypox.dicio.settings.datastore.InputDevice.INPUT_DEVICE_EXTERNAL_POPUP
import org.stypox.dicio.settings.datastore.InputDevice.INPUT_DEVICE_UNSET
import org.stypox.dicio.settings.datastore.InputDevice.INPUT_DEVICE_VOSK
import org.stypox.dicio.settings.datastore.InputDevice.INPUT_DEVICE_TWO_PASS
import org.stypox.dicio.settings.datastore.InputDevice.INPUT_DEVICE_SENSEVOICE
import org.stypox.dicio.settings.datastore.InputDevice.INPUT_DEVICE_WEBSOCKET
import org.stypox.dicio.settings.datastore.InputDevice.UNRECOGNIZED
import org.stypox.dicio.util.WebSocketConfig
import org.stypox.dicio.settings.datastore.SttPlaySound
import org.stypox.dicio.settings.datastore.UserSettings
import org.stypox.dicio.util.distinctUntilChangedBlockingFirst
import javax.inject.Singleton


interface SttInputDeviceWrapper {
    val uiState: StateFlow<SttState?>

    fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean

    fun stopListening()

    fun onClick(eventListener: (InputEvent) -> Unit)

    fun reinitializeToReleaseResources()
}

class SttInputDeviceWrapperImpl(
    @ApplicationContext private val appContext: Context,
    dataStore: DataStore<UserSettings>,
    private val localeManager: LocaleManager,
    private val okHttpClient: OkHttpClient,
    private val activityForResultManager: ActivityForResultManager,
) : SttInputDeviceWrapper {
    
    companion object {
        private const val TAG = "SttInputDeviceWrapper"
    }
    private val scope = CoroutineScope(Dispatchers.Default)

    private var inputDeviceSetting: InputDevice
    private var sttPlaySoundSetting: SttPlaySound
    private var sttInputDevice: SttInputDevice?

    // null means that the user has not enabled any STT input device
    private val _uiState: MutableStateFlow<SttState?> = MutableStateFlow(null)
    override val uiState: StateFlow<SttState?> = _uiState
    private var uiStateJob: Job? = null


    init {
        // Run blocking, because the data store is always available right away since LocaleManager
        // also initializes in a blocking way from the same data store.
        val (firstSettings, nextSettingsFlow) = dataStore.data
            .map { Pair(it.inputDevice, it.sttPlaySound) }
            .distinctUntilChangedBlockingFirst()

        inputDeviceSetting = firstSettings.first
        sttPlaySoundSetting = firstSettings.second
        sttInputDevice = buildInputDevice(inputDeviceSetting)
        scope.launch {
            restartUiStateJob()
        }

        scope.launch {
            nextSettingsFlow.collect { (inputDevice, sttPlaySound) ->
                sttPlaySoundSetting = sttPlaySound
                if (inputDeviceSetting != inputDevice) {
                    changeInputDeviceTo(inputDevice)
                }
            }
        }
    }

    private suspend fun changeInputDeviceTo(setting: InputDevice) {
        val prevSttInputDevice = sttInputDevice
        inputDeviceSetting = setting
        sttInputDevice = buildInputDevice(setting)
        prevSttInputDevice?.destroy()
        restartUiStateJob()
    }

    private fun buildInputDevice(setting: InputDevice): SttInputDevice? {
        Log.d(TAG, "🏗️ 构建STT输入设备: $setting")
        return when (setting) {
            UNRECOGNIZED,
            INPUT_DEVICE_UNSET -> {
                // 默认使用 WebSocket（如果可用）
                if (WebSocketConfig.isWebSocketAvailable(appContext)) {
                    Log.d(TAG, "   🌐 创建 WebSocketInputDevice (默认)")
                    WebSocketInputDevice(
                        appContext = appContext,
                        serverUrl = WebSocketConfig.getWebSocketUrl(appContext),
                        accessToken = WebSocketConfig.getAccessToken(appContext),
                        deviceId = WebSocketConfig.getDeviceId(appContext),
                        clientId = WebSocketConfig.getClientId(appContext)
                    )
                } else {
                    Log.d(TAG, "   🎙️ 回退到 SenseVoiceInputDevice")
                    SenseVoiceInputDevice.getInstance(appContext, localeManager)
                }
            }
            INPUT_DEVICE_WEBSOCKET -> {
                Log.d(TAG, "   🌐 创建 WebSocketInputDevice")
                WebSocketInputDevice(
                    appContext = appContext,
                    serverUrl = WebSocketConfig.getWebSocketUrl(appContext),
                    accessToken = WebSocketConfig.getAccessToken(appContext),
                    deviceId = WebSocketConfig.getDeviceId(appContext),
                    clientId = WebSocketConfig.getClientId(appContext)
                )
            }
            INPUT_DEVICE_SENSEVOICE -> {
                Log.d(TAG, "   🎙️ 获取SenseVoiceInputDevice单例")
                SenseVoiceInputDevice.getInstance(appContext, localeManager)
            }
            INPUT_DEVICE_VOSK -> {
                Log.d(TAG, "   📡 创建VoskInputDevice")
                VoskInputDevice(appContext, okHttpClient, localeManager)
            }
            INPUT_DEVICE_TWO_PASS -> {
                Log.d(TAG, "   🎯 创建TwoPassInputDevice (双识别模式)")
                TwoPassInputDevice(appContext, okHttpClient, localeManager)
            }
            INPUT_DEVICE_EXTERNAL_POPUP -> {
                Log.d(TAG, "   🖥️ 创建ExternalPopupInputDevice")
                ExternalPopupInputDevice(appContext, activityForResultManager, localeManager)
            }
            INPUT_DEVICE_NOTHING -> {
                Log.d(TAG, "   ❌ 无输入设备")
                null
            }
        }
    }

    private suspend fun restartUiStateJob() {
        uiStateJob?.cancel()
        val newSttInputDevice = sttInputDevice
        if (newSttInputDevice == null) {
            uiStateJob = null
            _uiState.emit(null)
        } else {
            uiStateJob = scope.launch {
                newSttInputDevice.uiState.collect {
                    _uiState.emit(it)
                    if (it == SttState.Listening) {
                        playSound(R.raw.listening_sound)
                    }
                }
            }
        }
    }

    private fun playSound(resid: Int) {
        val attributes = AudioAttributes.Builder()
            .setUsage(
                when (sttPlaySoundSetting) {
                    SttPlaySound.UNRECOGNIZED,
                    SttPlaySound.STT_PLAY_SOUND_UNSET,
                    SttPlaySound.STT_PLAY_SOUND_NOTIFICATION -> AudioAttributes.USAGE_NOTIFICATION
                    SttPlaySound.STT_PLAY_SOUND_ALARM -> AudioAttributes.USAGE_ALARM
                    SttPlaySound.STT_PLAY_SOUND_MEDIA -> AudioAttributes.USAGE_MEDIA
                    SttPlaySound.STT_PLAY_SOUND_NONE -> return // do not play any sound
                }
            )
            .build()
        val mediaPlayer = MediaPlayer.create(appContext, resid, attributes, 0)
        mediaPlayer.setVolume(0.75f, 0.75f)
        mediaPlayer.setOnCompletionListener { mp ->
            mp.release() // 播放完成后释放资源
        }
        mediaPlayer.start()
    }

    private fun wrapEventListener(eventListener: (InputEvent) -> Unit): (InputEvent) -> Unit = {
        if (it is InputEvent.None) {
            scope.launch {
                playSound(R.raw.listening_no_input_sound)
            }
        }
        eventListener(it)
    }

    override fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean {
        return sttInputDevice?.tryLoad(if (thenStartListeningEventListener != null) {
            wrapEventListener(thenStartListeningEventListener)
        } else { null }) ?: false
    }

    override fun stopListening() {
        sttInputDevice?.stopListening()
    }

    override fun onClick(eventListener: (InputEvent) -> Unit) {
        sttInputDevice?.onClick(wrapEventListener(eventListener))
    }

    override fun reinitializeToReleaseResources() {
        scope.launch { changeInputDeviceTo(inputDeviceSetting) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
class SttInputDeviceWrapperModule {
    @Provides
    @Singleton
    fun provideInputDeviceWrapper(
        @ApplicationContext appContext: Context,
        dataStore: DataStore<UserSettings>,
        localeManager: LocaleManager,
        okHttpClient: OkHttpClient,
        activityForResultManager: ActivityForResultManager,
    ): SttInputDeviceWrapper {
        return SttInputDeviceWrapperImpl(
            appContext, dataStore, localeManager, okHttpClient, activityForResultManager
        )
    }
}
