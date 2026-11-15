package com.henriquesebastiao.downtify.core.data.offline

import com.henriquesebastiao.downtify.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistExporterTest {
    @Test
    fun `m3u contains metadata and portable relative audio paths`() {
        val track = track(title = "Ранняя песня", artist = "Исполнитель", duration = 123.6)

        val rendered = PlaylistExporter.render(
            title = "Утро",
            tracks = listOf(track to "Music/Исполнитель/Альбом/01 - Ранняя песня [id].flac"),
        )

        assertEquals(
            "#EXTM3U\n" +
                "#PLAYLIST:Утро\n" +
                "#EXTINF:124,Исполнитель - Ранняя песня\n" +
                "../Music/Исполнитель/Альбом/01 - Ранняя песня [id].flac\n",
            rendered,
        )
    }

    @Test
    fun `metadata newlines cannot inject playlist entries`() {
        val rendered = PlaylistExporter.render(
            title = "First\n#EXTINF:0,Injected",
            tracks = listOf(track(title = "Song\nBad", artist = "Artist\rBad") to "Music/Song.flac"),
        )

        assertEquals(
            "#EXTM3U\n" +
                "#PLAYLIST:First #EXTINF:0,Injected\n" +
                "#EXTINF:10,Artist Bad - Song Bad\n" +
                "../Music/Song.flac\n",
            rendered,
        )
    }

    private fun track(title: String, artist: String, duration: Double = 10.0) = Track(
        id = "id",
        file = "song.flac",
        title = title,
        artist = artist,
        artists = listOf(artist),
        album = "Album",
        albumArtist = artist,
        albumId = "album",
        artistId = "artist",
        trackNumber = 1,
        year = "2026",
        duration = duration,
        codec = "flac",
        bitrate = 1_000_000,
        sampleRate = 44_100,
        channels = 2,
        size = 100,
        added = 0,
        hasCover = false,
        playlists = emptyList(),
    )
}
