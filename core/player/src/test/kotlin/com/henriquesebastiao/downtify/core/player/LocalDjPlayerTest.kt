package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalDjPlayerTest {
    @Test fun `one facade preserves duplicate queue UIDs and exposes the dominant source`() = runTest {
        val first = LocalDjFakeDeck()
        val second = LocalDjFakeDeck()
        val player = LocalDjPlayer(first, second, this)
        player.setMediaItems(listOf(localDjItem("a"), localDjItem("a"), localDjItem("b")))
        player.prepare()
        player.play()
        val before = (0 until player.mediaItemCount).map { player.currentTimeline.getWindow(it, Timeline.Window()).uid }
        player.setEnabled(true)
        player.pulseForTest()
        first.position(8800)
        player.pulseForTest()
        first.position(9400)
        second.position(600)
        player.pulseForTest()
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals(600L, player.currentPosition)
        val after = (0 until player.mediaItemCount).map { player.currentTimeline.getWindow(it, Timeline.Window()).uid }
        assertEquals(before, after)
        assertTrue(before[0] != before[1])
        assertEquals(1f, player.volume, 0.001f)
        player.pause()
        assertFalse(first.playWhenReady)
        assertFalse(second.playWhenReady)
        player.seekTo(2, 500)
        assertEquals(2, player.currentMediaItemIndex)
        assertEquals(500L, player.currentPosition)
        assertEquals(0, first.mediaItemCount)
        player.release()
        assertEquals(1, first.releases)
        assertEquals(1, second.releases)
    }

    @Test fun `owner reset clears both transports and denies retired preparation`() = runTest {
        val first = LocalDjFakeDeck()
        val second = LocalDjFakeDeck()
        val player = LocalDjPlayer(first, second, this)
        player.setMediaItems(listOf(localDjItem("a"), localDjItem("b")))
        player.prepare()
        player.play()
        player.setEnabled(true)
        player.pulseForTest()
        assertTrue(second.mediaItemCount > 0)
        player.resetForOwner()
        player.pulseForTest()
        assertEquals(0, first.mediaItemCount)
        assertEquals(0, second.mediaItemCount)
        assertFalse(player.playWhenReady)
        assertEquals(LocalDjPhase.Disabled, player.transition.value.phase)
        player.release()
    }
}
