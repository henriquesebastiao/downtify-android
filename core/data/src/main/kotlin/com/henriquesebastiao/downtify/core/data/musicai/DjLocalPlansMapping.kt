package com.henriquesebastiao.downtify.core.data.musicai

import com.henriquesebastiao.downtify.core.model.DjLocalAnalysis
import com.henriquesebastiao.downtify.core.model.DjLocalPlan
import com.henriquesebastiao.downtify.core.model.DjLocalPlans
import com.henriquesebastiao.downtify.core.model.DjLocalRequest
import com.henriquesebastiao.downtify.core.model.DjLocalTrack
import com.henriquesebastiao.downtify.core.model.DjLocalTrackStatus
import com.henriquesebastiao.downtify.core.network.dto.MusicDjLocalPlansDto

/** Strict bounds before evidence reaches either decoder. Never substitute a fade for a missing plan. */
internal fun localPlansBody(body: MusicDjLocalPlansDto, request: DjLocalRequest): DjLocalPlans {
    require(request.valid() && body.version == 1 && body.revision.length in 1..128)
    require(body.analysisModel.length in 1..256 && body.tracks.size == request.ids.size)
    val tracks = body.tracks.mapIndexed { index, row ->
        require(row.index == index && row.id == request.ids[index])
        val status = when (row.status) {
            "ready" -> DjLocalTrackStatus.Ready
            "missing" -> DjLocalTrackStatus.Missing
            "stale" -> DjLocalTrackStatus.Stale
            "unreliable" -> DjLocalTrackStatus.Unreliable
            else -> throw IllegalArgumentException("Unknown DJ analysis status")
        }
        val analysis = if (status == DjLocalTrackStatus.Ready) {
            require(row.validity == "source_verified")
            val bpm = requireNotNull(row.bpm)
            val confidence = requireNotNull(row.confidence)
            val beats = row.downbeatsMs
            require(bpm.isFinite() && bpm in 30f..300f && confidence.isFinite() && confidence in .75f..1f)
            require(row.revision.length in 1..128 && beats.size in 1..20_000)
            require(beats.all { it in 0L..86_400_000L } && beats.zipWithNext().all { (a, b) -> a < b })
            DjLocalAnalysis(bpm, confidence, beats.toList(), row.revision)
        } else {
            require(row.bpm == null && row.confidence == null && row.downbeatsMs.isEmpty())
            null
        }
        DjLocalTrack(index, row.id, status, analysis)
    }
    // Duplicate occurrences must describe the same source, not conflicting cached evidence.
    require(tracks.groupBy { it.id }.values.all { values -> values.map { it.analysis to it.status }.distinct().size == 1 })
    require(body.plans.size == request.ids.size - 1)
    val plans = body.plans.mapIndexed { index, plan ->
        require(plan.fromIndex == index && plan.toIndex == index + 1 && plan.reason.length <= 128)
        require(plan.speed.isFinite() && plan.speed in .96f..1.04f)
        val ready = when (plan.state) {
            "ready" -> true
            "unavailable" -> false
            else -> throw IllegalArgumentException("Unknown DJ plan status")
        }
        if (ready) {
            require(plan.aligned && tracks[index].analysis != null && tracks[index + 1].analysis != null)
            require(plan.outgoingStartMs in 0L..86_400_000L && plan.incomingCueMs in 0L..86_400_000L)
            require(plan.durationMs in 1L..request.transitionMs)
            require(plan.sourceEndMs == null || plan.sourceEndMs == plan.outgoingStartMs + plan.durationMs)
        } else {
            require(!plan.aligned)
        }
        DjLocalPlan(index, index + 1, ready, plan.outgoingStartMs, plan.incomingCueMs,
            plan.durationMs, plan.speed, plan.reason)
    }
    return DjLocalPlans(body.revision, body.analysisModel, tracks, plans)
}
