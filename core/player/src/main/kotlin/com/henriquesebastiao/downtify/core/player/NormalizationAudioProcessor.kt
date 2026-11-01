package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.roundToInt

/** Stream metadata binds the gain to decoded audio, including prefetch and gapless transitions. */
@UnstableApi
class NormalizationAudioProcessor(
    private val gainForTrack: (String) -> Double,
    private val isEnabled: () -> Boolean = { true },
) : BaseAudioProcessor() {
    private var trackId = ""
    private var streamGainDb = 0.0
    private var multiplier = 1.0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        trackId = trackId(streamMetadata)
        streamGainDb = if (trackId.isBlank()) 0.0 else gainForTrack(trackId)
        // Set the level before the first sample; never ramp from the previous song.
        multiplier = target()
    }

    private fun trackId(metadata: AudioProcessor.StreamMetadata): String {
        val timeline = metadata.timeline ?: return ""
        val uid = metadata.periodUid ?: return ""
        val index = timeline.getIndexOfPeriod(uid)
        if (index == C.INDEX_UNSET) return ""
        val period = timeline.getPeriod(index, Timeline.Period())
        return timeline.getWindow(period.windowIndex, Timeline.Window()).mediaItem.mediaId
    }

    private fun target(): Double {
        val gain = if (isEnabled()) streamGainDb else 0.0
        return if (gain.isFinite() && gain in -60.0..12.0) 10.0.pow(gain / 20.0) else 1.0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // The pipeline also polls with AudioProcessor.EMPTY_BUFFER, which is this processor's own
        // output buffer until the first sample: copying it into itself throws.
        if (!inputBuffer.hasRemaining()) return
        val output = replaceOutputBuffer(inputBuffer.remaining()).order(ByteOrder.LITTLE_ENDIAN)
        val target = target()
        if (multiplier == 1.0 && target == 1.0) {
            output.put(inputBuffer).flip()
            return
        }
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        // Smooth only an in-track setting/profile change, keeping stereo channels together.
        val channels = inputAudioFormat.channelCount
        var channel = 0
        while (inputBuffer.remaining() >= Short.SIZE_BYTES) {
            if (channel == 0) {
                val difference = target - multiplier
                multiplier += difference.coerceIn(-RAMP_STEP, RAMP_STEP)
            }
            val sample = (inputBuffer.short * multiplier).roundToInt().coerceIn(
                Short.MIN_VALUE.toInt(),
                Short.MAX_VALUE.toInt(),
            )
            output.putShort(sample.toShort())
            channel = (channel + 1) % channels
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onReset() {
        trackId = ""
        streamGainDb = 0.0
        multiplier = 1.0
    }

    private companion object {
        const val RAMP_STEP = 1.0 / 512
    }
}
