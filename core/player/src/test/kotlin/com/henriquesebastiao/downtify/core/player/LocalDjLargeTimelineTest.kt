package com.henriquesebastiao.downtify.core.player

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalDjLargeTimelineTest {
    @Test fun `playback events do not materialize the entire library`() = runTest {
        val scope = backgroundScope
        fun readCounts(size: Int): Pair<Int, Int> {
            val native = CountingTimeline("first", size)
            val player = LocalDjPlayer(TimelineDeck(native), TimelineDeck(Timeline.EMPTY), scope)
            assertEquals(size, player.mediaItemCount)
            val timeline = player.currentTimeline
            native.windowsRead = 0
            native.periodsRead = 0
            repeat(20) { index ->
                player.volume = if (index % 2 == 0) 0.8f else 0.9f
                player.play()
                player.pause()
            }
            assertTrue("Transport events must exercise the state reader", native.windowsRead > 0)
            assertEquals(size, player.mediaItemCount)
            assertEquals(native.item, player.getMediaItemAt(size - 1))
            assertSame(timeline, player.currentTimeline)
            assertFalse(player.playWhenReady)
            val counts = native.windowsRead to native.periodsRead
            player.release()
            return counts
        }
        val small = readCounts(28)
        val large = readCounts(28_000)
        val windows = "${small.first}/${large.first}"
        val periods = "${small.second}/${large.second}"
        println("Transport reads for 28 and 28000 items: windows=$windows, periods=$periods")
        assertEquals("Window reads must not grow with queue size", small.first, large.first)
        assertEquals("Period reads must not grow with queue size", small.second, large.second)
    }

    @Test fun `construction is lazy and native shuffle navigation is retained`() {
        val native = CountingTimeline("first", 28_000)
        val facade = LocalDjTimeline(native, List(28_000) { Any() }, 1)
        assertEquals(0, native.windowsRead)
        assertEquals(0, native.periodsRead)
        assertEquals(27_999, facade.getFirstWindowIndex(true))
        assertEquals(0, facade.getNextWindowIndex(1, Player.REPEAT_MODE_OFF, true))
        assertEquals(27_999, facade.getNextWindowIndex(0, Player.REPEAT_MODE_ALL, true))
        assertTrue(facade.matches(native, 1))
        assertTrue(!facade.matches(native, 2))
    }

    @Test fun `duplicate local occurrences survive deck switches and move without mutating old timelines`() {
        val occurrences = List(2) { Any() }
        val before = LocalDjTimeline(CountingTimeline("first", 2), occurrences, 1)
        val after = LocalDjTimeline(CountingTimeline("second", 2), occurrences, 1)
        val first = before.getUidOfPeriod(0)
        val second = before.getUidOfPeriod(1)
        assertNotEquals(first, second)
        assertEquals(second, after.getUidOfPeriod(1))
        assertEquals(1, after.getIndexOfPeriod(second))
        val moved = LocalDjTimeline(CountingTimeline("second", 2), occurrences.reversed(), 2)
        assertEquals(0, moved.getIndexOfPeriod(second))
        assertEquals(1, before.getIndexOfPeriod(second))
        assertEquals(0, moved.getPeriodByUid(second, Timeline.Period()).windowIndex)
    }

    @Test fun `remote stream periods follow native identity changes`() {
        val occurrences = listOf(Any())
        val before = LocalDjTimeline(CountingTimeline("manifest-a", 1, local = false), occurrences, 1)
        val after = LocalDjTimeline(CountingTimeline("manifest-b", 1, local = false), occurrences, 1)
        assertEquals(C.INDEX_UNSET, after.getIndexOfPeriod(before.getUidOfPeriod(0)))
        assertEquals(0, after.getIndexOfPeriod(after.getUidOfPeriod(0)))
    }

    @Test fun `retained remote period UID survives a sliding manifest`() {
        val occurrences = listOf(Any())
        val before = LocalDjTimeline(
            CountingTimeline("live", 1, local = false, periodsPerWindow = 2),
            occurrences,
            1,
        )
        val after = LocalDjTimeline(
            CountingTimeline("live", 1, local = false, periodsPerWindow = 2, periodOffset = 1),
            occurrences,
            1,
        )
        val retained = before.getUidOfPeriod(1)
        assertEquals(0, after.getIndexOfPeriod(retained))
        assertEquals(retained, after.getUidOfPeriod(0))
        assertEquals(retained, after.getPeriodByUid(retained, Timeline.Period()).uid)
    }

    /** Constant-size native source: reading the whole queue is observable, not hidden in a fixture. */
    private class CountingTimeline(
        private val name: String,
        private val count: Int,
        local: Boolean = true,
        private val periodsPerWindow: Int = 1,
        private val periodOffset: Int = 0,
    ) : Timeline() {
        val item: MediaItem = if (local) {
            localDjItem(
                "duplicate",
            )
        } else {
            MediaItem.fromUri("https://example.invalid/live.m3u8")
        }
        var windowsRead = 0
        var periodsRead = 0

        override fun getWindowCount() = count
        override fun getPeriodCount() = count * periodsPerWindow
        override fun getWindow(index: Int, window: Window, defaultPositionProjectionUs: Long): Window {
            windowsRead++
            return window.set(
                "$name-window-$index", item, null, C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET,
                true, false, null, 0, 10_000_000L * periodsPerWindow,
                index * periodsPerWindow, (index + 1) * periodsPerWindow - 1, 0,
            )
        }
        override fun getPeriod(index: Int, period: Period, setIds: Boolean): Period {
            periodsRead++
            return period.set(
                null,
                if (setIds) getUidOfPeriod(index) else null,
                index / periodsPerWindow,
                10_000_000,
                (index % periodsPerWindow) * 10_000_000L,
            )
        }
        override fun getUidOfPeriod(index: Int): Any = "$name-period-${index + periodOffset}"
        override fun getIndexOfPeriod(uid: Any): Int {
            val index = (uid as? String)?.takeIf { it.startsWith("$name-period-") }
                ?.removePrefix("$name-period-")?.toIntOrNull()?.minus(periodOffset)
            return index?.takeIf { it in 0 until periodCount } ?: C.INDEX_UNSET
        }
        override fun getFirstWindowIndex(shuffleModeEnabled: Boolean): Int =
            if (shuffleModeEnabled) count - 1 else super.getFirstWindowIndex(false)
        override fun getNextWindowIndex(index: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int = when {
            !shuffleModeEnabled -> super.getNextWindowIndex(index, repeatMode, false)
            repeatMode == Player.REPEAT_MODE_ONE -> index
            index > 0 -> index - 1
            repeatMode == Player.REPEAT_MODE_ALL -> count - 1
            else -> C.INDEX_UNSET
        }
    }

    private class TimelineDeck(private val source: Timeline) : SimpleBasePlayer(Looper.getMainLooper()) {
        private var wantedPlay = false
        private var level = 1f
        override fun getState(): State = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(source, Tracks.EMPTY, MediaMetadata.EMPTY)
            .setCurrentMediaItemIndex(if (source.isEmpty) C.INDEX_UNSET else 0)
            .setVolume(level)
            .setPlayWhenReady(wantedPlay, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            wantedPlay = playWhenReady
            return Futures.immediateVoidFuture()
        }
        override fun handleSetVolume(volume: Float, volumeOperationType: Int): ListenableFuture<*> {
            level = volume
            return Futures.immediateVoidFuture()
        }
        override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
    }
}
