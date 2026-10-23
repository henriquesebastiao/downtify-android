package com.henriquesebastiao.downtify.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.library.LibrarySnapshot
import com.henriquesebastiao.downtify.core.data.library.SyncStatus
import com.henriquesebastiao.downtify.core.data.settings.LibraryLayout
import com.henriquesebastiao.downtify.core.data.settings.LibrarySort
import com.henriquesebastiao.downtify.core.data.settings.SettingsRepository
import com.henriquesebastiao.downtify.core.data.sync.SyncCoordinator
import com.henriquesebastiao.downtify.core.model.Album
import com.henriquesebastiao.downtify.core.model.Artist
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.Playlist
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryTab { Songs, Albums, Artists, Playlists }

/** A playlist row: [playlist] null is "Liked songs". */
data class PlaylistItem(val playlist: Playlist?, val title: String, val count: Int, val coverTrackId: String?)

data class LibraryUiState(
    val loading: Boolean = true,
    val tab: LibraryTab = LibraryTab.Songs,
    val layout: LibraryLayout = LibraryLayout.Grid,
    val sort: LibrarySort = LibrarySort.RecentlyAdded,
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val songs: List<Track> = emptyList(),
    val refreshing: Boolean = false,
    val currentTrackId: String? = null,
    val isPlaying: Boolean = false,
    val likedIds: Set<String> = emptySet(),
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val sync: SyncCoordinator,
    private val player: PlayerController,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val tab = savedState.getStateFlow(KEY_TAB, LibraryTab.Songs.name)

    /** Only the catalog and sort order can change these sorted lists. */
    private val sorted = combine(
        library.library,
        settings.settings.map { it.librarySort }.distinctUntilChanged(),
    ) { snapshot, sort ->
        snapshot to LibraryUiState(
            loading = snapshot == null,
            sort = sort,
            albums = sortAlbums(snapshot?.albums.orEmpty(), sort),
            artists = sortArtists(snapshot?.artists.orEmpty(), sort),
            songs = sortSongs(snapshot?.tracks.orEmpty(), sort),
        )
    }.flowOn(Dispatchers.Default)

    private val content = combine(
        sorted,
        library.playlists,
        library.likedIds,
        settings.settings.map { it.libraryLayout }.distinctUntilChanged(),
    ) { (snapshot, lists), playlists, liked, layout ->
        lists.copy(
            layout = layout,
            playlists = playlistItems(snapshot, playlists, liked),
            likedIds = liked.toSet(),
        )
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<LibraryUiState> = combine(
        content,
        tab,
        library.syncStatus,
        player.nowPlaying,
    ) { content, tabName, syncStatus, playing ->
        content.copy(
            tab = LibraryTab.entries.firstOrNull { it.name == tabName } ?: LibraryTab.Songs,
            refreshing = syncStatus == SyncStatus.Syncing,
            currentTrackId = playing.trackId,
            isPlaying = playing.isPlaying,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

    fun selectTab(value: LibraryTab) {
        savedState[KEY_TAB] = value.name
    }

    fun setSort(value: LibrarySort) {
        viewModelScope.launch { settings.setLibrarySort(value) }
    }

    fun toggleLayout() {
        val next = if (uiState.value.layout == LibraryLayout.Grid) LibraryLayout.List else LibraryLayout.Grid
        viewModelScope.launch { settings.setLibraryLayout(next) }
    }

    fun refresh() = sync.requestSync(refresh = true)

    fun playSong(index: Int) {
        player.play(uiState.value.songs, index, PlaybackContext(PlaybackContextType.Songs, "", ""))
    }

    fun shuffleSongs() {
        val songs = uiState.value.songs
        if (songs.isNotEmpty()) {
            player.play(songs, 0, PlaybackContext(PlaybackContextType.Songs, "", ""), shuffle = true)
        }
    }

    fun addToQueue(track: Track) = player.addToQueue(track)

    fun toggleLike(track: Track) {
        viewModelScope.launch { library.setLiked(track.id, track.id !in uiState.value.likedIds) }
    }

    private fun sortAlbums(albums: List<Album>, sort: LibrarySort) = when (sort) {
        LibrarySort.RecentlyAdded -> albums.sortedByDescending { it.added }

        LibrarySort.Alphabetical -> albums.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

        LibrarySort.Artist -> albums.sortedWith(
            compareBy<Album, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }.thenBy { it.year },
        )
    }

    private fun sortArtists(artists: List<Artist>, sort: LibrarySort) = when (sort) {
        LibrarySort.RecentlyAdded -> artists.sortedByDescending { a -> a.tracks.maxOfOrNull { it.added } ?: 0 }
        else -> artists.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    private fun sortSongs(tracks: List<Track>, sort: LibrarySort) = when (sort) {
        LibrarySort.RecentlyAdded -> tracks.sortedByDescending { it.added }

        LibrarySort.Alphabetical -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayTitle })

        LibrarySort.Artist -> tracks.sortedWith(
            compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.albumArtist }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.album }
                .thenBy { it.trackNumber },
        )
    }

    private fun playlistItems(
        snapshot: LibrarySnapshot?,
        playlists: List<Playlist>,
        liked: List<String>,
    ): List<PlaylistItem> {
        val likedTracks = snapshot?.tracks(liked).orEmpty()
        val likedItem = PlaylistItem(null, "", likedTracks.size, likedTracks.firstOrNull { it.hasCover }?.id)
        // The server lists its liked-songs playlist too; the app shows likes from /api/v1/likes instead.
        val others = playlists.filterNot { it.liked }.map { p ->
            val tracks = snapshot?.tracks(p.trackIds).orEmpty()
            PlaylistItem(p, p.title, p.trackIds.size, tracks.firstOrNull { it.hasCover }?.id)
        }
        return (listOf(likedItem) + others).sortedByDescending { it.playlist?.favorite == true }
    }

    private companion object {
        const val KEY_TAB = "tab"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
