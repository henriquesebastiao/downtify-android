package com.henriquesebastiao.downtify.core.data.similar

import com.henriquesebastiao.downtify.core.data.ServerResult
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.network.ApiFactory
import com.henriquesebastiao.downtify.core.network.session.Session
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SimilarAvailabilityTest {
    private val server = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob().apply { cancel() } + Dispatchers.IO)
    private lateinit var ai: MusicAiRepository
    private lateinit var similar: SimilarRepository

    @Before
    fun setUp() {
        server.start()
        val paired =
            MutableStateFlow<Session?>(Session(server.url("/").toString(), "server", "Test", "device", "fixture"))
        val sessions = mockk<SessionStore>(relaxed = true)
        every { sessions.current } answers { paired.value }
        every { sessions.session } returns paired
        val apis = ApiFactory(OkHttpClient())
        ai = MusicAiRepository(sessions, apis, scope)
        similar = SimilarRepository(sessions, apis, ai)
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
    }

    private fun answer(code: Int, body: String = "{}") {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    @Test
    fun `a temporary capability failure never invokes the legacy radio`() = runTest {
        answer(503)
        answer(200, """{"tracks":[]}""")
        assertEquals(ServerResult.Failed(503), similar.similar("Synthetic artist", "Synthetic title"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `authentication failure is not an old server`() = runTest {
        answer(401)
        answer(200, """{"tracks":[]}""")
        assertEquals(ServerResult.Failed(401), similar.similar("Synthetic artist", "Synthetic title"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `unreadable capabilities never invoke an external fallback`() = runTest {
        answer(200, "not-json")
        answer(200, """{"tracks":[]}""")
        assertEquals(ServerResult.Failed(null), similar.similar("Synthetic artist", "Synthetic title"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a confirmed older server keeps its original radio endpoint`() = runTest {
        answer(404)
        answer(200, """{"tracks":[]}""")
        assertTrue(similar.similar("Synthetic artist", "Synthetic title") is ServerResult.Ok)
        assertEquals(2, server.requestCount)
        assertEquals("/api/v1/music-ai/capabilities", server.takeRequest().url.encodedPath)
        assertEquals("/api/similar/tracks", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `disabled modules are refused without a recommendation request`() = runTest {
        answer(200, """{"version":1,"features":{"similarity":false,"external_beta":false}}""")
        answer(200, """{"tracks":[]}""")
        assertEquals(ServerResult.Failed(409), similar.similar("Synthetic artist", "Synthetic title"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `an unsupported future protocol is not treated as legacy`() = runTest {
        answer(200, """{"version":2}""")
        answer(200, """{"tracks":[]}""")
        assertEquals(ServerResult.Failed(503), similar.similar("Synthetic artist", "Synthetic title"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `known capabilities survive a transient refresh failure`() = runTest {
        answer(200, """{"version":1,"features":{"similarity":true}}""")
        assertTrue(ai.refresh() is ServerResult.Ok)
        answer(503)
        assertEquals(ServerResult.Failed(503), ai.refresh())
        assertEquals(1, ai.capabilities.value.version)
        assertTrue(ai.capabilities.value.enabled("similarity"))
    }
}
