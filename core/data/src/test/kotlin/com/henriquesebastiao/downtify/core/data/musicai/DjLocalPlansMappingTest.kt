package com.henriquesebastiao.downtify.core.data.musicai

import com.henriquesebastiao.downtify.core.model.DjLocalRequest
import com.henriquesebastiao.downtify.core.network.dto.MusicDjLocalPlanDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjLocalPlansDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjLocalTrackDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DjLocalPlansMappingTest {
    private val request = DjLocalRequest(listOf("a", "b"), 1_000, 1)
    private val body = MusicDjLocalPlansDto(
        version = 1, revision = "window", analysisModel = "pinned",
        tracks = listOf("a", "b").mapIndexed { index, id ->
            MusicDjLocalTrackDto(index, id, "source-$id", "ready", "source_verified", 300f, .9f, listOf(0, 800, 1_600))
        },
        plans = listOf(MusicDjLocalPlanDto(0, 1, "ready", true, 9_200, 800, 800, 1.02f, "bars_aligned")),
    )

    @Test fun `real aligned bars may be shorter than the requested preference minimum`() {
        val result = localPlansBody(body, request)
        assertTrue(result.plans.single().ready)
        assertEquals(800L, result.plans.single().durationMs)
        assertEquals(1.02f, result.plans.single().speed)
    }

    @Test fun `ready plans require physically verified reliable source analyses`() {
        listOf("", "cached_only", "source_changed", "source_timeout").forEach { validity ->
            rejects(body.copy(tracks = body.tracks.map { it.copy(validity = validity) }))
        }
        rejects(body.copy(tracks = body.tracks.map { it.copy(confidence = .74f) }))
        rejects(body.copy(tracks = body.tracks.map { it.copy(bpm = Float.NaN) }))
        rejects(body.copy(tracks = body.tracks.map { it.copy(downbeatsMs = listOf(800, 0)) }))
    }

    @Test fun `wrong occurrences mismatched revisions and unbounded data are rejected`() {
        rejects(body.copy(tracks = body.tracks.reversed()))
        rejects(body.copy(plans = body.plans.map { it.copy(toIndex = 0) }))
        rejects(body.copy(plans = body.plans.map { it.copy(speed = 1.041f) }))
        rejects(body.copy(plans = body.plans.map { it.copy(durationMs = 1_001) }))
        rejects(body.copy(tracks = body.tracks.map { it.copy(downbeatsMs = List(20_001) { n -> n.toLong() }) }))
        val duplicates = request.copy(ids = listOf("a", "a"))
        val rows = body.tracks.mapIndexed { index, _ -> body.tracks.first().copy(index = index) }
        assertTrue(localPlansBody(body.copy(tracks = rows), duplicates).plans.single().ready)
        assertTrue(runCatching {
            localPlansBody(body.copy(tracks = rows.mapIndexed { i, row -> row.copy(revision = "r$i") }), duplicates)
        }.isFailure)
    }

    @Test fun `missing evidence remains unavailable instead of becoming a fade`() {
        val unavailable = body.copy(
            tracks = body.tracks.map { it.copy(status = "stale", validity = "source_changed", bpm = null, confidence = null, downbeatsMs = emptyList()) },
            plans = listOf(MusicDjLocalPlanDto(0, 1, "unavailable", reason = "source_changed")),
        )
        assertFalse(localPlansBody(unavailable, request).plans.single().ready)
        rejects(unavailable.copy(plans = body.plans))
    }

    private fun rejects(value: MusicDjLocalPlansDto) = assertTrue(runCatching { localPlansBody(value, request) }.isFailure)
}
