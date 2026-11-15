package com.henriquesebastiao.downtify.core.player

import com.henriquesebastiao.downtify.core.data.library.LibrarySnapshot
import com.henriquesebastiao.downtify.core.model.Playlist
import com.henriquesebastiao.downtify.core.model.Track
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    @Test fun `ten thousand tracks remain reachable without pagination and retain full queue order`() {
        val tracks = (0 until 10_000).map { track("id$it", "Track %05d".format(it), "Artist", "Album") }
        val index = CarLibrary(LibrarySnapshot(tracks), emptyList(), emptyList(), emptySet())
        fun walk(parent: String): List<CarLibrary.Entry> {
            val rows = checkNotNull(index.children(parent))
            assertTrue(rows.size <= CarLibrary.MAX_CHILDREN)
            return rows.flatMap { if (it.track == null) walk(it.id) else listOf(it) }
        }
        val rows = walk(CarLibrary.TRACKS)
        assertEquals(tracks.map { it.id }, rows.map { it.track!!.id })
        val playback = index.playback(rows[5_432].id)!!
        assertEquals(10_000, playback.first.size)
        assertEquals(5_432, playback.second)
        assertEquals(tracks, playback.first)
    }

    @Test fun `ten visible occurrences resolve the full 300 or 1000 item playlist`() {
        for (count in listOf(300, 1_000)) {
            val ids = List(count) { if (it % 11 == 0) "a" else "b" }
            val index = catalog(listOf(Playlist("Long", false, "", ids)))
            val playlist = index.children(CarLibrary.PLAYLISTS)!!.single()
            fun rows(parent: String): List<CarLibrary.Entry> = index.children(parent)!!.flatMap {
                if (it.track == null) rows(it.id) else listOf(it)
            }
            val page = rows(playlist.id).subList(120, 130).map { it.id }
            val playback = requireNotNull(index.playbackPage(page, 7))
            assertEquals(count, playback.first.size)
            assertEquals(ids, playback.first.map { it.id })
            assertEquals(127, playback.second)
        }
    }

    @Test fun `plain IDs noncontiguous and mixed collection selections keep their explicit queue`() {
        val index = catalog(listOf(Playlist("Long", false, "", listOf("a", "b", "a"))), likes = listOf("a", "b"))
        val playlist = index.children(CarLibrary.PLAYLISTS)!!.single()
        val rows = index.children(playlist.id)!!
        assertNull(index.playbackPage(listOf("a", "b"), 0))
        assertNull(index.playbackPage(listOf(rows[0].id, rows[2].id), 0))
        assertNull(index.playbackPage(listOf(rows[0].id, index.children(CarLibrary.FAVORITES)!![1].id), 0))
    }

    @Test fun `suggestions open complete collections and rank qualified playlist listens`() {
        val index = catalog(
            listOf(
                Playlist("New", false, "", listOf("a"), favorite = true),
                Playlist(
                    "Played",
                    false,
                    "",
                    List(300) {
                        "b"
                    },
                ),
            ),
            likes = listOf("a", "b", "a"),
        )
        val choices = index.recommendations(LocalDate.of(2026, 11, 12), mapOf("Played" to 7, "New" to 1))
        assertEquals(listOf("likes", "most_played", "daily"), choices.map { it.suggestion })
        assertEquals(3, index.playback(choices[0].id)!!.first.size)
        assertEquals("Played", choices[1].title)
        assertEquals(300, index.playback(choices[1].id)!!.first.size)
        assertEquals("Played", index.context(choices[1].id)?.refId)
    }

    @Test fun `daily selection is stable all day and renews without a catalog rescan`() {
        val tracks = List(100) { track("id$it", "Track %03d".format(it), "Artist", "Album") }
        val index = CarLibrary(LibrarySnapshot(tracks), emptyList(), emptyList(), emptySet())
        val day = LocalDate.of(2026, 11, 12)
        val first = index.recommendations(day, emptyMap()).single()
        val same = index.recommendations(day, emptyMap()).single()
        val tomorrow = index.recommendations(day.plusDays(1), emptyMap()).single()
        assertEquals(first, same)
        val queue = index.playback(first.id)!!.first
        assertEquals(25, queue.size)
        assertEquals(25, queue.map { it.id }.distinct().size)
        assertNotEquals(queue, index.playback(tomorrow.id)!!.first)
    }

    @Test fun `suggestions do not invent listening history or keep revoked collections`() {
        val index = catalog(listOf(Playlist("List", false, "", listOf("a"))))
        val choice = index.recommendations(LocalDate.of(2026, 11, 12), emptyMap()).first()
        assertEquals("playlist", choice.suggestion)
        val empty = CarLibrary(LibrarySnapshot.Empty, emptyList(), emptyList(), emptySet())
        assertTrue(empty.recommendations(LocalDate.of(2026, 11, 12), emptyMap()).isEmpty())
        assertNull(empty.playback(choice.id))
    }

    private fun track(id: String, title: String, artist: String, album: String) = Track(
        id = id, file = "$id.flac", title = title, artist = artist, artists = listOf(artist), album = album,
        albumArtist = artist, albumId = album, artistId = artist, trackNumber = 1, year = "2026", duration = 120.0,
        codec = "flac", bitrate = 0, sampleRate = 44100, channels = 2, size = 100L, added = 0L,
        hasCover = true, playlists = emptyList(),
    )
}
