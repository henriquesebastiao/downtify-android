package com.henriquesebastiao.downtify.core.player

import android.content.Context
import com.henriquesebastiao.downtify.core.data.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Engine choice belongs to this device. Playback arming never survives a process/account change. */
@Singleton
class LocalDjSettings @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("local-dj-beta-v1", Context.MODE_PRIVATE)
    private val selected = MutableStateFlow(preferences.getBoolean("device", false))
    val device = selected.asStateFlow()
    private val enabled = MutableStateFlow(false)
    val armed = enabled.asStateFlow()
    private val progress = MutableStateFlow(LocalDjTransition())
    val transition = progress.asStateFlow()

    fun chooseDevice(value: Boolean) {
        enabled.value = false
        selected.value = value
        preferences.edit().putBoolean("device", value).apply()
    }
    fun setArmed(value: Boolean) {
        enabled.value = value && selected.value
    }
    internal fun publish(value: LocalDjTransition) {
        progress.value = value
    }
}
