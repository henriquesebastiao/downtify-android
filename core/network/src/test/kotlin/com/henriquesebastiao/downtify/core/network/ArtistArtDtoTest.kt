package com.henriquesebastiao.downtify.core.network

import com.henriquesebastiao.downtify.core.network.dto.ArtistArtDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistArtDtoTest {
    private val base = "http://nas.local:8000"

    @Test
    fun `a saved banner becomes an encoded url with its version`() {
        val art = NetworkJson.decodeFromString<ArtistArtDto>(
            """
            {"photo_url":"/downloads/.metadata/ArtistImage/Avril Lavigne.jpg","photo_version":1,
             "banner_url":"/downloads/.metadata/ArtistBannerImage/Avril Lavigne.banner.jpg",
             "banner_version":1758700000123}
            """.trimIndent(),
        )
        assertEquals(
            "$base/downloads/.metadata/ArtistBannerImage/Avril%20Lavigne.banner.jpg?v=1758700000123",
            art.bannerUrl(base),
        )
    }

    @Test
    fun `no banner saved means none`() {
        val art = NetworkJson.decodeFromString<ArtistArtDto>(
            """{"photo_url":null,"photo_version":null,"banner_url":null,"banner_version":null}""",
        )
        assertNull(art.bannerUrl(base))
    }
}
