package com.henriquesebastiao.downtify.core.player

import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalDjOutgoingStallTest {
    private val first = LocalDjFakeDeck()
    private val second = LocalDjFakeDeck()
    private val engine = LocalDjEngine(first, second) {}

    private fun begin() {
        first.setMediaItems(listOf(localDjItem("a"), localDjItem("b"), localDjItem("c")))
        first.prepare()
        first.play()
        engine.setEnabled(true)
        engine.pulse()
        first.position(8800)
        engine.pulse()
        first.position(9100)
        second.position(300)
        engine.pulse()
        assertEquals(0.25f, engine.transition.value.progress, 0.001f)
    }

    private fun playbackState(deck: LocalDjFakeDeck, state: Int) {
        var observed = false
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == state) observed = true
            }
        }
        deck.addListener(listener)
        // Preserve the shared fixture and exercise SimpleBasePlayer's actual event dispatch.
        LocalDjFakeDeck::class.java.getDeclaredField("ready").apply {
            isAccessible = true
            setInt(deck, state)
        }
        deck.position(deck.currentPosition)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Expected a real playback-state event", observed)
        deck.removeListener(listener)
    }

    @Test fun `outgoing buffering pauses incoming until playback can advance again`() {
        begin()
        playbackState(first, Player.STATE_BUFFERING)
        engine.pulse()
        assertFalse(first.isPlaying)
        assertFalse(second.playWhenReady)
        assertEquals(300L, second.currentPosition)
        assertEquals(0.25f, engine.transition.value.progress, 0.001f)
        playbackState(first, Player.STATE_READY)
        engine.pulse()
        assertTrue(first.playWhenReady)
        assertTrue(second.playWhenReady)
        assertEquals(0.25f, engine.transition.value.progress, 0.001f)
    }

    @Test fun `manual pause prevents an automatic restart after outgoing recovery`() {
        begin()
        playbackState(first, Player.STATE_BUFFERING)
        engine.pulse()
        engine.setPlaying(false)
        playbackState(first, Player.STATE_READY)
        repeat(3) { engine.pulse() }
        assertFalse(first.playWhenReady)
        assertFalse(second.playWhenReady)
        engine.setPlaying(true)
        assertTrue(first.playWhenReady)
        assertTrue(second.playWhenReady)
    }

    @Test fun `manual resume during buffering does not start the incoming decoder early`() {
        begin()
        playbackState(first, Player.STATE_BUFFERING)
        engine.pulse()
        engine.setPlaying(false)
        engine.setPlaying(true)
        assertTrue(first.playWhenReady)
        assertFalse(second.playWhenReady)
        engine.pulse()
        assertFalse(second.playWhenReady)
        playbackState(first, Player.STATE_READY)
        engine.pulse()
        assertTrue(second.playWhenReady)
    }

    @Test fun `disable while buffering fences late readiness and keeps the original position`() {
        begin()
        playbackState(first, Player.STATE_BUFFERING)
        engine.pulse()
        engine.setEnabled(false)
        playbackState(first, Player.STATE_READY)
        repeat(3) { engine.pulse() }
        assertEquals(LocalDjPhase.Disabled, engine.transition.value.phase)
        assertSame(first, engine.active)
        assertEquals(9100L, first.currentPosition)
        assertEquals(0, second.mediaItemCount)
        assertFalse(second.playWhenReady)
    }

    @Test fun `outgoing error promotes an already ready incoming deck once`() {
        begin()
        playbackState(first, Player.STATE_BUFFERING)
        engine.pulse()
        first.fail()
        shadowOf(Looper.getMainLooper()).idle()
        engine.pulse()
        assertSame(second, engine.active)
        assertEquals(1, second.currentMediaItemIndex)
        assertEquals(300L, second.currentPosition)
        assertEquals(3, second.mediaItemCount)
        assertTrue(second.playWhenReady)
        assertEquals(1f, second.volume, 0.001f)
        assertEquals(0, first.mediaItemCount)
        assertEquals(LocalDjFallback.DecoderFailure, engine.transition.value.fallback)
    }

    @Test fun `outgoing error with no ready replacement stops both without retrying`() {
        begin()
        playbackState(second, Player.STATE_BUFFERING)
        first.fail()
        engine.pulse()
        assertFalse(first.playWhenReady)
        assertFalse(second.playWhenReady)
        assertSame(first, engine.active)
        assertEquals(LocalDjFallback.DecoderFailure, engine.transition.value.fallback)
        repeat(3) { engine.pulse() }
        assertFalse(first.playWhenReady)
        assertFalse(second.playWhenReady)
    }

    @Test fun `outgoing error cannot override a user pause or later disable`() {
        begin()
        engine.setPlaying(false)
        first.fail()
        repeat(3) { engine.pulse() }
        assertFalse(first.playWhenReady)
        assertFalse(second.playWhenReady)
        engine.setEnabled(false)
        repeat(3) { engine.pulse() }
        assertFalse(first.playWhenReady)
        assertFalse(second.playWhenReady)
    }
}
