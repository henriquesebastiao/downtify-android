package com.henriquesebastiao.downtify.core.model

/** Tracks grouped by `album_id`: album artist + album title, case-insensitive. */
data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String,
    val year: String,
    val tracks: List<Track>,
    /** Unix seconds: when the newest of its tracks was added. */
    val added: Long,
) {
    val trackCount: Int get() = tracks.size
    val duration: Double get() = tracks.sumOf { it.duration }

    /** A track whose cover stands for the album, or null when none has one. */
    val coverTrackId: String? get() = tracks.firstOrNull { it.hasCover }?.id
}

/** Tracks grouped by `artist_id`: the album artist. */
data class Artist(val id: String, val name: String, val albums: List<Album>, val tracks: List<Track>) {
    val coverTrackId: String? get() = albums.firstNotNullOfOrNull { it.coverTrackId }
        ?: tracks.firstOrNull { it.hasCover }?.id
}

/** A playlist the server lists, with its tracks in playlist order. */
data class Playlist(
    val name: String,
    val liked: Boolean,
    /** Server path for `GET /playlist-cover?file=`, or blank. */
    val cover: String,
    val trackIds: List<String>,
    val favorite: Boolean = false,
    val displayName: String = "",
    val favoriteSupported: Boolean = false,
) {
    val title: String get() = displayName.ifBlank { name }
}
