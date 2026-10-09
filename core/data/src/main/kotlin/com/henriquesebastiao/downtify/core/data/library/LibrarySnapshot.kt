package com.henriquesebastiao.downtify.core.data.library

import com.henriquesebastiao.downtify.core.model.Album
import com.henriquesebastiao.downtify.core.model.Artist
import com.henriquesebastiao.downtify.core.model.LibraryGrouping
import com.henriquesebastiao.downtify.core.model.RemoteSong
import com.henriquesebastiao.downtify.core.model.Track

/** The whole synced library, grouped once per change and shared by every screen. */
class LibrarySnapshot(val tracks: List<Track>) {
    val byId: Map<String, Track> = tracks.associateBy { it.id }
    val albums: List<Album> = LibraryGrouping.albums(tracks)
    val artists: List<Artist> = LibraryGrouping.artists(tracks)
    val albumsById: Map<String, Album> = albums.associateBy { it.id }
    val artistsById: Map<String, Artist> = artists.associateBy { it.id }

    fun tracks(ids: List<String>): List<Track> = ids.mapNotNull(byId::get)

    /**
     * The library track behind a downloaded server song, matched by title
     * and artist. Files carry no id to compare against, so this is
     * best-effort: null when the sync hasn't caught up yet.
     */
    fun findSong(song: RemoteSong): Track? = findTrack(song.title, song.artist)

    /** Same match by plain title and artist (a playing stream, a queue row). */
    fun findTrack(title: String, artist: String): Track? {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return null
        val cleanArtist = artist.trim()
        return tracks.firstOrNull { track ->
            track.displayTitle.equals(cleanTitle, ignoreCase = true) &&
                (cleanArtist.isEmpty() || track.displayArtist.equals(cleanArtist, ignoreCase = true))
        }
    }

    val isEmpty: Boolean get() = tracks.isEmpty()

    companion object {
        val Empty = LibrarySnapshot(emptyList())
    }
}
