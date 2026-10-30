package com.henriquesebastiao.downtify.core.player

import com.henriquesebastiao.downtify.core.data.library.LibrarySnapshot
import com.henriquesebastiao.downtify.core.model.Playlist
import com.henriquesebastiao.downtify.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarLibraryTest {
    private val first = track("a", "Été", "Angèle", "A")
    private val second = track("b", "Blanche", "Maes", "B")
    private val snapshot = LibrarySnapshot(listOf(first, second))

    private fun catalog(
        playlists: List<Playlist> = emptyList(),
        likes: List<String> = emptyList(),
        offline: Set<String> = emptySet(),
    ) = CarLibrary(snapshot, playlists, likes, offline)

    @Test fun `tracks sort by name without depending on feed order`() {
        assertEquals(listOf("b", "a"), catalog().children(CarLibrary.TRACKS)!!.map { it.track!!.id })
    }

    @Test fun `playlist order and selected position survive browsing`() {
        val index = catalog(listOf(Playlist("list:/été", false, "", listOf("a", "gone", "b"))))
        val playlist = index.children(CarLibrary.PLAYLISTS)!!.single()
        val rows = index.children(playlist.id)!!
        assertEquals(listOf("a", "b"), rows.map { it.track!!.id })
        val playback = index.playback(rows[1].id)!!
        assertEquals(listOf(first, second), playback.first)
        assertEquals(1, playback.second)
    }

    @Test fun `offline includes only completed known library IDs`() {
        val index = catalog(offline = setOf("a", "missing"))
        assertEquals(listOf("a"), index.children(CarLibrary.OFFLINE)!!.map { it.track!!.id })
    }

    @Test fun `a repeated playlist track can start at its second occurrence`() {
        val index = catalog(listOf(Playlist("repeat", false, "", listOf("a", "b", "a"))))
        val folder = index.children(CarLibrary.PLAYLISTS)!!.single()
        val rows = index.children(folder.id)!!
        assertEquals(3, rows.map { it.id }.distinct().size)
        assertEquals(2, index.playback(rows.last().id)!!.second)
        assertEquals(listOf(first, second, first), index.playback(rows.last().id)!!.first)
    }

    @Test fun `favorites preserve the native liked order and omit missing IDs`() {
        assertEquals(
            listOf("a", "b"),
            catalog(likes = listOf("a", "missing", "b"))
                .children(CarLibrary.FAVORITES)!!.map { it.track!!.id },
        )
    }

    @Test fun `search accepts accents and matches artist and album without network`() {
        assertTrue(catalog().search("angele ete").any { it.track == first })
        assertTrue(catalog().search("B").any { it.track == second })
        assertTrue(catalog().search("  ").isEmpty())
    }

    @Test fun `known plain playback IDs are valid but forged URLs and unknown IDs are not`() {
        val index = catalog()
        assertEquals(first, index.track("a"))
        assertNull(index.track("https://untrusted.test/song"))
        assertNull(index.playback("auto:track:bogus:bogus"))
        assertNull(index.playback("gone"))
    }

    @Test fun `folder identity does not depend on display order or punctuation`() {
        val a = Playlist("a:/é", false, "", listOf("a"))
        val b = Playlist("b", false, "", listOf("b"))
        val left = catalog(listOf(a, b)).children(CarLibrary.PLAYLISTS)!!.associate { it.title to it.id }
        val right = catalog(listOf(b, a)).children(CarLibrary.PLAYLISTS)!!.associate { it.title to it.id }
        assertEquals(left, right)
        assertFalse(left.getValue(a.title) == left.getValue(b.title))
    }

    @Test fun `empty library still exposes every empty category`() {
        val empty = CarLibrary(LibrarySnapshot.Empty, emptyList(), emptyList(), emptySet())
        CarLibrary.ROOTS.forEach { assertEquals(emptyList<CarLibrary.Entry>(), empty.children(it)) }
    }

    @Test fun `pagination handles invalid input large values and the end of a list`() {
        assertEquals(listOf(3, 4), CarLibrary.page(listOf(1, 2, 3, 4, 5), 1, 2))
        assertEquals(emptyList<Int>(), CarLibrary.page(listOf(1), Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(emptyList<Int>(), CarLibrary.page(listOf(1), -1, 20))
        assertEquals(emptyList<Int>(), CarLibrary.page(listOf(1), 0, 0))
    }

    @Test fun `rebuilding after deletion cannot resolve old car or playback IDs`() {
        val original = catalog().children(CarLibrary.TRACKS)!!.first { it.track == first }
        val next = CarLibrary(LibrarySnapshot(listOf(second)), emptyList(), emptyList(), emptySet())
        assertNull(next.track(original.id))
        assertNull(next.track(first.id))
    }

    private fun track(id: String, title: String, artist: String, album: String) = Track(
        id = id, file = "$id.flac", title = title, artist = artist, artists = listOf(artist), album = album,
        albumArtist = artist, albumId = album, artistId = artist, trackNumber = 1, year = "2026", duration = 120.0,
        codec = "flac", bitrate = 0, sampleRate = 44100, channels = 2, size = 100L, added = 0L,
        hasCover = true, playlists = emptyList(),
    )
}
