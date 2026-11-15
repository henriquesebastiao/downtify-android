package com.henriquesebastiao.downtify.core.data.musicai

import com.henriquesebastiao.downtify.core.model.DjLocalProblem
import com.henriquesebastiao.downtify.core.model.DjLocalRequest
import com.henriquesebastiao.downtify.core.model.DjLocalResult
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiStatusDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjLocalPlanDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjLocalPlansDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjLocalTrackDto
import com.henriquesebastiao.downtify.core.network.session.Session
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DjLocalPlansRepositoryTest {
    private val owner = Session("https://fixture.test", "server", "Test", "device", "fixture")
    private val session = MutableStateFlow<Session?>(owner)
    private val capabilities = MutableStateFlow(
        MusicAiCapabilitiesDto(
            version = 1,
            features = mapOf("dj_interactive" to true),
            dj = MusicAiStatusDto(localPlansVersion = 1),
        ),
    )
    private val music = mockk<MusicAiRepository> {
        every { this@mockk.session } returns this@DjLocalPlansRepositoryTest.session
        every { this@mockk.capabilities } returns this@DjLocalPlansRepositoryTest.capabilities
    }
    private val request = DjLocalRequest(listOf("a", "b"), 5_000, 1)
    private val body = MusicDjLocalPlansDto(
        version = 1,
        revision = "window",
        analysisModel = "pinned",
        tracks = listOf("a", "b").mapIndexed { i, id -> MusicDjLocalTrackDto(i, id, status = "missing") },
        plans = listOf(MusicDjLocalPlanDto(0, 1, "unavailable", reason = "missing_analysis")),
    )

    @Test fun `old or disabled servers never receive a local plans request`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repository = DjLocalPlansRepository(music, scope)
            capabilities.value = capabilities.value.copy(dj = MusicAiStatusDto())
            assertEquals(DjLocalResult.Unavailable(DjLocalProblem.Unsupported), repository.load(request))
            capabilities.value = capabilities.value.copy(features = emptyMap())
            assertEquals(DjLocalResult.Unavailable(DjLocalProblem.Disabled), repository.load(request))
            coVerify(exactly = 0) { music.localPlans(any(), any(), any()) }
        } finally {
            scope.cancel()
        }
    }

    @Test fun `late response from retired owner cannot be cached or applied`() = runBlocking {
        withTimeout(5_000) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            try {
                val entered = CompletableDeferred<Unit>()
                val response = CompletableDeferred<MusicDjLocalPlansDto>()
                coEvery { music.localPlans(any(), any(), owner) } coAnswers {
                    entered.complete(Unit)
                    response.await()
                }
                val repository = DjLocalPlansRepository(music, scope)
                val old = async { repository.load(request) }
                entered.await()
                session.value = owner.copy(baseUrl = "https://other-fixture.test")
                response.complete(body)
                assertTrue(runCatching { old.await() }.exceptionOrNull() is CancellationException)
                coEvery { music.localPlans(any(), any(), checkNotNull(session.value)) } returns body
                assertTrue(repository.load(request) is DjLocalResult.Available)
                assertTrue(repository.load(request) is DjLocalResult.Available)
                coVerify(exactly = 1) { music.localPlans(any(), any(), owner) }
                coVerify(exactly = 1) { music.localPlans(any(), any(), checkNotNull(session.value)) }
            } finally {
                scope.cancel()
            }
        }
    }
}
