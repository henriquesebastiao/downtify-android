package com.henriquesebastiao.downtify.core.player

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiFailure
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.model.DjIds
import com.henriquesebastiao.downtify.core.model.EpisodeIds
import com.henriquesebastiao.downtify.core.model.StreamIds
import com.henriquesebastiao.downtify.core.network.dto.MusicAiListenDto
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Optional per-track profile events, independent of the native artist listen counter. */
internal class MusicAiListenTracker(
    private val player: Player,
    private val music: MusicAiRepository,
    private val scope: CoroutineScope,
) : Player.Listener {
    private var track: String? = null
    private var terminalTrack: String? = null
    private var owner = music.session.value
    private var eventId = ""
    private var seconds = 0.0
    private var sequence = 0
    private var generation = 0
    private var lastPosition = 0L
    private var lastClock = SystemClock.elapsedRealtime()
    private var sentAt = lastClock
    private var timer: Job? = null
    private var occurrence: Long? = null

    private fun identity(item: MediaItem?): String? = if (DjIds.isDj(item?.mediaId)) {
        item?.mediaMetadata?.extras?.getString("djTrackId")
    } else {
        item?.mediaId
    }

    private fun currentOccurrence(): Long? = player.currentMediaItem?.takeIf { DjIds.isDj(it.mediaId) }
        ?.mediaMetadata?.extras?.getLong("djStart")

    fun start() {
        player.addListener(this)
        timer = scope.launch {
            while (isActive) {
                delay(1000)
                sample()
            }
        }
    }
    fun stop() {
        publish("pause")
        player.removeListener(this)
        timer?.cancel()
    }

    private fun reset(id: String?) {
        occurrence = currentOccurrence()
        track =
            id?.takeUnless {
                EpisodeIds.isEpisode(it) || StreamIds.isStream(it) ||
                    DjIds.isDj(it)
            }
        owner = music.session.value
        eventId = UUID.randomUUID().toString()
        seconds = 0.0
        sequence = 0
        generation = music.capabilities.value.profileRevision
        lastPosition = player.currentPosition
        lastClock = SystemClock.elapsedRealtime()
        sentAt = lastClock
    }
    private fun enabled() = music.capabilities.value.enabled("personalization")
    private fun changed(id: String) =
        track != id || occurrence != currentOccurrence() || generation != music.capabilities.value.profileRevision
    private fun sample() {
        if (!enabled() || owner != music.session.value) {
            reset(null)
            return
        }
        val id = identity(player.currentMediaItem) ?: return
        if (id == terminalTrack) return
        if (changed(id)) {
            publish("pause")
            reset(id)
        }
        if (track == null) return
        val clock = SystemClock.elapsedRealtime()
        val position = player.currentPosition
        val elapsed = clock - lastClock
        val movement = position - lastPosition
        val continuousMovement = elapsed in 1..3000 && movement > 0 && movement <= elapsed * 1.25 + 200
        if (player.isPlaying && continuousMovement) {
            seconds += minOf(movement, elapsed) / 1000.0
        }
        lastPosition = position
        lastClock = clock
        if (clock - sentAt >= 20000) publish("pause")
    }
    private fun publish(outcome: String) {
        val id = track ?: return
        if (!enabled() || owner != music.session.value || generation != music.capabilities.value.profileRevision) return
        val paired = owner
        val event = MusicAiListenDto(eventId, id, seconds, outcome, ++sequence, generation)
        sentAt = SystemClock.elapsedRealtime()
        scope.launch {
            if (music.session.value != paired) return@launch
            try {
                music.listen(event)
            } catch (
                _: MusicAiFailure,
            ) { /* Next checkpoint retries the cumulative duration. */ }
        }
    }
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (DjIds.isDj(mediaItem?.mediaId) && identity(mediaItem) == track && occurrence == currentOccurrence()) return
        terminalTrack = null
        publish(
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            ) {
                "complete"
            } else {
                "skip"
            },
        )
        reset(identity(mediaItem))
    }
    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        if (DjIds.isDj(player.currentMediaItem?.mediaId)) sample()
    }
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) terminalTrack = null
        if (!isPlaying) publish("pause")
        lastPosition = player.currentPosition
        lastClock = SystemClock.elapsedRealtime()
    }
    override fun onPlayerError(error: PlaybackException) {
        terminalTrack = identity(player.currentMediaItem)
        publish("error")
        reset(null)
    }
    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            terminalTrack = identity(player.currentMediaItem)
            publish("complete")
            reset(null)
        }
    }
}
