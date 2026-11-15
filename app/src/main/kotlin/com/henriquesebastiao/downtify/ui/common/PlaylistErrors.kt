package com.henriquesebastiao.downtify.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.data.playlists.PlaylistEditError

/**
 * What a refused playlist write says, in the user's words: a permission is a message (never a
 * crash), the reference limit and the warming-up sync index say what to do about them.
 */
@Composable
fun playlistErrorMessage(error: PlaylistEditError): String = when (error) {
    PlaylistEditError.NotSupported -> stringResource(R.string.pl_error_unsupported)
    PlaylistEditError.Forbidden -> stringResource(R.string.pl_error_forbidden)
    PlaylistEditError.TooManyTracks -> stringResource(R.string.pl_error_limit)
    PlaylistEditError.IndexNotReady -> stringResource(R.string.pl_error_index)
    PlaylistEditError.NotFound -> stringResource(R.string.pl_error_not_found)
    PlaylistEditError.Stale -> stringResource(R.string.pl_error_stale)
    PlaylistEditError.Offline -> stringResource(R.string.pl_error_offline)
    is PlaylistEditError.Failed -> stringResource(R.string.pl_error_generic)
    PlaylistEditError.InvalidName, PlaylistEditError.NoTracks -> stringResource(R.string.pl_error_generic)
}
