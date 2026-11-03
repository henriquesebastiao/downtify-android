package com.henriquesebastiao.downtify.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePlannerTest {
    private fun file(id: String, bytes: Long = 1_000) = OfflineFile(id, "$id.audio", bytes)

    @Test
    fun downloadsWhatIsMissingInOrder() {
        val plan = OfflinePlanner.plan(
            wanted = listOf(track("a"), track("b"), track("c")),
            files = mapOf("b" to file("b")),
            limitBytes = OfflinePlanner.UNLIMITED,
        )
        assertEquals(listOf("a", "c"), plan.toDownload.map { it.id })
        assertEquals(setOf("a", "b", "c"), plan.keep)
        assertTrue(plan.toDelete.isEmpty())
        assertEquals(3_000L, plan.plannedBytes)
    }

    @Test
    fun aTrackInTwoCollectionsIsPlannedOnce() {
        val plan = OfflinePlanner.plan(
            wanted = listOf(track("a"), track("b"), track("a")),
            files = emptyMap(),
            limitBytes = OfflinePlanner.UNLIMITED,
        )
        assertEquals(listOf("a", "b"), plan.toDownload.map { it.id })
        assertEquals(2_000L, plan.plannedBytes)
    }

    @Test
    fun filesNoCollectionWantsAreDeleted() {
        val plan = OfflinePlanner.plan(
            wanted = listOf(track("a")),
            files = mapOf("a" to file("a"), "gone" to file("gone")),
            limitBytes = OfflinePlanner.UNLIMITED,
        )
        assertEquals(setOf("gone"), plan.toDelete)
        assertTrue(plan.toDownload.isEmpty())
    }

    @Test
    fun nothingWantedDeletesEverything() {
        val plan = OfflinePlanner.plan(emptyList(), mapOf("a" to file("a")), OfflinePlanner.UNLIMITED)
        assertEquals(setOf("a"), plan.toDelete)
        assertTrue(plan.keep.isEmpty())
    }

    @Test
    fun theLimitSkipsWhatDoesNotFitButKeepsSmallerTracksAfterIt() {
        val plan = OfflinePlanner.plan(
            wanted = listOf(track("a", size = 600), track("big", size = 900), track("c", size = 300)),
            files = emptyMap(),
            limitBytes = 1_000,
        )
        assertEquals(listOf("a", "c"), plan.toDownload.map { it.id })
        assertEquals(setOf("big"), plan.overLimit)
        assertEquals(900L, plan.plannedBytes)
    }

    @Test
    fun loweringTheLimitDeletesTheNewestFirst() {
        val plan = OfflinePlanner.plan(
            wanted = listOf(track("old"), track("new")),
            files = mapOf("old" to file("old"), "new" to file("new")),
            limitBytes = 1_500,
        )
        assertEquals(setOf("old"), plan.keep)
        assertEquals(setOf("new"), plan.toDelete)
        assertEquals(setOf("new"), plan.overLimit)
    }

    @Test
    fun aFileOnThePhoneCountsItsRealSize() {
        val plan = OfflinePlanner.plan(
            wanted = listOf(track("a", size = 5_000), track("b", size = 500)),
            files = mapOf("a" to file("a", bytes = 400)),
            limitBytes = 1_000,
        )
        assertEquals(setOf("a", "b"), plan.keep)
        assertEquals(900L, plan.plannedBytes)
    }

    @Test
    fun progressCountsTheCollectionsTracksOnThePhone() {
        val tracks = listOf(track("a"), track("b", size = 5_000), track("c"))
        val files = mapOf("a" to file("a", 700))
        val plan = OfflinePlanner.plan(tracks, files, limitBytes = 2_000)
        val progress = OfflineProgress.of(tracks, files, plan)
        assertEquals(OfflineProgress(total = 3, downloaded = 1, bytes = 700, overLimit = 1), progress)
        assertTrue(!progress.isComplete)
    }

    // The no-break space keeps a size on one line: "412" never ends a line with "MB" on the next.
    @Test
    fun formatsBytesInDecimalUnits() {
        assertEquals("0\u00A0B", formatBytes(0))
        assertEquals("412\u00A0MB", formatBytes(412_000_000))
        assertEquals("2.4\u00A0GB", formatBytes(2_400_000_000))
        assertEquals("128\u00A0GB", formatBytes(128_000_000_000))
        assertEquals("12\u00A0KB", formatBytes(12_000))
    }
}
