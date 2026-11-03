package com.henriquesebastiao.downtify.core.network

import com.henriquesebastiao.downtify.core.model.ServerInfo
import com.henriquesebastiao.downtify.core.network.dto.ActivityTrackDto
import com.henriquesebastiao.downtify.core.network.dto.LibraryPageDto
import com.henriquesebastiao.downtify.core.network.dto.PairResponse
import com.henriquesebastiao.downtify.core.network.dto.PlaybackActivityRequest
import com.henriquesebastiao.downtify.core.network.dto.ServerInfoDto
import com.henriquesebastiao.downtify.core.network.live.LiveEvent
import com.henriquesebastiao.downtify.core.network.live.LiveUpdatesClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The contract's own examples parse into what the app expects. */
class ContractParsingTest {

    @Test
    fun `pair response names the user the device belongs to`() {
        val response = NetworkJson.decodeFromString<PairResponse>(
            """
            {"token":"dtfy_x","device":{"id":"8uz4","name":"Pixel 8"},
             "server":{"server_id":"cf12","name":"nas"},"user":{"username":"maria","role":"user"}}
            """.trimIndent(),
        )
        assertEquals("maria", response.user?.username)
        // Servers before 3.2 send no user.
        assertNull(NetworkJson.decodeFromString<PairResponse>("""{"token":"t","device":{}}""").user)
    }

    @Test
    fun `a stopped activity report sends an empty track`() {
        val stopped = NetworkJson.encodeToString(
            PlaybackActivityRequest.serializer(),
            PlaybackActivityRequest("p1", "stopped"),
        )
        assertEquals("""{"player":"p1","state":"stopped","track":{},"position":0}""", stopped)
        val playing = NetworkJson.encodeToString(
            PlaybackActivityRequest.serializer(),
            PlaybackActivityRequest(
                "p1",
                "playing",
                ActivityTrackDto(trackId = "t7b2", title = "Roads", artist = "Portishead", duration = 305),
                42,
            ),
        )
        assertEquals(
            """{"player":"p1","state":"playing","track":{"track_id":"t7b2","title":"Roads",""" +
                """"artist":"Portishead","duration":305},"position":42}""",
            playing,
        )
        // A podcast episode has no track id: it's named by its file.
        val episode = NetworkJson.encodeToString(
            PlaybackActivityRequest.serializer(),
            PlaybackActivityRequest(
                "p1",
                "playing",
                ActivityTrackDto(file = "Podcasts/Radiolab/e.mp3", title = "Zoozve", artist = "Radiolab"),
                7,
            ),
        )
        assertEquals(
            """{"player":"p1","state":"playing","track":{"file":"Podcasts/Radiolab/e.mp3",""" +
                """"title":"Zoozve","artist":"Radiolab"},"position":7}""",
            episode,
        )
    }

    @Test
    fun `server info`() {
        val info = NetworkJson.decodeFromString<ServerInfoDto>(
            """
            {"server_id":"cf12","name":"nas","product":"Downtify","version":"3.2.0","api_version":1,
             "require_sign_in":true,"capabilities":{"transcoding":{"available":true,"formats":["aac","mp3","opus"],
             "bitrates":[96,128,160,192,256,320]},"signed_urls":true,"pairing":true,"podcasts":true,"discover":true,
             "lyrics":true,"library_sync":true,"password_sign_in":true,"something_new":{"x":1}}}
            """.trimIndent(),
        ).toModel()
        assertEquals("cf12", info.serverId)
        assertEquals(ServerInfo.Compatibility.Ok, info.compatibility)
        assertTrue(info.capabilities.transcoding.available)
        assertEquals(listOf("aac", "mp3", "opus"), info.capabilities.transcoding.formats)
        assertTrue(info.capabilities.passwordSignIn)
        // Servers from before app sign-in don't say so: they only pair.
        val older = NetworkJson.decodeFromString<ServerInfoDto>("""{"capabilities":{"pairing":true}}""").toModel()
        assertFalse(older.capabilities.passwordSignIn)
    }

    @Test
    fun `refuses newer api versions and other products`() {
        val newer = NetworkJson.decodeFromString<ServerInfoDto>("""{"product":"Downtify","api_version":2}""").toModel()
        assertEquals(ServerInfo.Compatibility.TooNew, newer.compatibility)
        val other = NetworkJson.decodeFromString<ServerInfoDto>("""{"product":"Jellyfin","api_version":1}""").toModel()
        assertEquals(ServerInfo.Compatibility.NotDowntify, other.compatibility)
    }

    @Test
    fun `library page`() {
        val page = NetworkJson.decodeFromString<LibraryPageDto>(
            """
            {"cursor":1742,"full":false,"tracks":[{"id":"t7b2","file":"Portishead - Roads.flac","title":"Roads",
             "artist":"Portishead","artists":["Portishead"],"album":"Dummy","album_artist":"Portishead",
             "album_id":"6aa1","artist_id":"0a92","track_number":3,"year":"1994","duration":305.12,"codec":"flac",
             "bitrate":912000,"sample_rate":44100,"channels":2,"size":34812211,"added":1789600669,"has_cover":true,
             "playlists":["Trip-hop"]}],"deleted":["t0c4"]}
            """.trimIndent(),
        ).toModel()
        assertEquals(1742L, page.cursor)
        val track = page.tracks.single()
        assertEquals("6aa1", track.albumId)
        assertEquals(305.12, track.duration, 0.0)
        assertEquals(listOf("t0c4"), page.deleted)
    }

    @Test
    fun `websocket messages`() {
        assertEquals(LiveEvent.LibraryChanged, LiveUpdatesClient.parse("""{"type":"library_changed"}"""))
        assertEquals(LiveEvent.LikesChanged, LiveUpdatesClient.parse("""{"type":"likes","count":3}"""))
        assertNull(LiveUpdatesClient.parse("""{"song":{},"progress":42.5,"status":"downloading"}"""))
        assertNull(LiveUpdatesClient.parse("not json"))
    }
}
