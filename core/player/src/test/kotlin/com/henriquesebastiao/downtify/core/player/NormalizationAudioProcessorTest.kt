package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NormalizationAudioProcessorTest {
    private val gains = mutableMapOf("one" to -6.020599913, "two" to 6.020599913)
    private var enabled = true
    private val processor = NormalizationAudioProcessor({ gains[it] ?: 0.0 }, { enabled })

    private fun metadata(id: String): AudioProcessor.StreamMetadata {
        val timeline = object : Timeline() {
            override fun getWindowCount() = 1
            override fun getPeriodCount() = 1
            override fun getIndexOfPeriod(uid: Any) = if (uid == "period") 0 else C.INDEX_UNSET
            override fun getUidOfPeriod(index: Int): Any = "period"
            override fun getWindow(index: Int, window: Window, projectionUs: Long): Window = window.apply {
                mediaItem = MediaItem.Builder().setMediaId(id).build()
            }
            override fun getPeriod(index: Int, period: Period, setIds: Boolean): Period =
                period.apply { windowIndex = 0 }
        }
        return AudioProcessor.StreamMetadata.Builder().setTimeline(timeline).setPeriodUid("period").build()
    }

    private fun start(id: String) {
        processor.configure(AudioProcessor.AudioFormat(48000, 1, C.ENCODING_PCM_16BIT))
        processor.flush(metadata(id))
    }

    private fun process(vararg samples: Int): IntArray {
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { input.putShort(it.toShort()) }
        input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        return IntArray(output.remaining() / 2) { output.short.toInt() }
    }

    @Test
    fun gainIsAppliedBeforeTheFirstSampleAndUsesTheActualStream() {
        start("one")
        assertArrayEquals(intArrayOf(500, -500), process(1000, -1000))
        start("two")
        assertArrayEquals(intArrayOf(2000, -2000), process(1000, -1000))
    }

    @Test
    fun prefetchingAnotherProfileDoesNotChangeTheCurrentStreamGain() {
        start("one")
        gains["one"] = -40.0
        gains["two"] = 12.0
        assertArrayEquals(intArrayOf(500), process(1000))
        start("two")
        assertEquals(3981, process(1000).single())
    }

    @Test
    fun unknownStreamAndDisabledNormalizationPreserveSamples() {
        start("unknown")
        assertArrayEquals(intArrayOf(1234, -2345), process(1234, -2345))
        enabled = false
        start("two")
        assertArrayEquals(intArrayOf(1234, -2345), process(1234, -2345))
    }

    @Test
    fun invalidGainNeverProducesInvalidSamplesAndLargeInputCannotWrap() {
        gains["one"] = Double.NaN
        start("one")
        assertEquals(1000, process(1000).single())
        gains["two"] = 12.0
        start("two")
        assertArrayEquals(intArrayOf(32767, -32768), process(30000, -30000))
    }
}
