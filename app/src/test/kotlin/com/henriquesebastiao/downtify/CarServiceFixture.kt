package com.henriquesebastiao.downtify

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import com.henriquesebastiao.downtify.core.data.NetworkMonitor
import com.henriquesebastiao.downtify.core.data.library.LibrarySnapshot
import com.henriquesebastiao.downtify.core.data.offline.OfflineState
import com.henriquesebastiao.downtify.core.model.PlaybackSnapshot
import com.henriquesebastiao.downtify.core.model.Playlist
import com.henriquesebastiao.downtify.core.model.SavedPlaybackItem
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.network.session.Session
import com.henriquesebastiao.downtify.core.player.PlaybackService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.robolectric.util.ReflectionHelpers

/** Fake storage/network only: the service, ExoPlayer and Media3 binder remain real. */
@UnstableApi
internal class CarServiceFixture {
    val association = MutableStateFlow<Session?>(Session("https://fixture.test", "server", "Car", "device", "fixture"))
    val source = mockk<DataSource.Factory>()
    val saved = PlaybackSnapshot(
        listOf(SavedPlaybackItem("a", "One", "Artist"), SavedPlaybackItem("b", "Two", "Artist")),
        1,
        12_345,
        true,
        listOf(1, 0),
        Player.REPEAT_MODE_ALL,
        1.25f,
    )

    fun inject(service: PlaybackService) {
        // Replace Hilt injection, not any service method. No production dependency graph or network is started.
        ReflectionHelpers.setField(service, "injected", true)
        service.audioDataSource = source
        service.artworkDataSource = source
        service.library = mockk(relaxed = true) {
            every { library } returns
                MutableStateFlow<LibrarySnapshot?>(LibrarySnapshot(listOf(track("a"), track("b"))))
            every { playlists } returns MutableStateFlow(listOf(Playlist("Car", false, "", listOf("b", "a"))))
            every { likedIds } returns MutableStateFlow(listOf("a"))
            every { successfulSync } returns MutableStateFlow(0)
        }
        service.sessions = mockk(relaxed = true) {
            every { current } answers { association.value }
            every { session } returns association
        }
        service.playback = mockk(relaxed = true) {
            coEvery { load(any()) } returns saved
        }
        service.offline = mockk(relaxed = true) {
            every { state } returns MutableStateFlow<OfflineState?>(null)
        }
        service.network = mockk {
            every { status } returns MutableStateFlow(NetworkMonitor.Status(false, false))
        }
        service.loudness = mockk(relaxed = true) {
            every { enabled } returns MutableStateFlow(false)
        }
        service.readAhead = mockk(relaxed = true) { every { enabled } returns true }
        service.artwork = mockk(relaxed = true) {
            coEvery { cached(any(), any()) } returns null
            coEvery { prepare(any(), any(), any()) } returns null
        }
        service.reporter = mockk(relaxed = true)
        service.activityReporter = mockk(relaxed = true)
        service.podcasts = mockk(relaxed = true)
        service.catalog = mockk(relaxed = true)
        service.resolver = mockk(relaxed = true)
        service.streamingClient = mockk(relaxed = true)
        service.syncCoordinator = mockk(relaxed = true)
    }

    private fun track(id: String) = Track(
        id, "$id.flac", if (id == "a") "One" else "Two", "Artist", listOf("Artist"), "Album", "Artist",
        "album", "artist", if (id == "a") 1 else 2, "2026", 120.0, "flac", 0, 44100, 2, 100, 0, false, emptyList(),
    )
}
