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

@Serializable data object SettingsRoute

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

/** The four top-level destinations of the navigation bar / rail. */
enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int,
) {
    Home(HomeRoute, R.string.nav_home, DowntifyIcons.Home, DowntifyIcons.HomeFilled),
    Search(SearchRoute, R.string.nav_search, DowntifyIcons.Search, DowntifyIcons.Search),
    Library(LibraryRoute, R.string.nav_library, DowntifyIcons.Library, DowntifyIcons.LibraryFilled),
    Downloads(DownloadsRoute, R.string.nav_downloads, DowntifyIcons.Downloads, DowntifyIcons.DownloadsFilled),
}
