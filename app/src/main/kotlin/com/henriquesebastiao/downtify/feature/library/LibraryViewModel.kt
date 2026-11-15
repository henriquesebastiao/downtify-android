package com.henriquesebastiao.downtify.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.library.LibrarySnapshot
import com.henriquesebastiao.downtify.core.data.library.SyncStatus
import com.henriquesebastiao.downtify.core.data.playlists.PlaylistEditError
import com.henriquesebastiao.downtify.core.data.playlists.PlaylistEditorRepository
import com.henriquesebastiao.downtify.core.data.playlists.PlaylistWriteResult
import com.henriquesebastiao.downtify.core.data.settings.LibraryLayout
import com.henriquesebastiao.downtify.core.data.settings.LibrarySort
import com.henriquesebastiao.downtify.core.data.settings.SettingsRepository
import com.henriquesebastiao.downtify.core.data.sync.SyncCoordinator
import com.henriquesebastiao.downtify.core.model.Album
import com.henriquesebastiao.downtify.core.model.Artist
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.Playlist
import com.henriquesebastiao.downtify.core.model.Selection
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.model.addablePlaylists
import com.henriquesebastiao.downtify.core.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    /** What's picked for a bulk action, in the order it was picked. */
    val selection: Selection = Selection(),
    /** The paired server has the playlist write routes (older ones stay read-only). */
    val canEditPlaylists: Boolean = false,
    /** The playlists a song may be added to: manual ones the user may edit. */
    val editablePlaylists: List<Playlist> = emptyList(),
    /** The "Add to playlist" sheet is open, for the selection or for one song. */
    val addSheetOpen: Boolean = false,
    /** The name dialog is open: a new playlist, of [namingIds] songs when that isn't empty. */
    val naming: Boolean = false,
    val namingIds: List<String> = emptyList(),
    val busy: Boolean = false,
    val editError: PlaylistEditError? = null,
    /** A playlist just created: the screen opens it, then says so through `onPlaylistOpened`. */
    val openPlaylist: String? = null,
) {
    /** Whether the tab has anything for Shuffle to play. */
    val canShuffle: Boolean get() = shuffleCandidates(this).isNotEmpty()

    /** Whether rows show their check and taps pick instead of play. */
    val selecting: Boolean get() = selection.count > 0
}

/** What Shuffle plays: [tracks], shuffled, as [context]. */
data class ShufflePick(val tracks: List<Track>, val context: PlaybackContext)

/** The playlist-editing side of this screen: what's picked, what's open, what the server answered. */
data class LibraryEditState(
    val selection: Selection = Selection(),
    val sheetOpen: Boolean = false,
    /** The one song the sheet is for; null while it is for the selection. */
    val sheetTrack: Track? = null,
    val naming: Boolean = false,
    /** Songs to put into the playlist being named; empty for a plain "New playlist". */
    val namingIds: List<String> = emptyList(),
    val busy: Boolean = false,
    val error: PlaylistEditError? = null,
    val openPlaylist: String? = null,
)

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
    private val playlists: PlaylistEditorRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val tab = savedState.getStateFlow(KEY_TAB, LibraryTab.Songs.name)

    private val edit = MutableStateFlow(LibraryEditState())

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

    /** What the server lists (with `manual` and `can_edit_tracks`) and whether it can edit at all. */
    private val serverPlaylists = combine(
        playlists.playlists,
        playlists.editingSupported,
    ) { listed, editing -> listed to editing }

    private val content = combine(
        sorted,
        library.playlists,
        library.likedIds,
        settings.settings.map { it.libraryLayout }.distinctUntilChanged(),
        serverPlaylists,
    ) { (snapshot, lists), playlists, liked, layout, (serverList, editing) ->
        lists.copy(
            layout = layout,
            playlists = playlistItems(snapshot, playlists, liked),
            likedIds = liked.toSet(),
            canEditPlaylists = editing,
            editablePlaylists = addablePlaylists(serverList),
        )
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<LibraryUiState> = combine(
        content,
        tab,
        library.syncStatus,
        player.nowPlaying,
        edit,
    ) { content, tabName, syncStatus, playing, editState ->
        val songs = content.songs
        content.copy(
            tab = LibraryTab.entries.firstOrNull { it.name == tabName } ?: LibraryTab.Songs,
            refreshing = syncStatus == SyncStatus.Syncing,
            currentTrackId = playing.trackId,
            isPlaying = playing.isPlaying,
            // A song that left the library (a sync, a hidden one) leaves the selection too.
            selection = editState.selection.retain(songs.map { it.id }.toSet()),
            addSheetOpen = editState.sheetOpen,
            naming = editState.naming,
            namingIds = editState.namingIds,
            busy = editState.busy,
            editError = editState.error,
            openPlaylist = editState.openPlaylist,
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

    // Selection

    /** A press-and-hold on a song starts a selection with it. */
    fun startSelection(track: Track) = edit.update { it.copy(selection = it.selection.select(track.id)) }

    fun toggleSelection(track: Track) = edit.update { it.copy(selection = it.selection.toggle(track.id)) }

    fun clearSelection() = edit.update { it.copy(selection = it.selection.clear()) }

    fun toggleSelectAll() {
        val songs = uiState.value.songs.map { it.id }
        edit.update { state ->
            val selection = state.selection
            state.copy(
                selection = if (selection.covers(songs)) selection.clear() else selection.selectAll(songs),
            )
        }
    }

    // Adding to a playlist

    fun openAddSheet() = edit.update { it.copy(sheetOpen = true, sheetTrack = null) }

    fun openAddSheetFor(track: Track) = edit.update { it.copy(sheetOpen = true, sheetTrack = track) }

    fun dismissAddSheet() = edit.update { it.copy(sheetOpen = false, sheetTrack = null) }

    /** The tab's "New playlist", or the sheet's: the second keeps the songs it was opened for. */
    fun startNewPlaylist() = edit.update { it.copy(sheetOpen = false, sheetTrack = null, naming = true) }

    fun dismissNaming() = edit.update { it.copy(naming = false, namingIds = emptyList()) }

    fun dismissError() = edit.update { it.copy(error = null) }

    fun playlistOpened() = edit.update { it.copy(openPlaylist = null) }

    /** The songs the sheet is for: its own song, else the selection. */
    private fun sheetIds(state: LibraryEditState): List<String> =
        listOfNotNull(state.sheetTrack?.id).ifEmpty { state.selection.ids }

    /** Puts the sheet's songs into [name]. */
    fun addToPlaylist(name: String) {
        val wanted = sheetIds(edit.value)
        if (wanted.isEmpty()) return
        edit.update { it.copy(sheetOpen = false, sheetTrack = null, busy = true, error = null) }
        viewModelScope.launch {
            val outcome = playlists.addTracks(name, wanted)
            when (outcome) {
                is PlaylistWriteResult.Ok ->
                    edit.update { it.copy(busy = false, selection = it.selection.clear()) }

                is PlaylistWriteResult.Failed -> edit.update { it.copy(busy = false, error = outcome.error) }
            }
        }
    }

    /** Creates [name] and opens it; from the sheet it also gets the songs the sheet was for. */
    fun createPlaylist(name: String) {
        val songs = edit.value.namingIds
        edit.update { it.copy(naming = false, namingIds = emptyList(), busy = true, error = null) }
        viewModelScope.launch {
            val outcome = if (songs.isEmpty()) playlists.create(name) else playlists.createWithTracks(name, songs)
            when (outcome) {
                is PlaylistWriteResult.Ok -> edit.update {
                    it.copy(
                        busy = false,
                        selection = it.selection.clear(),
                        openPlaylist = outcome.result.name.ifBlank { name.trim() },
                    )
                }

                is PlaylistWriteResult.Failed -> edit.update { it.copy(busy = false, error = outcome.error) }
            }
        }
    }

    /** The sheet's "New playlist" row: it names a playlist that will take the sheet's songs. */
    fun startNewPlaylistForSheet() {
        val songs = sheetIds(edit.value)
        edit.update { it.copy(sheetOpen = false, sheetTrack = null, naming = true, namingIds = songs) }
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
