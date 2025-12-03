package com.ai.voice.io.wake

import kotlinx.coroutines.flow.StateFlow

interface WakeDevice {
    val state: StateFlow<WakeState>

    fun download()

    /**
     * This is blocking and should be called only from the background service.
     * @return true if the wake word was detected
     */
    fun processFrame(audio16bitPcm: ShortArray): Boolean

    /**
     * The size of audio frames passed to [processFrame]
     */
    fun frameSize(): Int

    /**
     * Reset internal state (e.g., clear feature buffers)
     * Should be called when AudioRecord is restarted to avoid using stale data
     */
    fun reset()

    fun destroy()

    /**
     * Returns `true` if the wake word is "Hey Dicio", `false` if a custom model is being used
     */
    fun isHeyDicio(): Boolean
}
