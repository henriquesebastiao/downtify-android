package com.henriquesebastiao.downtify.core.player

import android.graphics.Bitmap
import android.net.Uri
import com.henriquesebastiao.downtify.core.network.session.AuthInterceptor
import com.henriquesebastiao.downtify.core.network.session.Session
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SessionArtworkCacheTest {
    @Test
    fun `authenticated cover is shared locally and reused without network`() = runTest {
        MockWebServer().apply { start() }.use { server ->
            val base = server.url("/").toString().trimEnd('/')
            val paired = MutableStateFlow<Session?>(Session(base, "server", "Test", "device-cache", "fixture-token"))
            val sessions = mockk<SessionStore>(relaxed = true)
            every { sessions.session } returns paired
            every { sessions.current } answers { paired.value }
            val cache = SessionArtworkCache(
                RuntimeEnvironment.getApplication(),
                sessions,
                OkHttpClient.Builder().addInterceptor(AuthInterceptor(sessions)).build(),
                backgroundScope,
            )
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            val bytes = ByteArrayOutputStream().also {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }.toByteArray()
            bitmap.recycle()
            server.enqueue(
                MockResponse.Builder().body(Buffer().write(bytes)).addHeader("Content-Type", "image/png").build(),
            )
            val source = Uri.parse("$base/api/v1/tracks/one/cover")
            val uri = cache.prepare("one", source)
            assertNotNull(uri)
            assertEquals("content", uri!!.scheme)
            assertEquals("Bearer fixture-token", server.takeRequest().headers["Authorization"])
            cache.open(uri, "r").use { assertEquals(true, it.statSize > 0) }
            assertEquals(uri, cache.prepare("one", source, allowNetwork = false))
            assertEquals(1, server.requestCount)
            assertThrows(FileNotFoundException::class.java) { cache.open(uri, "rw") }
            assertThrows(FileNotFoundException::class.java) {
                cache.open(Uri.parse("content://${uri.authority}/../secret"), "r")
            }
            paired.value = paired.value!!.copy(deviceId = "another-account")
            assertThrows(FileNotFoundException::class.java) { cache.open(uri, "r") }
        }
    }

    @Test
    fun `offline cache miss and invalid images do not expose a network proxy`() = runTest {
        MockWebServer().apply { start() }.use { server ->
            val paired = Session(server.url("/").toString().trimEnd('/'), "server", "Test", "missing-art", "fixture")
            val sessions = mockk<SessionStore>(relaxed = true)
            every { sessions.session } returns MutableStateFlow(paired)
            every { sessions.current } returns paired
            val cache =
                SessionArtworkCache(RuntimeEnvironment.getApplication(), sessions, OkHttpClient(), backgroundScope)
            val source = Uri.parse(server.url("/cover").toString())
            assertNull(cache.prepare("missing", source, allowNetwork = false))
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse.Builder().body("not an image").build())
            assertNull(cache.prepare("missing", source))
            assertNull(cache.prepare("missing", Uri.parse("file:///private/secret")))
            assertEquals(1, server.requestCount)
        }
    }
}
