package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiFailure
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.model.DjIds
import com.henriquesebastiao.downtify.core.model.DjReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Share one server render with web clients while retaining the native foreground media service. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class DjControlTracker(
    private val player: Player,
    private val music: MusicAiRepository,
    private val scope: CoroutineScope,
) : Player.Listener {
    private var current: DjReference? = null
    private var polling: Job? = null
    private var pausedByServer = false

    fun start() {
        player.addListener(this)
        changed()
    }
    fun stop() {
        player.removeListener(this)
        polling?.cancel()
    }

    private fun command(reference: DjReference, action: String) {
        if (reference.mode != "interactive") return
        val owner = music.session.value
        scope.launch {
            if (music.session.value != owner || !music.capabilities.value.enabled("dj_interactive")) return@launch
            try {
                music.djCommand(reference.id, action)
            } catch (
                _: MusicAiFailure,
            ) { /* Next server poll reconciles. */ }
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
            current?.let { command(it, if (playWhenReady) "resume" else "pause") }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = changed()

    private fun changed() {
        val next = DjIds.referenceOf(player.currentMediaItem?.mediaId)
        if (next == current) return
        current?.let { command(it, "pause") }
        polling?.cancel()
        pausedByServer = false
        current = next
        (player as? androidx.media3.exoplayer.ExoPlayer)?.pauseAtEndOfMediaItems = next != null
        if (next == null) return
        val handover =
            next.mode == "interactive" &&
                player.currentMediaItem?.mediaMetadata?.extras?.getBoolean("djStartOnLoad") == true &&
                player.playWhenReady
        if (handover) command(next, "resume")
        val owner = music.session.value
        polling = scope.launch {
            if (handover) delay(1000)
            while (isActive && current == next && owner == music.session.value) {
                if (music.capabilities.value.enabled("dj_" + next.mode)) reconcile(next)
                delay(2000)
            }
        }
    }

    private suspend fun reconcile(reference: DjReference) {
        try {
            val owner = music.session.value
            val session = music.djSession(reference.id)
            if (owner != music.session.value || current != reference ||
                !music.capabilities.value.enabled("dj_" + reference.mode)
            ) {
                return
            }
            if (owner != null) updateMetadata(session, owner.baseUrl)
            if (reference.mode != "interactive") return
            when (session.state) {
                "paused" -> {
                    if (player.playWhenReady) pausedByServer = true
                    player.pause()
                }

                "playing" -> if (pausedByServer) {
                    pausedByServer = false
                    player.play()
                }

                "failed", "stopped", "cancelled", "deleted", "deleting" -> player.pause()
            }
        } catch (error: MusicAiFailure) {
            if (error.status in setOf(401, 403, 404, 409)) player.pause()
        }
    }

    private fun updateMetadata(
        session: com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto,
        baseUrl: String,
    ) {
        val item = player.currentMediaItem ?: return
        if (session.timeline.isEmpty()) return
        val position = DjTimeline.position(player, session.mediaOrigin?.times(1000)?.toLong() ?: 0)
        val updated = DjTimeline.metadata(item, session, position, baseUrl)
        if (updated.mediaMetadata != item.mediaMetadata) player.replaceMediaItem(player.currentMediaItemIndex, updated)
    }
}
