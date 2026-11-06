package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ForwardingTimeline

/** Stable occurrence identities must not replace the decoder's native shuffle navigation. */
@UnstableApi
internal class LocalDjTimeline(stable: Timeline, private val order: Timeline) : ForwardingTimeline(stable) {
    override fun getFirstWindowIndex(shuffleModeEnabled: Boolean): Int = order.getFirstWindowIndex(shuffleModeEnabled)

    override fun getLastWindowIndex(shuffleModeEnabled: Boolean): Int = order.getLastWindowIndex(shuffleModeEnabled)

    override fun getNextWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int =
        order.getNextWindowIndex(windowIndex, repeatMode, shuffleModeEnabled)

    override fun getPreviousWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int =
        order.getPreviousWindowIndex(windowIndex, repeatMode, shuffleModeEnabled)
}
