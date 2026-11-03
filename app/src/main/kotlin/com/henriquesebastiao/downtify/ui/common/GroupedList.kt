package com.henriquesebastiao.downtify.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.henriquesebastiao.downtify.core.designsystem.component.ChoiceGroup
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.IconAvatar
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.ListStyle
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.designsystem.theme.withDataValues

/** What tapping a [GroupedRow] does, and what it shows at the end. */
sealed interface RowAction {
    /** Opens something; [trailingIcon] hints at what (a chevron for a screen, "open in new" for a link). */
    data class Click(val onClick: () -> Unit, @DrawableRes val trailingIcon: Int? = null) : RowAction

    /** A switch: the whole row toggles it. */
    data class Toggle(val checked: Boolean, val onCheckedChange: (Boolean) -> Unit) : RowAction

    /** A control the row holds under its text, as wide as the row (a button group); the row does nothing. */
    data class Control(val content: @Composable () -> Unit) : RowAction
}

/**
 * One row of a [GroupedList]. [summaryValues] are the parts of [summary] that are technical values
 * (a size, a version), in tabular figures. A [destructive] row (unpair) is in the error colors.
 */
data class GroupedRow(
    val title: String,
    val action: RowAction,
    val summary: String? = null,
    @DrawableRes val icon: Int? = null,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val summaryValues: List<String> = emptyList(),
)

/**
 * A group of rows as Material 3 Expressive draws a grouped list: each row its own surface, the ends
 * of the group with large outer corners, a small gap between rows. [style] sets the corners and the
 * gap: segmented (settings, About), separated or compact. Rows never change shape or tint. A row's
 * icon sits in a tonal circle ([IconAvatar]).
 */
@Composable
fun GroupedList(rows: List<GroupedRow>, modifier: Modifier = Modifier, style: ListStyle = ListStyle.Segmented) {
    Column(
        modifier.padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(style.gap),
    ) {
        rows.forEachIndexed { index, row -> GroupedListItem(row, index, rows.size, style = style) }
    }
}

/** A row that is the [index]th of [count] in its group, which decides its corners. */
@Composable
fun GroupedListItem(
    row: GroupedRow,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    style: ListStyle = ListStyle.Segmented,
) {
    // A row looks the same on, off, pressed or focused: the stock item rounds a checked row all the
    // way and tints it, which made the switches stand out from the rest of the group.
    val shape = style.shapeOf(index, count)
    val shapes = ListItemShapes(shape, shape, shape, shape, shape, shape)
    val scheme = MaterialTheme.colorScheme
    val colors = ListItemDefaults.segmentedColors(
        containerColor = scheme.surfaceContainer,
        selectedContainerColor = scheme.surfaceContainer,
        selectedContentColor = scheme.onSurface,
        selectedLeadingContentColor = scheme.onSurfaceVariant,
        selectedTrailingContentColor = scheme.onSurfaceVariant,
        selectedSupportingContentColor = scheme.onSurfaceVariant,
    )
    val leading: @Composable (() -> Unit)? = row.icon?.let { icon ->
        {
            IconAvatar(
                icon,
                container = if (row.destructive) scheme.errorContainer else scheme.primaryContainer,
                content = if (row.destructive) scheme.onErrorContainer else scheme.onPrimaryContainer,
                modifier = if (row.enabled) Modifier else Modifier.alpha(DISABLED_ALPHA),
            )
        }
    }
    val supporting: @Composable (() -> Unit)? = row.summary?.let { summary ->
        { Text(withDataValues(summary, row.summaryValues)) }
    }
    val title: @Composable () -> Unit = {
        Text(row.title, color = if (row.destructive) scheme.error else Color.Unspecified)
    }
    when (val action = row.action) {
        is RowAction.Click -> SegmentedListItem(
            onClick = action.onClick,
            shapes = shapes,
            modifier = modifier,
            enabled = row.enabled,
            leadingContent = leading,
            trailingContent = action.trailingIcon?.let { icon ->
                {
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            supportingContent = supporting,
            colors = colors,
            content = title,
        )

        is RowAction.Toggle -> SegmentedListItem(
            checked = action.checked,
            onCheckedChange = action.onCheckedChange,
            shapes = shapes,
            modifier = modifier,
            enabled = row.enabled,
            leadingContent = leading,
            trailingContent = { Switch(checked = action.checked, onCheckedChange = null, enabled = row.enabled) },
            supportingContent = supporting,
            colors = colors,
            content = title,
        )

        is RowAction.Control -> Surface(shape = shape, color = scheme.surfaceContainer, modifier = modifier) {
            Column {
                SegmentedListItem(
                    shapes = shapes,
                    enabled = row.enabled,
                    leadingContent = leading,
                    supportingContent = supporting,
                    colors = colors,
                    content = title,
                )
                Box(Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg)) { action.content() }
            }
        }
    }
}

/** A group's title: titleMedium in the accent color, inset a little from the group's edge. */
@Composable
fun GroupTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = Spacing.screen + Spacing.md, end = Spacing.screen, top = Spacing.xl, bottom = Spacing.sm)
            .semantics { heading() },
    )
}

private const val DISABLED_ALPHA = 0.38f

@PhonePreviews
@Composable
private fun GroupedListPreview() {
    DowntifyTheme {
        Surface {
            Column {
                GroupTitle("Appearance")
                GroupedList(
                    listOf(
                        GroupedRow(
                            "Theme",
                            RowAction.Control {
                                ChoiceGroup(
                                    listOf("System", "Light", "Dark"),
                                    "System",
                                    { it },
                                    {},
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                )
                            },
                            "System follows your device's light or dark mode.",
                            DowntifyIcons.DarkMode,
                        ),
                        GroupedRow(
                            "Pure black",
                            RowAction.Toggle(true) {
                            },
                            "True black in the dark theme",
                            DowntifyIcons.Contrast,
                        ),
                        GroupedRow(
                            "Temporary audio cache",
                            RowAction.Click({}),
                            "50 MB",
                            DowntifyIcons.Storage,
                            summaryValues = listOf("50 MB"),
                        ),
                        GroupedRow("About", RowAction.Click({}, DowntifyIcons.ChevronRight), null, DowntifyIcons.Info),
                        GroupedRow("Unpair", RowAction.Click({}), icon = DowntifyIcons.Unpair, destructive = true),
                    ),
                )
            }
        }
    }
}
