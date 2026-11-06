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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Share one server render with web clients while retaining the native foreground media service. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class DjControlTracker(
    private val player: Player,
    private val music: MusicAiRepository,
    private val scope: CoroutineScope,
) : Player.Listener {
    private var current: DjReference? = null
    private var polling: Job? = null
    private var ticker: Job? = null
    private var latest: com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto? = null
    private var lastMarker: Double? = null
    private var generation = 0L
    private var terminal = false
    private val commands = Mutex()

    fun start() {
        player.addListener(this)
        changed()
    }
    fun stop() {
        generation++
        current = null
        player.removeListener(this)
        polling?.cancel()
        ticker?.cancel()
        latest = null
    }

    private fun command(reference: DjReference, action: String) {
        if (reference.mode != "interactive") return
        val owner = music.session.value
        val mine = generation
        scope.launch {
            commands.withLock {
                if (music.session.value != owner || !music.capabilities.value.enabled("dj_interactive") ||
                    mine != generation || current != reference
                ) {
                    return@withLock
                }
                try {
                    music.djCommand(reference.id, action)
                } catch (_: MusicAiFailure) { /* A remote failure never overrides local intent. */ }
            }
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!terminal && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
            current?.let { command(it, if (playWhenReady) "resume" else "pause") }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = changed()

    private fun changed() {
        val next = DjIds.referenceOf(player.currentMediaItem?.mediaId)
        if (next == current) return
        val retired = current
        polling?.cancel()
        ticker?.cancel()
        latest = null
        lastMarker = null
        val mine = ++generation
        terminal = false
        current = next
        if (retired != null) {
            // System/car controllers can replace media without creating the app coordinator.
            // Retire only the old transport, after fencing callbacks with the new generation.
            for (index in (0 until player.mediaItemCount).reversed()) {
                if (index != player.currentMediaItemIndex &&
                    DjIds.referenceOf(player.getMediaItemAt(index).mediaId) == retired
                ) {
                    player.removeMediaItem(index)
                }
            }
            val owner = music.session.value
            if (retired.mode == "interactive") {
                scope.launch {
                    commands.withLock {
                        if (owner == music.session.value && current != retired) {
                            try {
                                music.djStop(retired.id)
                            } catch (_: MusicAiFailure) { }
                        }
                    }
                }
            }
        }
        (player as? androidx.media3.exoplayer.ExoPlayer)?.pauseAtEndOfMediaItems = next != null
        if (next == null) return
        val handover =
            next.mode == "interactive" &&
                player.currentMediaItem?.mediaMetadata?.extras?.getBoolean("djStartOnLoad") == true &&
                player.playWhenReady
        if (handover) command(next, "resume")
        val owner = music.session.value
        ticker = scope.launch {
            while (isActive && current == next && owner == music.session.value) {
                val value = latest
                if (value != null && owner != null) {
                    val position = DjTimeline.position(player, value.mediaOrigin?.times(1000)?.toLong() ?: 0)
                    val marker = value.timeline.lastOrNull { it.start * 1000 <= position }?.start
                    if (marker != lastMarker) {
                        lastMarker = marker
                        updateMetadata(value, owner.baseUrl)
                    }
                }
                delay(250)
            }
        }
        polling = scope.launch {
            if (handover) delay(1000)
            while (isActive && current == next && owner == music.session.value) {
                if (music.capabilities.value.enabled("dj_" + next.mode)) reconcile(next, mine)
                delay(3000)
            }
        }
    }

    private suspend fun reconcile(reference: DjReference, mine: Long) {
        try {
            val owner = music.session.value
            val position = DjTimeline.position(player, latest?.mediaOrigin?.times(1000)?.toLong() ?: 0)
            val session = if (music.capabilities.value.djLightStatus) {
                music.djPlayback(
                    reference.id,
                    position / 1000.0,
                )
            } else {
                music.djSession(reference.id)
            }
            if (mine != generation || owner != music.session.value || current != reference ||
                !music.capabilities.value.enabled("dj_" + reference.mode)
            ) {
                return
            }
            latest = session
            if (owner != null) updateMetadata(session, owner.baseUrl)
            if (reference.mode != "interactive") return
            when (session.state) {
                // These are renderer states, not the user's playback intent.
                // A late status must never undo a local pause or resume.
                "failed", "stopped", "cancelled", "deleted", "deleting" -> {
                    terminal = true
                    player.pause()
                }
            }
        } catch (error: MusicAiFailure) {
            if (mine == generation && current == reference && error.status in setOf(401, 403, 404, 409)) {
                terminal = true
                player.pause()
            }
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
