package com.henriquesebastiao.downtify.core.model

/** Temporary streaming storage, deliberately separate from collections kept offline. */
data class ReadAheadPolicy(val tracks: Int = 0, val cacheMb: Int = 100, val mobile: Boolean = false) {
    fun normalized(): ReadAheadPolicy = copy(
        tracks = tracks.coerceIn(0, 3),
        cacheMb = ((cacheMb.coerceIn(10, 200) + 5) / 10) * 10,
    )

    val cacheBytes: Long get() = normalized().cacheMb * 1_000_000L
}
