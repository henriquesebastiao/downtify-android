package com.henriquesebastiao.downtify.core.model

/**
 * Where a song sent to the server's music trash is. The server moves the files in the background,
 * so a confirmed song is [Pending] (already out of the library) until the move is done.
 */
enum class TrashState(val wire: String) {
    Pending("pending"),
    Moving("moving"),
    Trashed("trashed"),
    RestorePending("restore_pending"),
    Restoring("restoring"),
    Restored("restored"),
    Purging("purging"),
    Purged("purged"),

    /** Stopped by a problem (see [TrashItem.error]); nothing was deleted. */
    Error("error"),

    /** A state this version of the app doesn't know. */
    Unknown(""),
    ;

    /** Still on its way somewhere: worth asking the server again soon. */
    val inFlight: Boolean get() = this in IN_FLIGHT

    companion object {
        private val IN_FLIGHT = setOf(Pending, Moving, RestorePending, Restoring, Purging)

        fun of(wire: String): TrashState = entries.firstOrNull { it != Unknown && it.wire == wire } ?: Unknown
    }
}

/** One song in the music trash (one operation on the server). */
data class TrashItem(
    val id: String,
    /** The library path it had, and gets back. */
    val file: String,
    val trackId: String = "",
    val title: String = "",
    val artist: String = "",
    val codec: String = "",
    val state: TrashState = TrashState.Unknown,
    /** Bytes of the audio and the files that move with it. */
    val size: Long = 0,
    /** When it is deleted for good (epoch millis); null until it has moved. */
    val expiresAt: Long? = null,
    /** The server's code when [state] is [TrashState.Error]. */
    val error: String = "",
) {
    val displayTitle: String get() = title.ifBlank { file.substringAfterLast('/') }

    /** "FLAC", "MP3"…: the format, from the tags or else the file's extension. */
    val format: String get() = codec.ifBlank { file.substringAfterLast('.', "") }.uppercase()

    /** Whether it can still come back: kept, waiting to move or stopped, and not expired. */
    fun restorable(nowMillis: Long): Boolean = state in RESTORABLE && (expiresAt == null || expiresAt > nowMillis)

    private companion object {
        val RESTORABLE = setOf(TrashState.Trashed, TrashState.Pending, TrashState.Error)
    }
}

/** What the server checked for a move, to show before it is confirmed. */
data class TrashPreview(val confirmation: String, val items: List<TrashItem>)

/** A page of the trash; [total] and [bytes] cover every match, not only this page. */
data class TrashPage(val items: List<TrashItem>, val total: Int, val bytes: Long)

/** Whether the paired server has the trash on, and whether this pairing may use it. */
data class TrashCapability(val enabled: Boolean = false, val canManage: Boolean = false, val retentionDays: Int = 30)
