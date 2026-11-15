package com.henriquesebastiao.downtify.core.data.offline

import android.content.Context
import android.net.Uri
import android.os.StatFs
import androidx.documentfile.provider.DocumentFile
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal class SafOfflineBackend(private val context: Context, private val tree: DocumentFile) : OfflineBackend {
    constructor(context: Context, treeUri: Uri) : this(
        context,
        DocumentFile.fromTreeUri(context, treeUri) ?: throw IOException("Cannot open the selected folder"),
    )

    override fun target(path: String): OfflineTarget = SafTarget(pathSegments(path))

    override fun audio(path: String): OfflineAudio? = io {
        find(pathSegments(path))?.takeIf { it.isFile }?.let {
            OfflineAudio(it.uri, path, it.length(), it.lastModified())
        }
    }

    override fun commit(part: OfflineTarget, targetPath: String): OfflineAudio? {
        require(part is SafTarget)
        val target = target(targetPath)
        return try {
            target.delete()
            part.inputStream().use { input ->
                target.outputStream(false).use { output -> input.copyTo(output) }
            }
            val audio = audio(targetPath)
            if (audio == null || audio.bytes != part.length()) {
                target.delete()
                null
            } else if (part.delete()) {
                audio
            } else {
                target.delete()
                null
            }
        } catch (error: IOException) {
            target.delete()
            throw error
        }
    }

    override fun delete(path: String) {
        io { find(pathSegments(path))?.delete() }
    }

    override fun list(folder: String): List<OfflineAudio> = io {
        if (folder.isEmpty()) {
            listOf(OfflinePaths.MUSIC, OfflinePaths.PLAYLISTS, OfflinePaths.PARTS).flatMap { list(it) }
        } else {
            val root = find(pathSegments(folder))
            root?.let { listRecursive(it, folder) }.orEmpty()
        }
    }

    override fun sweep(keepPaths: Set<String>) {
        list().filter { it.path !in keepPaths }.forEach { delete(it.path) }
    }

    override fun freeBytes(): Long = StatFs(context.filesDir.path).availableBytes

    private fun managedRoot(create: Boolean): DocumentFile? {
        val existing = tree.findFile(MANAGED_FOLDER)
        return existing ?: if (create) tree.createDirectory(MANAGED_FOLDER) else null
    }

    private fun find(segments: List<String>): DocumentFile? {
        var current = managedRoot(create = false) ?: return null
        segments.forEach { segment -> current = current.findFile(segment) ?: return null }
        return current
    }

    private fun document(segments: List<String>, create: Boolean): DocumentFile? {
        var current = managedRoot(create) ?: return null
        segments.dropLast(1).forEach { segment ->
            current = current.findFile(segment) ?: if (create) {
                current.createDirectory(segment) ?: return null
            } else {
                return null
            }
        }
        val name = segments.last()
        return current.findFile(name) ?: if (create) current.createFile(mimeType(name), name) else null
    }

    private fun listRecursive(document: DocumentFile, prefix: String): List<OfflineAudio> =
        document.listFiles().flatMap { child ->
            val path = if (prefix.isEmpty()) child.name.orEmpty() else "$prefix/${child.name}"
            if (child.isDirectory) {
                listRecursive(child, path)
            } else if (child.isFile) {
                listOf(OfflineAudio(child.uri, path, child.length(), child.lastModified()))
            } else {
                emptyList()
            }
        }

    private fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "m4a", "aac" -> "audio/mp4"
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        "m3u" -> "audio/x-mpegurl"
        else -> "application/octet-stream"
    }

    private fun <T> io(block: () -> T): T = try {
        block()
    } catch (error: SecurityException) {
        throw IOException("Access to the selected folder was revoked", error)
    }

    private inner class SafTarget(private val segments: List<String>) : OfflineTarget {
        override val path: String = segments.joinToString("/")
        private val existing: DocumentFile? get() = find(segments)

        override fun length(): Long = io { existing?.length() ?: 0 }
        override fun delete(): Boolean = io { existing?.delete() ?: true }

        override fun inputStream(): InputStream = io {
            val document = existing ?: throw FileNotFoundException(path)
            context.contentResolver.openInputStream(document.uri) ?: throw FileNotFoundException(path)
        }

        override fun outputStream(append: Boolean): OutputStream = io {
            val document = document(segments, create = true) ?: throw IOException("Cannot create $path")
            if (!document.isFile) throw IOException("Not a file: $path")
            context.contentResolver.openOutputStream(document.uri, if (append) "wa" else "wt")
                ?: throw FileNotFoundException(path)
        }
    }

    private companion object {
        const val MANAGED_FOLDER = "Downtify"
    }
}
