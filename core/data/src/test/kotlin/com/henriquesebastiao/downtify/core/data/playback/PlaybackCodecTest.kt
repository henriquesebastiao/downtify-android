package com.henriquesebastiao.downtify.core.data.playback

import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.PlaybackSnapshot
import com.henriquesebastiao.downtify.core.model.SavedPlaybackItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackCodecTest {
    private val state = PlaybackSnapshot(
        listOf(
            SavedPlaybackItem("one", "Été\n\"Song\"", "Artist", hasCover = true),
            SavedPlaybackItem("one", "Été\n\"Song\"", "Artist", queued = true),
            SavedPlaybackItem(
                "stream:abcdefghijk",
                "Stream",
                "Artist",
                videoId = "abcdefghijk",
                rawSong = "{\"opaque\":true}",
            ),
            SavedPlaybackItem(
                "episode:3",
                "Episode",
                "Show",
                episodeId = 3,
                showId = 2,
                episodeFile = "Podcasts/a.mp3",
            ),
        ),
        index = 2,
        positionMs = 12_345,
        shuffle = true,
        shuffleOrder = listOf(2, 0, 3, 1),
        repeatMode = 2,
        speed = 1.5f,
        context = PlaybackContext(PlaybackContextType.entries.first(), "ref", "Saved context"),
    )

    @Test
    fun `round trip keeps mixed media and duplicate occurrences`() {
        assertEquals(state, PlaybackCodec.decode(PlaybackCodec.encode("server-device", state)))
    }

    @Test
    fun `corrupt queue data is ignored`() {
        val row = PlaybackCodec.encode("owner", state)
        assertNull(PlaybackCodec.decode(row.copy(items = "not JSON")))
        assertNull(PlaybackCodec.decode(row.copy(items = "[{}]")))
        assertNull(PlaybackCodec.decode(row.copy(items = "[42]")))
    }
}
