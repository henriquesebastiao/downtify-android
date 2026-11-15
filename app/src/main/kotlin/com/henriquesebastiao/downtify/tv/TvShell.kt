package com.henriquesebastiao.downtify.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
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
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.focusTrap
import com.henriquesebastiao.downtify.core.designsystem.component.requestFocusWhenPlaced
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.RecentContext
import com.henriquesebastiao.downtify.core.player.PlayerEvent
import com.henriquesebastiao.downtify.feature.about.AboutRoute
import com.henriquesebastiao.downtify.feature.about.LicenseRoute
import com.henriquesebastiao.downtify.feature.artist.ArtistRoute
import com.henriquesebastiao.downtify.feature.collection.CollectionRoute
import com.henriquesebastiao.downtify.feature.discover.DiscoverRoute
import com.henriquesebastiao.downtify.feature.downloads.DownloadsRoute
import com.henriquesebastiao.downtify.feature.home.HomeRoute
import com.henriquesebastiao.downtify.feature.library.LibraryNavigation
import com.henriquesebastiao.downtify.feature.player.LyricsScreen
import com.henriquesebastiao.downtify.feature.player.NowPlayingActions
import com.henriquesebastiao.downtify.feature.player.NowPlayingScreen
import com.henriquesebastiao.downtify.feature.player.PlayerViewModel
import com.henriquesebastiao.downtify.feature.player.QueueActions
import com.henriquesebastiao.downtify.feature.player.QueueScreen
import com.henriquesebastiao.downtify.feature.podcasts.PodcastsRoute
import com.henriquesebastiao.downtify.feature.podcasts.ShowRoute
import com.henriquesebastiao.downtify.feature.search.SearchNavigation
import com.henriquesebastiao.downtify.feature.search.SearchRoute
import com.henriquesebastiao.downtify.feature.settings.SettingsRoute
import com.henriquesebastiao.downtify.feature.similar.SimilarRoute as SimilarScreenRoute
import com.henriquesebastiao.downtify.feature.trash.MoveToTrashHost
import com.henriquesebastiao.downtify.feature.trash.MoveToTrashViewModel
import com.henriquesebastiao.downtify.feature.trash.TrashRoute as TrashScreenRoute
import com.henriquesebastiao.downtify.ui.navigation.AboutRoute as AboutDestination
import com.henriquesebastiao.downtify.ui.navigation.AlbumRoute
import com.henriquesebastiao.downtify.ui.navigation.AppDrawer
import com.henriquesebastiao.downtify.ui.navigation.ArtistRoute as ArtistDestination
import com.henriquesebastiao.downtify.ui.navigation.DiscoverRoute as DiscoverDestination
import com.henriquesebastiao.downtify.ui.navigation.DownloadsRoute as DownloadsDestination
import com.henriquesebastiao.downtify.ui.navigation.DrawerDestination
import com.henriquesebastiao.downtify.ui.navigation.HomeRoute as HomeDestination
import com.henriquesebastiao.downtify.ui.navigation.LibraryRoute as LibraryDestination
import com.henriquesebastiao.downtify.ui.navigation.LicenseRoute as LicenseDestination
import com.henriquesebastiao.downtify.ui.navigation.LikedRoute
import com.henriquesebastiao.downtify.ui.navigation.PlaylistRoute
import com.henriquesebastiao.downtify.ui.navigation.PodcastsRoute as PodcastsDestination
import com.henriquesebastiao.downtify.ui.navigation.SearchQueryRoute
import com.henriquesebastiao.downtify.ui.navigation.SearchRoute as SearchDestination
import com.henriquesebastiao.downtify.ui.navigation.SettingsRoute as SettingsDestination
import com.henriquesebastiao.downtify.ui.navigation.ShowRoute as ShowDestination
import com.henriquesebastiao.downtify.ui.navigation.SimilarRoute
import com.henriquesebastiao.downtify.ui.navigation.TopLevelDestination
import com.henriquesebastiao.downtify.ui.navigation.TrashRoute as TrashDestination
import kotlinx.coroutines.launch

/*
 * The app on a TV: its own shell over the same ViewModels, repositories and player as the phone's
 * (see DowntifyApp's MainShell, which this file leaves alone). A top bar instead of the
 * navigation bar or rail, and the player as a full screen instead of the mini player that grows
 * into Now Playing. The library has a TV screen of its own; the other screens are still the
 * phone's, driven by the remote, until they get theirs.
 */

/** The TV's main shell, once paired. */
@Composable
fun TvShell() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val player: PlayerViewModel = hiltViewModel()
    val hasMedia by player.hasMedia.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val addedToQueue = stringResource(R.string.track_added_to_queue)
    LaunchedEffect(player) {
        player.events.collect { event ->
            when (event) {
                is PlayerEvent.AddedToQueue -> {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(addedToQueue)
                }
            }
        }
    }
    val scope = rememberCoroutineScope()
    var currentTab by rememberSaveable { mutableStateOf(TopLevelDestination.Home) }
    TopLevelDestination.entries.firstOrNull { destination?.hasRoute(it.route::class) == true }?.let { currentTab = it }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(hasMedia) { if (!hasMedia) playerOpen = false }

    // The remote's focus starts on the selected tab; it comes back to what's playing when the player closes.
    val tabFocus = remember { FocusRequester() }
    val nowPlayingFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { tabFocus.requestFocusWhenPlaced() }

    val openTab: (TopLevelDestination) -> Unit = { tab ->
        currentTab = tab
        if (!navController.popBackStack(tab.route, inclusive = false)) navController.navigateTopLevel(tab)
    }
    // Home's menu button opens the phone's side menu (Downloads, the trash), which works with a remote.
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val trash: MoveToTrashViewModel = hiltViewModel()
    val trashOn by trash.canManage.collectAsStateWithLifecycle()
    AppDrawer(
        drawerState = drawerState,
        selected = DrawerDestination.entries.firstOrNull { destination?.hasRoute(it.route::class) == true },
        onSelect = { item ->
            scope.launch { drawerState.close() }
            navController.navigate(item.route) { launchSingleTop = true }
        },
        destinations = DrawerDestination.entries.filter { trashOn || !it.trashOnly },
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TvTopBar(
                    selected = currentTab,
                    onSelect = openTab,
                    onSettings = { navController.navigate(SettingsDestination) { launchSingleTop = true } },
                    selectedFocus = tabFocus,
                    nowPlaying = {
                        val state by player.state.collectAsStateWithLifecycle()
                        TvNowPlayingCard(
                            state = state,
                            onOpen = { playerOpen = true },
                            modifier = Modifier.focusRequester(nowPlayingFocus),
                        )
                    },
                )
                Box(
                    Modifier
                        .weight(1f)
                        // Up out of the page goes to the selected tab, not to whatever top-bar item is nearest.
                        .focusProperties {
                            onExit = { if (requestedFocusDirection == FocusDirection.Up) tabFocus.requestFocus() }
                        }
                        .focusGroup(),
                ) {
                    MoveToTrashHost(onMessage = { text -> scope.launch { snackbar.showSnackbar(text) } }) {
                        TvNavHost(navController, onOpenMenu = { scope.launch { drawerState.open() } })
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.xl))
            if (hasMedia) {
                TvPlayer(
                    open = playerOpen,
                    player = player,
                    onClose = { playerOpen = false },
                    onClosed = { scope.launch { nowPlayingFocus.requestFocusWhenPlaced() } },
                    onNavigate = { route ->
                        playerOpen = false
                        navController.navigate(route) { launchSingleTop = true }
                    },
                )
            }
        }
    }
}

@Composable
private fun TvNavHost(navController: NavHostController, onOpenMenu: () -> Unit) {
    val toAlbum: (String) -> Unit = { navController.navigate(AlbumRoute(it)) }
    val toArtist: (String) -> Unit = { navController.navigate(ArtistDestination(it)) }
    val toPlaylist: (String) -> Unit = { navController.navigate(PlaylistRoute(it)) }
    val search = SearchNavigation(
        onAlbum = toAlbum,
        onArtist = toArtist,
        onPlaylist = toPlaylist,
        onSimilar = { artist, title -> navController.navigate(SimilarRoute(artist, title)) },
        onOpenLink = { navController.navigate(SearchQueryRoute(it)) },
    )
    NavHost(navController = navController, startDestination = HomeDestination) {
        composable<HomeDestination> {
            HomeRoute(
                onOpenMenu = onOpenMenu,
                onOpenSettings = { navController.navigate(SettingsDestination) },
                onOpenRecent = { navController.navigate(routeFor(it)) },
                onOpenAlbum = toAlbum,
                onSeeAll = { navController.navigateTopLevel(TopLevelDestination.Library) },
                onOpenDiscover = { navController.navigate(DiscoverDestination) },
                onOpenPodcasts = { navController.navigate(PodcastsDestination) },
                onOpenSimilar = { navController.navigate(SimilarRoute()) },
            )
        }
        composable<SearchDestination> { SearchRoute(search) }
        composable<SearchQueryRoute> { SearchRoute(search) }
        composable<SimilarRoute> { SimilarScreenRoute(onBack = navController::popBackStack) }
        composable<DiscoverDestination> {
            DiscoverRoute(
                onBack = navController::popBackStack,
                onOpenInSearch = { navController.navigate(SearchQueryRoute(it)) },
            )
        }
        composable<PodcastsDestination> {
            PodcastsRoute(onBack = navController::popBackStack, onShow = {
                navController.navigate(ShowDestination(it))
            })
        }
        composable<ShowDestination> { ShowRoute(onBack = navController::popBackStack) }
        composable<LibraryDestination> {
            TvLibraryRoute(
                LibraryNavigation(
                    onSearch = { navController.navigateTopLevel(TopLevelDestination.Search) },
                    onAlbum = toAlbum,
                    onArtist = toArtist,
                    onPlaylist = toPlaylist,
                    onLiked = { navController.navigate(LikedRoute) },
                    onSimilar = { artist, title -> navController.navigate(SimilarRoute(artist, title)) },
                ),
            )
        }
        composable<DownloadsDestination> {
            DownloadsRoute(
                onBack = navController::popBackStack,
                onOpen = { item ->
                    when (item.type) {
                        PlaybackContextType.Album -> toAlbum(item.refId)
                        PlaybackContextType.Playlist -> toPlaylist(item.refId)
                        else -> navController.navigate(LikedRoute)
                    }
                },
            )
        }
        composable<TrashDestination> { TrashScreenRoute(onBack = navController::popBackStack) }
        composable<SettingsDestination> {
            SettingsRoute(
                onBack = navController::popBackStack,
                onOpenAbout = { navController.navigate(AboutDestination) },
            )
        }
        composable<AboutDestination> {
            AboutRoute(onBack = navController::popBackStack, onOpenLicense = {
                navController.navigate(LicenseDestination)
            })
        }
        composable<LicenseDestination> { LicenseRoute(onBack = navController::popBackStack) }
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

/**
 * The player over the whole TV while [open]: the phone's Now Playing, with the queue and the lyrics
 * rising over it. Focus lands on play/pause and stays inside; Back closes the queue or the lyrics,
 * then the player, and [onClosed] runs once it has gone (focus goes back to what's playing).
 */
@Composable
private fun TvPlayer(
    open: Boolean,
    player: PlayerViewModel,
    onClose: () -> Unit,
    onClosed: () -> Unit,
    onNavigate: (Any) -> Unit,
) {
    var lyricsOpen by rememberSaveable { mutableStateOf(false) }
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (!open) {
            queueOpen = false
            lyricsOpen = false
        }
    }
    BackHandler(enabled = open && queueOpen) { queueOpen = false }
    BackHandler(enabled = open && lyricsOpen) { lyricsOpen = false }
    BackHandler(enabled = open && !queueOpen && !lyricsOpen, onBack = onClose)

    val controls = remember(player, onClose, onNavigate) {
        NowPlayingActions(
            onCollapse = onClose,
            onTogglePlay = player::togglePlayPause,
            onNext = player::next,
            onPrevious = player::previous,
            onSeek = player::seekTo,
            onToggleShuffle = player::toggleShuffle,
            onCycleRepeat = player::cycleRepeat,
            onToggleLike = player::toggleLike,
            onDownloadStream = player::downloadStream,
            onShiftLyrics = player::shiftLyrics,
            onResetLyrics = player::resetLyricsOffset,
            onOpenLyrics = {
                player.loadLyrics()
                lyricsOpen = true
            },
            onOpenQueue = { queueOpen = true },
            onOpenContext = { routeFor(it)?.let(onNavigate) },
            onGoToArtist = { onNavigate(ArtistDestination(it)) },
            onSkipBack = player::skipBack,
            onSkipForward = player::skipForward,
            onCycleSpeed = player::cycleSpeed,
            onGoToShow = { onNavigate(ShowDestination(it)) },
        )
    }

    AnimatedVisibility(visible = open, enter = fadeIn(), exit = fadeOut()) {
        val state by player.state.collectAsStateWithLifecycle()
        val isLiked by player.isLiked.collectAsStateWithLifecycle()
        val streamJob by player.streamJob.collectAsStateWithLifecycle()
        val streamTrack by player.streamTrack.collectAsStateWithLifecycle()
        val serverName by player.serverName.collectAsStateWithLifecycle()
        val playFocus = remember { FocusRequester() }
        val onTop = !lyricsOpen && !queueOpen
        LaunchedEffect(onTop) { if (onTop) playFocus.requestFocusWhenPlaced() }
        // Once the fade has run and the player is gone, not when it starts to close.
        DisposableEffect(Unit) { onDispose(onClosed) }
        NowPlayingScreen(
            state = state,
            isLiked = isLiked,
            serverName = serverName,
            downloadJob = streamJob,
            hasLibraryCopy = streamTrack != null,
            libraryTrack = streamTrack,
            actions = controls,
            playFocus = playFocus,
            modifier = Modifier.focusTrap(onTop),
        )
    }

    AnimatedVisibility(
        visible = open && queueOpen,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        val state by player.state.collectAsStateWithLifecycle()
        val queueFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { queueFocus.requestFocusWhenPlaced() }
        QueueScreen(
            state = state,
            actions = QueueActions(
                onClose = { queueOpen = false },
                onSkipTo = player::skipTo,
                onRemove = player::removeFromQueue,
                onClearQueued = player::clearQueued,
                onMove = player::moveInQueue,
            ),
            controls = controls,
            modifier = Modifier.focusRequester(queueFocus).focusTrap(queueOpen),
        )
    }

    AnimatedVisibility(
        visible = open && lyricsOpen,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        val state by player.state.collectAsStateWithLifecycle()
        val streamTrack by player.streamTrack.collectAsStateWithLifecycle()
        val lyrics by player.lyrics.collectAsStateWithLifecycle()
        val lyricsOffset by player.lyricsOffset.collectAsStateWithLifecycle()
        LaunchedEffect(state.track?.id, streamTrack?.id) { player.loadLyrics() }
        val lyricsFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { lyricsFocus.requestFocusWhenPlaced() }
        LyricsScreen(
            lyrics = lyrics,
            offsetMs = lyricsOffset,
            state = state,
            controls = controls,
            onBack = { lyricsOpen = false },
            modifier = Modifier.focusRequester(lyricsFocus).focusTrap(lyricsOpen),
        )
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
