package com.henriquesebastiao.downtify.core.data.offline

import android.net.Uri
import android.os.StatFs
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream

internal class FileOfflineBackend(private val root: File, private val onChanged: (File) -> Unit = {}) :
    OfflineBackend {
    override fun target(path: String): OfflineTarget = FileTarget(safeFile(path))

    override fun audio(path: String): OfflineAudio? = safeFile(path).takeIf { it.isFile }?.toAudio(path)

    override fun commit(part: OfflineTarget, targetPath: String): OfflineAudio? {
        require(part is FileTarget && part.file.canonicalPath.startsWith(root.canonicalPath + File.separator))
        val target = safeFile(targetPath)
        target.parentFile?.mkdirs()
        target.delete()
        if (!part.file.renameTo(target)) {
            part.inputStream().use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            if (!part.file.delete()) {
                target.delete()
                return null
            }
        }
        onChanged(target)
        return target.toAudio(targetPath)
    }

    override fun delete(path: String) {
        val file = safeFile(path)
        file.delete()
        onChanged(file)
        removeEmptyParents(file.parentFile)
    }

    override fun list(folder: String): List<OfflineAudio> {
        if (folder.isEmpty()) return MANAGED_FOLDERS.flatMap { list(it) }
        val start = safeFile(folder)
        return start.walkBottomUp().filter { it.isFile }.map { file ->
            file.toAudio(file.relativeTo(root).invariantSeparatorsPath)
        }.toList()
    }

    override fun sweep(keepPaths: Set<String>) {
        MANAGED_FOLDERS.forEach { folder ->
            val base = safeFile(folder)
            base.walkBottomUp().filter { it.isFile && relativePath(it) !in keepPaths }.forEach { file ->
                file.delete()
                onChanged(file)
            }
            removeEmptyParents(base)
        }
    }

    override fun freeBytes(): Long = StatFs(root.apply { mkdirs() }.path).availableBytes

    private fun safeFile(path: String): File {
        val segments = pathSegments(path)
        val base = root.canonicalFile.apply { mkdirs() }
        val target = segments.fold(base) { parent, segment -> File(parent, segment) }.canonicalFile
        require(target.path.startsWith(base.path + File.separator)) { "Path traversal attempt: $path" }
        return target
    }

    private fun relativePath(file: File): String = file.relativeTo(root).invariantSeparatorsPath

    private fun File.toAudio(path: String) = OfflineAudio(Uri.fromFile(this), path, length(), lastModified())

    private fun removeEmptyParents(start: File?) {
        var current = start
        while (current != null && current.canonicalFile != root.canonicalFile) {
            if (current.list()?.isNotEmpty() != false || !current.delete()) break
            current = current.parentFile
        }
    }

    private inner class FileTarget(val file: File) : OfflineTarget {
        override val path: String = relativePath(file)
        override fun length(): Long = file.length()
        override fun delete(): Boolean = !file.exists() || file.delete()
        override fun inputStream(): InputStream = FileInputStream(file)
        override fun outputStream(append: Boolean): OutputStream {
            file.parentFile?.mkdirs()
            return object : FilterOutputStream(FileOutputStream(file, append)) {
                override fun close() {
                    super.close()
                    if (!path.startsWith("${OfflinePaths.PARTS}/")) onChanged(file)
                }
            }
        }
    }

    private companion object {
        val MANAGED_FOLDERS = listOf(OfflinePaths.MUSIC, OfflinePaths.PLAYLISTS, OfflinePaths.PARTS)
    }
}
