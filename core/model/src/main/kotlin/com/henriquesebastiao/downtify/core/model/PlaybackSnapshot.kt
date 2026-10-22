package com.henriquesebastiao.downtify.core.model

/** Only logical media identities and display metadata; never credentials or resolved audio URLs. */
data class SavedPlaybackItem(
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0,
    val hasCover: Boolean = false,
    val queued: Boolean = false,
    val videoId: String? = null,
    val rawSong: String? = null,
    val episodeId: Long? = null,
    val showId: Long? = null,
    val episodeFile: String? = null,
    val artworkUrl: String? = null,
)

data class PlaybackSnapshot(
    val items: List<SavedPlaybackItem>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean = false,
    val shuffleOrder: List<Int> = items.indices.toList(),
    val repeatMode: Int = 0,
    val speed: Float = 1f,
    val context: PlaybackContext? = null,
) {
    /** A corrupt/older snapshot must never crash the service or replay the wrong entry. */
    fun validated(): PlaybackSnapshot? {
        if (items.isEmpty() || index !in items.indices || items.any { it.mediaId.isBlank() }) return null
        val order = shuffleOrder.takeIf { it.size == items.size && it.toSet() == items.indices.toSet() }
            ?: items.indices.toList()
        val duration = items[index].durationMs
        return copy(
            positionMs = positionMs.coerceAtLeast(0).let { if (duration > 0) it.coerceAtMost(duration) else it },
            shuffleOrder = order,
            repeatMode = repeatMode.takeIf { it in 0..2 } ?: 0,
            speed = speed.takeIf { it.isFinite() && it in 0.5f..3f } ?: 1f,
        )
    }
}
