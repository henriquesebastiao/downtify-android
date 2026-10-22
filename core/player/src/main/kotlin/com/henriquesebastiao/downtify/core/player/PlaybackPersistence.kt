package com.henriquesebastiao.downtify.core.player

import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import com.henriquesebastiao.downtify.core.data.playback.PlaybackRepository
import com.henriquesebastiao.downtify.core.data.playback.playbackOwner
import com.henriquesebastiao.downtify.core.model.PlaybackSnapshot
import com.henriquesebastiao.downtify.core.model.SavedPlaybackItem
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One service-owned checkpoint observer; polling never enumerates or serializes the queue. */
@UnstableApi
internal class PlaybackPersistence(
    private val player: ExoPlayer,
    private val repository: PlaybackRepository,
    private val sessions: SessionStore,
    private val scope: CoroutineScope,
) : Player.Listener {
    private val ready = CompletableDeferred<Unit>()
    private var items: List<SavedPlaybackItem> = emptyList()
    private var mediaItems: List<MediaItem> = emptyList()
    private var order: List<Int> = emptyList()
    private var ticker: Job? = null
    private var owner: String? = null
    private var observing = false

    fun start() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { sessions.ensureLoaded() }
                val paired = sessions.current ?: return@launch
                owner = paired.playbackOwner
                val state = repository.load(paired.playbackOwner)
                if (sessions.current?.playbackOwner != paired.playbackOwner) return@launch
                if (state != null && player.mediaItemCount == 0) {
                    player.setMediaItems(
                        state.items.map {
                            MediaItems.restored(it, paired.baseUrl)
                        },
                        state.index,
                        state.positionMs,
                    )
                    player.setShuffleOrder(DefaultShuffleOrder(state.shuffleOrder.toIntArray(), 0L))
                    player.shuffleModeEnabled = state.shuffle
                    player.repeatMode = state.repeatMode
                    player.setPlaybackSpeed(state.speed)
                    player.playlistMetadata = state.context?.let(MediaItems::contextMetadata) ?: MediaMetadata.EMPTY
                    // Leave STATE_IDLE and do not prepare: reopening must not require network or start audio.
                    player.playWhenReady = false
                }
                captureQueue()
                player.addListener(this@PlaybackPersistence)
                observing = true
                ticker = scope.launch {
                    while (isActive) {
                        delay(CHECKPOINT_MS)
                        if (player.isPlaying) checkpoint()
                    }
                }
            } catch (error: SQLiteException) {
                Log.w("PlaybackPersistence", "Could not restore playback", error)
            } finally {
                ready.complete(Unit)
            }
        }
    }

    suspend fun awaitReady() = ready.await()

    fun stop() {
        if (observing) checkpoint()
        observing = false
        player.removeListener(this)
        ticker?.cancel()
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) captureQueue()
        checkpoint()
    }

    private fun captureQueue() {
        val current = (0 until player.mediaItemCount).map(player::getMediaItemAt)
        val saved = current.mapIndexed { index, item ->
            if (mediaItems.getOrNull(index) === item) items[index] else MediaItems.saved(item)
        }
        // Local cover publication changes the timeline, but not its logical queue.
        // Preserve references so the repository keeps doing a small position UPDATE.
        if (items != saved) items = saved
        mediaItems = current
        val shuffled = PlayOrder.shuffled(player.currentTimeline)
        if (order != shuffled) order = shuffled
    }

    private fun checkpoint() {
        val expected = owner ?: return
        if (sessions.current?.playbackOwner != expected) return
        val index = player.currentMediaItemIndex
        val snapshot = if (index in items.indices) {
            PlaybackSnapshot(
                items = items,
                index = index,
                positionMs = player.currentPosition.coerceAtLeast(0),
                shuffle = player.shuffleModeEnabled,
                shuffleOrder = order,
                repeatMode = player.repeatMode,
                speed = player.playbackParameters.speed,
                context = MediaItems.contextOf(player.playlistMetadata),
            )
        } else {
            null
        }
        repository.save(expected, snapshot)
    }

    private companion object {
        const val CHECKPOINT_MS = 5_000L
    }
}
