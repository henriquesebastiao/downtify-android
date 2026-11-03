package com.henriquesebastiao.downtify.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyLogo
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyShape
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing

/**
 * The side menu, after PixelPlayer's: a modal drawer over the whole shell (the bar and the mini
 * player too) with the destinations that don't need a tab ([DrawerDestination]). It opens only
 * from Home's menu button, since a swipe from the edge would fight the system's back gesture;
 * once open, a swipe, the scrim or (predictive) back closes it.
 */
@Composable
fun AppDrawer(
    drawerState: DrawerState,
    selected: DrawerDestination?,
    onSelect: (DrawerDestination) -> Unit,
    modifier: Modifier = Modifier,
    destinations: List<DrawerDestination> = DrawerDestination.entries,
    content: @Composable () -> Unit,
) {
    ModalNavigationDrawer(
        drawerContent = { AppDrawerSheet(drawerState, selected, onSelect, destinations) },
        modifier = modifier,
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        content = content,
    )
}

/** The app's name over Material's drawer items, outlined at rest and filled when selected. */
@Composable
private fun AppDrawerSheet(
    drawerState: DrawerState,
    selected: DrawerDestination?,
    onSelect: (DrawerDestination) -> Unit,
    destinations: List<DrawerDestination>,
) {
    ModalDrawerSheet(drawerState = drawerState, drawerShape = DowntifyShape.Drawer) {
        Row(
            Modifier.padding(start = HEADER_INSET, end = Spacing.xl, top = Spacing.xl, bottom = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            DowntifyLogo(size = 40.dp)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        }
        destinations.forEach { destination ->
            val isSelected = destination == selected
            NavigationDrawerItem(
                label = { Text(stringResource(destination.label)) },
                selected = isSelected,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        painterResource(if (isSelected) destination.selectedIcon else destination.icon),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
    }
}

// Lined up with the items' icons: their 12 dp padding plus the item's own 16 dp.
private val HEADER_INSET = 28.dp

@PhonePreviews
@Composable
private fun AppDrawerPreview() {
    DowntifyTheme {
        AppDrawer(
            drawerState = rememberDrawerState(DrawerValue.Open),
            selected = null,
            onSelect = {},
        ) {
            Surface { Box(Modifier.fillMaxSize()) }
        }
    }
}
