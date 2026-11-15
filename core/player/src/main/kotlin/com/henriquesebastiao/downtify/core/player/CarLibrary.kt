package com.henriquesebastiao.downtify.core.player

import com.henriquesebastiao.downtify.core.data.library.LibrarySnapshot
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.Playlist
import com.henriquesebastiao.downtify.core.model.Track
import java.text.Normalizer
import java.time.LocalDate
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
        val suggestion: String? = null,
    )

    private val children = mutableMapOf<String, List<Entry>>()
    private val browseChildren = mutableMapOf<String, List<Entry>>()
    private val entries = mutableMapOf<String, Entry>()
    private val positions = mutableMapOf<String, Pair<String, Int>>()
    private val playlistsById = mutableMapOf<String, Playlist>()
    private val suggestionSources = mutableMapOf<String, String>()
    private val orderedTracks: List<Track>
    private val searchable: List<Pair<Entry, String>>

    init {
        val tracks = snapshot.tracks.sortedWith(compareBy({ normalized(it.displayTitle) }, { it.id }))
        orderedTracks = tracks
        addTracks(TRACKS, tracks)
        addTracks(FAVORITES, snapshot.tracks(likedIds))
        addTracks(OFFLINE, tracks.filter { it.id in offlineIds })
        children[PLAYLISTS] = playlists.sortedBy { normalized(it.title) }.map { playlist ->
            val id = folder("playlist", playlist.name)
            playlistsById[id] = playlist
            suggestionSources[folder("suggestion-playlist", playlist.name)] = id
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
        children.toMap().forEach { (parent, rows) -> group(parent, rows) }
    }

    /** Hierarchical ranges work with legacy clients that never request another page. */
    private fun group(parent: String, rows: List<Entry>) {
        if (rows.size <= MAX_CHILDREN) {
            browseChildren[parent] = rows
            return
        }
        var span = MAX_CHILDREN
        while ((rows.size.toLong() + span - 1) / span > MAX_CHILDREN) span *= MAX_CHILDREN
        browseChildren[parent] = rows.chunked(span).mapIndexed { index, part ->
            val id = "auto:range:${encode(parent)}:$index"
            group(id, part)
            Entry(id, "${part.first().title} \u2013 ${part.last().title}").also { entries[id] = it }
        }
    }

    fun children(parent: String): List<Entry>? = browseChildren[parent]
    fun item(id: String): Entry? = entries[id] ?: suggestion(id)

    /** Only known playable IDs from this association can become playback items. */
    fun track(id: String): Track? = entries[id]?.track ?: snapshot.byId[id]

    fun search(query: String): List<Entry> {
        val words = normalized(query).split(' ').filter(String::isNotBlank)
        if (words.isEmpty()) return emptyList()
        return searchable.filter { (_, text) -> words.all(text::contains) }.map { it.first }
    }

    /** Starting a row keeps the rest of that playlist/album in the shared player queue. */
    fun playback(id: String): Pair<List<Track>, Int>? {
        if (isSuggestion(id)) {
            val tracks = when {
                id == SUGGESTED_LIKES -> children[FAVORITES].orEmpty().mapNotNull(Entry::track)
                id in suggestionSources -> children[suggestionSources[id]].orEmpty().mapNotNull(Entry::track)
                else -> dailyDate(id)?.let(::dailyTracks).orEmpty()
            }
            return tracks.takeIf { it.isNotEmpty() }?.let { it to 0 }
        }
        val entry = entries[id]
        if (entry?.track != null) {
            val (parent, index) = positions[id] ?: return null
            return children[parent].orEmpty().mapNotNull(Entry::track) to index
        }
        return snapshot.byId[id]?.let { listOf(it) to 0 }
    }

    /** A receiver can submit just its visible page; occurrence IDs retain the full source. */
    fun playbackPage(ids: List<String>, selectedIndex: Int): Pair<List<Track>, Int>? {
        if (selectedIndex !in ids.indices) return null
        val source = ids.map { positions[it] ?: return null }
        val (parent, offset) = source.first()
        if (source.withIndex().any { (index, position) -> position != (parent to offset + index) }) return null
        return children[parent].orEmpty().mapNotNull(Entry::track) to (offset + selectedIndex)
    }

    fun recommendations(day: LocalDate, plays: Map<String, Long>): List<Entry> = buildList {
        suggestion(SUGGESTED_LIKES)?.let(::add)
        val best = playlistsById.entries.filter { !it.value.liked && children[it.key].orEmpty().isNotEmpty() }
            .sortedWith(
                compareByDescending<Map.Entry<String, Playlist>> { plays[it.value.name] ?: 0 }
                    .thenByDescending { it.value.favorite }.thenBy { normalized(it.value.title) },
            )
            .firstOrNull()
        if (best != null) {
            val id = folder("suggestion-playlist", best.value.name)
            suggestion(id)?.let {
                add(
                    it.copy(
                        suggestion = if ((plays[best.value.name] ?: 0) >
                            0
                        ) {
                            "most_played"
                        } else {
                            "playlist"
                        },
                    ),
                )
            }
        }
        suggestion(DAILY_PREFIX + day)?.let(::add)
    }

    private fun suggestion(id: String): Entry? {
        val source = suggestionSources[id]
        val playlist = playlistsById[source]
        val tracks = when {
            id == SUGGESTED_LIKES -> children[FAVORITES].orEmpty().mapNotNull(Entry::track)
            source != null -> children[source].orEmpty().mapNotNull(Entry::track)
            else -> dailyDate(id)?.let(::dailyTracks) ?: return null
        }
        if (tracks.isEmpty()) return null
        return Entry(
            id,
            playlist?.title.orEmpty(),
            coverTrackId = tracks.firstOrNull { it.hasCover }?.id,
            suggestion = if (id == SUGGESTED_LIKES) {
                "likes"
            } else if (source != null) {
                "playlist"
            } else {
                "daily"
            },
        )
    }

    private fun dailyTracks(day: LocalDate): List<Track> {
        if (orderedTracks.isEmpty()) return emptyList()
        val count = minOf(25, orderedTracks.size)
        val start = Math.floorMod(day.toEpochDay(), orderedTracks.size.toLong()).toInt()
        val step = (orderedTracks.size / count).coerceAtLeast(1)
        return List(count) { orderedTracks[(start + it * step) % orderedTracks.size] }
    }

    private fun dailyDate(id: String): LocalDate? = if (id.startsWith(DAILY_PREFIX)) {
        runCatching { LocalDate.parse(id.removePrefix(DAILY_PREFIX)) }.getOrNull()
    } else {
        null
    }

    fun isSuggestion(id: String) = id == SUGGESTED_LIKES || id in suggestionSources || dailyDate(id) != null

    fun context(id: String): PlaybackContext? {
        val source = positions[id]?.first ?: suggestionSources[id] ?: id
        playlistsById[source]?.let { return PlaybackContext(PlaybackContextType.Playlist, it.name, it.title) }
        return when {
            source == FAVORITES || id == SUGGESTED_LIKES -> PlaybackContext(PlaybackContextType.Liked, "", "")
            dailyDate(id) != null -> PlaybackContext(PlaybackContextType.Songs, id, "")
            else -> null
        }
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
        const val MAX_CHILDREN = 100
        const val ROOT = "auto:root"
        const val PLAYLISTS = "auto:playlists"
        const val FAVORITES = "auto:favorites"
        const val ARTISTS = "auto:artists"
        const val ALBUMS = "auto:albums"
        const val TRACKS = "auto:tracks"
        const val OFFLINE = "auto:offline"
        const val SUGGESTED_LIKES = "auto:suggestion:likes"
        const val DAILY_PREFIX = "auto:suggestion:daily:"
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
