package com.henriquesebastiao.downtify.core.data.loudness

import android.content.Context
import android.util.LruCache
import com.henriquesebastiao.downtify.core.data.di.ApplicationScope
import com.henriquesebastiao.downtify.core.data.settings.SettingsRepository
import com.henriquesebastiao.downtify.core.model.LoudnessProfile
import com.henriquesebastiao.downtify.core.model.NormalizationLevel
import com.henriquesebastiao.downtify.core.network.ApiFactory
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import com.henriquesebastiao.downtify.core.network.session.cacheOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The server's loudness measurements for volume normalization. A track's is looked up on
 * Media3's loader thread when its audio is opened, so the gain is known before its first
 * sample reaches the normalizer; an offline copy uses the one bound to it when it was
 * downloaded whole ([bindDownloaded]), and only while the file is still that copy.
 */
@Suppress("TooGenericExceptionCaught")
@Singleton
class LoudnessProfiles @Inject constructor(
    @ApplicationContext context: Context,
    private val sessions: SessionStore,
    private val apis: ApiFactory,
    settings: SettingsRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    val enabled = settings.settings.map { it.normalizeVolume }.stateIn(scope, SharingStarted.Eagerly, false)
    val level = settings.settings.map { it.normalizationLevel }
        .stateIn(scope, SharingStarted.Eagerly, NormalizationLevel.Normal)
    private val known = LruCache<String, Known>(CACHE_SIZE)
    private val receipts = File(context.filesDir, "loudness")
    private val json = Json { ignoreUnknownKeys = true }

    /** What a stream (or which local file) was last prepared with. */
    private class Known(val profile: LoudnessProfile?, val origin: String) {
        private val at = System.currentTimeMillis()

        fun fresh() = profile != null && System.currentTimeMillis() - at < STREAM_TTL_MS
    }

    private fun key(trackId: String): String? = sessions.current?.let { it.cacheOwner + "\u0000" + trackId }

    /** The gain for [trackId]'s audio as last prepared; 0 when off or not measured. */
    fun gainDb(trackId: String): Double {
        if (!enabled.value) return 0.0
        val key = key(trackId) ?: return 0.0
        return known[key]?.profile?.safeGainDb(level.value) ?: 0.0
    }

    /**
     * Looks up a streamed track's measurement. Asks the server only when [ask] (the start of
     * the track, online) and nothing fresh is known; when the server can't answer, an older
     * measurement of the same track still applies. Blocks for up to [LOOKUP_TIMEOUT_MS]: call
     * it from Media3's loader thread only.
     */
    fun prepareStreamBlocking(trackId: String, ask: Boolean) {
        if (!enabled.value) return
        val key = key(trackId) ?: return
        val cached = known[key]?.takeIf { it.origin == STREAM }
        if (cached != null && (!ask || cached.fresh())) return
        val fetched = if (ask) runBlocking(Dispatchers.IO) { fetch(trackId) } else null
        when {
            fetched != null -> known.put(key, Known(fetched, STREAM))
            cached == null -> known.remove(key)
        }
    }

    /**
     * Uses the measurement bound to the offline copy [file], if it still is that copy. The file
     * is hashed once per size and modification time, not on every play. Loader thread only.
     */
    fun prepareLocalBlocking(trackId: String, file: File) {
        if (!enabled.value) return
        val key = key(trackId) ?: return
        val origin = "${file.absolutePath}:${file.length()}:${file.lastModified()}"
        if (known[key]?.origin == origin) return
        known.put(key, Known(readBound(key, file), origin))
    }

    private suspend fun fetch(trackId: String): LoudnessProfile? {
        val session = sessions.current ?: return null
        return try {
            withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { apis.create(session.baseUrl).loudness(trackId) }
                ?.takeIf { sessions.current == session }
                ?.toModel()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Older servers (404), timeouts, no network: the original level.
            null
        }
    }

    /**
     * Binds the server's measurement to a finished offline download, whatever the setting, so
     * the copy is ready when normalization is switched on. Only a whole download can prove it
     * is the measured file ([etag] and [bytes] match the original's); a resumed one has no
     * [etag] and keeps its original volume.
     */
    suspend fun bindDownloaded(trackId: String, file: File, etag: String?, bytes: Long) = withContext(Dispatchers.IO) {
        val owner = key(trackId) ?: return@withContext
        // Whatever was bound before described an earlier copy.
        receiptFile(owner).delete()
        if (etag.isNullOrBlank()) return@withContext
        val profile = fetch(trackId) ?: return@withContext
        if (!profile.bindable || profile.sourceEtag != etag || profile.sourceSize != bytes) return@withContext
        try {
            val receipt = Receipt.of(file.length(), file.lastModified(), digest(file), profile)
            receipts.mkdirs()
            receiptFile(owner).writeText(json.encodeToString(Receipt.serializer(), receipt))
        } catch (_: Exception) {
            // Optional metadata must never fail a completed audio download.
        }
    }

    private fun readBound(owner: String, file: File): LoudnessProfile? = try {
        val receipt = json.decodeFromString(Receipt.serializer(), receiptFile(owner).readText())
        receipt.profile().takeIf {
            file.length() == receipt.bytes && file.lastModified() == receipt.modified &&
                digest(file) == receipt.sha256
        }
    } catch (_: Exception) {
        null
    }

    private fun receiptFile(owner: String) = File(receipts, hash(owner.toByteArray()) + ".json")

    private fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            var count = input.read(buffer)
            while (count >= 0) {
                digest.update(buffer, 0, count)
                count = input.read(buffer)
            }
        }
        return digest.digest().toHex()
    }

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    /** An offline copy and the measurement that matched it when it was downloaded. */
    @Serializable
    private data class Receipt(
        val bytes: Long,
        val modified: Long,
        val sha256: String,
        val state: String,
        val algorithmVersion: Int,
        val integratedLufs: Double?,
        val truePeakDbtp: Double?,
        val gainDb: Double,
        val fingerprint: String,
    ) {
        fun profile() = LoudnessProfile(state, algorithmVersion, integratedLufs, truePeakDbtp, gainDb, fingerprint)

        companion object {
            fun of(bytes: Long, modified: Long, sha256: String, profile: LoudnessProfile) = Receipt(
                bytes,
                modified,
                sha256,
                profile.state,
                profile.algorithmVersion,
                profile.integratedLufs,
                profile.truePeakDbtp,
                profile.gainDb,
                profile.fingerprint,
            )
        }
    }

    private companion object {
        const val CACHE_SIZE = 64
        const val BUFFER_BYTES = 64 * 1024
        const val LOOKUP_TIMEOUT_MS = 1500L

        /** How long a streamed track's measurement is reused before the next start asks again. */
        const val STREAM_TTL_MS = 10 * 60 * 1000L
        const val STREAM = "stream"
    }
}
