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
    /**
     * A playlist made in the library's own list. Only these can be edited by track ID: the write
     * routes refuse an imported playlist with `403`.
     */
    val manual: Boolean = false,
    /**
     * The caller may add, remove and reorder its songs: it is theirs, or they are one of its
     * editors. An editor may change songs but never rename or share it.
     */
    val canEditTracks: Boolean = false,
    /** The caller owns it (only then may it be renamed). */
    val mine: Boolean = false,
    /** The owner's username; blank on the caller's own playlists. */
    val owner: String = "",
) {
    val title: String get() = displayName.ifBlank { name }

    /** Whether its songs may be edited here and now: [serverSupportsEditing] is the capability flag. */
    fun isEditable(serverSupportsEditing: Boolean): Boolean = serverSupportsEditing && manual && canEditTracks

    /** Whether it may be renamed here: its owner, on a server that edits playlists, never an editor. */
    fun isRenamable(serverSupportsEditing: Boolean): Boolean =
        serverSupportsEditing && manual && mine && canEditTracks
}
