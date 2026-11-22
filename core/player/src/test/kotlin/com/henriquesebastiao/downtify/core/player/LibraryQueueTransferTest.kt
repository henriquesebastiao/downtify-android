package com.henriquesebastiao.downtify.core.player

import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.Track
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryQueueTransferTest {
    private val context = PlaybackContext(PlaybackContextType.Songs, "", "")
    private fun track(key: Int): Track = mockk<Track>(relaxed = true) {
        every { id } returns "track-$key"
        every { title } returns "Track $key"
        every { file } returns "$key.mp3"
        every { artists } returns emptyList()
        every { playlists } returns emptyList()
    }

    @Test fun `1501 shuffled occurrences load once and the whole permutation remains ahead`() = runTest {
        val player = LocalDjPlayer(
            ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
            ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
            this,
        )
        val transfer = LibraryQueueTransfer()
        val tracks = (0 until 1501).map(::track)
        val counts = mutableListOf<Int>()
        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                counts.add(player.mediaItemCount)
            }
        })
        try {
            val token = transfer.submit("owner", tracks, 0, context, true)
            assertTrue(transfer.play(token, player, null) { "owner" })
            assertEquals(1501, player.mediaItemCount)
            assertEquals(tracks.map { it.id }.toSet(), (0 until 1501).map { player.getMediaItemAt(it).mediaId }.toSet())
            assertEquals(1501, PlayOrder.fromCurrent(player).size)
            assertTrue(counts.all { it <= 1501 })
            assertTrue(counts.contains(500))
        } finally {
            player.release()
        }
    }

    @Test fun `tap keeps selected occurrence natural order and duplicates`() = runTest {
        val player =
            LocalDjPlayer(
                ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
                ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
                this,
            )
        val transfer = LibraryQueueTransfer()
        val tracks = (0 until 1301).map { track(it % 1299) }
        var firstNext: String? = null
        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (player.mediaItemCount == 500 && player.currentMediaItem?.mediaId == "track-1200") {
                    firstNext = player.nextMediaItemIndex.takeIf { it >= 0 }?.let { player.getMediaItemAt(it).mediaId }
                }
            }
        })
        try {
            val token = transfer.submit("owner", tracks, 1200, context, false)
            assertTrue(transfer.play(token, player, null) { "owner" })
            assertEquals(
                tracks.map {
                    it.id
                },
                (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
            )
            assertEquals(1200, player.currentMediaItemIndex)
            assertEquals("track-1201", firstNext)
        } finally {
            player.release()
        }
    }

    @Test fun `repeated context loads the whole queue after an end of catalog tap`() = runTest {
        val deck = LocalDjPlayer(
            ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
            ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
            this,
        )
        val player = DjSessionPlayer(deck)
        val transfer = LibraryQueueTransfer()
        val tracks = (0 until 1501).map(::track)
        val selected = tracks.lastIndex - 1
        try {
            val first = transfer.submit("owner", tracks, 0, context, true)
            assertTrue(transfer.play(first, player, null) { "owner" })
            assertEquals(tracks.size, player.mediaItemCount)

            val second = transfer.submit("owner", tracks, selected, context, false)
            val accepted = transfer.play(second, player, null) { "owner" }
            assertEquals(
                "second selection must finish after its first batch, current index=${player.currentMediaItemIndex}",
                tracks.size,
                player.mediaItemCount,
            )
            assertTrue(accepted)
            assertEquals(selected, player.currentMediaItemIndex)
            assertEquals(tracks[selected].id, player.currentMediaItem?.mediaId)
            assertEquals(
                tracks.map { it.id },
                (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
            )
        } finally {
            player.release()
        }
    }

    @Test fun `external replacement with the same items and context stops the old batches`() = runTest {
        val scope = this
        val deck = LocalDjPlayer(
            ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
            ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
            this,
        )
        val player = DjSessionPlayer(deck)
        val transfer = LibraryQueueTransfer()
        val tracks = (0 until 1501).map(::track)
        var scheduled = false
        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (player.mediaItemCount == 500 && !scheduled) {
                    scheduled = true
                    scope.launch {
                        val sameItems = (0 until player.mediaItemCount).map(player::getMediaItemAt)
                        player.setMediaItems(sameItems, 42, 0)
                        player.playlistMetadata = MediaItems.contextMetadata(context)
                        player.pause()
                    }
                }
            }
        })
        try {
            val token = transfer.submit("owner", tracks, 0, context, false)
            assertFalse(transfer.play(token, player, null) { "owner" })
            assertTrue(scheduled)
            assertEquals(500, player.mediaItemCount)
            assertEquals(42, player.currentMediaItemIndex)
            assertFalse(player.playWhenReady)
        } finally {
            player.release()
        }
    }

    @Test fun `clearing the first batch prevents later batches from refilling it`() = runTest {
        val scope = this
        val player = LocalDjPlayer(
            ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
            ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
            this,
        )
        val transfer = LibraryQueueTransfer()
        var scheduled = false
        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (player.mediaItemCount == 500 && !scheduled) {
                    scheduled = true
                    scope.launch { player.clearMediaItems() }
                }
            }
        })
        try {
            val token = transfer.submit("owner", (0 until 1501).map(::track), 0, context, false)
            assertFalse(transfer.play(token, player, null) { "owner" })
            assertTrue(scheduled)
            assertEquals(0, player.mediaItemCount)
        } finally {
            player.release()
        }
    }

    @Test fun `metadata artwork and pause edits preserve pending batches`() = runTest {
        val scope = this
        val player = LocalDjPlayer(
            ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
            ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
            this,
        )
        val transfer = LibraryQueueTransfer()
        var scheduled = false
        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (player.mediaItemCount == 500 && !scheduled) {
                    scheduled = true
                    scope.launch {
                        val item = player.getMediaItemAt(0)
                        val metadata = item.mediaMetadata.buildUpon().setArtworkUri(Uri.parse("file:///cover.png"))
                        player.replaceMediaItem(0, item.buildUpon().setMediaMetadata(metadata.build()).build())
                        player.playlistMetadata = MediaItems.contextMetadata(context).buildUpon()
                            .setTitle("Updated context title").build()
                        player.pause()
                    }
                }
            }
        })
        try {
            val token = transfer.submit("owner", (0 until 1501).map(::track), 0, context, false)
            assertTrue(transfer.play(token, player, null) { "owner" })
            assertTrue(scheduled)
            assertEquals(1501, player.mediaItemCount)
            assertEquals(Uri.parse("file:///cover.png"), player.getMediaItemAt(0).mediaMetadata.artworkUri)
            assertEquals("Updated context title", player.playlistMetadata.title?.toString())
            assertFalse(player.playWhenReady)
        } finally {
            player.release()
        }
    }

    @Test fun `pause during loading stays paused`() = runTest {
        val player =
            LocalDjPlayer(
                ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
                ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
                this,
            )
        val transfer = LibraryQueueTransfer()
        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (player.mediaItemCount >= 1000) player.pause()
            }
        })
        try {
            val token = transfer.submit("owner", (0 until 1301).map(::track), 0, context, false)
            assertTrue(transfer.play(token, player, null) { "owner" })
            assertEquals(1301, player.mediaItemCount)
            assertFalse(player.playWhenReady)
        } finally {
            player.release()
        }
    }

    @Test fun `account change never starts stale selection`() = runTest {
        val player =
            LocalDjPlayer(
                ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
                ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
                this,
            )
        val transfer = LibraryQueueTransfer()
        var owner = "owner"
        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                owner = "other"
            }
        })
        try {
            val token = transfer.submit("owner", (0 until 1001).map(::track), 0, context, false)
            assertFalse(transfer.play(token, player, null) { owner })
            assertEquals(500, player.mediaItemCount)
            assertFalse(player.playWhenReady)
        } finally {
            player.release()
        }
    }

    @Test fun `whole 28001 track catalog is queued without truncation or added duplicates`() = runTest {
        val base = Track(
            id = "base", file = "base.mp3", title = "Track", artist = "Artist", artists = listOf("Artist"),
            album = "Album", albumArtist = "Artist", albumId = "album", artistId = "artist", trackNumber = 1,
            year = "2026", duration = 120.0, codec = "mp3", bitrate = 128000, sampleRate = 44100,
            channels = 2, size = 1000, added = 1, hasCover = false, playlists = emptyList(),
        )
        val tracks = List(28001) { base.copy(id = "track-$it", file = "$it.mp3") }
        val player = LocalDjPlayer(
            ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
            ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
            this,
        )
        val transfer = LibraryQueueTransfer()
        try {
            val token = transfer.submit("owner", tracks, 0, context, true)
            assertTrue(transfer.play(token, player, null) { "owner" })
            assertEquals(tracks.size, player.mediaItemCount)
            val order = PlayOrder.fromCurrent(player)
            assertEquals(tracks.size, order.size)
            assertEquals(tracks.map { it.id }.toSet(), order.map { player.getMediaItemAt(it).mediaId }.toSet())
        } finally {
            player.release()
        }
    }

    @Test fun `new selection or account never installs stale queue`() = runTest {
        val player =
            LocalDjPlayer(
                ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
                ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
                this,
            )
        val transfer = LibraryQueueTransfer()
        try {
            val old = transfer.submit("old", listOf(track(1)), 0, context, true)
            val recent = transfer.submit("new", listOf(track(2)), 0, context, false)
            assertFalse(transfer.play(old, player, null) { "old" })
            assertFalse(transfer.play(recent, player, null) { "other" })
            assertEquals(0, player.mediaItemCount)
        } finally {
            player.release()
        }
    }
}
