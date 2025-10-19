package com.ai.voice.di

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
import com.ai.voice.R
import com.ai.voice.io.input.InputEvent
import com.ai.voice.io.input.SttInputDevice
import com.ai.voice.io.input.SttState
import com.ai.voice.io.input.external_popup.ExternalPopupInputDevice
import com.ai.voice.io.input.vosk.VoskInputDevice
import com.ai.voice.io.input.TwoPassInputDevice
import com.ai.voice.io.input.sensevoice.SenseVoiceInputDevice
import com.ai.voice.io.input.sherpa_simulate.SherpaOnnxSimulateInputDevice
import com.ai.voice.settings.datastore.InputDevice
import com.ai.voice.settings.datastore.InputDevice.INPUT_DEVICE_NOTHING
import com.ai.voice.settings.datastore.InputDevice.INPUT_DEVICE_EXTERNAL_POPUP
import com.ai.voice.settings.datastore.InputDevice.INPUT_DEVICE_UNSET
import com.ai.voice.settings.datastore.InputDevice.INPUT_DEVICE_VOSK
import com.ai.voice.settings.datastore.InputDevice.INPUT_DEVICE_TWO_PASS
import com.ai.voice.settings.datastore.InputDevice.INPUT_DEVICE_SENSEVOICE
import com.ai.voice.settings.datastore.InputDevice.INPUT_DEVICE_SHERPA_SIMULATE
import com.ai.voice.settings.datastore.InputDevice.UNRECOGNIZED
import com.ai.voice.settings.datastore.SttPlaySound
import com.ai.voice.settings.datastore.UserSettings
import com.ai.voice.util.distinctUntilChangedBlockingFirst
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
        Log.d(TAG, "🏗️ [INIT] SttInputDeviceWrapper初始化开始")
        // Run blocking, because the data store is always available right away since LocaleManager
        // also initializes in a blocking way from the same data store.
        val (firstSettings, nextSettingsFlow) = dataStore.data
            .map { Pair(it.inputDevice, it.sttPlaySound) }
            .distinctUntilChangedBlockingFirst()

        Log.d(TAG, "📝 [INIT] 读取配置完成: ${firstSettings.first}")
        inputDeviceSetting = firstSettings.first
        sttPlaySoundSetting = firstSettings.second
        Log.d(TAG, "🔨 [INIT] 开始构建SttInputDevice")
        sttInputDevice = buildInputDevice(inputDeviceSetting)
        Log.d(TAG, "✅ [INIT] SttInputDevice构建完成")
        scope.launch {
            restartUiStateJob()
        }

        scope.launch {
            nextSettingsFlow.collect { (inputDevice, sttPlaySound) ->
                Log.d(TAG, "📨 收到设置更新: inputDevice=$inputDevice, sttPlaySound=$sttPlaySound")
                Log.d(TAG, "   当前设置: inputDeviceSetting=$inputDeviceSetting")
                sttPlaySoundSetting = sttPlaySound
                if (inputDeviceSetting != inputDevice) {
                    Log.w(TAG, "⚠️ 检测到设备类型变化: $inputDeviceSetting → $inputDevice")
                    changeInputDeviceTo(inputDevice)
                } else {
                    Log.d(TAG, "✅ 设备类型未变化，跳过切换")
                }
            }
        }
    }

    private suspend fun changeInputDeviceTo(setting: InputDevice) {
        Log.d(TAG, "🔄 切换输入设备: $setting")
        val prevSttInputDevice = sttInputDevice
        
        // 🔥 对于单例设备（如SenseVoice），不要调用destroy()，只停止监听
        // 只有在切换到不同类型的设备时才需要销毁
        val newDevice = buildInputDevice(setting)
        
        if (prevSttInputDevice != null && prevSttInputDevice !== newDevice) {
            // 不同的设备实例，停止旧设备的监听
            Log.d(TAG, "🛑 停止旧设备监听...")
            prevSttInputDevice.stopListening()
            
            // 只有非单例设备才需要销毁
            if (prevSttInputDevice !is SenseVoiceInputDevice) {
                Log.d(TAG, "🧹 销毁非单例设备...")
                prevSttInputDevice.destroy()
            }
        }
        
        // 切换到新设备
        inputDeviceSetting = setting
        sttInputDevice = newDevice
        
        Log.d(TAG, "✅ 设备切换完成")
        restartUiStateJob()
    }

    private fun buildInputDevice(setting: InputDevice): SttInputDevice? {
        Log.d(TAG, "🏗️ 构建STT输入设备: $setting")
        return when (setting) {
            UNRECOGNIZED,
            INPUT_DEVICE_UNSET -> {
                // 默认使用 SenseVoice
                Log.d(TAG, "   🎙️ 创建 SenseVoiceInputDevice (默认)")
                SenseVoiceInputDevice.getInstance(appContext, localeManager)
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
            INPUT_DEVICE_SHERPA_SIMULATE -> {
                Log.d(TAG, "   🎬 创建SherpaOnnxSimulateInputDevice")
                SherpaOnnxSimulateInputDevice(appContext, localeManager)
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
