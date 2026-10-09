package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.Player
import com.henriquesebastiao.downtify.core.data.catalog.CatalogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Warms the server's stream cache one track ahead: when the queue moves
 * to a new track, and again once it has loaded, the next streamed song
 * starts downloading on the server — skipping to it then starts at once.
 * Silent by design: a failed warm only means advancing resolves on demand.
 */
internal class StreamPrefetchTracker(
    private val player: Player,
    private val catalog: CatalogRepository,
    private val scope: CoroutineScope,
) : Player.Listener {
    private var warmedFor = -1

    fun start() {
        player.addListener(this)
    }

    fun stop() {
        player.removeListener(this)
    }

    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
        warmedFor = -1
        warmNext()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) warmNext()
    }

    private fun warmNext() {
        val index = player.currentMediaItemIndex
        if (index < 0 || warmedFor == index) return
        warmedFor = index
        if (index + 1 >= player.mediaItemCount) return
        val videoId = MediaItems.streamOf(player.getMediaItemAt(index + 1))?.videoId ?: return
        scope.launch { catalog.prefetchStream(videoId) }
    }
}
