package com.henriquesebastiao.downtify.core.player

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.henriquesebastiao.downtify.core.data.di.ApplicationScope
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.library.RecentsRepository
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PodcastEpisode
import com.henriquesebastiao.downtify.core.model.PodcastShow
import com.henriquesebastiao.downtify.core.model.RemoteSong
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The UI's handle on playback: a [MediaController] connected to
 * [PlaybackService], exposed as a [StateFlow]. The activity [connect]s it on
 * start and [release]s it on stop, so the service can stop once paused.
 */
@OptIn(UnstableApi::class)
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: LibraryRepository,
    private val recents: RecentsRepository,
    private val sessions: SessionStore,
    private val resolver: StreamResolver,
    @ApplicationScope scope: CoroutineScope,
) {
    private val main = CoroutineScope(scope.coroutineContext + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var controller: MediaController? = null
    private var ticker: Job? = null
    private val mutableState = MutableStateFlow(PlayerState())

    val state: StateFlow<PlayerState> = mutableState.asStateFlow()

    /** The playing track and context, without position ticks. */
    val nowPlaying: StateFlow<NowPlayingRef> = mutableState
        .map {
            NowPlayingRef(
                it.track?.id,
                it.episode?.episodeId,
                it.stream?.videoId,
                it.isPlaying,
                it.context,
            )
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, NowPlayingRef())

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh()
        }

        override fun onPlayerError(error: PlaybackException) {
            mutableState.update { it.copy(error = classify(error)) }
        }
    }

    init {
        // Library changes (a sync) can fill in details of the playing track.
        main.launch { library.library.collect { refresh() } }
    }

    suspend fun connect(): MediaController = mutex.withLock {
        controller?.let { return it }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val built = withContext(Dispatchers.Main) { MediaController.Builder(context, token).buildAsync().await() }
        built.addListener(listener)
        controller = built
        refresh()
        built
    }

    fun release() {
        main.launch {
            mutex.withLock {
                ticker?.cancel()
                controller?.removeListener(listener)
                controller?.release()
                controller = null
            }
        }
    }

    /** Plays [tracks] from [startIndex] as the new queue, remembering where they came from. */
    fun play(tracks: List<Track>, startIndex: Int, from: PlaybackContext, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        main.launch {
            val player = connect()
            val baseUrl = sessions.current?.baseUrl
            val start = if (shuffle && startIndex == 0) tracks.indices.random() else startIndex.coerceIn(tracks.indices)
            player.setMediaItems(tracks.map { MediaItems.from(it, baseUrl) }, start, C.TIME_UNSET)
            player.setPlaybackSpeed(1f)
            player.shuffleModeEnabled = shuffle
            player.playlistMetadata = MediaItems.contextMetadata(from)
            player.prepare()
            player.play()
            mutableState.update { it.copy(error = null) }
            recents.record(from, tracks[start].takeIf { it.hasCover }?.id ?: tracks.firstOrNull { it.hasCover }?.id)
        }
    }

    /**
     * Plays songs that aren't in the library, in full, from the server's
     * stream cache — the queue works through downloaded and streamed rows
     * alike. Streaming rows never reach [recents]: there is no library id
     * to remember them by.
     */
    fun playStreams(entries: List<StreamEntry>, startIndex: Int, from: PlaybackContext, shuffle: Boolean = false) {
        if (entries.isEmpty()) return
        main.launch {
            val player = connect()
            val start = if (shuffle && startIndex == 0) {
                entries.indices.random()
            } else {
                startIndex.coerceIn(entries.indices)
            }
            player.setMediaItems(entries.map { MediaItems.stream(it.song, it.videoId) }, start, C.TIME_UNSET)
            player.setPlaybackSpeed(1f)
            player.shuffleModeEnabled = shuffle
            player.playlistMetadata = MediaItems.contextMetadata(from)
            player.prepare()
            player.play()
            mutableState.update { it.copy(error = null) }
        }
    }

    /**
     * Appends streamed songs behind the current one: the rows after a tap
     * resolve in the background while the tapped song already plays.
     */
    fun appendStreams(entries: List<StreamEntry>) {
        if (entries.isEmpty()) return
        main.launch {
            val player = connect()
            val baseUrl = sessions.current?.baseUrl ?: return@launch
            player.addMediaItems(entries.map { MediaItems.stream(it.song, it.videoId) })
        }
    }

    /** Plays a downloaded episode from where the server says the user stopped. */
    fun playEpisode(episode: PodcastEpisode, show: PodcastShow) {
        val baseUrl = sessions.current?.baseUrl ?: return
        val item = MediaItems.episode(episode, show, baseUrl) ?: return
        main.launch {
            val player = connect()
            player.setMediaItem(item, (episode.resumeAtSeconds ?: 0) * MS_PER_SECOND)
            player.shuffleModeEnabled = false
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.playlistMetadata = MediaMetadata.EMPTY
            player.prepare()
            player.play()
            mutableState.update { it.copy(error = null) }
        }
    }

    /** 10 seconds back, for an episode. */
    fun skipBack() = command { it.seekBack() }

    /** 30 seconds forward, for an episode. */
    fun skipForward() = command { it.seekForward() }

    fun setSpeed(speed: Float) = command { it.setPlaybackSpeed(speed) }

    fun togglePlayPause() = command {
        if (it.isPlaying) {
            it.pause()
        } else {
            if (it.playbackState == Player.STATE_IDLE) it.prepare()
            if (it.playbackState == Player.STATE_ENDED) it.seekToDefaultPosition()
            it.play()
        }
    }

    fun pause() = command { it.pause() }

    fun resume() = command { it.play() }

    fun next() = command { it.seekToNext() }

    fun previous() = command { it.seekToPrevious() }

    fun seekTo(positionMs: Long) = command {
        it.seekTo(positionMs)
        mutableState.update { s -> s.copy(positionMs = positionMs) }
    }

    fun skipTo(index: Int) = command { it.seekToDefaultPosition(index) }

    fun toggleShuffle() = command { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = command {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun dismissError() = mutableState.update { it.copy(error = null) }

    private fun command(block: (MediaController) -> Unit) {
        main.launch { block(connect()) }
    }

    private data class CurrentContent(val track: Track?, val episode: PlayingEpisode?, val stream: PlayingStream?)

    private fun contentOf(item: MediaItem?): CurrentContent {
        val episode = item?.let(MediaItems::episodeOf)
        val stream = if (episode == null) item?.let(MediaItems::streamOf) else null
        val track = if (episode == null && stream == null) {
            item?.mediaId?.let { library.library.value?.byId?.get(it) }
        } else {
            null
        }
        return CurrentContent(track, episode, stream)
    }

    private fun isNewContent(old: PlayerState, content: CurrentContent): Boolean = old.track?.id != content.track?.id ||
        old.episode?.episodeId != content.episode?.episodeId ||
        old.stream?.videoId != content.stream?.videoId

    private fun refresh() {
        val player = controller ?: return
        val item = player.currentMediaItem
        val (track, episode, stream) = contentOf(item)
        val queue = (0 until player.mediaItemCount).map { i ->
            val entry = player.getMediaItemAt(i)
            QueueEntry(
                index = i,
                trackId = entry.mediaId,
                title = entry.mediaMetadata.title?.toString().orEmpty(),
                artist = entry.mediaMetadata.artist?.toString().orEmpty(),
            )
        }
        mutableState.update { old ->
            old.copy(
                track = track,
                episode = episode,
                stream = stream,
                speed = player.playbackParameters.speed,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = player.duration.takeIf { it != C.TIME_UNSET }
                    ?: ((track?.duration ?: 0.0) * 1000).toLong().takeIf { it > 0 }
                    ?: item?.mediaMetadata?.durationMs
                    ?: 0L,
                shuffle = player.shuffleModeEnabled,
                repeat = when (player.repeatMode) {
                    Player.REPEAT_MODE_ALL -> RepeatMode.All
                    Player.REPEAT_MODE_ONE -> RepeatMode.One
                    else -> RepeatMode.Off
                },
                queue = queue,
                currentIndex = player.currentMediaItemIndex,
                context = MediaItems.contextOf(player.playlistMetadata),
                quality = item?.mediaId?.let(resolver::qualityOf),
                fromPhone = item?.mediaId?.let(resolver::isLocal) == true,
                error = if (isNewContent(old, CurrentContent(track, episode, stream))) null else old.error,
            )
        }
        if (player.isPlaying) startTicker() else ticker?.cancel()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = main.launch {
            while (isActive) {
                val player = controller ?: break
                mutableState.update { it.copy(positionMs = player.currentPosition.coerceAtLeast(0)) }
                delay(TICK_MS)
            }
        }
    }

    private fun classify(error: PlaybackException): PlaybackError {
        val cause = error.cause
        val isStream = controller?.currentMediaItem?.let(MediaItems::streamOf) != null
        return when {
            cause is HttpDataSource.InvalidResponseCodeException && cause.responseCode == 401 ->
                PlaybackError.Unauthorized

            cause is HttpDataSource.InvalidResponseCodeException && cause.responseCode == 404 && isStream ->
                PlaybackError.OldServer

            cause is HttpDataSource.InvalidResponseCodeException && cause.responseCode == 404 ->
                PlaybackError.NotFound

            error.errorCode in NETWORK_ERRORS -> PlaybackError.Network

            else -> PlaybackError.Other
        }
    }

    private companion object {
        const val MS_PER_SECOND = 1000L
        const val TICK_MS = 500L
        val NETWORK_ERRORS = setOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )
    }
}

/** One not-downloaded row with the video the server streams for it. */
data class StreamEntry(val song: RemoteSong, val videoId: String)
