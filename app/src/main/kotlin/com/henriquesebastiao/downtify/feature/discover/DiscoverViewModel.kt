package com.henriquesebastiao.downtify.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.henriquesebastiao.downtify.core.data.ServerResult
import com.henriquesebastiao.downtify.core.data.catalog.CatalogRepository
import com.henriquesebastiao.downtify.core.data.discover.DiscoverRepository
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.model.DiscoverArtist
import com.henriquesebastiao.downtify.core.model.DiscoverCollections
import com.henriquesebastiao.downtify.core.model.DiscoverInput
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.player.RemotePlayback
import com.henriquesebastiao.downtify.ui.common.LoadError
import com.henriquesebastiao.downtify.ui.common.loadError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoverUiState(
    val loading: Boolean = true,
    val libraryEmpty: Boolean = false,
    val artists: List<DiscoverArtist> = emptyList(),
    /** Deezer couldn't be asked about some artists: the list is shorter than usual. */
    val partial: Boolean = false,
    val collections: DiscoverCollections? = null,
    val collectionsLoading: Boolean = false,
    val error: LoadError? = null,
    val showAll: Boolean = false,
) {
    /** The best twelve until "Show all". */
    val shownArtists: List<DiscoverArtist> get() = if (showAll) artists else artists.take(TOP_ARTISTS)

    companion object {
        const val TOP_ARTISTS = 12
    }
}

sealed interface DiscoverMessage {
    /** An artist was hidden: offer to undo. */
    data class Hidden(val artist: DiscoverArtist) : DiscoverMessage
    data object Failed : DiscoverMessage
}

/** Artists, albums and playlists suggested from the library. */
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val discover: DiscoverRepository,
    private val library: LibraryRepository,
    private val catalog: CatalogRepository,
    private val remotePlayback: RemotePlayback,
) : ViewModel() {
    private val state = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = state.asStateFlow()

    private val messageChannel = Channel<DiscoverMessage>(Channel.BUFFERED)
    val messages: Flow<DiscoverMessage> = messageChannel.receiveAsFlow()

    private var loading: Job? = null

    init {
        load()
    }

    /** Asks again (the server re-ranks with the latest library, likes and listens). */
    fun refresh() = load()

    fun showAll() = state.update { it.copy(showAll = true) }

    /** Hear a suggestion first: plays the artist's top server match in full. */
    fun preview(artist: DiscoverArtist) {
        viewModelScope.launch {
            val songs = (catalog.search(artist.name) as? ServerResult.Ok)?.value?.songs.orEmpty()
            if (songs.isEmpty()) {
                messageChannel.send(DiscoverMessage.Failed)
                return@launch
            }
            val played = remotePlayback.play(songs, 0, PlaybackContext(PlaybackContextType.Songs, "", ""))
            if (!played) messageChannel.send(DiscoverMessage.Failed)
        }
    }

    /** "Not interested": gone at once; undone if the server refuses. */
    fun hide(artist: DiscoverArtist) {
        state.update { it.copy(artists = it.artists - artist) }
        viewModelScope.launch {
            if (discover.hide(artist.name) is ServerResult.Ok) {
                messageChannel.send(DiscoverMessage.Hidden(artist))
            } else {
                state.update { it.copy(artists = (it.artists + artist).sortedByOriginal(artist)) }
                messageChannel.send(DiscoverMessage.Failed)
            }
        }
    }

    fun undoHide(artist: DiscoverArtist) {
        viewModelScope.launch {
            if (discover.show(artist.name) is ServerResult.Ok) load() else messageChannel.send(DiscoverMessage.Failed)
        }
    }

    // A refused hide puts the artist back first: its old place isn't known any more.
    private fun List<DiscoverArtist>.sortedByOriginal(first: DiscoverArtist) = listOf(first) + (this - first)

    private fun load() {
        loading?.cancel()
        state.update { it.copy(loading = true, error = null) }
        loading = viewModelScope.launch {
            // The suggestions are built from the synced library.
            val snapshot = library.library.filterNotNull().first()
            if (snapshot.isEmpty) {
                state.value = DiscoverUiState(loading = false, libraryEmpty = true)
                return@launch
            }
            val artists = discover.artists()
            val ok = (artists as? ServerResult.Ok)?.value
            state.update {
                it.copy(
                    loading = false,
                    artists = ok?.artists ?: it.artists,
                    partial = ok?.partial ?: false,
                    error = artists.loadError(),
                    collectionsLoading = ok != null,
                )
            }
            if (ok == null) return@launch
            // Albums and playlists come a few seconds after the artists, as on the web page.
            val collections = discover.collections()
            state.update { current ->
                val found = (collections as? ServerResult.Ok)?.value
                current.copy(
                    collections = found ?: current.collections,
                    // Each suggestion opens its Spotify page when the server found it by exact name.
                    artists = found?.let { DiscoverInput.withLinks(current.artists, it.artistUrls) } ?: current.artists,
                    partial = current.partial || found?.partial == true,
                    collectionsLoading = false,
                )
            }
        }
    }
}
