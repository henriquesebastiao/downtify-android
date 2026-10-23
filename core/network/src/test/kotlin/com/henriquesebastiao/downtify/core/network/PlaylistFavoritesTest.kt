package com.henriquesebastiao.downtify.core.network

import com.henriquesebastiao.downtify.core.network.dto.PlaylistDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistFavoritesTest {
    @Test
    fun olderServerKeepsCanonicalNameAndDoesNotOfferUnsupportedAction() {
        val playlist = Json.decodeFromString<PlaylistDto>("""{"name":"Source playlist"}""").toModel()
        assertEquals("Source playlist", playlist.title)
        assertFalse(playlist.favorite)
        assertFalse(playlist.favoriteSupported)
    }

    @Test
    fun aliasNeverChangesPlaylistIdentityOrTrackOrder() {
        val playlist = Json.decodeFromString<PlaylistDto>(
            """{"name":"Source playlist","display_name":"Road trip","favorite":true,"track_ids":["two","one"]}""",
        ).toModel()
        assertEquals("Source playlist", playlist.name)
        assertEquals("Road trip", playlist.title)
        assertEquals(listOf("two", "one"), playlist.trackIds)
        assertTrue(playlist.favorite)
        assertTrue(playlist.favoriteSupported)
    }
}
