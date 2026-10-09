package com.henriquesebastiao.downtify.core.player

import com.henriquesebastiao.downtify.core.data.catalog.CatalogRepository
import com.henriquesebastiao.downtify.core.data.di.ApplicationScope
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.RemoteSong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Playing songs that aren't in the library, in full, through the media
 * session: the tapped row's video resolves first so it starts at once,
 * the rows behind it resolve in the background and join the queue
 * ([PlayerController.appendStreams]), and the player works through to
 * the end, streaming from the server's cache. Nothing here downloads a
 * file or touches the library.
 */
@Singleton
class RemotePlayback @Inject constructor(
    private val player: PlayerController,
    private val catalog: CatalogRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutableResolving = MutableStateFlow<String?>(null)

    /** The id of the song whose stream address is resolving now, if any. */
    val resolvingId: StateFlow<String?> = mutableResolving.asStateFlow()

    /**
     * Plays [song] out of [songs] from [startIndex]. Returns false when
     * nothing resolves (the caller says so); pausing a playing stream is
     * the caller's job — it knows what's on screen. Songs past
     * [MAX_STREAM_SECONDS] never start: the server refuses to stream
     * them, so resolving would only burn a download.
     */
    suspend fun play(songs: List<RemoteSong>, startIndex: Int, from: PlaybackContext): Boolean {
        val song = songs.getOrNull(startIndex) ?: return false
        if (song.durationSeconds > MAX_STREAM_SECONDS) return false
        mutableResolving.value = song.id
        val videoId = catalog.streamVideoId(song)
        mutableResolving.value = null
        if (videoId == null) return false
        player.playStreams(listOf(StreamEntry(song, videoId)), 0, from)
        scope.launch { appendRest(songs, startIndex) }
        return true
    }

    private suspend fun appendRest(songs: List<RemoteSong>, startIndex: Int) {
        val entries = songs.subList(startIndex + 1, songs.size).mapNotNull { song ->
            if (song.durationSeconds > MAX_STREAM_SECONDS) return@mapNotNull null
            catalog.streamVideoId(song)?.let { StreamEntry(song, it) }
        }
        player.appendStreams(entries)
    }

    private companion object {
        /** Mirrors the server's download/stream cap (10 minutes, in seconds). */
        const val MAX_STREAM_SECONDS = 600
    }
}
