package com.henriquesebastiao.downtify.core.network

import com.henriquesebastiao.downtify.core.network.dto.AuthStatusDto
import com.henriquesebastiao.downtify.core.network.dto.BlockArtistRequest
import com.henriquesebastiao.downtify.core.network.dto.CreatePlaylistRequest
import com.henriquesebastiao.downtify.core.network.dto.DiscoverArtistsDto
import com.henriquesebastiao.downtify.core.network.dto.DiscoverCollectionsDto
import com.henriquesebastiao.downtify.core.network.dto.DiscoverRequest
import com.henriquesebastiao.downtify.core.network.dto.LibraryPageDto
import com.henriquesebastiao.downtify.core.network.dto.LikeRequest
import com.henriquesebastiao.downtify.core.network.dto.LikeResponse
import com.henriquesebastiao.downtify.core.network.dto.LikesDto
import com.henriquesebastiao.downtify.core.network.dto.ListenRequest
import com.henriquesebastiao.downtify.core.network.dto.LoudnessDto
import com.henriquesebastiao.downtify.core.network.dto.LyricsDto
import com.henriquesebastiao.downtify.core.network.dto.MeResponse
import com.henriquesebastiao.downtify.core.network.dto.PairRequest
import com.henriquesebastiao.downtify.core.network.dto.PairResponse
import com.henriquesebastiao.downtify.core.network.dto.PlaybackActivityRequest
import com.henriquesebastiao.downtify.core.network.dto.PlaylistDto
import com.henriquesebastiao.downtify.core.network.dto.PlaylistFavoriteRequest
import com.henriquesebastiao.downtify.core.network.dto.PlaylistFavoriteResponse
import com.henriquesebastiao.downtify.core.network.dto.PlaylistOrderRequest
import com.henriquesebastiao.downtify.core.network.dto.PlaylistTracksRequest
import com.henriquesebastiao.downtify.core.network.dto.PlaylistWriteDto
import com.henriquesebastiao.downtify.core.network.dto.PodcastEpisodeDto
import com.henriquesebastiao.downtify.core.network.dto.PodcastEpisodesDto
import com.henriquesebastiao.downtify.core.network.dto.PodcastPlaybackRequest
import com.henriquesebastiao.downtify.core.network.dto.PodcastShowDto
import com.henriquesebastiao.downtify.core.network.dto.RenamePlaylistRequest
import com.henriquesebastiao.downtify.core.network.dto.ServerInfoDto
import com.henriquesebastiao.downtify.core.network.dto.SignInRequest
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
 * The server routes this app uses — only those in the mobile client contract
 * (`~/git/downtify/docs/mobile-client-contract.md`). Paths are relative so a
 * base URL with a path (a reverse proxy) works.
 */
interface DowntifyApi {
    @GET("api/server/info")
    suspend fun serverInfo(): ServerInfoDto

    @GET("api/auth/status")
    suspend fun authStatus(): AuthStatusDto

    @POST("api/auth/pair")
    suspend fun pair(@Body body: PairRequest): PairResponse

    @POST("api/auth/token")
    suspend fun signIn(@Body body: SignInRequest): PairResponse

    @GET("api/v1/library")
    suspend fun library(
        @Query("since") since: Long,
        @Query("refresh") refresh: Boolean? = null,
    ): Response<LibraryPageDto>

    @GET("api/v1/playlists")
    suspend fun playlists(): List<PlaylistDto>

    @PUT("api/v1/playlists/favorite")
    suspend fun setPlaylistFavorite(@Body body: PlaylistFavoriteRequest): PlaylistFavoriteResponse

    // The playlist write routes ("Editing playlists"): manual playlists only, by stable track ID.
    // A refused write throws: 409 stale order, 403 not allowed, 400 over 10,000 references, 503
    // while the sync index is not ready.

    @POST("api/v1/playlists")
    suspend fun createPlaylist(@Body body: CreatePlaylistRequest): PlaylistWriteDto

    @POST("api/v1/playlists/rename")
    suspend fun renamePlaylist(@Body body: RenamePlaylistRequest): PlaylistWriteDto

    @POST("api/v1/playlists/tracks")
    suspend fun editPlaylistTracks(@Body body: PlaylistTracksRequest): PlaylistWriteDto

    @PUT("api/v1/playlists/order")
    suspend fun reorderPlaylist(@Body body: PlaylistOrderRequest): PlaylistWriteDto

    @GET("api/v1/likes")
    suspend fun likes(): LikesDto

    @PUT("api/v1/likes")
    suspend fun setLike(@Body body: LikeRequest): LikeResponse

    @GET("api/v1/tracks/{id}/lyrics")
    suspend fun lyrics(@Path("id") trackId: String): LyricsDto

    @GET("api/v1/tracks/{id}/loudness")
    suspend fun loudness(@Path("id") trackId: String): LoudnessDto

    @GET("api/me")
    suspend fun me(): MeResponse

    @POST("api/activity/playback")
    suspend fun reportPlayback(@Body body: PlaybackActivityRequest): Response<Unit>

    @POST("api/discover/listens")
    suspend fun reportListen(@Body body: ListenRequest): Response<Unit>
}
