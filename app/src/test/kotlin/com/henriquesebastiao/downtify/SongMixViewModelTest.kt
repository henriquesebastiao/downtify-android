package com.henriquesebastiao.downtify

import com.henriquesebastiao.downtify.core.data.ServerResult
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.library.LibrarySnapshot
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import com.henriquesebastiao.downtify.core.player.PlayerController
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.core.player.PlayingStream
import com.henriquesebastiao.downtify.ui.common.SongMixViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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

    private class Fixture(enabled: Boolean) {
        val seed = mockk<Track>(relaxed = true) { every { id } returns "seed" }
        val state = MutableStateFlow(PlayerState(stream = PlayingStream("one", "", "", "", "")))
        val player = mockk<PlayerController>(relaxed = true) { every { state } returns this@Fixture.state }
        val ai = mockk<MusicAiRepository> {
            every { capabilities } returns
                MutableStateFlow(MusicAiCapabilitiesDto(1, features = mapOf("similarity" to enabled)))
            coEvery { refresh() } returns ServerResult.Ok(Unit)
        }
        private val library = mockk<LibraryRepository> {
            every { library } returns MutableStateFlow<LibrarySnapshot?>(LibrarySnapshot.Empty)
        }
        private val sessions = mockk<SessionStore> { every { current } returns mockk(relaxed = true) }
        val viewModel = SongMixViewModel(ai, library, player, sessions)
    }
}
