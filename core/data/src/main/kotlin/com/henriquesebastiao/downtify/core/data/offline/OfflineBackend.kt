package com.henriquesebastiao.downtify.core.data.offline

import android.net.Uri
import java.io.InputStream
import java.io.OutputStream

data class OfflineAudio(val uri: Uri, val path: String, val bytes: Long, val lastModified: Long)

interface OfflineTarget {
    val path: String

    fun length(): Long
    fun delete(): Boolean
    fun inputStream(): InputStream
    fun outputStream(append: Boolean): OutputStream
}

internal interface OfflineBackend {
    fun target(path: String): OfflineTarget
    fun audio(path: String): OfflineAudio?
    fun open(audio: OfflineAudio): InputStream = target(audio.path).inputStream()
    fun commit(part: OfflineTarget, targetPath: String): OfflineAudio?
    fun delete(path: String)
    fun list(folder: String = ""): List<OfflineAudio>
    fun sweep(keepPaths: Set<String>)
    fun freeBytes(): Long

    fun pathSegments(path: String): List<String> {
        require(path.isNotBlank() && !path.startsWith('/') && !path.startsWith('\\') && '\\' !in path) {
            "Invalid offline path: $path"
        }
        return path.split('/').also { segments ->
            require(segments.all { it.isNotBlank() && it != "." && it != ".." }) { "Invalid offline path: $path" }
        }
    }
}
