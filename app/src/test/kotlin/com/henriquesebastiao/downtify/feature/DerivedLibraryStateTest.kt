package com.henriquesebastiao.downtify.feature

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.henriquesebastiao.downtify.core.data.ServerResult
import com.henriquesebastiao.downtify.core.data.catalog.CatalogRepository
import com.henriquesebastiao.downtify.core.data.catalog.CatalogSearch
import com.henriquesebastiao.downtify.core.data.catalog.ServerQueueRepository
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.library.LibrarySnapshot
import com.henriquesebastiao.downtify.core.data.library.SyncStatus
import com.henriquesebastiao.downtify.core.data.settings.LibraryLayout
import com.henriquesebastiao.downtify.core.data.settings.LibrarySort
import com.henriquesebastiao.downtify.core.data.settings.SettingsRepository
import com.henriquesebastiao.downtify.core.data.settings.UserSettings
import com.henriquesebastiao.downtify.core.data.sync.SyncCoordinator
import com.henriquesebastiao.downtify.core.model.Capabilities
import com.henriquesebastiao.downtify.core.model.Playlist
import com.henriquesebastiao.downtify.core.model.RemoteSong
import com.henriquesebastiao.downtify.core.model.ServerInfo
import com.henriquesebastiao.downtify.core.model.ServerJob
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.player.NowPlayingRef
import com.henriquesebastiao.downtify.core.player.PlayerController
import com.henriquesebastiao.downtify.core.player.RemotePlayback
import com.henriquesebastiao.downtify.feature.library.LibraryViewModel
import com.henriquesebastiao.downtify.feature.search.SearchViewModel
import com.henriquesebastiao.downtify.feature.search.ServerResults
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DerivedLibraryStateTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModelStore()
    private val repository = mockk<LibraryRepository>()
    private val settings = mockk<SettingsRepository>()
    private val player = mockk<PlayerController>()
    private val catalog = mockk<CatalogRepository>()
    private val queue = mockk<ServerQueueRepository>()
    private val remotePlayback = mockk<RemotePlayback>()
    private val snapshot = MutableStateFlow<LibrarySnapshot?>(LibrarySnapshot(listOf(track("b"), track("a"))))
    private val playlists = MutableStateFlow<List<Playlist>>(emptyList())
    private val likes = MutableStateFlow<List<String>>(emptyList())
    private val preferences = MutableStateFlow(UserSettings(librarySort = LibrarySort.Alphabetical))
    private val playing = MutableStateFlow(NowPlayingRef())
    private val serverInfo = MutableStateFlow<com.henriquesebastiao.downtify.core.model.ServerInfo?>(null)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { repository.library } returns snapshot
        every { repository.playlists } returns playlists
        every { repository.likedIds } returns likes
        every { repository.syncStatus } returns MutableStateFlow(SyncStatus.Idle)
        every { settings.settings } returns preferences
        every { player.nowPlaying } returns playing
        every { queue.jobs } returns MutableStateFlow<Map<String, ServerJob>>(emptyMap())
        every { catalog.serverInfo } returns serverInfo
        every { catalog.requestedAlbums } returns MutableStateFlow<Map<String, List<String>>>(emptyMap())
        every { remotePlayback.resolvingId } returns MutableStateFlow<String?>(null)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `likes layout and playback preserve sorted lists but sorting still updates`() = runTest(dispatcher) {
        val model = LibraryViewModel(repository, settings, mockk<SyncCoordinator>(), player, SavedStateHandle())
        viewModels.put("subject", model)
        model.uiState.test {
            val original = matching { !it.loading }
            assertEquals(listOf("a", "b"), original.songs.map { it.id })
            likes.value = listOf("b")
            val liked = matching { "b" in it.likedIds }
            assertSame(original.songs, liked.songs)
            assertSame(original.albums, liked.albums)
            assertSame(original.artists, liked.artists)
            assertEquals(1, liked.playlists.first().count)
            preferences.value = preferences.value.copy(libraryLayout = LibraryLayout.List, amoled = true)
            val layout = matching { it.layout == LibraryLayout.List }
            assertSame(original.songs, layout.songs)
            playing.value = NowPlayingRef(trackId = "b", isPlaying = true)
            val active = matching { it.isPlaying }
            assertSame(original.songs, active.songs)
            assertEquals("b", active.currentTrackId)
            preferences.value = preferences.value.copy(librarySort = LibrarySort.RecentlyAdded)
            val sorted = matching { it.sort == LibrarySort.RecentlyAdded }
            assertEquals(listOf("b", "a"), sorted.songs.map { it.id })
            assertNotSame(original.songs, sorted.songs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search results survive playback and likes but update with the catalog`() = runTest(dispatcher) {
        val model = SearchViewModel(
            SavedStateHandle(mapOf("query" to "x")),
            repository,
            player,
            catalog,
            queue,
            remotePlayback,
        )
        viewModels.put("subject", model)
        model.uiState.test {
            val original = matching { it.results.songs.size == 2 }
            playing.value = NowPlayingRef(trackId = "a", isPlaying = true)
            val active = matching { it.isPlaying }
            assertSame(original.results, active.results)
            assertEquals("a", active.currentTrackId)
            likes.value = listOf("a")
            val liked = matching { "a" in it.likedIds }
            assertSame(original.results, liked.results)
            snapshot.value = LibrarySnapshot(listOf(track("c")))
            val changed = matching { it.results.songs.map(Track::id) == listOf("c") }
            assertNotSame(original.results, changed.results)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `library only change clears remote results and preserves local search`() = runTest(dispatcher) {
        snapshot.value = LibrarySnapshot(listOf(track("a").copy(title = "Tune")))
        serverInfo.value = ServerInfo("test", "Test", "Downtify", "3.7.0", 1, true, Capabilities())
        coEvery { catalog.search("Tu") } returns
            ServerResult.Ok(CatalogSearch(listOf(mockk<RemoteSong>()), emptyList()))
        val model =
            SearchViewModel(
                SavedStateHandle(mapOf("query" to "Tu")),
                repository,
                player,
                catalog,
                queue,
                remotePlayback,
            )
        viewModels.put("subject", model)
        model.uiState.test {
            val online = matching { it.server is ServerResults.Found && it.results.songs.size == 1 }
            serverInfo.value = serverInfo.value?.copy(libraryOnly = true)
            val local = matching { it.server == ServerResults.Idle }
            assertSame(online.results, local.results)
            assertEquals(listOf("a"), local.results.songs.map(Track::id))
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Optional host-side comparison; run the same test against main and this branch. */
    @Test
    fun `benchmark library updates without catalog changes`() = runTest(dispatcher) {
        org.junit.Assume.assumeTrue(System.getenv("DOWNTIFY_BENCHMARK") == "1")
        for (size in listOf(2_000, 7_000, 20_000)) {
            viewModels.clear()
            snapshot.value = syntheticLibrary(size)
            likes.value = emptyList()
            val model = LibraryViewModel(repository, settings, mockk<SyncCoordinator>(), player, SavedStateHandle())
            viewModels.put("subject", model)
            model.uiState.test {
                var previous = matching { !it.loading }
                var rebuilt = 0
                val samples = mutableListOf<Double>()
                repeat(25) { index ->
                    val id = index.toString()
                    val started = System.nanoTime()
                    likes.value = listOf(id)
                    val updated = matching { it.likedIds == setOf(id) }
                    val elapsed = (System.nanoTime() - started) / 1_000_000.0
                    if (index >= 5) {
                        samples += elapsed
                        if (previous.songs !== updated.songs) rebuilt++
                    }
                    previous = updated
                }
                println("BENCH library size=$size median_ms=${samples.sorted()[samples.size / 2]} rebuilt=$rebuilt/20")
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `benchmark search updates without query changes`() = runTest(dispatcher) {
        org.junit.Assume.assumeTrue(System.getenv("DOWNTIFY_BENCHMARK") == "1")
        for (size in listOf(2_000, 7_000, 20_000)) {
            viewModels.clear()
            snapshot.value = syntheticLibrary(size)
            playing.value = NowPlayingRef()
            val model = SearchViewModel(
                SavedStateHandle(mapOf("query" to "z")),
                repository,
                player,
                catalog,
                queue,
                remotePlayback,
            )
            viewModels.put("subject", model)
            model.uiState.test {
                var previous = matching { it.query == "z" && it.library != null }
                var rebuilt = 0
                val samples = mutableListOf<Double>()
                repeat(25) { index ->
                    val id = index.toString()
                    val started = System.nanoTime()
                    playing.value = NowPlayingRef(trackId = id, isPlaying = true)
                    val updated = matching { it.currentTrackId == id }
                    val elapsed = (System.nanoTime() - started) / 1_000_000.0
                    if (index >= 5) {
                        samples += elapsed
                        if (previous.results !== updated.results) rebuilt++
                    }
                    previous = updated
                }
                println("BENCH search size=$size median_ms=${samples.sorted()[samples.size / 2]} rebuilt=$rebuilt/20")
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
    private suspend fun <T> ReceiveTurbine<T>.matching(predicate: (T) -> Boolean): T {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    private fun syntheticLibrary(size: Int) = LibrarySnapshot(
        List(size) { index ->
            val artist = "Artist ${index / 6}"
            track(index.toString()).copy(
                album = "Album ${index / 3}",
                albumId = "album-${index / 3}",
                artist = artist,
                artists = listOf(artist),
                albumArtist = artist,
                artistId = "artist-${index / 6}",
            )
        },
    )
    private fun track(id: String) = Track(
        id = id, file = "$id.mp3", title = "x $id", artist = "Artist", artists = listOf("Artist"),
        album = "Album", albumArtist = "Artist", albumId = "album", artistId = "artist",
        trackNumber = 1, year = "2026", duration = 120.0, codec = "mp3", bitrate = 320000,
        sampleRate = 44100, channels = 2, size = 1000, added = 1, hasCover = true, playlists = emptyList(),
    )
}
