package com.henriquesebastiao.downtify.core.data.offline

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import com.henriquesebastiao.downtify.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

data class OfflinePlaylist(val title: String, val key: String, val tracks: List<Pair<Track, String>>)

@Singleton
class OfflineStorage @Inject constructor(@ApplicationContext private val context: Context) {
    private var backend: OfflineBackend = FileOfflineBackend(
        defaultDirectory(context),
        onChanged = { file ->
            MediaScannerConnection.scanFile(context, arrayOf(file.path), null, null)
        },
    )
    private var treeUri: String? = null

    internal constructor(context: Context, root: File) : this(context) {
        backend = FileOfflineBackend(root)
    }

    @Synchronized
    fun part(trackId: String): OfflineTarget = backend.target(OfflinePaths.part(trackId))

    @Synchronized
    fun audio(path: String): OfflineAudio? = backend.audio(path)

    fun open(audio: OfflineAudio): InputStream = backend.open(audio)

    @Synchronized
    fun usable(path: String, bytes: Long): Boolean {
        val audio = audio(path) ?: return false
        if (audio.bytes != bytes || bytes <= 0) return false
        return try {
            val prefix = ByteArray(PREFIX_BYTES)
            val count = open(audio).use { input -> input.read(prefix) }
            count > 0 && prefix.take(count).any { it != 0.toByte() }
        } catch (_: IOException) {
            false
        }
    }

    fun fileName(track: Track): String = OfflinePaths.audio(track)

    @Synchronized
    fun commit(part: OfflineTarget, track: Track): OfflineAudio? = backend.commit(part, fileName(track))

    @Synchronized
    fun delete(path: String) = backend.delete(path)

    @Synchronized
    fun sweep(keepPaths: Set<String>) = backend.sweep(keepPaths)

    @Synchronized
    fun partialBytes(): Long = backend.list(OfflinePaths.PARTS).sumOf { it.bytes }

    @Synchronized
    fun freeBytes(): Long = backend.freeBytes()

    @Synchronized
    fun playlistPaths(): Set<String> = backend.list(OfflinePaths.PLAYLISTS).mapTo(mutableSetOf()) { it.path }

    @Synchronized
    fun syncPlaylists(playlists: List<OfflinePlaylist>) {
        val expected = mutableSetOf<String>()
        playlists.forEach { playlist ->
            val path = OfflinePaths.playlist(playlist.title, playlist.key)
            expected += path
            backend.target(path).outputStream(false).use { output ->
                output.write(PlaylistExporter.render(playlist.title, playlist.tracks).toByteArray(Charsets.UTF_8))
            }
        }
        backend.list(OfflinePaths.PLAYLISTS).filter { it.path !in expected }.forEach { backend.delete(it.path) }
    }

    fun legacyFile(fileName: String): File {
        val directory = File(context.filesDir, "offline").canonicalFile
        if ('/' in fileName || '\\' in fileName) return File(directory, ".migrated")
        return File(directory, fileName).canonicalFile.also { file ->
            require(file.parentFile == directory) { "Invalid legacy offline path" }
        }
    }

    @Synchronized
    @Suppress("TooGenericExceptionCaught")
    fun importLegacy(legacy: File, track: Track): OfflineAudio? {
        val path = fileName(track)
        audio(path)?.takeIf { it.bytes == legacy.length() }?.let {
            legacy.delete()
            return it
        }
        val target = backend.target(path)
        try {
            legacy.inputStream().use { input ->
                target.outputStream(false).use { output -> input.copyTo(output) }
            }
            val audio = backend.audio(path)
            if (audio == null || audio.bytes != legacy.length()) {
                target.delete()
                return null
            }
            return audio.takeIf { legacy.delete() }
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    @Synchronized
    fun moveTo(treeUri: String?) {
        if (this.treeUri == treeUri) return
        val destination = if (treeUri == null) {
            FileOfflineBackend(
                defaultDirectory(context),
                onChanged = { file -> MediaScannerConnection.scanFile(context, arrayOf(file.path), null, null) },
            )
        } else {
            SafOfflineBackend(context, Uri.parse(treeUri))
        }
        val source = backend
        val files = try {
            source.list()
        } catch (error: IOException) {
            if (treeUri != null) throw error
            emptyList()
        }
        val copied = copyManagedFiles(source, destination, files)
        backend = destination
        this.treeUri = treeUri
        copied.forEach { source.delete(it) }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun copyManagedFiles(
        source: OfflineBackend,
        destination: OfflineBackend,
        files: List<OfflineAudio>,
    ): List<String> {
        val copied = mutableListOf<String>()
        try {
            files.forEach { audio ->
                copyManagedFile(source, destination, audio)
                copied += audio.path
            }
        } catch (error: Exception) {
            copied.forEach { destination.delete(it) }
            throw error
        }
        return copied
    }

    private fun copyManagedFile(source: OfflineBackend, destination: OfflineBackend, audio: OfflineAudio) {
        source.open(audio).use { input ->
            destination.target(audio.path).outputStream(false).use { output -> input.copyTo(output) }
        }
        val target = destination.audio(audio.path)
        if (target == null || target.bytes != audio.bytes) throw IOException("Could not move ${audio.path}")
    }

    @Synchronized
    fun locationLabel(): String = treeUri?.let { Uri.parse(it).lastPathSegment ?: it }
        ?: "Android/media/${context.packageName}"

    companion object {
        private const val PREFIX_BYTES = 64

        fun defaultDirectory(context: Context): File = context.getExternalMediaDirs().firstOrNull()
            ?: File(context.filesDir, "offline-media")
    }
}
