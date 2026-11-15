package com.henriquesebastiao.downtify.core.data.offline

import android.content.Context
import androidx.work.Operation
import androidx.work.impl.WorkManagerImpl
import com.google.common.util.concurrent.ListenableFuture
import com.henriquesebastiao.downtify.core.data.db.DowntifyDatabase
import com.henriquesebastiao.downtify.core.data.db.LikeDao
import com.henriquesebastiao.downtify.core.data.db.OfflineDao
import com.henriquesebastiao.downtify.core.data.db.PlaylistDao
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.library.LibrarySnapshot
import com.henriquesebastiao.downtify.core.data.loudness.LoudnessProfiles
import com.henriquesebastiao.downtify.core.data.settings.SettingsRepository
import com.henriquesebastiao.downtify.core.data.settings.UserSettings
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verifyOrder
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineRepositoryStorageTest {
    private val storage = mockk<OfflineStorage>(relaxed = true)
    private val settings = mockk<SettingsRepository> {
        every { settings } returns MutableStateFlow(UserSettings())
    }
    private val context = mockk<Context>(relaxed = true)
    private val workManager = mockk<WorkManagerImpl>()
    private val work = mockk<Operation>()
    private val future = mockk<ListenableFuture<Operation.State.SUCCESS?>>()

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `location changes cancel downloads before moving and persist only after success`() = runTest {
        mockWorkCancellation()

        val moved = repository().setDirectory("content://music/tree/selected")

        assertTrue(moved)
        verifyOrder {
            workManager.cancelUniqueWork(OfflineRepository.WORK_NAME)
            storage.moveTo("content://music/tree/selected")
        }
        coVerify { settings.setOfflineDirectory("content://music/tree/selected") }
    }

    @Test
    fun `failed location move keeps the previous setting`() = runTest {
        mockWorkCancellation()
        every { storage.moveTo("content://music/tree/denied") } throws IOException("denied")

        val repository = repository()
        val moved = repository.setDirectory("content://music/tree/denied")

        assertFalse(moved)
        coVerify(exactly = 0) { settings.setOfflineDirectory(any()) }
        assertTrue(repository.locationChange.value is OfflineLocationChange.Failed)
    }

    @Test
    fun `storage failure is surfaced without escaping reconciliation`() = runTest {
        every { storage.playlistPaths() } throws IOException("revoked")
        val repository = repository()
        val state = OfflineState(
            collections = emptyList(),
            files = emptyMap(),
            plan = com.henriquesebastiao.downtify.core.model.OfflinePlan.Empty,
            limitBytes = 1,
            wifiOnly = true,
        )

        repository.reconcileSafely(state)

        assertTrue(repository.locationChange.value is OfflineLocationChange.Failed)
    }

    private fun mockWorkCancellation() {
        mockkStatic(WorkManagerImpl::class)
        every { WorkManagerImpl.getInstance(context) } returns workManager
        every { workManager.cancelUniqueWork(OfflineRepository.WORK_NAME) } returns work
        every { work.result } returns future
        every { future.get() } returns null
    }

    private fun TestScope.repository(): OfflineRepository {
        val dao = mockk<OfflineDao> {
            every { observeCollections() } returns emptyFlow()
            every { observeFiles() } returns emptyFlow()
        }
        val playlists = mockk<PlaylistDao> { every { observeAll() } returns emptyFlow() }
        val likes = mockk<LikeDao> { every { observeIds() } returns emptyFlow() }
        val db = mockk<DowntifyDatabase> {
            every { offline() } returns dao
            every { playlists() } returns playlists
            every { likes() } returns likes
        }
        val library = mockk<LibraryRepository> {
            every { library } returns MutableStateFlow(LibrarySnapshot.Empty)
        }
        coEvery { settings.setOfflineDirectory(any()) } returns Unit
        return OfflineRepository(
            context = context,
            db = db,
            library = library,
            settings = settings,
            storage = storage,
            sessions = mockk<SessionStore>(relaxed = true),
            loudness = mockk<LoudnessProfiles>(relaxed = true),
            client = OkHttpClient(),
            scope = backgroundScope,
        )
    }
}
