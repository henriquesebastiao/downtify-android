package com.henriquesebastiao.downtify.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverUrlsTest {
    private val covers = CoverUrls("http://nas.local:8000")

    @Test
    fun anArtistsPhotoComesFromTheServersPhotoProxyByName() {
        assertEquals(
            "http://nas.local:8000/api/artists/photo-proxy?name=AC%2FDC+%26+Friends",
            covers.artist("AC/DC & Friends"),
        )
    }

    @Test
    fun noNameOrNoServerMeansNoPhoto() {
        assertNull(covers.artist(""))
        assertNull(covers.artist(null))
        assertNull(CoverUrls.None.artist("Journey"))
    }
}
