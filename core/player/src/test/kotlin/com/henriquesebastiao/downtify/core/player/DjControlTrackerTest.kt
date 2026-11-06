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

    @Test fun `shared server pause resumes only its own loaded direct and stops polling on release`() = runTest {
        setup()
        var playing = true
        every { player.playWhenReady } answers { playing }
        every { player.pause() } answers { playing = false }
        every { player.play() } answers { playing = true }
        status = status.copy(state = "paused")
        val tracker = DjControlTracker(player, music, backgroundScope)
        tracker.start()
        runCurrent()
        verify(exactly = 1) { player.pause() }
        status = status.copy(state = "playing")
        advanceTimeBy(2001)
        runCurrent()
        verify(exactly = 1) { player.play() }
        tracker.stop()
        advanceTimeBy(4000)
        runCurrent()
        coVerify(exactly = 2) { music.djSession(id) }
    }
}
