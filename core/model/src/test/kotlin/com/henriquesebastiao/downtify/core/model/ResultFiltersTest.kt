package com.henriquesebastiao.downtify.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultFiltersTest {
    private fun song(
        id: String,
        title: String,
        artists: List<String> = listOf("Nora Vale"),
        album: String = "Harbor Nights",
        year: String = "2021",
        duration: Int = 240,
    ) = RemoteSong(
        id = id,
        title = title,
        artists = artists,
        album = album,
        coverUrl = "",
        durationSeconds = duration,
        url = "https://music.youtube.com/watch?v=$id",
        source = CatalogSource.YouTube,
        previewUrl = "",
        year = year,
        raw = "{}",
    )

    private val page = listOf(
        song("a", "Harbor Song", duration = 242),
        song(
            "b",
            "Lights Out",
            artists = listOf("Nora Vale", "Kai Sato"),
            album = "Static Bloom",
            year = "2019",
            duration = 190,
        ),
        song("c", "All The Way", artists = listOf("Kai Sato"), album = "Static Bloom", year = "2019", duration = 305),
        song("d", "Beacon", album = "Low Tide", year = "", duration = 120),
    )

    @Test
    fun `the page offers each artist, album and year once`() {
        val options = ResultFiltering.options(page)
        // Artists in reading order, a duet counted for both of them.
        assertEquals(listOf("Kai Sato", "Nora Vale"), options.artists)
        assertEquals(listOf("Harbor Nights", "Low Tide", "Static Bloom"), options.albums)
        // Newest first, the song without a year contributing nothing.
        assertEquals(listOf("2021", "2019"), options.years)
        assertFalse(options.isEmpty)
        assertTrue(ResultFiltering.options(emptyList()).isEmpty)
    }

    @Test
    fun `an artist, an album and a year narrow the page`() {
        // Ignoring case: a chip picked from another source still matches.
        val duet = ResultFiltering.apply(page, ResultFilters(artist = "kai sato"))
        assertEquals(listOf("b", "c"), duet.map { it.id })

        val bloom = ResultFiltering.apply(page, ResultFilters(album = "Static Bloom"))
        assertEquals(listOf("b", "c"), bloom.map { it.id })

        val older = ResultFiltering.apply(page, ResultFilters(year = "2019"))
        assertEquals(listOf("b", "c"), older.map { it.id })

        val both = ResultFiltering.apply(page, ResultFilters(artist = "Nora Vale", album = "Static Bloom"))
        assertEquals(listOf("b"), both.map { it.id })

        assertTrue(ResultFiltering.apply(page, ResultFilters(artist = "Nobody")).isEmpty())
        assertTrue(ResultFiltering.apply(page, ResultFilters(album = "Harbor Nights", year = "2019")).isEmpty())
    }

    @Test
    fun `relevance keeps the server's ranking, the others order the narrowed rows`() {
        val filters = ResultFilters(album = "Static Bloom")
        // The page as the server ranked it, untouched.
        assertEquals(listOf("b", "c"), ResultFiltering.apply(page, filters).map { it.id })
        assertEquals(page, ResultFiltering.apply(page, ResultFilters()))

        // Titles: "All The Way" before "Lights Out".
        assertEquals(listOf("c", "b"), ResultFiltering.apply(page, filters.copy(sort = ResultSort.Title)).map { it.id })
        // Shortest first.
        assertEquals(
            listOf("b", "c"),
            ResultFiltering.apply(page, filters.copy(sort = ResultSort.Duration)).map { it.id },
        )
        // A duet sorts by all of its artists at once; equal artists keep the page's order.
        assertEquals(
            listOf("c", "b"),
            ResultFiltering.apply(page, filters.copy(sort = ResultSort.Artist)).map { it.id },
        )

        // Sorting alone touches nothing else.
        assertEquals(
            listOf("c", "d", "a", "b"),
            ResultFiltering.apply(page, ResultFilters(sort = ResultSort.Title)).map { it.id },
        )
        assertEquals(
            listOf("c", "a", "d", "b"),
            ResultFiltering.apply(page, ResultFilters(sort = ResultSort.Artist)).map { it.id },
        )
        assertEquals(
            listOf("d", "b", "a", "c"),
            ResultFiltering.apply(page, ResultFilters(sort = ResultSort.Duration)).map { it.id },
        )
    }

    @Test
    fun `album cards follow the chips too`() {
        val albums = listOf(
            RemoteAlbum("h", "Harbor Nights", "Nora Vale", "", "2021", "", "Album"),
            RemoteAlbum("s", "Static Bloom", "Kai Sato", "", "2019", "", "Album"),
        )
        assertEquals(albums, ResultFiltering.albums(albums, ResultFilters()))
        assertEquals(listOf("s"), ResultFiltering.albums(albums, ResultFilters(artist = "kai sato")).map { it.id })
        assertEquals(listOf("h"), ResultFiltering.albums(albums, ResultFilters(album = "Harbor Nights")).map { it.id })
        assertEquals(listOf("s"), ResultFiltering.albums(albums, ResultFilters(year = "2019")).map { it.id })
        assertTrue(
            ResultFiltering.albums(albums, ResultFilters(artist = "Kai Sato", album = "Harbor Nights")).isEmpty(),
        )
    }

    @Test
    fun `clear means nothing set`() {
        assertFalse(ResultFilters().isActive)
        assertFalse(ResultFilters().hasFields)
        assertTrue(ResultFilters(sort = ResultSort.Duration).isActive)
        assertFalse(ResultFilters(sort = ResultSort.Duration).hasFields)
        assertTrue(ResultFilters(artist = "Nora Vale").hasFields)
    }
}
