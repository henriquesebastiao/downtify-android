package com.henriquesebastiao.downtify.core.network

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface MusicDjApi {
    @retrofit2.http.DELETE("api/v1/music-ai/dj/requests/{nonce}")
    suspend fun djCancelRequest(@retrofit2.http.Path("nonce") nonce: String)

    @GET("api/v1/music-ai/dj/sessions")
    suspend fun djSessions(
        @retrofit2.http.Query("summary") summary: Boolean = false,
    ): com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionsDto

    @GET("api/v1/music-ai/dj/sessions/{id}")
    suspend fun djSession(@Path("id") id: String): com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto

    @GET("api/v1/music-ai/dj/sessions/{id}")
    suspend fun djPlayback(
        @Path("id") id: String,
        @retrofit2.http.Query("position") position: Double,
        @retrofit2.http.Header("If-None-Match") etag: String?,
        @retrofit2.http.Query("view") view: String = "playback",
    ): retrofit2.Response<com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto>

    @POST("api/v1/music-ai/dj/sessions/{id}/commands")
    suspend fun djCommand(
        @Path("id") id: String,
        @Body command: com.henriquesebastiao.downtify.core.network.dto.MusicDjCommandDto,
    ): com.henriquesebastiao.downtify.core.network.dto.MusicDjCommandResultDto

    @POST("api/v1/music-ai/dj/sessions")
    suspend fun djCreate(
        @Body selection: com.henriquesebastiao.downtify.core.network.dto.MusicDjSelectionDto,
    ): com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto

    @DELETE("api/v1/music-ai/dj/sessions/{id}")
    suspend fun djStop(@Path("id") id: String): JsonObject
}
