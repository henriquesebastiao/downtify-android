package com.henriquesebastiao.downtify.core.network.dto

import com.henriquesebastiao.downtify.core.model.TrashCapability
import com.henriquesebastiao.downtify.core.model.TrashItem
import com.henriquesebastiao.downtify.core.model.TrashPage
import com.henriquesebastiao.downtify.core.model.TrashPreview
import com.henriquesebastiao.downtify.core.model.TrashState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The music trash (`/api/v1/library/trash/…`), on servers that have it on. Every field has a
// default: a server that lacks one, or sends more, still decodes.

private const val MILLIS_PER_SECOND = 1000

@Serializable
data class TrashCapabilitiesDto(
    val enabled: Boolean = false,
    @SerialName("can_manage") val canManage: Boolean = false,
    @SerialName("retention_days") val retentionDays: Int = 30,
) {
    fun toModel() = TrashCapability(enabled = enabled, canManage = enabled && canManage, retentionDays = retentionDays)
}

/** Library ids, never paths: the server finds the files. */
@Serializable
data class TrashSelectionDto(@SerialName("track_ids") val trackIds: List<String>)

@Serializable
data class TrashConfirmationDto(val confirmation: String)

@Serializable
data class TrashIdsDto(val ids: List<String>)

@Serializable
data class TrashMetadataDto(val title: String? = null, val artist: String? = null, val codec: String? = null)

@Serializable
data class TrashItemDto(
    val id: String,
    val file: String = "",
    @SerialName("track_id") val trackId: String? = null,
    val metadata: TrashMetadataDto = TrashMetadataDto(),
    val state: String = "",
    val size: Long = 0,
    @SerialName("expires_at") val expiresAt: Double? = null,
    val error: String? = null,
) {
    fun toModel() = TrashItem(
        id = id,
        file = file,
        trackId = trackId.orEmpty(),
        title = metadata.title.orEmpty(),
        artist = metadata.artist.orEmpty(),
        codec = metadata.codec.orEmpty(),
        state = TrashState.of(state),
        size = size,
        expiresAt = expiresAt?.let { (it * MILLIS_PER_SECOND).toLong() },
        error = error.orEmpty(),
    )
}

@Serializable
data class TrashPreviewDto(val confirmation: String, val items: List<TrashItemDto> = emptyList()) {
    fun toModel() = TrashPreview(confirmation, items.map { it.toModel() })
}

@Serializable
data class TrashPreviewStartDto(@SerialName("request_id") val requestId: String, val state: String = "preparing")

@Serializable
data class TrashPreviewStatusDto(val state: String = "preparing", val preview: TrashPreviewDto? = null)

@Serializable
data class TrashConfirmedDto(val ids: List<String> = emptyList(), val state: String = "pending")

@Serializable
data class TrashListingDto(val items: List<TrashItemDto> = emptyList(), val total: Int = 0, val size: Long = 0) {
    fun toModel() = TrashPage(items.map { it.toModel() }, total, size)
}

@Serializable
data class TrashExclusionsDto(val items: List<TrashItemDto> = emptyList())
