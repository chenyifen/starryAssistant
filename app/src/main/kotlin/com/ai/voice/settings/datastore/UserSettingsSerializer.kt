package com.ai.voice.settings.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object UserSettingsSerializer : Serializer<UserSettings> {
    override val defaultValue: UserSettings = UserSettings.getDefaultInstance()
        .toBuilder()
        .setLanguage(Language.LANGUAGE_KO) // 默认韩语
        .setWakeDevice(WakeDevice.WAKE_DEVICE_HI_NUDGE_V8) // 默认V8唤醒
        .setInputDevice(InputDevice.INPUT_DEVICE_SENSEVOICE) // 默认SenseVoice ASR
        .setAutoFinishSttPopup(true)
        .setPauseWakeDuringAsr(true) // 默认启用：ASR时暂停唤醒服务
        .build()

    override suspend fun readFrom(input: InputStream): UserSettings {
        try {
            return UserSettings.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto", exception)
        }
    }

    override suspend fun writeTo(t: UserSettings, output: OutputStream) {
        t.writeTo(output)
    }
}
