package com.henriquesebastiao.downtify.core.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.henriquesebastiao.downtify.core.data.activity.PlaybackActivityReporter
import com.henriquesebastiao.downtify.core.data.catalog.CatalogRepository
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.listens.ListenReporter
import com.henriquesebastiao.downtify.core.data.podcasts.PodcastsRepository
import com.henriquesebastiao.downtify.core.model.EpisodeIds
import com.henriquesebastiao.downtify.core.model.StreamIds
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
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
import kotlinx.coroutines.launch

/**
 * ExoPlayer in a [MediaSessionService]: the media notification, lock screen,
 * Bluetooth and headset buttons, and other controllers all come from the
 * session. Audio focus is handled by ExoPlayer; unplugging headphones pauses.
 */
@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    private var listens: ListenTracker? = null
    private var activity: ActivityTracker? = null
    private var episodes: EpisodeProgressTracker? = null
    private var prefetch: StreamPrefetchTracker? = null
    private val likeCommand = SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY)

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
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

        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .setBitmapLoader(
                CacheBitmapLoader(
                    DataSourceBitmapLoader(DataSourceBitmapLoader.DEFAULT_EXECUTOR_SERVICE.get(), artworkDataSource),
                ),
            )
            .apply { launchIntent()?.let(::setSessionActivity) }
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this).apply { setSmallIcon(R.drawable.ic_notification) },
        )

        listens = ListenTracker(player, reporter, scope).also { it.start() }
        activity = ActivityTracker(player, activityReporter, scope).also { it.start() }
        episodes = EpisodeProgressTracker(player, podcasts, scope).also { it.start() }
        prefetch = StreamPrefetchTracker(player, catalog, scope).also { it.start() }
        keepLikeButtonCurrent(player)
        stopWhenSignedOut(player)
    }

    /** Unpaired (here or from the web): the queue belongs to a server the app can't reach anymore. */
    private fun stopWhenSignedOut(player: Player) {
        sessions.session
            .filter { it == null }
            .onEach {
                player.stop()
                player.clearMediaItems()
            }
            .launchIn(scope)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Stop when the user swipes the app away while nothing is playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        listens?.stop()
        activity?.stop()
        episodes?.stop()
        prefetch?.stop()
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

    private fun likeButton(liked: Boolean): CommandButton =
        CommandButton.Builder(if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName(getString(if (liked) R.string.player_unlike else R.string.player_like))
            .setSessionCommand(likeCommand)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(likeCommand).build(),
            )
            .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_TOGGLE_LIKE) {
                val id = session.player.currentMediaItem?.mediaId
                if (id != null && !EpisodeIds.isEpisode(id) && !StreamIds.isStream(id)) {
                    val liked = id in library.likedIds.value
                    scope.launch { library.setLiked(id, !liked) }
                }
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
                snapshot?.byId?.get(item.mediaId)?.let { MediaItems.from(it, baseUrl) }
                    ?: item.takeIf {
                        it.mediaId.isNotEmpty()
                    }?.let { it.buildUpon().setUri(MediaItems.uriFor(it.mediaId)).build() }
            }
            return Futures.immediateFuture(resolved.toMutableList())
        }
    }

    companion object {
        const val ACTION_TOGGLE_LIKE = "com.henriquesebastiao.downtify.TOGGLE_LIKE"
        const val SKIP_BACK_MS = 10_000L
        const val SKIP_FORWARD_MS = 30_000L
    }
}
