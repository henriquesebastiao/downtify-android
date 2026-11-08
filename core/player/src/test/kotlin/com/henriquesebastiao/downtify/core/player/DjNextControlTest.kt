package com.henriquesebastiao.downtify.core.player

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android Auto, Bluetooth and the notification all press the player's own "next": it must stay
 * inside the mix. Before this, the player advanced to the classic queue item stored after the DJ
 * render, which is exactly the reported jump out of the DJ.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DjNextControlTest {
    private val render = "0123456789abcdef0123456789abcdef"

    private fun djItem(mode: String, nextStart: Long): MediaItem {
        val base = DjMediaItems.from(render, "Mix DJ", 600_000L, mode)
        val extras = Bundle().apply {
            putLong("djStart", 0)
            putLong("djEnd", 180_000)
            putLong("djNextStart", nextStart)
            putString("djTrackId", "track-1")
        }
        return base.buildUpon()
            .setMediaMetadata(base.mediaMetadata.buildUpon().setExtras(extras).build())
            .build()
    }

    private fun player(scope: kotlinx.coroutines.test.TestScope): LocalDjPlayer =
        LocalDjPlayer(LocalDjFakeDeck(), LocalDjFakeDeck(), scope)

    @Test fun `next inside a prepared mix moves to the next marker`() = runTest {
        val player = player(this)
        player.setMediaItems(listOf(djItem("prepared", 180_000), localDjItem("classic")), 0, 0)
        player.prepare()
        player.play()

        player.seekToNext()
        advanceUntilIdle()

        assertEquals("reste sur le mix", 0, player.currentMediaItemIndex)
        assertEquals(180_000L, player.currentPosition)
        player.release()
    }

    @Test fun `next inside an interactive mix asks the server and stays on the mix`() = runTest {
        val player = player(this)
        var asked = 0
        player.setDjNextHandler { asked++ }
        player.setMediaItems(listOf(djItem("interactive", 180_000), localDjItem("classic")), 0, 0)
        player.prepare()
        player.play()

        player.seekToNext()
        advanceUntilIdle()

        assertEquals(1, asked)
        assertEquals(0, player.currentMediaItemIndex)
        player.release()
    }

    @Test fun `next outside a mix still advances the queue`() = runTest {
        val player = player(this)
        player.setMediaItems(listOf(localDjItem("a"), localDjItem("b")), 0, 0)
        player.prepare()
        player.play()

        player.seekToNext()
        advanceUntilIdle()

        assertEquals(1, player.currentMediaItemIndex)
        player.release()
    }
}
