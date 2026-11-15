package com.henriquesebastiao.downtify.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sleep timer's countdown, on a fake clock: a timer of minutes counts the time playback runs,
 * an end-of-item one follows the item, and both fade before they pause.
 */
class SleepTimerTest {
    private val track = 239_000L

    /** [ms] of playback, in the one-second readings the service makes. */
    private fun SleepTimerState.play(ms: Long, durationMs: Long = 0L, step: Long = 1_000L): SleepTimerState {
        var timer = this
        var left = ms
        while (left > 0) {
            val move = minOf(step, left)
            timer = timer.advance(
                nowMs = timer.anchorMs + move,
                playing = true,
                positionMs = timer.positionMs + move,
                durationMs = durationMs,
            )
            left -= move
        }
        return timer
    }

    /** [ms] with playback paused: the clock moves on, the countdown does not. */
    private fun SleepTimerState.waitFor(ms: Long): SleepTimerState = advance(
        nowMs = anchorMs + ms,
        playing = false,
        positionMs = positionMs,
        durationMs = durationMs,
    )

    @Test
    fun aTimerOfMinutesCountsOnlyTheTimeItPlays() {
        var timer = SleepTimerState.afterMinutes(10, nowMs = 0)
        assertEquals(10 * MINUTE, timer.remainingMs)
        assertEquals(SleepTimerPhase.Counting, timer.phase)

        timer = timer.play(MINUTE)
        assertEquals(9 * MINUTE, timer.remainingMs)

        // Nine minutes paused: the clock passes, the countdown stays where it was.
        timer = timer.waitFor(9 * MINUTE)
        assertEquals(9 * MINUTE, timer.remainingMs)

        timer = timer.play(MINUTE)
        assertEquals(8 * MINUTE, timer.remainingMs)
    }

    @Test
    fun theLastFiveSecondsFadeAndThenItIsOutOfTime() {
        val timer = SleepTimerState.afterMinutes(5, nowMs = 0).play(4 * MINUTE + 57_000)

        assertEquals(SleepTimerPhase.Fading, timer.phase)
        assertEquals(3_000L, timer.remainingMs)
        assertEquals(0.6f, timer.volume, VOLUME_DELTA)

        val over = timer.play(3_000)
        assertTrue(over.expired)
        assertEquals(0L, over.remainingMs)
        assertEquals(0f, over.volume, VOLUME_DELTA)
    }

    @Test
    fun anEndOfTrackTimerFollowsTheSong() {
        var timer = SleepTimerState.endOfTrack(positionMs = 200_000, durationMs = track, nowMs = 0)
        assertEquals(39_000L, timer.remainingMs)

        timer = timer.play(38_000, durationMs = track)
        assertEquals(SleepTimerPhase.Fading, timer.phase)
        assertEquals(1_000L, timer.remainingMs)

        timer = timer.play(1_000, durationMs = track)
        assertTrue(timer.expired)
    }

    @Test
    fun aPausedSongBurnsNoTime() {
        val timer = SleepTimerState.endOfTrack(positionMs = 1_000, durationMs = track, nowMs = 0)
            .waitFor(60 * MINUTE)

        assertEquals(track - 1_000, timer.remainingMs)
    }

    @Test
    fun seekingBackGivesAnEndOfTrackTimerItsTimeBack() {
        val near = SleepTimerState.endOfTrack(positionMs = 236_000, durationMs = track, nowMs = 0)
        assertEquals(SleepTimerPhase.Fading, near.phase)

        val back = near.advance(nowMs = 1_000, playing = true, positionMs = 60_000, durationMs = track)
        assertEquals(track - 60_000, back.remainingMs)
        assertEquals(SleepTimerPhase.Counting, back.phase)
        assertEquals(1f, back.volume, VOLUME_DELTA)
    }

    @Test
    fun anItemWithNoKnownLengthHasNoCountdown() {
        val timer = SleepTimerState.endOfEpisode(positionMs = 0, durationMs = 0, nowMs = 0).play(60_000)

        assertNull(timer.remainingMs)
        assertFalse(timer.expired)
        assertEquals(SleepTimerPhase.Counting, timer.phase)
        assertEquals(1f, timer.volume, VOLUME_DELTA)
    }

    @Test
    fun extendingATimerOfMinutesKeepsItsChoice() {
        val timer = SleepTimerState.afterMinutes(30, nowMs = 0).play(10 * MINUTE)
        assertEquals(20 * MINUTE, timer.remainingMs)

        val longer = timer.extend(5)
        assertEquals(SleepTimerMode.AfterMinutes(35), longer.mode)
        assertEquals(25 * MINUTE, longer.remainingMs)
    }

    @Test
    fun extendingAnEndOfItemTimerCarriesOnAsMinutes() {
        val timer = SleepTimerState.endOfTrack(positionMs = 200_000, durationMs = track, nowMs = 0)

        val longer = timer.extend(10)
        assertEquals(SleepTimerMode.AfterMinutes(10), longer.mode)
        assertEquals(10 * MINUTE, longer.remainingMs)
    }

    @Test
    fun aKeptTimerOfMinutesComesBackWithWhatWasLeftOfIt() {
        val running = SleepTimerState.afterMinutes(30, nowMs = 0).play(12 * MINUTE)

        val saved = running.saved()
        assertEquals(SavedSleepTimer(SleepTimerMode.AfterMinutes(30), 18 * MINUTE), saved)

        // The service comes back: nothing is counted for the time it was away.
        val back = SleepTimerState.restored(requireNotNull(saved), nowMs = 5_000)
        assertEquals(18 * MINUTE, back.remainingMs)
        assertEquals(18 * MINUTE - 1_000, back.play(1_000).remainingMs)
    }

    @Test
    fun aKeptEndOfItemTimerReadsTheItemAgain() {
        val saved = SleepTimerState.endOfTrack(positionMs = 200_000, durationMs = track, nowMs = 0).saved()
        assertEquals(SleepTimerMode.EndOfTrack, saved?.mode)

        // Its length is not kept: the player gives it back with the first reading.
        val back = SleepTimerState.restored(requireNotNull(saved), nowMs = 0)
        assertNull(back.remainingMs)
        val again = back.advance(nowMs = 250, playing = true, positionMs = 200_000, durationMs = track)
        assertEquals(39_000L, again.remainingMs)
    }

    @Test
    fun withoutATimerNothingIsCountedAndNothingIsSaved() {
        val idle = SleepTimerState.Inactive.advance(
            nowMs = 60 * MINUTE,
            playing = true,
            positionMs = 5_000,
            durationMs = 239_000,
        )

        assertEquals(SleepTimerState.Inactive, idle)
        assertFalse(idle.active)
        assertEquals(SleepTimerPhase.Inactive, idle.phase)
        assertNull(idle.remainingMs)
        assertNull(idle.saved())
    }

    @Test
    fun aLongGapBetweenTwoReadingsCountsNoMoreThanAMoment() {
        // A stalled process must not burn the whole timer the moment it wakes up.
        val timer = SleepTimerState.afterMinutes(60, nowMs = 0)
            .advance(nowMs = 10 * MINUTE, playing = true, positionMs = 0, durationMs = 0)

        assertEquals(60 * MINUTE - SleepTimerState.MAX_STEP_MS, timer.remainingMs)
    }

    @Test
    fun anyNumberOfMinutesIsKeptWithinTheTimersOwnBounds() {
        assertEquals(60 * MINUTE, SleepTimerState.afterMinutes(0, nowMs = 0).remainingMs)
        assertEquals(600 * MINUTE, SleepTimerState.afterMinutes(1_000, nowMs = 0).remainingMs)
        assertEquals(7 * MINUTE, SleepTimerState.afterMinutes(7, nowMs = 0).remainingMs)
    }

    private companion object {
        const val MINUTE = 60_000L
        const val VOLUME_DELTA = 0.001f
    }
}
