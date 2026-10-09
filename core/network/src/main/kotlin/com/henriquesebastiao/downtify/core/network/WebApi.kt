package com.henriquesebastiao.downtify.core.network

import com.henriquesebastiao.downtify.core.network.dto.AuthStatusDto
import com.henriquesebastiao.downtify.core.network.dto.BlockArtistRequest
import com.henriquesebastiao.downtify.core.network.dto.DiscoverArtistsDto
import com.henriquesebastiao.downtify.core.network.dto.DiscoverCollectionsDto
import com.henriquesebastiao.downtify.core.network.dto.DiscoverRequest
import com.henriquesebastiao.downtify.core.network.dto.LibraryPageDto
import com.henriquesebastiao.downtify.core.network.dto.LikeRequest
import com.henriquesebastiao.downtify.core.network.dto.LikeResponse
import com.henriquesebastiao.downtify.core.network.dto.LikesDto
import com.henriquesebastiao.downtify.core.network.dto.ListenRequest
import com.henriquesebastiao.downtify.core.network.dto.LyricsDto
import com.henriquesebastiao.downtify.core.network.dto.MeResponse
import com.henriquesebastiao.downtify.core.network.dto.PairRequest
import com.henriquesebastiao.downtify.core.network.dto.PairResponse
import com.henriquesebastiao.downtify.core.network.dto.PlaybackActivityRequest
import com.henriquesebastiao.downtify.core.network.dto.PlaylistDto
import com.henriquesebastiao.downtify.core.network.dto.PodcastEpisodeDto
import com.henriquesebastiao.downtify.core.network.dto.PodcastEpisodesDto
import com.henriquesebastiao.downtify.core.network.dto.PodcastPlaybackRequest
import com.henriquesebastiao.downtify.core.network.dto.PodcastShowDto
import com.henriquesebastiao.downtify.core.network.dto.PrefetchRequestDto
import com.henriquesebastiao.downtify.core.network.dto.ServerInfoDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The web app's own routes that a device may call: search and link resolving, previews, download
 * requests, the download queue, podcasts and Discover. They aren't versioned like `/api/v1`
 * (the contract only says devices may use them), so what they answer is read leniently.
 */
interface WebApi {
    @GET("api/songs/search")
    suspend fun searchSongs(@Query("query") query: String): JsonArray

    /** Undocumented in the API reference, but in the server's code and allowed to devices. */
    @GET("api/albums/search")
    suspend fun searchAlbums(@Query("query") query: String, @Query("limit") limit: Int): JsonArray

    @GET("api/url/resolve")
    suspend fun resolve(@Query("url") url: String): JsonObject

    /** Tracks like one track, from YouTube Music's radio mix (`GET /api/similar/tracks`). Needs no key. */
    @GET("api/similar/tracks")
    suspend fun similarTracks(
        @Query("artist") artist: String,
        @Query("track") track: String,
        @Query("limit") limit: Int,
    ): JsonObject

    /** Start caching a video's audio on the server without waiting for it. */
    @POST("api/stream/prefetch")
    suspend fun prefetchStream(@Body body: PrefetchRequestDto): Response<Unit>

    @POST("api/download/batch")
    suspend fun downloadBatch(@Body body: JsonObject): Response<Unit>

    @GET("api/queue")
    suspend fun queue(): JsonArray

    @GET("api/podcasts/shows")
    suspend fun podcastShows(): List<PodcastShowDto>

    @GET("api/podcasts/shows/{id}/episodes")
    suspend fun podcastEpisodes(@Path("id") showId: Long): PodcastEpisodesDto

    /** Blocks until the server has the file: use a patient client. */
    @POST("api/podcasts/episodes/{id}/download")
    suspend fun downloadPodcastEpisode(@Path("id") episodeId: Long): PodcastEpisodeDto

    @PUT("api/podcasts/episodes/{id}/playback")
    suspend fun setPodcastPlayback(@Path("id") episodeId: Long, @Body body: PodcastPlaybackRequest): PodcastEpisodeDto

    /** Slow the first time: the server asks Deezer for the library's artists. Use a patient client. */
    @POST("api/discover")
    suspend fun discover(@Body body: DiscoverRequest): DiscoverArtistsDto

    @POST("api/discover/collections")
    suspend fun discoverCollections(@Body body: DiscoverRequest): DiscoverCollectionsDto

    @POST("api/discover/blocked")
    suspend fun blockArtist(@Body body: BlockArtistRequest): Response<Unit>

    @DELETE("api/discover/blocked")
    suspend fun unblockArtist(@Query("name") name: String): Response<Unit>
}
