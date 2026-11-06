package com.henriquesebastiao.downtify.core.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSleepTimerTest {
    @Test fun `every requested duration pauses once and clears its deadline`() = runTest {
        val timer = PlaybackSleepTimer { testScheduler.currentTime }
        var pauses = 0
        backgroundScope.launch { timer.run { pauses++ } }
        PlaybackSleepTimer.PRESETS.forEachIndexed { index, minutes ->
            timer.set(minutes)
            runCurrent()
            advanceTimeBy(minutes * 60_000L - 1)
            assertEquals(index, pauses)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(index + 1, pauses)
            assertNull(timer.deadline.value)
        }
    }

    @Test fun `replacing and cancelling a timer cannot pause later playback`() = runTest {
        val timer = PlaybackSleepTimer { testScheduler.currentTime }
        var pauses = 0
        backgroundScope.launch { timer.run { pauses++ } }
        timer.set(5)
        runCurrent()
        timer.set(10)
        runCurrent()
        advanceTimeBy(5 * 60_000L)
        assertEquals(0, pauses)
        timer.set(null)
        runCurrent()
        advanceTimeBy(60 * 60_000L)
        runCurrent()
        assertEquals(0, pauses)
        assertNull(timer.deadline.value)
    }
}
