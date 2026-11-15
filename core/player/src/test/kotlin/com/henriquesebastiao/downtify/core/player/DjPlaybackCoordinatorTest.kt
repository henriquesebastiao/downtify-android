package com.henriquesebastiao.downtify.core.player

import com.henriquesebastiao.downtify.core.data.ServerResult
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.library.LibrarySnapshot
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiFailure
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.model.Track
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        val library = mockk<LibraryRepository>(relaxed = true)
        val caps = MutableStateFlow(
            MusicAiCapabilitiesDto(
                version = 1,
                features = mapOf("dj_interactive" to true),
                dj = MusicAiStatusDto(interactiveReady = true, queueSupported = true),
            ),
        )
        init {
            every { music.session } returns owner
            every { music.capabilities } returns caps
            coEvery { music.refresh(force = true) } returns ServerResult.Ok(Unit)
            every { player.selectionVersion } returns selection
            every { player.state } returns state
            every { player.playDj(any(), any(), any(), any(), any(), any()) } answers {
                selection.value++
                state.value = PlayerState(dj = PlayingDj(firstArg(), "Fixture", arg(3)))
            }
            every { player.exitDj(any()) } answers { state.value = PlayerState() }
        }
        val coordinator = DjPlaybackCoordinator(music, player, library, scope)
    }

    @Test fun `seed refreshes stale capabilities and accepts the permitted beta trial`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.caps.value = MusicAiCapabilitiesDto()
        coEvery { fixture.music.refresh(force = true) } coAnswers {
            fixture.caps.value = MusicAiCapabilitiesDto(
                version = 1,
                features = mapOf("dj_interactive" to true, "dj_trial" to true),
                djSeedMix = true,
                dj = MusicAiStatusDto(interactiveTrialReady = true),
            )
            ServerResult.Ok(Unit)
        }
        val session = MusicDjSessionDto("a".repeat(32), state = "playing", theme = "fete", mode = "interactive")
        coEvery { fixture.music.djSeed("seed", any(), skipSeed = false) } returns session
        coEvery { fixture.music.djSession(session.id) } returns session
        runCurrent()
        fixture.coordinator.startSeed("seed")
        runCurrent()
        advanceTimeBy(2001)
        runCurrent()
        coVerify(exactly = 1) { fixture.music.refresh(force = true) }
        verify(exactly = 1) { fixture.player.playDj(session.id, any(), any(), "interactive", any(), session) }
        assertEquals(DjPhase.Active, fixture.coordinator.phase.value)
    }

    @Test fun `trial readiness never bypasses a disabled interactive feature`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.caps.value = MusicAiCapabilitiesDto(
            version = 1,
            features = mapOf("dj_interactive" to false, "dj_trial" to true),
            dj = MusicAiStatusDto(interactiveTrialReady = true),
        )
        runCurrent()
        fixture.coordinator.startTheme("fete", "library")
        runCurrent()
        coVerify(exactly = 0) { fixture.music.djContinuous(any(), any(), any()) }
        assertFalse(fixture.coordinator.busy.value)
        assertTrue(fixture.coordinator.message.value.contains("Paramètres"))
    }

    @Test fun `authentication route failure shows a reconnect action`() = runTest {
        val fixture = Fixture(backgroundScope)
        coEvery { fixture.music.djContinuous(any(), any(), any()) } throws MusicAiFailure(401)
        runCurrent()
        fixture.coordinator.startTheme("fete", "library")
        runCurrent()
        assertTrue(fixture.coordinator.message.value.contains("Reconnectez"))
        assertFalse(fixture.coordinator.busy.value)
        assertEquals(DjPhase.Classic, fixture.coordinator.phase.value)
    }

    @Test fun `unavailable engine route failure has a distinct visible reason`() = runTest {
        val fixture = Fixture(backgroundScope)
        coEvery { fixture.music.djContinuous(any(), any(), any()) } throws MusicAiFailure(503)
        runCurrent()
        fixture.coordinator.startTheme("fete", "library")
        runCurrent()
        assertTrue(fixture.coordinator.message.value.contains("moteur DJ"))
        assertTrue(fixture.coordinator.message.value.contains("prêt"))
        assertFalse(fixture.coordinator.busy.value)
    }

    @Test fun `seed return timeout is visible and never attaches another transport`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.caps.value = fixture.caps.value.copy(djSeedMix = true)
        fixture.state.value = PlayerState(dj = PlayingDj("a".repeat(32), "Fixture", "interactive"))
        every { fixture.player.exitDj(any()) } answers { }
        runCurrent()
        fixture.coordinator.startSeed("seed")
        runCurrent()
        advanceTimeBy(2001)
        runCurrent()
        assertTrue(fixture.coordinator.message.value.contains("quitté"))
        assertFalse(fixture.coordinator.busy.value)
        coVerify(exactly = 0) { fixture.music.djSeed(any(), any(), any()) }
        verify(exactly = 0) { fixture.player.playDj(any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `missing account gives an explicit connection message`() = runTest {
        val fixture = Fixture(backgroundScope)
        runCurrent()
        fixture.owner.value = null
        runCurrent()
        fixture.coordinator.startTheme("fete", "library")
        runCurrent()
        assertTrue(fixture.coordinator.message.value.contains("Connectez"))
        assertFalse(fixture.coordinator.busy.value)
    }

    @Test fun `owner change during capability refresh fences late preparation`() = runTest {
        val fixture = Fixture(backgroundScope)
        val response = CompletableDeferred<ServerResult<Unit>>()
        coEvery { fixture.music.refresh(force = true) } coAnswers { response.await() }
        runCurrent()
        fixture.coordinator.startTheme("fete", "library")
        runCurrent()
        fixture.owner.value = Session("https://other.test", "other", "Other", "new-device", "other-token")
        runCurrent()
        response.complete(ServerResult.Ok(Unit))
        runCurrent()
        coVerify(exactly = 0) { fixture.music.djContinuous(any(), any(), any()) }
        verify(exactly = 0) { fixture.player.playDj(any(), any(), any(), any(), any(), any()) }
        assertEquals("", fixture.coordinator.message.value)
        assertFalse(fixture.coordinator.busy.value)
    }

    @Test fun `large queue prepares only forty following tracks and waits for the current title`() = runTest {
        val fixture = Fixture(backgroundScope)
        val current = mockk<Track>(relaxed = true)
        every { current.id } returns "current"
        val ids = (1..50).map { "next-$it" }
        val library = mockk<LibrarySnapshot>()
        every { library.byId } returns ids.associateWith { mockk<Track>() }
        every { fixture.library.library } returns MutableStateFlow<LibrarySnapshot?>(library)
        fixture.state.value = PlayerState(
            track = current,
            currentIndex = 0,
            upNext = ids.mapIndexed { index, id -> QueueEntry(index + 1, id, id, "") },
        )
        val session = MusicDjSessionDto("a".repeat(32), state = "playing", theme = "fete", mode = "interactive")
        coEvery { fixture.music.djQueue(ids.take(40), any()) } returns session
        coEvery { fixture.music.djSession(session.id) } returns session
        runCurrent()
        fixture.coordinator.toggleQueue()
        runCurrent()
        advanceTimeBy(2001)
        runCurrent()
        coVerify(exactly = 1) { fixture.music.djQueue(ids.take(40), any()) }
        coVerify(exactly = 1) { fixture.music.djCommand(session.id, "pause", any()) }
        verify(exactly = 1) { fixture.player.queueDj(session.id, any(), any(), session, true) }
        verify(exactly = 0) { fixture.player.playDj(any(), any(), any(), any(), any(), any()) }
        assertTrue(fixture.coordinator.armed.value)
        assertFalse(fixture.coordinator.busy.value)
        assertTrue(fixture.coordinator.message.value.contains("fin du titre"))
    }

    @Test fun `one following title explains the minimum instead of replaying the current title`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.state.value = PlayerState(upNext = listOf(QueueEntry(1, "next", "Next", "")))
        runCurrent()
        fixture.coordinator.toggleQueue()
        runCurrent()
        coVerify(exactly = 0) { fixture.music.djQueue(any(), any()) }
        assertTrue(fixture.coordinator.message.value.contains("deux titres"))
        assertFalse(fixture.coordinator.busy.value)
    }

    @Test fun `an already ended server render is reported instead of polling silently`() = runTest {
        val fixture = Fixture(backgroundScope)
        val session = MusicDjSessionDto("a".repeat(32), state = "ready", theme = "fete", mode = "interactive")
        coEvery { fixture.music.djContinuous(any(), any(), any()) } returns session
        coEvery { fixture.music.djSession(session.id) } returns session
        runCurrent()
        fixture.coordinator.startTheme("fete", "library")
        runCurrent()
        advanceTimeBy(2001)
        runCurrent()
        assertTrue(fixture.coordinator.message.value.contains("terminé"))
        assertFalse(fixture.coordinator.busy.value)
        coVerify(exactly = 1) { fixture.music.djStop(session.id) }
        verify(exactly = 0) { fixture.player.playDj(any(), any(), any(), any(), any(), any()) }
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
