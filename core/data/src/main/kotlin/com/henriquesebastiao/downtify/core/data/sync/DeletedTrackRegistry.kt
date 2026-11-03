package com.henriquesebastiao.downtify.core.data.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Persist cache invalidations until the player has safely handled them. */
@Singleton
class DeletedTrackRegistry @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("deleted_track_cache", Context.MODE_PRIVATE)
    private val lock = Any()
    private val changes = MutableStateFlow(0L)
    val revision = changes.asStateFlow()

    suspend fun record(owner: String, ids: Collection<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        synchronized(lock) {
            val key = key(owner)
            val all = prefs.getStringSet(key, emptySet()).orEmpty() + ids
            if (!prefs.edit().putStringSet(key, all).commit()) throw IOException("cache journal unavailable")
            changes.update { it + 1 }
        }
    }

    fun pending(owner: String): Set<String> = synchronized(lock) {
        prefs.getStringSet(key(owner), emptySet()).orEmpty().toSet()
    }

    fun acknowledge(owner: String, ids: Collection<String>): Boolean = synchronized(lock) {
        val key = key(owner)
        val remaining = prefs.getStringSet(key, emptySet()).orEmpty() - ids.toSet()
        prefs.edit().putStringSet(key, remaining).commit()
    }

    private fun key(owner: String): String = MessageDigest.getInstance("SHA-256")
        .digest(owner.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
