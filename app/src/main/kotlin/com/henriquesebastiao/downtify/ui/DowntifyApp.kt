package com.henriquesebastiao.downtify.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.henriquesebastiao.downtify.AppState
import com.henriquesebastiao.downtify.MainViewModel
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.RecentContext
import com.henriquesebastiao.downtify.feature.artist.ArtistRoute
import com.henriquesebastiao.downtify.feature.collection.CollectionRoute
import com.henriquesebastiao.downtify.feature.connect.ConnectRoute
import com.henriquesebastiao.downtify.feature.discover.DiscoverRoute
import com.henriquesebastiao.downtify.feature.downloads.DownloadsRoute
import com.henriquesebastiao.downtify.feature.home.HomeRoute
import com.henriquesebastiao.downtify.feature.library.LibraryNavigation
import com.henriquesebastiao.downtify.feature.library.LibraryRoute
import com.henriquesebastiao.downtify.feature.player.LyricsSheet
import com.henriquesebastiao.downtify.feature.player.MiniPlayer
import com.henriquesebastiao.downtify.feature.player.NowPlayingActions
import com.henriquesebastiao.downtify.feature.player.NowPlayingScreen
import com.henriquesebastiao.downtify.feature.player.PlayerViewModel
import com.henriquesebastiao.downtify.feature.player.QueueSheet
import com.henriquesebastiao.downtify.feature.podcasts.PodcastsRoute
import com.henriquesebastiao.downtify.feature.podcasts.ShowRoute
import com.henriquesebastiao.downtify.feature.search.SearchNavigation
import com.henriquesebastiao.downtify.feature.search.SearchRoute
import com.henriquesebastiao.downtify.feature.settings.SettingsRoute
import com.henriquesebastiao.downtify.feature.similar.SimilarRoute as SimilarScreenRoute
import com.henriquesebastiao.downtify.ui.common.CoverUrls
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.navigation.AlbumRoute
import com.henriquesebastiao.downtify.ui.navigation.ArtistRoute as ArtistDestination
import com.henriquesebastiao.downtify.ui.navigation.DiscoverRoute as DiscoverDestination
import com.henriquesebastiao.downtify.ui.navigation.DownloadsRoute as DownloadsDestination
import com.henriquesebastiao.downtify.ui.navigation.HomeRoute as HomeDestination
import com.henriquesebastiao.downtify.ui.navigation.LibraryRoute as LibraryDestination
import com.henriquesebastiao.downtify.ui.navigation.LikedRoute
import com.henriquesebastiao.downtify.ui.navigation.PlaylistRoute
import com.henriquesebastiao.downtify.ui.navigation.PodcastsRoute as PodcastsDestination
import com.henriquesebastiao.downtify.ui.navigation.SearchQueryRoute
import com.henriquesebastiao.downtify.ui.navigation.SearchRoute as SearchDestination
import com.henriquesebastiao.downtify.ui.navigation.SettingsRoute as SettingsDestination
import com.henriquesebastiao.downtify.ui.navigation.ShowRoute as ShowDestination
import com.henriquesebastiao.downtify.ui.navigation.SimilarRoute
import com.henriquesebastiao.downtify.ui.navigation.TopLevelDestination
import kotlin.coroutines.cancellation.CancellationException

/** The app: the connect screen until the phone is paired, then the main shell. */
@Composable
fun DowntifyApp(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val appState by viewModel.appState.collectAsStateWithLifecycle()
    val pairingRequest by viewModel.pairingRequest.collectAsStateWithLifecycle()
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (val state = appState) {
            AppState.Loading -> Unit

            AppState.NeedsPairing -> ConnectRoute(
                pairingRequest = pairingRequest,
                onPairingRequestHandled = viewModel::consumePairingRequest,
            )

            is AppState.Paired -> {
                // Opening a pairing link while paired does nothing: unpair in Settings first.
                LaunchedEffect(pairingRequest) { if (pairingRequest != null) viewModel.consumePairingRequest() }
                CompositionLocalProvider(LocalCoverUrls provides remember(state.baseUrl) { CoverUrls(state.baseUrl) }) {
                    MainShell()
                }
            }
        }
    }
}

@Composable
private fun MainShell() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val player: PlayerViewModel = hiltViewModel()
    val playerState by player.state.collectAsStateWithLifecycle()
    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    var currentTab by rememberSaveable { mutableStateOf(TopLevelDestination.Home) }
    val onSettings = destination?.hasRoute<SettingsDestination>() == true

    TopLevelDestination.entries.firstOrNull { destination?.hasRoute(it.route::class) == true }?.let { currentTab = it }
    RequestNotificationPermission()

    Box(Modifier.fillMaxSize()) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                TopLevelDestination.entries.forEach { tab ->
                    val selected = tab == currentTab
                    item(
                        selected = selected,
                        onClick = {
                            if (selected) {
                                // Re-tapping the open tab closes whatever was
                                // opened above it (Discover, an album, a
                                // search) and shows the tab's start view.
                                // Falls back to a plain switch when the tab
                                // has no entry in the stack.
                                if (!navController.popBackStack(tab.route, inclusive = false)) {
                                    navController.navigateTopLevel(tab)
                                }
                            } else {
                                currentTab = tab
                                navController.navigateTopLevel(tab)
                            }
                        },
                        icon = {
                            Icon(
                                painterResource(if (selected) tab.selectedIcon else tab.icon),
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            },
            layoutType = if (onSettings) {
                NavigationSuiteType.None
            } else {
                NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
            },
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { AppNavHost(navController) }
                if (playerState.hasMedia && !onSettings) {
                    MiniPlayer(
                        state = playerState,
                        onOpen = { nowPlayingOpen = true },
                        onTogglePlay = player::togglePlayPause,
                        onSimilar = { artist, title -> navController.navigate(SimilarRoute(artist, title)) },
                        downloadJob = player.streamJob.collectAsStateWithLifecycle().value,
                        onDownloadStream = player::downloadStream,
                    )
                }
            }
        }
        NowPlayingOverlay(
            open = nowPlayingOpen && playerState.hasMedia,
            player = player,
            onClose = { nowPlayingOpen = false },
            onNavigate = { route ->
                nowPlayingOpen = false
                // Similar opens per track: a fresh entry, so tapping it for
                // another track while its page is already open still shows the
                // new mix (single-top would keep the stale one).
                if (route is SimilarRoute) {
                    navController.navigate(route)
                } else {
                    navController.navigate(route) { launchSingleTop = true }
                }
            },
        )
    }
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    val toAlbum: (String) -> Unit = { navController.navigate(AlbumRoute(it)) }
    val toArtist: (String) -> Unit = { navController.navigate(ArtistDestination(it)) }
    val toPlaylist: (String) -> Unit = { navController.navigate(PlaylistRoute(it)) }
    NavHost(navController = navController, startDestination = HomeDestination) {
        composable<HomeDestination> {
            HomeRoute(
                onOpenSettings = { navController.navigate(SettingsDestination) },
                onOpenRecent = { navController.navigate(routeFor(it)) },
                onOpenAlbum = toAlbum,
                onSeeAll = { navController.navigateTopLevel(TopLevelDestination.Library) },
                onOpenDiscover = { navController.navigate(DiscoverDestination) },
                onOpenPodcasts = { navController.navigate(PodcastsDestination) },
                onOpenSimilar = { navController.navigate(SimilarRoute()) },
            )
        }
        composable<SearchDestination> {
            SearchRoute(
                SearchNavigation(
                    onAlbum = toAlbum,
                    onArtist = toArtist,
                    onPlaylist = toPlaylist,
                    onSimilar = { artist, title -> navController.navigate(SimilarRoute(artist, title)) },
                    onOpenLink = { navController.navigate(SearchQueryRoute(it)) },
                ),
            )
        }
        // The same screen with something typed in (a suggestion from Discover); its query is the route's argument.
        composable<SearchQueryRoute> {
            SearchRoute(
                SearchNavigation(
                    onAlbum = toAlbum,
                    onArtist = toArtist,
                    onPlaylist = toPlaylist,
                    onSimilar = { artist, title -> navController.navigate(SimilarRoute(artist, title)) },
                    onOpenLink = { navController.navigate(SearchQueryRoute(it)) },
                ),
            )
        }
        composable<SimilarRoute> {
            SimilarScreenRoute(onBack = navController::popBackStack)
        }
        composable<DiscoverDestination> {
            DiscoverRoute(
                onBack = navController::popBackStack,
                onOpenInSearch = { navController.navigate(SearchQueryRoute(it)) },
            )
        }
        composable<PodcastsDestination> {
            PodcastsRoute(
                onBack = navController::popBackStack,
                onShow = { navController.navigate(ShowDestination(it)) },
            )
        }
        composable<ShowDestination> { ShowRoute(onBack = navController::popBackStack) }
        composable<LibraryDestination> {
            LibraryRoute(
                LibraryNavigation(
                    onSearch = { navController.navigateTopLevel(TopLevelDestination.Search) },
                    onAlbum = toAlbum,
                    onArtist = toArtist,
                    onPlaylist = toPlaylist,
                    onLiked = { navController.navigate(LikedRoute) },
                ),
            )
        }
        composable<DownloadsDestination> {
            DownloadsRoute(
                onOpen = { item ->
                    when (item.type) {
                        PlaybackContextType.Album -> toAlbum(item.refId)
                        PlaybackContextType.Playlist -> toPlaylist(item.refId)
                        else -> navController.navigate(LikedRoute)
                    }
                },
            )
        }
        composable<SettingsDestination> { SettingsRoute(onBack = navController::popBackStack) }
        composable<AlbumRoute> {
            CollectionRoute(onBack = navController::popBackStack, onArtist = toArtist, onAlbum = toAlbum)
        }
        composable<PlaylistRoute> {
            CollectionRoute(onBack = navController::popBackStack, onArtist = toArtist, onAlbum = toAlbum)
        }
        composable<LikedRoute> {
            CollectionRoute(onBack = navController::popBackStack, onArtist = toArtist, onAlbum = toAlbum)
        }
        composable<ArtistDestination> { ArtistRoute(onBack = navController::popBackStack, onAlbum = toAlbum) }
    }
}

/** Now Playing slides up over everything and collapses back into the mini player, following predictive back. */
@Composable
private fun NowPlayingOverlay(open: Boolean, player: PlayerViewModel, onClose: () -> Unit, onNavigate: (Any) -> Unit) {
    val state by player.state.collectAsStateWithLifecycle()
    val isLiked by player.isLiked.collectAsStateWithLifecycle()
    val streamJob by player.streamJob.collectAsStateWithLifecycle()
    val streamTrack by player.streamTrack.collectAsStateWithLifecycle()
    val serverName by player.serverName.collectAsStateWithLifecycle()
    val lyrics by player.lyrics.collectAsStateWithLifecycle()
    var backProgress by remember { mutableFloatStateOf(0f) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val density = LocalDensity.current

    PredictiveBackHandler(enabled = open && sheet == null) { progress ->
        try {
            progress.collect { backProgress = it.progress }
            onClose()
        } catch (e: CancellationException) {
            backProgress = 0f
            throw e
        }
    }
    LaunchedEffect(open) { if (open) backProgress = 0f }

    AnimatedVisibility(
        visible = open,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        NowPlayingScreen(
            state = state,
            isLiked = isLiked,
            serverName = serverName,
            downloadJob = streamJob,
            hasLibraryCopy = streamTrack != null,
            actions = NowPlayingActions(
                onCollapse = onClose,
                onTogglePlay = player::togglePlayPause,
                onNext = player::next,
                onPrevious = player::previous,
                onSeek = player::seekTo,
                onToggleShuffle = player::toggleShuffle,
                onCycleRepeat = player::cycleRepeat,
                onToggleLike = player::toggleLike,
                onDownloadStream = player::downloadStream,
                onSimilar = { artist, title -> onNavigate(SimilarRoute(artist, title)) },
                onOpenLyrics = {
                    player.loadLyrics()
                    sheet = SHEET_LYRICS
                },
                onOpenQueue = { sheet = SHEET_QUEUE },
                onOpenContext = { routeFor(it)?.let(onNavigate) },
                onGoToAlbum = { onNavigate(AlbumRoute(it)) },
                onGoToArtist = { onNavigate(ArtistDestination(it)) },
                onSkipBack = player::skipBack,
                onSkipForward = player::skipForward,
                onCycleSpeed = player::cycleSpeed,
                onGoToShow = { onNavigate(ShowDestination(it)) },
            ),
            modifier = Modifier.graphicsLayer {
                val p = backProgress
                translationY = with(density) { (p * 96.dp.toPx()) }
                scaleX = 1f - p * 0.08f
                scaleY = 1f - p * 0.08f
                shape = RoundedCornerShape((p * 28).dp)
                clip = p > 0f
            },
        )
    }

    when (sheet) {
        SHEET_LYRICS -> {
            LaunchedEffect(state.track?.id) { player.loadLyrics() }
            LyricsSheet(lyrics = lyrics, positionMs = state.positionMs, onSeek = player::seekTo, onDismiss = {
                sheet =
                    null
            })
        }

        SHEET_QUEUE -> QueueSheet(state = state, onSkipTo = player::skipTo, onDismiss = { sheet = null })
    }
}

/** Android 13+: the media notification needs the notification permission. Asked once. */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private fun NavController.navigateTopLevel(tab: TopLevelDestination) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun routeFor(recent: RecentContext): Any = when (recent.type) {
    PlaybackContextType.Album -> AlbumRoute(recent.refId)
    PlaybackContextType.Artist -> ArtistDestination(recent.refId)
    PlaybackContextType.Playlist -> PlaylistRoute(recent.refId)
    PlaybackContextType.Liked -> LikedRoute
    PlaybackContextType.Songs -> LibraryDestination
}

private fun routeFor(context: PlaybackContext): Any? = when (context.type) {
    PlaybackContextType.Album -> AlbumRoute(context.refId)
    PlaybackContextType.Artist -> ArtistDestination(context.refId)
    PlaybackContextType.Playlist -> PlaylistRoute(context.refId)
    PlaybackContextType.Liked -> LikedRoute
    PlaybackContextType.Songs -> null
}

private const val SHEET_LYRICS = "lyrics"
private const val SHEET_QUEUE = "queue"
