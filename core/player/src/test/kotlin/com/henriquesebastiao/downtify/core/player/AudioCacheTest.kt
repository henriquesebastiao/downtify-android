package com.henriquesebastiao.downtify.core.player

import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.henriquesebastiao.downtify.core.data.settings.SettingsRepository
import com.henriquesebastiao.downtify.core.data.settings.UserSettings
import com.henriquesebastiao.downtify.core.model.StreamQuality
import com.henriquesebastiao.downtify.core.network.session.Session
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioCacheTest {
    @Test
    fun `quota shrinks immediately and sacrifices history before upcoming songs`() {
        val context = RuntimeEnvironment.getApplication()
        val quota = QuotaCacheEvictor(12)
        val cache = SimpleCache(File(context.cacheDir, "quota-test"), quota, StandaloneDatabaseProvider(context))
        try {
            write(cache, "history", 4)
            write(cache, "current", 4)
            write(cache, "next", 4)
            quota.prioritize(cache, listOf("current", "next"))
            write(cache, "later", 4)
            assertFalse(cache.isCached("history", 0, 4))
            assertTrue(cache.isCached("current", 0, 4))
            assertTrue(cache.isCached("next", 0, 4))
            quota.setLimit(cache, 4)
            assertTrue(cache.cacheSpace <= 4)
            assertTrue(cache.isCached("current", 0, 4))
        } finally {
            cache.release()
        }
    }

    @Test
    fun `complete bytes play without upstream and partial copies never count as ready`() = runTest {
        val paired = MutableStateFlow<Session?>(Session("http://localhost", "server", "Test", "device", "fixture"))
        val sessions = mockk<SessionStore>(relaxed = true)
        every { sessions.current } answers { paired.value }
        val settings = mockk<SettingsRepository>()
        every { settings.settings } returns MutableStateFlow(UserSettings())
        val store = AudioCacheStore(RuntimeEnvironment.getApplication(), sessions, settings, backgroundScope)
        try {
            val key = store.key("one", StreamQuality.Original.encode())
            write(store.cache, key, 4, total = 8)
            assertFalse(store.complete(key))
            MockWebServer().apply { start() }.use { server ->
                server.enqueue(MockResponse.Builder().body("abcdefgh").build())
                val spec = DataSpec.Builder().setUri(server.url("/two").toString())
                    .setKey(store.key("two", StreamQuality.Original.encode())).build()
                val source = CacheDataSource.Factory().setCache(store.cache)
                    .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(OkHttpClient())).createDataSource()
                CacheWriter(source, spec, null, null).cache()
                assertTrue(store.complete(spec.key!!))
                val upstream = mockk<DataSource>(relaxed = true)
                every { upstream.open(any()) } throws java.io.IOException("Network unavailable")
                val noNetwork = DataSource.Factory { upstream }
                val reader = CacheDataSource.Factory().setCache(store.cache).setUpstreamDataSourceFactory(noNetwork)
                    .createDataSource()
                reader.open(spec)
                val result = ByteArray(8)
                assertEquals(8, reader.read(result, 0, result.size))
                reader.close()
                assertEquals("abcdefgh", String(result))
                verify(exactly = 0) { upstream.open(any()) }
                assertEquals(1, server.requestCount)
                assertEquals(StreamQuality.Original, store.completedQuality("two"))
                assertFalse(store.complete(store.key("two", "mp3:128")))
                paired.value = paired.value!!.copy(deviceId = "another-pairing")
                assertFalse(store.complete(store.key("two", StreamQuality.Original.encode())))
            }
        } finally {
            store.cache.release()
        }
    }

    private fun write(cache: SimpleCache, key: String, size: Int, total: Long = size.toLong()) {
        val hole = cache.startReadWrite(key, 0, size.toLong())
        try {
            val file = cache.startFile(key, 0, size.toLong())
            file.writeBytes(ByteArray(size))
            cache.commitFile(file, size.toLong())
            cache.applyContentMetadataMutations(
                key,
                ContentMetadataMutations().apply {
                    ContentMetadataMutations.setContentLength(this, total)
                },
            )
        } finally {
            cache.releaseHoleSpan(hole)
        }
    }
}
