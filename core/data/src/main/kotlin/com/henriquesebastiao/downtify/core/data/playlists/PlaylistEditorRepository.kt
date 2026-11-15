package com.henriquesebastiao.downtify.core.data.playlists

import android.util.Log
import com.henriquesebastiao.downtify.core.data.di.ApplicationScope
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.session.ServerRepository
import com.henriquesebastiao.downtify.core.model.Playlist
import com.henriquesebastiao.downtify.core.model.PlaylistEditResult
import com.henriquesebastiao.downtify.core.model.playlistTrackIds
import com.henriquesebastiao.downtify.core.network.ApiFactory
import com.henriquesebastiao.downtify.core.network.DowntifyApi
import com.henriquesebastiao.downtify.core.network.dto.CreatePlaylistRequest
import com.henriquesebastiao.downtify.core.network.dto.PlaylistOrderRequest
import com.henriquesebastiao.downtify.core.network.dto.PlaylistTracksRequest
import com.henriquesebastiao.downtify.core.network.dto.PlaylistWriteDto
import com.henriquesebastiao.downtify.core.network.dto.RenamePlaylistRequest
import com.henriquesebastiao.downtify.core.network.dto.TrackEntryDto
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

/** Why a playlist write didn't happen, as the UI needs to say it. */
sealed interface PlaylistEditError {
    /** The paired server has no playlist write routes (`capabilities.playlist_editing` is false). */
    data object NotSupported : PlaylistEditError

    /** `403`: not the playlist's owner or an editor, or an imported playlist. */
    data object Forbidden : PlaylistEditError

    /** `400`: over the 10,000 references a write may carry. */
    data object TooManyTracks : PlaylistEditError

    /** `503`: the server's library sync index isn't ready yet; try again in a moment. */
    data object IndexNotReady : PlaylistEditError

    /** `404`: the playlist or a track is gone (or hidden); reload the library. */
    data object NotFound : PlaylistEditError

    /** `409` on a reorder, twice: the playlist changed under us. Reload and try by hand. */
    data object Stale : PlaylistEditError

    /** The name is empty: worth neither a request nor a message. */
    data object InvalidName : PlaylistEditError

    /** Nothing to send: the pick held no library track (an episode, or nothing at all). */
    data object NoTracks : PlaylistEditError

    /** The server can't be reached. */
    data object Offline : PlaylistEditError

    /** An answer the app couldn't use, [status] when the server gave one. */
    data class Failed(val status: Int?) : PlaylistEditError
}

/** What a write came to: the playlist as the server lists it now, or why not. */
sealed interface PlaylistWriteResult {
    data class Ok(val result: PlaylistEditResult) : PlaylistWriteResult

    data class Failed(val error: PlaylistEditError) : PlaylistWriteResult
}

/**
 * Creating, renaming and editing the songs of manual playlists, and the playlists as the server
 * lists them (with `manual` and `can_edit_tracks`, which the app's own copy in the database doesn't
 * carry).
 *
 * The contract's four write routes answer the playlist as the caller sees it now; a refused write
 * says why with its status: `409` is a stale order (reload and retry once), `403` a permission
 * (a message, never a crash), `400` the 10,000-reference limit, `503` a sync index still warming up.
 * Every successful write refetches `GET /api/v1/playlists` here and in [LibraryRepository], and
 * [com.henriquesebastiao.downtify.core.network.live.LiveEvent.PlaylistsChanged] does the same when
 * another device changes one.
 */
@Singleton
class PlaylistEditorRepository @Inject constructor(
    private val sessions: SessionStore,
    private val apis: ApiFactory,
    private val server: ServerRepository,
    private val library: LibraryRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    private val listed = MutableStateFlow<List<Playlist>>(emptyList())

    /** The caller-visible playlists as last fetched, `liked` included. */
    val playlists: StateFlow<List<Playlist>> = listed.asStateFlow()

    /**
     * Whether the paired server supports the writes. Read from `/api/server/info` (kept across
     * launches), so an older server leaves playlists read-only instead of answering `403`.
     */
    val editingSupported: StateFlow<Boolean> = server.serverInfo
        .map { it?.capabilities?.playlistEditing == true }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** The same flag, read now: what the writes check before they go out. */
    private val supported: Boolean get() = server.serverInfo.value?.capabilities?.playlistEditing == true

    /** Refetches the playlists; false when the server couldn't be reached. */
    suspend fun refresh(): Boolean {
        val outcome = call { it.playlists().map { dto -> dto.toModel() } }
        return when (outcome) {
            is ServerCall.Ok -> {
                listed.value = outcome.value
                true
            }

            is ServerCall.Err -> false
        }
    }

    /** `POST /api/v1/playlists`: an empty manual playlist, owned by the caller. */
    suspend fun create(name: String): PlaylistWriteResult {
        val wanted = name.trim()
        if (wanted.isEmpty()) return PlaylistWriteResult.Failed(PlaylistEditError.InvalidName)
        return write { it.createPlaylist(CreatePlaylistRequest(wanted)) }
    }

    /** `POST /api/v1/playlists/rename`: the owner or an admin, never just an editor. */
    suspend fun rename(name: String, newName: String): PlaylistWriteResult {
        val wanted = newName.trim()
        if (wanted.isEmpty()) return PlaylistWriteResult.Failed(PlaylistEditError.InvalidName)
        if (wanted == name) return PlaylistWriteResult.Ok(PlaylistEditResult(name = name))
        return write { it.renamePlaylist(RenamePlaylistRequest(name, wanted)) }
    }

    /**
     * `POST /api/v1/playlists/tracks` with `add`: appends the ids, skipping the ones already there
     * unless [allowDuplicates]. Podcast episodes are dropped (see [playlistTrackIds]): the server
     * has no track for them.
     */
    suspend fun addTracks(
        name: String,
        trackIds: List<String>,
        allowDuplicates: Boolean = false,
    ): PlaylistWriteResult {
        val ids = playlistTrackIds(trackIds)
        if (ids.isEmpty()) return PlaylistWriteResult.Failed(PlaylistEditError.NoTracks)
        if (ids.size > MAX_REFERENCES) return PlaylistWriteResult.Failed(PlaylistEditError.TooManyTracks)
        return write {
            it.editPlaylistTracks(
                PlaylistTracksRequest(name = name, add = ids, allowDuplicates = if (allowDuplicates) true else null),
            )
        }
    }

    /** `POST /api/v1/playlists/tracks` with `remove`: every copy of each id goes. */
    suspend fun removeTracks(name: String, trackIds: List<String>): PlaylistWriteResult {
        val ids = playlistTrackIds(trackIds)
        if (ids.isEmpty()) return PlaylistWriteResult.Failed(PlaylistEditError.NoTracks)
        if (ids.size > MAX_REFERENCES) return PlaylistWriteResult.Failed(PlaylistEditError.TooManyTracks)
        return write { it.editPlaylistTracks(PlaylistTracksRequest(name = name, remove = ids)) }
    }

    /**
     * Takes out one [occurrence] (zero-based, as the playlist lists them) of [trackId], leaving the
     * other copies: a manual playlist may hold the same song more than once.
     */
    suspend fun removeEntry(name: String, trackId: String, occurrence: Int): PlaylistWriteResult {
        if (playlistTrackIds(listOf(trackId)).isEmpty() || occurrence < 0) {
            return PlaylistWriteResult.Failed(PlaylistEditError.NoTracks)
        }
        return write {
            it.editPlaylistTracks(
                PlaylistTracksRequest(name = name, removeEntries = listOf(TrackEntryDto(trackId, occurrence))),
            )
        }
    }

    /**
     * `PUT /api/v1/playlists/order`: the caller-visible ids in the order they should be, repeats
     * kept. A `409` means our list was stale: reload, retry once when the songs still match what the
     * user meant, else say the playlist changed.
     */
    suspend fun reorder(name: String, trackIds: List<String>): PlaylistWriteResult {
        if (trackIds.isEmpty()) return PlaylistWriteResult.Failed(PlaylistEditError.NoTracks)
        if (trackIds.size > MAX_REFERENCES) return PlaylistWriteResult.Failed(PlaylistEditError.TooManyTracks)
        val requested = PlaylistOrderRequest(name, trackIds)
        val first = write { it.reorderPlaylist(requested) }
        if (first !is ServerCall.Err || first.error != PlaylistEditError.Stale) return result(first)
        return retriedReorder(name, trackIds, requested)
    }

    private suspend fun retriedReorder(
        name: String,
        trackIds: List<String>,
        requested: PlaylistOrderRequest,
    ): PlaylistWriteResult {
        refresh()
        val current = listed.value.firstOrNull { it.name == name }?.trackIds
        // Retry only when the reload still holds the same songs: the user's order is then still right.
        if (current == null || current.sorted() != trackIds.sorted()) {
            return PlaylistWriteResult.Failed(PlaylistEditError.Stale)
        }
        return result(write { it.reorderPlaylist(requested) })
    }

    /** Creates a playlist called [name] and puts [trackIds] in it, for the picker's "New playlist". */
    suspend fun createWithTracks(name: String, trackIds: List<String>): PlaylistWriteResult {
        val created = create(name)
        if (created !is PlaylistWriteResult.Ok) return created
        val playlist = created.result.name.ifBlank { name.trim() }
        val ids = playlistTrackIds(trackIds)
        if (ids.isEmpty()) return PlaylistWriteResult.Ok(created.result)
        val added = addTracks(playlist, ids)
        // The playlist exists: report the add's failure through the playlist it made.
        return if (added is PlaylistWriteResult.Ok) added else PlaylistWriteResult.Ok(created.result)
    }

    /** Runs [block] against the paired server, then reacts to what came back. */
    private suspend fun write(block: suspend (DowntifyApi) -> PlaylistWriteDto): ServerCall<PlaylistEditResult> =
        when (val outcome = call { block(it).toModel() }) {
            is ServerCall.Ok -> {
                afterWrite()
                outcome
            }

            is ServerCall.Err -> outcome
        }

    /** The server lists the playlists afresh, and so does the library's own copy. */
    private suspend fun afterWrite() {
        refresh()
        library.refreshPlaylists()
    }

    private fun result(outcome: ServerCall<PlaylistEditResult>): PlaylistWriteResult = when (outcome) {
        is ServerCall.Ok -> PlaylistWriteResult.Ok(outcome.value)
        is ServerCall.Err -> PlaylistWriteResult.Failed(outcome.error)
    }

    /**
     * The mobile routes only: `callServer` builds a
     * [com.henriquesebastiao.downtify.core.network.WebApi], and these routes are on [DowntifyApi].
     * The mapping is `ServerResult`'s, with the statuses the writes give names.
     */
    private suspend fun <T> call(block: suspend (DowntifyApi) -> T): ServerCall<T> {
        if (!supported) return ServerCall.Err(PlaylistEditError.NotSupported)
        val session = sessions.current ?: return ServerCall.Err(PlaylistEditError.Offline)
        val api = apis.create(session.baseUrl)
        return try {
            ServerCall.Ok(block(api))
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            Log.w(TAG, "Playlist write refused: ${e.code()}")
            ServerCall.Err(errorFor(e.code()))
        } catch (e: SerializationException) {
            Log.w(TAG, "Unexpected answer", e)
            ServerCall.Err(PlaylistEditError.Failed(null))
        } catch (e: IOException) {
            Log.d(TAG, "Server unreachable", e)
            ServerCall.Err(PlaylistEditError.Offline)
        }
    }

    private fun errorFor(status: Int): PlaylistEditError = when (status) {
        STATUS_BAD_REQUEST -> PlaylistEditError.TooManyTracks
        STATUS_FORBIDDEN -> PlaylistEditError.Forbidden
        STATUS_NOT_FOUND -> PlaylistEditError.NotFound
        STATUS_CONFLICT -> PlaylistEditError.Stale
        STATUS_UNAVAILABLE -> PlaylistEditError.IndexNotReady
        else -> PlaylistEditError.Failed(status)
    }

    private companion object {
        const val TAG = "PlaylistEditor"
        const val STATUS_BAD_REQUEST = 400
        const val STATUS_FORBIDDEN = 403
        const val STATUS_NOT_FOUND = 404
        const val STATUS_CONFLICT = 409
        const val STATUS_UNAVAILABLE = 503

        /** "at most 10,000 total references across all three arrays" ("Editing playlists"). */
        const val MAX_REFERENCES = 10_000
    }
}

/** The answer to one call, before it is a [PlaylistWriteResult]. */
internal sealed interface ServerCall<out T> {
    data class Ok<T>(val value: T) : ServerCall<T>

    data class Err(val error: PlaylistEditError) : ServerCall<Nothing>
}
