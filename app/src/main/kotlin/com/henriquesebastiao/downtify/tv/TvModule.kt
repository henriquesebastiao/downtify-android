package com.henriquesebastiao.downtify.tv

import android.content.Context
import com.henriquesebastiao.downtify.core.data.settings.DeviceDefaults
import com.henriquesebastiao.downtify.core.data.settings.ThemeMode
import com.henriquesebastiao.downtify.core.model.AppFont
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/** The settings' defaults for this device: on a TV, Downtify classic (whose fonts the APK carries) and dark. */
@Module
@InstallIn(SingletonComponent::class)
object DeviceDefaultsModule {
    @Provides
    fun deviceDefaults(@ApplicationContext context: Context): DeviceDefaults = if (context.isTelevision()) {
        DeviceDefaults(font = AppFont.Downtify, theme = ThemeMode.Dark)
    } else {
        DeviceDefaults()
    }
}
