package com.henriquesebastiao.downtify.core.network

import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiEffectiveDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiListenDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiPlaylistsDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiPreferencesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiSavedDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiSelectionDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiTracksDto
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Optional versioned endpoints; pairing and authentication use the normal client. */
interface MusicAiApi : MusicDjApi {
    @GET("api/v1/music-ai/capabilities")
    suspend fun capabilities(): MusicAiCapabilitiesDto

    @GET("api/v1/music-ai/preferences")
    suspend fun preferences(): MusicAiPreferencesDto

    @PUT("api/v1/music-ai/preferences")
    suspend fun preferences(@Body changes: Map<String, Boolean>): MusicAiEffectiveDto

    @GET("api/v1/music-ai/admin/features")
    suspend fun globalFeatures(): Map<String, Boolean>

    @PUT("api/v1/music-ai/admin/features")
    suspend fun globalFeatures(@Body changes: Map<String, Boolean>): Map<String, Boolean>

    @GET("api/v1/music-ai/recommendations")
    suspend fun recommendations(@Query("seed") seed: String = ""): MusicAiTracksDto

    @GET("api/v1/music-ai/similar")
    suspend fun similar(
        @Query("artist") artist: String,
        @Query("track") track: String,
        @Query("seed") seed: String = "",
    ): JsonObject

    @GET("api/v1/music-ai/external-similar")
    suspend fun externalSimilar(@Query("artist") artist: String, @Query("track") track: String): JsonObject

    @GET("api/v1/music-ai/playlists/{cadence}/{mood}")
    suspend fun playlist(@Path("cadence") cadence: String, @Path("mood") mood: String): MusicAiTracksDto

    @GET("api/v1/music-ai/playlists")
    suspend fun playlists(): MusicAiPlaylistsDto

    @POST("api/v1/music-ai/playlists/save")
    suspend fun save(@Body selection: MusicAiSelectionDto): MusicAiSavedDto

    @POST("api/v1/music-ai/listens")
    suspend fun listen(@Body event: MusicAiListenDto): JsonObject

    @DELETE("api/v1/music-ai/profile")
    suspend fun forget(): JsonObject
}
