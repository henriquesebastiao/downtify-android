package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import com.henriquesebastiao.downtify.core.model.PlayQueue

/**
 * "Add to queue" and queue moves. They work on the [Player] the session exposes, which is the
 * [LocalDjPlayer] facade and not an [ExoPlayer]: casting the session player was a class cast
 * error, so both commands failed before touching the queue.
 */
@UnstableApi
internal object QueueEdit {
    /** Plays [item] right after the current one, then after anything already queued. */
    fun add(player: Player, item: MediaItem) {
        if (player.mediaItemCount == 0) {
            player.setMediaItem(item)
            player.prepare()
            return
        }
        val anchor = PlayQueue.anchor(PlayOrder.fromCurrent(player)) {
            MediaItems.isQueued(player.getMediaItemAt(it))
        } ?: return
        if (player.shuffleModeEnabled) {
            val shuffled = PlayOrder.shuffled(player.currentTimeline)
            val newIndex = player.mediaItemCount
            player.addMediaItem(item)
            applyShuffleOrder(player, PlayQueue.shuffleOrderWith(shuffled, anchor, newIndex))
        } else {
            player.addMediaItem(anchor + 1, item)
        }
    }

    /**
     * Moves the item at [from] to play right after the item at [after] (indices in the player's
     * list), marked as queued or not. With shuffle on only the shuffled order changes.
     */
    fun move(player: Player, from: Int, after: Int, queued: Boolean) {
        val count = player.mediaItemCount
        if (from !in 0 until count || after !in 0 until count || from == after) return
        if (from == player.currentMediaItemIndex) return
        val item = player.getMediaItemAt(from)
        if (MediaItems.isQueued(item) != queued) player.replaceMediaItem(from, MediaItems.withQueued(item, queued))
        if (player.shuffleModeEnabled) {
            val order = PlayQueue.moveAfter(PlayOrder.shuffled(player.currentTimeline), from, after)
            applyShuffleOrder(player, order)
        } else {
            player.moveMediaItem(from, PlayQueue.moveTarget(from, after))
        }
    }

    /** Media3 keeps the shuffled order on the deck; the facade owns the deck that is playing. */
    private fun applyShuffleOrder(player: Player, order: List<Int>) {
        val array = order.toIntArray()
        when (player) {
            is LocalDjPlayer -> player.restoreShuffleOrder(array)
            is ExoPlayer -> player.setShuffleOrder(DefaultShuffleOrder(array, java.util.Random().nextLong()))
        }
    }
}
