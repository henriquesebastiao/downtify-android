package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.C
import androidx.media3.common.Player
import com.henriquesebastiao.downtify.core.data.activity.ActivityTrack
import com.henriquesebastiao.downtify.core.data.activity.PlaybackActivityReporter
import com.henriquesebastiao.downtify.core.data.activity.PlaybackActivityState
import com.henriquesebastiao.downtify.core.model.StreamIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Reports what's playing to the server's Activity page: when a song starts,
 * on pause/resume and stop, and every 30 seconds while playing.
 */
internal class ActivityTracker(
    private val player: Player,
    private val reporter: PlaybackActivityReporter,
    private val scope: CoroutineScope,
) : Player.Listener {
    private var last: Pair<String?, PlaybackActivityState>? = null
    private var ticker: Job? = null

    fun start() {
        player.addListener(this)
        send()
    }

    fun stop() {
        player.removeListener(this)
        ticker?.cancel()
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
            )
        ) {
            send()
        }
    }

    private fun currentState(): PlaybackActivityState = when {
        player.currentMediaItem == null || player.playbackState == Player.STATE_IDLE ||
            player.playbackState == Player.STATE_ENDED -> PlaybackActivityState.Stopped

        player.playWhenReady -> PlaybackActivityState.Playing

        else -> PlaybackActivityState.Paused
    }

    /** Reports a change of song or state; nothing when neither changed. */
    private fun send() {
        val state = currentState()
        val key = player.currentMediaItem?.mediaId to state
        if (key == last) return
        // Nothing was reported yet and nothing plays: no need to say "stopped".
        if (last == null && state == PlaybackActivityState.Stopped) return
        // Streams have no library row to report: keep quiet, like the web player does.
        if (StreamIds.isStream(player.currentMediaItem?.mediaId)) {
            last = key
            ticker?.cancel()
            return
        }
        last = key
        post(state)
        ticker?.cancel()
        if (state == PlaybackActivityState.Playing) {
            ticker = scope.launch {
                while (isActive) {
                    delay(TICK_MS)
                    post(PlaybackActivityState.Playing)
                }
            }
        }
    }

    private fun post(state: PlaybackActivityState) {
        val item = player.currentMediaItem
        val meta = player.mediaMetadata
        val episode = item?.let(MediaItems::episodeOf)
        val track = item?.let {
            ActivityTrack(
                trackId = it.mediaId.takeIf { id -> episode == null && id.isNotEmpty() },
                file = episode?.file,
                title = meta.title?.toString(),
                artist = meta.artist?.toString(),
                album = meta.albumTitle?.toString(),
                durationSeconds = player.duration.takeIf { d -> d != C.TIME_UNSET }?.let { d -> (d / MS).toInt() },
            )
        }
        val position = (player.currentPosition / MS).toInt()
        scope.launch { reporter.report(state, track, position) }
    }

    private companion object {
        const val TICK_MS = 30_000L
        const val MS = 1000L
    }
}
