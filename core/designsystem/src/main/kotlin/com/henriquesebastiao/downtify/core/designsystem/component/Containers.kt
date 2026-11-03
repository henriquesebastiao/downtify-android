package com.henriquesebastiao.downtify.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyShape
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing

/** A [SectionCard]'s container: a surface step, or a container tone for a highlight. */
enum class SectionTone { Low, Container, High, Highest, Primary, Secondary, Tertiary }

/**
 * A tonal card that groups one subject: no shadow, no border, depth from its tone. [hero] makes it
 * the screen's focal point (32 dp corners, 20 dp padding, a larger avatar and title); use one per
 * screen. The header (icon, overline, title, trailing) is optional; [content] goes below it.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    tone: SectionTone = SectionTone.Container,
    hero: Boolean = false,
    icon: Int? = null,
    overline: String? = null,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val (container, onContainer) = tone.colors()
    val shape = if (hero) DowntifyShape.Hero else DowntifyShape.Section
    val hasHeader = listOf(icon, overline, title, trailing).any { it != null }
    val body: @Composable () -> Unit = {
        Column(
            Modifier.padding(if (hero) 20.dp else Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (hasHeader) {
                SectionHeaderRow(icon, overline, title, trailing, hero, tinted = onContainer != null)
            }
            content()
        }
    }
    val contentColor = onContainer ?: MaterialTheme.colorScheme.onSurface
    if (onClick == null) {
        Surface(modifier, shape = shape, color = container, contentColor = contentColor, content = body)
    } else {
        Surface(onClick, modifier, shape = shape, color = container, contentColor = contentColor, content = body)
    }
}

@Composable
private fun SectionHeaderRow(
    icon: Int?,
    overline: String?,
    title: String?,
    trailing: (@Composable () -> Unit)?,
    hero: Boolean,
    tinted: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val muted = if (tinted) LocalContentColor.current.copy(alpha = MUTED_ALPHA) else scheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (icon != null) {
            IconAvatar(
                icon,
                container = if (tinted) {
                    LocalContentColor.current.copy(
                        alpha = AVATAR_ALPHA,
                    )
                } else {
                    scheme.primaryContainer
                },
                content = if (tinted) LocalContentColor.current else scheme.onPrimaryContainer,
                size = if (hero) 56.dp else 40.dp,
            )
        }
        Column(Modifier.weight(1f)) {
            if (overline != null) {
                Text(
                    overline,
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (title != null) {
                Text(
                    title,
                    style = if (hero) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        trailing?.invoke()
    }
}

/** An icon in a tonal circle: what a card or a settings row is about. */
@Composable
fun IconAvatar(
    icon: Int,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = 40.dp,
) {
    Box(modifier.size(size).background(container, CircleShape), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = content)
    }
}

/** The card's container, and its content color when the tone has one of its own. */
@Composable
private fun SectionTone.colors(): Pair<Color, Color?> {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        SectionTone.Low -> scheme.surfaceContainerLow to null
        SectionTone.Container -> scheme.surfaceContainer to null
        SectionTone.High -> scheme.surfaceContainerHigh to null
        SectionTone.Highest -> scheme.surfaceContainerHighest to null
        SectionTone.Primary -> scheme.primaryContainer to scheme.onPrimaryContainer
        SectionTone.Secondary -> scheme.secondaryContainer to scheme.onSecondaryContainer
        SectionTone.Tertiary -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
}

/**
 * A big number for a hero card ("12.4 GB", "318"): 40 sp bold over its label, on a wash of the
 * content color.
 */
@Composable
fun KpiTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(LocalContentColor.current.copy(alpha = KPI_WASH_ALPHA), DowntifyShape.Section)
            .padding(horizontal = Spacing.sm, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.displayMedium.copy(
                fontSize = 40.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
            softWrap = false,
            autoSize = KpiAutoSize,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(LABEL_ALPHA),
        )
    }
}

// A long value ("1,204.5 GB") steps down instead of breaking in a narrow tile.
private val KpiAutoSize = TextAutoSize.StepBased(
    minFontSize = 24.sp,
    maxFontSize = 40.sp,
    stepSize = 2.sp,
)

private const val MUTED_ALPHA = 0.78f
private const val AVATAR_ALPHA = 0.14f
private const val KPI_WASH_ALPHA = 0.1f
private const val LABEL_ALPHA = 0.82f

@PhonePreviews
@Composable
private fun ContainersPreview() {
    DowntifyTheme {
        Surface {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                SectionCard(
                    tone = SectionTone.Primary,
                    hero = true,
                    icon = DowntifyIcons.Storage,
                    overline = "Offline",
                    title = "On this phone",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        KpiTile("12.4 GB", "Used", Modifier.weight(1f))
                        KpiTile("318", "Songs", Modifier.weight(1f))
                    }
                }
                SectionCard(icon = DowntifyIcons.Explore, title = "Discover", onClick = {}) {
                    Text("New songs from what you play", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
