package com.henriquesebastiao.downtify.feature.collection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.offline.OfflineRepository
import com.henriquesebastiao.downtify.core.data.playlists.PlaylistEditError
import com.henriquesebastiao.downtify.core.data.playlists.PlaylistEditorRepository
import com.henriquesebastiao.downtify.core.data.playlists.PlaylistWriteResult
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.Playlist
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.model.moved
import com.henriquesebastiao.downtify.core.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CollectionKind { Album, Playlist, Liked }

data class CollectionUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val kind: CollectionKind = CollectionKind.Album,
    val title: String = "",
    val artist: String = "",
    val artistId: String = "",
    val year: String = "",
    val tracks: List<Track> = emptyList(),
    val coverTrackId: String? = null,
    /** For a playlist with its own cover on the server. */
    val playlist: Playlist? = null,
    val currentTrackId: String? = null,
    val isPlaying: Boolean = false,
    /** This album/playlist is what's loaded in the player. */
    val isCurrentContext: Boolean = false,
    val likedIds: Set<String> = emptySet(),
    /** Everything in the library: what the "Add songs" sheet offers. */
    val librarySongs: List<Track> = emptyList(),
    val offline: CollectionOffline = CollectionOffline(),
    val favoriteBusy: Boolean = false,
    val favoriteError: Boolean = false,
    /** Its songs may be changed here: the caller's own manual playlist, or one they edit. */
    val canEditTracks: Boolean = false,
    /** Only its owner may rename it (an editor never does). */
    val canRename: Boolean = false,
    /** Edit mode: drag to reorder, swipe to remove. */
    val editing: Boolean = false,
    val renameOpen: Boolean = false,
    val addSongsOpen: Boolean = false,
    /** A write is on its way: the rows hold still until it lands. */
    val saving: Boolean = false,
    val editError: PlaylistEditError? = null,
    /**
     * Counts rolled-back edits: the rows are keyed by position, id and this, so a swipe that the
     * server refused comes back as a row that isn't still dismissed.
     */
    val revision: Int = 0,
) {
    val durationSeconds: Double get() = tracks.sumOf { it.duration }
}

/** Whether this collection is kept on the phone, and how far its download got. */
data class CollectionOffline(
    val kept: Boolean = false,
    val downloaded: Int = 0,
    val total: Int = 0,
    /** Tracks of this collection with a copy on the phone (kept here or by another collection). */
    val downloadedIds: Set<String> = emptySet(),
) {
    val isComplete: Boolean get() = kept && total > 0 && downloaded == total
}

/** The editing the user did to a playlist, before (and until) the server answers. */
data class CollectionEdit(
    /** The ids in the order the user dropped them; null while the server's order stands. */
    val order: List<String>? = null,
    val editing: Boolean = false,
    val renameOpen: Boolean = false,
    val addSongsOpen: Boolean = false,
    val saving: Boolean = false,
    val error: PlaylistEditError? = null,
    val revision: Int = 0,
    /** A just-renamed playlist: the route still says the old name. */
    val renamedTo: String? = null,
)

/** An album, a playlist or Liked songs: a header and its tracks. */
@HiltViewModel
class CollectionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val library: LibraryRepository,
    private val player: PlayerController,
    private val offline: OfflineRepository,
    private val playlists: PlaylistEditorRepository,
) : ViewModel() {
    private val albumId: String? = savedState["albumId"]
    private val playlistName: String? = savedState["name"]
    private val kind = when {
        albumId != null -> CollectionKind.Album
        playlistName != null -> CollectionKind.Playlist
        else -> CollectionKind.Liked
    }

    private val contextType = when (kind) {
        CollectionKind.Album -> PlaybackContextType.Album
        CollectionKind.Playlist -> PlaybackContextType.Playlist
        CollectionKind.Liked -> PlaybackContextType.Liked
    }

    private val refId: String = albumId ?: playlistName.orEmpty()
    private val mutableFavoriteBusy = MutableStateFlow(false)
    val favoriteBusy = mutableFavoriteBusy.asStateFlow()
    private val mutableFavoriteError = MutableStateFlow(false)
    val favoriteError = mutableFavoriteError.asStateFlow()

    private val edit = MutableStateFlow(CollectionEdit())

    /** The reorder waiting for the drag to settle, so a drag sends one order and not one per row. */
    private var reorderJob: Job? = null

    /** The server's playlists (with `manual`/`can_edit_tracks`) over the library's own copy. */
    private val sources = combine(
        library.playlists,
        playlists.playlists,
        playlists.editingSupported,
    ) { local, server, supported -> PlaylistSources(local, server, supported) }

    private val base = combine(
        library.library,
        sources,
        library.likedIds,
        player.nowPlaying,
    ) { snapshot, lists, liked, playing ->
        if (snapshot == null) return@combine CollectionUiState(kind = kind)
        val initial = CollectionUiState(
            loading = false,
            kind = kind,
            currentTrackId = playing.trackId,
            isPlaying = playing.isPlaying,
            likedIds = liked.toSet(),
            librarySongs = snapshot.tracks,
        )
        val found = when (kind) {
            CollectionKind.Album -> snapshot.albumsById[albumId]?.let { album ->
                initial.copy(
                    title = album.title,
                    artist = album.artist,
                    artistId = album.artistId,
                    year = album.year,
                    tracks = album.tracks,
                    coverTrackId = album.coverTrackId,
                )
            }

            CollectionKind.Playlist -> playlistOf(lists)?.let { p ->
                val tracks = snapshot.tracks(p.trackIds)
                initial.copy(
                    title = p.title,
                    tracks = tracks,
                    playlist = p,
                    coverTrackId = tracks.firstOrNull { it.hasCover }?.id,
                    canEditTracks = p.isEditable(lists.supported),
                    canRename = p.isRenamable(lists.supported),
                )
            }

            CollectionKind.Liked -> {
                val tracks = snapshot.tracks(liked)
                initial.copy(tracks = tracks, coverTrackId = tracks.firstOrNull { it.hasCover }?.id)
            }
        } ?: initial.copy(notFound = true)
        found.copy(isCurrentContext = playing.context == context(found.title))
    }

    val uiState: StateFlow<CollectionUiState> = combine(
        base,
        offline.state,
        edit,
    ) { state, offlineState, editState ->
        val kept = offlineState?.kept(contextType, refId)
        val tracks = editState.order?.let { ordered -> reorder(state.tracks, ordered) } ?: state.tracks
        val downloaded = offlineState?.files?.keys
        state.copy(
            tracks = tracks,
            editing = editState.editing,
            renameOpen = editState.renameOpen,
            addSongsOpen = editState.addSongsOpen,
            saving = editState.saving,
            editError = editState.error,
            revision = editState.revision,
            title = editState.renamedTo ?: state.title,
            offline = CollectionOffline(
                kept = kept != null,
                downloaded = kept?.progress?.downloaded ?: 0,
                total = kept?.progress?.total ?: 0,
                downloadedIds = downloaded?.let { ids -> tracks.map { it.id }.filterTo(mutableSetOf()) { it in ids } }
                    ?: emptySet(),
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CollectionUiState(kind = kind))

    /** The playlist on screen: the server's entry (it knows what may be edited), else the library's. */
    private fun playlistOf(sources: PlaylistSources): Playlist? {
        val name = edit.value.renamedTo ?: playlistName
        return sources.server.firstOrNull { it.name == name }
            ?: sources.local.firstOrNull { it.name == name }
    }

    /** [tracks] in [ids] order: one row per position, so a repeated song keeps its copies apart. */
    private fun reorder(tracks: List<Track>, ids: List<String>): List<Track> {
        val byId = tracks.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    fun play(index: Int, shuffle: Boolean = false) {
        val state = uiState.value
        player.play(state.tracks, index, context(state.title), shuffle)
    }

    /** The big play button: pause/resume when this is what's playing, else play from the top. */
    fun playOrPause() {
        if (uiState.value.isCurrentContext) player.togglePlayPause() else play(0)
    }

    fun shuffle() = play(0, shuffle = true)

    fun addToQueue(track: Track) = player.addToQueue(track)

    fun toggleLike(track: Track) {
        viewModelScope.launch { library.setLiked(track.id, track.id !in uiState.value.likedIds) }
    }

    fun toggleFavorite() {
        val playlist = uiState.value.playlist ?: return
        if (!playlist.favoriteSupported || mutableFavoriteBusy.value) return
        mutableFavoriteBusy.value = true
        mutableFavoriteError.value = false
        viewModelScope.launch {
            try {
                mutableFavoriteError.value = !library.setPlaylistFavorite(playlist.name, !playlist.favorite)
            } finally {
                mutableFavoriteBusy.value = false
            }
        }
    }

    /** Keeps this collection on the phone, or removes its copies. */
    fun toggleOffline() {
        val keep = !uiState.value.offline.kept
        viewModelScope.launch { offline.setKept(contextType, refId, keep) }
    }

    // Editing the playlist's songs

    fun toggleEditing() {
        if (!uiState.value.canEditTracks) return
        edit.update { it.copy(editing = !it.editing, order = null, error = null) }
    }

    fun openRename() = edit.update { it.copy(renameOpen = true) }

    fun dismissRename() = edit.update { it.copy(renameOpen = false) }

    fun openAddSongs() = edit.update { it.copy(addSongsOpen = true) }

    fun dismissAddSongs() = edit.update { it.copy(addSongsOpen = false) }

    fun dismissError() = edit.update { it.copy(error = null) }

    /** `POST /api/v1/playlists/rename`: the owner keeps the new name even though the route doesn't. */
    fun rename(newName: String) {
        val current = uiState.value.playlist ?: return
        edit.update { it.copy(renameOpen = false, saving = true, error = null) }
        viewModelScope.launch {
            when (val outcome = playlists.rename(current.name, newName)) {
                is PlaylistWriteResult.Ok -> edit.update {
                    it.copy(saving = false, renamedTo = outcome.result.name.ifBlank { newName.trim() })
                }

                is PlaylistWriteResult.Failed -> edit.update { it.copy(saving = false, error = outcome.error) }
            }
        }
    }

    /** Puts songs picked from the library into this playlist; a manual one may repeat them. */
    fun addSongs(trackIds: List<String>) {
        val current = uiState.value.playlist ?: return
        if (trackIds.isEmpty()) return
        edit.update { it.copy(addSongsOpen = false, saving = true, error = null) }
        viewModelScope.launch {
            val outcome = playlists.addTracks(current.name, trackIds)
            edit.update { state ->
                when (outcome) {
                    is PlaylistWriteResult.Ok -> state.copy(saving = false)
                    is PlaylistWriteResult.Failed -> state.copy(saving = false, error = outcome.error)
                }
            }
        }
    }

    /** Takes out one row: the [occurrence]th copy of the song, as the playlist lists them now. */
    fun removeAt(index: Int) {
        val state = uiState.value
        val current = state.playlist ?: return
        val track = state.tracks.getOrNull(index) ?: return
        if (!state.canEditTracks || edit.value.saving) return
        val occurrence = state.tracks.take(index).count { it.id == track.id }
        val order = state.tracks.map { it.id }.toMutableList().also { it.removeAt(index) }
        edit.update { it.copy(order = order, saving = true, error = null) }
        viewModelScope.launch {
            val outcome = playlists.removeEntry(current.name, track.id, occurrence)
            edit.update { it.rolledBack(outcome) }
        }
    }

    /**
     * Drags the row at [from] to [to]: the order shows at once, and `PUT /api/v1/playlists/order`
     * goes out once the drag settles — a swap per row crossed would be a request per row.
     */
    fun move(from: Int, to: Int) {
        val state = uiState.value
        val current = state.playlist ?: return
        if (!state.canEditTracks) return
        val ids = state.tracks.map { it.id }.moved(from, to)
        if (ids == state.tracks.map { it.id }) return
        edit.update { it.copy(order = ids, error = null) }
        reorderJob?.cancel()
        reorderJob = viewModelScope.launch {
            delay(REORDER_SETTLE_MS)
            edit.update { it.copy(saving = true) }
            val outcome = playlists.reorder(current.name, ids)
            edit.update { it.rolledBack(outcome) }
        }
    }

    /** A refused edit puts the server's order back and rebuilds the rows. */
    private fun CollectionEdit.rolledBack(outcome: PlaylistWriteResult): CollectionEdit = when (outcome) {
        is PlaylistWriteResult.Ok -> copy(order = null, saving = false)

        is PlaylistWriteResult.Failed -> copy(
            order = null,
            saving = false,
            error = outcome.error,
            revision = revision + 1,
        )
    }

    private fun context(title: String) = when (kind) {
        CollectionKind.Album -> PlaybackContext(PlaybackContextType.Album, albumId.orEmpty(), title)
        CollectionKind.Playlist -> PlaybackContext(PlaybackContextType.Playlist, playlistName.orEmpty(), title)
        CollectionKind.Liked -> PlaybackContext(PlaybackContextType.Liked, "", title)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** How long after the last swap the order goes to the server. */
        const val REORDER_SETTLE_MS = 350L
    }
}

/** The playlists this page can draw: the server's (fresher, with what may be edited) and the library's. */
private data class PlaylistSources(val local: List<Playlist>, val server: List<Playlist>, val supported: Boolean)
