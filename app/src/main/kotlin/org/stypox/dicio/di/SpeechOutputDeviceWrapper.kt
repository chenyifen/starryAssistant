package org.stypox.dicio.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.dicio.skill.context.SpeechOutputDevice
import org.stypox.dicio.io.speech.AndroidTtsSpeechDevice
import org.stypox.dicio.io.speech.CloudTtsSpeechDevice
import org.stypox.dicio.io.speech.NothingSpeechDevice
import org.stypox.dicio.io.speech.SherpaOnnxTtsSpeechDevice
import org.stypox.dicio.io.speech.SnackbarSpeechDevice
import org.stypox.dicio.io.speech.ToastSpeechDevice
import org.stypox.dicio.io.speech.WebSocketTtsSpeechDevice
import org.stypox.dicio.settings.datastore.SpeechOutputDevice.SPEECH_OUTPUT_DEVICE_ANDROID_TTS
import org.stypox.dicio.settings.datastore.SpeechOutputDevice.SPEECH_OUTPUT_DEVICE_CLOUD_TTS
import org.stypox.dicio.settings.datastore.SpeechOutputDevice.SPEECH_OUTPUT_DEVICE_NOTHING
import org.stypox.dicio.settings.datastore.SpeechOutputDevice.SPEECH_OUTPUT_DEVICE_SHERPA_ONNX_TTS
import org.stypox.dicio.settings.datastore.SpeechOutputDevice.SPEECH_OUTPUT_DEVICE_SNACKBAR
import org.stypox.dicio.settings.datastore.SpeechOutputDevice.SPEECH_OUTPUT_DEVICE_TOAST
import org.stypox.dicio.settings.datastore.SpeechOutputDevice.SPEECH_OUTPUT_DEVICE_UNSET
import org.stypox.dicio.settings.datastore.SpeechOutputDevice.SPEECH_OUTPUT_DEVICE_WEBSOCKET
import org.stypox.dicio.settings.datastore.SpeechOutputDevice.UNRECOGNIZED
import org.stypox.dicio.io.net.WebSocketProtocol
import org.stypox.dicio.util.WebSocketConfig
import org.stypox.dicio.settings.datastore.UserSettings
import org.stypox.dicio.settings.datastore.TtsFallbackDevice
import org.stypox.dicio.settings.datastore.TtsFallbackChain
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechOutputDeviceWrapper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<UserSettings>,
    private val localeManager: LocaleManager,
    // this is always instantiated, but will do nothing if
    // it is not the speech device chosen by the user
    private val snackbarSpeechDevice: SnackbarSpeechDevice,
) : SpeechOutputDevice {

    // instantiate SpeechOutputDevices on the main thread
    private val scope = CoroutineScope(Dispatchers.Main)
    private var wrappedSpeechDevice: SpeechOutputDevice = NothingSpeechDevice()
    private var webSocketProtocol: WebSocketProtocol? = null
    
    // 降级链配置：默认降级顺序
    private val defaultTtsFallbackChain = listOf(
        TtsFallbackDevice.TTS_FALLBACK_DEVICE_WEBSOCKET,
        TtsFallbackDevice.TTS_FALLBACK_DEVICE_SHERPA_ONNX,
        TtsFallbackDevice.TTS_FALLBACK_DEVICE_ANDROID_TTS,
        TtsFallbackDevice.TTS_FALLBACK_DEVICE_TOAST,
        TtsFallbackDevice.TTS_FALLBACK_DEVICE_SNACKBAR
    )
    
    private var currentFallbackChain: List<TtsFallbackDevice> = defaultTtsFallbackChain
    private var currentFallbackIndex = 0

    init {
        scope.launch {
            dataStore.data
                .combine(localeManager.locale) { userSettings, locale ->
                    Triple(userSettings.ttsFallbackChain, userSettings.speechOutputDevice, locale)
                }
                .distinctUntilChanged()
                .collect { (fallbackChain, setting, locale) ->
                    // 更新降级链配置
                    currentFallbackChain = if (fallbackChain != null && fallbackChain.devicesList.isNotEmpty()) {
                        Log.d(TAG, "📋 使用自定义TTS降级链: ${fallbackChain.devicesList}")
                        fallbackChain.devicesList
                    } else {
                        Log.d(TAG, "📋 使用默认TTS降级链: $defaultTtsFallbackChain")
                        defaultTtsFallbackChain
                    }
                    
                    // 重置降级索引
                    currentFallbackIndex = 0
                    
                    // 初始化TTS设备（使用降级链）
                    val prevDevice = wrappedSpeechDevice
                    wrappedSpeechDevice = tryCreateTtsDeviceWithFallback(locale)
                    prevDevice.cleanup()
                }
        }
    }

    /**
     * 尝试使用降级链创建TTS设备
     */
    private suspend fun tryCreateTtsDeviceWithFallback(locale: java.util.Locale): SpeechOutputDevice {
        for (i in currentFallbackIndex until currentFallbackChain.size) {
            val deviceType = currentFallbackChain[i]
            try {
                val device = createTtsDevice(deviceType, locale)
                if (device != null) {
                    currentFallbackIndex = i
                    Log.i(TAG, "✅ TTS降级链: 使用 ${deviceType.name} (索引: $i)")
                    return device
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ TTS降级链: ${deviceType.name} 创建失败: ${e.message}, 尝试下一个")
            }
        }
        
        // 所有设备都失败，返回NothingSpeechDevice作为最后的保底
        Log.e(TAG, "❌ TTS降级链: 所有设备创建失败，使用 NothingSpeechDevice")
        return NothingSpeechDevice()
    }
    
    /**
     * 根据类型创建TTS设备
     */
    private suspend fun createTtsDevice(deviceType: TtsFallbackDevice, locale: java.util.Locale): SpeechOutputDevice? {
        return when (deviceType) {
            TtsFallbackDevice.TTS_FALLBACK_DEVICE_WEBSOCKET -> {
                if (!WebSocketConfig.isWebSocketAvailable(context)) {
                    Log.w(TAG, "⚠️ WebSocket配置不可用")
                    return null
                }
                
                // 创建或重用 WebSocket 协议实例
                if (webSocketProtocol == null) {
                    webSocketProtocol = WebSocketProtocol(
                        context = context,
                        serverUrl = WebSocketConfig.getWebSocketUrl(context),
                        accessToken = WebSocketConfig.getAccessToken(context),
                        deviceId = WebSocketConfig.getDeviceId(context),
                        clientId = WebSocketConfig.getClientId(context)
                    )
                    
                    // 尝试连接
                    val connected = webSocketProtocol?.connect() ?: false
                    if (!connected) {
                        Log.w(TAG, "⚠️ WebSocket连接失败")
                        webSocketProtocol = null
                        return null
                    }
                }
                
                WebSocketTtsSpeechDevice(context, webSocketProtocol!!)
            }
            TtsFallbackDevice.TTS_FALLBACK_DEVICE_SHERPA_ONNX -> {
                try {
                    SherpaOnnxTtsSpeechDevice(context, locale)
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ SherpaOnnxTtsSpeechDevice创建失败: ${e.message}")
                    null
                }
            }
            TtsFallbackDevice.TTS_FALLBACK_DEVICE_ANDROID_TTS -> {
                try {
                    AndroidTtsSpeechDevice(context, locale)
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ AndroidTtsSpeechDevice创建失败: ${e.message}")
                    null
                }
            }
            TtsFallbackDevice.TTS_FALLBACK_DEVICE_CLOUD_TTS -> {
                try {
                    CloudTtsSpeechDevice(context)
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ CloudTtsSpeechDevice创建失败: ${e.message}")
                    null
                }
            }
            TtsFallbackDevice.TTS_FALLBACK_DEVICE_TOAST -> {
                ToastSpeechDevice(context)
            }
            TtsFallbackDevice.TTS_FALLBACK_DEVICE_SNACKBAR -> {
                snackbarSpeechDevice
            }
            TtsFallbackDevice.TTS_FALLBACK_DEVICE_NOTHING -> {
                NothingSpeechDevice()
            }
            else -> {
                Log.w(TAG, "⚠️ 未知的TTS设备类型: $deviceType")
                null
            }
        }
    }

    override fun speak(speechOutput: String) {
        // 在每次speak调用前检查当前设备是否可用
        scope.launch {
            if (!isCurrentDeviceAvailable()) {
                Log.w(TAG, "⚠️ 当前TTS设备不可用，尝试降级")
                // 尝试降级到下一个设备
                currentFallbackIndex++
                val newDevice = tryCreateTtsDeviceWithFallback(localeManager.locale.value)
                wrappedSpeechDevice.cleanup()
                wrappedSpeechDevice = newDevice
            }
            
            wrappedSpeechDevice.speak(speechOutput)
        }
    }
    
    /**
     * 检查当前设备是否可用
     */
    private fun isCurrentDeviceAvailable(): Boolean {
        return when (wrappedSpeechDevice) {
            is WebSocketTtsSpeechDevice -> {
                val connectionState = webSocketProtocol?.connectionState?.value
                connectionState is org.stypox.dicio.io.net.ConnectionState.Connected
            }
            is NothingSpeechDevice -> {
                // NothingSpeechDevice表示降级链已耗尽，返回false触发重新尝试
                false
            }
            else -> {
                // 其他设备默认认为可用
                true
            }
        }
    }

    override fun stopSpeaking() {
        wrappedSpeechDevice.stopSpeaking()
    }

    override val isSpeaking: Boolean
        get() = wrappedSpeechDevice.isSpeaking

    override fun runWhenFinishedSpeaking(runnable: Runnable) {
        wrappedSpeechDevice.runWhenFinishedSpeaking(runnable)
    }

    override fun cleanup() {
        // never called, nothing to do
        Log.w(TAG, "Unexpected call to SpeechOutputDeviceWrapper.cleanup()")
    }

    companion object {
        val TAG: String = SpeechOutputDeviceWrapper::class.simpleName!!
    }
}
