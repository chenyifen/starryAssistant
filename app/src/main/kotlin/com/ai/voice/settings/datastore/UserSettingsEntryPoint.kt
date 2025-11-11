package com.ai.voice.settings.datastore

import androidx.datastore.core.DataStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * EntryPoint用于在非注入环境中访问DataStore
 * 例如：在Application.onCreate()、WakeService、AsrHandler等非注入类中使用
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface UserSettingsEntryPoint {
    fun userSettings(): DataStore<UserSettings>
}

