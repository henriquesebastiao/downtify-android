package com.henriquesebastiao.downtify.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyShape
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import kotlinx.coroutines.delay

/**
 * The bottom of a multi-step flow: where it's at ([label], "Step 1 of 2") and the 96 dp button that
 * moves it on ([nextLabel] is what it does), which changes shape at every [step] (circle, squircle,
 * leaf, drop) and squares up while pressed, on the spatial spring. [busy] puts a loading indicator
 * in it; disabled, it turns grey. Pads itself above the navigation bar.
 */
@Composable
fun OnboardingFooter(
    step: Int,
    label: String,
    nextLabel: String,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Int = DowntifyIcons.Forward,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    Surface(
        modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = DowntifyShape.OnboardingFooter,
    ) {
        // On a tablet the step and its button stay over the content's column, not at the window's edges.
        Box(Modifier.navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier
                    .widthIn(max = CONTENT_WIDTH)
                    .fillMaxWidth()
                    .padding(start = Spacing.xxl, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                NextButton(step, nextLabel, icon, onNext, enabled, busy)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NextButton(step: Int, label: String, icon: Int, onClick: () -> Unit, enabled: Boolean, busy: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corners = if (pressed) PressedCorners else StepCorners[(step - 1).mod(StepCorners.size)]
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val topStart by animateFloatAsState(corners[0], spec, label = "topStart")
    val topEnd by animateFloatAsState(corners[1], spec, label = "topEnd")
    val bottomEnd by animateFloatAsState(corners[2], spec, label = "bottomEnd")
    val bottomStart by animateFloatAsState(corners[3], spec, label = "bottomStart")

    // The spring overshoots; a corner past half the side would fold the shape onto itself.
    fun corner(percent: Float) = CornerSize(percent.coerceIn(0f, MAX_CORNER))
    val shape = RoundedCornerShape(corner(topStart), corner(topEnd), corner(bottomEnd), corner(bottomStart))
    val scheme = MaterialTheme.colorScheme
    FilledIconButton(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = shape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
            // Waiting isn't refusing: the button keeps its color while the indicator runs.
            disabledContainerColor = if (busy) scheme.primaryContainer else scheme.surfaceContainerHighest,
            disabledContentColor = if (busy) scheme.onPrimaryContainer else scheme.onSurface.copy(alpha = 0.38f),
        ),
        interactionSource = interaction,
        modifier = Modifier.size(NEXT_SIZE).semantics { contentDescription = label },
    ) {
        if (busy) {
            LoadingIndicator(color = scheme.onPrimaryContainer, modifier = Modifier.size(56.dp))
        } else {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(32.dp))
        }
    }
}

/**
 * An onboarding step's picture: a tilted card holding [center] (the app's mark, the step's icon)
 * with three [satellites], the step's icons, in Material shapes around it. They pop in one after
 * another on the spatial spring. Decorative: nothing in it is read out.
 *
 * It is 220 dp wide at most and shrinks, keeping its proportions, to the width and the height it's
 * given: a screen that must not scroll can hand it whatever room is left.
 */
@Composable
fun ShapeIllustration(satellites: List<Int>, modifier: Modifier = Modifier, center: @Composable () -> Unit) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val byHeight = if (constraints.hasBoundedHeight) maxHeight * ILLUSTRATION_RATIO else ILLUSTRATION_WIDTH
        val width = minOf(maxWidth, ILLUSTRATION_WIDTH, byHeight)
        ShapeKit(satellites, Modifier.size(width, width / ILLUSTRATION_RATIO), center)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeKit(satellites: List<Int>, modifier: Modifier, center: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tints = listOf(scheme.onSurfaceVariant, scheme.tertiary, scheme.primary)
    val shapes = listOf(CircleShape, MaterialShapes.Cookie9Sided.toShape(), CircleShape)
    BoxWithConstraints(modifier) {
        val unit = maxWidth / KIT_WIDTH
        val scale = (maxWidth / ILLUSTRATION_WIDTH).coerceAtMost(1f)
        PopIn(0, Modifier.offset(unit * 70f, unit * 40f).size(unit * 220f, unit * 200f)) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationZ = CARD_TURN }
                    .background(scheme.surfaceContainerHigh, RoundedCornerShape(unit * 40f)),
            )
            // [center] is drawn for the full-size picture: it shrinks with it.
            Box(
                Modifier.matchParentSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
                contentAlignment = Alignment.Center,
            ) { center() }
        }
        SatellitePlaces.zip(satellites).forEachIndexed { index, (place, icon) ->
            val side = unit * place.size
            PopIn(
                index + 1,
                Modifier
                    .offset(maxWidth * place.x - side / 2, maxHeight * place.y - side / 2)
                    .size(side),
            ) {
                Box(
                    Modifier.matchParentSize().background(scheme.surfaceContainerHigh, shapes[index]),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        tint = tints[index],
                        modifier = Modifier.size(side * SATELLITE_ICON),
                    )
                }
            }
        }
    }
}

@Composable
private fun PopIn(index: Int, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val appear = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        delay(index * STAGGER_MS)
        appear.animateTo(1f, spec)
    }
    Box(
        modifier.graphicsLayer {
            val scale = POP_FROM + (1f - POP_FROM) * appear.value
            scaleX = scale
            scaleY = scale
            alpha = appear.value.coerceIn(0f, 1f)
        },
        content = content,
    )
}

/** Where a satellite sits, its center as fractions of the picture, and its side in kit units. */
private data class SatellitePlace(val x: Float, val y: Float, val size: Float)

// The design kit draws the picture 340 × 300; places and sizes are in its units.
private const val KIT_WIDTH = 340f
private const val ILLUSTRATION_RATIO = 340f / 300f
private val ILLUSTRATION_WIDTH = 220.dp
private val SatellitePlaces = listOf(
    SatellitePlace(x = 0.18f, y = 0.10f, size = 96f),
    SatellitePlace(x = 0.24f, y = 0.80f, size = 76f),
    SatellitePlace(x = 0.82f, y = 0.66f, size = 92f),
)
private const val CARD_TURN = -14f
private const val SATELLITE_ICON = 0.42f
private const val STAGGER_MS = 80L
private const val POP_FROM = 0.6f

// Corners in percent of the side, topStart, topEnd, bottomEnd, bottomStart: circle, squircle,
// leaf, drop, then around again.
private val StepCorners = listOf(
    listOf(50f, 50f, 50f, 50f),
    listOf(30f, 30f, 30f, 30f),
    listOf(50f, 50f, 50f, 22f),
    listOf(22f, 50f, 50f, 50f),
)
private val PressedCorners = listOf(24f, 24f, 24f, 24f)
private const val MAX_CORNER = 50f
private val NEXT_SIZE = 96.dp
private val CONTENT_WIDTH = 480.dp

@PhonePreviews
@Composable
private fun OnboardingPreview() {
    DowntifyTheme {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                var step by remember { mutableIntStateOf(1) }
                ShapeIllustration(
                    satellites = listOf(DowntifyIcons.Wifi, DowntifyIcons.QrCode, DowntifyIcons.Server),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { DowntifyLogo(size = 80.dp) }
                OnboardingFooter(step = step, label = "Step $step of 2", nextLabel = "Next", onNext = { step++ })
                OnboardingFooter(step = 2, label = "Step 2 of 2", nextLabel = "Pair", onNext = {}, busy = true)
            }
        }
    }
}
