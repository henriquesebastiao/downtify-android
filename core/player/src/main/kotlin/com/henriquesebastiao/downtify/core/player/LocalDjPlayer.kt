package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer.State
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.henriquesebastiao.downtify.core.model.DjIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One session-facing player for two audio decks. Both decoders must use the same looper,
 * independent audio processors and no internal audio focus/noisy handling. [LocalDjAudioFocus]
 * owns those policies. Release that helper before releasing this player; this releases both decks.
 */
// Player facade: the explicit methods mirror Media3 command callbacks.
@Suppress("TooManyFunctions")
@UnstableApi
class LocalDjPlayer(first: Player, second: Player, private val scope: CoroutineScope) :
    ForwardingSimpleBasePlayer(first) {
    private var initialized = false
    private val decks = listOf(first, second)
    private val dominant = kotlinx.coroutines.flow.MutableStateFlow(first)
    val dominantDecoder = dominant.asStateFlow()
    private val occurrenceIds = MutableList(first.mediaItemCount) { Any() }
    private var occurrenceRevision = 0L
    private var stableTimeline: LocalDjTimeline? = null
    internal var queueGeneration = 0L
        private set
    private val engine = LocalDjEngine(first, second) {
        setPlayer(it)
        dominant.value = it
    }
    private var ticker: Job? = null
    private var playGate: () -> Boolean = { true }
    val transition = engine.transition

    init {
        require(first !== second && first.applicationLooper == second.applicationLooper)
        initialized = true
    }

    fun setEnabled(value: Boolean) {
        engine.setEnabled(value)
        ticker?.cancel()
        ticker = if (value) {
            scope.launch {
                while (isActive) {
                    engine.pulse()
                    delay(50)
                }
            }
        } else {
            null
        }
        invalidateState()
    }

    fun setServerDjBarrier(value: Boolean) {
        decks.forEach { (it as? androidx.media3.exoplayer.ExoPlayer)?.pauseAtEndOfMediaItems = value }
    }

    fun restoreShuffleOrder(order: IntArray) {
        (engine.active as? androidx.media3.exoplayer.ExoPlayer)?.setShuffleOrder(
            androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(order, 0L),
        )
        // The facade caches its state, so it keeps the deck's previous order until rebuilt.
        invalidateState()
    }

    fun updateAnalyses(values: Map<String, LocalDjAnalysis>) = engine.updateAnalyses(values)

    internal fun setMixMode(value: LocalDjMode) = engine.setMode(value)

    internal fun evidenceWindow(): LocalDjWindow {
        val active = engine.active
        val indices = localDjWindowIndices(active.mediaItemCount, active.currentMediaItemIndex, active.repeatMode == Player.REPEAT_MODE_ALL)
        return LocalDjWindow(queueGeneration, occurrenceRevision, indices,
            indices.map { active.getMediaItemAt(it).mediaId }, active.shuffleModeEnabled,
            active.repeatMode == Player.REPEAT_MODE_ONE, engine.playbackParameters.speed, engine.playbackParameters.pitch)
    }

    internal fun retainEvidence(window: LocalDjWindow, sources: Set<String>) = engine.retainEvidence(window, sources)

    internal fun clearEvidence() = engine.clearEvidence()

    internal fun currentPlanReady(window: LocalDjWindow) = engine.currentPlanReady(window)

    internal fun updateEvidence(
        expected: LocalDjWindow,
        analyses: Map<String, LocalDjAnalysis>,
        plans: List<LocalDjVerifiedPlan>,
    ): Boolean {
        if (expected != evidenceWindow()) return false
        updateAnalyses(analyses)
        engine.updatePlans(plans)
        return true
    }

    /** Crossfade length chosen by the user; applied to the next armed transition. */
    fun setTransitionMs(value: Long) {
        engine.transitionMs = value
    }

    fun resetForOwner() {
        setEnabled(false)
        engine.setPlaying(false)
        clearMediaItems()
        occurrenceRevision++
        occurrenceIds.clear()
    }

    fun setFocusGain(value: Float) {
        engine.setFocusGain(value)
        invalidateState()
    }

    internal fun setPlayGate(value: () -> Boolean) {
        playGate = value
    }

    override fun getState(): State {
        val state = super.getState()
        if (!initialized) return state
        if (occurrenceIds.size < state.timeline.windowCount) occurrenceRevision++
        while (occurrenceIds.size < state.timeline.windowCount) occurrenceIds.add(Any())
        val stable = stableTimeline?.takeIf { it.matches(state.timeline, occurrenceRevision) } ?: run {
            LocalDjTimeline(state.timeline, occurrenceIds.toList(), occurrenceRevision).also { stableTimeline = it }
        }
        val commands = state.availableCommands.buildUpon()
        val currentItem = if (state.currentMediaItemIndex in 0 until state.timeline.windowCount) {
            state.timeline.getWindow(state.currentMediaItemIndex, Timeline.Window()).mediaItem
        } else {
            null
        }
        if (DjIds.referenceOf(currentItem?.mediaId) != null) {
            commands.addAll(
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            )
        }
        return state.buildUpon().setAvailableCommands(commands.build()).setPlaylist(
            stable,
            engine.active.currentTracks,
            engine.active.mediaMetadata,
        ).setVolume(engine.volume)
            .setUnmuteVolume(engine.unmuteVolume).setPlaybackParameters(engine.playbackParameters)
            .setPlayWhenReady(engine.playIntent ?: state.playWhenReady, state.playWhenReadyChangeReason).build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        engine.setPlaying(playWhenReady && playGate())
        return Futures.immediateVoidFuture()
    }

    override fun handleSetVolume(volume: Float, volumeOperationType: Int): ListenableFuture<*> {
        when (volumeOperationType) {
            C.VOLUME_OPERATION_TYPE_SET_VOLUME -> engine.setVolume(volume)
            C.VOLUME_OPERATION_TYPE_MUTE -> engine.setVolume(0f, remember = false)
            C.VOLUME_OPERATION_TYPE_UNMUTE -> engine.setVolume(engine.unmuteVolume)
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        engine.setParameters(playbackParameters)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetAudioAttributes(
        audioAttributes: AudioAttributes,
        handleAudioFocus: Boolean,
    ): ListenableFuture<*> {
        decks.forEach { it.setAudioAttributes(audioAttributes, false) }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        engine.reset()
        return super.handleStop()
    }

    override fun handleRelease(): ListenableFuture<*> {
        ticker?.cancel()
        engine.release()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        // Compat/car controllers may translate Next/Previous to a generic indexed seek.
        // The classic queue is retained for explicit Exit only, never a DJ navigation target.
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ->
                nextInsideDj()?.let { return it }

            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ->
                previousInsideDj()?.let { return it }
        }
        if (DjIds.referenceOf(currentMediaItem?.mediaId) != null && mediaItemIndex != currentMediaItemIndex) {
            return when (mediaItemIndex) {
                nextMediaItemIndex -> nextInsideDj() ?: Futures.immediateVoidFuture()
                previousMediaItemIndex -> previousInsideDj() ?: Futures.immediateVoidFuture()
                else -> unavailable("Quittez le DJ pour choisir un titre dans la file classique.")
            }
        }
        engine.reset()
        return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        engine.reset()
        return super.handleSetRepeatMode(repeatMode)
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        engine.reset()
        val result = super.handleSetShuffleModeEnabled(shuffleModeEnabled)
        if (shuffleModeEnabled) {
            val player = engine.active
            val order = PlayOrder.shuffled(player.currentTimeline)
            val current = order.indexOf(player.currentMediaItemIndex)
            if (current > 0 && player is androidx.media3.exoplayer.ExoPlayer) {
                player.setShuffleOrder(
                    androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(
                        (order.drop(current) + order.take(current)).toIntArray(),
                        0L,
                    ),
                )
                invalidateState()
            }
        }
        return result
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        queueGeneration++
        engine.clearEvidence()
        occurrenceRevision++
        occurrenceIds.clear()
        repeat(mediaItems.size) { occurrenceIds.add(Any()) }
        return super.handleSetMediaItems(mediaItems, startIndex, startPositionMs)
    }

    override fun handleAddMediaItems(index: Int, mediaItems: List<MediaItem>): ListenableFuture<*> {
        engine.clearEvidence()
        occurrenceRevision++
        occurrenceIds.addAll(index, List(mediaItems.size) { Any() })
        return super.handleAddMediaItems(index, mediaItems)
    }

    override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int): ListenableFuture<*> {
        engine.clearEvidence()
        occurrenceRevision++
        val moved = occurrenceIds.subList(fromIndex, toIndex).toList()
        occurrenceIds.subList(fromIndex, toIndex).clear()
        occurrenceIds.addAll(newIndex, moved)
        return super.handleMoveMediaItems(fromIndex, toIndex, newIndex)
    }

    override fun handleReplaceMediaItems(
        fromIndex: Int,
        toIndex: Int,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<*> {
        engine.clearEvidence()
        occurrenceRevision++
        occurrenceIds.subList(fromIndex, toIndex).clear()
        occurrenceIds.addAll(fromIndex, List(mediaItems.size) { Any() })
        return super.handleReplaceMediaItems(fromIndex, toIndex, mediaItems)
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        if (fromIndex == 0 && toIndex == mediaItemCount && toIndex > 0) queueGeneration++
        engine.clearEvidence()
        occurrenceRevision++
        occurrenceIds.subList(fromIndex, toIndex).clear()
        return super.handleRemoveMediaItems(fromIndex, toIndex)
    }

    internal fun pulseForTest() = engine.pulse()

    /** Asked when a controller presses Next inside an interactive DJ render. */
    private var djNext: (() -> Unit)? = null
    private var djNavigationError: (String) -> Unit = {}

    fun setDjNextHandler(handler: (() -> Unit)?) {
        djNext = handler
    }

    fun setDjNavigationErrorHandler(handler: (String) -> Unit) {
        djNavigationError = handler
    }

    private fun unavailable(message: String): ListenableFuture<*> {
        djNavigationError(message)
        return Futures.immediateVoidFuture()
    }

    /**
     * Next inside a DJ render stays inside the mix: a prepared render jumps to its next marker,
     * an interactive one asks the server for the next transition. Letting the player advance
     * would leave the mix for the classic queue item stored right after it — the reported
     * Android Auto behaviour.
     */
    internal fun nextInsideDj(): ListenableFuture<*>? {
        val item = currentMediaItem ?: return null
        val dj = DjMediaItems.of(item) ?: return null
        return if (dj.mode == "interactive") {
            val handler = djNext
            if (handler == null) {
                unavailable("La commande DJ est indisponible. Réessayez dans un instant.")
            } else {
                handler()
                Futures.immediateVoidFuture()
            }
        } else {
            nextPrepared(item, dj)
        }
    }

    private fun nextPrepared(item: MediaItem, dj: PlayingDj): ListenableFuture<*> {
        // A missing next marker is authoritative even when an older resume plan remains.
        if (dj.nextStart == Long.MAX_VALUE || dj.nextStart <= 0) {
            return unavailable("Aucun titre suivant n’est disponible dans ce mix.")
        }
        val positionMs = DjTimeline.position(this, dj.origin)
        val rows = DjTimeline.resumeRows(item)
        val index = DjTimeline.markerIndex(rows, positionMs)
        val next = if (rows.isEmpty()) {
            dj.nextStart
        } else {
            rows.getOrNull(index + 1)
                ?.takeIf { index >= 0 }?.start?.let(DjTimeline::boundaryMs) ?: Long.MAX_VALUE
        }
        val end = rows.getOrNull(index)?.end?.let(DjTimeline::boundaryMs) ?: dj.end
        if (next == Long.MAX_VALUE || next <= 0) {
            return unavailable("Aucun titre suivant n’est disponible dans ce mix.")
        }
        val windowTarget = maxOf(next, end) - positionMs + currentPosition
        if (windowTarget < 0) return unavailable("Le titre suivant est sorti de la fenêtre du direct.")
        return seekInsideDj(windowTarget)
    }

    /** Previous inside a DJ render restarts the track, or returns to the previous marker. */
    internal fun previousInsideDj(): ListenableFuture<*>? {
        val item = currentMediaItem ?: return null
        val dj = DjMediaItems.of(item) ?: return null
        val positionMs = DjTimeline.position(this, dj.origin)
        val rows = DjTimeline.resumeRows(item)
        val row = DjTimeline.markerIndex(rows, positionMs)
        fun start(index: Int) = DjTimeline.boundaryMs(
            maxOf(rows[index].start, rows.getOrNull(index - 1)?.end ?: 0.0),
        )
        val target = when {
            rows.isEmpty() -> return unavailable("Les repères du mix ne sont pas encore disponibles.")
            row < 0 -> return unavailable("Le repère précédent n’est plus disponible dans ce direct.")
            positionMs - start(row) > RESTART_MS -> start(row)
            row > 0 -> start(row - 1)
            else -> start(row)
        }
        val windowTarget = target - positionMs + currentPosition
        if (windowTarget < 0) return unavailable("Le titre précédent est sorti de la fenêtre du direct.")
        return seekInsideDj(windowTarget)
    }

    private fun seekInsideDj(positionMs: Long): ListenableFuture<*> {
        engine.reset()
        val result = super.handleSeek(currentMediaItemIndex, positionMs, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        // This explicit marker path bypasses SimpleBasePlayer's public seek bookkeeping.
        invalidateState()
        return result
    }

    /** Explicit Exit may select a retained classic occurrence; ordinary DJ seeks cannot. */
    internal fun seekToClassic(index: Int, positionMs: Long): ListenableFuture<*> {
        engine.reset()
        val result = super.handleSeek(index, positionMs, Player.COMMAND_SEEK_TO_MEDIA_ITEM)
        invalidateState()
        return result
    }

    private companion object {
        /** Listening longer than this restarts the track instead of stepping back. */
        const val RESTART_MS = 3_000L
    }
}
