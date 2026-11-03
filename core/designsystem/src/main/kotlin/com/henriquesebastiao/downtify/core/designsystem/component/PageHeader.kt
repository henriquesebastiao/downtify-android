package com.henriquesebastiao.downtify.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing

/**
 * A screen's header (v3), on one row as the library's: the back button at the start, the title in
 * 36 sp bold beside it (a long word steps down to fit) and [actions] at the end, all circular
 * 48 dp buttons. Pass a [TopAppBarDefaults.pinnedScrollBehavior] so the row takes a container
 * color while the content scrolls under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backLabel: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                title,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.displayMedium,
                autoSize = PageTitleAutoSize,
                // Clear of the back button; without one, where the library's title sits.
                modifier = Modifier
                    .padding(start = if (onBack == null) Spacing.sm else Spacing.md)
                    .semantics { heading() },
            )
        },
        navigationIcon = {
            if (onBack != null) {
                CircleButton(DowntifyIcons.Back, backLabel, onBack, Modifier.padding(start = Spacing.xs))
            }
        },
        actions = {
            actions()
            Spacer(Modifier.width(Spacing.xs))
        },
        expandedHeight = HEADER_HEIGHT,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        scrollBehavior = scrollBehavior,
        modifier = modifier,
    )
}

/**
 * A circular 48 dp tonal button: a header's back and actions, a list row's "more". Squarer while
 * pressed, as Material 3 Expressive's icon buttons are.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CircleButton(
    icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        modifier = modifier.size(CIRCLE_BUTTON_SIZE),
    ) {
        Icon(painterResource(icon), contentDescription = contentDescription)
    }
}

// Long words in 36 sp beside a back button and an action don't fit a 360 dp phone: the title steps
// down instead of breaking or cutting.
private val PageTitleAutoSize = TextAutoSize.StepBased(
    minFontSize = 24.sp,
    maxFontSize = 36.sp,
    stepSize = 2.sp,
)

private val CIRCLE_BUTTON_SIZE = 48.dp

// The library's header row: a 48 dp button with 8 dp above and below.
private val HEADER_HEIGHT = 64.dp

@PhonePreviews
@Composable
private fun PageHeaderPreview() {
    DowntifyTheme {
        Surface {
            Column {
                PageHeader(title = "Settings", onBack = {}, backLabel = "Back")
                PageHeader(
                    title = "Discover",
                    onBack = {},
                    backLabel = "Back",
                    actions = { CircleButton(DowntifyIcons.Refresh, "Refresh", {}) },
                )
                PageHeader(title = "Search", titleColor = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
