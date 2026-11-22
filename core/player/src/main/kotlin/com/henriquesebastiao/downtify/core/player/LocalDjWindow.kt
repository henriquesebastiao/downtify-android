package com.henriquesebastiao.downtify.core.player

import com.henriquesebastiao.downtify.core.model.DjLocalProblem

/** Occurrence indices survive duplicate IDs; position ticks are deliberately not request keys. */
internal data class LocalDjWindow(
    val generation: Long,
    val revision: Long,
    val indices: List<Int>,
    val ids: List<String>,
    val shuffled: Boolean,
    val repeatOne: Boolean,
    val speed: Float,
    val pitch: Float,
)

/** Native shuffled timelines and repeat-one are not reproduced by the beta's second deck. */
internal fun localDjWindowProblem(window: LocalDjWindow, protocol: Int, sourcesPresent: Boolean): DjLocalProblem? =
    when {
        protocol != 1 -> DjLocalProblem.Unsupported
        window.shuffled || window.repeatOne || window.ids.size < 2 -> DjLocalProblem.UnsupportedQueue
        !sourcesPresent -> DjLocalProblem.MissingAnalysis
        window.speed != 1f || window.pitch != 1f -> DjLocalProblem.UnsupportedPlayback
        else -> null
    }

internal fun localDjWindowIndices(count: Int, current: Int, repeatAll: Boolean): List<Int> {
    if (current !in 0 until count) return emptyList()
    val result = mutableListOf(current)
    while (result.size < 4) {
        val next = result.last() + 1
        if (next < count) {
            result += next
        } else if (repeatAll && count > 1) {
            result += 0
        } else {
            break
        }
    }
    return result
}
