package com.henriquesebastiao.downtify.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadAheadPolicyTest {
    @Test
    fun `defaults do not start unsolicited transfers`() {
        assertEquals(ReadAheadPolicy(0, 100, false), ReadAheadPolicy())
    }

    @Test
    fun `invalid stored preferences are bounded and quota uses decimal megabytes`() {
        assertEquals(ReadAheadPolicy(0, 10), ReadAheadPolicy(-7, Int.MIN_VALUE).normalized())
        assertEquals(ReadAheadPolicy(3, 200), ReadAheadPolicy(9, Int.MAX_VALUE).normalized())
        assertEquals(120, ReadAheadPolicy(cacheMb = 116).normalized().cacheMb)
        assertEquals(200_000_000L, ReadAheadPolicy(cacheMb = 200).cacheBytes)
        (0..3).forEach { assertEquals(it, ReadAheadPolicy(tracks = it).normalized().tracks) }
        (10..200 step 10).forEach { assertEquals(it, ReadAheadPolicy(cacheMb = it).normalized().cacheMb) }
    }
}
