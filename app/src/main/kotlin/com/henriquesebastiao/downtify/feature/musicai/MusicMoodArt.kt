package com.henriquesebastiao.downtify.feature.musicai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Native vector artwork: no network request, bitmap decoding or generated image dependency. */
@Composable
internal fun MusicMoodArt(
    mood: String,
    cadence: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    compact: Boolean = false,
) {
    val tint = moodColors[mood] ?: Color(0xFF0F766E)
    val dark = Color(tint.red * .48f, tint.green * .48f, tint.blue * .48f)
    Box(modifier.background(Brush.linearGradient(listOf(tint, dark)))) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = .08f), size.width * .6f, Offset(size.width, size.height * .1f))
            drawCircle(
                Color.White.copy(alpha = .06f),
                size.width * .5f,
                Offset(size.width * .9f, size.height * .15f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
            )
        }
        Column(
            Modifier.fillMaxSize().padding(if (compact) 12.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (cadence ==
                    "daily"
                ) {
                    "MIX DU JOUR"
                } else {
                    "MIX DE LA SEMAINE"
                },
                color = Color.White.copy(alpha = .8f),
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.weight(1f))
            Text(
                moodNames[mood] ?: "Pour vous",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = .88f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
