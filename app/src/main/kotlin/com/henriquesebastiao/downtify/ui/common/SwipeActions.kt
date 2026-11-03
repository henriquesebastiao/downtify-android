package com.henriquesebastiao.downtify.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Swipe a song to the right to add it to the queue, as in Spotify. The row
 * springs back once the swipe passes the threshold; nothing is dismissed.
 */
@Composable
fun SwipeToQueue(
    onAddToQueue: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    content: @Composable RowScope.() -> Unit,
) {
    SwipeAction(
        direction = SwipeToDismissBoxValue.StartToEnd,
        icon = DowntifyIcons.AddToQueue,
        label = stringResource(R.string.track_add_to_queue),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        resetAfter = true,
        onSwiped = onAddToQueue,
        shape = shape,
        modifier = modifier,
        content = content,
    )
}

/** Swipe a queue row to the left to take it out of the queue. */
@Composable
fun SwipeToRemove(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    content: @Composable RowScope.() -> Unit,
) {
    SwipeAction(
        direction = SwipeToDismissBoxValue.EndToStart,
        icon = DowntifyIcons.Delete,
        label = stringResource(R.string.player_queue_remove),
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        resetAfter = false,
        onSwiped = onRemove,
        shape = shape,
        modifier = modifier,
        content = content,
    )
}

@Composable
private fun SwipeAction(
    direction: SwipeToDismissBoxValue,
    @DrawableRes icon: Int,
    label: String,
    containerColor: Color,
    contentColor: Color,
    resetAfter: Boolean,
    onSwiped: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val startToEnd = direction == SwipeToDismissBoxValue.StartToEnd
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            // Only while this direction is being swiped: settling back shows nothing.
            if (state.dismissDirection == direction) {
                Row(
                    // Behind a card the color takes the card's corners.
                    Modifier.fillMaxSize().background(containerColor, shape).padding(horizontal = Spacing.xl),
                    horizontalArrangement = if (startToEnd) Arrangement.Start else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(icon), contentDescription = null, tint = contentColor)
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        modifier = Modifier.padding(horizontal = Spacing.sm),
                    )
                }
            }
        },
        enableDismissFromStartToEnd = startToEnd,
        enableDismissFromEndToStart = !startToEnd,
        onDismiss = { value ->
            if (value == direction) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onSwiped()
                if (resetAfter) scope.launch { state.reset() }
            }
        },
        modifier = modifier,
        content = content,
    )
}
