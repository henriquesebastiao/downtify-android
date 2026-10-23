package com.henriquesebastiao.downtify.core.data.offline

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrackDownloaderTest {
    private val server = MockWebServer()
    private val downloader = TrackDownloader(OkHttpClient())
    private lateinit var part: File

    @Before
    fun setUp() {
        server.start()
        part = Files.createTempFile("track", ".part").toFile().apply { delete() }
    }

    @After
    fun tearDown() {
        server.close()
        part.delete()
    }

    private fun url() = server.url("/api/v1/tracks/t1/stream").toString()

    @Test
    fun downloadsAWholeFile() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("0123456789").build())

        val result = downloader.download(url(), part)

        assertEquals(TrackDownloader.Result.Complete(10), result)
        assertEquals("0123456789", part.readText())
        assertNull(server.takeRequest().headers["Range"])
    }

    @Test
    fun wholeFileCarriesItsEtagForAnOfflineMeasurement() = runTest {
        server.enqueue(MockResponse.Builder().code(200).addHeader("ETag", "source-one").body("audio").build())
        assertEquals(TrackDownloader.Result.Complete(5, "source-one"), downloader.download(url(), part))
    }

    @Test
    fun resumedBytesCannotClaimAWholeFileEtag() = runTest {
        part.writeText("a")
        server.enqueue(
            MockResponse.Builder().code(206).addHeader("ETag", "new-source")
                .addHeader("Content-Range", "bytes 1-4/5").body("udio").build(),
        )
        assertEquals(TrackDownloader.Result.Complete(5), downloader.download(url(), part))
    }

    @Test
    fun resumesAPartWithARangeRequest() = runTest {
        part.writeText("012")
        server.enqueue(
            MockResponse.Builder().code(206).addHeader("Content-Range", "bytes 3-9/10").body("3456789").build(),
        )

        val result = downloader.download(url(), part)

        assertEquals(TrackDownloader.Result.Complete(10), result)
        assertEquals("0123456789", part.readText())
        assertEquals("bytes=3-", server.takeRequest().headers["Range"])
    }

    @Test
    fun startsOverWhenTheServerIgnoresTheRange() = runTest {
        part.writeText("xyz")
        server.enqueue(MockResponse.Builder().code(200).body("0123456789").build())

        val result = downloader.download(url(), part)

        assertEquals(TrackDownloader.Result.Complete(10), result)
        assertEquals("0123456789", part.readText())
    }

    @Test
    fun aPartLongerThanTheFileStartsOverOnce() = runTest {
        part.writeText("0123456789AB")
        server.enqueue(MockResponse.Builder().code(416).build())
        server.enqueue(MockResponse.Builder().code(200).body("0123456789").build())

        val result = downloader.download(url(), part)

        assertEquals(TrackDownloader.Result.Complete(10), result)
        assertEquals("bytes=12-", server.takeRequest().headers["Range"])
        assertNull(server.takeRequest().headers["Range"])
    }

    @Test
    fun aMissingTrackIsGone() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())
        assertEquals(TrackDownloader.Result.Gone, downloader.download(url(), part))
    }

    @Test
    fun aServerErrorFailsAndKeepsThePart() = runTest {
        part.writeText("012")
        server.enqueue(MockResponse.Builder().code(503).build())

        val result = downloader.download(url(), part)

        assertEquals(TrackDownloader.Result.Failed(503), result)
        assertEquals("012", part.readText())
    }

    @Test
    fun anUnreachableServerFails() = runTest {
        val url = url()
        server.close()
        val result = downloader.download(url, part)
        assertTrue(result is TrackDownloader.Result.Failed && result.status == null)
    }
}
