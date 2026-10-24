package com.henriquesebastiao.downtify.core.data.discover

import com.henriquesebastiao.downtify.core.data.ServerResult
import com.henriquesebastiao.downtify.core.data.callServer
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.model.DiscoverArtists
import com.henriquesebastiao.downtify.core.model.DiscoverCollections
import com.henriquesebastiao.downtify.core.model.DiscoverInput
import com.henriquesebastiao.downtify.core.network.ApiFactory
import com.henriquesebastiao.downtify.core.network.dto.BlockArtistRequest
import com.henriquesebastiao.downtify.core.network.dto.DiscoverRequest
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

/**
 * Artists, albums and playlists suggested from the library (`/api/discover…`).
 * The server weighs the library with what was played and liked, asks Deezer and
 * Spotify, and keeps the answers for a week — so opening this is quick after the first time.
 */
@Singleton
class DiscoverRepository @Inject constructor(
    private val sessions: SessionStore,
    private val apis: ApiFactory,
    private val library: LibraryRepository,
    private val server: com.henriquesebastiao.downtify.core.data.session.ServerRepository,
) {
    val serverInfo get() = server.serverInfo

    /** Suggested artists, best first. Empty until the library is synced. */
    suspend fun artists(): ServerResult<DiscoverArtists> {
        val seeds = seeds()
        if (seeds.isEmpty()) return ServerResult.Ok(DiscoverArtists(emptyList(), partial = false))
        return callServer(sessions, apis, patient = true) { api ->
            val answer = api.discover(DiscoverRequest.of(seeds))
            DiscoverArtists(answer.artists.filter { it.name.isNotBlank() }.map { it.toModel() }, answer.partial)
        }
    }

    /** Albums and playlists built on the suggestions; asked after [artists], which the server reuses. */
    suspend fun collections(): ServerResult<DiscoverCollections> {
        val seeds = seeds()
        if (seeds.isEmpty()) {
            return ServerResult.Ok(
                DiscoverCollections(emptyList(), emptyList(), emptyList(), emptyMap(), false),
            )
        }
        val albums = library.library.value?.albums?.let(DiscoverInput::albumKeys).orEmpty()
        return callServer(sessions, apis, patient = true) { api ->
            val answer = api.discoverCollections(DiscoverRequest.of(seeds, albums))
            DiscoverCollections(
                albums = answer.albums.map { it.toModel() },
                moreAlbums = answer.moreAlbums.map { it.toModel() },
                playlists = answer.playlists.map { it.toModel() },
                artistUrls = answer.artistUrls,
                partial = answer.partial,
            )
        }
    }

    /** "Not interested": never suggest [name] again. */
    suspend fun hide(name: String): ServerResult<Unit> = callServer(sessions, apis) { api ->
        val response = api.blockArtist(BlockArtistRequest(name))
        if (!response.isSuccessful) throw HttpException(response)
    }

    /** Undo for [hide]. */
    suspend fun show(name: String): ServerResult<Unit> = callServer(sessions, apis) { api ->
        val response = api.unblockArtist(name)
        if (!response.isSuccessful) throw HttpException(response)
    }

    private fun seeds() = library.library.value
        ?.let { DiscoverInput.seeds(it.artists, library.likedIds.value.toSet()) }
        .orEmpty()
}
