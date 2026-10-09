package com.henriquesebastiao.downtify.core.data.catalog

import com.henriquesebastiao.downtify.core.data.ServerResult
import com.henriquesebastiao.downtify.core.data.callServer
import com.henriquesebastiao.downtify.core.model.RemoteAlbum
import com.henriquesebastiao.downtify.core.model.RemoteSong
import com.henriquesebastiao.downtify.core.model.ResolvedLink
import com.henriquesebastiao.downtify.core.model.StreamVideo
import com.henriquesebastiao.downtify.core.network.ApiFactory
import com.henriquesebastiao.downtify.core.network.CatalogJson
import com.henriquesebastiao.downtify.core.network.WebApi
import com.henriquesebastiao.downtify.core.network.dto.PrefetchRequestDto
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import retrofit2.HttpException

/** What the server's search found that isn't in the library. */
data class CatalogSearch(val songs: List<RemoteSong>, val albums: List<RemoteAlbum>)

/**
 * The server's own search (YouTube Music, pasted Spotify and YouTube links),
 * full-track streaming of what it found, and asking the server to download it.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val sessions: SessionStore,
    private val apis: ApiFactory,
    private val queue: ServerQueueRepository,
) {
    private val requested = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    /** Albums (by id or link) this phone asked for, with their songs' ids — for "Downloading 4/9". */
    val requestedAlbums: StateFlow<Map<String, List<String>>> = requested.asStateFlow()

    suspend fun search(query: String): ServerResult<CatalogSearch> = call { api ->
        coroutineScope {
            // Albums are extra: their search may be off for this user, or fail alone.
            val albums = async { runCatching { CatalogJson.albums(api.searchAlbums(query, ALBUM_LIMIT)) }.getOrNull() }
            val songs = CatalogJson.songs(api.searchSongs(query))
            CatalogSearch(songs, albums.await().orEmpty())
        }
    }

    suspend fun resolve(url: String): ServerResult<ResolvedLink> = call { api ->
        CatalogJson.resolved(url, api.resolve(url))
    }

    /**
     * The YouTube video to stream [song] from the server's cache: its own
     * when the row carries one, else the first search hit with one. Null
     * when nothing resolves (the row can't play).
     */
    suspend fun streamVideoId(song: RemoteSong): String? {
        StreamVideo.videoIdOf(song)?.let { return it }
        val artist = song.artists.firstOrNull() ?: return null
        if (song.title.isBlank()) return null
        return when (val result = call { api -> api.searchSongs("$artist ${song.title}") }) {
            is ServerResult.Ok ->
                result.value
                    .let(CatalogJson::songs)
                    .firstNotNullOfOrNull(StreamVideo::videoIdOf)

            else -> null
        }
    }

    /** Warm the server's stream cache for [videoId] without waiting for it. */
    suspend fun prefetchStream(videoId: String) {
        if (videoId.isBlank()) return
        call { api -> api.prefetchStream(PrefetchRequestDto(videoId)) }
    }

    /** Queues [songs] on the server; a [playlistUrl] keeps them together as that playlist. */
    suspend fun download(songs: List<RemoteSong>, playlistUrl: String? = null): ServerResult<Unit> {
        if (songs.isEmpty()) return ServerResult.Ok(Unit)
        val result = call { api ->
            val response = api.downloadBatch(CatalogJson.batch(songs, playlistUrl))
            if (!response.isSuccessful) throw HttpException(response)
        }
        if (result is ServerResult.Ok) queue.refresh()
        return result
    }

    /** Resolves [album]'s tracks and queues them all. */
    suspend fun downloadAlbum(album: RemoteAlbum): ServerResult<Unit> = when (val resolved = resolve(album.url)) {
        is ServerResult.Ok -> {
            val tracks = resolved.value.tracks
            requested.update { it + (album.id to tracks.map { t -> t.id }) }
            download(tracks)
        }

        is ServerResult.Failed -> ServerResult.Failed(resolved.status)

        ServerResult.Unreachable -> ServerResult.Unreachable
    }

    private suspend fun <T> call(block: suspend (WebApi) -> T): ServerResult<T> =
        callServer(sessions, apis, block = block)

    private companion object {
        const val ALBUM_LIMIT = 10
    }
}
