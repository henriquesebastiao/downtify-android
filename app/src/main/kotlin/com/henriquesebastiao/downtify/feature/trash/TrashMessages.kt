package com.henriquesebastiao.downtify.feature.trash

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.model.TrashState

/** What the server's `detail.code` means, in a few words; an unknown code says nothing was lost. */
@Composable
fun trashMessage(code: String): String = stringResource(
    when (code) {
        "admin_required", "admin_access_revoked" -> R.string.trash_error_admin

        "file_changed", "trash_file_changed", "file_changed_during_move" -> R.string.trash_error_changed

        "file_missing", "trash_file_missing" -> R.string.trash_error_missing

        "restore_collision" -> R.string.trash_error_collision

        "track_is_playing" -> R.string.trash_error_playing

        "library_task_active" -> R.string.trash_error_upgrade

        "confirmation_expired", "confirmation_invalid", "preview_unavailable" -> R.string.trash_error_confirmation

        "identity_uncertain" -> R.string.trash_error_identity

        "preview_busy", "operation_already_pending", "operation_in_progress", "operation_not_complete" ->
            R.string.trash_error_busy

        "retention_expired" -> R.string.trash_error_expired

        "unsupported_collection", "unsupported_lyrics_location", "invalid_path", "unsafe_symlink" ->
            R.string.trash_error_folder

        "root_unavailable", "nas_marker_missing", "nas_marker_changed", "filesystem_unavailable",
        "atomic_move_unsupported", "trash_permissions_unsafe", "trash_ignore_invalid",
        -> R.string.trash_error_storage

        "trash_not_configured" -> R.string.trash_error_off

        else -> R.string.trash_error
    },
)

@Composable
fun trashStateLabel(state: TrashState): String = stringResource(
    when (state) {
        TrashState.Pending -> R.string.trash_state_pending
        TrashState.Moving -> R.string.trash_state_moving
        TrashState.Trashed -> R.string.trash_state_trashed
        TrashState.RestorePending, TrashState.Restoring -> R.string.trash_state_restoring
        TrashState.Restored -> R.string.trash_state_restored
        TrashState.Purging, TrashState.Purged -> R.string.trash_state_expired
        TrashState.Error -> R.string.trash_state_error
        TrashState.Unknown -> R.string.trash_state_unknown
    },
)
