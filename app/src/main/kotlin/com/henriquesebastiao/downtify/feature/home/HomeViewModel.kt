package com.henriquesebastiao.downtify.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.library.RecentsRepository
import com.henriquesebastiao.downtify.core.data.library.SyncStatus
import com.henriquesebastiao.downtify.core.data.session.ConnectionState
import com.henriquesebastiao.downtify.core.data.session.ServerRepository
import com.henriquesebastiao.downtify.core.data.sync.SyncCoordinator
import com.henriquesebastiao.downtify.core.model.Album
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.Playlist
import com.henriquesebastiao.downtify.core.model.RecentContext
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class ServerStatus { Streaming, Syncing, Unreachable, DifferentServer }

data class HomeUiState(
    val loading: Boolean = true,
    val serverName: String = "",
    val status: ServerStatus = ServerStatus.Streaming,
    /** "Jump back in": recent contexts, or — before anything was played — Liked songs and the newest albums. */
    val jumpBackIn: List<RecentContext> = emptyList(),
    /** The library's tracks behind the loose songs in [jumpBackIn], by id: their tiles' artist. */
    val recentSongs: Map<String, Track> = emptyMap(),
    val recentlyAdded: List<Album> = emptyList(),
    val favoritePlaylists: List<Playlist> = emptyList(),
    /** Every playlist by name, for the covers of the ones in [jumpBackIn]: favourite or not. */
    val playlistsByName: Map<String, Playlist> = emptyMap(),
    val refreshing: Boolean = false,
    val libraryEmpty: Boolean = false,
    /** What the server can do (`capabilities`): the shortcuts below "Recently added". */
    val showDiscover: Boolean = false,
    val showPodcasts: Boolean = false,
) {
    /**
     * Every playlist Home knows, by name: what a tile looks its playlist up in, so a recent
     * playlist shows its own cover, not its first song's, whether it is a favourite or not.
     */
    val knownPlaylists: Map<String, Playlist>
        get() = favoritePlaylists.associateBy { it.name } + playlistsByName
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val library: LibraryRepository,
    recents: RecentsRepository,
    server: ServerRepository,
    private val sync: SyncCoordinator,
    private val player: PlayerController,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        combine(library.library, library.likedIds, library.playlists, ::Triple),
        recents.recents(RECENT_CONTEXT_LIMIT),
        library.syncStatus,
        combine(server.connection, server.session, server.serverInfo, ::Triple),
    ) { (snapshot, likedIds, playlists), recentList, syncStatus, (connection, session, info) ->
        val newest = snapshot?.albums.orEmpty().sortedByDescending { it.added }
        // A song gone from the library has nothing left to play.
        val recent = recentList.filter {
            it.type != PlaybackContextType.Songs || snapshot?.byId?.containsKey(it.refId) != false
        }
        val jump = recent.ifEmpty {
            buildList {
                if (likedIds.isNotEmpty()) add(RecentContext(PlaybackContextType.Liked, "", "", null, 0))
                newest.take(JUMP_BACK_IN_SIZE - size).forEach {
                    add(RecentContext(PlaybackContextType.Album, it.id, it.title, it.coverTrackId, 0))
                }
            }
        }
        HomeUiState(
            loading = snapshot == null,
            serverName = session?.serverName.orEmpty().ifBlank { session?.baseUrl?.substringAfter("://").orEmpty() },
            status = when {
                connection == ConnectionState.DifferentServer -> ServerStatus.DifferentServer
                syncStatus == SyncStatus.Syncing -> ServerStatus.Syncing
                connection == ConnectionState.Unreachable || syncStatus is SyncStatus.Failed -> ServerStatus.Unreachable
                else -> ServerStatus.Streaming
            },
            jumpBackIn = jump.take(RECENT_CONTEXT_LIMIT),
            recentSongs = jump.filter { it.type == PlaybackContextType.Songs }
                .mapNotNull { snapshot?.byId?.get(it.refId) }
                .associateBy { it.id },
            recentlyAdded = newest.take(RECENTLY_ADDED_SIZE),
            favoritePlaylists = playlists.filter { it.favorite && !it.liked }.sortedBy { it.title.lowercase() },
            playlistsByName = playlists.associateBy { it.name },
            refreshing = syncStatus == SyncStatus.Syncing,
            libraryEmpty = snapshot?.isEmpty == true,
            showDiscover = info?.capabilities?.discover == true,
            showPodcasts = info?.capabilities?.podcasts == true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    /**
     * A loose song's tile ([PlaybackContextType.Songs]) opens the song's album through [openAlbum].
     * A song with no album in the library (untagged, so no album id) has no page to open: it plays
     * at once instead.
     */
    fun openRecentSong(entry: RecentContext, openAlbum: (String) -> Unit) {
        val snapshot = library.library.value ?: return
        val track = snapshot.byId[entry.refId] ?: return
        if (track.albumId in snapshot.albumsById) {
            openAlbum(track.albumId)
        } else {
            player.play(listOf(track), 0, PlaybackContext(PlaybackContextType.Songs, "", ""))
        }
    }

    /** Pull to refresh: the server rescans its folder, then the app syncs. */
    fun refresh() = sync.requestSync(refresh = true)

    private companion object {
        const val JUMP_BACK_IN_SIZE = 6
        const val RECENT_CONTEXT_LIMIT = 12
        const val RECENTLY_ADDED_SIZE = 12
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
