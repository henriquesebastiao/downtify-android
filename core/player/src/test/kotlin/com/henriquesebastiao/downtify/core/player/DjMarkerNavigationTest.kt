package com.henriquesebastiao.downtify.core.player

import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiTrackDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjMarkerDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto
import com.henriquesebastiao.downtify.core.network.session.Session
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@UnstableApi
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DjMarkerNavigationTest {
    private val id = "a".repeat(32)
    private val session = MusicDjSessionDto(
        id = id,
        mode = "prepared",
        state = "ready",
        theme = "fete",
        tracks = listOf(MusicAiTrackDto("one", "First"), MusicAiTrackDto("two", "Second")),
        timeline = listOf(
            MusicDjMarkerDto("one", 0.0, 153.0517),
            MusicDjMarkerDto("two", 148.25, 310.2004),
        ),
    )

    private fun TestScope.playerAndTracker(): Pair<LocalDjPlayer, DjControlTracker> {
        val player = LocalDjPlayer(
            ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
            ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build(),
            backgroundScope,
        )
        val music = mockk<MusicAiRepository>()
        every { music.session } returns MutableStateFlow<Session?>(
            Session("https://fixture.test", "server", "Test", "device", "fixture"),
        )
        every { music.capabilities } returns MutableStateFlow(
            MusicAiCapabilitiesDto(version = 1, features = mapOf("dj_prepared" to true)),
        )
        coEvery { music.djSession(id) } returns session
        val item = DjTimeline.metadata(DjMediaItems.from(id, "Mix", 310_201), session, 0, "https://fixture.test")
        player.setMediaItem(item)
        val tracker = DjControlTracker(player, music, backgroundScope)
        tracker.start()
        return player to tracker
    }

    private fun TestScope.settleMarker() {
        // ExoPlayer callbacks use Android's looper, independently of the coroutine test clock.
        shadowOf(Looper.getMainLooper()).idle()
        advanceTimeBy(250)
        runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun `next across a fractional overlap end changes the displayed marker while paused`() = runTest {
        val (player, tracker) = playerAndTracker()
        try {
            runCurrent()
            shadowOf(Looper.getMainLooper()).idle()
            player.nextInsideDj()
            settleMarker()
            assertTrue(player.currentPosition / 1000.0 >= 153.0517)
            assertEquals("two", DjMediaItems.of(requireNotNull(player.currentMediaItem))?.trackId)
            assertFalse(player.playWhenReady)
            advanceTimeBy(20_000)
            runCurrent()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("two", DjMediaItems.of(requireNotNull(player.currentMediaItem))?.trackId)
            assertFalse(player.playWhenReady)
        } finally {
            tracker.stop()
            player.release()
        }
    }

    @Test fun `previous restarts the second marker after its fractional overlap end`() = runTest {
        val (player, tracker) = playerAndTracker()
        try {
            runCurrent()
            shadowOf(Looper.getMainLooper()).idle()
            player.seekTo(0, 212_268)
            settleMarker()
            assertEquals("two", DjMediaItems.of(requireNotNull(player.currentMediaItem))?.trackId)
            player.previousInsideDj()
            settleMarker()
            assertTrue(player.currentPosition / 1000.0 >= 153.0517)
            assertEquals("two", DjMediaItems.of(requireNotNull(player.currentMediaItem))?.trackId)
            assertFalse(player.playWhenReady)
            // A second Previous near this restart reaches the prior occurrence.
            player.previousInsideDj()
            settleMarker()
            assertEquals("one", DjMediaItems.of(requireNotNull(player.currentMediaItem))?.trackId)
            assertFalse(player.playWhenReady)
        } finally {
            tracker.stop()
            player.release()
        }
    }
}
