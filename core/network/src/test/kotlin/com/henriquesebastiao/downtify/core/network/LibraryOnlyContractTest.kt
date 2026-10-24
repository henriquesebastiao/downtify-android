package com.henriquesebastiao.downtify.core.network

import com.henriquesebastiao.downtify.core.network.dto.ServerInfoDto
import com.henriquesebastiao.downtify.core.network.live.LiveEvent
import com.henriquesebastiao.downtify.core.network.live.LiveUpdatesClient
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryOnlyContractTest {
    @Test
    fun oldServerDefaultsToOnline() {
        val dto = NetworkJson.decodeFromString<ServerInfoDto>("""{"server_id":"old"}""")
        assertFalse(dto.toModel().libraryOnly)
    }

    @Test
    fun libraryPolicySurvivesPersistentInfoRoundTrip() {
        val dto = NetworkJson.decodeFromString<ServerInfoDto>("""{"server_id":"server","library_only":true}""")
        val restored = ServerInfoDto.from(dto.toModel()).toModel()
        assertTrue(restored.libraryOnly)
        assertEquals("server", restored.serverId)
    }

    @Test
    fun livePolicyChangeIsRecognized() {
        assertEquals(
            LiveEvent.MusicPolicyChanged,
            LiveUpdatesClient.parse("""{"type":"music_policy_changed","library_only":true}"""),
        )
    }

    @Test
    fun libraryResultKeepsFileIdentityAndHasNoExternalPreview() {
        val json = NetworkJson.parseToJsonElement(
            """{"song_id":"library:a.flac","name":"Title","source":"library","file":"a.flac"}""",
        ) as JsonObject
        val song = requireNotNull(CatalogJson.song(json))
        assertEquals("a.flac", song.libraryFile)
        assertTrue(song.previewUrl.isEmpty())
        assertTrue(song.url.isEmpty())
    }

    @Test
    fun remoteResultCannotClaimLibraryFile() {
        val json = NetworkJson.parseToJsonElement(
            """{"song_id":"remote","source":"youtube","file":"a.flac"}""",
        ) as JsonObject
        assertTrue(requireNotNull(CatalogJson.song(json)).libraryFile.isEmpty())
    }
}
