package com.henriquesebastiao.downtify.core.data.offline

import org.junit.Assert.assertEquals
import org.junit.Test

/** What [OfflineRepository.downloadNext] may do with the free space it has. */
class OfflineSpaceCheckTest {
    private val margin = OfflineRepository.FREE_SPACE_MARGIN

    @Test
    fun aTrackBiggerThanTheFreeSpaceIsPassedOverNotTheWholeQueue() {
        assertEquals(SpaceCheck.SkipTrack, OfflineRepository.spaceCheck(margin + 10_000, 100_000))
    }

    @Test
    fun aTrackThatFitsWithTheMarginKeepsDownloadsGoing() {
        assertEquals(SpaceCheck.Enough, OfflineRepository.spaceCheck(margin + 100_000, 100_000))
    }

    @Test
    fun withoutTheMarginLeftTheRunStops() {
        assertEquals(SpaceCheck.Full, OfflineRepository.spaceCheck(margin - 1, 1))
        assertEquals(SpaceCheck.Full, OfflineRepository.spaceCheck(0, 0))
    }
}
