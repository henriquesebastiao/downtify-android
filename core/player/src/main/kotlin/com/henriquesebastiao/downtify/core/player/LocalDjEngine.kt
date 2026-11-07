package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** All operations run on the players' application looper. Only the next file is decoded ahead. */
@UnstableApi
internal class LocalDjEngine(first: Player, second: Player, private val onDominant: (Player) -> Unit) {
    private val decks = listOf(first, second)
    var active: Player = first
        private set
    private val standby get() = decks.first { it !== active }
    private var analyses = emptyMap<String, LocalDjAnalysis>()
    private var generation = 0L
    private var armed: Armed? = null
    private var blend: Blend? = null
    private var releaseSpeed: SpeedRelease? = null
    private var enabled = false
    private var released = false
    var volume = first.volume
        private set
    var unmuteVolume = volume.takeIf { it > 0f } ?: 1f
        private set
    var playbackParameters = first.playbackParameters
        private set
    private var focusGain = 1f
    private val mutableTransition = MutableStateFlow(LocalDjTransition())
    val transition = mutableTransition.asStateFlow()
    val playIntent: Boolean? get() = blend?.wantedPlay

    private data class Armed(
        val generation: Long,
        val outgoing: Player,
        val incoming: Player,
        val outgoingIndex: Int,
        val index: Int,
        val plan: LocalDjPlan,
    )
    private data class Blend(val armed: Armed, var wantedPlay: Boolean, var switched: Boolean = false)
    private data class SpeedRelease(val player: Player, val positionMs: Long, val speed: Float)

    fun setEnabled(value: Boolean) {
        enabled = value
        reset()
        if (!value) analyses = emptyMap()
        publishWaiting()
    }

    fun updateAnalyses(values: Map<String, LocalDjAnalysis>) {
        val found = values.entries.take(16).associate { (id, value) ->
            id to value.copy(downbeatsMs = value.downbeatsMs.take(20_000).filter { it >= 0 }.distinct().sorted())
        }
        if (found == analyses) return
        analyses = found
        if (blend == null) reset()
    }

    fun reset() {
        generation++
        blend = null
        armed = null
        releaseSpeed = null
        standby.pause()
        standby.stop()
        standby.clearMediaItems()
        active.volume = volume * focusGain
        active.playbackParameters = playbackParameters
        publishWaiting()
    }

    fun setPlaying(value: Boolean) {
        val mixing = blend
        if (mixing == null) {
            active.playWhenReady = value
            standby.pause()
        } else {
            mixing.wantedPlay = value
            mixing.armed.outgoing.playWhenReady = value
            mixing.armed.incoming.playWhenReady = value && !waitingForOutgoing(mixing.armed)
        }
    }

    fun setVolume(value: Float, remember: Boolean = true) {
        volume = value.coerceIn(0f, 1f)
        if (remember && volume > 0f) unmuteVolume = volume
        applyGains()
    }

    fun setFocusGain(value: Float) {
        focusGain = value.coerceIn(0f, 1f)
        applyGains()
    }

    fun setParameters(value: PlaybackParameters) {
        reset()
        playbackParameters = value
        active.playbackParameters = value
    }

    fun release() {
        released = true
        enabled = false
        analyses = emptyMap()
        decks.forEach {
            it.pause()
            it.release()
        }
        armed = null
        blend = null
        releaseSpeed = null
    }

    fun pulse() {
        if (!enabled || released) return
        val mixing = blend
        if (mixing != null) {
            advance(mixing)
            return
        }
        easeSpeed()
        if (!canMix()) return
        val next = active.nextMediaItemIndex
        val prepared = armed
        if (prepared == null || prepared.index != next || prepared.outgoing !== active) arm(next)
        val ready = armed ?: return
        if (active.currentPosition >= ready.plan.startMs) start(ready)
    }

    private fun canMix(): Boolean {
        if (!active.isPlaying || active.repeatMode == Player.REPEAT_MODE_ONE) return false
        if (active.shuffleModeEnabled) {
            publishWaiting(LocalDjFallback.UnsupportedQueue)
            return false // Preserve native shuffle order until identical shuffle timelines are supported.
        }
        val next = active.nextMediaItemIndex
        val current = active.currentMediaItem ?: return false
        if (next == C.INDEX_UNSET || active.duration <= 4000) return false
        val supported = localTrack(current) && localTrack(active.getMediaItemAt(next))
        if (!supported) publishWaiting(LocalDjFallback.UnsupportedQueue)
        return supported
    }

    private fun arm(index: Int) {
        val incoming = standby
        val current = active.currentMediaItem ?: return
        val next = active.getMediaItemAt(index)
        val plan = LocalDjPlan.create(active.duration, analyses[current.mediaId], analyses[next.mediaId])
        incoming.pause()
        incoming.volume = 0f
        incoming.repeatMode = active.repeatMode
        incoming.shuffleModeEnabled = false
        incoming.setMediaItems((0 until active.mediaItemCount).map(active::getMediaItemAt), index, plan.cueMs)
        incoming.playbackParameters =
            PlaybackParameters(playbackParameters.speed * plan.speed, playbackParameters.pitch)
        incoming.prepare()
        armed = Armed(generation, active, incoming, active.currentMediaItemIndex, index, plan)
        mutableTransition.value = LocalDjTransition(
            LocalDjPhase.Prepared,
            current.mediaId,
            next.mediaId,
            analysed = plan.analysed,
            fallback = plan.fallback,
        )
    }

    private fun start(value: Armed) {
        if (value.generation != generation) return
        if (value.incoming.playerError != null) {
            reset()
            publishWaiting(LocalDjFallback.DecoderFailure)
            return
        }
        val buffered = value.incoming.bufferedPosition - value.incoming.currentPosition
        if (value.incoming.playbackState != Player.STATE_READY || buffered < value.plan.durationMs + 250) {
            mutableTransition.value = mutableTransition.value.copy(fallback = LocalDjFallback.NotBuffered)
            return
        }
        // Never start a late long overlap halfway through its plan: leave native playback intact.
        if (value.outgoing.currentPosition - value.plan.startMs > 250) return
        blend = Blend(value, value.outgoing.playWhenReady)
        value.incoming.playWhenReady = value.outgoing.playWhenReady
        advance(checkNotNull(blend))
    }

    private fun advance(value: Blend) {
        val pair = value.armed
        if (pair.generation != generation) return
        if (pair.incoming.playerError != null) {
            failIncoming(pair)
            return
        }
        if (!value.wantedPlay) {
            pair.outgoing.pause()
            pair.incoming.pause()
            return
        }
        if (!outgoingReady(pair)) return
        if (pair.incoming.playbackState != Player.STATE_READY) {
            failIncoming(pair)
            return
        }
        pair.incoming.playWhenReady = true
        val progress = progress(pair)
        setBlendGains(pair, progress)
        if (progress >= 0.5f && !value.switched) {
            value.switched = true
            active = pair.incoming
            onDominant(active)
        }
        mutableTransition.value = LocalDjTransition(
            LocalDjPhase.Mixing,
            pair.outgoing.currentMediaItem?.mediaId,
            pair.incoming.currentMediaItem?.mediaId,
            progress,
            pair.plan.analysed,
            pair.plan.fallback,
        )
        if (progress >= 1f) finish(pair)
    }

    private fun waitingForOutgoing(pair: Armed) = pair.outgoing.currentMediaItemIndex == pair.outgoingIndex &&
        pair.outgoing.playbackState == Player.STATE_BUFFERING

    private fun outgoingReady(pair: Armed): Boolean {
        if (pair.outgoing.playerError != null) {
            // A failed decoder cannot provide the fade clock. Promote only audio
            // already ready; otherwise stop instead of retrying a silent overlap.
            if (pair.incoming.playbackState == Player.STATE_READY) {
                pair.incoming.playWhenReady = true
                finish(pair)
            } else {
                pair.outgoing.pause()
                failIncoming(pair)
            }
            publishWaiting(LocalDjFallback.DecoderFailure)
            return false
        }
        if (waitingForOutgoing(pair)) {
            // Keep playback intent, but do not let the other clock drift while
            // progress is frozen. A later pulse resumes only if intent is play.
            pair.incoming.pause()
            return false
        }
        return true
    }

    private fun progress(pair: Armed): Float {
        if (pair.outgoing.currentMediaItemIndex != pair.outgoingIndex ||
            pair.outgoing.playbackState == Player.STATE_ENDED
        ) {
            return 1f
        }
        return ((pair.outgoing.currentPosition - pair.plan.startMs).toFloat() / pair.plan.durationMs).coerceIn(0f, 1f)
    }

    private fun finish(pair: Armed) {
        active = pair.incoming
        onDominant(active)
        pair.outgoing.pause()
        pair.outgoing.stop()
        pair.outgoing.clearMediaItems()
        active.volume = volume * focusGain
        releaseSpeed = SpeedRelease(active, active.currentPosition, pair.plan.speed)
        blend = null
        armed = null
        publishWaiting()
    }

    private fun failIncoming(pair: Armed) {
        // Keep the still-audible source rather than promoting an empty decoder.
        active = pair.outgoing
        onDominant(active)
        reset()
        publishWaiting(LocalDjFallback.DecoderFailure)
    }

    private fun easeSpeed() {
        val value = releaseSpeed ?: return
        val progress = ((value.player.currentPosition - value.positionMs) / 4000f).coerceIn(0f, 1f)
        val speed = value.speed + (1f - value.speed) * progress
        value.player.playbackParameters = PlaybackParameters(playbackParameters.speed * speed, playbackParameters.pitch)
        if (progress >= 1f) releaseSpeed = null
    }

    private fun applyGains() {
        val value = blend
        if (value == null) active.volume = volume * focusGain else setBlendGains(value.armed, progress(value.armed))
    }

    private fun setBlendGains(pair: Armed, progress: Float) {
        val (first, second) = localDjGains(progress)
        pair.outgoing.volume = volume * focusGain * first
        pair.incoming.volume = volume * focusGain * second
    }

    private fun publishWaiting(reason: LocalDjFallback = LocalDjFallback.None) {
        mutableTransition.value =
            LocalDjTransition(if (enabled) LocalDjPhase.Waiting else LocalDjPhase.Disabled, fallback = reason)
    }

    private fun localTrack(item: MediaItem) = item.localConfiguration?.uri?.let(MediaItems::trackIdOf) != null
}
