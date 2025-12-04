package com.ai.voice.io.wake

import com.ai.voice.util.Progress

sealed interface WakeState {
    /**
     * Should never be generated directly by a [com.ai.voice.WakeDevice]. In fact,
     * this is used directly in the UI layer, since permission checks can only be done there.
     */
    data object NoMicOrNotificationPermission : WakeState

    data object NotDownloaded : WakeState

    data class Downloading(
        val progress: Progress,
    ) : WakeState

    data class ErrorDownloading(
        val throwable: Throwable
    ) : WakeState

    data object NotLoaded : WakeState

    data object Loading : WakeState

    data class ErrorLoading(
        val throwable: Throwable
    ) : WakeState

    data object Loaded : WakeState
}
