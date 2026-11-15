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
    private val seconds = MutableStateFlow(
        (preferences.getInt("transition_seconds", DEFAULT_TRANSITION_SECONDS))
            .coerceIn(MIN_TRANSITION_SECONDS, MAX_TRANSITION_SECONDS),
    )

    /** How long a local crossfade lasts, in seconds: the user's choice, 1 to 12. */
    val transitionSeconds = seconds.asStateFlow()

    fun chooseDevice(value: Boolean) {
        enabled.value = false
        selected.value = value
        preferences.edit().putBoolean("device", value).apply()
    }
    fun setArmed(value: Boolean) {
        enabled.value = value && selected.value
    }

    fun setTransitionSeconds(value: Int) {
        val bounded = value.coerceIn(MIN_TRANSITION_SECONDS, MAX_TRANSITION_SECONDS)
        seconds.value = bounded
        preferences.edit().putInt("transition_seconds", bounded).apply()
    }

    internal fun publish(value: LocalDjTransition) {
        progress.value = value
    }

    companion object {
        const val MIN_TRANSITION_SECONDS = 1
        const val MAX_TRANSITION_SECONDS = 12
        const val DEFAULT_TRANSITION_SECONDS = 5
    }
}
