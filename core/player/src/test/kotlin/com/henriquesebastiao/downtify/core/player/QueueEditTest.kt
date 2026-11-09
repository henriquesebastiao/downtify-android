package com.henriquesebastiao.downtify.core.player

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * "Add to queue" and queue moves used to cast the session player to ExoPlayer, so both commands
 * failed on the LocalDjPlayer facade. These tests drive the real operations on a deck and through
 * the facade, including the shuffled order restored from a previous session.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueueEditTest {
    private fun deck(): ExoPlayer = ExoPlayer.Builder(RuntimeEnvironment.getApplication())
        .setLooper(Looper.getMainLooper())
        .setMediaSourceFactory(DefaultMediaSourceFactory(mockk<DataSource.Factory>()))
        .build()

    private fun list(player: Player) = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }

    /** The item that plays right after [index], in the order the player will follow. */
    private fun successor(player: Player, index: Int): Int =
        player.currentTimeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)

    /** What plays next, as the player will play it, starting with the current item. */
    private fun playing(player: Player) = PlayOrder.fromCurrent(player).map { player.getMediaItemAt(it).mediaId }

    @Test fun `add to queue plays right after the current song`() {
        val player = deck()
        try {
            player.setMediaItems(listOf("a", "b", "c").map(::localDjItem), 0, C.TIME_UNSET)
            QueueEdit.add(player, localDjItem("q"))
            assertEquals(listOf("a", "q", "b", "c"), list(player))
        } finally {
            player.release()
        }
    }

    @Test fun `add to queue in shuffle plays right after the current song`() {
        val player = deck()
        try {
            player.setMediaItems((0 until 6).map { localDjItem("t$it") }, 0, C.TIME_UNSET)
            player.shuffleModeEnabled = true
            QueueEdit.add(player, localDjItem("q"))
            assertEquals(
                "le titre mis en file suit immédiatement le titre courant",
                listOf("t0", "q"),
                playing(player).take(2),
            )
        } finally {
            player.release()
        }
    }

    @Test fun `add to queue works through the session facade`() = runTest {
        val facade = LocalDjPlayer(deck(), deck(), backgroundScope)
        try {
            facade.setMediaItems((0 until 5).map { localDjItem("t$it") }, 0, C.TIME_UNSET)
            facade.shuffleModeEnabled = true
            QueueEdit.add(facade, localDjItem("q"))
            assertEquals(6, facade.mediaItemCount)
            assertEquals(listOf("t0", "q"), playing(facade).take(2))
        } finally {
            facade.release()
        }
    }

    @Test fun `an empty queue takes the added song and prepares it`() {
        val player = deck()
        try {
            QueueEdit.add(player, localDjItem("q"))
            assertEquals(listOf("q"), list(player))
        } finally {
            player.release()
        }
    }

    @Test fun `moving a song without shuffle moves it in the list`() {
        val player = deck()
        try {
            player.setMediaItems(listOf("a", "b", "c", "d").map(::localDjItem), 0, C.TIME_UNSET)
            QueueEdit.move(player, from = 3, after = 1, queued = false)
            assertEquals(listOf("a", "b", "d", "c"), list(player))
        } finally {
            player.release()
        }
    }

    @Test fun `moving a song in shuffle puts it right after the chosen one and marks it queued`() {
        val player = deck()
        try {
            player.setMediaItems((0 until 5).map { localDjItem("t$it") }, 0, C.TIME_UNSET)
            player.shuffleModeEnabled = true
            QueueEdit.move(player, from = 4, after = 1, queued = true)
            assertTrue(
                "le titre déplacé est marqué comme mis en file",
                MediaItems.isQueued(player.getMediaItemAt(4)),
            )
            assertEquals("t4 suit t1 dans l'ordre de lecture", 4, successor(player, 1))
        } finally {
            player.release()
        }
    }

    @Test fun `moving the current song is refused`() {
        val player = deck()
        try {
            player.setMediaItems(listOf("a", "b", "c").map(::localDjItem), 0, C.TIME_UNSET)
            QueueEdit.move(player, from = 0, after = 2, queued = false)
            assertEquals(listOf("a", "b", "c"), list(player))
        } finally {
            player.release()
        }
    }

    @Test fun `moving in the shuffled order works through the session facade`() = runTest {
        val facade = LocalDjPlayer(deck(), deck(), backgroundScope)
        try {
            facade.setMediaItems((0 until 5).map { localDjItem("t$it") }, 0, C.TIME_UNSET)
            facade.shuffleModeEnabled = true
            QueueEdit.move(facade, from = 3, after = 1, queued = true)
            assertEquals(5, facade.mediaItemCount)
            assertEquals("t3 suit t1 dans l'ordre de lecture", 3, successor(facade, 1))
        } finally {
            facade.release()
        }
    }

    @Test fun `a saved permutation restored on the session player is the one that plays`() = runTest {
        val facade = LocalDjPlayer(deck(), deck(), backgroundScope)
        try {
            facade.setMediaItems((0 until 6).map { localDjItem("t$it") }, 0, C.TIME_UNSET)
            facade.shuffleModeEnabled = true
            val saved = listOf(3, 0, 5, 2, 4, 1)
            facade.restoreShuffleOrder(saved.toIntArray())
            // The facade must publish the deck's order: playback continues with the saved successor.
            assertEquals("le successeur enregistré est celui qui joue", 5, successor(facade, 0))
        } finally {
            facade.release()
        }
    }
}
