package com.ai.voice.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import com.ai.voice.io.wake.WakeDevice
import com.ai.voice.io.wake.WakeState
import com.ai.voice.io.wake.oww.OpenWakeWordDevice
import com.ai.voice.io.wake.oww.HiNudgeOpenWakeWordDevice
import com.ai.voice.io.wake.onnx.HiNudgeOnnxWakeDevice
import com.ai.voice.io.wake.onnx.HiNudgeOnnxV8WakeDevice
import com.ai.voice.io.wake.sherpa.SherpaOnnxWakeDevice
import com.ai.voice.settings.datastore.UserSettings
import com.ai.voice.util.DebugLogger
import com.ai.voice.settings.datastore.WakeDevice.UNRECOGNIZED
import com.ai.voice.settings.datastore.WakeDevice.WAKE_DEVICE_NOTHING
import com.ai.voice.settings.datastore.WakeDevice.WAKE_DEVICE_OWW
import com.ai.voice.settings.datastore.WakeDevice.WAKE_DEVICE_SHERPA_ONNX
import com.ai.voice.settings.datastore.WakeDevice.WAKE_DEVICE_HI_NUDGE
import com.ai.voice.settings.datastore.WakeDevice.WAKE_DEVICE_HI_NUDGE_V8
import com.ai.voice.settings.datastore.WakeDevice.WAKE_DEVICE_UNSET
import com.ai.voice.util.distinctUntilChangedBlockingFirst
import javax.inject.Singleton

interface WakeDeviceWrapper {
    val state: StateFlow<WakeState?>
    val isHeyDicio: StateFlow<Boolean>

    fun download()
    fun processFrame(audio16bitPcm: ShortArray): Boolean
    fun frameSize(): Int
    fun reinitializeToReleaseResources()
}

typealias DataStoreWakeDevice = com.ai.voice.settings.datastore.WakeDevice

class WakeDeviceWrapperImpl(
    @ApplicationContext private val appContext: Context,
    dataStore: DataStore<UserSettings>,
    private val okHttpClient: OkHttpClient,
) : WakeDeviceWrapper {
    private val scope = CoroutineScope(Dispatchers.Default)

    private var currentSetting: DataStoreWakeDevice
    private var lastFrameHadWrongSize = false

    // null means that the user has not enabled any STT input device
    private val _state: MutableStateFlow<WakeState?> = MutableStateFlow(null)
    override val state: StateFlow<WakeState?> = _state
    private val _isHeyDicio: MutableStateFlow<Boolean>
    override val isHeyDicio: StateFlow<Boolean>
    private val currentDevice: MutableStateFlow<WakeDevice?>

    init {
        Log.d("WakeDeviceWrapper", "🏗️ [INIT] WakeDeviceWrapper初始化开始")
        // Run blocking, because the data store is always available right away since LocaleManager
        // also initializes in a blocking way from the same data store.
        val (firstWakeDeviceSetting, nextWakeDeviceFlow) = dataStore.data
            .map { it.wakeDevice }
            .distinctUntilChangedBlockingFirst()

        Log.d("WakeDeviceWrapper", "📝 [INIT] 读取配置完成: $firstWakeDeviceSetting")
        currentSetting = firstWakeDeviceSetting
        Log.d("WakeDeviceWrapper", "🔨 [INIT] 开始构建WakeDevice")
        val firstWakeDevice = buildInputDevice(firstWakeDeviceSetting)
        Log.d("WakeDeviceWrapper", "✅ [INIT] WakeDevice构建完成")
        currentDevice = MutableStateFlow(firstWakeDevice)
        _isHeyDicio = MutableStateFlow(firstWakeDevice?.isHeyDicio() ?: true)
        isHeyDicio = _isHeyDicio

        scope.launch {
            currentDevice.collectLatest { newWakeDevice ->
                _isHeyDicio.emit(newWakeDevice?.isHeyDicio() ?: true)
                if (newWakeDevice == null) {
                    _state.emit(null)
                } else {
                    newWakeDevice.state.collect { _state.emit(it) }
                }
            }
        }

        scope.launch {
            nextWakeDeviceFlow.collect(::changeWakeDeviceTo)
        }
    }

    private fun changeWakeDeviceTo(setting: DataStoreWakeDevice) {
        DebugLogger.logWakeWord("WakeDeviceWrapper", "🔄 Changing wake device to: $setting")
        currentSetting = setting
        val newWakeDevice = buildInputDevice(setting)
        DebugLogger.logWakeWord("WakeDeviceWrapper", "🏗️ Built wake device: ${newWakeDevice?.javaClass?.simpleName}")
        lastFrameHadWrongSize = false
        currentDevice.update { prevWakeDevice ->
            prevWakeDevice?.destroy()
            newWakeDevice
        }
    }

    private fun buildInputDevice(setting: DataStoreWakeDevice): WakeDevice? {
        return when (setting) {
            UNRECOGNIZED,
            WAKE_DEVICE_UNSET -> SherpaOnnxWakeDevice(appContext) // 默认使用SherpaOnnx KWS
            WAKE_DEVICE_OWW -> OpenWakeWordDevice(appContext, okHttpClient)
            WAKE_DEVICE_SHERPA_ONNX -> SherpaOnnxWakeDevice(appContext)
            WAKE_DEVICE_HI_NUDGE -> HiNudgeOnnxWakeDevice(appContext)
            WAKE_DEVICE_HI_NUDGE_V8 -> HiNudgeOnnxV8WakeDevice(appContext)
            WAKE_DEVICE_NOTHING -> null
        }
    }

    override fun download() {
        currentDevice.value?.download()
    }

    override fun processFrame(audio16bitPcm: ShortArray): Boolean {
        val device = currentDevice.value
            ?: return false // 如果没有设备则返回false而不是抛异常
        
        // 检查设备状态，只有在Loaded状态才处理音频
        if (device.state.value != WakeState.Loaded) {
            // 设备未就绪，直接返回false
            return false
        }

        if (audio16bitPcm.size != device.frameSize()) {
            if (lastFrameHadWrongSize) {
                // a single badly-sized frame may happen when switching wake device, so we can
                // tolerate it, but otherwise it is a programming error and should be reported
                throw IllegalArgumentException("Wrong audio frame size: expected ${
                    device.frameSize()} samples but got ${audio16bitPcm.size}")
            }
            lastFrameHadWrongSize = true
            return false

        } else {
            // process the frame only if it has the correct size
            lastFrameHadWrongSize = false
            return try {
                device.processFrame(audio16bitPcm)
            } catch (e: Exception) {
                // 捕获任何异常，防止崩溃
                Log.w(TAG, "❌ Error processing wake word frame: ${e.message}")
                false
            }
        }
    }

    override fun frameSize(): Int {
        return currentDevice.value?.frameSize() ?: 0
    }

    override fun reinitializeToReleaseResources() {
        changeWakeDeviceTo(currentSetting)
    }
    
    companion object {
        private const val TAG = "WakeDeviceWrapper"
    }
}

@Module
@InstallIn(SingletonComponent::class)
class WakeDeviceWrapperModule {
    @Provides
    @Singleton
    fun provideWakeDeviceWrapper(
        @ApplicationContext appContext: Context,
        dataStore: DataStore<UserSettings>,
        okHttpClient: OkHttpClient,
    ): WakeDeviceWrapper {
        return WakeDeviceWrapperImpl(appContext, dataStore, okHttpClient)
    }
}
