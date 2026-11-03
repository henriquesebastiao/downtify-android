package com.henriquesebastiao.downtify.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyType
import com.henriquesebastiao.downtify.core.designsystem.theme.ListStyle
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing

/**
 * A content row (song, album, file, download) as a card of a separated list: [leading] (a 64 dp
 * [Artwork] or a track number), a titleMedium title over a bodyLarge [subtitle], an optional
 * tabular [meta] line (a size, a duration) and [trailing] controls (a [CircleButton] "more").
 * [active] is what's playing: the title in bold primary; [dimmed] is something done with (an
 * episode heard), its title in the muted color. Lines are one line, ellipsized; a title or status
 * that must be read whole may take [titleLines] or [subtitleLines]. [extra] goes under them (a
 * progress bar). [leading] is optional: an episode has no cover of its own.
 * [shape] comes from the list's [ListStyle]; the row looks the same pressed or not, but ripples.
 * [compact] tightens it for long lists (the library's songs, with a 56 dp cover). [titleColor] and
 * [subtitleColor] override the colors above, for a row on a container of its own (what's playing).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaListItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    meta: String? = null,
    active: Boolean = false,
    shape: Shape = ListStyle.Separated.shapeOf(0, 1),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    supportingLeading: (@Composable () -> Unit)? = null,
    subtitleLines: Int = 1,
    titleLines: Int = 1,
    dimmed: Boolean = false,
    compact: Boolean = false,
    titleColor: Color = Color.Unspecified,
    subtitleColor: Color = Color.Unspecified,
    extra: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    SegmentedListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = onLongClickLabel,
        shapes = ListItemShapes(shape, shape, shape, shape, shape, shape),
        colors = ListItemDefaults.segmentedColors(containerColor = containerColor),
        contentPadding = PaddingValues(
            start = if (compact) 12.dp else 14.dp,
            end = 10.dp,
            top = if (compact) 12.dp else 14.dp,
            bottom = if (compact) 12.dp else 14.dp,
        ),
        leadingContent = leading,
        supportingContent = if (subtitle != null || meta != null || extra != null) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (subtitle != null) {
                        // On a status of two lines the mark (a check, a badge) stays with the first.
                        val multiline = subtitleLines > 1
                        Row(verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically) {
                            if (supportingLeading != null) {
                                Box(if (multiline) Modifier.padding(top = 4.dp) else Modifier) { supportingLeading() }
                            }
                            Text(
                                subtitle,
                                style = typography.bodyLarge,
                                color = subtitleColor.takeOrElse { scheme.onSurfaceVariant },
                                maxLines = subtitleLines,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (meta != null) {
                        Text(
                            meta,
                            style = DowntifyType.dataSmall,
                            color = subtitleColor.takeOrElse { scheme.onSurfaceVariant },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    extra?.invoke()
                }
            }
        } else {
            null
        },
        trailingContent = trailing?.let { controls ->
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    content = controls,
                )
            }
        },
        modifier = modifier,
    ) {
        Text(
            title,
            style = if (active) typography.titleMediumEmphasized else typography.titleMedium,
            color = when {
                titleColor.isSpecified -> titleColor
                active -> scheme.primary
                dimmed -> scheme.onSurfaceVariant
                else -> Color.Unspecified
            },
            maxLines = titleLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@PhonePreviews
@Composable
private fun MediaListItemPreview() {
    DowntifyTheme {
        Surface {
            Column(
                Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(ListStyle.Separated.gap),
            ) {
                val art: @Composable () -> Unit = {
                    Artwork(url = null, contentDescription = null, modifier = Modifier.size(64.dp))
                }
                MediaListItem(
                    title = "A very long song title that has to stop somewhere",
                    subtitle = "The Artist · The Album",
                    onClick = {},
                    leading = art,
                    active = true,
                    trailing = {
                        PlayingBars()
                        CircleButton(DowntifyIcons.MoreVert, "More", {})
                    },
                )
                MediaListItem(
                    title = "Downloaded album",
                    subtitle = "12 songs",
                    meta = "84.2 MB",
                    onClick = {},
                    leading = {
                        Artwork(
                            url = null,
                            contentDescription = null,
                            shape = CircleShape,
                            modifier = Modifier.size(64.dp),
                        )
                    },
                    trailing = { CircleButton(DowntifyIcons.MoreVert, "More", {}) },
                )
            }
        }
    }
}
