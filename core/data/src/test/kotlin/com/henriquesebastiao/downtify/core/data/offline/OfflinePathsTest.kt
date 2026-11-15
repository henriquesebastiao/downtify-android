package com.henriquesebastiao.downtify.core.data.offline

import com.henriquesebastiao.downtify.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePathsTest {
    @Test
    fun `audio path is readable and keeps the source extension`() {
        val path = OfflinePaths.audio(track(id = "track/1", title = "Ранняя песня", trackNumber = 7))

        assertTrue(path.startsWith("Music/Artist A/Album A/07 - Ранняя песня ["))
        assertTrue(path.endsWith("].flac"))
        assertFalse(path.contains("track/1"))
    }

    @Test
    fun `path components cannot escape their folders`() {
        val path = OfflinePaths.audio(
            track(
                id = "../../outside",
                artist = "../Artist\\Name",
                album = "..\\Album/Name",
                title = "../../Song\r\nInjected",
                file = "song.MP3",
            ),
        )

        assertFalse(path.contains(".."))
        assertFalse(path.contains('\\'))
        assertFalse(path.contains('\r'))
        assertFalse(path.contains('\n'))
        assertTrue(path.endsWith("].mp3"))
    }

    @Test
    fun `duplicate titles in one album get different paths`() {
        val first = OfflinePaths.audio(track(id = "first"))
        val second = OfflinePaths.audio(track(id = "second"))

        assertNotEquals(first, second)
    }

    @Test
    fun `long names are bounded without losing uniqueness`() {
        val longName = "я".repeat(300)
        val first = OfflinePaths.audio(track(id = "first", title = longName))
        val second = OfflinePaths.audio(track(id = "second", title = longName))

        assertTrue(first.length <= MAX_EXPECTED_PATH)
        assertTrue(second.length <= MAX_EXPECTED_PATH)
        assertNotEquals(first, second)
    }

    @Test
    fun `playlist names that sanitize alike do not collide`() {
        val first = OfflinePaths.playlist("Rock/2026", "playlist-one")
        val second = OfflinePaths.playlist("Rock\\2026", "playlist-two")

        assertNotEquals(first, second)
        assertTrue(first.startsWith("Playlists/Rock_2026 ["))
        assertTrue(first.endsWith("].m3u"))
    }

    private fun track(
        id: String,
        title: String = "Song",
        artist: String = "Artist A",
        album: String = "Album A",
        trackNumber: Int = 1,
        file: String = "song.flac",
    ) = Track(
        id = id,
        file = file,
        title = title,
        artist = artist,
        artists = listOf(artist),
        album = album,
        albumArtist = artist,
        albumId = "album",
        artistId = "artist",
        trackNumber = trackNumber,
        year = "2026",
        duration = 123.4,
        codec = "flac",
        bitrate = 1_000_000,
        sampleRate = 44_100,
        channels = 2,
        size = 100,
        added = 0,
        hasCover = false,
        playlists = emptyList(),
    )

    private companion object {
        const val MAX_EXPECTED_PATH = 330
    }
}
