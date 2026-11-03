package com.henriquesebastiao.downtify.feature.player

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.CoverTheme
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.MiniPlayerButton
import com.henriquesebastiao.downtify.core.designsystem.component.MiniPlayerCard
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.core.player.PlayingStream
import com.henriquesebastiao.downtify.ui.common.PreviewData

/**
 * Sits above the navigation bar while something is loaded, in the cover's colors; tapping it
 * opens Now Playing. Its buttons are the transport only, as on Now Playing: previous, play/pause
 * and next, or an episode's skips back and forward. Saving a stream to the server lives on Now
 * Playing. [attached] joins it to the floating bar right under it. [modifier] is the card's (the
 * caller sets its margins), [coverModifier] its cover's.
 */
@Composable
fun MiniPlayer(
    state: PlayerState,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onSkipBack: () -> Unit = {},
    onSkipForward: () -> Unit = {},
    attached: Boolean = false,
    coverModifier: Modifier = Modifier,
) {
    val track = state.track
    val episode = state.episode
    val stream = state.stream
    if (track == null && episode == null && stream == null) return
    val coverUrl = playingCoverUrl(state)
    CoverTheme(coverUrl) {
        MiniPlayerCard(
            title = track?.displayTitle ?: stream?.title ?: episode?.title.orEmpty(),
            subtitle = track?.displayArtist ?: stream?.artist ?: episode?.showName.orEmpty(),
            coverUrl = coverUrl,
            onOpen = onOpen,
            openLabel = stringResource(R.string.player_open),
            attached = attached,
            coverModifier = coverModifier,
            modifier = modifier,
        ) {
            if (episode != null) {
                MiniPlayerButton(DowntifyIcons.SkipBack, stringResource(R.string.player_skip_back), onSkipBack)
            } else {
                MiniPlayerButton(DowntifyIcons.Previous, stringResource(R.string.player_previous), onPrevious)
            }
            MiniPlayerButton(
                if (state.isPlaying) DowntifyIcons.Pause else DowntifyIcons.Play,
                stringResource(if (state.isPlaying) R.string.player_pause else R.string.player_play),
                onTogglePlay,
                main = true,
                playing = state.isPlaying,
            )
            if (episode != null) {
                MiniPlayerButton(DowntifyIcons.SkipForward, stringResource(R.string.player_skip_forward), onSkipForward)
            } else {
                MiniPlayerButton(DowntifyIcons.Next, stringResource(R.string.player_next), onNext)
            }
        }
    }
}

@PhonePreviews
@Composable
private fun MiniPlayerPreview() {
    DowntifyTheme {
        Surface {
            MiniPlayer(
                state = PlayerState(
                    track = PreviewData.tracks[1],
                    isPlaying = true,
                    positionMs = 70_000,
                    durationMs = 239_000,
                ),
                onOpen = {},
                onTogglePlay = {},
                modifier = Modifier.padding(Spacing.screen),
            )
        }
    }
}

@PhonePreviews
@Composable
private fun MiniPlayerStreamPreview() {
    DowntifyTheme {
        Surface {
            MiniPlayer(
                state = PlayerState(stream = PlayingStream("id", "A stream with a long title", "Artist", "", "{}")),
                onOpen = {},
                onTogglePlay = {},
                attached = true,
                modifier = Modifier.padding(Spacing.screen),
            )
        }
    }
}
