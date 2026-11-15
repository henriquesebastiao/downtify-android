package com.henriquesebastiao.downtify.core.model

/** How the server's search page is ordered. [Relevance] keeps the order the server ranked it in. */
enum class ResultSort { Relevance, Title, Artist, Duration }

/**
 * What narrows the server's search page: one artist, one album and one year, each picked from the
 * page itself.
 *
 * The server's `/api/songs/search` takes a single `query` and nothing else (see
 * `search_endpoint` in the server's `api.py`), so none of this is sent to it: the page comes back
 * whole and is narrowed here, on the phone. That also bounds it — a song the server left off its
 * page can't be reached by a filter.
 */
data class ResultFilters(
    val artist: String? = null,
    val album: String? = null,
    val year: String? = null,
    val sort: ResultSort = ResultSort.Relevance,
) {
    /** Whether a field is narrowed: what the chips in the bar say. */
    val hasFields: Boolean get() = artist != null || album != null || year != null

    /** Whether anything at all is set, the order included: when "clear" has something to do. */
    val isActive: Boolean get() = hasFields || sort != ResultSort.Relevance
}

/** The values a page offers to its chips, each once; empty ones never make it in. */
data class ResultFilterOptions(
    val artists: List<String> = emptyList(),
    val albums: List<String> = emptyList(),
    val years: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = artists.isEmpty() && albums.isEmpty() && years.isEmpty()
}

/** Narrows and orders one page of the server's search results. Pure: no Android, no I/O. */
object ResultFiltering {
    /**
     * The page's artists, albums and years as chips: artists and albums in the app's reading
     * order, years newest first (anything that isn't a number after them).
     */
    fun options(songs: List<RemoteSong>): ResultFilterOptions = ResultFilterOptions(
        artists = songs.flatMap { it.artists }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER),
        albums = songs.map { it.album.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER),
        years = songs.map { it.year.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            // Newest first; a year the server wrote in some other way goes after the numbers.
            .sortedWith(compareByDescending<String> { it.toIntOrNull() ?: Int.MIN_VALUE }.thenBy { it }),
    )

    /**
     * [songs] with the fields of [filters] applied and its sort order: the rows the search shows.
     * [ResultSort.Relevance] returns them as they came, so the server's ranking survives.
     */
    fun apply(songs: List<RemoteSong>, filters: ResultFilters): List<RemoteSong> {
        val artist = filters.artist
        val album = filters.album
        val year = filters.year
        val narrowed = if (!filters.hasFields) {
            songs
        } else {
            songs.filter { matches(it, artist, album, year) }
        }
        return when (filters.sort) {
            ResultSort.Relevance -> narrowed
            ResultSort.Title -> narrowed.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            ResultSort.Artist -> narrowed.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist })
            ResultSort.Duration -> narrowed.sortedBy { it.durationSeconds }
        }
    }

    /**
     * [albums] with the fields of [filters] applied: the album cards follow the songs, so an
     * artist's chip doesn't leave another artist's album under the rows it hid. Their order stays
     * the server's — an album has no duration to sort by.
     */
    fun albums(albums: List<RemoteAlbum>, filters: ResultFilters): List<RemoteAlbum> {
        val artist = filters.artist
        val album = filters.album
        val year = filters.year
        if (!filters.hasFields) return albums
        return albums.filter {
            same(it.artist, artist) && same(it.title, album) && same(it.year, year)
        }
    }

    /** A song with every artist written its own way: a duet matches either of its two chips. */
    private fun matches(song: RemoteSong, artist: String?, album: String?, year: String?): Boolean =
        (artist == null || song.artists.any { same(it, artist) }) &&
            same(song.album, album) &&
            same(song.year, year)

    /** Whether [value] is the one picked, ignoring case and the spaces around it; null picks all. */
    private fun same(value: String, picked: String?): Boolean =
        picked == null || value.trim().equals(picked.trim(), ignoreCase = true)
}
