package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer.State
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
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

    fun resetForOwner() {
        setEnabled(false)
        engine.setPlaying(false)
        clearMediaItems()
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
        while (occurrenceIds.size < state.playlist.size) occurrenceIds.add(Any())
        val stable = state.playlist.mapIndexed { index, row ->
            val builder = row.buildUpon().setUid(occurrenceIds[index])
            if (row.mediaItem.localConfiguration?.uri?.let(MediaItems::trackIdOf) !=
                null
            ) {
                builder.setPeriods(emptyList())
            }
            builder.build()
        }
        val normalized = state.buildUpon().setPlaylist(stable).build()
        return normalized.buildUpon().setPlaylist(
            LocalDjTimeline(normalized.timeline, state.timeline),
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
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ->
                nextInsideDj()?.let { return it }

            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ->
                previousInsideDj()?.let { return it }
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
        return super.handleSetShuffleModeEnabled(shuffleModeEnabled)
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        engine.reset()
        occurrenceIds.clear()
        repeat(mediaItems.size) { occurrenceIds.add(Any()) }
        return super.handleSetMediaItems(mediaItems, startIndex, startPositionMs)
    }

    override fun handleAddMediaItems(index: Int, mediaItems: List<MediaItem>): ListenableFuture<*> {
        engine.reset()
        occurrenceIds.addAll(index, List(mediaItems.size) { Any() })
        return super.handleAddMediaItems(index, mediaItems)
    }

    override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int): ListenableFuture<*> {
        engine.reset()
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
        engine.reset()
        occurrenceIds.subList(fromIndex, toIndex).clear()
        occurrenceIds.addAll(fromIndex, List(mediaItems.size) { Any() })
        return super.handleReplaceMediaItems(fromIndex, toIndex, mediaItems)
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        engine.reset()
        occurrenceIds.subList(fromIndex, toIndex).clear()
        return super.handleRemoveMediaItems(fromIndex, toIndex)
    }

    internal fun pulseForTest() = engine.pulse()

    /** Asked when a controller presses Next inside an interactive DJ render. */
    private var djNext: (() -> Unit)? = null

    fun setDjNextHandler(handler: (() -> Unit)?) {
        djNext = handler
    }

    /**
     * Next inside a DJ render stays inside the mix: a prepared render jumps to its next marker,
     * an interactive one asks the server for the next transition. Letting the player advance
     * would leave the mix for the classic queue item stored right after it — the reported
     * Android Auto behaviour.
     */
    private fun nextInsideDj(): ListenableFuture<*>? {
        val dj = DjMediaItems.of(currentMediaItem ?: return null) ?: return null
        if (dj.mode == "interactive") {
            djNext?.invoke() ?: return null
            return Futures.immediateVoidFuture()
        }
        if (dj.nextStart == Long.MAX_VALUE || dj.nextStart <= 0) return null
        scope.launch { seekTo(currentMediaItemIndex, dj.nextStart) }
        return Futures.immediateVoidFuture()
    }

    /** Previous inside a DJ render restarts the track, or returns to the previous marker. */
    private fun previousInsideDj(): ListenableFuture<*>? {
        val item = currentMediaItem ?: return null
        if (DjMediaItems.of(item) == null) return null
        val positionMs = currentPosition
        val rows = DjTimeline.resumeRows(item)
        val seconds = positionMs / 1000.0
        val row = rows.indexOfFirst { it.start <= seconds && seconds < it.end }
        val target = when {
            rows.isEmpty() -> return null
            row < 0 -> rows.last().start
            positionMs - (rows[row].start * 1000).toLong() > RESTART_MS -> rows[row].start
            row > 0 -> rows[row - 1].start
            else -> rows[row].start
        }
        scope.launch { seekTo(currentMediaItemIndex, (target * 1000).toLong()) }
        return Futures.immediateVoidFuture()
    }

    private companion object {
        /** Listening longer than this restarts the track instead of stepping back. */
        const val RESTART_MS = 3_000L
    }
}
