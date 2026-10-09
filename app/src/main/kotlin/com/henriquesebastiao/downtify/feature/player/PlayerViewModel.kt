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
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.network.CatalogJson
import com.henriquesebastiao.downtify.core.player.PlayerController
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.core.player.PlayingStream
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
            !job.status.isActive && job.songId == stream.videoId
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val lyricsState = MutableStateFlow<LyricsState>(LyricsState.Loading)
    val lyrics: StateFlow<LyricsState> = lyricsState.asStateFlow()

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

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
