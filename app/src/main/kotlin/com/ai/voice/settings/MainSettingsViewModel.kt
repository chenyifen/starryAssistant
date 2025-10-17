package com.ai.voice.settings

import android.app.Application
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.ai.voice.di.WakeDeviceWrapper
import com.ai.voice.io.wake.oww.OpenWakeWordDevice
import com.ai.voice.settings.datastore.InputDevice
import com.ai.voice.settings.datastore.Language
import com.ai.voice.settings.datastore.SpeechOutputDevice
import com.ai.voice.settings.datastore.SttPlaySound
import com.ai.voice.settings.datastore.Theme
import com.ai.voice.settings.datastore.UserSettings
import com.ai.voice.settings.datastore.WakeDevice
import com.ai.voice.util.toStateFlowDistinctBlockingFirst
import javax.inject.Inject

@HiltViewModel
class MainSettingsViewModel @Inject constructor(
    application: Application,
    private val wakeDeviceWrapper: WakeDeviceWrapper?,
    private val dataStore: DataStore<UserSettings>
) : AndroidViewModel(application) {
    // run blocking because the settings screen cannot start if settings have not been loaded yet
    val settingsState = dataStore.data
        .toStateFlowDistinctBlockingFirst(viewModelScope)

    private fun updateData(transform: (UserSettings.Builder) -> Unit) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .apply(transform)
                    .build()
            }
        }
    }

    val isHeyDicio: StateFlow<Boolean> = wakeDeviceWrapper?.isHeyDicio ?: MutableStateFlow(true)

    fun addOwwUserWakeFile(uri: Uri) {
        viewModelScope.launch {
            OpenWakeWordDevice.addUserWakeFile(getApplication(), uri)
            wakeDeviceWrapper?.reinitializeToReleaseResources()
        }
    }

    fun removeOwwUserWakeFile() {
        viewModelScope.launch {
            OpenWakeWordDevice.removeUserWakeFile(getApplication())
            wakeDeviceWrapper?.reinitializeToReleaseResources()
        }
    }

    fun setLanguage(value: Language) =
        updateData { it.setLanguage(value) }
    fun setTheme(value: Theme) =
        updateData { it.setTheme(value) }
    fun setDynamicColors(value: Boolean) =
        updateData { it.setDynamicColors(value) }
    fun setInputDevice(value: InputDevice) =
        updateData { it.setInputDevice(value) }
    fun setWakeDevice(value: WakeDevice) =
        updateData { it.setWakeDevice(value) }
    fun setSpeechOutputDevice(value: SpeechOutputDevice) =
        updateData { it.setSpeechOutputDevice(value) }
    fun setSttPlaySound(value: SttPlaySound) =
        updateData { it.setSttPlaySound(value) }
    fun setAutoFinishSttPopup(value: Boolean) =
        updateData { it.setAutoFinishSttPopup(value) }
    fun setPauseWakeDuringAsr(value: Boolean) =
        updateData { it.setPauseWakeDuringAsr(value) }
}
