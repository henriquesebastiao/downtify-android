package com.henriquesebastiao.downtify.core.model

/**
 * What ends playback when the sleep timer runs out: a stretch of listening time, or the item that
 * is on. A timer keeps its mode whatever plays next, so an end-of-track timer picked while a
 * playlist runs stops the song that is on when it runs out.
 */
sealed interface SleepTimerMode {
    /**
     * Playback time from when it started ([SleepTimerState.afterMinutes]). The sheet offers
     * [SleepTimerState.PRESET_MINUTES]; any other number of minutes works too, kept within
     * [SleepTimerState.MIN_MINUTES]..[SleepTimerState.MAX_MINUTES].
     */
    data class AfterMinutes(val minutes: Int) : SleepTimerMode

    /** The song playing now: playback pauses where that song ends. */
    data object EndOfTrack : SleepTimerMode

    /** The podcast episode playing now, likewise. */
    data object EndOfEpisode : SleepTimerMode
}

/** How far a running [SleepTimerState] is. */
enum class SleepTimerPhase {
    /** No timer: the player plays to the end of the queue as always. */
    Inactive,

    /** Time is left; the volume is untouched. */
    Counting,

    /** The last [SleepTimerState.FADE_MS]: the volume is on its way to nothing. */
    Fading,
}

/**
 * A sleep timer as the service must remember it: Android may stop the service between two songs,
 * and the countdown has to pick up where it was rather than be forgotten. [remainingMs] is the
 * playback time left of an [SleepTimerMode.AfterMinutes] timer; an end-of-item timer counts from
 * the queue, so what is left of it is read again from the player instead.
 */
data class SavedSleepTimer(val mode: SleepTimerMode, val remainingMs: Long)

/**
 * The sleep timer: one immutable value the player advances with what it is doing, so it is pure
 * Kotlin and needs no clock of its own — [advance] takes the elapsed-realtime reading and the
 * player's progress, and counts only while audio runs (a paused player burns no listening time).
 *
 * Two ways to run out, both ending in [SleepTimerPhase.Fading] first so playback can be turned
 * down rather than cut: [SleepTimerMode.AfterMinutes] counts down the time the timer has been
 * playing (a seek or a skip changes nothing for it), and the end-of-item modes count the item's
 * own progress, so they end where the item does whatever the user seeks to.
 */
data class SleepTimerState(
    /** What ends playback, or null when there is no timer. */
    val mode: SleepTimerMode? = null,
    /** How much of an [SleepTimerMode.AfterMinutes] timer has been counted down: playback time only. */
    val countedMs: Long = 0,
    /** The clock reading of the last [advance]: only the time after it counts, never before. */
    val anchorMs: Long = 0,
    /** Where the item on is, for the end-of-item modes. */
    val positionMs: Long = 0,
    /** That item's length, 0 while the player doesn't know it (a stream, before it starts). */
    val durationMs: Long = 0,
) {
    /** A timer is armed: an expired one is still active until the player pauses and clears it. */
    val active: Boolean get() = mode != null

    /**
     * What is left of it: null when it cannot be known yet (an end-of-item timer whose item has no
     * length), 0 once it is out of time.
     */
    val remainingMs: Long?
        get() = when (val running = mode) {
            null -> null
            is SleepTimerMode.AfterMinutes -> (running.minutes * MINUTE_MS - countedMs).coerceAtLeast(0)
            SleepTimerMode.EndOfTrack, SleepTimerMode.EndOfEpisode -> {
                (durationMs - positionMs).takeIf { durationMs > 0 }?.coerceAtLeast(0)
            }
        }

    val phase: SleepTimerPhase
        get() {
            val left = remainingMs
            return when {
                mode == null -> SleepTimerPhase.Inactive
                left != null && left <= FADE_MS -> SleepTimerPhase.Fading
                else -> SleepTimerPhase.Counting
            }
        }

    /** Out of time: the player pauses now and the timer goes. */
    val expired: Boolean get() = remainingMs == 0L

    /**
     * The player's volume for [phase]: 1 until the fade starts, then down to 0 at the very end, so
     * the pause is heard as a fade rather than a cut.
     */
    val volume: Float
        get() {
            val left = remainingMs ?: return 1f
            return (left.toFloat() / FADE_MS).coerceIn(0f, 1f)
        }

    /**
     * One look at the player: [nowMs] is an elapsed-realtime reading, [playing] whether audio is
     * running, and the item's [positionMs] and [durationMs]. Without a timer it does nothing; with
     * one it adds the time since the last reading — never more than [MAX_STEP_MS], so a stall or a
     * suspended process cannot burn a timer in one go — and remembers the item's progress.
     */
    fun advance(nowMs: Long, playing: Boolean, positionMs: Long, durationMs: Long): SleepTimerState {
        if (mode == null) return this
        val step = if (playing) (nowMs - anchorMs).coerceIn(0L, MAX_STEP_MS) else 0L
        return copy(
            countedMs = countedMs + step,
            anchorMs = nowMs,
            positionMs = positionMs.coerceAtLeast(0),
            durationMs = durationMs.coerceAtLeast(0),
        )
    }

    /**
     * Adds [minutes] to a running timer: an [SleepTimerMode.AfterMinutes] one keeps its option and
     * grows, and an end-of-item one carries on as that many minutes plus what was left of the item.
     */
    fun extend(minutes: Int): SleepTimerState {
        val add = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        return when (val running = mode) {
            null -> this
            is SleepTimerMode.AfterMinutes -> copy(
                mode = SleepTimerMode.AfterMinutes((running.minutes + add).coerceIn(MIN_MINUTES, MAX_MINUTES)),
            )

            SleepTimerMode.EndOfTrack, SleepTimerMode.EndOfEpisode -> {
                val minutesLeft = ((remainingMs ?: 0L) / MINUTE_MS).toInt()
                copy(
                    mode = SleepTimerMode.AfterMinutes((minutesLeft + add).coerceIn(MIN_MINUTES, MAX_MINUTES)),
                    countedMs = 0,
                )
            }
        }
    }

    /** What a service restart is given: what it ends, and how much playback time is left of it. */
    fun saved(): SavedSleepTimer? = mode?.let { SavedSleepTimer(it, remainingMs ?: 0L) }

    companion object {
        /** No timer. */
        val Inactive = SleepTimerState()

        /** The durations the sheet offers, in minutes. */
        val PRESET_MINUTES = listOf(5, 10, 15, 30, 45, 60, 90)

        /** Anything else the user may type: from a minute to ten hours. */
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 600

        /** How long the volume takes to go from full to nothing before playback pauses. */
        const val FADE_MS = 5_000L

        /** The most playback time one [advance] may count, whatever the gap between two readings. */
        const val MAX_STEP_MS = 30_000L

        /** A timer for [minutes] of playback, starting at the elapsed-realtime reading [nowMs]. */
        fun afterMinutes(minutes: Int, nowMs: Long): SleepTimerState = SleepTimerState(
            mode = SleepTimerMode.AfterMinutes(minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)),
            anchorMs = nowMs,
        )

        /** A timer for the end of the song on, which is [positionMs] into its [durationMs]. */
        fun endOfTrack(positionMs: Long, durationMs: Long, nowMs: Long): SleepTimerState =
            endOfItem(SleepTimerMode.EndOfTrack, positionMs, durationMs, nowMs)

        /** A timer for the end of the episode on. */
        fun endOfEpisode(positionMs: Long, durationMs: Long, nowMs: Long): SleepTimerState =
            endOfItem(SleepTimerMode.EndOfEpisode, positionMs, durationMs, nowMs)

        /** A timer kept from before the service restarted: [nowMs] is when that service came back. */
        fun restored(saved: SavedSleepTimer, nowMs: Long): SleepTimerState = when (saved.mode) {
            is SleepTimerMode.AfterMinutes -> SleepTimerState(
                mode = saved.mode,
                countedMs = (saved.mode.minutes * MINUTE_MS - saved.remainingMs).coerceAtLeast(0),
                anchorMs = nowMs,
            )

            SleepTimerMode.EndOfTrack, SleepTimerMode.EndOfEpisode -> SleepTimerState(
                mode = saved.mode,
                anchorMs = nowMs,
            )
        }

        private fun endOfItem(mode: SleepTimerMode, positionMs: Long, durationMs: Long, nowMs: Long) =
            SleepTimerState(
                mode = mode,
                anchorMs = nowMs,
                positionMs = positionMs.coerceAtLeast(0),
                durationMs = durationMs.coerceAtLeast(0),
            )

        private const val MINUTE_MS = 60_000L
    }
}
