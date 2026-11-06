package com.henriquesebastiao.downtify.core.player

import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiStatusDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto
import com.henriquesebastiao.downtify.core.network.session.Session
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DjPlaybackCoordinatorTest {
    private class Fixture(scope: CoroutineScope) {
        val owner = MutableStateFlow<Session?>(Session("https://fixture.test", "server", "Test", "device", "fixture"))
        val selection = MutableStateFlow(0L)
        val state = MutableStateFlow(PlayerState())
        val music = mockk<MusicAiRepository>(relaxed = true)
        val player = mockk<PlayerController>(relaxed = true)
        init {
            every { music.session } returns owner
            every { music.capabilities } returns MutableStateFlow(
                MusicAiCapabilitiesDto(
                    version = 1,
                    features = mapOf("dj_interactive" to true),
                    dj = MusicAiStatusDto(interactiveReady = true, queueSupported = true),
                ),
            )
            every { player.selectionVersion } returns selection
            every { player.state } returns state
            every { player.playDj(any(), any(), any(), any(), any(), any()) } answers {
                selection.value++
                state.value = PlayerState(dj = PlayingDj(firstArg(), "Fixture", arg(3)))
            }
            every { player.exitDj(any()) } answers { state.value = PlayerState() }
        }
        val coordinator = DjPlaybackCoordinator(music, player, mockk<LibraryRepository>(relaxed = true), scope)
    }

    @Test fun `cancelling preparation cancels its nonce before any transport can attach`() = runTest {
        val fixture = Fixture(backgroundScope)
        val response = CompletableDeferred<MusicDjSessionDto>()
        coEvery { fixture.music.djContinuous(any(), any(), any()) } coAnswers { response.await() }
        runCurrent()
        fixture.coordinator.startTheme("detente", "library")
        runCurrent()
        fixture.coordinator.stop()
        runCurrent()
        response.complete(MusicDjSessionDto("a".repeat(32), state = "playing", theme = "detente", mode = "interactive"))
        runCurrent()
        coVerify(exactly = 1) { fixture.music.djCancelRequest(any()) }
        verify(exactly = 0) { fixture.player.playDj(any(), any(), any(), any(), any(), any()) }
        assertEquals(DjPhase.Classic, fixture.coordinator.phase.value)
        assertFalse(fixture.coordinator.busy.value)
    }

    @Test fun `25 mode cycles retire the previous session and keep one playback authority`() = runTest {
        val fixture = Fixture(backgroundScope)
        val session = MusicDjSessionDto("a".repeat(32), state = "playing", theme = "fete", mode = "interactive")
        runCurrent()
        repeat(25) {
            fixture.coordinator.listen(session, "Fixture")
            runCurrent()
            assertEquals(DjPhase.Active, fixture.coordinator.phase.value)
            fixture.coordinator.stop()
            runCurrent()
            assertEquals(DjPhase.Classic, fixture.coordinator.phase.value)
        }
        verify(exactly = 25) { fixture.player.playDj(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 25) { fixture.player.exitDj(session.id) }
        coVerify(exactly = 25) { fixture.music.djStop(session.id) }
    }

    @Test fun `a system controller selecting normal media retires the old DJ transport`() = runTest {
        val fixture = Fixture(backgroundScope)
        val session = MusicDjSessionDto("a".repeat(32), state = "playing", theme = "fete", mode = "interactive")
        runCurrent()
        fixture.coordinator.listen(session, "Fixture")
        runCurrent()
        fixture.state.value = PlayerState(stream = PlayingStream("normal", "", "", "", ""))
        runCurrent()
        assertEquals(DjPhase.Classic, fixture.coordinator.phase.value)
        verify(exactly = 1) { fixture.player.exitDj(session.id) }
        coVerify(exactly = 1) { fixture.music.djStop(session.id) }
    }

    @Test fun `leaving a prepared set preserves its reusable server render`() = runTest {
        val fixture = Fixture(backgroundScope)
        val session = MusicDjSessionDto("a".repeat(32), state = "ready", theme = "fete", mode = "prepared")
        runCurrent()
        fixture.coordinator.listen(session, "Fixture")
        runCurrent()
        fixture.coordinator.stop()
        runCurrent()
        verify(exactly = 1) { fixture.player.exitDj(session.id) }
        coVerify(exactly = 0) { fixture.music.djStop(any()) }
        assertEquals(DjPhase.Classic, fixture.coordinator.phase.value)
    }
}
