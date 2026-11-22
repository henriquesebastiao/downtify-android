package com.henriquesebastiao.downtify.core.player

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest

/** Owned by the foreground media service, independent of activity/controller releases. */
@Singleton
class PlaybackSleepTimer internal constructor(private val now: () -> Long) {
    @Inject constructor() : this(System::currentTimeMillis)
    private val mutableDeadline = MutableStateFlow<Long?>(null)
    val deadline = mutableDeadline.asStateFlow()

    fun set(minutes: Int?) {
        require(minutes == null || minutes in PRESETS)
        mutableDeadline.value = minutes?.let { now() + it * MILLIS_PER_MINUTE }
    }

    suspend fun run(pause: () -> Unit) {
        deadline.collectLatest { at ->
            if (at != null) {
                delay((at - now()).coerceAtLeast(0))
                if (mutableDeadline.value == at) {
                    mutableDeadline.value = null
                    pause()
                }
            }
        }
    }

    companion object {
        val PRESETS = listOf(5, 10, 20, 30, 45, 60)
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}
