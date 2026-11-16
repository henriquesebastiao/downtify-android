package com.henriquesebastiao.downtify.tv

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.Artwork
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.PlayingBars
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.feature.player.playingCoverUrl
import com.henriquesebastiao.downtify.ui.common.PreviewData
import com.henriquesebastiao.downtify.ui.navigation.TopLevelDestination

/**
 * The TV's top bar, after the TV music apps: what's playing on the left (it opens the player),
 * the three tabs in the middle, settings on the right. The tabs stay centered whatever the sides
 * hold. [selectedFocus] is the selected tab's, where the remote's focus starts.
 */
@Composable
fun TvTopBar(
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    selectedFocus: FocusRequester? = null,
    nowPlaying: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = TvMargin, vertical = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { nowPlaying() }
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            TopLevelDestination.entries.forEach { tab ->
                val isSelected = tab == selected
                TvTab(
                    label = stringResource(tab.label),
                    icon = if (isSelected) tab.selectedIcon else tab.icon,
                    selected = isSelected,
                    onClick = { onSelect(tab) },
                    modifier = if (isSelected && selectedFocus != null) {
                        Modifier.focusRequester(selectedFocus)
                    } else {
                        Modifier
                    },
                )
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            TvIconButton(
                DowntifyIcons.Settings,
                stringResource(R.string.settings_title),
                onSettings,
                size = BarButtonSize,
                iconSize = TabIcon,
            )
        }
    }
}

/** A tab: its icon and name, bright when selected, dimmed otherwise; ringed when focused. */
@Composable
private fun TvTab(label: String, icon: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val color = if (selected || focused) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier
            .tvFocusFrame(focused, CircleShape)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(TabIcon))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = TabText),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = color,
            maxLines = 1,
        )
    }
}

/** A round icon button with the TV's ring: [size] across, its icon [iconSize]. */
@Suppress("LongParameterList")
@Composable
internal fun TvIconButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = TvIconButtonSize,
    iconSize: Dp = ButtonIcon,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier
            .tvFocusFrame(focused, CircleShape)
            .clickable(interaction, indication = null, onClickLabel = label, role = Role.Button, onClick = onClick)
            .size(size),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * What's playing, as a card as wide as the bar's side leaves (up to 300 dp): the cover, the title
 * over the artist and, while it plays, the playing bars. OK opens the player. Shows nothing while nothing is loaded.
 */
@Composable
fun TvNowPlayingCard(state: PlayerState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val track = state.track
    val episode = state.episode
    val stream = state.stream
    if (track == null && episode == null && stream == null) return
    val title = track?.displayTitle ?: stream?.title ?: episode?.title.orEmpty()
    val subtitle = track?.displayArtist ?: stream?.artist ?: episode?.showName.orEmpty()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val open = stringResource(R.string.player_open)
    Row(
        modifier
            .widthIn(max = NowPlayingMaxWidth)
            .fillMaxWidth()
            .tvFocusFrame(focused, NowPlayingRingShape)
            .clickable(interaction, indication = null, onClickLabel = open, role = Role.Button, onClick = onOpen)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, NowPlayingShape),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Artwork(
            url = playingCoverUrl(state),
            contentDescription = null,
            shape = NowPlayingShape,
            placeholderIcon = if (episode != null) DowntifyIcons.Podcasts else DowntifyIcons.Song,
            modifier = Modifier.size(NowPlayingCover),
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = CardTitleText),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = CardSubtitleText),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // The bars say it's playing, not just loaded, as the TV music apps do.
        Box(Modifier.padding(end = Spacing.md).size(BarsSize), contentAlignment = Alignment.Center) {
            if (state.isPlaying) PlayingBars(Modifier.size(BarsSize))
        }
    }
}

/** The side margins of the TV screens: clear of the edges a TV may crop (overscan). */
internal val TvMargin = 48.dp
internal val TvIconButtonSize = 40.dp
private val ButtonIcon = 20.dp

// The top bar sized as the TV music apps size theirs: small, so the page below gets the room.
private val TabIcon = 16.dp
private val TabText = 13.sp
private val BarButtonSize = 36.dp
private val CardTitleText = 12.sp
private val CardSubtitleText = 11.sp
private val NowPlayingMaxWidth = 280.dp
private val NowPlayingCover = 36.dp
private val BarsSize = 14.dp
private val NowPlayingShape = RoundedCornerShape(8.dp)
private val NowPlayingRingShape = RoundedCornerShape(8.dp + TvRingSpace)

@Preview(name = "TV", device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_TELEVISION)
@Composable
private fun TvTopBarPreview() {
    DowntifyTheme(darkTheme = true) {
        Surface {
            TvTopBar(
                selected = TopLevelDestination.Library,
                onSelect = {},
                onSettings = {},
                nowPlaying = {
                    TvNowPlayingCard(PlayerState(track = PreviewData.tracks[1], isPlaying = true), onOpen = {})
                },
            )
        }
    }
}
