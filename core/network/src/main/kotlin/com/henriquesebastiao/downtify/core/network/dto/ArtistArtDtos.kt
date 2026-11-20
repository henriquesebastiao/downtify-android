package com.henriquesebastiao.downtify.core.network.dto

import com.henriquesebastiao.downtify.core.network.ServerUrls
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /api/artists/art`: an artist's saved photo and banner, as paths under the server's
 * `/downloads` (null when none is saved), each with its file's modified time.
 */
@Serializable
data class ArtistArtDto(
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("photo_version") val photoVersion: Long? = null,
    @SerialName("banner_url") val bannerUrl: String? = null,
    @SerialName("banner_version") val bannerVersion: Long? = null,
) {
    /** The banner on the server at [baseUrl], or null when the artist has none. */
    fun bannerUrl(baseUrl: String): String? = bannerUrl?.takeIf { it.isNotBlank() }?.let {
        ServerUrls.file(baseUrl, it, bannerVersion)
    }
}
