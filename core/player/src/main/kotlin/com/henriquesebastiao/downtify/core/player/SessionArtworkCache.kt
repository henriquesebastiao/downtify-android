package com.henriquesebastiao.downtify.core.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.henriquesebastiao.downtify.core.data.di.ApplicationScope
import com.henriquesebastiao.downtify.core.network.di.StreamingClient
import com.henriquesebastiao.downtify.core.network.session.Session
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Local, bounded artwork for the media session; HTTP credentials never leave this process. */
@Singleton
class SessionArtworkCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessions: SessionStore,
    @StreamingClient client: OkHttpClient,
    @ApplicationScope scope: CoroutineScope,
) {
    private val root = File(context.cacheDir, "session-artwork")
    private val lock = Mutex()
    private val http = client.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()

    init {
        scope.launch(Dispatchers.IO) {
            sessions.ensureLoaded()
            sessions.session.map { it?.artworkOwner }.distinctUntilChanged().collect { owner ->
                lock.withLock {
                    root.listFiles()?.filter {
                        it.name != owner && HEX.matches(it.name)
                    }?.forEach(File::deleteRecursively)
                }
            }
        }
    }

    suspend fun prepare(mediaId: String, source: Uri, allowNetwork: Boolean = true): Uri? =
        withContext(Dispatchers.IO) {
            val owner = sessions.current?.artworkOwner ?: return@withContext null
            if (source.scheme !in setOf("http", "https")) return@withContext null
            lock.withLock {
                if (sessions.current?.artworkOwner != owner) return@withLock null
                val key = digest("$mediaId\u0000$source")
                val file = File(File(root, owner), "$key.jpg")
                try {
                    if (!file.isFile && (!allowNetwork || !save(source, owner, file))) return@withLock null
                    file.setLastModified(System.currentTimeMillis())
                    trim(file)
                    Uri.Builder().scheme("content").authority("${context.packageName}.artwork")
                        .appendPath(owner).appendPath(key).build()
                } catch (_: IOException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
        }

    /** Cache-only lookup never waits for a network fetch or the writer mutex. */
    suspend fun cached(mediaId: String, source: Uri): Uri? = withContext(Dispatchers.IO) {
        val owner = sessions.current?.artworkOwner ?: return@withContext null
        val key = digest("$mediaId\u0000$source")
        val file = File(File(root, owner), "$key.jpg")
        if (!file.isFile || sessions.current?.artworkOwner != owner) return@withContext null
        Uri.Builder().scheme("content").authority("${context.packageName}.artwork")
            .appendPath(owner).appendPath(key).build()
    }

    /** Resolves only previously prepared images for the current association, never a URL or arbitrary path. */
    fun open(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r" || uri.scheme != "content" ||
            uri.authority != "${context.packageName}.artwork"
        ) {
            throw FileNotFoundException()
        }
        val parts = uri.pathSegments
        if (parts.size != 2 || parts.any { !HEX.matches(it) } ||
            parts[0] != sessions.current?.artworkOwner
        ) {
            throw FileNotFoundException()
        }
        val file = File(File(root, parts[0]), "${parts[1]}.jpg")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun save(source: Uri, owner: String, file: File): Boolean {
        val bitmap = download(source) ?: return false
        try {
            if (sessions.current?.artworkOwner != owner) return false
            val parent = file.parentFile ?: return false
            if (!parent.isDirectory && !parent.mkdirs()) return false
            publish(bitmap, file, parent)
            return true
        } finally {
            bitmap.recycle()
        }
    }

    private fun publish(bitmap: Bitmap, file: File, parent: File) {
        val temporary = File.createTempFile("cover-", ".part", parent)
        try {
            temporary.outputStream().use {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)) throw IOException("Invalid artwork")
            }
            if (!temporary.renameTo(file)) throw IOException("Could not publish artwork")
        } finally {
            temporary.delete()
        }
    }

    private fun fetch(source: Uri): ByteArray? =
        http.newCall(Request.Builder().url(source.toString()).build()).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body
            if (body.contentLength() > MAX_INPUT_BYTES) return@use null
            body.byteStream().use(::readLimited)
        }

    private fun readLimited(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_INPUT_BYTES) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun download(source: Uri): Bitmap? {
        val bytes = fetch(source) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > EDGE * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val scale = EDGE.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (scale >= 1f) return bitmap
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        ).also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun trim(current: File) {
        val files = root.listFiles()?.flatMap {
            it.listFiles().orEmpty().filter { file -> file.extension == "jpg" }
        }.orEmpty()
        var size = files.sumOf(File::length)
        for (file in files.sortedBy(File::lastModified)) {
            if (size <= CACHE_BYTES) break
            if (file != current) {
                val length = file.length()
                if (file.delete()) size -= length
            }
        }
    }

    private companion object {
        val HEX = Regex("[a-f0-9]{64}")
        const val EDGE = 512
        const val JPEG_QUALITY = 85
        const val MAX_INPUT_BYTES = 8 * 1024 * 1024
        const val CACHE_BYTES = 32L * 1024 * 1024
    }
}

internal val Session.artworkOwner: String get() = digest("${serverId.ifBlank { baseUrl }}\u0000$deviceId")

private fun digest(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
