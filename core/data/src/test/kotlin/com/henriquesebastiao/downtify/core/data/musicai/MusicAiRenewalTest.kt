package com.henriquesebastiao.downtify.core.data.musicai

import com.henriquesebastiao.downtify.core.network.ApiFactory
import com.henriquesebastiao.downtify.core.network.session.Session
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
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
class MusicAiRenewalTest {
    private val server = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob().apply { cancel() } + Dispatchers.IO)
    private val cache = mockk<MusicAiCache>(relaxed = true)
    private lateinit var paired: MutableStateFlow<Session?>
    private lateinit var music: MusicAiRepository

    @Before fun setUp() {
        server.start()
        paired = MutableStateFlow(Session(server.url("/").toString(), "server", "Test", "device", "fixture"))
        val sessions = mockk<SessionStore>(relaxed = true)
        every { sessions.current } answers { paired.value }
        every { sessions.session } returns paired
        every { cache.scope(any()) } answers { firstArg<Session>().token }
        music = MusicAiRepository(sessions, ApiFactory(OkHttpClient()), cache, scope)
    }

    @After fun tearDown() {
        scope.cancel()
        server.close()
    }

    private suspend fun supported() {
        server.enqueue(
            MockResponse.Builder().body("""{"version":1,"home_cards":true,"playlist_refresh":true}""").build(),
        )
        music.refresh(force = true)
    }

    @Test fun `weekly creation refuses capabilities retained from another account`() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """{"version":1,"dj_weekly_sets":true,
                    "features":{"weekly":true,"dj_prepared":true,"dj_weekly":true}}""",
            ).build(),
        )
        music.refresh(force = true)
        paired.value = requireNotNull(paired.value).copy(token = "different-fixture")
        var cancelled = false
        try {
            music.djPrepareWeekly("rock", "stale-account")
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertEquals(1, server.requestCount)
    }

    @Test fun `weekly creation keeps its explicit idempotent intent`() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """{"version":1,"dj_weekly_sets":true,
                    "features":{"weekly":true,"dj_prepared":true,"dj_weekly":true}}""",
            ).build(),
        )
        music.refresh(force = true)
        server.takeRequest()
        server.enqueue(
            MockResponse.Builder().body(
                """{"id":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","state":"queued",
                    "theme":"energie","weekly_theme":"rock"}""",
            ).build(),
        )
        assertEquals("rock", music.djPrepareWeekly("rock", "same-intent").weeklyTheme)
        val request = server.takeRequest()
        assertEquals("/api/v1/music-ai/dj/weekly/rock", request.url.encodedPath)
        assertEquals("""{"request_id":"same-intent"}""", request.body?.utf8())
    }

    @Test fun `renewal invalidates a home response already in flight`() = runTest {
        supported()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val accepted = CountDownLatch(1)
        var homeCount = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.url.encodedPath.endsWith("/refresh")) {
                    accepted.countDown()
                    return MockResponse.Builder().body("""{"accepted":true,"revision":1}""").build()
                }
                homeCount++
                if (homeCount == 1) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return MockResponse.Builder().body(
                        """{"cards":[{"cadence":"weekly","mood":"rock","period":"2026-11-09","count":25}]}""",
                    ).build()
                }
                return MockResponse.Builder().body("""{"cards":[]}""").build()
            }
        }
        try {
            val old = async(Dispatchers.Default) { music.home() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val renewal = async(Dispatchers.Default) { music.renewPlaylists("intent") }
            assertTrue(accepted.await(5, TimeUnit.SECONDS))
            release.countDown()
            old.await()
            renewal.await()
            assertTrue(music.home().isEmpty())
            assertEquals(2, homeCount)
            coVerify(exactly = 1) { cache.clearHome("fixture") }
        } finally {
            release.countDown()
        }
    }

    @Test fun `logout refuses a renewal even while old capabilities remain cached`() = runTest {
        supported()
        paired.value = null
        var cancelled = false
        try {
            music.renewPlaylists("logged-out")
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertEquals(1, server.requestCount)
        coVerify(exactly = 0) { cache.clearHome(any()) }
    }

    @Test fun `renewal from a previous account cannot clear the new account cache`() = runTest {
        supported()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                return MockResponse.Builder().body("""{"accepted":true}""").build()
            }
        }
        try {
            val renewal = async(Dispatchers.Default) {
                try {
                    music.renewPlaylists("old-intent")
                    false
                } catch (_: CancellationException) {
                    true
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            paired.value = paired.value!!.copy(token = "new-account")
            release.countDown()
            assertTrue(renewal.await())
            coVerify(exactly = 0) { cache.clearHome(any()) }
        } finally {
            release.countDown()
        }
    }
}
