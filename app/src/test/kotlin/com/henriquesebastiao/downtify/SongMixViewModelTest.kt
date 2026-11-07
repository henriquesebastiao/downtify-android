package com.henriquesebastiao.downtify

import com.henriquesebastiao.downtify.core.data.ServerResult
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.library.LibrarySnapshot
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.session.Session
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import com.henriquesebastiao.downtify.core.player.DjPhase
import com.henriquesebastiao.downtify.core.player.DjPlaybackCoordinator
import com.henriquesebastiao.downtify.core.player.LocalDjSettings
import com.henriquesebastiao.downtify.core.player.PlayerController
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.core.player.PlayingStream
import com.henriquesebastiao.downtify.core.player.QueueEntry
import com.henriquesebastiao.downtify.ui.common.SongMixViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SongMixViewModelTest {
    @Test fun `disabled similarity makes no suggestion request`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fixture = Fixture(enabled = false)
            fixture.viewModel.start(fixture.seed)
            runCurrent()
            coVerify(exactly = 0) { fixture.ai.similar(any(), any(), any(), any()) }
            verify(exactly = 0) { fixture.player.startMix(any(), any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `a stream changed during analysis cannot be replaced by the old mix response`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fixture = Fixture(enabled = true)
            coEvery { fixture.ai.similar(any(), any(), any(), any()) } coAnswers {
                fixture.state.value = PlayerState(stream = PlayingStream("two", "", "", "", ""))
                Json.parseToJsonElement(
                    """{"tracks":[{"song_id":"next","name":"Next","source":"library"}]}""",
                ).jsonObject
            }
            fixture.viewModel.start(fixture.seed)
            runCurrent()
            coVerify(exactly = 1) { fixture.ai.similar("", "", false, "seed") }
            verify(exactly = 0) { fixture.player.startMix(any(), any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `local seed refreshes capabilities and arms only after the library queue arrives`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture(enabled = false, device = true)
            f.state.value = PlayerState(track = f.seed, positionMs = 42000)
            coEvery { f.ai.refresh(true) } coAnswers {
                f.capabilities.value =
                    MusicAiCapabilitiesDto(1, features = mapOf("similarity" to true, "dj_interactive" to true))
                ServerResult.Ok(Unit)
            }
            f.viewModel.startDj(f.seed)
            assertTrue(f.viewModel.djStatus.visible)
            assertTrue(f.viewModel.djStatus.preparing)
            runCurrent()
            coVerify(exactly = 1) { f.ai.refresh(true) }
            verify(exactly = 1) { f.player.startMix(f.seed, listOf(f.next)) }
            verify(exactly = 0) { f.local.setArmed(true) }
            verify(exactly = 0) { f.dj.startSeed(any()) }
            f.publishQueue()
            advanceTimeBy(50)
            runCurrent()
            verify(exactly = 1) { f.local.setArmed(true) }
            verify(exactly = 0) { f.local.chooseDevice(any()) }
            verify(exactly = 0) { f.player.seekTo(any()) }
            assertEquals(42000L, f.state.value.positionMs)
            assertTrue(f.viewModel.djStatus.canOpenPlayer)
            assertFalse(f.viewModel.djStatus.preparing)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `disabled local DJ gates never create a server seed request`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture(enabled = false, device = true)
            f.viewModel.startDj(f.seed)
            runCurrent()
            coVerify(exactly = 0) { f.ai.similar(any(), any(), any(), any()) }
            verify(exactly = 0) { f.dj.startSeed(any()) }
            verify(exactly = 0) { f.local.setArmed(true) }
            assertTrue(f.viewModel.djStatus.message.contains("Activez"))
            assertFalse(f.viewModel.djStatus.preparing)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `a selection change while local suggestions load cannot replace the queue`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture(enabled = true, device = true)
            coEvery { f.ai.similar(any(), any(), any(), any()) } coAnswers {
                f.selection.value++
                f.suggestions
            }
            f.viewModel.startDj(f.seed)
            runCurrent()
            verify(exactly = 0) { f.player.startMix(any(), any()) }
            verify(exactly = 0) { f.local.setArmed(true) }
            assertFalse(f.viewModel.djStatus.preparing)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `local cancellation retires delayed suggestions`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture(enabled = true, device = true)
            val response = CompletableDeferred<Unit>()
            coEvery { f.ai.similar(any(), any(), any(), any()) } coAnswers {
                response.await()
                f.suggestions
            }
            f.viewModel.startDj(f.seed)
            runCurrent()
            f.viewModel.cancelDjMix()
            response.complete(Unit)
            runCurrent()
            verify(exactly = 0) { f.player.startMix(any(), any()) }
            verify(exactly = 0) { f.local.setArmed(true) }
            assertTrue(f.viewModel.djStatus.message.contains("annulée"))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `account change retires a local request`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture(enabled = true, device = true)
            coEvery { f.ai.similar(any(), any(), any(), any()) } coAnswers {
                f.owner.value = null
                f.suggestions
            }
            f.viewModel.startDj(f.seed)
            runCurrent()
            verify(exactly = 0) { f.player.startMix(any(), any()) }
            verify(exactly = 0) { f.local.setArmed(true) }
            assertFalse(f.viewModel.djStatus.preparing)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `unconfirmed queue times out without arming local DJ`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture(enabled = true, device = true)
            f.viewModel.startDj(f.seed)
            runCurrent()
            advanceTimeBy(5001)
            runCurrent()
            verify(exactly = 1) { f.player.startMix(any(), any()) }
            verify(exactly = 0) { f.local.setArmed(true) }
            assertFalse(f.viewModel.djStatus.preparing)
            assertTrue(f.viewModel.djStatus.message.contains("confirmée"))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `account change retires preparing and ready server status without stopping the new account`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            for (phase in listOf(DjPhase.Preparing, DjPhase.Ready)) {
                val f = Fixture(enabled = true)
                f.viewModel.startDj(f.seed)
                f.djBusy.value = phase == DjPhase.Preparing
                f.djPhase.value = phase
                f.djMessage.value = "Ancien mix serveur"
                runCurrent()
                f.owner.value = mockk(relaxed = true)
                f.djBusy.value = false
                f.djPhase.value = DjPhase.Classic
                f.djMessage.value = ""
                runCurrent()
                assertFalse(f.viewModel.djStatus.preparing)
                assertFalse(f.viewModel.djStatus.canCancel)
                assertFalse(f.viewModel.djStatus.canOpenPlayer)
                assertTrue(f.viewModel.djStatus.message.contains("Compte ou serveur changé"))
                val retired = f.viewModel.djStatus
                f.djBusy.value = true
                f.djPhase.value = DjPhase.Preparing
                f.djMessage.value = "Mix du nouveau compte"
                runCurrent()
                assertEquals(retired, f.viewModel.djStatus)
                verify(exactly = 0) { f.dj.stop() }
                verify(exactly = 0) { f.local.setArmed(any()) }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `account change retires ready local status without disarming the new account`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture(enabled = true, device = true)
            f.viewModel.startDj(f.seed)
            runCurrent()
            f.publishQueue()
            advanceTimeBy(50)
            runCurrent()
            assertTrue(f.viewModel.djStatus.canOpenPlayer)
            verify(exactly = 1) { f.local.setArmed(true) }
            f.owner.value = mockk(relaxed = true)
            runCurrent()
            assertFalse(f.viewModel.djStatus.canOpenPlayer)
            assertFalse(f.viewModel.djStatus.canCancel)
            assertTrue(f.viewModel.djStatus.message.contains("Compte ou serveur changé"))
            verify(exactly = 0) { f.local.setArmed(false) }
            verify(exactly = 0) { f.dj.stop() }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `server status persists and follows preparation ready and error flows`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = Fixture(enabled = true)
            f.viewModel.startDj(f.seed)
            assertTrue(f.viewModel.djStatus.visible)
            verify(exactly = 1) { f.dj.startSeed("seed") }
            f.djBusy.value = true
            f.djPhase.value = DjPhase.Preparing
            f.djMessage.value = "Préparation des transitions"
            runCurrent()
            assertTrue(f.viewModel.djStatus.preparing)
            f.djBusy.value = false
            f.djPhase.value = DjPhase.Ready
            f.djMessage.value = "Relais au prochain titre"
            runCurrent()
            assertTrue(f.viewModel.djStatus.canOpenPlayer)
            f.djPhase.value = DjPhase.Classic
            f.djMessage.value = "Serveur indisponible"
            runCurrent()
            assertEquals("Serveur indisponible", f.viewModel.djStatus.message)
            assertFalse(f.viewModel.djStatus.canOpenPlayer)
            assertEquals(null, f.viewModel.message)
            f.viewModel.dismissDjStatus()
            assertFalse(f.viewModel.djStatus.visible)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class Fixture(enabled: Boolean, device: Boolean = false) {
        val seed = mockk<Track>(relaxed = true) { every { id } returns "seed" }
        val next = mockk<Track>(relaxed = true) { every { id } returns "next" }
        val state = MutableStateFlow(PlayerState(stream = PlayingStream("one", "", "", "", "")))
        val selection = MutableStateFlow(0L)
        val owner = MutableStateFlow<Session?>(mockk(relaxed = true))
        val player = mockk<PlayerController>(relaxed = true) {
            every { state } returns this@Fixture.state
            every { selectionVersion } returns selection
        }
        val capabilities = MutableStateFlow(
            MusicAiCapabilitiesDto(
                1,
                features = mapOf(
                    "similarity" to enabled,
                    "dj_interactive" to enabled,
                ),
            ),
        )
        val suggestions = Json.parseToJsonElement(
            """
            {"tracks":[
                {"song_id":"next","name":"Next","source":"library"},
                {"song_id":"missing","source":"library"},
                {"song_id":"seed","source":"library"},
                {"song_id":"next","source":"library"}
            ]}
            """.trimIndent(),
        ).jsonObject
        val ai = mockk<MusicAiRepository> {
            every { capabilities } returns this@Fixture.capabilities
            every { session } returns owner
            coEvery { refresh(any()) } returns ServerResult.Ok(Unit)
            coEvery { similar(any(), any(), any(), any()) } returns suggestions
        }
        private val library = mockk<LibraryRepository> {
            every { library } returns MutableStateFlow<LibrarySnapshot?>(LibrarySnapshot(listOf(seed, next)))
        }
        private val sessions = mockk<SessionStore> { every { current } answers { owner.value } }
        val djBusy = MutableStateFlow(false)
        val djPhase = MutableStateFlow(DjPhase.Classic)
        val djMessage = MutableStateFlow("")
        val dj = mockk<DjPlaybackCoordinator>(relaxed = true) {
            every { busy } returns djBusy
            every { phase } returns djPhase
            every { message } returns djMessage
            every { armed } returns MutableStateFlow(false)
        }
        val local = mockk<LocalDjSettings>(relaxed = true) {
            every { this@mockk.device } returns MutableStateFlow(device)
        }
        val viewModel = SongMixViewModel(ai, library, player, sessions, dj, local)

        fun publishQueue() {
            if (state.value.track?.id != seed.id) selection.value++
            state.value = state.value.copy(
                track = seed,
                stream = null,
                upNext = listOf(QueueEntry(1, "next", "Next", "")),
            )
        }
    }
}
