package com.henriquesebastiao.downtify.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.henriquesebastiao.downtify.core.model.AppFont
import com.henriquesebastiao.downtify.core.model.ReadAheadPolicy
import com.henriquesebastiao.downtify.core.model.StreamQuality
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) {
    val settings: Flow<UserSettings> = store.data.map { prefs ->
        val defaults = UserSettings()
        UserSettings(
            theme = enumOr(prefs[THEME], defaults.theme),
            dynamicColor = prefs[DYNAMIC_COLOR] ?: defaults.dynamicColor,
            amoled = prefs[AMOLED] ?: defaults.amoled,
            font = AppFont.of(prefs[FONT]),
            wifiQuality = StreamQuality.decode(prefs[WIFI_QUALITY]) ?: defaults.wifiQuality,
            mobileQuality = StreamQuality.decode(prefs[MOBILE_QUALITY]) ?: defaults.mobileQuality,
            libraryLayout = enumOr(prefs[LIBRARY_LAYOUT], defaults.libraryLayout),
            librarySort = enumOr(prefs[LIBRARY_SORT], defaults.librarySort),
            downloadWifiOnly = prefs[DOWNLOAD_WIFI_ONLY] ?: defaults.downloadWifiOnly,
            offlineLimitBytes = prefs[OFFLINE_LIMIT] ?: defaults.offlineLimitBytes,
            readAhead = ReadAheadPolicy(
                prefs[READ_AHEAD] ?: defaults.readAhead.tracks,
                prefs[STREAM_CACHE_MB] ?: defaults.readAhead.cacheMb,
                prefs[READ_AHEAD_MOBILE] ?: defaults.readAhead.mobile,
            ).normalized(),
        )
    }

    suspend fun setTheme(value: ThemeMode) {
        store.edit { it[THEME] = value.name }
    }

    suspend fun setDynamicColor(value: Boolean) {
        store.edit { it[DYNAMIC_COLOR] = value }
    }

    suspend fun setAmoled(value: Boolean) {
        store.edit { it[AMOLED] = value }
    }

    suspend fun setFont(value: AppFont) {
        store.edit { it[FONT] = value.name }
    }

    suspend fun setWifiQuality(value: StreamQuality) {
        store.edit { it[WIFI_QUALITY] = value.encode() }
    }

    suspend fun setMobileQuality(value: StreamQuality) {
        store.edit { it[MOBILE_QUALITY] = value.encode() }
    }

    suspend fun setLibraryLayout(value: LibraryLayout) {
        store.edit { it[LIBRARY_LAYOUT] = value.name }
    }

    suspend fun setLibrarySort(value: LibrarySort) {
        store.edit { it[LIBRARY_SORT] = value.name }
    }

    suspend fun setDownloadWifiOnly(value: Boolean) {
        store.edit { it[DOWNLOAD_WIFI_ONLY] = value }
    }

    suspend fun setOfflineLimit(bytes: Long) {
        store.edit { it[OFFLINE_LIMIT] = bytes }
    }

    suspend fun setReadAhead(tracks: Int) {
        store.edit { it[READ_AHEAD] = tracks.coerceIn(0, 3) }
    }

    suspend fun setStreamCacheMb(mb: Int) {
        store.edit { it[STREAM_CACHE_MB] = ReadAheadPolicy(cacheMb = mb).normalized().cacheMb }
    }

    suspend fun setReadAheadMobile(enabled: Boolean) {
        store.edit { it[READ_AHEAD_MOBILE] = enabled }
    }

    /** A stable id for this install, sent as the WebSocket's `client_id`. */
    suspend fun clientId(): String {
        store.data.first()[CLIENT_ID]?.let { return it }
        val id = UUID.randomUUID().toString()
        store.edit { if (it[CLIENT_ID] == null) it[CLIENT_ID] = id }
        return store.data.first()[CLIENT_ID] ?: id
    }

    private inline fun <reified E : Enum<E>> enumOr(value: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == value } ?: default

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AMOLED = booleanPreferencesKey("amoled")
        val FONT = stringPreferencesKey("font")
        val WIFI_QUALITY = stringPreferencesKey("wifi_quality")
        val MOBILE_QUALITY = stringPreferencesKey("mobile_quality")
        val LIBRARY_LAYOUT = stringPreferencesKey("library_layout")
        val LIBRARY_SORT = stringPreferencesKey("library_sort")
        val CLIENT_ID = stringPreferencesKey("client_id")
        val DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        val OFFLINE_LIMIT = longPreferencesKey("offline_limit_bytes")
        val READ_AHEAD = intPreferencesKey("read_ahead_tracks")
        val STREAM_CACHE_MB = intPreferencesKey("stream_cache_mb")
        val READ_AHEAD_MOBILE = booleanPreferencesKey("read_ahead_mobile")
    }
}
