package com.henriquesebastiao.downtify.core.player

import com.henriquesebastiao.downtify.core.network.dto.MusicAiTrackDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjMarkerDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

    @Test fun `returning to normal chooses the dominant deck and its original source position`() {
        val value = session.copy(
            timeline = listOf(
                MusicDjMarkerDto("one", 0.0, 100.0, cue = 10.0),
                MusicDjMarkerDto("two", 90.0, 200.0, cue = 5.0, speed = 1.02),
            ),
        )
        val item = DjTimeline.metadata(
            DjMediaItems.from(id, "Set", 0, "interactive"),
            value,
            97000,
            "https://fixture.test",
        )
        val point = requireNotNull(DjTimeline.resumePoint(item, 97000))
        assertEquals("two", point.id)
        assertEquals(12140L, point.sourceMs)
        assertEquals(1, point.occurrence)
        assertEquals("one", DjTimeline.resumePoint(item, 93000)?.id)
    }

    @Test fun `unchanged metadata reuses the same media item instead of rebuilding the player timeline`() {
        val item = DjTimeline.metadata(
            DjMediaItems.from(id, "Set", 0, "interactive"),
            session,
            20000,
            "https://fixture.test",
        )
        assertSame(item, DjTimeline.metadata(item, session, 22000, "https://fixture.test"))
    }
}
