package com.henriquesebastiao.downtify.feature.similar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.henriquesebastiao.downtify.core.data.ServerResult
import com.henriquesebastiao.downtify.core.data.catalog.CatalogRepository
import com.henriquesebastiao.downtify.core.data.catalog.ServerQueueRepository
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.library.LibrarySnapshot
import com.henriquesebastiao.downtify.core.data.similar.SimilarRepository
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.RemoteSong
import com.henriquesebastiao.downtify.core.model.ServerJob
import com.henriquesebastiao.downtify.core.model.StreamIds
import com.henriquesebastiao.downtify.core.model.StreamVideo
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.player.NowPlayingRef
import com.henriquesebastiao.downtify.core.player.PlayerController
import com.henriquesebastiao.downtify.core.player.PlayingStream
import com.henriquesebastiao.downtify.core.player.RemotePlayback
import com.henriquesebastiao.downtify.core.player.StreamEntry
import com.henriquesebastiao.downtify.feature.player.LyricsState
import com.henriquesebastiao.downtify.ui.common.LoadError
import com.henriquesebastiao.downtify.ui.common.loadError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SimilarUiState(
    val songs: List<RemoteSong> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
    val error: LoadError? = null,
    val playingStreamId: String? = null,
    val resolvingStreamId: String? = null,
    /** The server's download queue, by song id. */
    val jobs: Map<String, ServerJob> = emptyMap(),
    /** More rows are being appended past the current page. */
    val loadingMore: Boolean = false,
    /** False once a page adds nothing new: the radio ran dry. */
    val hasMore: Boolean = true,
    /** The synced library, for matching downloaded songs back to tracks. */
    val library: LibrarySnapshot? = null,
    /** Liked library track ids. */
    val likedIds: Set<String> = emptySet(),
)

/** One-off messages for the snackbar. */
sealed interface SimilarMessage {
    data class Queued(val title: String) : SimilarMessage
    data object NoStream : SimilarMessage
    data object RequestFailed : SimilarMessage
    data object Unreachable : SimilarMessage
}

/** Tracks that sound like one track, from YouTube Music's radio mix. No key needed. */
@HiltViewModel
class SimilarViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val similar: SimilarRepository,
    private val catalog: CatalogRepository,
    queue: ServerQueueRepository,
    private val player: PlayerController,
    private val remotePlayback: RemotePlayback,
    private val library: LibraryRepository,
) : ViewModel() {
    /** The form keeps synchronous state so typing stays instant. */
    var artist by mutableStateOf(savedState[KEY_ARTIST] ?: "")
        private set
    var track by mutableStateOf(savedState[KEY_TRACK] ?: "")
        private set

    private val songs = MutableStateFlow<List<RemoteSong>>(emptyList())
    private val loading = MutableStateFlow(false)
    private val searched = MutableStateFlow(false)
    private val error = MutableStateFlow<LoadError?>(null)
    private val messageChannel = Channel<SimilarMessage>(Channel.BUFFERED)

    val messages: Flow<SimilarMessage> = messageChannel.receiveAsFlow()

    private data class Basics(
        val songs: List<RemoteSong>,
        val loading: Boolean,
        val searched: Boolean,
        val error: LoadError?,
    )

    private val loadingMore = MutableStateFlow(false)
    private val hasMore = MutableStateFlow(true)

    private data class More(val loadingMore: Boolean, val hasMore: Boolean)

    private data class Playing(val videoId: String?, val resolvingId: String?, val jobs: Map<String, ServerJob>)

    val uiState: StateFlow<SimilarUiState> = combine(
        combine(songs, loading, searched, error, ::Basics),
        combine(library.library, library.likedIds) { snapshot, liked -> snapshot to liked.toSet() },
        combine(loadingMore, hasMore, ::More),
        combine(player.nowPlaying, remotePlayback.resolvingId, queue.jobs) {
                playing: NowPlayingRef,
                resolvingId: String?,
                jobs: Map<String, ServerJob>,
            ->
            Playing(playing.streamVideoId, resolvingId, jobs)
        },
    ) { basics: Basics, library: Pair<LibrarySnapshot?, Set<String>>, more: More, playing: Playing ->
        SimilarUiState(
            basics.songs,
            basics.loading,
            basics.searched,
            basics.error,
            playing.videoId,
            playing.resolvingId,
            playing.jobs,
            more.loadingMore,
            more.hasMore,
            library.first,
            library.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SimilarUiState())

    private var searching: Job? = null

    private var extending = false

    init {
        if (artist.isNotBlank() || track.isNotBlank()) search()
        viewModelScope.launch { followRouteArgs() }
        viewModelScope.launch {
            player.state
                .map { state -> state.stream?.videoId to state.queue.lastOrNull()?.trackId }
                .distinctUntilChanged()
                .collect { maybeExtend() }
        }
    }

    /**
     * The player can open Similar for another track while its page is
     * already on top: follow the route's arguments so the mix follows.
     */
    private suspend fun followRouteArgs() {
        savedState.getStateFlow(KEY_ARTIST, artist)
            .combine(savedState.getStateFlow(KEY_TRACK, track)) { a, t -> a to t }
            .distinctUntilChanged()
            .collect { (a, t) ->
                if (a.isBlank() && t.isBlank()) return@collect
                if (a == artist && t == track) return@collect
                artist = a
                track = t
                search()
            }
    }

    /**
     * The playing stream reached the list tail: grow the list from it and
     * queue what was added, so playback never stops at twenty. Backs off
     * the moment the queue moved on without this page.
     */
    private fun maybeExtend() {
        if (extending) return
        if (loading.value || loadingMore.value) return
        if (!hasMore.value) return
        val last = playingTail() ?: return
        extending = true
        viewModelScope.launch {
            try {
                val added = appendNextPage()
                val entries = added.mapNotNull { song ->
                    val id = StreamVideo.videoIdOf(song) ?: catalog.streamVideoId(song)
                    id?.let { StreamEntry(song, it) }
                }
                if (entries.isNotEmpty()) player.appendStreams(entries)
            } finally {
                extending = false
            }
        }
    }

    /** The list tail, when it is what is playing now and ends the queue. */
    private fun playingTail(): RemoteSong? {
        val last = songs.value.lastOrNull() ?: return null
        val stream = player.state.value.stream ?: return null
        if (!streamMatches(stream, last)) return null
        if (!queueEndsAt(last)) return null
        return last
    }

    private fun streamMatches(stream: PlayingStream, song: RemoteSong): Boolean {
        val id = StreamVideo.videoIdOf(song)
        if (id == null) {
            return stream.artist.equals(song.artist, ignoreCase = true) &&
                stream.title.equals(song.title, ignoreCase = true)
        }
        return id == stream.videoId
    }

    private fun queueEndsAt(song: RemoteSong): Boolean {
        val tail = player.state.value.queue.lastOrNull() ?: return false
        val id = StreamVideo.videoIdOf(song)
        if (id == null) {
            return tail.title.equals(song.title, ignoreCase = true) &&
                tail.artist.equals(song.artist, ignoreCase = true)
        }
        return tail.trackId == StreamIds.mediaId(id)
    }

    fun onArtistChange(value: String) {
        artist = value
    }

    fun onTrackChange(value: String) {
        track = value
    }

    fun search() {
        searching?.cancel()
        if (artist.isBlank() && track.isBlank()) return
        searching = viewModelScope.launch {
            loading.value = true
            error.value = null
            // A new seed restarts the endless list.
            hasMore.value = true
            loadingMore.value = false
            when (val result = similar.similar(artist, track)) {
                is ServerResult.Ok -> {
                    songs.value = result.value
                    searched.value = true
                }

                else -> error.value = result.loadError()
            }
            loading.value = false
        }
    }

    fun retry() = search()

    /**
     * Appends the next page, seeded from the last row: the radio mix is
     * finite per request, so the list grows by chaining seeds. Ends when
     * a page adds nothing new.
     */
    fun loadMore() {
        if (loading.value || loadingMore.value) return
        if (!hasMore.value || songs.value.isEmpty()) return
        viewModelScope.launch { appendNextPage() }
    }

    /**
     * Returns the songs [appendNextPage] added, for the player queue to
     * grow with the list.
     */
    private suspend fun appendNextPage(): List<RemoteSong> {
        val seed = songs.value.lastOrNull() ?: return emptyList()
        val seedArtist = seed.artists.firstOrNull().orEmpty()
        if (seedArtist.isBlank() || seed.title.isBlank()) {
            hasMore.value = false
            return emptyList()
        }
        loadingMore.value = true
        try {
            val fresh = when (val result = similar.similar(seedArtist, seed.title)) {
                is ServerResult.Ok -> result.value.filter { song -> songs.value.none { sameSong(it, song) } }
                else -> emptyList()
            }
            if (fresh.isNotEmpty()) songs.value = songs.value + fresh else hasMore.value = false
            return fresh
        } finally {
            loadingMore.value = false
        }
    }

    private fun sameSong(a: RemoteSong, b: RemoteSong): Boolean = (a.id.isNotBlank() && a.id == b.id) ||
        (a.title.equals(b.title, ignoreCase = true) && a.artist.equals(b.artist, ignoreCase = true))

    fun pivot(song: RemoteSong) {
        artist = song.artist
        track = song.title
        search()
    }

    fun playRemote(song: RemoteSong, songs: List<RemoteSong>) {
        val playing = uiState.value.playingStreamId
        if (playing != null && StreamVideo.videoIdOf(song) == playing && player.state.value.isPlaying) {
            player.pause()
            return
        }
        val at = songs.indexOf(song).coerceAtLeast(0)
        viewModelScope.launch {
            val played = remotePlayback.play(songs, at, PlaybackContext(PlaybackContextType.Songs, "", ""))
            if (!played) messageChannel.send(SimilarMessage.NoStream)
        }
    }

    fun download(song: RemoteSong) {
        viewModelScope.launch {
            val message = when (catalog.download(listOf(song))) {
                is ServerResult.Ok -> SimilarMessage.Queued(song.title)
                ServerResult.Unreachable -> SimilarMessage.Unreachable
                is ServerResult.Failed -> SimilarMessage.RequestFailed
            }
            messageChannel.send(message)
        }
    }

    fun toggleLike(track: Track) {
        viewModelScope.launch { library.setLiked(track.id, track.id !in uiState.value.likedIds) }
    }

    private val lyricsSheet = MutableStateFlow<LyricsState?>(null)
    val lyricsFor: StateFlow<LyricsState?> = lyricsSheet

    /** Lyrics of a downloaded song, shown in place instead of its download button. */
    fun showLyrics(track: Track) {
        lyricsSheet.value = LyricsState.Loading
        viewModelScope.launch { lyricsSheet.value = LyricsState.Loaded(track.id, library.lyrics(track.id)) }
    }

    fun hideLyrics() {
        lyricsSheet.value = null
    }

    private companion object {
        const val KEY_ARTIST = "artist"
        const val KEY_TRACK = "track"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
