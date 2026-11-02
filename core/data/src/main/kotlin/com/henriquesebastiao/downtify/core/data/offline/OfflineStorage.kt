package com.henriquesebastiao.downtify.core.data.offline

import android.content.Context
import android.os.StatFs
import com.henriquesebastiao.downtify.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The offline copies on disk: `files/offline/<track id>.<ext>`, with a
 * `<track id>.part` beside it while one downloads. App-private, not backed up.
 */
@Singleton
class OfflineStorage @Inject constructor(@ApplicationContext context: Context) {
    private val dir = File(context.filesDir, "offline")
    private val health = OfflineFileHealth()

    fun file(fileName: String): File = File(dir, fileName)

    fun usable(fileName: String, bytes: Long): Boolean = health.usable(file(fileName), bytes)

    fun partFile(trackId: String): File = File(ensureDir(), "$trackId$PART")

    /** The name a complete copy of [track] gets: its id, and the extension of the server's file. */
    fun fileName(track: Track): String {
        val ext = track.file.substringAfterLast('.', "").lowercase()
        return track.id + "." + ext.takeIf { it.isNotEmpty() && it.length <= MAX_EXT && it.all(Char::isLetterOrDigit) }
            .orEmpty().ifEmpty { "audio" }
    }

    /** Moves a finished [part] into place as [fileName]. */
    fun commit(part: File, fileName: String): File? {
        val target = file(fileName)
        target.delete()
        return target.takeIf { part.renameTo(it) }
    }

    fun delete(fileName: String) {
        file(fileName).delete()
    }

    /** Deletes whatever the folder holds besides [keep] (names) — leftovers of removed or failed downloads. */
    fun sweep(keep: Set<String>) {
        dir.listFiles()?.forEach { if (it.name !in keep) it.delete() }
    }

    /** Bytes held by partial downloads. */
    fun partialBytes(): Long = dir.listFiles()?.filter { it.name.endsWith(PART) }?.sumOf { it.length() } ?: 0

    /** Space left on the phone's internal storage. */
    fun freeBytes(): Long = StatFs(ensureDir().path).availableBytes

    private fun ensureDir(): File = dir.apply { mkdirs() }

    companion object {
        const val PART = ".part"
        private const val MAX_EXT = 5
    }
}
