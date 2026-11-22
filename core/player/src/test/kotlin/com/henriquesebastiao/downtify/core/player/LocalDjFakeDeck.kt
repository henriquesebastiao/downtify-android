package com.henriquesebastiao.downtify.core.player

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Real SimpleBasePlayer event semantics with manually advanced audio positions. */
@UnstableApi
internal class LocalDjFakeDeck : SimpleBasePlayer(Looper.getMainLooper()) {
    private var items = emptyList<MediaItem>()
    private var index = 0
    private var position = 0L
    private var buffered: Long? = null
    private var wantsPlay = false
    private var ready = Player.STATE_IDLE
    private var level = 1f
    private var parameters = PlaybackParameters.DEFAULT
    private var repeat = Player.REPEAT_MODE_OFF
    private var shuffle = false
    private var error: PlaybackException? = null
    var releases = 0
        private set

    override fun getState(): State {
        val playlist = items.mapIndexed { ordinal, item ->
            MediaItemData.Builder("fake-$ordinal").setMediaItem(item)
                .setDurationUs((item.mediaMetadata.durationMs ?: 10_000) * 1000)
                .setIsSeekable(true).build()
        }
        return State.Builder().setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(playlist).setCurrentMediaItemIndex(if (items.isEmpty()) C.INDEX_UNSET else index)
            .setContentPositionMs(
                position,
            ).setContentBufferedPositionMs(
                PositionSupplier.getConstant(buffered ?: (items.getOrNull(index)?.mediaMetadata?.durationMs ?: 10_000)),
            )
            .setTotalBufferedDurationMs(PositionSupplier.getConstant(10_000))
            .setPlaybackState(ready).setPlayWhenReady(wantsPlay, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setVolume(level).setPlaybackParameters(parameters).setRepeatMode(repeat).setShuffleModeEnabled(shuffle)
            .setPlayerError(error).build()
    }

    fun position(value: Long) {
        position = value
        invalidateState()
    }
    fun buffered(value: Long) {
        buffered = value
        invalidateState()
    }
    fun nativeIndex(value: Int) {
        index = value
        position = 0
        invalidateState()
    }
    fun fail() {
        ready = Player.STATE_IDLE
        error = PlaybackException(
            "synthetic decoder failure",
            null,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        )
        invalidateState()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        wantsPlay = playWhenReady
        return Futures.immediateVoidFuture()
    }
    override fun handlePrepare(): ListenableFuture<*> {
        ready = Player.STATE_READY
        return Futures.immediateVoidFuture()
    }
    override fun handleStop(): ListenableFuture<*> {
        ready = Player.STATE_IDLE
        return Futures.immediateVoidFuture()
    }
    override fun handleRelease(): ListenableFuture<*> {
        releases++
        return Futures.immediateVoidFuture()
    }
    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        items = mediaItems.toList()
        if (items.isEmpty()) ready = Player.STATE_IDLE
        index = if (startIndex == C.INDEX_UNSET) 0 else startIndex
        position = if (startPositionMs == C.TIME_UNSET) 0 else startPositionMs
        buffered = null
        error = null
        return Futures.immediateVoidFuture()
    }
    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        items = items.filterIndexed { ordinal, _ -> ordinal !in fromIndex until toIndex }
        if (items.isEmpty()) ready = Player.STATE_IDLE
        index = index.coerceAtMost((items.size - 1).coerceAtLeast(0))
        return Futures.immediateVoidFuture()
    }
    override fun handleAddMediaItems(index: Int, mediaItems: List<MediaItem>): ListenableFuture<*> {
        items = items.toMutableList().apply { addAll(index, mediaItems) }
        return Futures.immediateVoidFuture()
    }
    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        index = mediaItemIndex
        position = if (positionMs == C.TIME_UNSET) 0 else positionMs
        return Futures.immediateVoidFuture()
    }
    override fun handleSetVolume(volume: Float, volumeOperationType: Int): ListenableFuture<*> {
        level = volume
        return Futures.immediateVoidFuture()
    }
    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        parameters = playbackParameters
        return Futures.immediateVoidFuture()
    }
    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        repeat = repeatMode
        return Futures.immediateVoidFuture()
    }
    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        shuffle =
            shuffleModeEnabled
        return Futures.immediateVoidFuture()
    }
}

internal fun localDjItem(id: String): MediaItem = MediaItem.Builder().setMediaId(id).setUri(MediaItems.uriFor(id))
    .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(id).setDurationMs(10_000).build()).build()
