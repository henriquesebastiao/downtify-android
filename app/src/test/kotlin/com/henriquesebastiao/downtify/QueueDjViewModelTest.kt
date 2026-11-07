package com.henriquesebastiao.downtify

import androidx.lifecycle.viewModelScope
import com.henriquesebastiao.downtify.core.data.ServerResult
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiStatusDto
import com.henriquesebastiao.downtify.core.network.session.Session
import com.henriquesebastiao.downtify.core.player.DjPhase
import com.henriquesebastiao.downtify.core.player.DjPlaybackCoordinator
import com.henriquesebastiao.downtify.core.player.LocalDjSettings
import com.henriquesebastiao.downtify.core.player.LocalDjTransition
import com.henriquesebastiao.downtify.core.player.PlayerController
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.core.player.PlayingDj
import com.henriquesebastiao.downtify.core.player.QueueEntry
import com.henriquesebastiao.downtify.core.player.RepeatMode
import com.henriquesebastiao.downtify.feature.player.QueueDjViewModel
import com.henriquesebastiao.downtify.feature.player.queueDjAvailability
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueDjViewModelTest {
    @Test fun `queue opening refreshes trial availability without starting DJ`() = check { f ->
        f.device.value = false
        f.caps.value = MusicAiCapabilitiesDto()
        coEvery { f.music.refresh(true) } coAnswers {
            f.caps.value = f.readyCaps
            ServerResult.Ok(Unit)
        }
        f.vm.onQueueShown()
        assertTrue(f.vm.checking.value)
        runCurrent()
        coVerify(exactly = 1) { f.music.refresh(true) }
        assertTrue(f.vm.availability.value.ready)
        assertFalse(f.vm.checking.value)
        verify(exactly = 0) { f.coordinator.toggleQueue() }
        verify(exactly = 0) { f.local.setArmed(true) }
        assertSame(f.serverPhase, f.vm.phase)
    }

    @Test fun `local toggle refreshes stale gates before arming and explains the end of track handover`() = check { f ->
        f.serverMessage.value = "Mode DJ désactivé"
        f.caps.value = MusicAiCapabilitiesDto()
        coEvery { f.music.refresh(true) } coAnswers {
            f.caps.value = f.readyCaps
            ServerResult.Ok(Unit)
        }
        f.vm.toggle()
        runCurrent()
        coVerify(exactly = 1) { f.music.refresh(true) }
        verify(exactly = 1) { f.local.setArmed(true) }
        assertTrue(f.vm.message.value.contains("fin du titre"))
        assertFalse(f.vm.message.value.contains("désactivé"))
    }

    @Test fun `server toggle refreshes before delegating and permits trial readiness`() = check { f ->
        f.device.value = false
        f.caps.value = MusicAiCapabilitiesDto()
        coEvery { f.music.refresh(true) } coAnswers {
            verify(exactly = 0) { f.coordinator.toggleQueue() }
            f.caps.value = f.readyCaps
            ServerResult.Ok(Unit)
        }
        f.vm.toggle()
        runCurrent()
        verify(exactly = 1) { f.coordinator.toggleQueue() }
    }

    @Test fun `fresh disabled gates prevent local activation with visible reason`() = check { f ->
        coEvery { f.music.refresh(true) } coAnswers {
            f.caps.value = MusicAiCapabilitiesDto(version = 1)
            ServerResult.Ok(Unit)
        }
        f.vm.toggle()
        runCurrent()
        verify(exactly = 0) { f.local.setArmed(true) }
        assertTrue(f.vm.message.value.contains("Paramètres"))
        assertFalse(f.vm.availability.value.ready)
    }

    @Test fun `server errors are not masked by local device selection`() = check { f ->
        f.serverMessage.value = "Erreur du mix serveur"
        runCurrent()
        assertEquals("Erreur du mix serveur", f.vm.message.value)
    }

    @Test fun `refresh authentication and unavailable errors stay visible and never arm`() = check { f ->
        for ((code, word) in listOf(401 to "Session expirée", 503 to "indisponible")) {
            coEvery { f.music.refresh(true) } returns ServerResult.Failed(code)
            f.vm.toggle()
            runCurrent()
            assertTrue(f.vm.message.value.contains(word))
            assertFalse(f.vm.checking.value)
        }
        verify(exactly = 0) { f.local.setArmed(true) }
    }

    @Test fun `local queue requirements explain track shuffle and repeat constraints`() = check { f ->
        val cases = listOf(
            f.state.value.copy(track = null) to "bibliothèque",
            f.state.value.copy(upNext = emptyList()) to "au moins un",
            f.state.value.copy(shuffle = true) to "aléatoire",
            f.state.value.copy(repeat = RepeatMode.One) to "répétition",
        )
        for ((state, word) in cases) {
            f.state.value = state
            f.vm.toggle()
            runCurrent()
            assertTrue(f.vm.message.value.contains(word))
        }
        verify(exactly = 0) { f.local.setArmed(true) }
    }

    @Test fun `server needs two future tracks while local needs current and one future`() = check { f ->
        val oneNext = f.state.value.copy(upNext = f.state.value.upNext.take(1))
        assertTrue(queueDjAvailability(f.readyCaps, true, oneNext, true).ready)
        val server = queueDjAvailability(f.readyCaps, false, oneNext, true)
        assertFalse(server.ready)
        assertTrue(server.reason.contains("deux"))
        assertFalse(queueDjAvailability(f.readyCaps.copy(features = emptyMap()), true, oneNext, true).ready)
        val unsupported = f.readyCaps.copy(dj = f.readyCaps.dj.copy(queueSupported = false))
        assertFalse(queueDjAvailability(unsupported, false, f.state.value, true).ready)
    }

    @Test fun `refresh timeout is visible and cancellation prevents late activation`() = check { f ->
        val response = CompletableDeferred<Unit>()
        coEvery { f.music.refresh(true) } coAnswers {
            response.await()
            ServerResult.Ok(Unit)
        }
        f.vm.toggle()
        runCurrent()
        advanceTimeBy(10_001)
        runCurrent()
        assertTrue(f.vm.message.value.contains("délai"))
        assertFalse(f.vm.checking.value)
        f.vm.toggle()
        runCurrent()
        f.vm.toggle()
        response.complete(Unit)
        runCurrent()
        assertTrue(f.vm.message.value.contains("annulée"))
        verify(exactly = 0) { f.local.setArmed(true) }
    }

    @Test fun `account selection and engine changes fence delayed responses`() = check { f ->
        val changes: List<Pair<() -> Unit, String>> = listOf(
            {
                f.selection.value++
                Unit
            } to "Sélection",
            { f.device.value = false } to "Moteur",
            { f.owner.value = null } to "Compte",
        )
        for ((change, word) in changes) {
            f.device.value = true
            f.owner.value = f.session
            val response = CompletableDeferred<Unit>()
            coEvery { f.music.refresh(true) } coAnswers {
                response.await()
                ServerResult.Ok(Unit)
            }
            f.vm.toggle()
            runCurrent()
            change()
            response.complete(Unit)
            runCurrent()
            assertTrue(f.vm.message.value.contains(word))
            assertFalse(f.vm.checking.value)
        }
        verify(exactly = 0) { f.local.setArmed(true) }
        verify(exactly = 0) { f.coordinator.toggleQueue() }
    }

    @Test fun `changing device cancels pending activation and retains explanation`() = check { f ->
        val response = CompletableDeferred<Unit>()
        coEvery { f.music.refresh(true) } coAnswers {
            response.await()
            ServerResult.Ok(Unit)
        }
        f.vm.toggle()
        runCurrent()
        f.vm.chooseDevice(false)
        response.complete(Unit)
        runCurrent()
        assertFalse(f.vm.checking.value)
        assertTrue(f.vm.message.value.contains("Moteur DJ changé"))
        verify(exactly = 0) { f.local.setArmed(true) }
    }

    @Test fun `return from server timeout is visible without arming local DJ`() = check { f ->
        f.state.value = PlayerState(dj = PlayingDj("mix", "Mix"))
        f.vm.toggle()
        runCurrent()
        assertTrue(f.vm.checking.value)
        advanceTimeBy(2001)
        runCurrent()
        assertTrue(f.vm.message.value.contains("retour au lecteur classique tarde"))
        assertFalse(f.vm.checking.value)
        verify(exactly = 1) { f.coordinator.stop() }
        verify(exactly = 0) { f.local.setArmed(true) }
    }

    @Test fun `disarming and server stop are immediate without refresh`() = check { f ->
        f.localArmed.value = true
        f.vm.toggle()
        verify(exactly = 1) { f.local.setArmed(false) }
        f.device.value = false
        f.serverBusy.value = true
        f.vm.toggle()
        runCurrent()
        verify(exactly = 1) { f.coordinator.stop() }
        coVerify(exactly = 0) { f.music.refresh(any()) }
    }

    @Test fun `active playback can be disabled while opening the queue refreshes`() = check { f ->
        val response = CompletableDeferred<Unit>()
        coEvery { f.music.refresh(true) } coAnswers {
            response.await()
            ServerResult.Ok(Unit)
        }
        f.localArmed.value = true
        f.vm.onQueueShown()
        runCurrent()
        f.vm.toggle()
        verify(exactly = 1) { f.local.setArmed(false) }
        assertFalse(f.vm.checking.value)
        f.device.value = false
        f.serverBusy.value = true
        f.vm.onQueueShown()
        runCurrent()
        f.vm.toggle()
        verify(exactly = 1) { f.coordinator.stop() }
        response.complete(Unit)
        runCurrent()
        verify(exactly = 0) { f.local.setArmed(true) }
        verify(exactly = 0) { f.coordinator.toggleQueue() }
    }

    private fun check(block: suspend TestScope.(Fixture) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            block(fixture)
        } finally {
            fixture.vm.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    private class Fixture {
        val music = mockk<MusicAiRepository>(relaxed = true)
        val player = mockk<PlayerController>(relaxed = true)
        val coordinator = mockk<DjPlaybackCoordinator>(relaxed = true)
        val local = mockk<LocalDjSettings>(relaxed = true)
        val session = Session("https://fixture.test", "server", "Test", "device", "fixture")
        val owner = MutableStateFlow<Session?>(session)
        val device = MutableStateFlow(true)
        val localArmed = MutableStateFlow(false)
        val serverBusy = MutableStateFlow(false)
        val serverMessage = MutableStateFlow("")
        val serverPhase = MutableStateFlow(DjPhase.Classic)
        val selection = MutableStateFlow(0L)
        val readyCaps = MusicAiCapabilitiesDto(
            version = 1,
            features = mapOf("dj_interactive" to true, "dj_trial" to true),
            dj = MusicAiStatusDto(interactiveTrialReady = true, queueSupported = true),
        )
        val caps = MutableStateFlow(readyCaps)
        val state = MutableStateFlow(
            PlayerState(
                track = mockk<Track>(relaxed = true),
                upNext = listOf(QueueEntry(1, "one", "One", ""), QueueEntry(2, "two", "Two", "")),
            ),
        )
        val vm: QueueDjViewModel
        init {
            every { music.session } returns owner
            every { coordinator.capabilities } returns caps
            every { coordinator.message } returns serverMessage
            every { coordinator.phase } returns serverPhase
            every { coordinator.busy } returns serverBusy
            every { coordinator.armed } returns MutableStateFlow(false)
            every { local.device } returns device
            every { local.armed } returns localArmed
            every { local.transition } returns MutableStateFlow(LocalDjTransition())
            every { local.setArmed(any()) } answers { localArmed.value = firstArg() }
            every { local.chooseDevice(any()) } answers { device.value = firstArg() }
            every { player.state } returns state
            every { player.selectionVersion } returns selection
            coEvery { music.refresh(true) } returns ServerResult.Ok(Unit)
            vm = QueueDjViewModel(coordinator, local, player, music)
        }
    }
}
