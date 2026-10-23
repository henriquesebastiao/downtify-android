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
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.Playlist
import com.henriquesebastiao.downtify.core.model.RecentContext
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
    val recentlyAdded: List<Album> = emptyList(),
    val favoritePlaylists: List<Playlist> = emptyList(),
    val refreshing: Boolean = false,
    val libraryEmpty: Boolean = false,
    /** What the server can do (`capabilities`): the shortcuts below "Recently added". */
    val showDiscover: Boolean = false,
    val showPodcasts: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    library: LibraryRepository,
    recents: RecentsRepository,
    server: ServerRepository,
    private val sync: SyncCoordinator,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        combine(library.library, library.likedIds, library.playlists, ::Triple),
        recents.recents(RECENT_CONTEXT_LIMIT),
        library.syncStatus,
        combine(server.connection, server.session, server.serverInfo, ::Triple),
    ) { (snapshot, likedIds, playlists), recentList, syncStatus, (connection, session, info) ->
        val newest = snapshot?.albums.orEmpty().sortedByDescending { it.added }
        val jump = recentList.ifEmpty {
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
            recentlyAdded = newest.take(RECENTLY_ADDED_SIZE),
            favoritePlaylists = playlists.filter { it.favorite && !it.liked }.sortedBy { it.title.lowercase() },
            refreshing = syncStatus == SyncStatus.Syncing,
            libraryEmpty = snapshot?.isEmpty == true,
            showDiscover = info?.capabilities?.discover == true,
            showPodcasts = info?.capabilities?.podcasts == true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    /** Pull to refresh: the server rescans its folder, then the app syncs. */
    fun refresh() = sync.requestSync(refresh = true)

    private companion object {
        const val JUMP_BACK_IN_SIZE = 6
        const val RECENT_CONTEXT_LIMIT = 12
        const val RECENTLY_ADDED_SIZE = 12
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
