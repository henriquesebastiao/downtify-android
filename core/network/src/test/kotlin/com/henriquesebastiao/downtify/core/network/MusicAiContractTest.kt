package com.henriquesebastiao.downtify.core.network

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class MusicAiContractTest {
    private val server = MockWebServer()
    private val api get() = ApiFactory(OkHttpClient()).createMusicAi(server.url("/").toString())

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    @Test
    fun `two versions of a song are addressed by the chosen native id`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"source":"local","tracks":[]}""").build())
        api.similar("", "", "t0123456789abcdef")
        val url = server.takeRequest().url
        assertEquals("/api/v1/music-ai/similar", url.encodedPath)
        assertEquals("t0123456789abcdef", url.queryParameter("seed"))
        assertEquals("", url.queryParameter("artist"))
        assertEquals("", url.queryParameter("track"))
    }

    @Test
    fun `external beta uses its own route instead of a local recommendation`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"tracks":[]}""").build())
        api.externalSimilar("Synthetic artist", "Synthetic title")
        val url = server.takeRequest().url
        assertEquals("/api/v1/music-ai/external-similar", url.encodedPath)
        assertEquals("Synthetic title", url.queryParameter("track"))
        assertFalse(url.queryParameterNames.contains("seed"))
    }

    @Test
    fun `preferences rely on authenticated ownership and default missing features to off`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"effective":{"similarity":false}}""").build())
        api.preferences(mapOf("similarity" to false))
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("""{"similarity":false}""", request.body?.utf8())
        server.enqueue(MockResponse.Builder().body("""{"version":1,"features":{"similarity":false}}""").build())
        val capabilities = api.capabilities()
        assertFalse(capabilities.enabled("similarity"))
        assertFalse(capabilities.enabled("external_beta"))
        assertFalse(capabilities.enabled("dj_interactive"))
    }

    @Test fun `home playlist cards use one owner scoped metadata request`() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """{"playlists":[{"cadence":"daily","mood":"calme","period":"2026-11-06","count":40,"created":1}]}""",
            ).build(),
        )
        val result = api.playlists()
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/music-ai/playlists", request.url.encodedPath)
        assertEquals(0, request.url.querySize)
        assertEquals(1, server.requestCount)
        assertEquals(40, result.playlists.single().count)
        assertEquals("calme", result.playlists.single().mood)
    }

    @Test fun `DJ contract uses an idempotent request and ignores arbitrary server media URLs`() = runTest {
        val id = "a".repeat(32)
        server.enqueue(
            MockResponse.Builder().body(
                """{"id":"$id","state":"queued","theme":"fete","media_url":"https://untrusted.test/file"}""",
            ).build(),
        )
        val found = api.djCreate(
            com.henriquesebastiao.downtify.core.network.dto.MusicDjSelectionDto("intent", "fete", 2),
        )
        val request = server.takeRequest()
        assertEquals("/api/v1/music-ai/dj/sessions", request.url.encodedPath)
        assertEquals("POST", request.method)
        val body = request.body?.utf8().orEmpty()
        org.junit.Assert.assertTrue(body.contains("\"request_id\":\"intent\""))
        assertFalse(body.contains("owner"))
        assertEquals(id, found.id)
        assertEquals(
            "https://fixture.test/api/v1/music-ai/dj/sessions/$id/media",
            ServerUrls.djFile("https://fixture.test", found.id),
        )
    }
}
