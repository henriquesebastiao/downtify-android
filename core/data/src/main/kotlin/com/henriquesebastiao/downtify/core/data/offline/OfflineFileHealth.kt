package com.henriquesebastiao.downtify.core.data.offline

import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Rejects missing, truncated and zero-filled encoded audio without decoding it. */
internal class OfflineFileHealth {
    private data class Checked(val length: Long, val modified: Long, val usable: Boolean)
    private val checked = ConcurrentHashMap<String, Checked>()

    fun usable(file: File, expectedBytes: Long? = null): Boolean {
        val length = file.length()
        if (!file.isFile || length <= 0) return false
        if (expectedBytes != null && length != expectedBytes) return false
        val modified = file.lastModified()
        checked[file.path]?.takeIf { it.length == length && it.modified == modified }?.let { return it.usable }
        val usable = try {
            val prefix = ByteArray(minOf(length, PREFIX_BYTES.toLong()).toInt())
            DataInputStream(file.inputStream()).use { it.readFully(prefix) }
            prefix.any { it != 0.toByte() }
        } catch (_: IOException) {
            false
        }
        checked[file.path] = Checked(length, modified, usable)
        return usable
    }

    private companion object {
        const val PREFIX_BYTES = 64
    }
}
