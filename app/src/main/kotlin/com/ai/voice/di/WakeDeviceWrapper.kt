package com.ai.voice.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.ai.voice.io.wake.onnx.HiNudgeOnnxV8WakeDevice
import javax.inject.Singleton

// 类型别名，保持兼容性
typealias WakeDeviceWrapper = HiNudgeOnnxV8WakeDevice

@Module
@InstallIn(SingletonComponent::class)
class WakeDeviceWrapperModule {
    @Provides
    @Singleton
    fun provideWakeDevice(
        @ApplicationContext appContext: Context,
    ): HiNudgeOnnxV8WakeDevice {
        return HiNudgeOnnxV8WakeDevice(appContext)
    }
}
