package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.Player
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjCommandResultDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto
import com.henriquesebastiao.downtify.core.network.session.Session
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DjControlTrackerTest {
    private val id = "a".repeat(32)
    private val music = mockk<MusicAiRepository>()
    private val player = mockk<Player>(relaxed = true)
    private val capabilities =
        MutableStateFlow(MusicAiCapabilitiesDto(version = 1, features = mapOf("dj_interactive" to true)))
    private val owner =
        MutableStateFlow<Session?>(Session("https://fixture.test", "server", "Test", "device", "fixture"))
    private var status = MusicDjSessionDto(id = id, state = "playing", theme = "fete", mode = "interactive")

    private fun setup() {
        every { music.capabilities } returns capabilities
        every { music.session } returns owner
        every { player.currentMediaItem } returns DjMediaItems.from(id, "Live", 100000, "interactive")
        coEvery { music.djSession(id) } answers { status }
        coEvery { music.djCommand(any(), any(), any()) } returns MusicDjCommandResultDto(true)
        coEvery { music.djStop(any()) } returns kotlinx.serialization.json.JsonObject(emptyMap())
    }

    @Test fun `only user transport changes send global commands not buffering or audio focus`() = runTest {
        setup()
        val tracker = DjControlTracker(player, music, backgroundScope)
        tracker.start()
        runCurrent()
        tracker.onIsPlayingChanged(false)
        tracker.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
        runCurrent()
        coVerify(exactly = 0) { music.djCommand(any(), any(), any()) }
        tracker.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        runCurrent()
        coVerify(exactly = 1) { music.djCommand(id, "pause", any()) }
        capabilities.value = MusicAiCapabilitiesDto()
        tracker.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        runCurrent()
        coVerify(exactly = 0) { music.djCommand(id, "resume", any()) }
        tracker.stop()
    }

    @Test fun `render status never changes local playback intent and release stops polling`() = runTest {
        setup()
        var playing = true
        every { player.playWhenReady } answers { playing }
        every { player.pause() } answers { playing = false }
        every { player.play() } answers { playing = true }
        status = status.copy(state = "paused")
        val tracker = DjControlTracker(player, music, backgroundScope)
        tracker.start()
        runCurrent()
        verify(exactly = 0) { player.pause() }
        status = status.copy(state = "playing")
        advanceTimeBy(3001)
        runCurrent()
        verify(exactly = 0) { player.play() }
        tracker.stop()
        advanceTimeBy(4000)
        runCurrent()
        coVerify(exactly = 2) { music.djSession(id) }
    }

    @Test fun `25 repeated DJ normal cycles leave no retired polling or server-driven playback`() = runTest {
        setup()
        var item = DjMediaItems.from(id, "Live", 100000, "interactive")
        every { player.currentMediaItem } answers { item }
        status = status.copy(state = "paused")
        val tracker = DjControlTracker(player, music, backgroundScope)
        tracker.start()
        runCurrent()
        repeat(25) {
            item = androidx.media3.common.MediaItem.Builder().setMediaId("normal").build()
            tracker.onMediaItemTransition(item, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
            runCurrent()
            item = DjMediaItems.from(id, "Live", 100000, "interactive")
            tracker.onMediaItemTransition(item, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
            runCurrent()
        }
        tracker.stop()
        val calls = 26
        advanceTimeBy(10000)
        runCurrent()
        coVerify(exactly = calls) { music.djSession(id) }
        coVerify(exactly = 25) { music.djStop(id) }
        verify(exactly = 0) { player.pause() }
        verify(exactly = 0) { player.play() }
    }

    @Test fun `system selection removes retired HLS without controlling the normal title`() = runTest {
        setup()
        val dj = DjMediaItems.from(id, "Live", 100000, "interactive")
        val normal = androidx.media3.common.MediaItem.Builder().setMediaId("normal").build()
        var item = dj
        every { player.currentMediaItem } answers { item }
        every { player.mediaItemCount } returns 2
        every { player.currentMediaItemIndex } returns 1
        every { player.getMediaItemAt(0) } returns dj
        every { player.getMediaItemAt(1) } returns normal
        val tracker = DjControlTracker(player, music, backgroundScope)
        tracker.start()
        runCurrent()
        tracker.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        item = normal
        tracker.onMediaItemTransition(normal, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        runCurrent()
        coVerify(exactly = 0) { music.djCommand(id, "pause", any()) }
        coVerify(exactly = 1) { music.djStop(id) }
        verify(exactly = 1) { player.removeMediaItem(0) }
        verify(exactly = 0) { player.pause() }
        tracker.stop()
    }

    @Test fun `prepared sets remain reusable after a system selection`() = runTest {
        setup()
        var item = DjMediaItems.from(id, "Prepared", 100000, "prepared")
        every { player.currentMediaItem } answers { item }
        val tracker = DjControlTracker(player, music, backgroundScope)
        tracker.start()
        runCurrent()
        item = androidx.media3.common.MediaItem.Builder().setMediaId("normal").build()
        tracker.onMediaItemTransition(item, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        runCurrent()
        coVerify(exactly = 0) { music.djStop(any()) }
        tracker.stop()
    }
}
