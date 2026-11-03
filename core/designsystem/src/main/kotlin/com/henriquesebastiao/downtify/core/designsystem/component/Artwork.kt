package com.henriquesebastiao.downtify.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyShape
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing

/** The placeholder's colors: neutral, or one of the scheme's container tones. */
enum class ArtworkTone { Neutral, Primary, Secondary, Tertiary }

/**
 * A cover from the server (or anything Coil loads), clipped to [shape]: a squircle by default,
 * [CircleShape] for artists, a Material shape for a highlight. Without a cover, while it loads or
 * when it fails, an honest placeholder: a tonal container with [placeholderIcon], under the image.
 * [onReady] says whether what shows is final — the image, or the placeholder for good (no cover,
 * or it failed) — and not while it loads. Without [crossfade] the image shows at once, even the
 * first time it's drawn (a crossfade starts then): for a cover standing in for one already on
 * screen, such as Now Playing's flying cover.
 */
@Composable
fun Artwork(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = DowntifyShape.ArtworkThumb,
    placeholderIcon: Int = DowntifyIcons.Song,
    tone: ArtworkTone = ArtworkTone.Neutral,
    onReady: ((Boolean) -> Unit)? = null,
    crossfade: Boolean = true,
) {
    val (container, content) = tone.colors()
    val context = LocalPlatformContext.current
    val model = remember(url, crossfade, context) {
        if (crossfade || url == null) url else ImageRequest.Builder(context).data(url).crossfade(false).build()
    }
    var loaded by remember(url) { mutableStateOf(false) }
    if (url == null && onReady != null) SideEffect { onReady(true) }
    Box(modifier.clip(shape).background(container)) {
        // Under the image, not instead of it: gone once the image is in (a cover may be transparent in places).
        if (!loaded) ArtworkPlaceholder(placeholderIcon, content)
        if (url != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onState = {
                    loaded = it is AsyncImagePainter.State.Success
                    onReady?.invoke(loaded || it is AsyncImagePainter.State.Error)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ArtworkTone.colors(): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        ArtworkTone.Neutral -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        ArtworkTone.Primary -> scheme.primaryContainer to scheme.onPrimaryContainer
        ArtworkTone.Secondary -> scheme.secondaryContainer to scheme.onSecondaryContainer
        ArtworkTone.Tertiary -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
}

@Composable
private fun ArtworkPlaceholder(icon: Int, tint: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.fillMaxSize(PLACEHOLDER_ICON_FRACTION).alpha(PLACEHOLDER_ICON_ALPHA),
        )
    }
}

private const val PLACEHOLDER_ICON_FRACTION = 0.42f
private const val PLACEHOLDER_ICON_ALPHA = 0.8f

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@PhonePreviews
@Composable
private fun ArtworkPreview() {
    DowntifyTheme {
        Surface {
            Row(Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Artwork(url = null, contentDescription = null, modifier = Modifier.size(64.dp))
                Artwork(
                    url = null,
                    contentDescription = null,
                    shape = CircleShape,
                    placeholderIcon = DowntifyIcons.Artist,
                    tone = ArtworkTone.Secondary,
                    modifier = Modifier.size(64.dp),
                )
                Artwork(
                    url = null,
                    contentDescription = null,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    placeholderIcon = DowntifyIcons.Album,
                    tone = ArtworkTone.Tertiary,
                    modifier = Modifier.size(96.dp),
                )
            }
        }
    }
}
