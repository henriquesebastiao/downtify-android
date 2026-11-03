package com.henriquesebastiao.downtify.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing

/**
 * A [Tag]'s colors: a raised surface, a container tone, the error container (a status that went
 * wrong), or a wash of the content color on a tinted card.
 */
enum class TagTone { Surface, Primary, Secondary, Tertiary, Error, OnColor }

/**
 * A static label in a pill: a version, a status, a value ("44.1 kHz", "FLAC"), in the app's own
 * face. Not a filter: selection is a FilterChip. [small] is 32 dp tall instead of 40.
 */
@Composable
fun Tag(
    label: String,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    tone: TagTone = TagTone.Surface,
    small: Boolean = false,
) {
    val (container, content) = tone.colors()
    val style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
    val padding = when {
        small && icon != null -> PaddingValues(start = Spacing.sm, end = Spacing.md)
        small -> PaddingValues(horizontal = Spacing.md)
        icon != null -> PaddingValues(start = Spacing.md, end = Spacing.lg)
        else -> PaddingValues(horizontal = Spacing.lg)
    }
    Surface(color = container, contentColor = content, shape = CircleShape, modifier = modifier) {
        Row(
            Modifier.height(if (small) 32.dp else 40.dp).padding(padding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (icon != null) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(if (small) 16.dp else 20.dp),
                )
            }
            Text(label, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TagTone.colors(): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        TagTone.Surface -> scheme.surfaceContainerHigh to scheme.onSurface
        TagTone.Primary -> scheme.primaryContainer to scheme.onPrimaryContainer
        TagTone.Secondary -> scheme.secondaryContainer to scheme.onSecondaryContainer
        TagTone.Tertiary -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        TagTone.Error -> scheme.errorContainer to scheme.onErrorContainer
        TagTone.OnColor -> LocalContentColor.current.let { it.copy(alpha = ON_COLOR_ALPHA) to it }
    }
}

private const val ON_COLOR_ALPHA = 0.14f

@PhonePreviews
@Composable
private fun TagPreview() {
    DowntifyTheme {
        Surface {
            FlowRow(
                Modifier.padding(Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Tag("FLAC", tone = TagTone.Secondary)
                Tag("44.1 kHz", small = true)
                Tag("941 kbps", small = true)
                Tag("Offline", icon = DowntifyIcons.CheckCircle, tone = TagTone.Primary)
            }
        }
    }
}
