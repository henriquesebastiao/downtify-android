package com.henriquesebastiao.downtify.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDjPlanTest {
    private val timing = LocalDjAnalysis(120f, listOf(0, 2000, 4000, 6000, 8000), 0.9f, "synthetic")

    @Test fun `trusted downbeats align the requested overlap within a four percent tempo bound`() {
        val plan = LocalDjPlan.create(10_000, timing, timing.copy(bpm = 122f), 2_000)
        assertTrue(plan.analysed)
        assertEquals(8000L, plan.startMs)
        assertEquals(2000L, plan.durationMs)
        assertEquals(0L, plan.cueMs)
        assertEquals(120f / 122, plan.speed, 0.001f)
    }

    @Test fun `a longer request starts on an earlier downbeat`() {
        val plan = LocalDjPlan.create(10_000, timing, timing, 5_000)
        assertTrue(plan.analysed)
        assertEquals(4000L, plan.startMs)
        assertEquals(5000L, plan.durationMs)
    }

    @Test fun `the request is bounded to one and twelve seconds and to half the track`() {
        val shortest = LocalDjPlan.create(10_000, timing, timing, 0)
        assertEquals(1000L, shortest.durationMs)
        assertEquals(8000L, shortest.startMs)
        val default = LocalDjPlan.create(10_000, timing, timing)
        assertEquals(5000L, default.durationMs)
        val long = LocalDjAnalysis(120f, (0L..58_000L step 2_000L).toList(), 0.9f, "synthetic")
        val longest = LocalDjPlan.create(60_000, long, long, 20_000)
        assertEquals(12_000L, longest.durationMs)
        assertEquals(48_000L, longest.startMs)
        val half = LocalDjPlan.create(10_000, timing, timing, 12_000)
        assertEquals(5000L, half.durationMs)
    }

    @Test fun `uncertain or mismatched timing keeps the request at unchanged speed`() {
        val missing = LocalDjPlan.create(10_000, timing.copy(confidence = 0.3f), timing, 3_000)
        assertFalse(missing.analysed)
        assertEquals(LocalDjFallback.MissingAnalysis, missing.fallback)
        assertEquals(3000L, missing.durationMs)
        assertEquals(7000L, missing.startMs)
        assertEquals(1f, missing.speed, 0.001f)
        val incompatible = LocalDjPlan.create(10_000, timing, timing.copy(bpm = 80f), 3_000)
        assertEquals(LocalDjFallback.TempoMismatch, incompatible.fallback)
        assertEquals(3000L, incompatible.durationMs)
        assertEquals(1f, incompatible.speed, 0.001f)
    }

    @Test fun `gain proportions have smooth endpoints and cannot add full scale peaks`() {
        assertEquals(1f, localDjGains(0f).first, 0.001f)
        assertEquals(0f, localDjGains(0f).second, 0.001f)
        assertEquals(0.5f, localDjGains(0.5f).first, 0.001f)
        assertEquals(0.5f, localDjGains(0.5f).second, 0.001f)
        assertEquals(1f, localDjGains(1f).second, 0.001f)
        (0..1000).forEach {
            val (outgoing, incoming) = localDjGains(it / 1000f)
            assertTrue(outgoing >= 0f && incoming >= 0f)
            assertTrue(outgoing + incoming <= 1.00001f)
        }
    }
}
