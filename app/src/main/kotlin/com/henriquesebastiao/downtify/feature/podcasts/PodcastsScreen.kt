package com.henriquesebastiao.downtify.feature.podcasts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.EmptyState
import com.henriquesebastiao.downtify.core.designsystem.component.PageHeader
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.ListStyle
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.PodcastShow
import com.henriquesebastiao.downtify.ui.common.CoverItem
import com.henriquesebastiao.downtify.ui.common.LoadError
import com.henriquesebastiao.downtify.ui.common.plusBottomBars

@Composable
fun PodcastsRoute(
    onBack: () -> Unit,
    onShow: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PodcastsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PodcastsScreen(state, onBack, onShow, viewModel::refresh, modifier)
}

/** The shows the server follows, newest subscription first as the server lists them. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PodcastsScreen(
    state: PodcastsUiState,
    onBack: () -> Unit,
    onShow: (Long) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            PageHeader(
                title = stringResource(R.string.podcasts_title),
                onBack = onBack,
                backLabel = stringResource(R.string.action_back),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }

                state.shows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val error = state.error
                    if (error == null) {
                        EmptyState(
                            icon = DowntifyIcons.Podcasts,
                            title = stringResource(R.string.podcasts_empty_title),
                            body = stringResource(R.string.podcasts_empty_body),
                            hero = true,
                        )
                    } else {
                        EmptyState(
                            icon = DowntifyIcons.Podcasts,
                            title = stringResource(errorTitle(error)),
                            body = stringResource(R.string.podcasts_pull_to_retry),
                        )
                    }
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm).plusBottomBars(),
                    verticalArrangement = Arrangement.spacedBy(ListStyle.Separated.gap),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.shows, key = { it.id }) { show ->
                        CoverItem(
                            title = show.name,
                            subtitle = subtitle(show),
                            coverUrl = show.artworkUrl.ifBlank { null },
                            onClick = { onShow(show.id) },
                            placeholderIcon = DowntifyIcons.Podcasts,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun subtitle(show: PodcastShow): String {
    val episodes = pluralStringResource(R.plurals.podcasts_episodes, show.episodeCount, show.episodeCount)
    return if (show.author.isBlank()) episodes else stringResource(R.string.album_subtitle, show.author, episodes)
}

internal fun errorTitle(error: LoadError): Int = when (error) {
    LoadError.Unreachable -> R.string.load_error_unreachable
    LoadError.Failed -> R.string.load_error_failed
}

@PhonePreviews
@Composable
private fun PodcastsPreview() {
    DowntifyTheme {
        PodcastsScreen(
            state = PodcastsUiState(
                loading = false,
                shows = listOf(
                    PodcastShow(1, "Radiolab", "WNYC Studios", "", "", 671, 3),
                    PodcastShow(2, "99% Invisible", "Roman Mars", "", "", 580, 1),
                ),
            ),
            onBack = {},
            onShow = {},
            onRefresh = {},
        )
    }
}

@PhonePreviews
@Composable
private fun PodcastsEmptyPreview() {
    DowntifyTheme { PodcastsScreen(PodcastsUiState(loading = false), onBack = {}, onShow = {}, onRefresh = {}) }
}
