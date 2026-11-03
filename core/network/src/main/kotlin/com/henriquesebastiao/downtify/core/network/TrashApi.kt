package com.henriquesebastiao.downtify.core.network

import com.henriquesebastiao.downtify.core.network.dto.TrashCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.TrashConfirmationDto
import com.henriquesebastiao.downtify.core.network.dto.TrashConfirmedDto
import com.henriquesebastiao.downtify.core.network.dto.TrashExclusionsDto
import com.henriquesebastiao.downtify.core.network.dto.TrashIdsDto
import com.henriquesebastiao.downtify.core.network.dto.TrashListingDto
import com.henriquesebastiao.downtify.core.network.dto.TrashPreviewStartDto
import com.henriquesebastiao.downtify.core.network.dto.TrashPreviewStatusDto
import com.henriquesebastiao.downtify.core.network.dto.TrashSelectionDto
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The music trash part of the mobile client contract ("Music trash (admins)"), apart from
 * [DowntifyApi] only to keep each interface small. Older servers answer 404; the server checks the
 * account on every call.
 */
interface TrashApi {
    @GET("api/v1/library/trash/capabilities")
    suspend fun trashCapabilities(): TrashCapabilitiesDto

    @POST("api/v1/library/trash/preview")
    suspend fun trashPreview(@Body body: TrashSelectionDto): TrashPreviewStartDto

    @GET("api/v1/library/trash/preview/{id}")
    suspend fun trashPreviewStatus(@Path("id") requestId: String): TrashPreviewStatusDto

    @POST("api/v1/library/trash/confirm")
    suspend fun trashConfirm(@Body body: TrashConfirmationDto): TrashConfirmedDto

    @GET("api/v1/library/trash")
    suspend fun trashList(
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
        @Query("search") search: String,
    ): TrashListingDto

    @GET("api/v1/library/trash/exclusions")
    suspend fun trashExclusions(): TrashExclusionsDto

    @POST("api/v1/library/trash/restore")
    suspend fun trashRestore(@Body body: TrashIdsDto): JsonObject

    @POST("api/v1/library/trash/retry")
    suspend fun trashRetry(@Body body: TrashIdsDto): JsonObject

    @POST("api/v1/library/trash/allow-download")
    suspend fun trashAllowDownload(@Body body: TrashIdsDto): JsonObject
}
