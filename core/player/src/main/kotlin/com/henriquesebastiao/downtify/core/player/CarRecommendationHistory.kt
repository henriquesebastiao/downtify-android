package com.henriquesebastiao.downtify.core.player

import android.content.Context
import com.henriquesebastiao.downtify.core.data.playback.playbackOwner
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Qualified playlist listens on this phone, private and scoped to the current association. */
@Singleton
class CarRecommendationHistory @Inject constructor(
    @ApplicationContext context: Context,
    private val sessions: SessionStore,
) {
    private val preferences by lazy { context.getSharedPreferences("car-recommendations-v1", Context.MODE_PRIVATE) }
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()

    fun counts(names: List<String>): Map<String, Long> {
        val owner = sessions.current?.playbackOwner ?: return emptyMap()
        return names.associateWith { preferences.getLong(key(owner, it), 0).coerceAtLeast(0) }
    }

    fun record(context: PlaybackContext?, owner: String?) {
        if (context?.type != PlaybackContextType.Playlist || owner == null ||
            sessions.current?.playbackOwner != owner
        ) {
            return
        }
        val key = key(owner, context.refId)
        val before = preferences.getLong(key, 0).coerceAtLeast(0)
        preferences.edit().putLong(key, if (before == Long.MAX_VALUE) before else before + 1).apply()
        mutableRevision.value++
    }

    private fun key(owner: String, name: String): String = MessageDigest.getInstance("SHA-256")
        .digest((owner + "\u0000" + name).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
