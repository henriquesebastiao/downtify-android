package com.henriquesebastiao.downtify.core.data.loudness

import android.content.Context
import android.util.LruCache
import com.henriquesebastiao.downtify.core.data.di.ApplicationScope
import com.henriquesebastiao.downtify.core.data.settings.SettingsRepository
import com.henriquesebastiao.downtify.core.network.ApiFactory
import com.henriquesebastiao.downtify.core.network.dto.LoudnessDto
import com.henriquesebastiao.downtify.core.network.session.SessionStore
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Measurements are fetched on the loader/IO thread before decoded audio is queued. */
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
    private val profiles = LruCache<String, LoudnessDto>(CACHE_SIZE)
    private val origins = LruCache<String, String>(CACHE_SIZE)
    private val receipts = File(context.filesDir, "loudness")
    private val json = Json { ignoreUnknownKeys = true }

    private fun key(trackId: String): String? {
        val session = sessions.current ?: return null
        return listOf(session.serverId, session.username, trackId).joinToString("\u0000")
    }

    fun cacheVersion(trackId: String, profile: LoudnessDto?): String =
        hash((key(trackId).orEmpty() + "\u0000" + profile?.fingerprint.orEmpty()).toByteArray())

    fun gainDb(trackId: String): Double = if (enabled.value) {
        key(trackId)?.let {
            profiles[it]?.safeGainDb()
        } ?: 0.0
    } else {
        0.0
    }

    /** Called only by Media3's background data loader, never the UI thread. */
    fun prepareBlocking(trackId: String, localFile: File? = null, refresh: Boolean = true): LoudnessDto? {
        if (!enabled.value) return null
        val key = key(trackId) ?: return null
        val origin = localFile?.let { "${it.absolutePath}:${it.length()}:${it.lastModified()}" } ?: "remote"
        if (!refresh && origins[key] == origin) profiles[key]?.let { return it }
        val profile = if (localFile ==
            null
        ) {
            runBlocking(Dispatchers.IO) { fetch(trackId) }
        } else {
            readBound(key, localFile)
        }
        profiles.put(key, profile ?: LoudnessDto())
        origins.put(key, origin)
        return profile
    }

    private suspend fun fetch(trackId: String): LoudnessDto? {
        val session = sessions.current ?: return null
        return try {
            withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { apis.create(session.baseUrl).loudness(trackId) }
                ?.takeIf { sessions.current == session }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    /** A resumed download has no whole-file ETag proof and must keep its original volume. */
    suspend fun bindDownloaded(trackId: String, file: File, etag: String?, bytes: Long) = withContext(Dispatchers.IO) {
        if (!enabled.value) return@withContext
        val owner = key(trackId) ?: return@withContext
        if (etag.isNullOrBlank()) return@withContext
        val profile = fetch(trackId) ?: return@withContext
        if (profile.sourceEtag != etag || profile.sourceSize != bytes) return@withContext
        if (profile.algorithmVersion != 1 || profile.fingerprint.isBlank()) {
            return@withContext
        }
        try {
            val receipt = BoundProfile(file.length(), file.lastModified(), digest(file), profile)
            receipts.mkdirs()
            receiptFile(owner).writeText(json.encodeToString(receipt))
        } catch (_: Exception) {
            // Optional metadata must never fail a completed audio download.
        }
    }

    private fun readBound(owner: String, file: File): LoudnessDto? = try {
        val receipt = json.decodeFromString<BoundProfile>(receiptFile(owner).readText())
        receipt.profile.takeIf {
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
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
        "%02x".format(it)
    }

    @Serializable
    private data class BoundProfile(val bytes: Long, val modified: Long, val sha256: String, val profile: LoudnessDto)

    private companion object {
        const val CACHE_SIZE = 64
        const val BUFFER_BYTES = 64 * 1024
        const val LOOKUP_TIMEOUT_MS = 1500L
    }
}
