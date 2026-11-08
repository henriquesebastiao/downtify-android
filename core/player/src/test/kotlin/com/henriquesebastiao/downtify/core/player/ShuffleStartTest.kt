package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Shuffle on a new queue must play every occurrence. Media3 builds the permutation itself; a
 * start index picked outside it drops everything before that index — measured at 17.9 of 40
 * songs lost per launch before [PlayOrder.startShuffled] was used.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShuffleStartTest {
    private fun item(id: String) = MediaItem.Builder()
        .setMediaId(id)
        .setUri("https://fixture.test/$id.mp3")
        .build()

    private fun player() = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()

    @Test fun `a shuffled queue starts on the first item of its permutation and plays to the end`() {
        val count = 40
        repeat(30) { round ->
            val player = player()
            try {
                player.setMediaItems((0 until count).map { item("t$it") }, 0, C.TIME_UNSET)
                player.shuffleModeEnabled = true
                PlayOrder.startShuffled(player)
                val first = player.currentTimeline.getFirstWindowIndex(true)
                assertEquals("round $round", first, player.currentMediaItemIndex)
                val ahead = PlayOrder.ahead(player, count * 2)
                assertEquals("round $round: file entière", count, ahead.size)
                assertEquals("round $round: sans doublon", count, ahead.toSet().size)
            } finally {
                player.release()
            }
        }
    }

    @Test fun `a shuffled queue keeps duplicate occurrences as separate items`() {
        val player = player()
        try {
            player.setMediaItems(listOf(item("a"), item("b"), item("a"), item("a")), 0, C.TIME_UNSET)
            player.shuffleModeEnabled = true
            PlayOrder.startShuffled(player)
            assertEquals(4, PlayOrder.ahead(player, 8).size)
        } finally {
            player.release()
        }
    }

    @Test fun `an ordered queue is left where the caller asked`() {
        val player = player()
        try {
            player.setMediaItems((0 until 10).map { item("t$it") }, 3, C.TIME_UNSET)
            PlayOrder.startShuffled(player)
            assertEquals(3, player.currentMediaItemIndex)
            assertEquals(7, PlayOrder.ahead(player, 20).size)
        } finally {
            player.release()
        }
    }

    @Test fun `shuffle never plays the same occurrence twice before the queue ends`() {
        val player = player()
        try {
            player.setMediaItems((0 until 5).map { item("t$it") }, 0, C.TIME_UNSET)
            player.shuffleModeEnabled = true
            PlayOrder.startShuffled(player)
            val ahead = PlayOrder.ahead(player, 10)
            assertEquals(5, ahead.size)
            assertNotEquals(ahead[0], ahead[1])
            assertTrue(ahead.all { it in 0 until 5 })
        } finally {
            player.release()
        }
    }
}
