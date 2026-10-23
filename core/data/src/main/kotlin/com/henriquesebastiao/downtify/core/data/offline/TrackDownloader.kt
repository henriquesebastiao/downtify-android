package com.henriquesebastiao.downtify.core.data.offline

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Downloads one file into a `.part` file, resuming what's already there with an
 * HTTP `Range` request (the server answers `206`). Cancelling the coroutine
 * stops the transfer and leaves the part for the next attempt.
 */
class TrackDownloader(private val client: OkHttpClient) {

    sealed interface Result {
        data class Complete(val bytes: Long, val etag: String? = null) : Result

        /** The server doesn't have it (404/410): the track left the library. */
        data object Gone : Result

        /** Network trouble or an unexpected answer; the part is kept to resume. */
        data class Failed(val status: Int?, val cause: IOException? = null) : Result
    }

    suspend fun download(url: String, part: File, onProgress: (Long) -> Unit = {}): Result =
        withContext(Dispatchers.IO) { attempt(url, part, onProgress, restarted = false) }

    private suspend fun attempt(url: String, part: File, onProgress: (Long) -> Unit, restarted: Boolean): Result {
        val from = if (part.exists()) part.length() else 0L
        val request = Request.Builder().url(url)
            .apply { if (from > 0) header("Range", "bytes=$from-") }
            .build()
        val call = client.newCall(request)
        val cancel = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        return try {
            call.execute().use { response ->
                when {
                    response.code == HTTP_NOT_FOUND || response.code == HTTP_GONE -> Result.Gone

                    // The part is longer than the file (it changed on the server): start over once.
                    response.code == HTTP_RANGE_NOT_SATISFIABLE && !restarted -> {
                        part.delete()
                        null
                    }

                    response.code == HTTP_PARTIAL && resumesAt(
                        response,
                        from,
                    ) -> write(response, part, from, onProgress)

                    response.code == HTTP_OK -> write(response, part, 0, onProgress)

                    else -> Result.Failed(response.code)
                }
            } ?: attempt(url, part, onProgress, restarted = true)
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            Result.Failed(null, e)
        } finally {
            cancel?.dispose()
        }
    }

    private fun resumesAt(response: Response, from: Long): Boolean =
        response.header("Content-Range")?.startsWith("bytes $from-") == true

    private suspend fun write(response: Response, part: File, from: Long, onProgress: (Long) -> Unit): Result {
        val body = response.body
        val expected = body.contentLength().takeIf { it >= 0 }?.let { it + from }
        val written = FileOutputStream(part, from > 0).use { out ->
            body.byteStream().use { input -> copy(input, out, from, onProgress) }
        }
        return if (expected != null && written != expected) {
            Result.Failed(null, IOException("Got $written of $expected bytes"))
        } else {
            Result.Complete(written, response.header("ETag").takeIf { from == 0L })
        }
    }

    /** Copies until the end or cancellation; returns the bytes in the part, counting [from]. */
    private suspend fun copy(input: InputStream, out: OutputStream, from: Long, onProgress: (Long) -> Unit): Long {
        val buffer = ByteArray(BUFFER_BYTES)
        var written = from
        var read = input.read(buffer)
        while (read >= 0) {
            currentCoroutineContext().ensureActive()
            out.write(buffer, 0, read)
            written += read
            onProgress(written)
            read = input.read(buffer)
        }
        return written
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_PARTIAL = 206
        const val HTTP_NOT_FOUND = 404
        const val HTTP_GONE = 410
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        const val BUFFER_BYTES = 64 * 1024
    }
}
