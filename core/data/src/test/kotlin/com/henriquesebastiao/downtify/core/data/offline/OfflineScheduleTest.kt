package com.henriquesebastiao.downtify.core.data.offline

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.impl.WorkManagerImpl
import com.henriquesebastiao.downtify.core.data.db.DowntifyDatabase
import com.henriquesebastiao.downtify.core.data.db.LikeDao
import com.henriquesebastiao.downtify.core.data.db.OfflineDao
import com.henriquesebastiao.downtify.core.data.db.PlaylistDao
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.library.LibrarySnapshot
import com.henriquesebastiao.downtify.core.data.loudness.LoudnessProfiles
import com.henriquesebastiao.downtify.core.data.settings.SettingsRepository
import com.henriquesebastiao.downtify.core.data.settings.UserSettings
import com.henriquesebastiao.downtify.core.model.OfflinePlan
import com.henriquesebastiao.downtify.core.model.OfflinePlanner
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineScheduleTest {
    private val storage = mockk<OfflineStorage>(relaxed = true)
    private val settings = mockk<SettingsRepository> {
        every { settings } returns MutableStateFlow(UserSettings())
    }
    private val context = mockk<Context>(relaxed = true)
    private val workManager = mockk<WorkManagerImpl>()
    private val operation = mockk<Operation>()

    private val track = Track(
        id = "t1", file = "t1.flac", title = "t1", artist = "A", artists = listOf("A"), album = "Alb",
        albumArtist = "A", albumId = "alb1", artistId = "a", trackNumber = 1, year = "", duration = 1.0,
        codec = "flac", bitrate = 0, sampleRate = 0, channels = 2, size = 100, added = 0, hasCover = false,
        playlists = emptyList(),
    )

    @After
    fun tearDown() = unmockkAll()

    /**
     * The regression: a run of the worker that ended (failed, out of space, the app
     * killed) must not leave its ids marked as "already queued", or the song added
     * to a kept playlist after it waits for the next app start.
     */
    @Test
    fun `every reconcile hands the missing tracks over again`() = runTest {
        val policies = mutableListOf<ExistingWorkPolicy>()
        mockWorkManager(policies)
        val repository = repository()

        repository.reconcileSafely(state(wifiOnly = false))
        repository.reconcileSafely(state(wifiOnly = false))
        repository.reconcileSafely(state(wifiOnly = true))

        assertEquals(
            listOf(
                // Nothing queued yet, then work already waiting: `KEEP` does nothing.
                ExistingWorkPolicy.REPLACE,
                ExistingWorkPolicy.KEEP,
                // The network constraint changed: constraints can't be edited in place.
                ExistingWorkPolicy.REPLACE,
            ),
            policies,
        )
    }

    @Test
    fun `nothing to download enqueues nothing`() = runTest {
        val policies = mutableListOf<ExistingWorkPolicy>()
        mockWorkManager(policies)

        repository().reconcileSafely(state(wifiOnly = true, toDownload = emptyList()))

        assertEquals(emptyList<ExistingWorkPolicy>(), policies)
    }

    private fun mockWorkManager(policies: MutableList<ExistingWorkPolicy>) {
        mockkStatic(WorkManagerImpl::class)
        every { WorkManagerImpl.getInstance(context) } returns workManager
        every {
            workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        } answers {
            policies += secondArg<ExistingWorkPolicy>()
            operation
        }
    }

    private fun state(wifiOnly: Boolean, toDownload: List<Track> = listOf(track)) = OfflineState(
        collections = emptyList(),
        files = emptyMap(),
        plan = OfflinePlan(toDownload, setOf(track.id), emptySet(), emptySet(), track.size),
        limitBytes = OfflinePlanner.UNLIMITED,
        wifiOnly = wifiOnly,
    )

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
        every { storage.playlistPaths() } returns emptySet()
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
