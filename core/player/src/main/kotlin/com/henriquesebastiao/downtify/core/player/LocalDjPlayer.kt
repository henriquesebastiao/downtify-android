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
}
