package com.henriquesebastiao.downtify.core.player

import com.henriquesebastiao.downtify.core.network.session.Session
import java.io.IOException
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DjHttpRecoveryTest {
    private fun owner(server: MockWebServer) =
        Session(server.url("/").toString(), "fixture", "Fixture", "device", "fixture-token")

    @Test fun `temporary unavailability retries then returns audio`() {
        MockWebServer().use { server ->
            server.start()
            val owner = owner(server)
            val client = OkHttpClient.Builder().addInterceptor(DjHttpRecovery({ owner }, { true })).build()
            server.enqueue(MockResponse.Builder().code(503).build())
            server.enqueue(MockResponse.Builder().body("audio").build())
            client.newCall(Request.Builder().url(server.url("/segment.ts")).build()).execute().use {
                assertEquals("audio", it.body.string())
            }
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun `authentication failures are never retried`() {
        MockWebServer().use { server ->
            server.start()
            val owner = owner(server)
            val client = OkHttpClient.Builder().addInterceptor(DjHttpRecovery({ owner }, { true })).build()
            server.enqueue(MockResponse.Builder().code(401).build())
            client.newCall(Request.Builder().url(server.url("/segment.ts")).build()).execute().use {
                assertEquals(401, it.code)
            }
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `a disabled module cannot create an audio request`() {
        MockWebServer().use { server ->
            server.start()
            val owner = owner(server)
            val client = OkHttpClient.Builder().addInterceptor(DjHttpRecovery({ owner }, { false })).build()
            assertThrows(IOException::class.java) {
                client.newCall(Request.Builder().url(server.url("/segment.ts")).build()).execute()
            }
            assertEquals(0, server.requestCount)
        }
    }
}
