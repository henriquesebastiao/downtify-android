package com.henriquesebastiao.downtify.tv

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.Artwork
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.requestFocusWhenPlaced
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.core.player.QueueEntry
import com.henriquesebastiao.downtify.feature.player.artworkOf
import com.henriquesebastiao.downtify.feature.player.nextFromLabel
import com.henriquesebastiao.downtify.feature.player.playingCoverUrl
import com.henriquesebastiao.downtify.ui.common.PreviewData

/**
 * The queue on a TV, after the TV music apps: an opaque panel down the right of the player, which
 * stays in view beside it. "Queue", then what's playing with a button to skip it, then what the
 * user queued and what comes next from the album or playlist. OK on a song plays it. It opens with
 * focus on the first song to come (or the skip button when nothing comes); Back closes it, as the
 * caller handles. Reordering and removing stay on the phone.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvQueuePanel(state: PlayerState, onSkipTo: (Int) -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val queued = state.upNext.takeWhile { it.queued }
    val rest = state.upNext.drop(queued.size)
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocusWhenPlaced() }
    val queuedTitle = stringResource(R.string.player_queue_next_in_queue)
    val restTitle = nextFromLabel(state.context)
    Box(modifier.fillMaxHeight().width(PanelWidth).background(scheme.surfaceContainerLow)) {
        // Only as far as the focused song needs: "Queue" and "Now playing" stay in view at the top.
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberTvKeepVisibleSpec(KeepVisible)) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = Spacing.xxl - TvRingSpace,
                    end = TvMargin - TvRingSpace,
                    top = Spacing.xxl,
                    bottom = Spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "title") {
                    Text(
                        stringResource(R.string.player_queue),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = TvRingSpace, bottom = Spacing.md).semantics { heading() },
                    )
                }
                item(key = "now-header") { Header(stringResource(R.string.player_queue_now_playing)) }
                item(key = "now") {
                    NowPlaying(
                        state,
                        onNext,
                        if (state.upNext.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
                section("queued", queuedTitle, queued, onSkipTo, firstFocus, first = true)
                section("rest", restTitle, rest, onSkipTo, firstFocus, first = queued.isEmpty())
            }
        }
    }
}

private fun LazyListScope.section(
    key: String,
    title: String,
    entries: List<QueueEntry>,
    onSkipTo: (Int) -> Unit,
    firstFocus: FocusRequester,
    first: Boolean,
) {
    if (entries.isEmpty()) return
    item(key = "$key-header") { Header(title) }
    items(entries, key = { "$key-${it.index}" }) { entry ->
        QueueRow(
            title = entry.title,
            artist = entry.artist,
            coverUrl = artworkOf(entry),
            onClick = { onSkipTo(entry.index) },
            modifier = if (first && entry == entries.first()) Modifier.focusRequester(firstFocus) else Modifier,
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = TvRingSpace, top = Spacing.lg, bottom = Spacing.xs).semantics { heading() },
    )
}

/** What's playing, its title in the accent color, and a button to skip to what's next. */
@Composable
private fun NowPlaying(state: PlayerState, onNext: () -> Unit, nextModifier: Modifier) {
    val track = state.track
    val stream = state.stream
    val episode = state.episode
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        QueueRowContent(
            title = track?.displayTitle ?: stream?.title ?: episode?.title.orEmpty(),
            artist = track?.displayArtist ?: stream?.artist ?: episode?.showName.orEmpty(),
            coverUrl = playingCoverUrl(state),
            current = true,
            modifier = Modifier.weight(1f).padding(TvRingSpace),
        )
        if (state.upNext.isNotEmpty()) {
            TvIconButton(DowntifyIcons.Next, stringResource(R.string.player_next), onNext, nextModifier)
        }
    }
}

/** A song to come: OK plays it. */
@Composable
private fun QueueRow(
    title: String,
    artist: String,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scheme = MaterialTheme.colorScheme
    QueueRowContent(
        title = title,
        artist = artist,
        coverUrl = coverUrl,
        current = false,
        modifier = modifier
            .fillMaxWidth()
            .tvFocusFrame(focused, RowRingShape)
            .clickable(interaction, indication = null, onClickLabel = title, role = Role.Button, onClick = onClick)
            .background(if (focused) scheme.surfaceContainerHighest else scheme.surfaceContainerLow, RowShape),
    )
}

@Composable
private fun QueueRowContent(
    title: String,
    artist: String,
    coverUrl: String?,
    current: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.padding(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Artwork(url = coverUrl, contentDescription = null, shape = CoverShape, modifier = Modifier.size(RowCover))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (current) scheme.primary else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                artist,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val PanelWidth = 340.dp

/** How close to the panel's edges the focused song may come before the list scrolls. */
private val KeepVisible = 96.dp
private val RowCover = 48.dp
private val CoverShape = RoundedCornerShape(6.dp)
private val RowShape = RoundedCornerShape(8.dp)
private val RowRingShape = RoundedCornerShape(8.dp + TvRingSpace)

@Preview(name = "TV", device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_TELEVISION)
@Composable
private fun TvQueuePreview() {
    val tracks = PreviewData.tracks
    DowntifyTheme(darkTheme = true) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            TvQueuePanel(
                state = PlayerState(
                    track = tracks.first(),
                    upNext = tracks.drop(1).mapIndexed { i, t ->
                        QueueEntry(i + 1, t.id, t.displayTitle, t.displayArtist)
                    },
                ),
                onSkipTo = {},
                onNext = {},
            )
        }
    }
}
