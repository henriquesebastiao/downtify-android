package com.henriquesebastiao.downtify.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing

/** The Downtify shield on its green disc. */
@Composable
fun DowntifyLogo(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Image(painter = painterResource(DowntifyIcons.Logo), contentDescription = null, modifier = modifier.size(size))
}

/** A section title (titleLarge) with an optional text action ("See all"). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * A centered message for an empty list or an error, with an optional action. [hero] is for a
 * screen that is nothing but this message (an empty library): a big icon in primary, a headline.
 */
@Composable
fun EmptyState(
    icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    hero: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (hero) Spacing.xl else Spacing.xxl, vertical = if (hero) 48.dp else Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (hero) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(if (hero) 88.dp else 48.dp),
        )
        Text(
            title,
            style = if (hero) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                body,
                style = if (hero) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = if (hero) Modifier.widthIn(max = HERO_BODY_WIDTH) else Modifier,
            )
        }
        if (action != null) {
            if (hero) Spacer(Modifier.height(Spacing.md))
            action()
        }
    }
}

private val HERO_BODY_WIDTH = 320.dp

/** Three bouncing bars: the row that's playing. Still when [animate] is false (paused). */
@Composable
fun PlayingBars(
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "bars")
    val heights = listOf(0.55f to 420, 1f to 360, 0.7f to 500).map { (start, duration) ->
        val value by transition.animateFloat(
            initialValue = start,
            targetValue = if (animate) 0.25f else start,
            animationSpec = infiniteRepeatable(tween(duration), RepeatMode.Reverse),
            label = "bar",
        )
        value
    }
    Row(
        modifier = modifier.size(20.dp, 14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        heights.forEach { h ->
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(h)
                    .background(color, RoundedCornerShape(2.dp)),
            )
        }
    }
}

/** Where a search result comes from, in its brand's color (the Search board's badges). */
enum class SourceBrand { Spotify, YouTube, Neutral }

/** A tiny label before a result's artist: "Spotify", "YT Music", "Soulseek". */
@Composable
fun SourceBadge(text: String, brand: SourceBrand, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < DARK_LUMINANCE
    val (container, content) = when (brand) {
        SourceBrand.Spotify -> if (dark) SpotifyDark else SpotifyLight

        SourceBrand.YouTube -> if (dark) YouTubeDark else YouTubeLight

        SourceBrand.Neutral ->
            MaterialTheme.colorScheme.surfaceContainerHighest to
                MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(4.dp), modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

// The services' own greens and reds, softened as the Search board draws them.
private val SpotifyDark = Color(0x291DB954) to Color(0xFF3BD16F)
private val SpotifyLight = Color(0x241DB954) to Color(0xFF0B7A34)
private val YouTubeDark = Color(0x24FF4848) to Color(0xFFFF7A7A)
private val YouTubeLight = Color(0x1FFF0000) to Color(0xFFB3261E)
private const val DARK_LUMINANCE = 0.5f

@PhonePreviews
@Composable
private fun ComponentsPreview() {
    DowntifyTheme {
        Surface {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                modifier = Modifier.padding(vertical = Spacing.lg),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                ) {
                    DowntifyLogo(size = 40.dp)
                    PlayingBars(animate = false)
                }
                SectionHeader("Recently added", actionLabel = "See all", onAction = {})
                EmptyState(
                    icon = DowntifyIcons.Library,
                    title = "Nothing here yet",
                    body = "Songs you download on the server show up here.",
                )
            }
        }
    }
}
