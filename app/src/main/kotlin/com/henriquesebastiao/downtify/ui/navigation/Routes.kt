package com.henriquesebastiao.downtify.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute

@Serializable data object SearchRoute

@Serializable data object LibraryRoute

@Serializable data object DownloadsRoute

/** The server's music trash (an admin's pairing, while the server has it on). */
@Serializable data object TrashRoute

@Serializable data object SettingsRoute

@Serializable data object AboutRoute

@Serializable data object LicenseRoute

@Serializable data class AlbumRoute(val albumId: String)

@Serializable data class ArtistRoute(val artistId: String)

@Serializable data class PlaylistRoute(val name: String)

@Serializable data object LikedRoute

@Serializable data object DiscoverRoute

@Serializable data object PodcastsRoute

@Serializable data class ShowRoute(val showId: Long)

/** Search with something typed in: a suggestion from Discover, or a link to open. */
@Serializable data class SearchQueryRoute(val query: String)

/** Tracks like one track, from YouTube Music's radio mix; blank until something is named. */
@Serializable data class SimilarRoute(val artist: String = "", val track: String = "")

/** The three top-level destinations of the navigation bar / rail. */
enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int,
) {
    Home(HomeRoute, R.string.nav_home, DowntifyIcons.Home, DowntifyIcons.HomeFilled),
    Search(SearchRoute, R.string.nav_search, DowntifyIcons.Search, DowntifyIcons.Search),
    Library(LibraryRoute, R.string.nav_library, DowntifyIcons.Library, DowntifyIcons.LibraryFilled),
}

/**
 * The side menu's destinations (Home's menu button): what doesn't need a tab of its own. Each
 * opens above Home, with a back button, as Discover does.
 */
enum class DrawerDestination(
    val route: Any,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int,
    /** Shown only to an admin's pairing on a server that has the music trash on. */
    val trashOnly: Boolean = false,
) {
    Downloads(DownloadsRoute, R.string.nav_downloads, DowntifyIcons.Downloads, DowntifyIcons.DownloadsFilled),
    Trash(TrashRoute, R.string.nav_trash, DowntifyIcons.Delete, DowntifyIcons.Delete, trashOnly = true),
}
