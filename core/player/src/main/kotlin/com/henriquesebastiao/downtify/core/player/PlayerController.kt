package com.henriquesebastiao.downtify.core.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.henriquesebastiao.downtify.core.data.di.ApplicationScope
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.library.RecentsRepository
import com.henriquesebastiao.downtify.core.data.playback.playbackOwner
import com.henriquesebastiao.downtify.core.model.DjIds
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
// One small function per thing the UI can ask of playback: the count follows the feature list.
@Suppress("TooManyFunctions")
@OptIn(UnstableApi::class)
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: LibraryRepository,
    private val recents: RecentsRepository,
    private val sessions: SessionStore,
    private val resolver: StreamResolver,
    private val readAhead: ReadAheadBuffer,
    @ApplicationScope scope: CoroutineScope,
) {
    private val main = CoroutineScope(scope.coroutineContext + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var controller: MediaController? = null
    private var ticker: Job? = null
    private val mutableState = MutableStateFlow(PlayerState())
    private val mutableSelectionVersion = MutableStateFlow(0L)
    val selectionVersion = mutableSelectionVersion.asStateFlow()
    private val mutableEvents = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 1)

    /** Everything, position ticks included (twice a second while playing): collect it only where shown. */
    val state: StateFlow<PlayerState> = mutableState.asStateFlow()

    /** Whether anything is loaded, for the shell (which must not recompose on every tick). */
    val hasMedia: StateFlow<Boolean> = mutableState
        .map { it.hasMedia }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Snackbar-worthy moments, such as a song added to the queue. */
    val events: SharedFlow<PlayerEvent> = mutableEvents.asSharedFlow()

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
            refresh(queueChanged = QUEUE_EVENTS.any(events::contains))
        }

        override fun onPlayerError(error: PlaybackException) {
            mutableState.update { it.copy(error = classify(error)) }
        }
    }

    init {
        main.launch {
            readAhead.state.collect { status ->
                mutableState.update { it.copy(readAheadReady = status.ready, readAheadTarget = status.target) }
            }
        }
        // Library changes (a sync) can fill in details of the playing track.
        main.launch { library.library.collect { refresh() } }
    }

    suspend fun connect(): MediaController = mutex.withLock {
        controller?.takeIf { it.isConnected }?.let { return it }
        controller?.removeListener(listener)
        controller?.release()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val built = withContext(Dispatchers.Main) { MediaController.Builder(context, token).buildAsync().await() }
        built.addListener(listener)
        controller = built
        refresh(queueChanged = true)
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

    /** Voice intents use the same local search resolver as Android Auto. Never search or download online. */
    suspend fun playLibrarySearch(query: String) {
        if (query.isBlank()) return
        mutableSelectionVersion.value++
        val owner = withContext(Dispatchers.IO) { sessions.current?.playbackOwner } ?: return
        val player = connect()
        if (sessions.current?.playbackOwner != owner) return
        val request = MediaItem.RequestMetadata.Builder().setSearchQuery(query).build()
        player.setMediaItem(MediaItem.Builder().setRequestMetadata(request).build())
        player.prepare()
        player.play()
    }

    /** Plays [tracks] from [startIndex] as the new queue, remembering where they came from. */
    fun startMix(seed: Track, tracks: List<Track>) {
        val owner = sessions.current?.playbackOwner ?: return
        val expected = state.value.mediaKey
        main.launch {
            val player = connect()
            if (sessions.current?.playbackOwner != owner || state.value.mediaKey != expected) return@launch
            val from =
                PlaybackContext(
                    com.henriquesebastiao.downtify.core.model.PlaybackContextType.Songs,
                    "",
                    "Mix · ${seed.displayTitle}",
                )
            if (state.value.track?.id != seed.id) {
                play(listOf(seed) + tracks, 0, from)
                return@launch
            }
            // Rebuild only what follows: Media3 retains current position and decoder.
            val index = player.currentMediaItemIndex
            if (index + 1 < player.mediaItemCount) player.removeMediaItems(index + 1, player.mediaItemCount)
            if (index > 0) player.removeMediaItems(0, index)
            player.addMediaItems(tracks.map { MediaItems.from(it, sessions.current?.baseUrl) })
            player.shuffleModeEnabled = false
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.playlistMetadata = MediaItems.contextMetadata(from)
        }
    }

    fun play(tracks: List<Track>, startIndex: Int, from: PlaybackContext, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        mutableSelectionVersion.value++
        main.launch {
            val player = connect()
            val baseUrl = sessions.current?.baseUrl
            val start = startIndex.coerceIn(tracks.indices)
            player.setMediaItems(tracks.map { MediaItems.from(it, baseUrl) }, start, C.TIME_UNSET)
            player.setPlaybackSpeed(1f)
            player.shuffleModeEnabled = shuffle
            if (shuffle) {
                player.repeatMode = PlayOrder.repeatAfterShuffle(player.repeatMode)
                if (startIndex == 0) player.shuffleFromHead()
            }
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
        mutableSelectionVersion.value++
        main.launch {
            val player = connect()
            val start = startIndex.coerceIn(entries.indices)
            player.setMediaItems(entries.map { MediaItems.stream(it.song, it.videoId) }, start, C.TIME_UNSET)
            player.setPlaybackSpeed(1f)
            player.shuffleModeEnabled = shuffle
            if (shuffle) {
                player.repeatMode = PlayOrder.repeatAfterShuffle(player.repeatMode)
                if (startIndex == 0) player.shuffleFromHead()
            }
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
        mutableSelectionVersion.value++
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
        if (it.playWhenReady) {
            it.pause()
        } else {
            if (it.playbackState == Player.STATE_IDLE) it.prepare()
            if (it.playbackState == Player.STATE_ENDED) it.seekToDefaultPosition()
            it.play()
        }
    }

    fun pause() = command { it.pause() }

    /** Insert a render without discarding the classic queue. All media uses the same session. */
    fun playDj(
        id: String,
        title: String,
        durationMs: Long,
        mode: String = "prepared",
        expectedSelection: Long? = null,
        sessionPlan: com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto? = null,
    ) {
        val owner = sessions.current?.playbackOwner ?: return
        val initialItem = DjMediaItems.from(id, title, durationMs, mode)
        main.launch {
            val player = connect()
            if (sessions.current?.playbackOwner != owner) return@launch
            if (expectedSelection != null && mutableSelectionVersion.value != expectedSelection) return@launch
            mutableSelectionVersion.value++
            val extras = djClassicExtras(player, mode)
            val base = initialItem.buildUpon().setMediaMetadata(
                initialItem.mediaMetadata.buildUpon().setExtras(extras).build(),
            ).build()
            val item =
                sessionPlan?.let { DjTimeline.metadata(base, it, 0, sessions.current?.baseUrl.orEmpty()) } ?: base
            val existing = (0 until player.mediaItemCount).firstOrNull {
                player.getMediaItemAt(it).mediaId ==
                    item.mediaId
            }
            val index = existing ?: (player.currentMediaItemIndex + 1).coerceIn(0, player.mediaItemCount)
            if (existing == null) player.addMediaItem(index, item)
            player.seekTo(index, 0)
            player.setPlaybackSpeed(1f)
            player.prepare()
            player.play()
        }
    }

    private fun djClassicExtras(player: Player, mode: String): Bundle {
        val previous = player.currentMediaItem?.mediaMetadata?.extras
        val wasDj = DjIds.referenceOf(player.currentMediaItem?.mediaId) != null
        return Bundle().apply {
            putInt(
                "djClassicIndex",
                if (wasDj) {
                    previous?.getInt(
                        "djClassicIndex",
                    ) ?: 0
                } else {
                    player.currentMediaItemIndex.coerceAtLeast(0)
                },
            )
            putLong(
                "djClassicPosition",
                if (wasDj) previous?.getLong("djClassicPosition") ?: 0 else player.currentPosition.coerceAtLeast(0),
            )
            putInt("djQueueAnchor", getInt("djClassicIndex"))
            if (mode == "interactive") putBoolean("djStartOnLoad", true)
        }
    }

    fun queueDj(
        id: String,
        title: String,
        durationMs: Long,
        sessionPlan: com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto? = null,
        preserveQueue: Boolean = true,
    ) = command {
        val item = DjMediaItems.from(id, title, durationMs, "interactive")
        val extras = Bundle().apply {
            putBoolean("djStartOnLoad", true)
            putBoolean("djOwnQueue", preserveQueue)
            putInt("djClassicIndex", it.currentMediaItemIndex + 1)
            putInt("djQueueAnchor", it.currentMediaItemIndex + 1)
            putLong("djClassicPosition", 0)
        }
        val base = item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build()
        val planned =
            sessionPlan?.let { plan -> DjTimeline.metadata(base, plan, 0, sessions.current?.baseUrl.orEmpty()) } ?: base
        it.addMediaItem(
            (it.currentMediaItemIndex + 1).coerceIn(0, it.mediaItemCount),
            planned,
        )
    }

    fun exitDj(id: String) = command { player ->
        val current = player.currentMediaItem
        if (DjIds.referenceOf(current?.mediaId)?.id == id && current != null) {
            restoreClassic(player, current)
            return@command
        }
        for (index in (0 until player.mediaItemCount).reversed()) {
            if (DjIds.referenceOf(player.getMediaItemAt(index).mediaId)?.id == id) {
                if (index == player.currentMediaItemIndex) player.pause()
                player.removeMediaItem(index)
            }
        }
    }

    private fun restoreClassic(player: Player, current: MediaItem) {
        val extras = current.mediaMetadata.extras
        val absolute = DjTimeline.position(player, extras?.getLong("djOrigin") ?: 0)
        val point = DjTimeline.resumePoint(current, absolute)
        val (selection, index) = classicSelection(player, current, point)
        val wantsPlay = player.playWhenReady
        if (selection.isNotEmpty() && index >= 0) {
            player.setMediaItems(selection, index, point?.sourceMs ?: extras?.getLong("djClassicPosition") ?: 0)
            player.setPlaybackSpeed(1f)
            player.prepare()
            player.playWhenReady = wantsPlay
        } else {
            player.pause()
            player.clearMediaItems()
        }
    }

    private fun classicSelection(
        player: Player,
        current: MediaItem,
        point: DjResumePoint?,
    ): Pair<List<MediaItem>, Int> {
        val extras = current.mediaMetadata.extras
        val classic = (0 until player.mediaItemCount).map(player::getMediaItemAt).filter { row ->
            DjIds.referenceOf(row.mediaId) ==
                null
        }
        val anchor = extras?.getInt("djQueueAnchor") ?: 0
        val preferred = anchor + (point?.occurrence ?: 0)
        val index = if (point != null) {
            if (classic.getOrNull(preferred)?.mediaId ==
                point.id
            ) {
                preferred
            } else {
                classic.indexOfFirst { row -> row.mediaId == point.id }
            }
        } else {
            (extras?.getInt("djClassicIndex") ?: 0).coerceIn(0, (classic.size - 1).coerceAtLeast(0))
        }
        val needsRenderQueue = extras?.getBoolean("djOwnQueue") != true ||
            extras.getBoolean("djQueueOrder") != true || index < 0
        if (point != null && needsRenderQueue) {
            return renderedSelection(current, point)
        }
        return classic to index
    }

    private fun renderedSelection(current: MediaItem, point: DjResumePoint): Pair<List<MediaItem>, Int> {
        val rows = DjTimeline.resumeRows(current)
        val fallback = DjTimeline.fallbackItems(current, sessions.current?.baseUrl.orEmpty())
        val items = rows.withIndex().mapNotNull { (ordinal, row) ->
            val media =
                library.library.value?.byId?.get(row.id)?.let { track ->
                    MediaItems.from(track, sessions.current?.baseUrl)
                }
                    ?: fallback[row.id]
            media?.let { ordinal to it }
        }
        val occurrence =
            rows.indexOfFirst { row ->
                row.occurrence >= 0 && row.occurrence == point.occurrence
            }.takeIf { ordinal ->
                ordinal >=
                    0
            }
                ?: point.occurrence
        return items.map { pair -> pair.second } to items.indexOfFirst { pair -> pair.first == occurrence }
    }

    fun resume() = command {
        if (it.playbackState == Player.STATE_IDLE) it.prepare()
        it.play()
    }

    fun next() = command {
        if (state.value.dj?.mode == "interactive") {
            it.sendCustomCommand(SessionCommand(PlaybackService.ACTION_DJ_NEXT, Bundle.EMPTY), Bundle.EMPTY)
        } else {
            it.seekToNext()
        }
    }

    fun previous() = command { if (state.value.dj == null) it.seekToPrevious() }

    /** Starts a freshly shuffled queue at the first item of the permutation the player built. */
    private fun MediaController.shuffleFromHead() {
        sendCustomCommand(SessionCommand(PlaybackService.ACTION_SHUFFLE_FROM_HEAD, Bundle.EMPTY), Bundle.EMPTY)
    }

    fun seekTo(positionMs: Long) = command {
        if (state.value.dj?.mode == "interactive") return@command
        it.seekTo(positionMs + (state.value.dj?.start ?: 0))
        mutableState.update { s -> s.copy(positionMs = positionMs) }
    }

    fun skipTo(index: Int) {
        mutableSelectionVersion.value++
        command { it.seekToDefaultPosition(index) }
    }

    /** "Add to queue": plays after the current song and anything queued before it. */
    fun addToQueue(track: Track) {
        main.launch {
            val args = Bundle().apply { putString(PlaybackService.EXTRA_TRACK_ID, track.id) }
            connect().sendCustomCommand(SessionCommand(PlaybackService.ACTION_ADD_TO_QUEUE, Bundle.EMPTY), args)
            mutableEvents.tryEmit(PlayerEvent.AddedToQueue(track.displayTitle))
        }
    }

    /**
     * Moves the item at [from] to play right after the item at [after] (indices in the
     * player's list); [queued] says whether it now belongs to "Next in queue".
     */
    fun moveInQueue(from: Int, after: Int, queued: Boolean) {
        main.launch {
            val args = Bundle().apply {
                putInt(PlaybackService.EXTRA_FROM, from)
                putInt(PlaybackService.EXTRA_AFTER, after)
                putBoolean(PlaybackService.EXTRA_QUEUED, queued)
            }
            connect().sendCustomCommand(SessionCommand(PlaybackService.ACTION_MOVE_IN_QUEUE, Bundle.EMPTY), args)
        }
    }

    /** Takes the item at [index] out of the queue. */
    fun removeFromQueue(index: Int) = command {
        if (index != it.currentMediaItemIndex && index in 0 until it.mediaItemCount) it.removeMediaItem(index)
    }

    /** Takes out every song the user queued that hasn't played yet. */
    fun clearQueued() = command { player ->
        state.value.upNext.filter { it.queued }.map { it.index }.sortedDescending().forEach(player::removeMediaItem)
    }

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

    private data class CurrentContent(
        val track: Track?,
        val episode: PlayingEpisode?,
        val stream: PlayingStream?,
        val dj: PlayingDj?,
    )

    private fun contentOf(item: MediaItem?): CurrentContent {
        val episode = item?.let(MediaItems::episodeOf)
        val dj = item?.let(DjMediaItems::of)
        val stream = if (episode == null) item?.let(MediaItems::streamOf) else null
        val track = if (episode == null && stream == null && dj == null) {
            item?.let { library.library.value?.byId?.get(it.mediaId) ?: MediaItems.trackFromMetadata(it) }
        } else {
            null
        }
        return CurrentContent(track, episode, stream, dj)
    }

    private fun isNewContent(old: PlayerState, content: CurrentContent): Boolean = old.track?.id != content.track?.id ||
        old.episode?.episodeId != content.episode?.episodeId ||
        old.stream?.videoId != content.stream?.videoId || old.dj?.id != content.dj?.id

    private fun refresh(queueChanged: Boolean = false) {
        val player = controller ?: return
        val item = player.currentMediaItem
        val content = contentOf(item)
        val track = content.track
        val episode = content.episode
        val stream = content.stream
        val absolute =
            content.dj?.let { DjTimeline.position(player, it.origin) } ?: player.currentPosition.coerceAtLeast(0)
        val dj = content.dj?.let { DjTimeline.shown(it, absolute) }
        // Walking the whole list is the costly part: only when the list, its order or the current item moved.
        val upNext = if (queueChanged) upNextOf(player) else mutableState.value.upNext
        mutableState.update { old ->
            old.copy(
                track = track,
                episode = episode,
                stream = stream,
                dj = dj,
                speed = player.playbackParameters.speed,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                positionMs = if (dj != null) (absolute - dj.start).coerceAtLeast(0) else absolute,
                durationMs = shownDuration(player, content),
                shuffle = player.shuffleModeEnabled,
                repeat = when (player.repeatMode) {
                    Player.REPEAT_MODE_ALL -> RepeatMode.All
                    Player.REPEAT_MODE_ONE -> RepeatMode.One
                    else -> RepeatMode.Off
                },
                upNext = if (dj !=
                    null
                ) {
                    dj.nextTrackId?.let { listOf(QueueEntry(-1, it, dj.nextTitle, dj.nextArtist)) }.orEmpty()
                } else {
                    upNext
                },
                currentIndex = player.currentMediaItemIndex,
                context = MediaItems.contextOf(player.playlistMetadata),
                quality = item?.mediaId?.let(resolver::qualityOf),
                fromPhone = item?.mediaId?.let(resolver::isLocal) == true,
                error = if (isNewContent(old, content)) null else old.error,
            )
        }
        if (player.isPlaying) startTicker() else ticker?.cancel()
    }

    private fun shownDuration(player: Player, content: CurrentContent): Long =
        content.dj?.takeIf { it.end > it.start }?.let { it.end - it.start }
            ?: player.duration.takeIf { it != C.TIME_UNSET }
            ?: ((content.track?.duration ?: 0.0) * 1000).toLong().takeIf { it > 0 }
            ?: player.currentMediaItem?.mediaMetadata?.durationMs
            ?: 0L

    private fun upNextOf(player: Player): List<QueueEntry> = PlayOrder.fromCurrent(player).drop(1).map { i ->
        val item = player.getMediaItemAt(i)
        QueueEntry(
            index = i,
            trackId = item.mediaId,
            title = item.mediaMetadata.title?.toString().orEmpty(),
            artist = item.mediaMetadata.artist?.toString().orEmpty(),
            queued = MediaItems.isQueued(item),
            artworkUrl = item.mediaMetadata.artworkUri?.toString(),
        )
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = main.launch {
            while (isActive) {
                val player = controller ?: break
                if (mutableState.value.dj != null) {
                    refresh()
                } else {
                    mutableState.update { it.copy(positionMs = player.currentPosition.coerceAtLeast(0)) }
                }
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
        val QUEUE_EVENTS = intArrayOf(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
        )
        val NETWORK_ERRORS = setOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )
    }
}

/** One not-downloaded row with the video the server streams for it. */
data class StreamEntry(val song: RemoteSong, val videoId: String)
