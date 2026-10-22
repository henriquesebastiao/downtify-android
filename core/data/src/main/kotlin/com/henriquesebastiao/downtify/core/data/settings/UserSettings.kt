package com.henriquesebastiao.downtify.core.data.settings

import com.henriquesebastiao.downtify.core.model.AppFont
import com.henriquesebastiao.downtify.core.model.OfflinePlanner
import com.henriquesebastiao.downtify.core.model.ReadAheadPolicy
import com.henriquesebastiao.downtify.core.model.StreamQuality

enum class ThemeMode { System, Dark, Light }

enum class LibraryLayout { Grid, List }

enum class LibrarySort { RecentlyAdded, Alphabetical, Artist }

data class UserSettings(
    val theme: ThemeMode = ThemeMode.System,
    /** Material You wallpaper colors instead of Downtify green. Off by default. */
    val dynamicColor: Boolean = false,
    /** True black instead of the dark gray in the dark theme, for OLED screens. Off by default. */
    val amoled: Boolean = false,
    val font: AppFont = AppFont.Default,
    val wifiQuality: StreamQuality = StreamQuality.Original,
    val mobileQuality: StreamQuality = StreamQuality.MobileDefault,
    val libraryLayout: LibraryLayout = LibraryLayout.Grid,
    val librarySort: LibrarySort = LibrarySort.RecentlyAdded,
    /** Offline copies wait for an unmetered network. */
    val downloadWifiOnly: Boolean = true,
    /** Most bytes offline copies may take; [OfflinePlanner.UNLIMITED] for no limit. */
    val offlineLimitBytes: Long = DEFAULT_OFFLINE_LIMIT,
    val readAhead: ReadAheadPolicy = ReadAheadPolicy(),
) {
    companion object {
        const val DEFAULT_OFFLINE_LIMIT = 8_000_000_000L

        /** The choices Settings offers, in bytes (decimal GB, like the system's storage screen). */
        val OFFLINE_LIMITS = listOf(1L, 2L, 4L, 8L, 16L, 32L, 64L).map { it * 1_000_000_000L } +
            OfflinePlanner.UNLIMITED
    }
}
