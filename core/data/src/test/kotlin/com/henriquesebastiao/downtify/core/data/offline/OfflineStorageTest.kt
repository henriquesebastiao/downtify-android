package com.henriquesebastiao.downtify.core.data.offline

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.henriquesebastiao.downtify.core.model.Track
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineStorageTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `default directory is the app specific external media folder`() {
        val media = File(temporary.root, "Android/media/com.henriquesebastiao.downtify")
        val context = mockk<Context> {
            every { getExternalMediaDirs() } returns arrayOf(media)
        }

        assertEquals(media, OfflineStorage.defaultDirectory(context))
    }

    @Test
    fun `nested audio commits and resumes through streaming targets`() {
        val backend = FileOfflineBackend(temporary.root)
        val part = backend.target(OfflinePaths.part("track"))
        part.outputStream(false).use { it.write(byteArrayOf(1, 2)) }
        part.outputStream(true).use { it.write(byteArrayOf(3, 4)) }

        assertEquals(4, part.length())
        val audio = backend.commit(part, "Music/Artist/Album/song.flac")!!

        assertEquals("file", audio.uri.scheme)
        assertEquals(4, audio.bytes)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), backend.open(audio).use { it.readBytes() })
    }

    @Test
    fun `completed playlist writes are published to the media scanner`() {
        var published: File? = null
        val backend = FileOfflineBackend(temporary.root, onChanged = { published = it })

        backend.target("Playlists/Mix.m3u").outputStream(false).use { it.write(1) }

        assertEquals(File(temporary.root, "Playlists/Mix.m3u"), published)
    }

    @Test
    fun `storage health and sweep only affect managed files`() {
        val storage = OfflineStorage(RuntimeEnvironment.getApplication(), temporary.root)
        val track = track("track")
        val path = storage.fileName(track)
        val part = storage.part(track.id)
        part.outputStream(false).use { it.write(byteArrayOf(4, 3, 2, 1)) }
        storage.commit(part, track)!!
        val unrelated = File(temporary.root, "personal.txt").apply { writeText("keep") }

        assertTrue(storage.usable(path, 4))
        assertFalse(storage.usable(path, 3))
        storage.sweep(emptySet())

        assertNull(storage.audio(path))
        assertEquals("keep", unrelated.readText())
    }

    @Test
    fun `absolute and parent paths are rejected`() {
        val backend = FileOfflineBackend(temporary.root)

        assertThrows(IllegalArgumentException::class.java) { backend.target("../outside") }
        assertThrows(IllegalArgumentException::class.java) { backend.target("/absolute") }
    }

    @Test
    fun `file listing excludes unrelated content from moves`() {
        val backend = FileOfflineBackend(temporary.root)
        backend.target("Music/song.flac").outputStream(false).use { it.write(1) }
        File(temporary.root, "personal.txt").writeText("keep")

        assertEquals(listOf("Music/song.flac"), backend.list().map { it.path })
    }

    @Test
    fun `SAF document access failures become downloader IO errors`() {
        every { treeDocument.findFile("Downtify") } throws SecurityException("revoked")
        val backend = SafOfflineBackend(mockk(), treeDocument)

        assertThrows(IOException::class.java) { backend.target(TRACK_PATH).outputStream(false) }
    }

    @Test
    fun `SAF audio keeps its content uri and opens append streams`() {
        val tree = documentTree()
        val resolver = mockk<ContentResolver>()
        val context = mockk<Context> { every { contentResolver } returns resolver }
        val output = ByteArrayOutputStream()
        every { resolver.openOutputStream(trackDocument.uri, "wa") } returns output
        val backend = SafOfflineBackend(context, tree)

        val audio = backend.audio(TRACK_PATH)
        val target = backend.target(TRACK_PATH)

        assertSame(trackDocument.uri, audio?.uri)
        assertEquals(4L, audio?.bytes)
        assertSame(output, target.outputStream(true))
    }

    @Test
    fun `SAF missing audio and revoked permission are safe failures`() {
        val tree = documentTree()
        every { albumDocument.findFile("song.flac") } returns null
        every { albumDocument.createFile("audio/flac", "song.flac") } returns trackDocument
        val resolver = mockk<ContentResolver>()
        val context = mockk<Context> { every { contentResolver } returns resolver }
        every { resolver.openOutputStream(trackDocument.uri, "wt") } throws SecurityException("revoked")
        val backend = SafOfflineBackend(context, tree)

        assertNull(backend.audio(TRACK_PATH))
        assertThrows(IOException::class.java) { backend.target(TRACK_PATH).outputStream(false) }
    }

    @Test
    fun `SAF sweep preserves files outside managed folders`() {
        val tree = mockk<DocumentFile>()
        val managed = mockk<DocumentFile>()
        val personal = mockk<DocumentFile>(relaxed = true)
        every { tree.findFile("Downtify") } returns managed
        every { managed.findFile("Music") } returns null
        every { managed.findFile("Playlists") } returns null
        every { managed.findFile(".parts") } returns null
        every { managed.listFiles() } returns arrayOf(personal)
        every { personal.isFile } returns true
        val backend = SafOfflineBackend(mockk(relaxed = true), tree)

        backend.sweep(emptySet())

        verify(exactly = 0) { personal.delete() }
    }

    private val treeDocument = mockk<DocumentFile>()
    private val managedDocument = mockk<DocumentFile>()
    private val musicDocument = mockk<DocumentFile>()
    private val artistDocument = mockk<DocumentFile>()
    private val albumDocument = mockk<DocumentFile>()
    private val trackDocument = mockk<DocumentFile>()

    private fun documentTree(): DocumentFile {
        every { treeDocument.findFile("Downtify") } returns managedDocument
        every { managedDocument.findFile("Music") } returns musicDocument
        every { musicDocument.findFile("Artist") } returns artistDocument
        every { artistDocument.findFile("Album") } returns albumDocument
        every { albumDocument.findFile("song.flac") } returns trackDocument
        every { trackDocument.isFile } returns true
        every { trackDocument.uri } returns Uri.parse("content://downloads/tree/track")
        every { trackDocument.length() } returns 4
        every { trackDocument.lastModified() } returns 123
        return treeDocument
    }

    private fun track(id: String) = Track(
        id = id,
        file = "$id.flac",
        title = id,
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
        size = 100,
        added = 0,
        hasCover = false,
        playlists = emptyList(),
    )

    private companion object {
        const val TRACK_PATH = "Music/Artist/Album/song.flac"
    }
}
