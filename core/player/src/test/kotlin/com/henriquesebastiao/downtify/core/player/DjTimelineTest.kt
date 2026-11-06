package com.henriquesebastiao.downtify.core.player

import com.henriquesebastiao.downtify.core.network.dto.MusicAiTrackDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjMarkerDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DjTimelineTest {
    private val id = "a".repeat(32)
    private val session = MusicDjSessionDto(
        id = id,
        mode = "interactive",
        state = "playing",
        theme = "fete",
        tracks = listOf(MusicAiTrackDto("one", "First", "Artist A"), MusicAiTrackDto("two", "Next", "Artist B")),
        timeline = listOf(MusicDjMarkerDto("one", 0.0, 100.0), MusicDjMarkerDto("two", 90.0, 200.0)),
    )

    @Test fun `metadata keeps the same stream and media identity`() {
        val item = DjMediaItems.from(id, "Set", 0, "interactive")
        val updated = DjTimeline.metadata(item, session, 95000, "https://fixture.test")
        assertEquals(item.localConfiguration, updated.localConfiguration)
        assertEquals(item.mediaId, updated.mediaId)
        val dj = requireNotNull(DjMediaItems.of(updated))
        assertEquals("First", dj.title)
        assertEquals("Artist A", dj.artist)
        assertEquals("Next", dj.nextTitle)
        assertEquals(0.5f, DjTimeline.shown(dj, 95000).transition)
        assertNull(DjTimeline.shown(dj, 89000).transition)
    }

    @Test fun `next title becomes current at the actual end of the overlap`() {
        val item = DjMediaItems.from(id, "Set", 0, "interactive")
        val updated = DjTimeline.metadata(item, session, 100000, "https://fixture.test")
        val dj = requireNotNull(DjMediaItems.of(updated))
        assertEquals("Next", dj.title)
        assertEquals(90000L, dj.start)
        assertEquals("", dj.nextTitle)
    }
}
