package com.henriquesebastiao.downtify.core.player

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

internal fun localDjWindowIndices(count: Int, current: Int, repeatAll: Boolean): List<Int> {
    if (current !in 0 until count) return emptyList()
    val result = mutableListOf(current)
    while (result.size < 4) {
        val next = result.last() + 1
        if (next < count) result += next else if (repeatAll && count > 1) result += 0 else break
    }
    return result
}
