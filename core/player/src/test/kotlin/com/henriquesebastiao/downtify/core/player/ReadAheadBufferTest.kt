package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.henriquesebastiao.downtify.core.data.NetworkMonitor
import com.henriquesebastiao.downtify.core.data.session.ServerRepository
import com.henriquesebastiao.downtify.core.data.settings.SettingsRepository
import com.henriquesebastiao.downtify.core.data.settings.UserSettings
import com.henriquesebastiao.downtify.core.data.sync.DeletedTrackRegistry
import com.henriquesebastiao.downtify.core.model.ReadAheadPolicy
import com.henriquesebastiao.downtify.core.network.session.Session
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import io.mockk.every
import io.mockk.mockk
import java.util.TreeSet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadAheadBufferTest {
    @Test
    fun `stopping a plan never touches the cache on the calling thread`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val caller = Thread.currentThread()
        val registered = CountDownLatch(2)
        val removed = CountDownLatch(2)
        val calledOnUi = AtomicBoolean(false)
        val cache = mockk<SimpleCache>(relaxed = true)
        every { cache.addListener(any(), any()) } answers {
            registered.countDown()
            TreeSet<CacheSpan>()
        }
        every { cache.removeListener(any(), any()) } answers {
            if (Thread.currentThread() == caller) calledOnUi.set(true)
            removed.countDown()
        }
        val store = mockk<AudioCacheStore>(relaxed = true)
        every { store.cache } returns cache
        val paired = Session("https://example.test", "server", "Test", "device", "fixture")
        val sessions = mockk<SessionStore>(relaxed = true)
        every { sessions.current } returns paired
        every { sessions.session } returns MutableStateFlow(paired)
        val settings = mockk<SettingsRepository>()
        every { settings.settings } returns MutableStateFlow(UserSettings(readAhead = ReadAheadPolicy(1, 100, false)))
        val network = mockk<NetworkMonitor>()
        every { network.status } returns MutableStateFlow(NetworkMonitor.Status(true, false))
        val server = mockk<ServerRepository>(relaxed = true)
        every { server.serverInfo } returns MutableStateFlow(null)
        val resolver = mockk<StreamResolver>()
        every { resolver.preloadSpec(any(), any()) } answers {
            val id = firstArg<android.net.Uri>().lastPathSegment!!
            DataSpec.Builder().setUri("https://example.test/$id").setKey(id).build()
        }
        val timeline = mockk<Timeline>()
        every { timeline.isEmpty } returns false
        every { timeline.getNextWindowIndex(any(), any(), any()) } answers {
            if (firstArg<Int>() == 0) 1 else C.INDEX_UNSET
        }
        val player = mockk<Player>(relaxed = true)
        every { player.currentTimeline } returns timeline
        every { player.currentMediaItemIndex } returns 0
        every { player.getMediaItemAt(any()) } answers {
            val id = firstArg<Int>().toString()
            MediaItem.Builder().setMediaId(id).setUri(MediaItems.uriFor(id)).build()
        }
        val buffer = ReadAheadBuffer(store, resolver, OkHttpClient(), settings, network, server, sessions)
        try {
            buffer.start(player, scope)
            await { registered.count == 0L }
            buffer.stop()
            await { removed.count == 0L }
            assertFalse(calledOnUi.get())
        } finally {
            buffer.stop()
            scope.cancel()
        }
    }

    @Test
    @Suppress("LongMethod") // One end-to-end policy scenario, with its HTTP/cache fixture kept together.
    fun `mobile policy and shuffled order control fetches and offline readiness`() = runBlocking {
        MockWebServer().apply { start() }.use { web ->
            repeat(4) { web.enqueue(MockResponse.Builder().body("synthetic-audio").build()) }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val paired = Session(web.url("/").toString(), "server", "Test", "device", "fixture")
            val sessions = mockk<SessionStore>(relaxed = true)
            every { sessions.current } returns paired
            every { sessions.session } returns MutableStateFlow(paired)
            val preferences = MutableStateFlow(UserSettings())
            val settings = mockk<SettingsRepository>()
            every { settings.settings } returns preferences
            val status = MutableStateFlow(NetworkMonitor.Status(true, true))
            val network = mockk<NetworkMonitor>()
            every { network.status } returns status
            val server = mockk<ServerRepository>(relaxed = true)
            every { server.serverInfo } returns MutableStateFlow(null)
            val context = RuntimeEnvironment.getApplication()
            val store = AudioCacheStore(
                context = context,
                sessions = sessions,
                deletedTracks = DeletedTrackRegistry(context),
                settings = settings,
                scope = scope,
            )
            val resolver = mockk<StreamResolver>()
            every { resolver.preloadSpec(any(), any()) } answers {
                val id = firstArg<android.net.Uri>().lastPathSegment!!
                DataSpec.Builder().setUri(web.url("/$id").toString()).setKey(store.key(id, "original")).build()
            }
            val timeline = mockk<Timeline>()
            every { timeline.isEmpty } returns false
            every { timeline.getNextWindowIndex(any(), any(), any()) } answers {
                when (firstArg<Int>()) {
                    2 -> 0
                    0 -> 3
                    3 -> 1
                    else -> C.INDEX_UNSET
                }
            }
            val player = mockk<Player>(relaxed = true)
            every { player.currentTimeline } returns timeline
            every { player.currentMediaItemIndex } returns 2
            every { player.isPlaying } returns true
            var loading = true
            every { player.isLoading } answers { loading }
            every { player.playbackState } returns Player.STATE_READY
            every { player.shuffleModeEnabled } returns true
            every { player.getMediaItemAt(any()) } answers {
                val id = firstArg<Int>().toString()
                MediaItem.Builder().setMediaId(id).setUri(MediaItems.uriFor(id)).build()
            }
            val buffer = ReadAheadBuffer(
                store,
                resolver,
                OkHttpClient(),
                settings,
                network,
                server,
                sessions,
            )
            try {
                buffer.start(player, scope)
                assertEquals(0, web.requestCount)
                preferences.value = preferences.value.copy(readAhead = ReadAheadPolicy(3, 100, false))
                await { buffer.enabled && buffer.state.value.target == 3 }
                assertEquals(0, web.requestCount)
                preferences.value = preferences.value.copy(readAhead = ReadAheadPolicy(3, 100, true))
                delay(100)
                assertEquals(0, web.requestCount)
                loading = false
                buffer.onEvents(
                    player,
                    Player.Events(
                        androidx.media3.common.FlagSet.Builder()
                            .add(Player.EVENT_IS_LOADING_CHANGED).build(),
                    ),
                )
                await { buffer.state.value.ready == 3 }
                assertEquals(listOf("/2", "/0", "/3", "/1"), (0..3).map { web.takeRequest().url.encodedPath })
                status.value = NetworkMonitor.Status(false, true)
                await { buffer.state.value.ready == 3 }
                assertEquals(4, web.requestCount)
                store.cache.removeResource(store.key("0", "original"))
                await { buffer.state.value.ready == 2 }
                assertEquals(4, web.requestCount)
            } finally {
                buffer.stop()
                scope.cancel()
                store.cache.release()
            }
        }
    }

    private suspend fun await(condition: () -> Boolean) {
        withTimeout(10_000) { while (!condition()) delay(10) }
    }
}
