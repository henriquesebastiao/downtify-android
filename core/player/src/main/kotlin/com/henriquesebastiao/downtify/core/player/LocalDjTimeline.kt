package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ForwardingTimeline

/** Stable identities without rebuilding MediaItemData for the entire library on every event. */
@UnstableApi
internal class LocalDjTimeline(
    private val source: Timeline,
    private val occurrences: List<Any>,
    private val revision: Long,
) : ForwardingTimeline(source) {
    private data class PeriodUid(val occurrence: Any, val ordinal: Int, val nativeUid: Any?)

    private val windowByOccurrence by lazy { occurrences.withIndex().associate { it.value to it.index } }

    fun matches(value: Timeline, occurrenceRevision: Long): Boolean = source === value && revision == occurrenceRevision

    override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window =
        source.getWindow(windowIndex, window, defaultPositionProjectionUs).apply { uid = occurrences[windowIndex] }

    override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
        source.getPeriod(periodIndex, period, setIds)
        if (setIds) {
            val window = source.getWindow(period.windowIndex, Window())
            val localTrack = window.mediaItem.localConfiguration?.uri?.let(MediaItems::trackIdOf) != null
            period.uid = PeriodUid(
                occurrences[period.windowIndex],
                if (localTrack) periodIndex - window.firstPeriodIndex else 0,
                if (localTrack) null else period.uid,
            )
        }
        return period
    }

    override fun getUidOfPeriod(periodIndex: Int): Any = getPeriod(periodIndex, Period(), true).uid!!

    override fun getIndexOfPeriod(uid: Any): Int {
        val key = uid as? PeriodUid ?: return C.INDEX_UNSET
        val windowIndex = windowByOccurrence[key.occurrence] ?: return C.INDEX_UNSET
        if (windowIndex >= source.windowCount) return C.INDEX_UNSET
        val window = source.getWindow(windowIndex, Window())
        val index = key.nativeUid?.let(source::getIndexOfPeriod) ?: (window.firstPeriodIndex + key.ordinal)
        return if (index in window.firstPeriodIndex..window.lastPeriodIndex) index else C.INDEX_UNSET
    }
}
