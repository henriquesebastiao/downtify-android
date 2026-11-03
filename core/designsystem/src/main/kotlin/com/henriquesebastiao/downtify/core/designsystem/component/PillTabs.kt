package com.henriquesebastiao.downtify.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing

/**
 * Pill tabs (v3) for a screen's sections (Albums, Artists, Songs): 52 dp toggle buttons with an
 * uppercase label, the selected one filled with primary and marked by a 4 × 28 dp bar below. The
 * row scrolls sideways and runs to the screen's edges; [contentPadding] keeps the first and last
 * pill on the screen's margins.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PillTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.screen),
) {
    val scheme = MaterialTheme.colorScheme
    val colors = ToggleButtonDefaults.colors(
        containerColor = scheme.surfaceContainerLowest,
        contentColor = scheme.onSurface,
        checkedContainerColor = scheme.primary,
        checkedContentColor = scheme.onPrimary,
    )
    // Pills when checked too: the stock toggle squares off its checked state.
    val shapes = ToggleButtonShapes(
        shape = CircleShape,
        pressedShape = ToggleButtonDefaults.pressedShape,
        checkedShape = CircleShape,
    )
    // The selected tab is always on screen: a later one ("Artistas" in Portuguese) would start past the edge.
    val requesters = remember(tabs.size) { List(tabs.size) { BringIntoViewRequester() } }
    LaunchedEffect(selectedIndex) {
        // After the first frame: before the row is laid out there is nothing to scroll to.
        withFrameNanos { }
        requesters.getOrNull(selectedIndex)?.bringIntoView()
    }
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        tabs.forEachIndexed { index, label ->
            val checked = index == selectedIndex
            Column(
                Modifier.bringIntoViewRequester(requesters[index]),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ToggleButton(
                    checked = checked,
                    onCheckedChange = { onSelect(index) },
                    shapes = shapes,
                    colors = colors,
                    contentPadding = PaddingValues(horizontal = Spacing.xl),
                    modifier = Modifier
                        .heightIn(min = TAB_HEIGHT)
                        // A tab, not a checkbox: read as "Albums, tab, selected".
                        .clearAndSetSemantics {
                            role = Role.Tab
                            selected = checked
                            text = AnnotatedString(label)
                            onClick {
                                onSelect(index)
                                true
                            }
                        },
                ) {
                    Text(
                        label.toUpperCase(LocaleList.current),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = LABEL_TRACKING,
                        maxLines = 1,
                    )
                }
                val width by animateDpAsState(
                    if (checked) INDICATOR_WIDTH else 0.dp,
                    MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "tabIndicator",
                )
                Box(
                    Modifier
                        .padding(top = Spacing.sm)
                        .width(width)
                        .height(INDICATOR_HEIGHT)
                        .background(scheme.primary, CircleShape),
                )
            }
        }
    }
}

private val TAB_HEIGHT = 52.dp
private val INDICATOR_WIDTH = 28.dp
private val INDICATOR_HEIGHT = 4.dp
private val LABEL_TRACKING = 0.04.em

@PhonePreviews
@Composable
private fun PillTabsPreview() {
    DowntifyTheme {
        Surface {
            PillTabs(
                tabs = listOf("Albums", "Artists", "Playlists", "Songs"),
                selectedIndex = 0,
                onSelect = {},
                modifier = Modifier.padding(vertical = Spacing.lg),
            )
        }
    }
}
