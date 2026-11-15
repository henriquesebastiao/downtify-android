package com.henriquesebastiao.downtify.feature.musicai

import androidx.lifecycle.ViewModelStore
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiFailure
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto
import com.henriquesebastiao.downtify.core.network.session.Session
import com.henriquesebastiao.downtify.core.player.DjPlaybackCoordinator
import com.henriquesebastiao.downtify.core.player.PlayerController
import com.henriquesebastiao.downtify.core.player.PlayerState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyDjDetailsLoadTest {
    private val fixtures = mutableListOf<Fixture>()
    private fun fixture() = Fixture().also { fixtures.add(it) }
    private fun closeModels() {
        fixtures.forEach { it.store.clear() }
        fixtures.clear()
    }

    private class Fixture {
        val store = ViewModelStore()
        val music = mockk<MusicAiRepository>()
        val owner = MutableStateFlow<Session?>(null)
        val caps = MutableStateFlow(
            MusicAiCapabilitiesDto(
                version = 1,
                features = mapOf("weekly" to true, "dj_prepared" to true, "dj_weekly" to true),
                djWeeklySets = true,
            ),
        )
        val player = mockk<PlayerController>(relaxed = true)
        val coordinator = mockk<DjPlaybackCoordinator>(relaxed = true)
        val model: MusicDjViewModel
        init {
            every { music.session } returns owner
            every { music.capabilities } returns caps
            every { player.state } returns MutableStateFlow(PlayerState())
            every { coordinator.message } returns MutableStateFlow("")
            every { coordinator.busy } returns MutableStateFlow(false)
            model = MusicDjViewModel(music, player, coordinator)
            store.put("weekly", model)
        }
    }

    @Test fun disabledFeatureDoesNotFetchDetails() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = fixture()
            f.caps.value = MusicAiCapabilitiesDto()
            f.model.loadWeeklyDetails("a".repeat(32))
            runCurrent()
            coVerify(exactly = 0) { f.music.djSession(any()) }
        } finally {
            closeModels()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test fun oldResponseCannotReplaceTheNewSelectedSet() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = fixture()
            val old = MusicDjSessionDto("a".repeat(32), state = "ready", theme = "rock")
            val latest = old.copy(id = "b".repeat(32))
            val pending = CompletableDeferred<MusicDjSessionDto>()
            coEvery { f.music.djSession(old.id) } coAnswers { withContext(NonCancellable) { pending.await() } }
            coEvery { f.music.djSession(latest.id) } returns latest
            f.model.loadWeeklyDetails(old.id)
            runCurrent()
            f.model.loadWeeklyDetails(latest.id)
            runCurrent()
            pending.complete(old)
            runCurrent()
            assertEquals(latest, f.model.state.value.details)
            assertFalse(f.model.state.value.detailsLoading)
        } finally {
            closeModels()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test fun leavingThePageRetiresAnUncancellableResponse() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = fixture()
            val old = MusicDjSessionDto("a".repeat(32), state = "ready", theme = "rock")
            val pending = CompletableDeferred<MusicDjSessionDto>()
            coEvery { f.music.djSession(old.id) } coAnswers { withContext(NonCancellable) { pending.await() } }
            f.model.loadWeeklyDetails(old.id)
            runCurrent()
            f.model.clearWeeklyDetails()
            pending.complete(old)
            runCurrent()
            assertNull(f.model.state.value.details)
            assertFalse(f.model.state.value.detailsLoading)
        } finally {
            closeModels()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test fun networkFailureKeepsAnExplicitRetryState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = fixture()
            coEvery { f.music.djSession(any()) } throws MusicAiFailure(503)
            f.model.loadWeeklyDetails("a".repeat(32))
            runCurrent()
            assertTrue(f.model.state.value.detailsMessage.isNotBlank())
            assertFalse(f.model.state.value.detailsLoading)
            assertNull(f.model.state.value.details)
        } finally {
            closeModels()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test fun disablingTheFeatureRetiresPendingDetails() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = fixture()
            val old = MusicDjSessionDto("a".repeat(32), state = "ready", theme = "rock")
            val pending = CompletableDeferred<MusicDjSessionDto>()
            coEvery { f.music.djSession(old.id) } coAnswers { withContext(NonCancellable) { pending.await() } }
            f.model.loadWeeklyDetails(old.id)
            runCurrent()
            f.caps.value = MusicAiCapabilitiesDto()
            runCurrent()
            pending.complete(old)
            runCurrent()
            assertNull(f.model.state.value.details)
            assertFalse(f.model.state.value.detailsLoading)
        } finally {
            closeModels()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}
