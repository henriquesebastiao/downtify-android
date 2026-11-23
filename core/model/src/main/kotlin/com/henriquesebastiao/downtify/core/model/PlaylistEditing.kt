package com.henriquesebastiao.downtify.core.model

/**
 * What a playlist write answered: `POST /api/v1/playlists`, `POST /api/v1/playlists/rename`,
 * `POST /api/v1/playlists/tracks` and `PUT /api/v1/playlists/order` all return the playlist as the
 * caller sees it now, `track_ids` in playlist order and once per occurrence.
 */
data class PlaylistEditResult(
    val name: String = "",
    val count: Int = 0,
    val trackIds: List<String> = emptyList(),
    /** Rename only: the name the playlist had before. */
    val previousName: String = "",
    /** Track edits: what the write added, removed, and skipped because it was already there. */
    val added: List<String> = emptyList(),
    val removed: List<String> = emptyList(),
    val already: List<String> = emptyList(),
)

/**
 * The ids that can go into a playlist. Podcast episodes are media items with an id of their own
 * ([EpisodeIds.isEpisode]) and the server has no track for them, so they are dropped here rather
 * than sent and refused; blanks and repeats go too (a manual playlist may repeat a song, and
 * `allow_duplicates` asks for that explicitly).
 */
fun playlistTrackIds(ids: Iterable<String?>): List<String> = ids
    .filterNotNull()
    .filter { it.isNotBlank() && !EpisodeIds.isEpisode(it) }
    .distinct()

/**
 * The playlists a song can be added to: manual ones the caller may edit (their own, or one they are
 * an editor of). Imported playlists and read-only ones are left out: the server answers `403` for
 * those, so they are not offered.
 */
fun addablePlaylists(playlists: List<Playlist>): List<Playlist> =
    playlists.filter { it.manual && it.canEditTracks && !it.liked }

/** [this] with the item at [from] moved to [to], as a drag of the row shows it. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    val copy = toMutableList()
    copy.add(to, copy.removeAt(from))
    return copy
}
