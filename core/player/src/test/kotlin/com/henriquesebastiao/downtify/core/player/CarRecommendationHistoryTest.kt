package com.henriquesebastiao.downtify.core.player

import com.henriquesebastiao.downtify.core.data.playback.playbackOwner
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.network.session.Session
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CarRecommendationHistoryTest {
    @Test fun `qualified playlist listens persist per association and reject a retired owner`() {
        val store = mockk<SessionStore>()
        var session = Session("https://fixture.test", "server", "Test", "first", "fixture")
        every { store.current } answers { session }
        val history = CarRecommendationHistory(RuntimeEnvironment.getApplication(), store)
        val playlist = PlaybackContext(PlaybackContextType.Playlist, "Road", "Road")
        val firstOwner = session.playbackOwner
        history.record(playlist, firstOwner)
        history.record(playlist, firstOwner)
        assertEquals(2L, history.counts(listOf("Road"))["Road"])
        session = session.copy(deviceId = "second")
        assertEquals(0L, history.counts(listOf("Road"))["Road"])
        history.record(playlist, firstOwner)
        assertEquals(0L, history.counts(listOf("Road"))["Road"])
        history.record(playlist, session.playbackOwner)
        assertEquals(1L, history.counts(listOf("Road"))["Road"])
    }

    @Test fun `browsing and nonplaylist contexts do not count as playlist listening`() {
        val store = mockk<SessionStore>()
        val session = Session("https://fixture.test", "server", "Test", "device", "fixture")
        every { store.current } returns session
        val history = CarRecommendationHistory(RuntimeEnvironment.getApplication(), store)
        history.record(null, session.playbackOwner)
        history.record(PlaybackContext(PlaybackContextType.Album, "Album", "Album"), session.playbackOwner)
        assertEquals(0L, history.counts(listOf("Album"))["Album"])
    }
}
