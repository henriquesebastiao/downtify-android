package com.henriquesebastiao.downtify.core.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.henriquesebastiao.downtify.core.data.NetworkMonitor
import com.henriquesebastiao.downtify.core.data.activity.PlaybackActivityReporter
import com.henriquesebastiao.downtify.core.data.catalog.CatalogRepository
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.listens.ListenReporter
import com.henriquesebastiao.downtify.core.data.loudness.LoudnessProfiles
import com.henriquesebastiao.downtify.core.data.offline.OfflineRepository
import com.henriquesebastiao.downtify.core.data.playback.PlaybackRepository
import com.henriquesebastiao.downtify.core.data.playback.playbackOwner
import com.henriquesebastiao.downtify.core.data.podcasts.PodcastsRepository
import com.henriquesebastiao.downtify.core.data.sync.SyncCoordinator
import com.henriquesebastiao.downtify.core.model.EpisodeIds
import com.henriquesebastiao.downtify.core.model.PlayQueue
import com.henriquesebastiao.downtify.core.model.StreamIds
import com.henriquesebastiao.downtify.core.network.di.StreamingClient
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * ExoPlayer in a [MediaLibraryService]: the media notification, lock screen,
 * Bluetooth and headset buttons, and other controllers all come from the
 * session. Audio focus is handled by ExoPlayer; unplugging headphones pauses.
 */
@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject
    @AudioDataSource
    lateinit var audioDataSource: DataSource.Factory

    @Inject
    @ArtworkDataSource
    lateinit var artworkDataSource: DataSource.Factory

    @Inject lateinit var library: LibraryRepository

    @Inject lateinit var reporter: ListenReporter

    @Inject lateinit var activityReporter: PlaybackActivityReporter

    @Inject lateinit var podcasts: PodcastsRepository

    @Inject lateinit var sessions: SessionStore

    @Inject lateinit var catalog: CatalogRepository

    @Inject lateinit var resolver: StreamResolver

    @Inject lateinit var loudness: LoudnessProfiles

    @Inject lateinit var readAhead: ReadAheadBuffer

    @Inject lateinit var playback: PlaybackRepository

    @Inject
    @StreamingClient
    lateinit var streamingClient: OkHttpClient

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Inject lateinit var offline: OfflineRepository

    @Inject lateinit var network: NetworkMonitor

    @Inject lateinit var artwork: SessionArtworkCache

    @Inject lateinit var syncCoordinator: SyncCoordinator

    private var session: MediaLibrarySession? = null
    private var artworkTracker: SessionArtworkTracker? = null
    private var listens: ListenTracker? = null
    private var activity: ActivityTracker? = null
    private var episodes: EpisodeProgressTracker? = null
    private var prefetch: StreamPrefetchTracker? = null
    private var warmer: TranscodeWarmer? = null
    private var persistence: PlaybackPersistence? = null
    private val likeCommand = SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY)
    private val addToQueueCommand = SessionCommand(ACTION_ADD_TO_QUEUE, Bundle.EMPTY)
    private val moveInQueueCommand = SessionCommand(ACTION_MOVE_IN_QUEUE, Bundle.EMPTY)

    override fun onCreate() {
        super.onCreate()
        val normalizer = NormalizationAudioProcessor(
            gainForTrack = loudness::gainDb,
            isEnabled = { loudness.enabled.value },
            gainRevision = { loudness.level.value.ordinal },
        )
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
                .setAudioProcessors(arrayOf(normalizer))
                .setEnableFloatOutput(false)
                .setAudioOffloadSupportProvider { _, _ -> AudioOffloadSupport.DEFAULT_UNSUPPORTED }
                .build()
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(audioDataSource))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            // For podcast episodes: the buttons in the app and the notification skip by these.
            .setSeekBackIncrementMs(SKIP_BACK_MS)
            .setSeekForwardIncrementMs(SKIP_FORWARD_MS)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val callback = SessionCallback()
        session = MediaLibrarySession.Builder(this, player, callback)
            .setBitmapLoader(
                CacheBitmapLoader(
                    DataSourceBitmapLoader(DataSourceBitmapLoader.DEFAULT_EXECUTOR_SERVICE.get(), artworkDataSource),
                ),
            )
            .apply { launchIntent()?.let(::setSessionActivity) }
            .build()

        callback.observe(checkNotNull(session))
        syncCoordinator.setMediaServiceActive(true)
        scope.launch {
            withContext(Dispatchers.IO) { sessions.ensureLoaded() }
            artworkTracker = SessionArtworkTracker(player, artwork, sessions, network, scope).also { it.start() }
        }

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this).apply { setSmallIcon(R.drawable.ic_notification) },
        )

        listens = ListenTracker(player, reporter, scope).also { it.start() }
        activity = ActivityTracker(player, activityReporter, scope).also { it.start() }
        episodes = EpisodeProgressTracker(player, podcasts, scope).also { it.start() }
        prefetch = StreamPrefetchTracker(player, catalog, scope, enabled = { !readAhead.enabled }).also { it.start() }
        warmer =
            TranscodeWarmer(player, resolver, streamingClient, scope, enabled = {
                !readAhead.enabled
            }).also { it.start() }
        readAhead.start(player, scope)
        keepLikeButtonCurrent(player)
        stopWhenSignedOut(player)
        removeDeletedLibraryTracks(player)
        persistence = PlaybackPersistence(player, playback, sessions, scope).also { it.start() }
    }

    /**
     * Songs that left the library (deleted, moved to the server's trash, hidden) leave the queue
     * too — but only once a sync of this session has succeeded: until then the local library may be
     * a stale copy, and a queue restored offline must survive.
     */
    private fun removeDeletedLibraryTracks(player: Player) {
        combine(library.successfulSync, library.library) { count, snapshot -> count to snapshot }
            .onEach { (count, snapshot) ->
                if (count > 0 && snapshot != null) {
                    for (index in player.mediaItemCount - 1 downTo 0) {
                        val item = player.getMediaItemAt(index)
                        val id = item.localConfiguration?.uri?.let(MediaItems::trackIdOf)
                        if (id != null && id !in snapshot.byId) {
                            if (index == player.currentMediaItemIndex) player.pause()
                            player.removeMediaItem(index)
                        }
                    }
                }
            }.launchIn(scope)
    }

    /** Unpaired (here or from the web): the queue belongs to a server the app can't reach anymore. */
    private fun stopWhenSignedOut(player: Player) {
        scope.launch {
            withContext(Dispatchers.IO) { sessions.ensureLoaded() }
            val expected = sessions.current?.playbackOwner
            sessions.session.collect { paired ->
                if (paired == null || paired.playbackOwner != expected) {
                    player.stop()
                    player.clearMediaItems()
                    stopSelf()
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    /** Stop when the user swipes the app away while nothing is playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        syncCoordinator.setMediaServiceActive(false)
        artworkTracker?.stop()
        readAhead.stop()
        persistence?.stop()
        listens?.stop()
        activity?.stop()
        episodes?.stop()
        prefetch?.stop()
        warmer?.stop()
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun launchIntent(): PendingIntent? {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** The heart in the notification follows the current track's like. */
    private fun keepLikeButtonCurrent(player: Player) {
        val currentId = MutableStateFlow(player.currentMediaItem?.mediaId)
        player.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    currentId.value = mediaItem?.mediaId
                }
            },
        )
        // Songs get a heart; an episode or a stream has nothing to like.
        combine(currentId, library.likedIds) { id, liked ->
            when {
                EpisodeIds.isEpisode(id) || StreamIds.isStream(id) -> null
                else -> id != null && id in liked
            }
        }
            .distinctUntilChanged()
            .onEach { liked ->
                session?.setMediaButtonPreferences(if (liked == null) emptyList() else listOf(likeButton(liked)))
            }
            .launchIn(scope)
    }

    /**
     * Puts [item] after the current song and anything queued before it, ahead of the rest of
     * the album or playlist — in the shuffled order too, where ExoPlayer alone would put it
     * at a random spot. With nothing loaded it becomes the queue, paused.
     */
    private fun addToQueue(player: ExoPlayer, item: MediaItem) {
        if (player.mediaItemCount == 0) {
            player.setMediaItem(item)
            player.prepare()
            return
        }
        val anchor = PlayQueue.anchor(PlayOrder.fromCurrent(player)) {
            MediaItems.isQueued(player.getMediaItemAt(it))
        } ?: return
        if (player.shuffleModeEnabled) {
            val shuffled = PlayOrder.shuffled(player.currentTimeline)
            val newIndex = player.mediaItemCount
            player.addMediaItem(item)
            val order = PlayQueue.shuffleOrderWith(shuffled, anchor, newIndex)
            player.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), Random.nextLong()))
        } else {
            player.addMediaItem(anchor + 1, item)
        }
    }

    /**
     * Moves the item at [from] to play right after the item at [after] (indices in the
     * player's list), marked as queued or not. With shuffle on only the shuffled order
     * changes; otherwise the item moves in the list.
     */
    private fun moveInQueue(player: ExoPlayer, from: Int, after: Int, queued: Boolean) {
        val count = player.mediaItemCount
        if (from !in 0 until count || after !in 0 until count || from == after) return
        if (from == player.currentMediaItemIndex) return
        val item = player.getMediaItemAt(from)
        if (MediaItems.isQueued(item) != queued) player.replaceMediaItem(from, MediaItems.withQueued(item, queued))
        if (player.shuffleModeEnabled) {
            val order = PlayQueue.moveAfter(PlayOrder.shuffled(player.currentTimeline), from, after)
            player.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), Random.nextLong()))
        } else {
            player.moveMediaItem(from, PlayQueue.moveTarget(from, after))
        }
    }

    private fun likeButton(liked: Boolean): CommandButton =
        CommandButton.Builder(if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName(getString(if (liked) R.string.player_unlike else R.string.player_like))
            .setSessionCommand(likeCommand)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()

    private inner class SessionCallback : CarBrowser(this@PlaybackService, library, offline, sessions, artwork, scope) {
        private fun own(controller: MediaSession.ControllerInfo): Boolean =
            ControllerAccess.isApp(controller, packageName, Process.myUid())

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = if (
            own(controller) && mediaItems.none { it.requestMetadata.searchQuery != null }
        ) {
            Futures.transform(
                onAddMediaItems(mediaSession, controller, mediaItems),
                { resolved ->
                    MediaSession.MediaItemsWithStartPosition(checkNotNull(resolved), startIndex, startPositionMs)
                },
                com.google.common.util.concurrent.MoreExecutors.directExecutor(),
            )
        } else {
            super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
        }

        override fun onConnectAsync(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.ConnectionResult> = scope.future {
            persistence?.awaitReady()
            onConnect(session, controller)
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            persistence?.awaitReady()
            val player = mediaSession.player
            MediaSession.MediaItemsWithStartPosition(
                (0 until player.mediaItemCount).map(player::getMediaItemAt),
                player.currentMediaItemIndex,
                player.currentPosition.coerceAtLeast(0),
            )
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            if (!ControllerAccess.canConnect(controller, packageName, Process.myUid())) {
                return MediaSession.ConnectionResult.reject()
            }
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            if (own(controller)) {
                commands.add(likeCommand).add(addToQueueCommand).add(moveInQueueCommand)
            } else {
                commands.add(likeCommand)
            }
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands.build()).build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (!own(controller) && customCommand.customAction != ACTION_TOGGLE_LIKE) {
                return Futures.immediateFuture(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
            }
            if (customCommand.customAction == ACTION_TOGGLE_LIKE) {
                val id = session.player.currentMediaItem?.mediaId
                if (id != null && !EpisodeIds.isEpisode(id) && !StreamIds.isStream(id)) {
                    val liked = id in library.likedIds.value
                    scope.launch { library.setLiked(id, !liked) }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == ACTION_ADD_TO_QUEUE) {
                val track = args.getString(EXTRA_TRACK_ID)?.let { library.library.value?.byId?.get(it) }
                    ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                addToQueue(
                    session.player as ExoPlayer,
                    MediaItems.from(track, sessions.current?.baseUrl, queued = true),
                )
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == ACTION_MOVE_IN_QUEUE) {
                moveInQueue(
                    session.player as ExoPlayer,
                    from = args.getInt(EXTRA_FROM, C.INDEX_UNSET),
                    after = args.getInt(EXTRA_AFTER, C.INDEX_UNSET),
                    queued = args.getBoolean(EXTRA_QUEUED),
                )
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        /**
         * Controllers send items without a URI (it's stripped between processes):
         * rebuild each from the library by its id.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            if (!own(controller)) return scope.future { carAddItems(mediaItems).toMutableList() }
            val snapshot = library.library.value
            val baseUrl = sessions.current?.baseUrl
            val resolved = mediaItems.mapNotNull { item ->
                // An episode carries its file's address in the request metadata.
                if (EpisodeIds.isEpisode(item.mediaId)) {
                    return@mapNotNull item.requestMetadata.mediaUri?.let { item.buildUpon().setUri(it).build() }
                }
                // A stream rebuilt as a library address would point the
                // resolver at `/api/v1/tracks/stream:…` (404): keep its own.
                StreamIds.videoIdOf(item.mediaId)?.let { videoId ->
                    return@mapNotNull item.buildUpon().setUri(MediaItems.streamUri(videoId)).build()
                }
                snapshot?.byId?.get(item.mediaId)?.let { MediaItems.from(it, baseUrl, MediaItems.isQueued(item)) }
                    ?: item.takeIf {
                        it.mediaId.isNotEmpty()
                    }?.let { it.buildUpon().setUri(MediaItems.uriFor(it.mediaId)).build() }
            }
            return Futures.immediateFuture(resolved.toMutableList())
        }
    }

    companion object {
        const val ACTION_TOGGLE_LIKE = "com.henriquesebastiao.downtify.TOGGLE_LIKE"
        const val ACTION_ADD_TO_QUEUE = "com.henriquesebastiao.downtify.ADD_TO_QUEUE"
        const val ACTION_MOVE_IN_QUEUE = "com.henriquesebastiao.downtify.MOVE_IN_QUEUE"
        const val EXTRA_TRACK_ID = "track_id"
        const val EXTRA_FROM = "from"
        const val EXTRA_AFTER = "after"
        const val EXTRA_QUEUED = "queued"
        const val SKIP_BACK_MS = 10_000L
        const val SKIP_FORWARD_MS = 30_000L
    }
}
