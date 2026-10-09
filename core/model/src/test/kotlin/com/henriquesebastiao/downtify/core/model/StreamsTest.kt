package com.henriquesebastiao.downtify.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamIdsTest {
    @Test
    fun roundTripsAVideoId() {
        val id = StreamIds.mediaId("dQw4w9WgXcQ")
        assertTrue(StreamIds.isStream(id))
        assertEquals("dQw4w9WgXcQ", StreamIds.videoIdOf(id))
    }

    @Test
    fun ignoresLibraryAndEpisodeIds() {
        assertFalse(StreamIds.isStream("t7b2trackid"))
        assertFalse(StreamIds.isStream("episode:12"))
        assertFalse(StreamIds.isStream(null))
        assertFalse(StreamIds.isStream(""))
        assertNull(StreamIds.videoIdOf("t7b2trackid"))
        assertNull(StreamIds.videoIdOf(null))
    }
}

class StreamVideoTest {
    private fun song(id: String, url: String = "") =
        RemoteSong(id, "Believe", listOf("Cher"), "Believe", "", 224, url, CatalogSource.YouTube, "", "1998", "{}")

    @Test
    fun takesAnElevenCharSongIdAsIs() {
        assertEquals("dQw4w9WgXcQ", StreamVideo.videoIdOf(song("dQw4w9WgXcQ")))
    }

    @Test
    fun readsTheVParamOfAWatchLink() {
        assertEquals(
            "dQw4w9WgXcQ",
            StreamVideo.videoIdOf(song("", "https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=abc")),
        )
    }

    @Test
    fun asksForASearchOtherwise() {
        assertNull(StreamVideo.videoIdOf(song("spotify:track:abc")))
        assertNull(StreamVideo.videoIdOf(song("", "https://open.spotify.com/track/abc")))
    }

    @Test
    fun buildsTheServerFileAddress() {
        assertEquals(
            "https://srv:8000/api/stream/file?video_id=dQw4w9WgXcQ",
            StreamVideo.fileUrl("https://srv:8000/", "dQw4w9WgXcQ"),
        )
    }
}
