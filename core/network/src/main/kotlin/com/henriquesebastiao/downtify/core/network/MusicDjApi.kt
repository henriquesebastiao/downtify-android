package com.henriquesebastiao.downtify.core.network

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface MusicDjApi {
    @GET("api/v1/music-ai/dj/sessions")
    suspend fun djSessions(): com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionsDto

    @GET("api/v1/music-ai/dj/sessions/{id}")
    suspend fun djSession(@Path("id") id: String): com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto

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
