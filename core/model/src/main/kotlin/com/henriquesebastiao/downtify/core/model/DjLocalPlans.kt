package com.henriquesebastiao.downtify.core.model

/** Cached server evidence only. No model is downloaded or executed by this client. */
data class DjLocalAnalysis(val bpm: Float, val confidence: Float, val downbeatsMs: List<Long>, val revision: String)

enum class DjLocalTrackStatus { Ready, Missing, Stale, Unreliable }

data class DjLocalTrack(val index: Int, val id: String, val status: DjLocalTrackStatus, val analysis: DjLocalAnalysis?)

data class DjLocalPlan(
    val fromIndex: Int,
    val toIndex: Int,
    val ready: Boolean,
    val outgoingStartMs: Long,
    val incomingCueMs: Long,
    val durationMs: Long,
    val speed: Float,
    val reason: String,
)

data class DjLocalPlans(val revision: String, val analysisModel: String, val tracks: List<DjLocalTrack>, val plans: List<DjLocalPlan>)

enum class DjLocalProblem { Unsupported, Disabled, Network, MissingAnalysis, StaleAnalysis, UnreliableAnalysis, InvalidResponse, UnsupportedPlayback, UnsupportedQueue }

sealed interface DjLocalResult {
    data class Available(val value: DjLocalPlans) : DjLocalResult
    data class Unavailable(val problem: DjLocalProblem) : DjLocalResult
}

/** A bounded request identity. The client revision changes on queue/source/explicit refresh. */
data class DjLocalRequest(val ids: List<String>, val transitionMs: Long, val clientRevision: Long) {
    fun valid() = ids.size in 2..4 && ids.all { it.length in 1..80 } && transitionMs in 1_000L..12_000L
}

/** One account/window only; an old completion cannot populate a replacement generation. */
class DjLocalPlanCache<Owner>(private val clock: () -> Long, private val ttlMs: Long = 30_000) {
    data class Ticket<Owner>(val generation: Long, val owner: Owner, val request: DjLocalRequest)
    private var generation = 0L
    private var ticket: Ticket<Owner>? = null
    private var cached: DjLocalResult? = null
    private var at = 0L

    fun begin(owner: Owner, request: DjLocalRequest): Ticket<Owner> {
        require(request.valid()) { "Invalid bounded DJ request" }
        val old = ticket
        if (old != null && old.owner == owner && old.request == request) return old
        return Ticket(++generation, owner, request.copy(ids = request.ids.toList())).also {
            ticket = it
            cached = null
        }
    }

    fun cached(expected: Ticket<Owner>): DjLocalResult? = cached.takeIf {
        expected == ticket && clock() - at in 0L until ttlMs
    }

    fun complete(expected: Ticket<Owner>, value: DjLocalResult): Boolean {
        if (expected != ticket) return false
        cached = value
        at = clock()
        return true
    }

    fun clear() {
        generation++
        ticket = null
        cached = null
    }
}
