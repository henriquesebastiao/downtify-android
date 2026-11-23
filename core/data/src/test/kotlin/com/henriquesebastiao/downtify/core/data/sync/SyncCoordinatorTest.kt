package com.henriquesebastiao.downtify.core.data.sync

import com.henriquesebastiao.downtify.core.data.catalog.ServerQueueRepository
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.listens.ListenReporter
import com.henriquesebastiao.downtify.core.data.playlists.PlaylistEditorRepository
import com.henriquesebastiao.downtify.core.data.session.ConnectionState
import com.henriquesebastiao.downtify.core.data.session.ServerRepository
import com.henriquesebastiao.downtify.core.data.settings.SettingsRepository
import com.henriquesebastiao.downtify.core.network.live.LiveEvent
import com.henriquesebastiao.downtify.core.network.live.LiveUpdatesClient
import com.henriquesebastiao.downtify.core.network.session.Session
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SyncCoordinatorTest {
    private val server = mockk<ServerRepository>(relaxed = true)
    private val library = mockk<LibraryRepository>(relaxed = true)
    private val listens = mockk<ListenReporter>(relaxed = true)
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val live = mockk<LiveUpdatesClient>()
    private val queue = mockk<ServerQueueRepository>(relaxed = true)
    private val playlists = mockk<PlaylistEditorRepository>(relaxed = true)
    private val session = Session("http://localhost", "server", "Test", "device", "test-token")

    @Test
    fun `first websocket connection recovers a failed initial probe`() = runTest {
        coEvery { server.refresh() } returns ConnectionState.Unreachable
        coEvery { library.sync(any()) } returns true
        every { live.events(any(), any()) } returns flow {
            emit(LiveEvent.Connected)
            awaitCancellation()
        }
        val coordinator = SyncCoordinator(server, library, listens, settings, live, queue, playlists, backgroundScope)
        backgroundScope.launch { coordinator.whilePaired(session) }
        runCurrent()
        coVerify(exactly = 1) { library.sync(false) }
        coVerify(exactly = 0) { library.sync(true) }
    }

    @Test
    fun `reconnect syncs incrementally after a successful initial probe`() = runTest {
        coEvery { server.refresh() } returns ConnectionState.Connected
        coEvery { library.sync(any()) } returns true
        var connections = 0
        every { live.events(any(), any()) } answers {
            flow {
                emit(LiveEvent.Connected)
                if (++connections > 1) awaitCancellation()
            }
        }
        val coordinator = SyncCoordinator(server, library, listens, settings, live, queue, playlists, backgroundScope)
        backgroundScope.launch { coordinator.whilePaired(session) }
        runCurrent()
        coVerify(exactly = 1) { library.sync(false) }
        advanceTimeBy(2_000)
        runCurrent()
        coVerify(exactly = 2) { library.sync(false) }
        coVerify(exactly = 0) { library.sync(true) }
    }

    @Test
    fun `first successful socket after failed attempts catches up incrementally`() = runTest {
        coEvery { server.refresh() } returns ConnectionState.Connected
        coEvery { library.sync(any()) } returns true
        var attempts = 0
        every { live.events(any(), any()) } answers {
            flow {
                if (++attempts > 1) {
                    emit(LiveEvent.Connected)
                    awaitCancellation()
                }
            }
        }
        val coordinator = SyncCoordinator(server, library, listens, settings, live, queue, playlists, backgroundScope)
        backgroundScope.launch { coordinator.whilePaired(session) }
        runCurrent()
        coVerify(exactly = 1) { library.sync(false) }
        advanceTimeBy(2_000)
        runCurrent()
        coVerify(exactly = 2) { library.sync(false) }
        coVerify(exactly = 0) { library.sync(true) }
    }

    @Test
    fun `ending a session cancels its active sync without marking it unreachable`() = runTest {
        coEvery { server.refresh() } returns ConnectionState.Connected
        coEvery { library.sync(any()) } coAnswers { awaitCancellation() }
        every { live.events(any(), any()) } returns flow { awaitCancellation() }
        val coordinator = SyncCoordinator(server, library, listens, settings, live, queue, playlists, backgroundScope)
        val job = backgroundScope.launch { coordinator.whilePaired(session) }
        runCurrent()
        job.cancel()
        runCurrent()
        coVerify(exactly = 1) { library.sync(false) }
        coVerify(exactly = 0) { server.markUnreachable() }
    }
}
