package com.henriquesebastiao.downtify.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.component.Tag
import com.henriquesebastiao.downtify.core.designsystem.component.TagTone
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.ListStyle
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.SleepTimerMode
import com.henriquesebastiao.downtify.core.model.SleepTimerState
import com.henriquesebastiao.downtify.core.model.formatDuration

/**
 * What is left of the sleep timer, in a small pill under the title: a countdown — `12:34` for a
 * timer of minutes — or, for one that waits for the item on, its end when that can't be timed yet.
 * A tap turns the timer off, and there is nothing at all while none is set.
 */
@Composable
fun SleepTimerChip(state: SleepTimerState, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    if (!state.active) return
    val left = state.remainingMs
    val label = if (left != null) {
        stringResource(R.string.sleep_chip_remaining, formatDuration(left / MS_PER_SECOND))
    } else {
        stringResource(endOfLabel(state.mode))
    }
    Tag(
        label = label,
        icon = DowntifyIcons.DarkMode,
        tone = TagTone.OnColor,
        small = true,
        modifier = modifier.clickable(
            onClickLabel = stringResource(R.string.sleep_cancel),
            role = Role.Button,
            onClick = onCancel,
        ),
    )
}

/**
 * The sleep timer's choices: the durations [SleepTimerState.PRESET_MINUTES] offers as chips, then
 * the end of what is playing. The running one is filled in — picking another replaces it — and a
 * running timer can be turned off here too.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SleepTimerOptions(
    mode: SleepTimerMode?,
    onPick: (SleepTimerMode) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            stringResource(R.string.sleep_timer),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = Spacing.sm, bottom = Spacing.xs).semantics { heading() },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SleepTimerState.PRESET_MINUTES.forEach { minutes ->
                FilterChip(
                    selected = (mode as? SleepTimerMode.AfterMinutes)?.minutes == minutes,
                    onClick = { onPick(SleepTimerMode.AfterMinutes(minutes)) },
                    label = { Text(stringResource(R.string.minutes_short, minutes)) },
                    shape = CircleShape,
                )
            }
        }
        val endings = listOf(SleepTimerMode.EndOfTrack, SleepTimerMode.EndOfEpisode)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ListStyle.Segmented.gap)) {
            endings.forEachIndexed { index, ending ->
                val selected = mode == ending
                val shape = ListStyle.Segmented.shapeOf(index, endings.size)
                SegmentedListItem(
                    selected = selected,
                    onClick = { onPick(ending) },
                    shapes = ListItemShapes(shape, shape, shape, shape, shape, shape),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = scheme.surfaceContainerLow,
                        selectedContainerColor = scheme.secondaryContainer,
                    ),
                    trailingContent = { RadioButton(selected = selected, onClick = null) },
                ) {
                    Text(stringResource(endOfLabel(ending)), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (mode != null) {
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.sleep_cancel))
            }
        }
    }
}

/** The choices in a sheet over Now Playing, from its moon button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    state: SleepTimerState,
    onPick: (SleepTimerMode) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        SleepTimerOptions(state.mode, onPick, onCancel)
    }
}

/** "End of track" or "End of episode": the label of an end-of-item choice, and of its countdown. */
private fun endOfLabel(mode: SleepTimerMode?): Int = when (mode) {
    SleepTimerMode.EndOfEpisode -> R.string.sleep_end_of_episode
    else -> R.string.sleep_end_of_track
}

/** A timer's remaining millis, divided by this, are the seconds `formatDuration` shows as `12:34`. */
private const val MS_PER_SECOND = 1000.0

@PhonePreviews
@Composable
private fun SleepTimerOptionsPreview() {
    DowntifyTheme {
        SleepTimerOptions(
            mode = SleepTimerMode.AfterMinutes(30),
            onPick = {},
            onCancel = {},
        )
    }
}

@PhonePreviews
@Composable
private fun SleepTimerOptionsEndOfEpisodePreview() {
    DowntifyTheme {
        SleepTimerOptions(
            mode = SleepTimerMode.EndOfEpisode,
            onPick = {},
            onCancel = {},
        )
    }
}

@PhonePreviews
@Composable
private fun SleepTimerChipPreview() {
    DowntifyTheme {
        SleepTimerChip(
            state = SleepTimerState(
                mode = SleepTimerMode.EndOfTrack,
                positionMs = 120_000,
                durationMs = 239_000,
            ),
            onCancel = {},
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}
