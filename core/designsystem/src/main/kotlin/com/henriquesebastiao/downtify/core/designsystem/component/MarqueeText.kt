package com.henriquesebastiao.downtify.core.designsystem.component

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing

/**
 * One line that, when it doesn't fit, slides to the start over and over, fading in and out at both
 * ends: a player's title, read whole without an ellipsis. A line that fits stays put, with no fade.
 */
@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
    var boxWidth by remember { mutableIntStateOf(0) }
    var textWidth by remember(text) { mutableIntStateOf(0) }
    val overflows = boxWidth in 1 until textWidth
    Box(
        modifier
            .onSizeChanged { boxWidth = it.width }
            .then(if (overflows) Modifier.fadeEdges(FADE_WIDTH) else Modifier),
    ) {
        Text(
            text,
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = 1,
            softWrap = false,
            // The marquee measures the text at its full width; it only moves when that is wider than the box.
            onTextLayout = { textWidth = it.size.width },
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = START_DELAY_MS),
        )
    }
}

/** Fades what's drawn to nothing over [width] at both ends, where the line comes in and goes out. */
private fun Modifier.fadeEdges(width: Dp) = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edge = (width.toPx() / size.width).coerceAtMost(0.5f)
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Transparent,
                edge to Color.Black,
                1f - edge to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

private val FADE_WIDTH = 16.dp
private const val START_DELAY_MS = 1_500

@PhonePreviews
@Composable
private fun MarqueeTextPreview() {
    DowntifyTheme {
        Surface {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                MarqueeText(
                    "January 4, 2024: Quadrantids",
                    Modifier.width(160.dp),
                    MaterialTheme.typography.titleMedium,
                )
                MarqueeText("Low Tide", Modifier.width(160.dp), MaterialTheme.typography.titleMedium)
            }
        }
    }
}
