package com.henriquesebastiao.downtify.core.player

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Engine choice belongs to this device. Playback arming never survives a process/account change. */
@Singleton
class LocalDjSettings @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("local-dj-beta-v1", Context.MODE_PRIVATE)
    private val selected = MutableStateFlow(preferences.getBoolean("device", false))
    val device = selected.asStateFlow()
    private val selectedMode = MutableStateFlow(
        LocalDjMode.entries.firstOrNull { it.name == preferences.getString("mode", "") } ?: LocalDjMode.Dj,
    )
    val mode = selectedMode.asStateFlow()
    private val enabled = MutableStateFlow(false)
    val armed = enabled.asStateFlow()
    private val progress = MutableStateFlow(LocalDjTransition())
    val transition = progress.asStateFlow()
    private val prepared = MutableStateFlow(LocalDjReadiness())
    val readiness = prepared.asStateFlow()
    private val revision = MutableStateFlow(0L)
    val preparationRevision = revision.asStateFlow()
    private val seconds = MutableStateFlow(
        (preferences.getInt("transition_seconds", DEFAULT_TRANSITION_SECONDS))
            .coerceIn(MIN_TRANSITION_SECONDS, MAX_TRANSITION_SECONDS),
    )

    /** How long a local crossfade lasts, in seconds: the user's choice, 1 to 12. */
    val transitionSeconds = seconds.asStateFlow()

    fun chooseDevice(value: Boolean) {
        if (selectedMode.value == LocalDjMode.Dj) enabled.value = false
        selected.value = value
        preferences.edit().putBoolean("device", value).apply()
    }
    fun setArmed(value: Boolean) {
        enabled.value = value && (selectedMode.value == LocalDjMode.Transitions || selected.value)
        if (!enabled.value) prepared.value = LocalDjReadiness()
    }

    fun setMode(value: LocalDjMode) {
        if (selectedMode.value == value) return
        enabled.value = false
        selectedMode.value = value
        prepared.value = LocalDjReadiness()
        preferences.edit().putString("mode", value.name).apply()
    }

    fun retryPlans() {
        revision.value++
    }

    internal fun publishReadiness(value: LocalDjReadiness) {
        prepared.value = value
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
