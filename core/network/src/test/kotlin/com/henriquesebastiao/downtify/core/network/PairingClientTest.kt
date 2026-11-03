package com.henriquesebastiao.downtify.core.network

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PairingClientTest {
    private val server = MockWebServer()
    private val client = PairingClient(ApiFactory(OkHttpClient()))

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun base() = server.url("/").toString()

    @Test
    fun `signing in trades the password for a device token`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).body(
                """
                {"token":"dtfy_8uz4_s3cret","device":{"id":"8uz4","name":"Pixel 8"},
                 "server":{"server_id":"cf12","name":"nas"},"user":{"username":"maria","role":"user"}}
                """.trimIndent(),
            ).build(),
        )

        val result = client.signIn(base(), " maria ", "correct horse", "Pixel 8")

        assertTrue(result is PairingClient.Result.Paired)
        val session = (result as PairingClient.Result.Paired).session
        assertEquals("dtfy_8uz4_s3cret", session.token)
        assertEquals("8uz4", session.deviceId)
        assertEquals("cf12", session.serverId)
        assertEquals("maria", session.username)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/auth/token", request.url.encodedPath)
        assertEquals(
            """{"username":"maria","password":"correct horse","device_name":"Pixel 8","platform":"android"}""",
            request.body?.utf8(),
        )
    }

    @Test
    fun `a wrong password is not a wrong code`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).body("""{"detail":"Wrong username or password"}""").build())

        assertEquals(PairingClient.Result.WrongPassword, client.signIn(base(), "maria", "nope", "Pixel 8"))
    }

    @Test
    fun `too many attempts waits as long as the server says`() = runTest {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "42").build())

        assertEquals(
            PairingClient.Result.TooManyAttempts(42),
            client.signIn(base(), "maria", "nope", "Pixel 8"),
        )
    }

    @Test
    fun `a server with sign-in turned off asks for a pairing code`() = runTest {
        server.enqueue(MockResponse.Builder().code(409).build())

        assertEquals(PairingClient.Result.SignInOff, client.signIn(base(), "maria", "x", "Pixel 8"))
    }

    @Test
    fun `a wrong pairing code is still a wrong code`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).build())

        assertEquals(PairingClient.Result.WrongCode, client.pair(base(), "K7QM-2XPD", "Pixel 8"))
        assertEquals("/api/auth/pair", server.takeRequest().url.encodedPath)
    }
}
