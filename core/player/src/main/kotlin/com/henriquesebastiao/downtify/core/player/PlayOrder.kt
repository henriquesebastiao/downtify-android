package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline

/** The order a [Player] will play its items in, which isn't index order when shuffle is on. */
internal object PlayOrder {
    /** Bounded look-ahead, including the current entry, following shuffle and repeat without cycling. */
    fun ahead(player: Player, count: Int): List<Int> {
        val timeline = player.currentTimeline
        if (timeline.isEmpty || count <= 0) return emptyList()
        val result = ArrayList<Int>()
        var index = player.currentMediaItemIndex
        while (index != C.INDEX_UNSET && index !in result && result.size < count) {
            result += index
            index = timeline.getNextWindowIndex(index, player.repeatMode, player.shuffleModeEnabled)
        }
        return result
    }

    /** The current item and everything after it, as it will play (repeat aside). */
    fun fromCurrent(player: Player): List<Int> {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return emptyList()
        return walk(timeline, player.currentMediaItemIndex, player.shuffleModeEnabled)
    }

    /** Every item in shuffled order, from the first. */
    fun shuffled(timeline: Timeline): List<Int> =
        if (timeline.isEmpty) emptyList() else walk(timeline, timeline.getFirstWindowIndex(true), true)

    /**
     * Moves a freshly shuffled queue onto the first item of its permutation. Media3 builds that
     * permutation itself when shuffle turns on; starting anywhere else silently drops every item
     * that precedes the current one in it, so a random start plays as little as one song.
     */
    fun startShuffled(player: Player) {
        val timeline = player.currentTimeline
        if (timeline.isEmpty || !player.shuffleModeEnabled) return
        val first = timeline.getFirstWindowIndex(true)
        if (first != C.INDEX_UNSET && first != player.currentMediaItemIndex) {
            player.seekTo(first, C.TIME_UNSET)
        }
    }

    /**
     * An explicit Shuffle launch drops "repeat one" — it would replay the same song forever inside
     * a queue meant to be heard once — but keeps "repeat all" if the user had chosen it.
     */
    fun repeatAfterShuffle(repeatMode: Int): Int =
        if (repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ALL else repeatMode

    private fun walk(timeline: Timeline, from: Int, shuffle: Boolean): List<Int> {
        val order = ArrayList<Int>()
        var index = from
        while (index != C.INDEX_UNSET && order.size < timeline.windowCount) {
            order += index
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
        }
        return order
    }
}
