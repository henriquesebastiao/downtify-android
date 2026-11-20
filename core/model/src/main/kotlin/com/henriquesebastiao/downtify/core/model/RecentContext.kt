package com.henriquesebastiao.downtify.core.model

/** Something the user played from, for Home's "Jump back in". */
data class RecentContext(
    val type: PlaybackContextType,
    /**
     * Album id, artist id or playlist name; the song's id for [PlaybackContextType.Songs] (see
     * [recentFor]); blank for Liked songs.
     */
    val refId: String,
    val title: String,
    val coverTrackId: String?,
    val playedAt: Long,
) {
    val key: String get() = "${type.name}:$refId"
}

enum class PlaybackContextType { Album, Artist, Playlist, Liked, Songs }

/** Where the queue came from: shown in Now Playing ("Playing from album") and recorded for Home. */
data class PlaybackContext(val type: PlaybackContextType, val refId: String, val title: String)

/**
 * What Home remembers when a queue from this context starts at [started]: the album, artist,
 * playlist or Liked songs themselves, but for loose songs ([PlaybackContextType.Songs]: the
 * library's song list, search, Discover) the song itself, which its tile shows and plays again.
 * Remembering "Songs" alone gave one tile titled "Songs" that only led back to the library.
 */
fun PlaybackContext.recentFor(started: Track): PlaybackContext =
    if (type == PlaybackContextType.Songs) copy(refId = started.id, title = started.displayTitle) else this

/** A loose song remembered before [recentFor]: no song to show or play. */
val RecentContext.isBareSongs: Boolean
    get() = type == PlaybackContextType.Songs && refId.isBlank()
