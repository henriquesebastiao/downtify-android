package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.henriquesebastiao.downtify.core.data.listens.ListenReporter
import com.henriquesebastiao.downtify.core.model.EpisodeIds
import com.henriquesebastiao.downtify.core.model.ListenCounter
import com.henriquesebastiao.downtify.core.model.StreamIds
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Reports a play to the server (through the offline-safe queue) once half the
 * track, or four minutes, has actually played — the web player's rule, with a
 * fresh `play_id` per play.
 */
internal class ListenTracker(
    private val player: Player,
    private val reporter: ListenReporter,
    private val scope: CoroutineScope,
) : Player.Listener {
    private val counter = ListenCounter()
    private var trackId: String? = null
    private var playId = UUID.randomUUID().toString()
    private var startedAt: Instant = Instant.now()

    fun start() {
        player.addListener(this)
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                if (player.isPlaying) tick()
            }
        }
    }

    fun stop() {
        player.removeListener(this)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        newPlay(mediaItem?.mediaId)
    }

    private fun newPlay(id: String?) {
        trackId = id
        playId = UUID.randomUUID().toString()
        startedAt = Instant.now()
        counter.reset(player.currentPosition / MS)
    }

    private fun tick() {
        val id = player.currentMediaItem?.mediaId ?: return
        if (id != trackId) newPlay(id)
        // Podcast episodes and streams don't count as listens (they're not tracks the server knows).
        if (EpisodeIds.isEpisode(id) || StreamIds.isStream(id)) return
        val duration = player.duration.takeIf { it != C.TIME_UNSET }
            ?: player.mediaMetadata.durationMs
            ?: return
        when (counter.onPosition(player.currentPosition / MS, duration / MS)) {
            ListenCounter.Progress.Counted -> {
                val report = Triple(id, playId, startedAt)
                scope.launch { reporter.report(report.first, report.second, report.third) }
            }

            ListenCounter.Progress.NewPlay -> {
                playId = UUID.randomUUID().toString()
                startedAt = Instant.now()
            }

            ListenCounter.Progress.None -> Unit
        }
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val MS = 1000.0
    }
}
