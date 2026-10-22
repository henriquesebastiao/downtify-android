package com.henriquesebastiao.downtify.core.player

import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadAheadRequestTest {
    @Test
    fun `cancel interrupts HTTP headers without cancelling another plan`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        MockWebServer().apply { start() }.use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.url.encodedPath == "/slow") {
                        entered.countDown()
                        release.await(30, TimeUnit.SECONDS)
                    }
                    return MockResponse.Builder().body("done").build()
                }
            }
            val client = OkHttpClient()
            val pending = ReadAheadRequest(client)
            val spec = DataSpec.Builder().setUri(server.url("/slow").toString()).build()
            try {
                val reader = executor.submit<IOException?> {
                    val source = pending.http.createDataSource()
                    try {
                        source.open(spec)
                        null
                    } catch (error: IOException) {
                        error
                    } finally {
                        source.close()
                    }
                }
                assertTrue("Request must reach the server", entered.await(5, TimeUnit.SECONDS))
                pending.cancel()
                assertNotNull(reader.get(5, TimeUnit.SECONDS))
                val next = ReadAheadRequest(client).http.createDataSource()
                try {
                    next.open(DataSpec.Builder().setUri(server.url("/fast").toString()).build())
                    val bytes = ByteArray(4)
                    assertEquals(4, next.read(bytes, 0, bytes.size))
                    assertEquals("done", String(bytes))
                } finally {
                    next.close()
                }
            } finally {
                release.countDown()
                pending.cancel()
                executor.shutdownNow()
            }
        }
    }
}
