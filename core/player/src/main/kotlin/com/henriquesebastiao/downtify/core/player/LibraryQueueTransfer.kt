package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.Track
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Same-process handoff: only a small token crosses MediaSession, never the entire catalog. */
@Singleton
@UnstableApi
class LibraryQueueTransfer @Inject constructor() {
    private data class Request(
        val token: String,
        val owner: String,
        val tracks: List<Track>,
        val start: Int,
        val from: PlaybackContext,
        val shuffle: Boolean,
    )
    private var pending: Request? = null
    private var active: String? = null

    fun cancel() {
        active = null
        pending = null
    }

    fun submit(owner: String, tracks: List<Track>, start: Int, from: PlaybackContext, shuffle: Boolean): String {
        val token = UUID.randomUUID().toString()
        active = token
        pending = Request(token, owner, tracks.toList(), start.coerceIn(tracks.indices), from, shuffle)
        return token
    }

    internal suspend fun play(token: String, player: Player, baseUrl: String?, owner: () -> String?): Boolean {
        val request = pending?.takeIf { it.token == token } ?: return false
        pending = null
        if (!current(request, owner)) return false
        val deck = when (player) {
            is DjSessionPlayer -> player.deck
            is LocalDjPlayer -> player
            else -> return false
        }
        var generation = deck.queueGeneration
        // Materialize shuffle once over ALL occurrences, before batching. Appending must never
        // insert new occurrences before the current shuffle position (Media3's default behavior).
        val ordered = withContext(Dispatchers.Default) {
            if (request.shuffle) request.tracks.shuffled() else request.tracks
        }
        val initialStart = if (request.shuffle) 0 else request.start
        // The chosen song starts immediately, including a tap near the end of a large catalog.
        val indices = (initialStart until ordered.size).toList() + (0 until initialStart).toList()
        var installed = 0
        var loadedBefore = 0
        for (chunk in indices.chunked(BATCH_SIZE)) {
            val batch = if (request.shuffle) chunk else chunk.sorted()
            val items = withContext(Dispatchers.Default) { batch.map { MediaItems.from(ordered[it], baseUrl) } }
            if (!currentQueue(request, owner, deck, generation)) return false
            if (installed == 0) {
                // Only our setMediaItems may advance this generation. A replacement made by
                // another controller, including during listener callbacks, owns the new queue.
                generation++
                installFirst(player, request, items, batch.indexOf(initialStart))
            } else {
                appendBatch(
                    player,
                    items,
                    if (request.shuffle) {
                        0
                    } else {
                        batch.takeWhile {
                            it < initialStart
                        }.size
                    },
                    loadedBefore,
                )
            }
            loadedBefore += batch.count { it < initialStart }
            installed += items.size
            if (!currentQueue(request, owner, deck, generation)) return false
            retainAppendOrder(request, deck)
            if (installed == items.size) {
                player.prepare()
                player.play()
            }
            // Let pause, navigation and the UI run between bounded updates. Never play() again.
            delay(25)
        }
        return currentQueue(request, owner, deck, generation)
    }

    private fun current(request: Request, owner: () -> String?) = active == request.token && owner() == request.owner

    private fun currentQueue(request: Request, owner: () -> String?, player: LocalDjPlayer, generation: Long) =
        current(request, owner) && player.queueGeneration == generation

    private fun retainAppendOrder(request: Request, player: LocalDjPlayer) {
        if (request.shuffle) player.restoreShuffleOrder(IntArray(player.mediaItemCount) { it })
    }

    private fun installFirst(player: Player, request: Request, items: List<MediaItem>, start: Int) {
        player.setMediaItems(items, start, C.TIME_UNSET)
        player.setPlaybackSpeed(1f)
        player.shuffleModeEnabled = request.shuffle
        if (request.shuffle) player.repeatMode = PlayOrder.repeatAfterShuffle(player.repeatMode)
        player.playlistMetadata = MediaItems.contextMetadata(request.from)
    }

    private fun appendBatch(player: Player, items: List<MediaItem>, before: Int, loadedBefore: Int) {
        if (before > 0) player.addMediaItems(loadedBefore, items.take(before))
        if (before < items.size) player.addMediaItems(items.drop(before))
    }

    companion object {
        const val BATCH_SIZE = 500
    }
}
