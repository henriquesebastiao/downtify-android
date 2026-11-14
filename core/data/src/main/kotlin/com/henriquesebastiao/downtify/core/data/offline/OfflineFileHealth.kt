package com.henriquesebastiao.downtify.core.data.offline

import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/** Rejects missing, truncated and zero-filled encoded audio without decoding it. */
internal class OfflineFileHealth {
    private data class Checked(val length: Long, val modified: Long, val usable: Boolean)
    private val checked = ConcurrentHashMap<String, Checked>()

    fun usable(file: File, expectedBytes: Long? = null): Boolean = check(
        key = file.path,
        length = file.length(),
        modified = file.lastModified(),
        exists = file.isFile,
        expectedBytes = expectedBytes,
        open = { file.inputStream() },
    )

    fun usable(target: OfflineTarget, expectedBytes: Long? = null): Boolean = check(
        key = target.path,
        length = target.length(),
        modified = 0,
        exists = target.length() > 0,
        expectedBytes = expectedBytes,
        open = { target.inputStream() },
    )

    private fun check(
        key: String,
        length: Long,
        modified: Long,
        exists: Boolean,
        expectedBytes: Long?,
        open: () -> InputStream,
    ): Boolean {
        if (!exists || length <= 0) return false
        if (expectedBytes != null && length != expectedBytes) return false
        checked[key]?.takeIf { it.length == length && it.modified == modified }?.let { return it.usable }
        val usable = try {
            val prefix = ByteArray(minOf(length, PREFIX_BYTES.toLong()).toInt())
            DataInputStream(open()).use { it.readFully(prefix) }
            prefix.any { it != 0.toByte() }
        } catch (_: IOException) {
            false
        }
        checked[key] = Checked(length, modified, usable)
        return usable
    }

    private companion object {
        const val PREFIX_BYTES = 64
    }
}
