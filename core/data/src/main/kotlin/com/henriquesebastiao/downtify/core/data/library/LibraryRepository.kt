package com.henriquesebastiao.downtify.core.data.library

import android.util.Log
import android.util.LruCache
import androidx.room.withTransaction
import com.henriquesebastiao.downtify.core.data.db.DowntifyDatabase
import com.henriquesebastiao.downtify.core.data.db.LikeEntity
import com.henriquesebastiao.downtify.core.data.db.PlaylistEntity
import com.henriquesebastiao.downtify.core.data.di.ApplicationScope
import com.henriquesebastiao.downtify.core.data.sync.LibraryLocalStore
import com.henriquesebastiao.downtify.core.data.sync.LibraryRemote
import com.henriquesebastiao.downtify.core.data.sync.LibrarySyncer
import com.henriquesebastiao.downtify.core.data.sync.SyncOutcome
import com.henriquesebastiao.downtify.core.model.Lyrics
import com.henriquesebastiao.downtify.core.model.Playlist
import com.henriquesebastiao.downtify.core.network.ApiFactory
import com.henriquesebastiao.downtify.core.network.DowntifyApi
import com.henriquesebastiao.downtify.core.network.dto.LikeRequest
import com.henriquesebastiao.downtify.core.network.dto.PlaylistFavoriteRequest
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import retrofit2.HttpException

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Failed(val offline: Boolean) : SyncStatus
}

/** The synced library, playlists and likes, and keeping them in step with the server. */
// Server calls here fail soft: any error leaves the local library as it was.
@Suppress("TooGenericExceptionCaught")
@Singleton
class LibraryRepository @Inject constructor(
    private val db: DowntifyDatabase,
    private val store: LibraryLocalStore,
    private val sessions: SessionStore,
    private val apis: ApiFactory,
    @ApplicationScope scope: CoroutineScope,
) {
    /** Null until the database was read once. */
    val library: StateFlow<LibrarySnapshot?> = db.tracks().observeAll()
        .distinctUntilChanged()
        .map { rows -> LibrarySnapshot(rows.map { it.toModel() }) }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, null)

    val playlists: StateFlow<List<Playlist>> = db.playlists().observeAll()
        .map { rows -> rows.map { it.toModel() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Liked track ids, most recently liked first. */
    val likedIds: StateFlow<List<String>> = db.likes().observeIds()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = status.asStateFlow()

    private val lyricsCache = LruCache<String, Lyrics>(LYRICS_CACHE_SIZE)

    private fun api(): DowntifyApi? = sessions.current?.let { apis.create(it.baseUrl) }

    /**
     * Pulls the library feed, then playlists and likes. [refresh] asks the server
     * to rescan its folder now (pull to refresh). Returns false when the server
     * couldn't be reached.
     */
    suspend fun sync(refresh: Boolean = false): Boolean {
        val session = sessions.current ?: return false
        val api = apis.create(session.baseUrl)
        status.value = SyncStatus.Syncing
        return try {
            val outcome = LibrarySyncer(store, remoteFor(api)).sync(session.serverId, refresh)
            if (outcome != SyncOutcome.Unchanged) Log.i(TAG, "Library sync: $outcome")
            refreshPlaylists(api)
            refreshLikes(api)
            status.value = SyncStatus.Idle
            true
        } catch (e: CancellationException) {
            status.value = SyncStatus.Idle
            throw e
        } catch (e: HttpException) {
            Log.w(TAG, "Library sync failed", e)
            status.value = SyncStatus.Failed(offline = false)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Library sync failed", e)
            status.value = SyncStatus.Failed(offline = true)
            false
        }
    }

    suspend fun refreshLikes() {
        api()?.let { runCatching { refreshLikes(it) }.onFailure { if (it is CancellationException) throw it } }
    }

    suspend fun setPlaylistFavorite(name: String, favorite: Boolean): Boolean {
        val session = sessions.current ?: return false
        val api = apis.create(session.baseUrl)
        return try {
            api.setPlaylistFavorite(PlaylistFavoriteRequest(name, favorite))
            if (sessions.current != session) return false
            refreshPlaylists(api)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Could not change a playlist favorite", error)
            false
        }
    }

    /** Likes or unlikes [trackId] here at once, then on the server; undone if the server says no. */
    suspend fun setLiked(trackId: String, liked: Boolean): Boolean {
        val likes = db.likes()
        if (liked) likes.upsert(listOf(LikeEntity(trackId, (likes.minPosition() ?: 0) - 1))) else likes.delete(trackId)
        val api = api() ?: return false
        return try {
            api.setLike(LikeRequest(trackId, liked))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not change the like of $trackId", e)
            runCatching { refreshLikes(api) }
            false
        }
    }

    /**
     * `{synced, plain}` lyrics, cached for the session; [Lyrics.Empty] when
     * there are none or the server can't be reached.
     */
    suspend fun lyrics(trackId: String): Lyrics {
        lyricsCache[trackId]?.let { return it }
        val api = api() ?: return Lyrics.Empty
        return try {
            withContext(Dispatchers.Default) { api.lyrics(trackId).toModel() }.also { lyricsCache.put(trackId, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No lyrics for $trackId", e)
            Lyrics.Empty
        }
    }

    private suspend fun refreshPlaylists(api: DowntifyApi) {
        val remote = api.playlists()
        db.withTransaction {
            db.playlists().deleteAll()
            db.playlists().upsert(
                remote.mapIndexed { index, p ->
                    PlaylistEntity(
                        p.name,
                        p.liked,
                        p.cover,
                        index,
                        p.trackIds,
                        p.favorite == true,
                        p.displayName,
                        p.favorite != null,
                    )
                },
            )
        }
    }

    private suspend fun refreshLikes(api: DowntifyApi) {
        val ids = api.likes().trackIds
        db.withTransaction {
            db.likes().deleteAll()
            db.likes().upsert(ids.mapIndexed { index, id -> LikeEntity(id, index) })
        }
    }

    private fun remoteFor(api: DowntifyApi) = LibraryRemote { since, refresh ->
        val response = api.library(since, if (refresh) true else null)
        when {
            response.code() == HTTP_NOT_MODIFIED -> null
            response.isSuccessful -> response.body()?.toModel() ?: error("Empty library response")
            else -> throw HttpException(response)
        }
    }

    private companion object {
        const val TAG = "LibraryRepository"
        const val HTTP_NOT_MODIFIED = 304
        const val LYRICS_CACHE_SIZE = 32
    }
}
