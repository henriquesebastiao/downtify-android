package com.henriquesebastiao.downtify.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.henriquesebastiao.downtify.core.model.AppFont
import com.henriquesebastiao.downtify.core.model.NormalizationLevel
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
        val sort = enumOr(prefs[LIBRARY_SORT], defaults.librarySort)
        UserSettings(
            theme = enumOr(prefs[THEME], defaults.theme),
            dynamicColor = prefs[DYNAMIC_COLOR] ?: defaults.dynamicColor,
            amoled = prefs[AMOLED] ?: defaults.amoled,
            font = AppFont.of(prefs[FONT]),
            floatingNavBar = prefs[FLOATING_NAV_BAR] ?: defaults.floatingNavBar,
            wifiQuality = StreamQuality.decode(prefs[WIFI_QUALITY]) ?: defaults.wifiQuality,
            mobileQuality = StreamQuality.decode(prefs[MOBILE_QUALITY]) ?: defaults.mobileQuality,
            normalizeVolume = prefs[NORMALIZE_VOLUME] ?: defaults.normalizeVolume,
            normalizationLevel = enumOr(prefs[NORMALIZATION_LEVEL], defaults.normalizationLevel),
            libraryLayout = enumOr(prefs[LIBRARY_LAYOUT], defaults.libraryLayout),
            librarySort = sort,
            // Saved only once the user flips it; until then each sort keeps its own order.
            librarySortDescending = prefs[LIBRARY_SORT_DESCENDING] ?: sort.descendingByDefault,
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

    suspend fun setFloatingNavBar(value: Boolean) {
        store.edit { it[FLOATING_NAV_BAR] = value }
    }

    suspend fun setWifiQuality(value: StreamQuality) {
        store.edit { it[WIFI_QUALITY] = value.encode() }
    }

    suspend fun setMobileQuality(value: StreamQuality) {
        store.edit { it[MOBILE_QUALITY] = value.encode() }
    }

    suspend fun setNormalizeVolume(value: Boolean) {
        store.edit { it[NORMALIZE_VOLUME] = value }
    }

    suspend fun setNormalizationLevel(value: NormalizationLevel) {
        store.edit { it[NORMALIZATION_LEVEL] = value.name }
    }

    suspend fun setLibraryLayout(value: LibraryLayout) {
        store.edit { it[LIBRARY_LAYOUT] = value.name }
    }

    /** Picking another sort starts it in its own order (see [LibrarySort.descendingByDefault]). */
    suspend fun setLibrarySort(value: LibrarySort) {
        store.edit { prefs ->
            if (enumOr(prefs[LIBRARY_SORT], UserSettings().librarySort) != value) prefs.remove(LIBRARY_SORT_DESCENDING)
            prefs[LIBRARY_SORT] = value.name
        }
    }

    suspend fun setLibrarySortDescending(value: Boolean) {
        store.edit { it[LIBRARY_SORT_DESCENDING] = value }
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
        val FLOATING_NAV_BAR = booleanPreferencesKey("floating_nav_bar")
        val WIFI_QUALITY = stringPreferencesKey("wifi_quality")
        val MOBILE_QUALITY = stringPreferencesKey("mobile_quality")
        val NORMALIZE_VOLUME = booleanPreferencesKey("normalize_volume")
        val NORMALIZATION_LEVEL = stringPreferencesKey("normalization_level")
        val LIBRARY_LAYOUT = stringPreferencesKey("library_layout")
        val LIBRARY_SORT = stringPreferencesKey("library_sort")
        val LIBRARY_SORT_DESCENDING = booleanPreferencesKey("library_sort_descending")
        val CLIENT_ID = stringPreferencesKey("client_id")
        val DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        val OFFLINE_LIMIT = longPreferencesKey("offline_limit_bytes")
        val READ_AHEAD = intPreferencesKey("read_ahead_tracks")
        val STREAM_CACHE_MB = intPreferencesKey("stream_cache_mb")
        val READ_AHEAD_MOBILE = booleanPreferencesKey("read_ahead_mobile")
    }
}
