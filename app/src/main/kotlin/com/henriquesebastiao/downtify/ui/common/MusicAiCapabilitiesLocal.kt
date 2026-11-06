package com.henriquesebastiao.downtify.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto

/** Legacy servers keep their native Similar action; v1 servers govern each module. */
val LocalMusicAiCapabilities = staticCompositionLocalOf { MusicAiCapabilitiesDto() }

fun MusicAiCapabilitiesDto.similarityAvailable() = version != 1 || enabled("similarity") || enabled("external_beta")
