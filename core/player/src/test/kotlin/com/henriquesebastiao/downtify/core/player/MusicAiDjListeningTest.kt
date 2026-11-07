package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.Player
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiListenDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiTrackDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjMarkerDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto
import com.henriquesebastiao.downtify.core.network.session.Session
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicAiDjListeningTest {
    @Test fun `only audible time is profiled and repeated DJ occurrences stay distinct`() = runTest {
        val music = mockk<MusicAiRepository>()
        val player = mockk<Player>(relaxed = true)
        val paired = MutableStateFlow<Session?>(Session("https://fixture.test", "test", "Test", "device", "fixture"))
        val caps = MutableStateFlow(MusicAiCapabilitiesDto(version = 1, features = mapOf("personalization" to true)))
        every { music.session } returns paired
        every { music.capabilities } returns caps
        val events = mutableListOf<MusicAiListenDto>()
        coEvery { music.listen(any()) } answers {
            events.add(firstArg())
            Unit
        }
        val id = "a".repeat(32)
        val session =
            MusicDjSessionDto(
                id = id,
                state = "playing",
                theme = "fete",
                tracks = listOf(MusicAiTrackDto("one")),
                timeline = listOf(MusicDjMarkerDto("one", 0.0, 100.0), MusicDjMarkerDto("one", 90.0, 190.0)),
            )
        val original = DjMediaItems.from(id, "Set", 0, "interactive")
        var item = DjTimeline.metadata(original, session, 0, "https://fixture.test")
        var position = 0L
        every { player.currentMediaItem } answers { item }
        every { player.currentPosition } answers { position }
        every { player.isPlaying } returns true
        val tracker = MusicAiListenTracker(player, music, backgroundScope)
        tracker.start()
        tracker.onMediaItemTransition(item, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        runCurrent()
        repeat(5) {
            position += 1000
            ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
            advanceTimeBy(1000)
            runCurrent()
        }
        position = 100000
        item = DjTimeline.metadata(original, session, position, "https://fixture.test")
        tracker.onMediaMetadataChanged(item.mediaMetadata)
        repeat(5) {
            position += 1000
            ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
            advanceTimeBy(1000)
            runCurrent()
        }
        tracker.stop()
        runCurrent()
        assertEquals(listOf("one", "one"), events.map { it.trackId })
        assertEquals(listOf("pause", "pause"), events.map { it.outcome })
        assertEquals(listOf(5.0, 5.0), events.map { it.seconds })
        assertNotEquals(events[0].eventId, events[1].eventId)
    }
}
