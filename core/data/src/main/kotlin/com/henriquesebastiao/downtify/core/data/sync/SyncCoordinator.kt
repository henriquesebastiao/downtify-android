package com.henriquesebastiao.downtify.core.data.sync

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.henriquesebastiao.downtify.core.data.catalog.ServerQueueRepository
import com.henriquesebastiao.downtify.core.data.di.ApplicationScope
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.listens.ListenReporter
import com.henriquesebastiao.downtify.core.data.session.ConnectionState
import com.henriquesebastiao.downtify.core.data.session.ServerRepository
import com.henriquesebastiao.downtify.core.data.settings.SettingsRepository
import com.henriquesebastiao.downtify.core.network.live.LiveEvent
import com.henriquesebastiao.downtify.core.network.live.LiveUpdatesClient
import com.henriquesebastiao.downtify.core.network.live.LiveUpdatesClosed
import com.henriquesebastiao.downtify.core.network.session.Session
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps the phone in step with the paired server while the app is in the
 * foreground: checks the server and syncs on launch, holds the WebSocket open,
 * and syncs again on `library_changed`. A refused socket (4401, or the 403 the
 * server sends before accepting) is checked with `/api/auth/status`, and the
 * token dropped when the phone was unpaired.
 */
@Singleton
class SyncCoordinator @Inject constructor(
    private val server: ServerRepository,
    private val library: LibraryRepository,
    private val listens: ListenReporter,
    private val settings: SettingsRepository,
    private val live: LiveUpdatesClient,
    private val serverQueue: ServerQueueRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val syncRequests = SyncRequests()
    private var started = false
    private val mediaServiceActive = MutableStateFlow(false)

    /** One shared sync loop while the phone UI or the single media service is active. */
    fun setMediaServiceActive(active: Boolean) {
        mediaServiceActive.value = active
    }

    fun start() {
        if (started) return
        started = true
        scope.launch {
            withContext(Dispatchers.IO) { server.ensureLoaded() }
            withContext(Dispatchers.Main) {
                syncVisibility(
                    ProcessLifecycleOwner.get().lifecycle.currentStateFlow.map {
                        it.isAtLeast(Lifecycle.State.STARTED)
                    },
                    mediaServiceActive,
                ).collectLatest { active ->
                    if (active) {
                        server.session.distinctUntilChangedBy { it?.token }.collectLatest { session ->
                            if (session != null) withContext(Dispatchers.Default) { whilePaired(session) }
                        }
                    }
                }
            }
        }
    }

    /** Syncs as soon as possible ([refresh]: rescan the server's folder first). */
    fun requestSync(refresh: Boolean = false) {
        syncRequests.request(refresh)
    }

    internal suspend fun whilePaired(session: Session) = coroutineScope {
        launch {
            syncRequests.collect { refresh ->
                if (library.sync(refresh)) server.markConnected() else server.markUnreachable()
            }
        }
        val state = server.refresh()
        if (state == ConnectionState.Connected) {
            requestSync()
            listens.schedule()
        }
        holdWebSocket(session, initialSyncRequested = state == ConnectionState.Connected)
    }

    private suspend fun holdWebSocket(session: Session, initialSyncRequested: Boolean) = coroutineScope {
        val clientId = settings.clientId()
        var attempt = 0
        var connectedBefore = false
        while (isActive) {
            try {
                live.events(session, clientId).collect { event ->
                    when (event) {
                        LiveEvent.Connected -> {
                            if (!initialSyncRequested || connectedBefore || attempt > 0) requestSync()
                            connectedBefore = true
                            attempt = 0
                            server.markConnected()
                            serverQueue.refresh()
                        }

                        LiveEvent.LibraryChanged -> requestSync()

                        LiveEvent.LikesChanged -> library.refreshLikes()

                        is LiveEvent.Refused -> server.verifyStillPaired()

                        is LiveEvent.DownloadProgress, LiveEvent.QueueReload ->
                            if (serverQueue.onEvent(event)) serverQueue.refresh()
                    }
                }
            } catch (e: LiveUpdatesClosed) {
                Log.d(TAG, "WebSocket closed", e)
            }
            delay(backoffMillis(attempt++))
        }
    }

    private fun backoffMillis(attempt: Int): Long = min(MAX_BACKOFF_MS, BASE_BACKOFF_MS shl min(attempt, 6))

    private companion object {
        const val TAG = "SyncCoordinator"
        const val BASE_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 120_000L
    }
}

internal fun syncVisibility(foreground: Flow<Boolean>, mediaService: Flow<Boolean>): Flow<Boolean> =
    combine(foreground, mediaService) { phone, media -> phone || media }.distinctUntilChanged()
