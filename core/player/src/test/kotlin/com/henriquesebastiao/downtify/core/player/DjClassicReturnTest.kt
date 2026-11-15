package com.henriquesebastiao.downtify.core.player

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.henriquesebastiao.downtify.core.network.dto.MusicAiTrackDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjMarkerDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DjClassicReturnTest {
    private val id = "a".repeat(32)

    private fun classic(id: String) = localDjItem(id).let { item ->
        item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setDurationMs(400_000).build()).build()
    }

    private fun mix(audible: String = "track-18"): MediaItem {
        val ids = listOf("track-17", audible, "track-19", "track-20")
        val session = MusicDjSessionDto(
            id = id,
            mode = "prepared",
            state = "ready",
            theme = "fete",
            tracks = ids.map { MusicAiTrackDto(it, it, duration = 400.0) },
            timeline = ids.mapIndexed { index, track ->
                MusicDjMarkerDto(track, index * 100.0, index * 100.0 + 110.0, occurrence = index)
            },
            queueOrder = false,
        )
        val original = DjMediaItems.from(id, "Mix", 410_000).let { item ->
            item.buildUpon().setMediaMetadata(
                item.mediaMetadata.buildUpon().setExtras(
                    Bundle().apply {
                        putInt("djClassicIndex", 17)
                        putInt("djQueueAnchor", 17)
                        putLong("djClassicPosition", 12_345)
                        // A theme render need not have the same order as the retained queue.
                        putBoolean("djOwnQueue", false)
                    },
                ).build(),
            ).build()
        }
        return DjTimeline.metadata(original, session, 150_000, "https://fixture.test")
    }

    private fun uids(player: Player) = (0 until player.mediaItemCount).map {
        player.currentTimeline.getWindow(it, Timeline.Window()).uid
    }

    private fun shuffleUids(player: Player) = PlayOrder.shuffled(player.currentTimeline).map {
        player.currentTimeline.getWindow(it, Timeline.Window()).uid
    }

    @Test fun `stop and relaunch preserve all 62 classic occurrences and their shuffled order`() = runTest {
        val deck = LocalDjPlayer(
            ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
            ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
            backgroundScope,
        )
        val player = DjSessionPlayer(deck)
        val original = (0 until 62).map { classic(if (it == 40) "track-18" else "track-$it") }
        try {
            player.setMediaItems(original, 17, 12_345)
            player.shuffleModeEnabled = true
            val occurrences = uids(player)
            val permutation = shuffleUids(player)
            repeat(2) {
                val item = mix()
                player.addMediaItem(18, item)
                player.seekTo(18, 150_000)
                assertEquals(63, player.mediaItemCount)
                DjClassicReturn.handle(player, DjClassicReturn.arguments(item.mediaId), "https://fixture.test").get()
                assertEquals(62, player.mediaItemCount)
                assertEquals(
                    original.map { it.mediaId },
                    (0 until player.mediaItemCount).map {
                        player.getMediaItemAt(it).mediaId
                    },
                )
                assertEquals(occurrences, uids(player))
                assertEquals(permutation, shuffleUids(player))
                assertEquals("track-18", player.currentMediaItem?.mediaId)
                assertEquals(18, player.currentMediaItemIndex)
                assertFalse(player.playWhenReady)
            }
        } finally {
            player.release()
        }
    }

    @Test fun `a render track outside the retained queue returns to the saved classic position`() = runTest {
        val player = LocalDjPlayer(LocalDjFakeDeck(), LocalDjFakeDeck(), backgroundScope)
        try {
            player.setMediaItems((0 until 62).map { classic("track-$it") }, 17, 12_345)
            val item = mix("outside")
            player.addMediaItem(18, item)
            player.seekTo(18, 150_000)
            DjClassicReturn.handle(player, DjClassicReturn.arguments(item.mediaId), "https://fixture.test").get()
            assertEquals(62, player.mediaItemCount)
            assertEquals("track-17", player.currentMediaItem?.mediaId)
            assertEquals(12_345L, player.currentPosition)
            assertFalse(player.playWhenReady)
        } finally {
            player.release()
        }
    }

    @Test fun `a render without a retained classic queue can continue as its four ordinary tracks`() = runTest {
        val player = LocalDjPlayer(LocalDjFakeDeck(), LocalDjFakeDeck(), backgroundScope)
        try {
            val item = mix()
            player.setMediaItem(item)
            player.seekTo(0, 150_000)
            DjClassicReturn.handle(player, DjClassicReturn.arguments(item.mediaId), "https://fixture.test") {
                classic(it)
            }.get()
            assertEquals(4, player.mediaItemCount)
            assertEquals("track-18", player.currentMediaItem?.mediaId)
            assertEquals(50_000L, player.currentPosition)
            assertFalse(player.playWhenReady)
        } finally {
            player.release()
        }
    }
}
