package com.henriquesebastiao.downtify.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.henriquesebastiao.downtify.core.data.catalog.CatalogRepository
import com.henriquesebastiao.downtify.core.data.catalog.ServerQueueRepository
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.session.ServerRepository
import com.henriquesebastiao.downtify.core.model.Lyrics
import com.henriquesebastiao.downtify.core.model.PlaybackSpeeds
import com.henriquesebastiao.downtify.core.model.RemoteSong
import com.henriquesebastiao.downtify.core.model.ServerJob
import com.henriquesebastiao.downtify.core.model.ServerJobStatus
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.network.CatalogJson
import com.henriquesebastiao.downtify.core.player.PlayerController
import com.henriquesebastiao.downtify.core.player.PlayerEvent
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.core.player.PlayingStream
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Lyrics of the current track, loaded when the lyrics sheet opens. */
sealed interface LyricsState {
    data object Loading : LyricsState
    data class Loaded(val trackId: String, val lyrics: Lyrics) : LyricsState
}

/** Shared by the mini player and Now Playing (activity scope). */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val player: PlayerController,
    private val library: LibraryRepository,
    server: ServerRepository,
    private val catalog: CatalogRepository,
    queue: ServerQueueRepository,
) : ViewModel() {
    val state: StateFlow<PlayerState> = player.state

    val hasMedia: StateFlow<Boolean> = player.hasMedia

    val events: SharedFlow<PlayerEvent> = player.events

    /**
     * The library track behind the playing stream, once it has been
     * downloaded: matched by title and artist, so the player can offer
     * the library actions (like, lyrics) without switching playback.
     */
    val streamTrack: StateFlow<Track?> =
        combine(player.state, library.library) { s, snapshot ->
            val stream = s.stream ?: return@combine null
            snapshot?.findTrack(stream.title, stream.artist)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val isLiked: StateFlow<Boolean> =
        combine(player.state, library.likedIds, streamTrack) { s, liked, downloaded ->
            (s.track?.id ?: downloaded?.id)?.let { it in liked } == true
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val serverName: StateFlow<String> = server.session.map { it?.serverName.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "")

    /** The server's download job for the playing stream, if it asked for it. */
    val streamJob: StateFlow<ServerJob?> = combine(queue.jobs, player.state) { jobs, s ->
        val stream = s.stream ?: return@combine null
        jobs.values.firstOrNull { job ->
            job.status.isActive && (job.songId == stream.videoId || matches(stream, job))
        } ?: jobs.values.firstOrNull { job ->
            job.status == ServerJobStatus.Done && (job.songId == stream.videoId || matches(stream, job))
        } ?: jobs.values.firstOrNull { job ->
            job.status == ServerJobStatus.Error && (job.songId == stream.videoId || matches(stream, job))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val lyricsState = MutableStateFlow<LyricsState>(LyricsState.Loading)
    val lyrics: StateFlow<LyricsState> = lyricsState.asStateFlow()

    /** How much later than their LRC the shown lyrics run, as set for that track on this phone. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val lyricsOffset: StateFlow<Long> = lyricsState
        .map { (it as? LyricsState.Loaded)?.trackId }
        .distinctUntilChanged()
        .flatMapLatest { id -> id?.let(library::lyricsOffset) ?: flowOf(0L) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0L)

    fun togglePlayPause() = player.togglePlayPause()
    fun next() = player.next()
    fun previous() = player.previous()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun skipBack() = player.skipBack()
    fun skipForward() = player.skipForward()
    fun cycleSpeed() = player.setSpeed(PlaybackSpeeds.next(state.value.speed))
    fun toggleShuffle() = player.toggleShuffle()
    fun cycleRepeat() = player.cycleRepeat()
    fun skipTo(index: Int) = player.skipTo(index)
    fun removeFromQueue(index: Int) = player.removeFromQueue(index)
    fun clearQueued() = player.clearQueued()
    fun moveInQueue(from: Int, after: Int, queued: Boolean) = player.moveInQueue(from, after, queued)
    fun dismissError() = player.dismissError()

    fun toggleLike() {
        val id = state.value.track?.id ?: streamTrack.value?.id ?: return
        val liked = isLiked.value
        viewModelScope.launch { library.setLiked(id, !liked) }
    }

    /** Asks the server to download the playing stream (it keeps streaming). */
    fun downloadStream() {
        val stream = state.value.stream ?: return
        val song = streamSong(stream) ?: return
        viewModelScope.launch { catalog.download(listOf(song)) }
    }

    private fun matches(stream: PlayingStream, job: ServerJob): Boolean =
        job.title.equals(stream.title, ignoreCase = true) &&
            job.artist.equals(stream.artist, ignoreCase = true)

    private fun streamSong(stream: PlayingStream): RemoteSong? = runCatching {
        CatalogJson.song(Json.parseToJsonElement(stream.raw) as JsonObject)
    }.getOrNull()

    /** Loads the lyrics of the current track (cached by the repository). */
    fun loadLyrics() {
        val id = state.value.track?.id ?: streamTrack.value?.id ?: return
        val current = lyricsState.value
        if (current is LyricsState.Loaded && current.trackId == id) return
        lyricsState.value = LyricsState.Loading
        viewModelScope.launch { lyricsState.value = LyricsState.Loaded(id, library.lyrics(id)) }
    }

    /** Moves the shown lyrics [byMs] later (earlier when negative), for an LRC timed to another cut. */
    fun shiftLyrics(byMs: Long) {
        val id = (lyricsState.value as? LyricsState.Loaded)?.trackId ?: return
        viewModelScope.launch { library.shiftLyrics(id, byMs) }
    }

    /** Puts the shown lyrics back on their LRC's times. */
    fun resetLyricsOffset() {
        val id = (lyricsState.value as? LyricsState.Loaded)?.trackId ?: return
        viewModelScope.launch { library.resetLyricsOffset(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
