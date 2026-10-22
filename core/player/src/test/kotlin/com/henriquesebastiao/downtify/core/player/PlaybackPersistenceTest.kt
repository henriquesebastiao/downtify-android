package com.henriquesebastiao.downtify.core.player

import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.henriquesebastiao.downtify.core.data.playback.PlaybackRepository
import com.henriquesebastiao.downtify.core.data.playback.playbackOwner
import com.henriquesebastiao.downtify.core.model.PlaybackSnapshot
import com.henriquesebastiao.downtify.core.model.SavedPlaybackItem
import com.henriquesebastiao.downtify.core.network.session.Session
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackPersistenceTest {
    @Test
    fun `failed restore must not erase the durable queue during service shutdown`() = runTest {
        val session = Session("https://example.test", "server", "Test", "device", "fixture")
        val sessions = mockk<SessionStore>(relaxed = true)
        every { sessions.current } returns session
        val repository = mockk<PlaybackRepository>(relaxed = true)
        coEvery { repository.load(session.playbackOwner) } throws
            android.database.sqlite.SQLiteException("Synthetic read failure")
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).setLooper(Looper.getMainLooper()).build()
        val persistence = PlaybackPersistence(player, repository, sessions, backgroundScope)
        try {
            persistence.start()
            persistence.awaitReady()
            persistence.stop()
            verify(exactly = 0) { repository.save(any(), any()) }
        } finally {
            persistence.stop()
            player.release()
        }
    }

    @Test
    fun `restart restores exact queue and shuffle order paused without opening audio`() = runTest {
        val session = Session("https://new.example", "server", "Test", "device", "fixture")
        val sessions = mockk<SessionStore>(relaxed = true)
        every { sessions.current } returns session
        val snapshot = PlaybackSnapshot(
            listOf(
                SavedPlaybackItem("a", "One", "Artist", hasCover = true),
                SavedPlaybackItem("a", "One", "Artist", queued = true, hasCover = true),
                SavedPlaybackItem("b", "Two", "Artist"),
            ),
            1,
            12_345,
            true,
            listOf(2, 0, 1),
            Player.REPEAT_MODE_ALL,
            1.25f,
        )
        val repository = mockk<PlaybackRepository>(relaxed = true)
        val saved = mutableListOf<PlaybackSnapshot>()
        every { repository.save(any(), any()) } answers {
            secondArg<PlaybackSnapshot?>()?.let(saved::add)
            Unit
        }
        coEvery { repository.load(session.playbackOwner) } returns snapshot
        val source = mockk<DataSource.Factory>()
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication())
            .setLooper(Looper.getMainLooper()).setMediaSourceFactory(DefaultMediaSourceFactory(source)).build()
        val persistence = PlaybackPersistence(player, repository, sessions, backgroundScope)
        try {
            persistence.start()
            persistence.awaitReady()
            assertEquals(
                listOf("a", "a", "b"),
                (0 until player.mediaItemCount).map {
                    player.getMediaItemAt(it).mediaId
                },
            )
            assertEquals(1, player.currentMediaItemIndex)
            assertEquals(12_345L, player.currentPosition)
            assertEquals(listOf(2, 0, 1), PlayOrder.shuffled(player.currentTimeline))
            assertEquals(Player.STATE_IDLE, player.playbackState)
            assertFalse(player.playWhenReady)
            assertEquals(
                "https://new.example",
                player.currentMediaItem!!.mediaMetadata.artworkUri!!.let {
                    "${it.scheme}://${it.host}"
                },
            )
            assertEquals("One", MediaItems.trackFromMetadata(player.currentMediaItem!!)!!.title)
            shadowOf(Looper.getMainLooper()).idle()
            player.repeatMode = Player.REPEAT_MODE_ONE
            shadowOf(Looper.getMainLooper()).idle()
            val beforeArtwork = saved.last()
            val currentItem = player.currentMediaItem!!
            player.replaceMediaItem(
                player.currentMediaItemIndex,
                currentItem.buildUpon().setMediaMetadata(
                    currentItem.mediaMetadata.buildUpon()
                        .setArtworkUri(android.net.Uri.parse("content://test.artwork/cover")).build(),
                ).build(),
            )
            shadowOf(Looper.getMainLooper()).idle()
            assertSame(beforeArtwork.items, saved.last().items)
            assertSame(beforeArtwork.shuffleOrder, saved.last().shuffleOrder)
            persistence.stop()
            verify {
                repository.save(session.playbackOwner, match { it?.items?.size == 3 && it.positionMs == 12_345L })
            }
            verify(exactly = 0) { source.createDataSource() }
        } finally {
            persistence.stop()
            player.release()
        }
    }
}
