package com.henriquesebastiao.downtify.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.toSize
import com.henriquesebastiao.downtify.core.designsystem.component.Artwork
import com.henriquesebastiao.downtify.core.designsystem.component.CoverTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyShape
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Now Playing and the mini player as one surface. At [PlayerExpansion.fraction] 0 it lies over the
 * mini player, which it looks just like; at 1 it fills the window. Between them its bounds and
 * corners follow the fraction: the mini player's content fades out at its top, Now Playing's
 * (laid out at the window's size, scaled to the surface's width) fades in, the cover flies from
 * one player's place to the other's, and a scrim dims what's behind. A vertical drag anywhere on
 * it moves it with the finger ([dragEnabled]); letting go settles it open or closed.
 *
 * Closed, it stays composed but off screen, with the mini player's content and the flying cover
 * loaded, so a drag starts on images already in: only Now Playing itself waits to be shown. [mini]
 * and [full] put the modifier they're given on their cover: it hides it while the flying one
 * stands in for it, and tells this where it is.
 */
@Composable
fun ExpandingPlayer(
    expansion: PlayerExpansion,
    coverUrl: String?,
    largeCoverUrl: String?,
    attached: Boolean,
    dragEnabled: Boolean,
    mini: @Composable (coverModifier: Modifier) -> Unit,
    full: @Composable (coverModifier: Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scrim = MaterialTheme.colorScheme.scrim
    val barCorner = with(density) { DowntifyShape.BarCorner.toPx() }
    val bottomCorner = with(density) { (if (attached) DowntifyShape.JoinedCorner else DowntifyShape.BarCorner).toPx() }
    val coverCorner = DowntifyShape.Artwork.topStart.toPx(Size.Unspecified, density)
    val progress = { expansion.fraction.coerceIn(0f, 1f) }
    val showMini by remember { derivedStateOf { expansion.fraction < MINI_FADE_END } }
    var fullCoverSize by remember { mutableStateOf<Size?>(null) }
    // The flying cover shows once its image is in, and only then do the two players hide theirs.
    var smallReady by remember { mutableStateOf(false) }
    var largeReady by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxSize().drawBehind { drawRect(scrim, alpha = SCRIM_ALPHA * progress()) }) {
        val window = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        SideEffect { expansion.fallbackTravel = window.height }
        val morph = remember(expansion, window, bottomCorner) { Morph(expansion, window, bottomCorner) }
        val flying by remember(morph) { derivedStateOf { expansion.moving && morph.miniCover() != null } }
        val coverHidden = if (flying && (smallReady || largeReady)) Modifier.alpha(0f) else Modifier

        CoverTheme(coverUrl) {
            val color = MaterialTheme.colorScheme.primaryContainer
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val r = morph.surface(progress())
                        val placeable = measurable.measure(
                            Constraints.fixed(
                                r.width.roundToInt().coerceAtLeast(0),
                                r.height.roundToInt().coerceAtLeast(0),
                            ),
                        )
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            // Closed, it's measured (its images load) but not placed: the mini player shows instead.
                            if (expansion.showing) placeable.place(r.left.roundToInt(), r.top.roundToInt())
                        }
                    }
                    .graphicsLayer {
                        val p = progress()
                        val top = mix(barCorner, 0f, p)
                        val bottom = mix(bottomCorner, 0f, p)
                        shape = RoundedCornerShape(top, top, bottom, bottom)
                        clip = true
                    }
                    .drawBehind { drawRect(color) }
                    .expansionDrag(expansion, enabled = dragEnabled),
            ) {
                if (expansion.showing) {
                    // Now Playing keeps the window's layout and shrinks to the surface's width, anchored at its top.
                    Box(
                        Modifier
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(
                                    Constraints.fixed(window.width.roundToInt(), window.height.roundToInt()),
                                )
                                layout(constraints.maxWidth, constraints.maxHeight) {
                                    placeable.placeWithLayer(0, 0) {
                                        val scale = constraints.maxWidth / window.width
                                        transformOrigin = TransformOrigin(0f, 0f)
                                        scaleX = scale
                                        scaleY = scale
                                        alpha = ((progress() - FULL_FADE_START) / (FULL_FADE_END - FULL_FADE_START))
                                            .coerceIn(0f, 1f)
                                    }
                                }
                            }
                            .onPlaced { morph.fullLayer = it },
                    ) {
                        full(
                            Modifier
                                .onPlaced {
                                    morph.fullCover = it
                                    fullCoverSize = it.size.toSize()
                                }
                                .then(coverHidden),
                        )
                    }
                }
                if (showMini) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(Alignment.Top, unbounded = true)
                            .graphicsLayer { alpha = (1f - progress() / MINI_FADE_END).coerceIn(0f, 1f) },
                    ) {
                        mini(coverHidden)
                    }
                }

                // The cover leads: it's most of the way to Now Playing's place while the surface is halfway there.
                val cover = { 1f - (1f - progress()).pow(COVER_LEAD) }
                val from = { morph.miniCover() ?: Rect.Zero }
                // Where the cover is now: the mini player's turning into Now Playing's, which scales with the
                // surface. Before Now Playing is first laid out, it stays on the mini player's.
                val rect = { lerp(from(), morph.fullCover(progress()) ?: from(), cover()) }
                val radius = { mix(from().width / 2, coverCorner * morph.scale(progress()), cover()) }
                // The mini player's small cover under the large one, which fades in (or covers it, closing,
                // while the small one isn't in yet). Both at their players' sizes, so they're the images
                // those already show.
                morph.miniCover()?.let { mini ->
                    Box(Modifier.flying(mini.size, rect, radius) { if (flying && smallReady) 1f else 0f }) {
                        Artwork(
                            coverUrl,
                            contentDescription = null,
                            Modifier.fillMaxSize(),
                            shape = RectangleShape,
                            crossfade = false,
                            onReady = { smallReady = it },
                        )
                    }
                }
                fullCoverSize?.let { size ->
                    val alpha = {
                        when {
                            !flying || !largeReady -> 0f
                            smallReady -> cover()
                            else -> 1f
                        }
                    }
                    Box(Modifier.flying(size, rect, radius, alpha)) {
                        Artwork(
                            largeCoverUrl,
                            contentDescription = null,
                            Modifier.fillMaxSize(),
                            shape = RectangleShape,
                            crossfade = false,
                            onReady = { largeReady = it },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Where things are on the way: the surface in the [window], growing from the mini player's card
 * (or, with no mini player on screen, a strip [below] the window's bottom), and the two covers in
 * the surface.
 */
private class Morph(private val expansion: PlayerExpansion, private val window: Size, private val below: Float) {
    var fullLayer: LayoutCoordinates? = null
    var fullCover: LayoutCoordinates? = null

    fun surface(fraction: Float): Rect {
        val mini = expansion.miniBounds() ?: Rect(0f, window.height, window.width, window.height + below)
        return lerp(mini, Rect(Offset.Zero, window), fraction)
    }

    /** How much Now Playing is scaled down to fit the surface's width. */
    fun scale(fraction: Float): Float = surface(fraction).width / window.width

    /** The mini player's cover in its card, which is the surface's top. */
    fun miniCover(): Rect? {
        val card = expansion.miniCard?.takeIf { it.isAttached } ?: return null
        val cover = expansion.miniCover?.takeIf { it.isAttached } ?: return null
        return card.localBoundingBoxOf(cover, clipBounds = false)
    }

    /** Now Playing's cover in the surface, scaled with it; null until Now Playing is laid out. */
    fun fullCover(fraction: Float): Rect? {
        val layer = fullLayer?.takeIf { it.isAttached } ?: return null
        val cover = fullCover?.takeIf { it.isAttached } ?: return null
        val r = layer.localBoundingBoxOf(cover, clipBounds = false)
        val scale = scale(fraction)
        return Rect(r.left * scale, r.top * scale, r.right * scale, r.bottom * scale)
    }
}

/**
 * Lays its content out at [base] and draws it over [rect] (in its parent's coordinates), clipped to
 * corners of [radius] there. It takes no room: the parent clips it.
 */
private fun Modifier.flying(base: Size, rect: () -> Rect, radius: () -> Float, alpha: () -> Float) =
    layout { measurable, _ ->
        val placeable = measurable.measure(
            Constraints.fixed(base.width.roundToInt().coerceAtLeast(1), base.height.roundToInt().coerceAtLeast(1)),
        )
        layout(0, 0) {
            placeable.placeWithLayer(0, 0) {
                val r = rect()
                val scale = r.width / placeable.width
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = r.left
                translationY = r.top
                scaleX = scale
                scaleY = scale
                shape = RoundedCornerShape(radius() / scale)
                clip = true
                this.alpha = alpha()
            }
        }
    }

private fun mix(start: Float, stop: Float, fraction: Float) = start + (stop - start) * fraction

// The mini player's content is gone by this fraction; Now Playing's appears after it, so the two
// players' titles and buttons never show over each other.
private const val MINI_FADE_END = 0.2f
private const val FULL_FADE_START = 0.2f
private const val FULL_FADE_END = 0.6f
private const val SCRIM_ALPHA = 0.32f

// How much the cover leads the surface: 1 keeps pace, 2 is most of the way at half.
private const val COVER_LEAD = 1.5f
