package com.henriquesebastiao.downtify.core.player

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sin

/** Timing supplied by a verified analysis, never inferred in the audio callback. */
data class LocalDjAnalysis(val bpm: Float, val downbeatsMs: List<Long>, val confidence: Float, val revision: String)

enum class LocalDjPhase { Disabled, Waiting, Prepared, Mixing }

enum class LocalDjFallback { None, MissingAnalysis, TempoMismatch, NotBuffered, UnsupportedQueue, DecoderFailure }

data class LocalDjTransition(
    val phase: LocalDjPhase = LocalDjPhase.Disabled,
    val outgoingId: String? = null,
    val incomingId: String? = null,
    val progress: Float = 0f,
    val analysed: Boolean = false,
    val fallback: LocalDjFallback = LocalDjFallback.None,
)

internal data class LocalDjPlan(
    val startMs: Long,
    val cueMs: Long,
    val durationMs: Long,
    val speed: Float,
    val fallback: LocalDjFallback,
) {
    val analysed get() = fallback == LocalDjFallback.None

    companion object {
        fun create(durationMs: Long, outgoing: LocalDjAnalysis?, incoming: LocalDjAnalysis?): LocalDjPlan {
            val short = 1200L.coerceAtMost(durationMs / 4).coerceAtLeast(1)
            val fallback =
                LocalDjPlan((durationMs - short).coerceAtLeast(0), 0, short, 1f, LocalDjFallback.MissingAnalysis)
            if (!usable(outgoing) || !usable(incoming)) return fallback
            val first = checkNotNull(outgoing)
            val second = checkNotNull(incoming)
            val speed = first.bpm / second.bpm
            if (speed !in 0.96f..1.04f) return fallback.copy(fallback = LocalDjFallback.TempoMismatch)
            // Four beats keep overlap short when no reliable vocal-zone analysis is available.
            val length = (240_000.0 / first.bpm).roundToLong().coerceIn(1500, 4000)
            val latest = durationMs - length
            val start = first.downbeatsMs.lastOrNull { it in (latest - 4000).coerceAtLeast(0)..latest }
                ?: return fallback
            val cue = second.downbeatsMs.firstOrNull { it in 0..4000 } ?: return fallback
            return LocalDjPlan(start, cue, length, speed, LocalDjFallback.None)
        }

        private fun usable(value: LocalDjAnalysis?) = value != null && value.bpm.isFinite() &&
            value.bpm in 30f..300f && value.confidence.isFinite() && value.confidence >= 0.65f &&
            value.downbeatsMs.isNotEmpty() && value.downbeatsMs.size <= 20_000
    }
}

/** Equal-power proportions with a sum-gain ceiling: correlated full-scale inputs cannot add gain. */
internal fun localDjGains(progress: Float): Pair<Float, Float> {
    val angle = progress.coerceIn(0f, 1f) * PI / 2
    val outgoing = cos(angle).toFloat().coerceAtLeast(0f)
    val incoming = sin(angle).toFloat().coerceAtLeast(0f)
    val ceiling = max(1f, outgoing + incoming)
    return outgoing / ceiling to incoming / ceiling
}
