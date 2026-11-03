package com.henriquesebastiao.downtify.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyType
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing

/**
 * The hero of the home and welcome screens: the wide, rounded display title (break its lines with
 * "\n"), a subtitle and an optional [action] at the end of the subtitle's row. [accent] is a last
 * line in the primary color ("from your server."). [small] starts from the smaller size. Either way
 * the title steps down until no word breaks. One per screen.
 */
@Composable
fun HeroHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accent: String? = null,
    small: Boolean = false,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.xl, bottom = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        val style = if (small) DowntifyType.heroSmall else DowntifyType.hero
        val primary = MaterialTheme.colorScheme.primary
        Text(
            buildAnnotatedString {
                append(title)
                if (accent != null) {
                    append('\n')
                    withStyle(SpanStyle(color = primary)) { append(accent) }
                }
            },
            style = style,
            color = titleColor,
            autoSize = remember(style) { WholeWords(style.fontSize) },
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null || action != null) {
            Row(
                Modifier.heightIn(min = if (action != null) 56.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text(
                    subtitle.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                action?.invoke()
            }
        }
    }
}

/**
 * Steps the hero title down from [max] until no word is split across lines: at 52 sp the wide face
 * fits few letters per line ("afternoon", "Καλησπέρα"). Lines may still break between words or
 * after a hyphen ("après-midi"). Runs at layout, so it measures the face that's really loaded.
 */
private data class WholeWords(val max: TextUnit) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        var size = max.value
        while (size > HERO_MIN_SP) {
            if (keepsWordsWhole(performLayout(constraints, text, size.sp), text.text)) return size.sp
            size -= HERO_STEP_SP
        }
        return HERO_MIN_SP.sp
    }
}

private fun keepsWordsWhole(layout: TextLayoutResult, text: String): Boolean = !layout.hasVisualOverflow &&
    (0 until layout.lineCount - 1).all { line ->
        val end = layout.getLineEnd(line)
        end <= 0 || end >= text.length || text[end - 1].isWhitespace() || text[end - 1] == '-' ||
            text[end].isWhitespace()
    }

private const val HERO_MIN_SP = 28f
private const val HERO_STEP_SP = 2f

/** The hero's round 96 dp action (Material's large FAB), in the tertiary container. */
@Composable
fun HeroAction(icon: Int, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LargeFloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier,
    ) {
        Icon(painterResource(icon), contentDescription = contentDescription, modifier = Modifier.size(40.dp))
    }
}

@PhonePreviews
@Composable
private fun HeroPreview() {
    DowntifyTheme {
        Surface {
            HeroHeader(title = "Good\nevening", subtitle = "Pick up where you left off")
        }
    }
}
