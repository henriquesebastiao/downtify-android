package com.henriquesebastiao.downtify.core.network

import com.henriquesebastiao.downtify.core.model.CatalogSource
import com.henriquesebastiao.downtify.core.model.LinkKind
import com.henriquesebastiao.downtify.core.model.RemoteSong
import com.henriquesebastiao.downtify.core.model.ServerJobStatus
import com.henriquesebastiao.downtify.core.network.live.LiveEvent
import com.henriquesebastiao.downtify.core.network.live.LiveUpdatesClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The server's song, album and queue objects, as its providers build them. */
class CatalogJsonTest {
    private fun obj(json: String) = NetworkJson.parseToJsonElement(json) as JsonObject

    private val ytSong = """
        {"song_id":"dQw4w9WgXcQ","name":"Harbor Song","artists":["Nora Vale"],"album_name":"Harbor Nights",
         "cover_url":"https://lh3/x","duration":242,"url":"https://music.youtube.com/watch?v=dQw4w9WgXcQ",
         "explicit":false,"year":"2021","release_date":"2021","source":"youtube","youtube_id":"dQw4w9WgXcQ"}
    """.trimIndent()

    @Test
    fun readsAYouTubeMusicSong() {
        val song = CatalogJson.song(obj(ytSong))!!
        assertEquals("dQw4w9WgXcQ", song.id)
        assertEquals("Harbor Song", song.title)
        assertEquals("Nora Vale", song.artist)
        assertEquals("Harbor Nights", song.album)
        assertEquals(242, song.durationSeconds)
        assertEquals(CatalogSource.YouTube, song.source)
        assertEquals("", song.previewUrl)
    }

    @Test
    fun readsASpotifySongWithItsPreview() {
        val song = CatalogJson.song(
            obj(
                """{"song_id":"4uLU","name":"Safe Harbor","artists":["Lumen Field","Orsa"],"album_name":"Tides",
                "duration":227.4,"url":"https://open.spotify.com/track/4uLU","source":"spotify",
                "preview_url":"https://p.scdn.co/mp3-preview/abc"}""",
            ),
        )!!
        assertEquals("Lumen Field, Orsa", song.artist)
        assertEquals(227, song.durationSeconds)
        assertEquals("https://p.scdn.co/mp3-preview/abc", song.previewUrl)
        assertEquals(CatalogSource.Spotify, song.source)
    }

    @Test
    fun oddFieldsBecomeDefaults() {
        val song = CatalogJson.song(obj("""{"url":"https://x","name":null,"artist":"A, B","duration":"n/a"}"""))!!
        assertEquals("https://x", song.id)
        assertEquals("", song.title)
        assertEquals(listOf("A", "B"), song.artists)
        assertEquals(0, song.durationSeconds)
        assertNull(CatalogJson.song(obj("""{"name":"no id or url"}""")))
    }

    @Test
    fun theBatchSendsTheSongsBackUnchanged() {
        val song = CatalogJson.song(obj(ytSong))!!
        val body = CatalogJson.batch(listOf(song), playlistUrl = null)
        val sent = body["songs"]!!.jsonArray.single().jsonObject
        assertEquals(obj(ytSong), sent)
        assertEquals("dQw4w9WgXcQ", sent["youtube_id"]!!.jsonPrimitive.content)
        assertEquals("false", body["generate_m3u"]!!.jsonPrimitive.content)
        assertTrue("playlist_url" !in body)
        val playlist = CatalogJson.batch(listOf(song), playlistUrl = "https://open.spotify.com/playlist/1")
        assertEquals("true", playlist["generate_m3u"]!!.jsonPrimitive.content)
    }

    @Test
    fun readsAlbumsAndResolvedLinks() {
        val albums = CatalogJson.albums(
            NetworkJson.parseToJsonElement(
                """[{"album_id":"MPREb_1","name":"Harbor Nights","artists":["Nora Vale"],"artist":"Nora Vale",
                "cover_url":"c","year":"2021","url":"https://music.youtube.com/browse/MPREb_1","release_type":"Album"},
                {"name":"no url"}]""",
            ) as JsonArray,
        )
        assertEquals(1, albums.size)
        assertEquals("Nora Vale", albums.single().artist)

        val link = CatalogJson.resolved(
            "https://open.spotify.com/album/1",
            obj(
                """{"kind":"album","name":"Tides","subtitle":"Lumen Field","cover_url":"c","year":"2020",""" +
                    """"tracks":[$ytSong],"albums":[]}""",
            ),
        )
        assertEquals(LinkKind.Album, link.kind)
        assertEquals(1, link.tracks.size)
    }

    @Test
    fun readsSimilarTracks() {
        val tracks = CatalogJson.similarTracks(
            obj("""{"artist":"Cher","track":"Believe","source":"youtube","tracks":[$ytSong]}"""),
        )
        assertEquals(1, tracks.size)
        assertEquals("dQw4w9WgXcQ", tracks.single().id)
        assertEquals("Harbor Song", tracks.single().title)
        assertEquals(emptyList<RemoteSong>(), CatalogJson.similarTracks(obj("""{"tracks":[]}""")))
        assertEquals(emptyList<RemoteSong>(), CatalogJson.similarTracks(obj("""{}""")))
    }

    @Test
    fun readsQueueRowsAndProgressMessages() {
        val jobs = CatalogJson.jobs(
            NetworkJson.parseToJsonElement(
                """[{"song":$ytSong,"status":"downloading","progress":42.5,"message":"","provider":"youtube-music"}]""",
            ) as JsonArray,
        )
        assertEquals(ServerJobStatus.Downloading, jobs.single().status)
        assertEquals(42.5f, jobs.single().progress)

        val event = LiveUpdatesClient.parse("""{"song":$ytSong,"progress":100,"status":"done","filename":"x.mp3"}""")
        assertTrue(event is LiveEvent.DownloadProgress && event.job.status == ServerJobStatus.Done)
        assertEquals(LiveEvent.QueueReload, LiveUpdatesClient.parse("""{"type":"queue_reload"}"""))
        assertNull(LiveUpdatesClient.parse("""{"type":"podcasts"}"""))
    }
}
