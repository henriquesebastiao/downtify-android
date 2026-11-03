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
import kotlin.random.Random
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

/** A playlist row: [playlist] null is "Liked songs". [tracks] are the ones in the library, in order. */
data class PlaylistItem(
    val playlist: Playlist?,
    val title: String,
    val count: Int,
    val coverTrackId: String?,
    val tracks: List<Track> = emptyList(),
)

data class LibraryUiState(
    val loading: Boolean = true,
    val tab: LibraryTab = LibraryTab.Songs,
    val layout: LibraryLayout = LibraryLayout.Grid,
    val sort: LibrarySort = LibrarySort.RecentlyAdded,
    val sortDescending: Boolean = sort.descendingByDefault,
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val songs: List<Track> = emptyList(),
    val refreshing: Boolean = false,
    val currentTrackId: String? = null,
    val isPlaying: Boolean = false,
    val likedIds: Set<String> = emptySet(),
) {
    /** Whether the tab has anything for Shuffle to play. */
    val canShuffle: Boolean get() = shuffleCandidates(this).isNotEmpty()
}

/** What Shuffle plays: [tracks], shuffled, as [context]. */
data class ShufflePick(val tracks: List<Track>, val context: PlaybackContext)

/**
 * Shuffle on each tab, as in PixelPlayer: on Songs every song; on the others one album, artist or
 * playlist (Liked songs among them) picked at random, its songs shuffled. The context is the one
 * its own page plays with, so that page marks what's playing.
 */
fun shufflePick(state: LibraryUiState, random: Random = Random): ShufflePick? =
    shuffleCandidates(state).randomOrNull(random)

private fun shuffleCandidates(state: LibraryUiState): List<ShufflePick> = when (state.tab) {
    LibraryTab.Songs -> listOf(ShufflePick(state.songs, PlaybackContext(PlaybackContextType.Songs, "", "")))

    LibraryTab.Albums -> state.albums.map {
        ShufflePick(it.tracks, PlaybackContext(PlaybackContextType.Album, it.id, it.title))
    }

    LibraryTab.Artists -> state.artists.map {
        ShufflePick(it.tracks, PlaybackContext(PlaybackContextType.Artist, it.id, it.name))
    }

    LibraryTab.Playlists -> state.playlists.map { item ->
        val context = item.playlist?.let { PlaybackContext(PlaybackContextType.Playlist, it.name, it.title) }
            ?: PlaybackContext(PlaybackContextType.Liked, "", "")
        ShufflePick(item.tracks, context)
    }
}.filter { it.tracks.isNotEmpty() }

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
        settings.settings.map { it.librarySort to it.librarySortDescending }.distinctUntilChanged(),
    ) { snapshot, (sort, descending) ->
        snapshot to LibraryUiState(
            loading = snapshot == null,
            sort = sort,
            sortDescending = descending,
            albums = snapshot?.albums.orEmpty().sortedWith(albumOrder(sort, descending)),
            artists = snapshot?.artists.orEmpty().sortedWith(artistOrder(sort, descending)),
            songs = snapshot?.tracks.orEmpty().sortedWith(songOrder(sort, descending)),
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

    fun setSortDescending(value: Boolean) {
        viewModelScope.launch { settings.setLibrarySortDescending(value) }
    }

    fun toggleLayout() {
        val next = if (uiState.value.layout == LibraryLayout.Grid) LibraryLayout.List else LibraryLayout.Grid
        viewModelScope.launch { settings.setLibraryLayout(next) }
    }

    fun refresh() = sync.requestSync(refresh = true)

    fun playSong(index: Int) {
        player.play(uiState.value.songs, index, PlaybackContext(PlaybackContextType.Songs, "", ""))
    }

    fun shuffle() {
        val pick = shufflePick(uiState.value) ?: return
        player.play(pick.tracks, 0, pick.context, shuffle = true)
    }

    fun addToQueue(track: Track) = player.addToQueue(track)

    fun toggleLike(track: Track) {
        viewModelScope.launch { library.setLiked(track.id, track.id !in uiState.value.likedIds) }
    }

    // [descending] reverses the first key only: an artist's albums stay by year, an album's songs by number.
    private fun albumOrder(sort: LibrarySort, descending: Boolean): Comparator<Album> = when (sort) {
        LibrarySort.RecentlyAdded -> compareBy<Album> { it.added }.directed(descending)

        LibrarySort.Alphabetical ->
            compareBy<Album, String>(String.CASE_INSENSITIVE_ORDER) { it.title }.directed(descending)

        LibrarySort.Artist -> compareBy<Album, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }
            .directed(descending)
            .thenBy { it.year }
    }

    private fun artistOrder(sort: LibrarySort, descending: Boolean): Comparator<Artist> = when (sort) {
        LibrarySort.RecentlyAdded -> compareBy<Artist> { a -> a.tracks.maxOfOrNull { it.added } ?: 0 }
        else -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }.directed(descending)

    private fun songOrder(sort: LibrarySort, descending: Boolean): Comparator<Track> = when (sort) {
        LibrarySort.RecentlyAdded -> compareBy<Track> { it.added }.directed(descending)

        LibrarySort.Alphabetical ->
            compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.displayTitle }.directed(descending)

        LibrarySort.Artist -> compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.albumArtist }
            .directed(descending)
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.album }
            .thenBy { it.trackNumber }
    }

    private fun <T> Comparator<T>.directed(descending: Boolean): Comparator<T> = if (descending) reversed() else this

    private fun playlistItems(
        snapshot: LibrarySnapshot?,
        playlists: List<Playlist>,
        liked: List<String>,
    ): List<PlaylistItem> {
        val likedTracks = snapshot?.tracks(liked).orEmpty()
        val likedItem =
            PlaylistItem(null, "", likedTracks.size, likedTracks.firstOrNull { it.hasCover }?.id, likedTracks)
        // The server lists its liked-songs playlist too; the app shows likes from /api/v1/likes instead.
        val others = playlists.filterNot { it.liked }.map { p ->
            val tracks = snapshot?.tracks(p.trackIds).orEmpty()
            PlaylistItem(p, p.title, p.trackIds.size, tracks.firstOrNull { it.hasCover }?.id, tracks)
        }
        return (listOf(likedItem) + others).sortedByDescending { it.playlist?.favorite == true }
    }

    private companion object {
        const val KEY_TAB = "tab"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
