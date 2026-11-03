package com.henriquesebastiao.downtify.feature.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.henriquesebastiao.downtify.core.data.library.LibraryTrashRepository
import com.henriquesebastiao.downtify.core.data.library.TrashFailure
import com.henriquesebastiao.downtify.core.data.sync.SyncCoordinator
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.model.TrashPreview
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where moving songs to the trash is: the server checks them, the user confirms, it's done. */
sealed interface MoveToTrashState {
    data object Idle : MoveToTrashState

    data class Checking(val count: Int) : MoveToTrashState

    data class Confirm(val preview: TrashPreview, val retentionDays: Int, val confirming: Boolean = false) :
        MoveToTrashState

    /** Confirmed: [count] songs left the library and are on their way to the trash. */
    data class Done(val count: Int) : MoveToTrashState

    /** [code] is the server's reason (see [trashMessage]). */
    data class Failed(val code: String) : MoveToTrashState
}

/**
 * "Move to trash" from any song's menu. One for the whole app (it outlives the screen the song was
 * on); another pairing or account cancels whatever was under way, and the server checks the
 * account again on every step.
 */
@HiltViewModel
class MoveToTrashViewModel @Inject constructor(
    private val trash: LibraryTrashRepository,
    private val sync: SyncCoordinator,
) : ViewModel() {
    val canManage: StateFlow<Boolean> = trash.capability.map { it.canManage }
        .stateIn(viewModelScope, SharingStarted.Eagerly, trash.capability.value.canManage)

    private val mutable = MutableStateFlow<MoveToTrashState>(MoveToTrashState.Idle)
    val state: StateFlow<MoveToTrashState> = mutable.asStateFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch {
            trash.session.drop(1).collect {
                job?.cancel()
                mutable.value = MoveToTrashState.Idle
            }
        }
    }

    fun move(tracks: List<Track>) {
        if (!canManage.value || tracks.isEmpty() || mutable.value != MoveToTrashState.Idle) return
        job = viewModelScope.launch {
            mutable.value = MoveToTrashState.Checking(tracks.size)
            mutable.value = try {
                val preview = trash.preview(tracks.map { it.id }.distinct())
                MoveToTrashState.Confirm(preview, trash.capability.value.retentionDays)
            } catch (e: TrashFailure) {
                MoveToTrashState.Failed(e.code)
            }
        }
    }

    fun confirm() {
        val asked = mutable.value as? MoveToTrashState.Confirm ?: return
        if (asked.confirming) return
        job = viewModelScope.launch {
            mutable.value = asked.copy(confirming = true)
            mutable.value = try {
                val count = trash.confirm(asked.preview.confirmation)
                // The songs are already out of the library on the server: show it now.
                sync.requestSync()
                MoveToTrashState.Done(count)
            } catch (e: TrashFailure) {
                MoveToTrashState.Failed(e.code)
            }
        }
    }

    /** Closes whatever is showing; a check still running is dropped (nothing moves without a confirm). */
    fun dismiss() {
        if ((mutable.value as? MoveToTrashState.Confirm)?.confirming == true) return
        job?.cancel()
        mutable.value = MoveToTrashState.Idle
    }
}
