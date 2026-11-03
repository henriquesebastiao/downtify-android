package com.henriquesebastiao.downtify.feature.player

import androidx.compose.runtime.Composable
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls

/**
 * The cover of what's playing at [size]: an episode's artwork is its publisher's own and a
 * stream's is the address its search result gave; a library track's comes from the server.
 * The player's surfaces seed their colors from the small one, so they all read the same cover.
 */
@Composable
fun playingCoverUrl(state: PlayerState, size: Int = ServerUrls.COVER_SMALL): String? =
    state.episode?.artworkUrl?.ifBlank { null }
        ?: state.stream?.coverUrl?.ifBlank { null }
        ?: state.track?.let { LocalCoverUrls.current.track(it.id.takeIf { _ -> it.hasCover }, size) }
