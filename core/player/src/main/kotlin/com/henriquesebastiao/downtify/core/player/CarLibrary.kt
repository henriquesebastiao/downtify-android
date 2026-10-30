package com.henriquesebastiao.downtify.core.player

import com.henriquesebastiao.downtify.core.data.library.LibrarySnapshot
import com.henriquesebastiao.downtify.core.model.Playlist
import com.henriquesebastiao.downtify.core.model.Track
import java.text.Normalizer
import java.util.Base64
import java.util.Locale

/** Immutable index rebuilt on local library changes, never on each car browse request. */
internal class CarLibrary(
    val snapshot: LibrarySnapshot,
    playlists: List<Playlist>,
    likedIds: List<String>,
    offlineIds: Set<String>,
) {
    data class Entry(
        val id: String,
        val title: String,
        val subtitle: String = "",
        val track: Track? = null,
        val coverTrackId: String? = null,
    )

    private val children = mutableMapOf<String, List<Entry>>()
    private val entries = mutableMapOf<String, Entry>()
    private val positions = mutableMapOf<String, Pair<String, Int>>()
    private val searchable: List<Pair<Entry, String>>

    init {
        val tracks = snapshot.tracks.sortedWith(compareBy({ normalized(it.displayTitle) }, { it.id }))
        addTracks(TRACKS, tracks)
        addTracks(FAVORITES, snapshot.tracks(likedIds))
        addTracks(OFFLINE, tracks.filter { it.id in offlineIds })
        children[PLAYLISTS] = playlists.sortedBy { normalized(it.title) }.map { playlist ->
            val id = folder("playlist", playlist.name)
            addTracks(id, snapshot.tracks(playlist.trackIds))
            Entry(id, playlist.title, coverTrackId = snapshot.tracks(playlist.trackIds).firstOrNull { it.hasCover }?.id)
        }
        children[ALBUMS] = snapshot.albums.sortedWith(compareBy({ normalized(it.title) }, { it.id })).map { album ->
            val id = folder("album", album.id)
            addTracks(id, album.tracks)
            Entry(id, album.title, album.artist, coverTrackId = album.coverTrackId)
        }
        children[ARTISTS] = snapshot.artists.sortedWith(compareBy({ normalized(it.name) }, { it.id })).map { artist ->
            val id = folder("artist", artist.id)
            children[id] = artist.albums.sortedWith(compareBy({ normalized(it.title) }, { it.id })).map { album ->
                Entry(folder("album", album.id), album.title, album.artist, coverTrackId = album.coverTrackId)
            }
            Entry(id, artist.name, coverTrackId = artist.coverTrackId)
        }
        children.values.flatten().forEach { entries[it.id] = it }
        searchable = (children[ARTISTS].orEmpty() + children[ALBUMS].orEmpty() + children[TRACKS].orEmpty()).map {
            it to normalized("${it.title} ${it.subtitle} ${it.track?.album.orEmpty()}")
        }
    }

    fun children(parent: String): List<Entry>? = children[parent]
    fun item(id: String): Entry? = entries[id]

    /** Only known playable IDs from this association can become playback items. */
    fun track(id: String): Track? = entries[id]?.track ?: snapshot.byId[id]

    fun search(query: String): List<Entry> {
        val words = normalized(query).split(' ').filter(String::isNotBlank)
        if (words.isEmpty()) return emptyList()
        return searchable.filter { (_, text) -> words.all(text::contains) }.map { it.first }
    }

    /** Starting a row keeps the rest of that playlist/album in the shared player queue. */
    fun playback(id: String): Pair<List<Track>, Int>? {
        val entry = entries[id]
        if (entry?.track != null) {
            val (parent, index) = positions[id] ?: return null
            return children[parent].orEmpty().mapNotNull(Entry::track) to index
        }
        return snapshot.byId[id]?.let { listOf(it) to 0 }
    }

    private fun addTracks(parent: String, tracks: List<Track>) {
        children[parent] = tracks.mapIndexed { position, track ->
            val id = "auto:track:${encode(parent)}:${encode(track.id)}:$position"
            positions[id] = parent to position
            Entry(
                id,
                track.displayTitle,
                track.displayArtist,
                track,
                track.id.takeIf { track.hasCover },
            )
        }
    }

    companion object {
        const val ROOT = "auto:root"
        const val PLAYLISTS = "auto:playlists"
        const val FAVORITES = "auto:favorites"
        const val ARTISTS = "auto:artists"
        const val ALBUMS = "auto:albums"
        const val TRACKS = "auto:tracks"
        const val OFFLINE = "auto:offline"
        val ROOTS = listOf(PLAYLISTS, FAVORITES, ARTISTS, ALBUMS, TRACKS, OFFLINE)
        private val MARKS = Regex("\\p{M}+")

        fun <T> page(items: List<T>, page: Int, size: Int): List<T> {
            if (page < 0 || size <= 0) return emptyList()
            val start = page.toLong() * size
            if (start >= items.size) return emptyList()
            return items.subList(start.toInt(), minOf(items.size.toLong(), start + size).toInt())
        }

        private fun folder(type: String, key: String): String = "auto:$type:${encode(key)}"
        private fun encode(value: String): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(Charsets.UTF_8))
        private fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(MARKS, "").lowercase(Locale.ROOT).trim()
    }
}
