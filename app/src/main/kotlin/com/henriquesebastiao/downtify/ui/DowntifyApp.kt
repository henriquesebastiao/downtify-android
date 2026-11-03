package com.henriquesebastiao.downtify.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
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
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.theme.LocalFloatingNavBar
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.RecentContext
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.core.player.PlayerEvent
import com.henriquesebastiao.downtify.feature.about.AboutRoute
import com.henriquesebastiao.downtify.feature.about.LicenseRoute
import com.henriquesebastiao.downtify.feature.artist.ArtistRoute
import com.henriquesebastiao.downtify.feature.collection.CollectionRoute
import com.henriquesebastiao.downtify.feature.connect.ConnectRoute
import com.henriquesebastiao.downtify.feature.discover.DiscoverRoute
import com.henriquesebastiao.downtify.feature.downloads.DownloadsRoute
import com.henriquesebastiao.downtify.feature.home.HomeRoute
import com.henriquesebastiao.downtify.feature.library.LibraryNavigation
import com.henriquesebastiao.downtify.feature.library.LibraryRoute
import com.henriquesebastiao.downtify.feature.player.ExpandingPlayer
import com.henriquesebastiao.downtify.feature.player.LyricsScreen
import com.henriquesebastiao.downtify.feature.player.MiniPlayer
import com.henriquesebastiao.downtify.feature.player.NowPlayingActions
import com.henriquesebastiao.downtify.feature.player.NowPlayingScreen
import com.henriquesebastiao.downtify.feature.player.PlayerExpansion
import com.henriquesebastiao.downtify.feature.player.PlayerViewModel
import com.henriquesebastiao.downtify.feature.player.QueueActions
import com.henriquesebastiao.downtify.feature.player.QueueScreen
import com.henriquesebastiao.downtify.feature.player.miniPlayerAnchor
import com.henriquesebastiao.downtify.feature.player.playingCoverUrl
import com.henriquesebastiao.downtify.feature.player.rememberPlayerExpansion
import com.henriquesebastiao.downtify.feature.podcasts.PodcastsRoute
import com.henriquesebastiao.downtify.feature.podcasts.ShowRoute
import com.henriquesebastiao.downtify.feature.search.SearchNavigation
import com.henriquesebastiao.downtify.feature.search.SearchRoute
import com.henriquesebastiao.downtify.feature.settings.SettingsRoute
import com.henriquesebastiao.downtify.feature.similar.SimilarRoute as SimilarScreenRoute
import com.henriquesebastiao.downtify.feature.trash.MoveToTrashHost
import com.henriquesebastiao.downtify.feature.trash.MoveToTrashViewModel
import com.henriquesebastiao.downtify.feature.trash.TrashRoute as TrashScreenRoute
import com.henriquesebastiao.downtify.ui.common.CoverUrls
import com.henriquesebastiao.downtify.ui.common.LocalBottomBarsInset
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.navigation.AboutRoute as AboutDestination
import com.henriquesebastiao.downtify.ui.navigation.AlbumRoute
import com.henriquesebastiao.downtify.ui.navigation.AppDrawer
import com.henriquesebastiao.downtify.ui.navigation.ArtistRoute as ArtistDestination
import com.henriquesebastiao.downtify.ui.navigation.DiscoverRoute as DiscoverDestination
import com.henriquesebastiao.downtify.ui.navigation.DownloadsRoute as DownloadsDestination
import com.henriquesebastiao.downtify.ui.navigation.DrawerDestination
import com.henriquesebastiao.downtify.ui.navigation.FloatingNavBar
import com.henriquesebastiao.downtify.ui.navigation.HomeRoute as HomeDestination
import com.henriquesebastiao.downtify.ui.navigation.LibraryRoute as LibraryDestination
import com.henriquesebastiao.downtify.ui.navigation.LicenseRoute as LicenseDestination
import com.henriquesebastiao.downtify.ui.navigation.LikedRoute
import com.henriquesebastiao.downtify.ui.navigation.NavLabel
import com.henriquesebastiao.downtify.ui.navigation.PlaylistRoute
import com.henriquesebastiao.downtify.ui.navigation.PodcastsRoute as PodcastsDestination
import com.henriquesebastiao.downtify.ui.navigation.SearchQueryRoute
import com.henriquesebastiao.downtify.ui.navigation.SearchRoute as SearchDestination
import com.henriquesebastiao.downtify.ui.navigation.SettingsRoute as SettingsDestination
import com.henriquesebastiao.downtify.ui.navigation.ShowRoute as ShowDestination
import com.henriquesebastiao.downtify.ui.navigation.SimilarRoute
import com.henriquesebastiao.downtify.ui.navigation.TopLevelDestination
import com.henriquesebastiao.downtify.ui.navigation.TrashRoute as TrashDestination
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

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
    // Only whether something is loaded: the full state ticks twice a second and would recompose the whole shell.
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
    // Now Playing, open or closed or anywhere between: it grows out of the mini player and shrinks back into it.
    val expansion = rememberPlayerExpansion()
    val scope = rememberCoroutineScope()
    LaunchedEffect(hasMedia) { if (!hasMedia) expansion.snapTo(open = false) }
    // The side menu, opened from Home's menu button.
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var currentTab by rememberSaveable { mutableStateOf(TopLevelDestination.Home) }
    val onSettings = destination?.hasRoute<SettingsDestination>() == true ||
        destination?.hasRoute<AboutDestination>() == true ||
        destination?.hasRoute<LicenseDestination>() == true

    TopLevelDestination.entries.firstOrNull { destination?.hasRoute(it.route::class) == true }?.let { currentTab = it }
    RequestNotificationPermission()

    val openTab: (TopLevelDestination) -> Unit = { tab ->
        if (tab == currentTab) {
            // Re-tapping the open tab closes whatever was opened above it (Discover, an album, a
            // search) and shows the tab's start view. Falls back to a plain switch when the tab
            // has no entry in the stack.
            if (!navController.popBackStack(tab.route, inclusive = false)) navController.navigateTopLevel(tab)
        } else {
            currentTab = tab
            navController.navigateTopLevel(tab)
        }
    }
    val adaptiveLayout = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    val phone = adaptiveLayout in PhoneBars
    // An artist, an album or a playlist takes the phone's whole height: no bar, only the mini player.
    val onDetail = destination?.hasRoute<AlbumRoute>() == true ||
        destination?.hasRoute<PlaylistRoute>() == true ||
        destination?.hasRoute<LikedRoute>() == true ||
        destination?.hasRoute<ArtistDestination>() == true
    // On a phone the bar floats as a pill unless the user picked the full-width one; the rail stays.
    val floatingBar = !onSettings && !onDetail && LocalFloatingNavBar.current && phone
    val noBar = onSettings || floatingBar || (onDetail && phone)
    val miniPlayer = hasMedia && !onSettings

    // The same instance MoveToTrashHost uses: whether this pairing gets the music trash.
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
            NavigationSuiteScaffold(
                navigationSuiteItems = {
                    TopLevelDestination.entries.forEach { tab ->
                        val selected = tab == currentTab
                        item(
                            selected = selected,
                            onClick = { openTab(tab) },
                            icon = {
                                Icon(
                                    painterResource(if (selected) tab.selectedIcon else tab.icon),
                                    contentDescription = null,
                                )
                            },
                            label = { NavLabel(stringResource(tab.label)) },
                        )
                    }
                },
                layoutType = if (noBar) NavigationSuiteType.None else adaptiveLayout,
            ) {
                // The content runs on behind the mini player and the floating bar, as in PixelPlayer:
                // they float over its end, and the screens pad their lists by what they cover.
                Box(Modifier.fillMaxSize()) {
                    // What sits at the bottom clears the system's navigation bar, unless the full-width bar
                    // under it already does; the screens above then leave no room for it themselves.
                    val fullWidthBar = !noBar && phone
                    val bottomStack = (miniPlayer || floatingBar) && !fullWidthBar
                    val content = if (bottomStack) {
                        Modifier.consumeWindowInsets(
                            WindowInsets.navigationBars,
                        )
                    } else {
                        Modifier
                    }
                    var stackHeight by remember { mutableStateOf(0.dp) }
                    val covered = if (miniPlayer || floatingBar) stackHeight else 0.dp
                    CompositionLocalProvider(LocalBottomBarsInset provides covered) {
                        Box(Modifier.fillMaxSize().then(content)) {
                            MoveToTrashHost(onMessage = { text -> scope.launch { snackbar.showSnackbar(text) } }) {
                                AppNavHost(navController, onOpenMenu = { scope.launch { drawerState.open() } })
                            }
                        }
                    }
                    if (miniPlayer || floatingBar) {
                        val density = LocalDensity.current
                        Column(
                            Modifier
                                .align(Alignment.BottomCenter)
                                // Measured with the system bar's padding: that's covered too.
                                .onSizeChanged { stackHeight = with(density) { it.height.toDp() } }
                                .then(if (fullWidthBar) Modifier else Modifier.navigationBarsPadding()),
                        ) {
                            if (miniPlayer) {
                                MiniPlayerHost(
                                    player = player,
                                    expansion = expansion,
                                    onOpen = { scope.launch { expansion.expand() } },
                                    attached = floatingBar,
                                )
                            }
                            if (floatingBar) FloatingNavBar(currentTab, openTab, attached = miniPlayer)
                        }
                    }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = covered))
                }
            }
            if (hasMedia) {
                NowPlayingOverlay(
                    expansion = expansion,
                    player = player,
                    attached = floatingBar,
                    onOpen = { scope.launch { expansion.expand() } },
                    onClose = { scope.launch { expansion.collapse() } },
                    onNavigate = { route ->
                        scope.launch { expansion.collapse() }
                        navController.navigate(route) { launchSingleTop = true }
                    },
                )
            }
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, onOpenMenu: () -> Unit) {
    val toAlbum: (String) -> Unit = { navController.navigate(AlbumRoute(it)) }
    val toArtist: (String) -> Unit = { navController.navigate(ArtistDestination(it)) }
    val toPlaylist: (String) -> Unit = { navController.navigate(PlaylistRoute(it)) }
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
            AboutRoute(
                onBack = navController::popBackStack,
                onOpenLicense = { navController.navigate(LicenseDestination) },
            )
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
 * The mini player collects the ticking state itself, so only it recomposes as the song plays. It's
 * where Now Playing grows out of: a drag up on it opens Now Playing under the finger.
 */
@Composable
private fun MiniPlayerHost(player: PlayerViewModel, expansion: PlayerExpansion, onOpen: () -> Unit, attached: Boolean) {
    val state by player.state.collectAsStateWithLifecycle()
    MiniPlayer(
        state = state,
        onOpen = onOpen,
        onTogglePlay = player::togglePlayPause,
        onPrevious = player::previous,
        onNext = player::next,
        onSkipBack = player::skipBack,
        onSkipForward = player::skipForward,
        attached = attached,
        coverModifier = Modifier.onGloballyPositioned { expansion.miniCover = it },
        modifier = Modifier
            .padding(horizontal = Spacing.screen, vertical = Spacing.xs)
            .miniPlayerAnchor(expansion),
    )
}

/**
 * Now Playing: it grows out of the mini player ([attached] to the floating bar or not) and shrinks
 * back into it, under a finger, a tap or predictive back. Closed, only the light part of it stays
 * composed, off screen (see [ExpandingPlayer]); Now Playing itself is composed while it shows.
 */
@Composable
private fun NowPlayingOverlay(
    expansion: PlayerExpansion,
    player: PlayerViewModel,
    attached: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onNavigate: (Any) -> Unit,
) {
    val state by player.state.collectAsStateWithLifecycle()
    val isLiked by player.isLiked.collectAsStateWithLifecycle()
    val streamJob by player.streamJob.collectAsStateWithLifecycle()
    val streamTrack by player.streamTrack.collectAsStateWithLifecycle()
    val serverName by player.serverName.collectAsStateWithLifecycle()
    val lyrics by player.lyrics.collectAsStateWithLifecycle()
    val lyricsOffset by player.lyricsOffset.collectAsStateWithLifecycle()
    var lyricsOpen by rememberSaveable { mutableStateOf(false) }
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    val open = expansion.expanded

    LaunchedEffect(open) {
        if (!open) {
            queueOpen = false
            lyricsOpen = false
        }
    }
    BackHandler(enabled = open && queueOpen) { queueOpen = false }
    BackHandler(enabled = open && lyricsOpen) { lyricsOpen = false }
    // Back folds the player towards the mini player as the gesture goes, then closes it or springs back open.
    PredictiveBackHandler(enabled = open && !lyricsOpen && !queueOpen) { progress ->
        try {
            expansion.foldBack { fold -> progress.collect { fold(it.progress) } }
            onClose()
        } catch (e: CancellationException) {
            onOpen()
            throw e
        }
    }

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

    ExpandingPlayer(
        expansion = expansion,
        coverUrl = playingCoverUrl(state),
        largeCoverUrl = playingCoverUrl(state, ServerUrls.COVER_LARGE),
        attached = attached,
        dragEnabled = !lyricsOpen && !queueOpen,
        mini = { coverModifier ->
            MiniPlayer(
                state = state,
                onOpen = onOpen,
                onTogglePlay = player::togglePlayPause,
                onPrevious = player::previous,
                onNext = player::next,
                onSkipBack = player::skipBack,
                onSkipForward = player::skipForward,
                attached = attached,
                coverModifier = coverModifier,
            )
        },
        full = { coverModifier ->
            NowPlayingScreen(
                state = state,
                isLiked = isLiked,
                serverName = serverName,
                downloadJob = streamJob,
                hasLibraryCopy = streamTrack != null,
                libraryTrack = streamTrack,
                actions = controls,
                coverModifier = coverModifier,
            )
        },
    )

    // The queue unfolds over Now Playing from its queue button, as in Spotify.
    AnimatedVisibility(
        visible = open && queueOpen,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
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
        )
    }

    // The lyrics, too, rise over Now Playing from its lyrics button.
    AnimatedVisibility(
        visible = open && lyricsOpen,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        LaunchedEffect(state.track?.id, streamTrack?.id) { player.loadLyrics() }
        LyricsScreen(
            lyrics = lyrics,
            offsetMs = lyricsOffset,
            state = state,
            controls = controls,
            onBack = { lyricsOpen = false },
        )
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

/** The layouts NavigationSuiteScaffold picks for a phone: where the floating bar takes over. */
private val PhoneBars = setOf(
    NavigationSuiteType.NavigationBar,
    NavigationSuiteType.ShortNavigationBarCompact,
    NavigationSuiteType.ShortNavigationBarMedium,
)
