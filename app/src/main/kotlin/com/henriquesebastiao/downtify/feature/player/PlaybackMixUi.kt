package com.henriquesebastiao.downtify.feature.player

import android.animation.ValueAnimator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.core.designsystem.component.Artwork
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.core.player.LocalDjPhase
import com.henriquesebastiao.downtify.core.player.LocalDjTransition
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls

@Composable
internal fun PlaybackMixSelector(selected: PlaybackMixMode, onSelect: (PlaybackMixMode) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            PlaybackMixMode.Disabled to "Désactivé",
            PlaybackMixMode.Transitions to "Transitions",
            PlaybackMixMode.Dj to "DJ · Bêta",
        ).forEach { (mode, title) ->
            FilterChip(selected = selected == mode, onClick = { onSelect(mode) }, label = { Text(title) })
        }
    }
}

/** Driven by the audible overlap, never by preparation or a decorative timer. */
@Composable
internal fun PlaybackOverlap(state: PlayerState, local: LocalDjTransition) {
    val server = state.dj
    val serverProgress = server?.transition
    val progress = when {
        serverProgress != null -> serverProgress
        server == null && local.phase == LocalDjPhase.Mixing -> local.progress
        else -> return
    }.coerceIn(0f, 1f)
    val outgoing = if (server != null) server.trackId else local.outgoingId
    val incoming = if (server != null) server.nextTrackId else local.incomingId
    if (outgoing == null || incoming == null) return
    val title = if (server != null) {
        server.nextTitle
    } else {
        state.upNext.firstOrNull { it.trackId == incoming }?.title
            ?: state.track?.takeIf { it.id == incoming }?.displayTitle.orEmpty()
    }
    val covers = LocalCoverUrls.current
    // Respect the platform's reduced-motion setting; the progress remains useful information.
    val motion = ValueAnimator.areAnimatorsEnabled()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Artwork(
                url = covers.track(outgoing, ServerUrls.COVER_SMALL),
                crossfade = motion,
                contentDescription = null,
                modifier = Modifier.size(48.dp).alpha(if (motion) 1f - .15f * progress else 1f)
                    .semantics { contentDescription = "Morceau sortant" },
            )
            Artwork(
                url = covers.track(incoming, ServerUrls.COVER_SMALL),
                crossfade = motion,
                contentDescription = null,
                modifier = Modifier.size(48.dp).alpha(if (motion) .85f + .15f * progress else 1f)
                    .semantics { contentDescription = "Morceau entrant : $title" },
            )
            Text(
                "Morceau entrant\n${title.ifBlank {
                    "Transition en cours"
                }}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}
