package com.henriquesebastiao.downtify.core.data.offline

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.henriquesebastiao.downtify.core.model.Track
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineStorageMigrationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `legacy id named audio is imported to its readable path`() {
        val context = mockk<Context> {
            every { filesDir } returns temporary.root
            every { getExternalMediaDirs() } returns arrayOf()
        }
        val legacy = File(context.filesDir, "offline/old-id.flac").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val storage = OfflineStorage(context, File(temporary.root, "media"))
        val track = track("old-id")

        val audio = storage.importLegacy(legacy, track)!!

        assertEquals(OfflinePaths.audio(track), audio.path)
        assertEquals(4, audio.bytes)
        assertFalse(legacy.exists())
        assertTrue(storage.usable(audio.path, 4))
    }

    @Test
    fun `playlist export refreshes relative links and removes stale playlists`() {
        val context = mockk<Context> {
            every { filesDir } returns temporary.root
            every { getExternalMediaDirs() } returns arrayOf()
        }
        val storage = OfflineStorage(context, File(temporary.root, "media"))
        val track = track("track")
        val part = storage.part(track.id)
        part.outputStream(false).use { it.write(byteArrayOf(1, 2, 3, 4)) }
        val audio = storage.commit(part, track)!!
        val stale = storage.playlistPaths()
        storage.syncPlaylists(
            listOf(OfflinePlaylist("Mix", "playlist-one", listOf(track to audio.path))),
        )

        val path = OfflinePaths.playlist("Mix", "playlist-one")
        val rendered = storage.open(storage.audio(path)!!).bufferedReader().use { it.readText() }
        assertTrue(rendered.contains("../${audio.path}"))
        assertEquals(setOf(path), storage.playlistPaths() - stale)

        storage.syncPlaylists(emptyList())

        assertNull(storage.audio(path))
        assertTrue(storage.usable(audio.path, 4))
    }

    @Test
    fun `migrated readable paths are ignored by legacy lookup`() {
        val context = mockk<Context> {
            every { filesDir } returns temporary.root
            every { getExternalMediaDirs() } returns arrayOf()
        }
        val storage = OfflineStorage(context, File(temporary.root, "media"))

        val legacy = storage.legacyFile(OfflinePaths.audio(track("migrated")))

        assertFalse(legacy.exists())
    }

    @Test
    fun `default location remains recoverable after a SAF grant is revoked`() {
        mockkStatic(DocumentFile::class)
        try {
            val defaultRoot = File(temporary.root, "default")
            val tree = mockk<DocumentFile>()
            val treeUri = Uri.parse("content://music/tree/revoked")
            val context = mockk<Context> {
                every { filesDir } returns temporary.root
                every { getExternalMediaDirs() } returns arrayOf(defaultRoot)
                every { packageName } returns "com.henriquesebastiao.downtify"
            }
            every { DocumentFile.fromTreeUri(context, treeUri) } returns tree
            val storage = OfflineStorage(context, File(temporary.root, "initial"))
            storage.moveTo(treeUri.toString())
            every { tree.findFile("Downtify") } throws SecurityException("revoked")

            storage.moveTo(null)

            assertEquals("Android/media/com.henriquesebastiao.downtify", storage.locationLabel())
        } finally {
            unmockkAll()
        }
    }

    private fun track(id: String) = Track(
        id = id,
        file = "$id.flac",
        title = "Song",
        artist = "Artist",
        artists = listOf("Artist"),
        album = "Album",
        albumArtist = "Artist",
        albumId = "album",
        artistId = "artist",
        trackNumber = 1,
        year = "2026",
        duration = 10.0,
        codec = "flac",
        bitrate = 1_000_000,
        sampleRate = 44_100,
        channels = 2,
        size = 4,
        added = 0,
        hasCover = false,
        playlists = emptyList(),
    )
}
