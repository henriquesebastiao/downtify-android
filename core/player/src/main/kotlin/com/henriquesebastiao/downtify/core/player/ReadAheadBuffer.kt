package com.henriquesebastiao.downtify.core.player

import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.henriquesebastiao.downtify.core.data.NetworkMonitor
import com.henriquesebastiao.downtify.core.data.session.ServerRepository
import com.henriquesebastiao.downtify.core.data.settings.SettingsRepository
import com.henriquesebastiao.downtify.core.data.settings.UserSettings
import com.henriquesebastiao.downtify.core.model.EpisodeIds
import com.henriquesebastiao.downtify.core.model.ReadAheadPolicy
import com.henriquesebastiao.downtify.core.model.StreamPolicy
import com.henriquesebastiao.downtify.core.model.StreamQuality
import com.henriquesebastiao.downtify.core.model.Transcoding
import com.henriquesebastiao.downtify.core.network.di.StreamingClient
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

data class ReadAheadStatus(val ready: Int = 0, val target: Int = 0)

/** One low-priority sequential worker, sharing the exact bytes and keys used by playback. */
@UnstableApi
@Singleton
class ReadAheadBuffer @Inject constructor(
    private val store: AudioCacheStore,
    private val resolver: StreamResolver,
    @StreamingClient private val client: OkHttpClient,
    private val settings: SettingsRepository,
    private val network: NetworkMonitor,
    private val server: ServerRepository,
    private val sessions: SessionStore,
) : Player.Listener {
    private data class Entry(val index: Int, val id: String, val uri: Uri, val current: Boolean)
    private data class Plan(
        val owner: String,
        val policy: ReadAheadPolicy,
        val quality: StreamQuality,
        val entries: List<Entry>,
        val fetch: Boolean,
    )
    private data class Resolved(val entry: Entry, val spec: DataSpec)
    private val mutableState = MutableStateFlow(ReadAheadStatus())
    val state = mutableState.asStateFlow()
    private var player: Player? = null
    private var scope: CoroutineScope? = null
    private var observer: Job? = null
    private var work: Job? = null

    @Volatile private var plan: Plan? = null
    private var prefs = UserSettings()
    private var request: ReadAheadRequest? = null
    private data class Watching(val plan: Plan, val items: List<Resolved>)
    private val watching = AtomicReference<Watching?>()
    private val cacheListener = object : Cache.Listener {
        override fun onSpanAdded(cache: Cache, span: CacheSpan) = Unit
        override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) = Unit
        override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
            val observed = watching.get() ?: return
            scope?.launch(Dispatchers.IO) {
                if (plan === observed.plan) publish(observed.items)
            }
        }
    }
    val enabled: Boolean get() = prefs.readAhead.tracks > 0

    fun start(player: Player, scope: CoroutineScope) {
        stop()
        this.player = player
        this.scope = scope
        player.addListener(this)
        observer = scope.launch {
            combine(settings.settings, network.status, server.serverInfo, sessions.session) { preferences, _, _, _ ->
                preferences
            }
                .collect {
                    prefs = it
                    update()
                }
        }
        update()
    }

    fun stop() {
        player?.removeListener(this)
        work?.cancel()
        request?.cancel()
        observer?.cancel()
        plan = null
        unwatch()
        player = null
        scope = null
        plan = null
        mutableState.value = ReadAheadStatus()
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_IS_LOADING_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED,
            )
        ) {
            update()
        }
    }

    private fun update() {
        val player = player ?: return
        val paired = sessions.current
        val policy = prefs.readAhead.normalized()
        val status = network.status.value
        val quality = StreamPolicy.select(
            prefs.wifiQuality,
            prefs.mobileQuality,
            status.metered,
            server.serverInfo.value?.capabilities?.transcoding ?: Transcoding(),
        )
        val entries = if (paired == null || policy.tracks == 0) {
            emptyList()
        } else {
            PlayOrder.ahead(player, policy.tracks + 1).mapNotNull { index ->
                val item = player.getMediaItemAt(index)
                if (EpisodeIds.isEpisode(item.mediaId)) return@mapNotNull null
                val uri =
                    item.localConfiguration?.uri ?: item.requestMetadata.mediaUri ?: MediaItems.uriFor(item.mediaId)
                Entry(index, item.mediaId, uri, index == player.currentMediaItemIndex)
            }
        }
        val allowedNetwork = status.online && (!status.metered || policy.mobile)
        val fetch =
            allowedNetwork && player.isPlaying && player.playbackState == Player.STATE_READY && !player.isLoading
        val next = Plan(
            owner = owner(),
            policy = policy,
            quality = quality,
            entries = entries,
            fetch = fetch,
        )
        if (next == plan) return
        plan = next
        unwatch()
        val previous = work
        previous?.cancel()
        request?.cancel()
        mutableState.value = ReadAheadStatus(target = entries.count { !it.current })
        val pending = ReadAheadRequest(client)
        request = pending
        work = scope?.launch {
            previous?.join()
            withContext(Dispatchers.IO) { execute(next, pending) }
        }
    }

    private suspend fun execute(plan: Plan, pending: ReadAheadRequest) {
        val context = kotlinx.coroutines.currentCoroutineContext()
        if (!sameOwner(plan)) return
        val resolved = plan.entries.mapNotNull { entry ->
            resolver.preloadSpec(entry.uri, plan.quality)?.let { Resolved(entry, it) }
        }
        context.ensureActive()
        if (!sameOwner(plan)) return
        store.prioritize(resolved.mapNotNull { it.spec.key })
        if (!watch(plan, resolved)) return
        try {
            publish(resolved)
            if (plan.fetch) preloadAll(plan, resolved, context, pending)
            // Keep observing evictions until this plan is cancelled. Registration and
            // cleanup stay on this IO worker, never on a Player callback/UI thread.
            awaitCancellation()
        } finally {
            resolved.mapNotNull { it.spec.key }.distinct().forEach {
                store.cache.removeListener(it, cacheListener)
            }
        }
    }

    private fun preloadAll(plan: Plan, items: List<Resolved>, context: CoroutineContext, pending: ReadAheadRequest) {
        for (item in items) {
            context.ensureActive()
            if (!sameOwner(plan)) return
            preload(item, context, pending)
            context.ensureActive()
            publish(items)
        }
    }

    private fun preload(item: Resolved, context: CoroutineContext, pending: ReadAheadRequest) {
        val key = item.spec.key ?: return
        if (store.complete(key) || !store.canWrite(key)) return
        val source = CacheDataSource.Factory().setCache(store.cache).setUpstreamDataSourceFactory(pending.http)
            .setCacheKeyFactory { it.key ?: store.uriKey(it.uri.toString()) }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR).createDataSource()
        try {
            pending.cache(source, item.spec) { length, bytesCached, _ ->
                if (!context.isActive) throw InterruptedIOException("Read-ahead cancelled")
                val total = if (length >= 0) length else bytesCached
                if (!store.fits(key, total)) throw IOException("Read-ahead quota reached")
            }
        } catch (_: IOException) {
            // Missing/partial data stays a normal streaming request; never claim offline readiness.
        }
    }

    private fun sameOwner(plan: Plan): Boolean = owner() == plan.owner

    private fun owner(): String = sessions.current?.let { "${it.serverId}:${it.deviceId}" }.orEmpty()

    private fun watch(expected: Plan, items: List<Resolved>): Boolean {
        if (plan !== expected) return false
        watching.set(Watching(expected, items))
        items.mapNotNull { it.spec.key }.distinct().forEach { store.cache.addListener(it, cacheListener) }
        return true
    }

    private fun unwatch() {
        watching.set(null)
    }

    private fun publish(items: List<Resolved>) {
        val upcoming = items.filter { !it.entry.current }
        mutableState.value = ReadAheadStatus(
            ready = upcoming.count {
                if (it.spec.uri.scheme == "file") {
                    File(it.spec.uri.path.orEmpty()).isFile
                } else {
                    it.spec.key?.let(store::complete) == true
                }
            },
            target = upcoming.size,
        )
    }
}
