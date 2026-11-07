package com.henriquesebastiao.downtify.core.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.henriquesebastiao.downtify.core.network.session.AuthInterceptor
import com.henriquesebastiao.downtify.core.network.session.Session
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DjSegmentCacheTest {
    @Test fun `an open cached reader stops when its render is replaced`() {
        Fixture().use { fixture ->
            fixture.server.enqueue(MockResponse.Builder().body("old").build())
            fixture.read()
            val reader = fixture.factory.createDataSource()
            reader.open(fixture.spec())
            fixture.recordWindow(day = "07")
            assertThrows(IOException::class.java) { reader.read(ByteArray(3), 0, 3) }
            reader.close()
        }
    }

    @Test fun `reusing a segment name in a new render never reuses old audio`() {
        Fixture().use { fixture ->
            fixture.server.enqueue(MockResponse.Builder().body("old render").build())
            assertEquals("old render", drain(fixture.factory, fixture.spec()).toString(Charsets.UTF_8))
            fixture.recordWindow(day = "07")
            fixture.server.enqueue(MockResponse.Builder().body("new render").build())
            assertEquals("new render", drain(fixture.factory, fixture.spec()).toString(Charsets.UTF_8))
            assertEquals(2, fixture.server.requestCount)
        }
    }

    @Test fun `fresh manifest without stable timestamps bypasses cache`() {
        Fixture().use { fixture ->
            fixture.factory.observeManifest(
                Uri.parse(fixture.url("index.m3u8")),
                "#EXTM3U\n#EXTINF:6,\nsegment-00000001.ts",
            )
            repeat(2) { fixture.server.enqueue(MockResponse.Builder().body("direct $it").build()) }
            assertEquals("direct 0", drain(fixture.factory, fixture.spec()).toString(Charsets.UTF_8))
            assertEquals("direct 1", drain(fixture.factory, fixture.spec()).toString(Charsets.UTF_8))
            assertEquals(0, fixture.cache.cacheSpace)
        }
    }

    @Test fun `an authenticated immutable segment and its ranges survive a network outage`() {
        Fixture().use { fixture ->
            fixture.server.enqueue(MockResponse.Builder().body("abcdefgh").build())
            assertEquals("abcdefgh", fixture.read())
            assertEquals("Bearer fixture-token", fixture.server.takeRequest().headers["Authorization"])
            fixture.server.close()
            assertEquals("abcdefgh", fixture.read())
            assertEquals("cde", fixture.read(position = 2, length = 3))
            assertEquals(1, fixture.server.requestCount)
            assertTrue(fixture.cache.keys.all { it.matches(Regex("dj-v1:[a-f0-9-]{36}:[a-f0-9]{64}")) })
            fixture.cache.keys.forEach {
                assertNull(ContentMetadata.getRedirectedUri(fixture.cache.getContentMetadata(it)))
            }
        }
    }

    @Test fun `live manifests always refresh and never enter the audio cache`() {
        Fixture().use { fixture ->
            fixture.server.enqueue(MockResponse.Builder().body("#EXTM3U\n# first window").build())
            fixture.server.enqueue(MockResponse.Builder().body("#EXTM3U\n# next window").build())
            assertTrue(fixture.read("index.m3u8").contains("first window"))
            assertTrue(fixture.read("index.m3u8").contains("next window"))
            assertEquals(2, fixture.server.requestCount)
            assertEquals(0, fixture.cache.cacheSpace)
        }
    }

    @Test fun `a missed segment fails explicitly and can retry when the server recovers`() {
        Fixture().use { fixture ->
            fixture.server.enqueue(MockResponse.Builder().code(503).build())
            assertThrows(IOException::class.java) { fixture.read() }
            assertEquals(0, fixture.cache.cacheSpace)
            fixture.server.enqueue(MockResponse.Builder().body("recovered").build())
            assertEquals("recovered", fixture.read())
            assertEquals(2, fixture.server.requestCount)
        }
    }

    @Test fun `partial bytes are not treated as a complete segment and a retry fills the hole`() {
        Fixture().use { fixture ->
            var fail = true
            val starts = mutableListOf<Long>()
            val payload = "abcdefgh".toByteArray()
            val upstream = DataSource.Factory { Bytes(payload, { fail }, starts) }
            val cache = fixture.segmentFactory(upstream)
            val spec = fixture.spec()
            assertThrows(IOException::class.java) { drain(cache, spec) }
            val key = fixture.cache.keys.single()
            assertTrue(fixture.cache.cacheSpace in 1 until payload.size)
            assertFalse(fixture.cache.isCached(key, 0, payload.size.toLong()))
            fail = false
            assertArrayEquals(payload, drain(cache, spec))
            assertTrue(fixture.cache.isCached(key, 0, payload.size.toLong()))
            assertEquals(listOf(0L, 3L), starts)
        }
    }

    @Test fun `unknown length transfer is playable but never persisted as a trustworthy segment`() {
        Fixture().use { fixture ->
            fixture.server.enqueue(MockResponse.Builder().chunkedBody("abcdefgh", 2).build())
            assertEquals("abcdefgh", fixture.read())
            assertEquals(0, fixture.cache.cacheSpace)
        }
    }

    @Test fun `a different account or token generation cannot reuse cached bytes or an open reader`() {
        Fixture().use { fixture ->
            fixture.server.enqueue(MockResponse.Builder().body("account-one").build())
            assertEquals("account-one", fixture.read())
            val firstKey = fixture.cache.keys.single()
            val reader = fixture.factory.createDataSource()
            reader.open(fixture.spec())
            fixture.owner = fixture.owner!!.copy(username = "another", token = "another-fixture-token")
            assertThrows(IOException::class.java) { reader.read(ByteArray(4), 0, 4) }
            reader.close()
            fixture.server.enqueue(MockResponse.Builder().body("account-two").build())
            assertEquals("account-two", fixture.read())
            assertEquals(2, fixture.server.requestCount)
            assertTrue(fixture.cache.keys.any { it != firstKey })
            fixture.factory.purgeRetired()
            assertFalse(fixture.cache.isCached(firstKey, 0, 1))
        }
    }

    @Test fun `revocation prevents even cached playback and a disabled module starts no request`() {
        Fixture().use { fixture ->
            fixture.server.enqueue(MockResponse.Builder().body("cached").build())
            assertEquals("cached", fixture.read())
            fixture.enabled = false
            assertThrows(IOException::class.java) { fixture.read() }
            assertEquals(1, fixture.server.requestCount)
            fixture.enabled = true
            fixture.server.enqueue(MockResponse.Builder().code(401).build())
            assertThrows(IOException::class.java) { fixture.read("segment-00000002.ts") }
            assertNull(fixture.owner)
            assertThrows(IOException::class.java) { fixture.read() }
            assertEquals(2, fixture.server.requestCount)
            fixture.factory.purgeRetired()
            assertEquals(0, fixture.cache.cacheSpace)
        }
    }

    @Test fun `a different render session never reuses an equally numbered segment`() {
        Fixture().use { fixture ->
            fixture.server.enqueue(MockResponse.Builder().body("old-render").build())
            assertEquals("old-render", fixture.read())
            fixture.server.enqueue(MockResponse.Builder().body("new-render").build())
            val spec = fixture.spec().withUri(Uri.parse(fixture.url().replace(ID, "b".repeat(32))))
            assertEquals("new-render", drain(fixture.factory, spec).toString(Charsets.UTF_8))
            assertEquals(2, fixture.server.requestCount)
        }
    }

    @Test fun `the LRU disk budget evicts old chunks without touching the ordinary audio cache`() {
        Fixture(limit = 12).use { fixture ->
            fixture.server.enqueue(MockResponse.Builder().body("abcdefgh").build())
            fixture.read()
            val old = fixture.cache.keys.single()
            fixture.server.enqueue(MockResponse.Builder().body("ijklmnop").build())
            fixture.read("segment-00000002.ts")
            assertTrue(fixture.cache.cacheSpace <= 12)
            assertFalse(fixture.cache.isCached(old, 0, 1))
            assertEquals("ijklmnop", fixture.read("segment-00000002.ts"))
            assertEquals(2, fixture.server.requestCount)
        }
    }

    @Test fun `query credentials foreign origins and redirects do not enter the cache`() {
        Fixture().use { fixture ->
            val spec = fixture.spec()
            assertThrows(IOException::class.java) {
                drain(fixture.factory, spec.withUri(Uri.parse(fixture.url() + "?credential=fixture")))
            }
            assertThrows(IOException::class.java) {
                drain(fixture.factory, spec.withUri(Uri.parse(fixture.url().replace("localhost", "other.test"))))
            }
            assertEquals(0, fixture.server.requestCount)
            fixture.server.enqueue(
                MockResponse.Builder().code(302).addHeader("Location", "/elsewhere?credential=fixture").build(),
            )
            assertThrows(IOException::class.java) { fixture.read() }
            assertEquals(1, fixture.server.requestCount)
            assertEquals(0, fixture.cache.cacheSpace)
        }
    }

    @Test fun `router preserves regular prepared and interactive transports`() {
        val routes = mutableListOf<String>()
        fun route(name: String) = DataSource.Factory {
            routes += name
            Bytes(byteArrayOf(1))
        }
        val source = DjDataSource(route("regular"), route("prepared"), route("live")) {
            it.path?.contains("/hls/") ==
                true
        }
        listOf(Uri.parse("file:///synthetic"), DjMediaItems.uri(ID), DjMediaItems.uri(ID, "interactive")).forEach {
            source.open(DataSpec.Builder().setUri(it).build())
            source.close()
        }
        assertEquals(listOf("regular", "prepared", "live"), routes)
    }

    private class Fixture(limit: Long = 64L * 1024 * 1024) : Closeable {
        val server = MockWebServer().apply { start() }
        var owner: Session? =
            Session(server.url("/").toString().trimEnd('/'), "server", "Test", "device", "fixture-token")
        var enabled = true
        private val context = RuntimeEnvironment.getApplication()
        private val sqlite = android.database.sqlite.SQLiteDatabase.create(null)
        private val database = object : androidx.media3.database.DatabaseProvider {
            override fun getReadableDatabase() = sqlite
            override fun getWritableDatabase() = sqlite
        }
        val cache = SimpleCache(
            File(context.cacheDir, "dj-cache-test-${UUID.randomUUID()}"),
            LeastRecentlyUsedCacheEvictor(limit),
            database,
        )
        private val sessions = mockk<SessionStore>(relaxed = true).also {
            every { it.current } answers { owner }
            every { it.onRevoked(any()) } answers { owner = null }
        }
        private val http = OkHttpDataSource.Factory(
            OkHttpClient.Builder().addInterceptor(AuthInterceptor(sessions)).followRedirects(false)
                .connectTimeout(1, TimeUnit.SECONDS).readTimeout(1, TimeUnit.SECONDS).build(),
        )
        val factory = segmentFactory(http)

        fun segmentFactory(upstream: DataSource.Factory) = DjSegmentCache(cache, {
            owner
        }, { enabled }, upstream).also {
            recordWindow(it)
        }
        fun recordWindow(target: DjSegmentCache = factory, day: String = "06") {
            target.observeManifest(
                Uri.parse(url("index.m3u8")),
                "#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:1\n#EXT-X-PROGRAM-DATE-TIME:2026-11-${day}T00:00:00Z\n" +
                    (1..5).joinToString("\n") { "#EXTINF:6,\nsegment-${it.toString().padStart(8, '0')}.ts" },
            )
        }
        fun url(resource: String = "segment-00000001.ts") =
            server.url("/api/v1/music-ai/dj/sessions/$ID/hls/$resource").toString()
        fun spec(resource: String = "segment-00000001.ts", position: Long = 0, length: Long = C.LENGTH_UNSET.toLong()) =
            DataSpec.Builder().setUri(url(resource)).setPosition(position).setLength(length).build()
        fun read(
            resource: String = "segment-00000001.ts",
            position: Long = 0,
            length: Long = C.LENGTH_UNSET.toLong(),
        ): String {
            if (resource.startsWith("segment-")) recordWindow()
            return drain(factory, spec(resource, position, length)).toString(Charsets.UTF_8)
        }

        override fun close() {
            cache.release()
            sqlite.close()
            server.close()
        }
    }

    private class Bytes(
        private val bytes: ByteArray,
        private val fail: () -> Boolean = { false },
        private val starts: MutableList<Long> = mutableListOf(),
    ) : DataSource {
        private var position = 0
        private var uri: Uri? = null
        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun open(dataSpec: DataSpec): Long {
            uri = dataSpec.uri
            starts += dataSpec.position
            position = dataSpec.position.toInt()
            return (bytes.size - position).toLong()
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (fail() && position >= 3) throw IOException("Synthetic segment disconnect")
            if (position == bytes.size) return C.RESULT_END_OF_INPUT
            val available = if (fail()) minOf(3, bytes.size) else bytes.size
            val count = minOf(length, available - position)
            bytes.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }
        override fun getUri(): Uri? = uri
        override fun close() {
            uri = null
        }
    }

    private companion object {
        const val ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        fun drain(factory: DataSource.Factory, spec: DataSpec): ByteArray {
            val source = factory.createDataSource()
            try {
                source.open(spec)
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16)
                while (true) {
                    val size = source.read(buffer, 0, buffer.size)
                    if (size == C.RESULT_END_OF_INPUT) return output.toByteArray()
                    output.write(buffer, 0, size)
                }
            } finally {
                source.close()
            }
        }
    }
}
