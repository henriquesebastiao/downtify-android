package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalDjEngineTest {
    private val first = LocalDjFakeDeck()
    private val second = LocalDjFakeDeck()
    private var dominant: Player = first
    private val engine = LocalDjEngine(first, second) { dominant = it }

    private fun begin(ids: List<String> = listOf("a", "b", "c")) {
        first.setMediaItems(ids.map(::localDjItem))
        first.prepare()
        first.play()
        engine.setEnabled(true)
        engine.pulse()
    }

    @Test fun `duplicate titles preserve the occurrence index at dominance`() {
        begin(listOf("a", "a", "b"))
        first.position(8800)
        engine.pulse()
        first.position(9400)
        second.position(600)
        engine.pulse()
        assertSame(second, dominant)
        assertEquals(1, dominant.currentMediaItemIndex)
        assertEquals(3, dominant.mediaItemCount)
        assertEquals("a", dominant.currentMediaItem?.mediaId)
        assertEquals(0.5f, engine.transition.value.progress, 0.001f)
    }

    @Test fun `pause freezes both decks and only an explicit resume restarts them`() {
        begin()
        first.position(8800)
        engine.pulse()
        first.position(9100)
        engine.pulse()
        engine.setPlaying(false)
        repeat(10) { engine.pulse() }
        assertFalse(first.playWhenReady)
        assertFalse(second.playWhenReady)
        assertEquals(0.25f, engine.transition.value.progress, 0.001f)
        engine.setPlaying(true)
        assertTrue(first.playWhenReady)
        assertTrue(second.playWhenReady)
    }

    @Test fun `disabling keeps the dominant original at its source position`() {
        begin()
        first.position(8800)
        engine.pulse()
        first.position(9500)
        second.position(700)
        engine.pulse()
        engine.setEnabled(false)
        assertSame(second, engine.active)
        assertEquals(700L, engine.active.currentPosition)
        assertTrue(engine.active.playWhenReady)
        assertEquals(0, first.mediaItemCount)
        assertEquals(1f, second.volume, 0.001f)
        assertEquals(LocalDjPhase.Disabled, engine.transition.value.phase)
    }

    @Test fun `a missing incoming buffer never fades the audible source`() {
        begin()
        second.buffered(200)
        first.position(8800)
        engine.pulse()
        assertEquals(LocalDjFallback.NotBuffered, engine.transition.value.fallback)
        assertEquals(1f, first.volume, 0.001f)
        assertFalse(second.playWhenReady)
        assertSame(first, engine.active)
    }

    @Test fun `an incoming decoder error does not take over normal playback`() {
        begin()
        second.fail()
        first.position(8800)
        engine.pulse()
        assertSame(first, engine.active)
        assertTrue(first.playWhenReady)
        assertEquals(1f, first.volume, 0.001f)
        assertEquals(LocalDjFallback.DecoderFailure, engine.transition.value.fallback)
    }

    @Test fun `late readiness after disable cannot restart a retired deck`() {
        begin()
        engine.setEnabled(false)
        second.setMediaItems(listOf(localDjItem("retired")))
        second.prepare()
        first.position(9900)
        repeat(3) { engine.pulse() }
        assertFalse(second.playWhenReady)
        assertSame(first, engine.active)
        assertEquals(LocalDjPhase.Disabled, engine.transition.value.phase)
    }

    @Test fun `a native end between pulses still finishes the handover once`() {
        begin()
        first.position(8800)
        engine.pulse()
        first.nativeIndex(1)
        second.position(1200)
        engine.pulse()
        assertSame(second, engine.active)
        assertEquals(1200L, second.currentPosition)
        assertEquals(0, first.mediaItemCount)
        assertTrue(second.playWhenReady)
    }

    @Test fun `remote streams shuffle and repeat one keep native playback`() {
        begin()
        engine.reset()
        first.setMediaItems(listOf(localDjItem("a"), MediaItem.fromUri("https://fixture.test/live.m3u8")))
        engine.pulse()
        assertEquals(0, second.mediaItemCount)
        first.setMediaItems(listOf(localDjItem("a"), localDjItem("b")))
        first.repeatMode = Player.REPEAT_MODE_ONE
        engine.pulse()
        assertEquals(0, second.mediaItemCount)
        first.repeatMode = Player.REPEAT_MODE_OFF
        first.shuffleModeEnabled = true
        engine.pulse()
        assertEquals(0, second.mediaItemCount)
        assertTrue(first.playWhenReady)
    }

    @Test fun `ducking and user volume remain independent during overlap`() {
        begin()
        first.position(8800)
        engine.pulse()
        first.position(9400)
        engine.pulse()
        engine.setVolume(0.8f)
        engine.setFocusGain(0.2f)
        assertEquals(0.8f, engine.volume, 0.001f)
        assertEquals(0.08f, first.volume, 0.001f)
        assertEquals(0.08f, second.volume, 0.001f)
        engine.setFocusGain(1f)
        assertEquals(0.4f, first.volume, 0.001f)
    }
}
