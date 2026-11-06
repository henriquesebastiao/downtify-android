package com.henriquesebastiao.downtify.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDjPlanTest {
    private val timing = LocalDjAnalysis(120f, listOf(0, 2000, 4000, 6000, 8000), 0.9f, "synthetic")

    @Test fun `trusted downbeats align a four beat overlap within a four percent tempo bound`() {
        val plan = LocalDjPlan.create(10_000, timing, timing.copy(bpm = 122f))
        assertTrue(plan.analysed)
        assertEquals(8000L, plan.startMs)
        assertEquals(2000L, plan.durationMs)
        assertEquals(0L, plan.cueMs)
        assertEquals(120f / 122, plan.speed, 0.001f)
    }

    @Test fun `uncertain or mismatched timing uses a short unchanged speed transition`() {
        val missing = LocalDjPlan.create(10_000, timing.copy(confidence = 0.3f), timing)
        assertFalse(missing.analysed)
        assertEquals(1200L, missing.durationMs)
        val incompatible = LocalDjPlan.create(10_000, timing, timing.copy(bpm = 80f))
        assertEquals(LocalDjFallback.TempoMismatch, incompatible.fallback)
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
