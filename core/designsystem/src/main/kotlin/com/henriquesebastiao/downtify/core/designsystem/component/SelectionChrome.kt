package com.henriquesebastiao.downtify.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyShape
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing

// The chrome of a selection: a contextual top bar that says how many are picked and can pick all,
// the check a row shows, and a bar of what to do with the pick. The app passes the words in, so
// this stays free of any screen's strings.

private const val CHROME_ALPHA = 0.12f

/**
 * Replaces a screen's own top bar while something is picked: a close button, "3 selected" and
 * select-all / clear, plus [actions] of the screen's own. Colors are the secondary container, so
 * the selection reads as a mode rather than as another page.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectionTopBar(
    title: String,
    closeLabel: String,
    selectAllLabel: String,
    clearLabel: String,
    allSelected: Boolean,
    onClose: () -> Unit,
    onToggleAll: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLargeEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            CircleButton(
                DowntifyIcons.Close,
                closeLabel,
                onClose,
                Modifier.padding(start = Spacing.xs),
                containerColor = scheme.onSecondaryContainer.copy(alpha = CHROME_ALPHA),
                contentColor = scheme.onSecondaryContainer,
            )
        },
        actions = {
            actions()
            CircleButton(
                if (allSelected) DowntifyIcons.Remove else DowntifyIcons.Check,
                if (allSelected) clearLabel else selectAllLabel,
                onToggleAll,
                Modifier.padding(end = Spacing.sm),
                containerColor = scheme.onSecondaryContainer.copy(alpha = CHROME_ALPHA),
                contentColor = scheme.onSecondaryContainer,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.secondaryContainer),
    )
}

/**
 * The bar under a selection, one action wide ("Add 3 songs to playlist"): it sits on the
 * secondary container and clears the system's navigation bar, so it reads as part of the mode.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectionBottomBar(
    actionLabel: String,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
        shape = RoundedCornerShape(topStart = DowntifyShape.BarCorner, topEnd = DowntifyShape.BarCorner),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            val height = ButtonDefaults.MediumContainerHeight
            Button(
                onClick = onClick,
                enabled = enabled,
                shapes = ButtonDefaults.shapesFor(height),
                contentPadding = ButtonDefaults.contentPaddingFor(height),
                modifier = Modifier.fillMaxWidth().heightIn(min = height),
            ) {
                Icon(painterResource(icon), contentDescription = null)
                Spacer(Modifier.size(Spacing.sm))
                Text(actionLabel, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** The check a row shows while selected: filled with a tick, or an empty circle when it isn't. */
@Composable
fun SelectionCheckbox(selected: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .size(CHECK_SIZE)
            .background(if (selected) scheme.primary else scheme.surfaceContainer, CircleShape)
            .border(2.dp, if (selected) scheme.primary else scheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                painterResource(DowntifyIcons.Check),
                contentDescription = null,
                tint = scheme.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private val CHECK_SIZE = 24.dp

@PhonePreviews
@Composable
private fun SelectionTopBarPreview() {
    DowntifyTheme {
        SelectionTopBar(
            title = "3 selected",
            closeLabel = "Close",
            selectAllLabel = "Select all",
            clearLabel = "Clear selection",
            allSelected = false,
            onClose = {},
            onToggleAll = {},
        )
    }
}

@PhonePreviews
@Composable
private fun SelectionBottomBarPreview() {
    DowntifyTheme {
        SelectionBottomBar(actionLabel = "Add 3 songs to playlist", icon = DowntifyIcons.AddToQueue, onClick = {})
    }
}

@PhonePreviews
@Composable
private fun SelectionCheckboxPreview() {
    DowntifyTheme {
        Surface {
            Row(Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                SelectionCheckbox(selected = true)
                SelectionCheckbox(selected = false)
            }
        }
    }
}
