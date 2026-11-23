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
import com.henriquesebastiao.downtify.core.model.SavedSleepTimer
import com.henriquesebastiao.downtify.core.model.SleepTimerMode
import com.henriquesebastiao.downtify.core.model.StreamQuality
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// One setter per preference, plus the sleep timer's deadline; same reason as PlayerController.
@Suppress("TooManyFunctions")
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
            offlineDirectoryUri = prefs[OFFLINE_DIRECTORY_URI],
            readAhead = ReadAheadPolicy(
                prefs[READ_AHEAD] ?: defaults.readAhead.tracks,
                prefs[STREAM_CACHE_MB] ?: defaults.readAhead.cacheMb,
                prefs[READ_AHEAD_MOBILE] ?: defaults.readAhead.mobile,
            ).normalized(),
            sleepTimer = sleepTimerOf(prefs),
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

    suspend fun setOfflineDirectory(uri: String?) {
        store.edit { prefs ->
            if (uri == null) prefs.remove(OFFLINE_DIRECTORY_URI) else prefs[OFFLINE_DIRECTORY_URI] = uri
        }
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

    /**
     * Keeps the sleep timer where a restarted service finds it (null clears it): what it ends, and
     * the playback time left of it. The service writes it as it counts, so a timer lost with the
     * process costs seconds, not the whole countdown.
     */
    suspend fun setSleepTimer(timer: SavedSleepTimer?) {
        store.edit { prefs ->
            if (timer == null) {
                prefs.remove(SLEEP_TIMER_MODE)
                prefs.remove(SLEEP_TIMER_MINUTES)
                prefs.remove(SLEEP_TIMER_REMAINING)
            } else {
                val mode = timer.mode
                prefs[SLEEP_TIMER_MODE] = when (mode) {
                    is SleepTimerMode.AfterMinutes -> {
                        prefs[SLEEP_TIMER_MINUTES] = mode.minutes
                        MODE_AFTER_MINUTES
                    }

                    SleepTimerMode.EndOfTrack -> MODE_END_OF_TRACK

                    SleepTimerMode.EndOfEpisode -> MODE_END_OF_EPISODE
                }
                if (mode !is SleepTimerMode.AfterMinutes) prefs.remove(SLEEP_TIMER_MINUTES)
                prefs[SLEEP_TIMER_REMAINING] = timer.remainingMs
            }
        }
    }

    /** A stable id for this install, sent as the WebSocket's `client_id`. */
    suspend fun clientId(): String {
        store.data.first()[CLIENT_ID]?.let { return it }
        val id = UUID.randomUUID().toString()
        store.edit { if (it[CLIENT_ID] == null) it[CLIENT_ID] = id }
        return store.data.first()[CLIENT_ID] ?: id
    }

    /** The kept sleep timer, or null when there is none or what is there makes no sense. */
    private fun sleepTimerOf(prefs: Preferences): SavedSleepTimer? {
        val mode = when (prefs[SLEEP_TIMER_MODE]) {
            MODE_AFTER_MINUTES -> SleepTimerMode.AfterMinutes(prefs[SLEEP_TIMER_MINUTES] ?: return null)
                .takeIf { it.minutes > 0 } ?: return null

            MODE_END_OF_TRACK -> SleepTimerMode.EndOfTrack

            MODE_END_OF_EPISODE -> SleepTimerMode.EndOfEpisode

            else -> return null
        }
        return SavedSleepTimer(mode, (prefs[SLEEP_TIMER_REMAINING] ?: 0L).coerceAtLeast(0))
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
        val OFFLINE_DIRECTORY_URI = stringPreferencesKey("offline_directory_uri")
        val READ_AHEAD = intPreferencesKey("read_ahead_tracks")
        val STREAM_CACHE_MB = intPreferencesKey("stream_cache_mb")
        val READ_AHEAD_MOBILE = booleanPreferencesKey("read_ahead_mobile")
        val SLEEP_TIMER_MODE = stringPreferencesKey("sleep_timer_mode")
        val SLEEP_TIMER_MINUTES = intPreferencesKey("sleep_timer_minutes")
        val SLEEP_TIMER_REMAINING = longPreferencesKey("sleep_timer_remaining_ms")

        // What SLEEP_TIMER_MODE holds: names of its own, so a rename of the model's types keeps the key.
        const val MODE_AFTER_MINUTES = "after_minutes"
        const val MODE_END_OF_TRACK = "end_of_track"
        const val MODE_END_OF_EPISODE = "end_of_episode"
    }
}
