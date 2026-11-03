package com.henriquesebastiao.downtify.feature.player

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * How far Now Playing is open: [fraction] 0 is the mini player, 1 the whole window, anything
 * between a player on its way, following a finger or a spring. [expanded] is where it rests or is
 * headed. The mini player ([miniPlayerAnchor]) and Now Playing ([ExpandingPlayer]) share it, so a
 * drag that starts on one carries on in the other.
 */
@Stable
class PlayerExpansion internal constructor(expanded: Boolean) {
    var fraction by mutableFloatStateOf(if (expanded) 1f else 0f)
        private set

    var expanded by mutableStateOf(expanded)
        private set

    private var animating by mutableStateOf(false)

    /** On its way between the two, under a finger or a spring. */
    val moving by derivedStateOf { animating || (fraction > 0f && fraction < 1f) }

    /** Now Playing is on screen, whole or on its way: the mini player hides under it. */
    val showing by derivedStateOf { expanded || moving || fraction > 0f }

    /** The mini player's card and its cover, where Now Playing shrinks into. */
    internal var miniCard by mutableStateOf<LayoutCoordinates?>(null)
    internal var miniCover by mutableStateOf<LayoutCoordinates?>(null)

    internal var spec: AnimationSpec<Float> = spring()
    internal var flingVelocity = 1f

    /** How far the top edge goes without a mini player to go into (the window's height). */
    internal var fallbackTravel = 1f

    /** The mini player's card in the window, while it's laid out. */
    internal fun miniBounds(): Rect? = miniCard?.takeIf { it.isAttached }?.boundsInRoot()

    // The top edge travels from the mini player's top to the window's: a finger moves it one to one.
    private val travel: Float get() = (miniBounds()?.top ?: fallbackTravel).coerceAtLeast(1f)

    internal val draggable = DraggableState { delta -> fraction = (fraction - delta / travel).coerceIn(0f, 1f) }

    suspend fun expand(velocity: Float = 0f) = settleTo(open = true, velocity)

    suspend fun collapse(velocity: Float = 0f) = settleTo(open = false, velocity)

    /** Straight to open or closed, no animation: playback stopped, or a test. */
    suspend fun snapTo(open: Boolean) = snapTo(if (open) 1f else 0f)

    internal suspend fun snapTo(value: Float) {
        draggable.drag(MutatePriority.PreventUserInput) { fraction = value.coerceIn(0f, 1f) }
        expanded = value >= 1f
    }

    /**
     * Where a drag let go at [velocity] (px/s, positive down) sends it: the way it was flung, else
     * the other end once it's gone a fifth of the way there.
     */
    internal suspend fun settle(velocity: Float) {
        val open = when {
            velocity < -flingVelocity -> true
            velocity > flingVelocity -> false
            expanded -> fraction > 1f - SETTLE_DISTANCE
            else -> fraction > SETTLE_DISTANCE
        }
        settleTo(open, velocity)
    }

    /**
     * A back gesture in progress folds the player a little towards the mini player: [gesture]
     * passes the gesture's progress (0 to 1) to the function it gets, until the gesture ends.
     */
    internal suspend fun foldBack(gesture: suspend (fold: (Float) -> Unit) -> Unit) {
        draggable.drag(MutatePriority.UserInput) {
            gesture { progress -> fraction = 1f - progress * BACK_FOLD }
        }
    }

    private suspend fun settleTo(open: Boolean, velocity: Float) {
        expanded = open
        draggable.drag(MutatePriority.Default) {
            animating = true
            try {
                // The spring may overshoot; the player stops at its ends instead.
                animate(fraction, if (open) 1f else 0f, -velocity / travel, spec) { value, _ ->
                    fraction = value.coerceIn(0f, 1f)
                }
            } finally {
                animating = false
            }
        }
    }

    companion object {
        val Saver = Saver<PlayerExpansion, Boolean>(save = { it.expanded }, restore = { PlayerExpansion(it) })
    }
}

/** A [PlayerExpansion] that remembers whether Now Playing was open across configuration changes. */
@Composable
fun rememberPlayerExpansion(): PlayerExpansion {
    val expansion = rememberSaveable(saver = PlayerExpansion.Saver) { PlayerExpansion(expanded = false) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val fling = with(LocalDensity.current) { FLING_VELOCITY.toPx() }
    SideEffect {
        expansion.spec = spec
        expansion.flingVelocity = fling
    }
    return expansion
}

/**
 * The mini player as the place Now Playing grows out of: it records where it is, hides while Now
 * Playing is out (which draws it in its place), and a drag up on it opens Now Playing.
 */
fun Modifier.miniPlayerAnchor(expansion: PlayerExpansion): Modifier = this
    .onGloballyPositioned { expansion.miniCard = it }
    .graphicsLayer { alpha = if (expansion.showing) 0f else 1f }
    .expansionDrag(expansion)

/** Moves the player with a vertical drag and settles it when the finger lets go. */
internal fun Modifier.expansionDrag(expansion: PlayerExpansion, enabled: Boolean = true): Modifier = draggable(
    state = expansion.draggable,
    orientation = Orientation.Vertical,
    enabled = enabled,
    startDragImmediately = expansion.moving,
    onDragStopped = { velocity -> expansion.settle(velocity) },
)

private val FLING_VELOCITY = 400.dp
private const val SETTLE_DISTANCE = 0.2f
private const val BACK_FOLD = 0.2f
