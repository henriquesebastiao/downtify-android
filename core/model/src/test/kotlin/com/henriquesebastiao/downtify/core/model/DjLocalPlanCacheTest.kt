package com.henriquesebastiao.downtify.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DjLocalPlanCacheTest {
    private var now = 0L
    private val cache = DjLocalPlanCache<String>({ now }, 30_000)
    private val result = DjLocalResult.Unavailable(DjLocalProblem.MissingAnalysis)
    private fun request(ids: List<String> = listOf("a", "a", "b"), revision: Long = 1) =
        DjLocalRequest(ids, 5_000, revision)

    @Test fun `late account or server completion cannot populate the replacement window`() {
        val old = cache.begin("server-a-account-a", request())
        val fresh = cache.begin("server-b-account-a", request())
        assertFalse(cache.complete(old, result))
        assertNull(cache.cached(fresh))
        assertTrue(cache.complete(fresh, result))
        assertEquals(result, cache.cached(fresh))
    }

    @Test fun `occurrences and client generations are part of the cache identity`() {
        val old = cache.begin("owner", request())
        val reordered = cache.begin("owner", request(listOf("a", "b", "a")))
        assertFalse(cache.complete(old, result))
        val nextGeneration = cache.begin("owner", request(listOf("a", "b", "a"), 2))
        assertFalse(cache.complete(reordered, result))
        cache.complete(nextGeneration, result)
        cache.clear()
        assertNull(cache.cached(nextGeneration))
    }

    @Test fun `request list is copied and expiry also rejects clock reversal`() {
        val ids = mutableListOf("a", "b")
        val ticket = cache.begin("owner", request(ids))
        ids[0] = "changed"
        assertEquals(listOf("a", "b"), ticket.request.ids)
        cache.complete(ticket, result)
        now = 29_999
        assertEquals(result, cache.cached(ticket))
        now = 30_000
        assertNull(cache.cached(ticket))
        now = -1
        assertNull(cache.cached(ticket))
    }

    @Test fun `the cache refuses unbounded or invalid requests`() {
        listOf(emptyList(), listOf("a"), List(5) { "a" }, listOf("a", "")).forEach { ids ->
            assertFalse(request(ids).valid())
            assertTrue(runCatching { cache.begin("owner", request(ids)) }.isFailure)
        }
        assertFalse(request().copy(transitionMs = 999).valid())
        assertFalse(request().copy(transitionMs = 12_001).valid())
    }
}
