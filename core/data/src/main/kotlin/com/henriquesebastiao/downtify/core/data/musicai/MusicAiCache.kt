package com.henriquesebastiao.downtify.core.data.musicai

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.henriquesebastiao.downtify.core.network.NetworkJson
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiHomeDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiPlaylistDto
import com.henriquesebastiao.downtify.core.network.session.Session
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Small derived cache in the app's existing private store, scoped to the pairing. */
@Singleton
class MusicAiCache @Inject constructor(private val preferences: DataStore<Preferences>) {
    fun scope(session: Session): String = MessageDigest.getInstance("SHA-256")
        .digest("${session.baseUrl}|${session.serverId}|${session.token}".toByteArray())
        .joinToString("") { "%02x".format(it) }
    private fun key(owner: String, kind: String) = stringPreferencesKey("music_ai:$owner:$kind")
    suspend fun capabilities(owner: String): MusicAiCapabilitiesDto? =
        preferences.data.first()[key(owner, "caps")]?.let {
            runCatching { NetworkJson.decodeFromString(MusicAiCapabilitiesDto.serializer(), it) }.getOrNull()
        }
    suspend fun home(owner: String): List<MusicAiPlaylistDto>? = preferences.data.first()[key(owner, "home")]?.let {
        runCatching { NetworkJson.decodeFromString(MusicAiHomeDto.serializer(), it).cards }.getOrNull()
    }
    suspend fun putCapabilities(owner: String, value: MusicAiCapabilitiesDto) = preferences.edit {
        it.asMap().keys.filter { entry ->
            entry.name.startsWith("music_ai:") &&
                !entry.name.startsWith("music_ai:$owner:")
        }.forEach { entry -> it.remove(stringPreferencesKey(entry.name)) }
        it[key(owner, "caps")] = NetworkJson.encodeToString(MusicAiCapabilitiesDto.serializer(), value)
    }
    suspend fun putHome(owner: String, cards: List<MusicAiPlaylistDto>) = preferences.edit {
        it[key(owner, "home")] = NetworkJson.encodeToString(MusicAiHomeDto.serializer(), MusicAiHomeDto(cards))
    }
    suspend fun clearHome(owner: String) = preferences.edit { it.remove(key(owner, "home")) }
}
