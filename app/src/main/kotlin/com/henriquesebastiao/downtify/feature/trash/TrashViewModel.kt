package com.henriquesebastiao.downtify.feature.trash

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.henriquesebastiao.downtify.core.data.library.LibraryTrashRepository
import com.henriquesebastiao.downtify.core.data.library.TrashFailure
import com.henriquesebastiao.downtify.core.data.sync.SyncCoordinator
import com.henriquesebastiao.downtify.core.model.TrashItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TrashTab { Trash, Exclusions }

data class TrashUiState(
    val loading: Boolean = true,
    val canManage: Boolean = true,
    val tab: TrashTab = TrashTab.Trash,
    val items: List<TrashItem> = emptyList(),
    /** Every match, not only what is loaded. */
    val total: Int = 0,
    val bytes: Long = 0,
    val exclusions: List<TrashItem> = emptyList(),
    val retentionDays: Int = 30,
    /** Operations with a request of ours on the way. */
    val working: Set<String> = emptySet(),
    /** The server's code for the last thing that failed, until it's dismissed. */
    val error: String? = null,
) {
    /** Something is still moving: worth asking again soon. */
    val inFlight: Boolean get() = items.any { it.state.inFlight }
}

/** The music trash page (admins): what is in it, restoring it, and what downloads still skip. */
@OptIn(FlowPreview::class)
@HiltViewModel
class TrashViewModel @Inject constructor(
    private val trash: LibraryTrashRepository,
    private val sync: SyncCoordinator,
) : ViewModel() {
    private val mutable = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = mutable.asStateFlow()

    /** The search box's text: synchronous state, so no keystroke is lost. */
    var search by mutableStateOf("")
        private set

    private var loading: Job? = null

    init {
        viewModelScope.launch {
            trash.capability.collect { capability ->
                mutable.update { it.copy(canManage = capability.canManage, retentionDays = capability.retentionDays) }
            }
        }
        viewModelScope.launch {
            // Another pairing or account: what was shown isn't theirs.
            trash.session.drop(1).collect {
                loading?.cancel()
                mutable.value = TrashUiState(canManage = false)
                trash.refreshCapability()
                reload()
            }
        }
        viewModelScope.launch {
            snapshotFlow { search }.drop(1).debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged().collect { reload() }
        }
        viewModelScope.launch {
            trash.refreshCapability()
            reload()
        }
    }

    fun onSearch(value: String) {
        search = value
    }

    fun selectTab(tab: TrashTab) {
        if (tab == mutable.value.tab) return
        mutable.update { it.copy(tab = tab, loading = true) }
        reload()
    }

    /** Asks the server again for what is shown (and as much of it as is loaded). */
    fun reload() {
        loading?.cancel()
        loading = viewModelScope.launch { load(keep = mutable.value.items.size) }
    }

    fun loadMore() {
        val state = mutable.value
        if (loading?.isActive == true || state.tab != TrashTab.Trash || state.items.size >= state.total) return
        loading = viewModelScope.launch {
            try {
                val page = trash.page(state.items.size, search)
                mutable.update {
                    it.copy(items = (it.items + page.items).distinctBy(TrashItem::id), total = page.total)
                }
            } catch (e: TrashFailure) {
                fail(e)
            }
        }
    }

    fun restore(item: TrashItem) = act(item) { trash.restore(listOf(item.id)) }

    fun retry(item: TrashItem) = act(item) { trash.retry(listOf(item.id)) }

    fun allowDownload(item: TrashItem) = act(item) { trash.allowDownload(listOf(item.id)) }

    fun dismissError() = mutable.update { it.copy(error = null) }

    private suspend fun load(keep: Int) {
        val state = mutable.value
        if (!trash.capability.value.canManage) {
            mutable.update { it.copy(loading = false) }
            return
        }
        try {
            if (state.tab == TrashTab.Exclusions) {
                val items = trash.exclusions()
                mutable.update { it.copy(exclusions = items, loading = false) }
                return
            }
            // The first page, then as many more as were already shown.
            var page = trash.page(0, search)
            val items = page.items.toMutableList()
            while (items.size < keep && items.size < page.total && page.items.isNotEmpty()) {
                page = trash.page(items.size, search)
                items += page.items
            }
            mutable.update {
                it.copy(
                    items = items.distinctBy(TrashItem::id),
                    total = page.total,
                    bytes = page.bytes,
                    loading = false,
                )
            }
        } catch (e: TrashFailure) {
            fail(e)
        }
    }

    private fun act(item: TrashItem, request: suspend () -> Unit) {
        if (item.id in mutable.value.working) return
        viewModelScope.launch {
            mutable.update { it.copy(working = it.working + item.id) }
            try {
                request()
                sync.requestSync()
                load(keep = mutable.value.items.size)
            } catch (e: TrashFailure) {
                fail(e)
            } finally {
                mutable.update { it.copy(working = it.working - item.id) }
            }
        }
    }

    private fun fail(e: TrashFailure) {
        mutable.update { it.copy(loading = false, error = e.code) }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
