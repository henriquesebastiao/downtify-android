package com.henriquesebastiao.downtify.feature.musicai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiFailure
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.data.sync.SyncCoordinator
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.network.dto.MusicAiPlaylistDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiTrackDto
import com.henriquesebastiao.downtify.core.player.PlayerController
import com.henriquesebastiao.downtify.feature.collection.CollectionKind
import com.henriquesebastiao.downtify.feature.collection.CollectionUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MusicAiUiState(
    val preferences: Map<String, Boolean> = emptyMap(),
    val global: Map<String, Boolean> = emptyMap(),
    val tracks: List<MusicAiTrackDto> = emptyList(),
    val playlists: List<MusicAiPlaylistDto> = emptyList(),
    val cadence: String = "daily",
    val selectedMood: String = "",
    val source: String = "personalization",
    val busy: Boolean = false,
    val message: String = "",
    val selectionId: String = "",
)

@HiltViewModel
class MusicAiViewModel @Inject constructor(
    private val music: MusicAiRepository,
    private val library: LibraryRepository,
    private val player: PlayerController,
    private val sync: SyncCoordinator,
) : ViewModel() {
    val capabilities = music.capabilities
    private val mutable = MutableStateFlow(MusicAiUiState())
    val state = mutable.asStateFlow()
    val collectionState = combine(state, library.library, library.likedIds, player.nowPlaying) {
            selection,
            snapshot,
            liked,
            playing,
        ->
        val tracks = snapshot?.tracks(selection.tracks.map { it.id }).orEmpty()
        CollectionUiState(
            kind = CollectionKind.Playlist,
            loading = snapshot == null || (selection.busy && selection.tracks.isEmpty()),
            title = selectionTitle(selection),
            artist = "Playlist temporaire · ${if (selection.source == "weekly") "de la semaine" else "du jour"}",
            tracks = tracks,
            coverTrackId = tracks.firstOrNull { it.hasCover }?.id,
            currentTrackId = playing.trackId,
            isPlaying = playing.isPlaying,
            isCurrentContext = selection.selectionId.isNotBlank() && playing.context == selectionContext(selection),
            likedIds = liked.toSet(),
        )
    }.flowOn(
        Dispatchers.Default,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CollectionUiState(kind = CollectionKind.Playlist))
    private var job: Job? = null

    init {
        viewModelScope.launch {
            music.session.drop(1).collect {
                job?.cancel()
                mutable.value = MusicAiUiState()
            }
        }
    }

    private fun run(work: suspend () -> Unit) {
        if (mutable.value.busy) return
        job = viewModelScope.launch {
            mutable.update { it.copy(busy = true, message = "") }
            try {
                work()
            } catch (error: MusicAiFailure) {
                mutable.update {
                    it.copy(
                        message = musicAiFailureMessage(error),
                    )
                }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }

    fun refresh() = run {
        music.refresh()
        if (capabilities.value.version == 1) {
            val preferences = music.preferences()
            val global = if (capabilities.value.administrator) music.globalFeatures() else emptyMap()
            mutable.update { it.copy(preferences = preferences, global = global) }
        }
    }
    fun toggle(feature: String, enabled: Boolean, global: Boolean = false) = run {
        if (global) music.setGlobal(feature, enabled) else music.set(feature, enabled)
        val preferences = music.preferences()
        val flags = if (capabilities.value.administrator) music.globalFeatures() else emptyMap()
        mutable.update { it.copy(preferences = preferences, global = flags, tracks = emptyList()) }
    }
    fun refreshPlaylists() = run {
        music.refresh()
        val caps = capabilities.value
        val playlists = if (caps.enabled("daily") || caps.enabled("weekly")) music.playlists() else emptyList()
        mutable.update {
            it.copy(
                playlists = playlists,
                cadence = if (caps.enabled(it.cadence)) {
                    it.cadence
                } else if (caps.enabled("daily")) {
                    "daily"
                } else {
                    "weekly"
                },
                tracks = if (caps.enabled(it.source)) it.tracks else emptyList(),
            )
        }
    }
    fun refreshHome() = run {
        music.refresh()
        if (listOf("daily", "weekly", "personalization").any(capabilities.value::enabled)) {
            val cards = music.home()
            mutable.update {
                it.copy(
                    playlists = cards,
                    message = if (music.homeStale) {
                        "Actualisation différée ; vos dernières playlists restent disponibles."
                    } else {
                        ""
                    },
                )
            }
        }
    }
    fun openPlaylist(cadence: String, mood: String) = run {
        mutable.update { it.copy(source = cadence, cadence = cadence, selectedMood = mood, tracks = emptyList()) }
        music.refresh()
        if (!capabilities.value.enabled(cadence)) {
            mutable.update {
                it.copy(message = "Cette fonction est désactivée. Retrouvez ses options dans Paramètres → IA.")
            }
        } else {
            val tracks = music.playlist(cadence, mood)
            mutable.update {
                it.copy(
                    tracks = tracks,
                    source = cadence,
                    cadence = cadence,
                    selectedMood = mood,
                    selectionId = java.util.UUID.randomUUID().toString(),
                    message = if (tracks.isEmpty()) "Cette playlist est en préparation." else "",
                )
            }
        }
    }
    fun selectCadence(value: String) {
        if (value in listOf("daily", "weekly") && capabilities.value.enabled(value)) {
            mutable.update { it.copy(cadence = value) }
        }
    }
    fun clearSelection() {
        if (!mutable.value.busy) {
            mutable.update { it.copy(tracks = emptyList(), selectedMood = "", message = "") }
        }
    }
    fun forYou() = run {
        val tracks = music.recommendations()
        mutable.update {
            it.copy(
                tracks = tracks,
                source = "personalization",
                selectedMood = "",
                selectionId = java.util.UUID.randomUUID().toString(),
            )
        }
    }
    fun mood(cadence: String, mood: String) = run {
        val tracks = music.playlist(cadence, mood)
        mutable.update {
            it.copy(
                tracks = tracks,
                source = cadence,
                cadence = cadence,
                selectedMood = mood,
                selectionId = java.util.UUID.randomUUID().toString(),
            )
        }
    }
    fun save(name: String) = run {
        val selection = mutable.value
        val saved = music.save(name.trim(), selection.tracks.map { it.id }, selection.source)
        sync.requestSync()
        mutable.update { it.copy(message = "Playlist enregistrée : ${saved.name}") }
    }
    fun forget() = run {
        music.forget()
        mutable.update { it.copy(tracks = emptyList(), message = "Profil d’écoute effacé.") }
    }
    fun play(row: MusicAiTrackDto, queue: Boolean = false) {
        val snapshot = library.library.value ?: return
        val track = snapshot.byId[row.id]
        if (track == null) {
            sync.requestSync()
            mutable.update { it.copy(message = "La bibliothèque est en cours d’actualisation.") }
            return
        }
        if (queue) {
            player.addToQueue(track)
        } else {
            val tracks = snapshot.tracks(mutable.value.tracks.map { it.id })
            player.play(
                tracks,
                tracks.indexOf(track).coerceAtLeast(0),
                selectionContext(mutable.value),
            )
        }
    }

    fun playSelection(index: Int, shuffle: Boolean = false) {
        val selection = mutable.value
        val tracks = library.library.value?.tracks(selection.tracks.map { it.id }).orEmpty()
        if (tracks.isNotEmpty()) {
            player.play(
                tracks,
                index.coerceIn(tracks.indices),
                selectionContext(selection),
                shuffle,
            )
        }
    }

    fun playOrPause() {
        if (collectionState.value.isCurrentContext) player.togglePlayPause() else playSelection(0)
    }

    fun addToQueue(track: Track) = player.addToQueue(track)
    fun toggleLike(track: Track) {
        viewModelScope.launch { library.setLiked(track.id, track.id !in library.likedIds.value) }
    }

    private fun selectionTitle(selection: MusicAiUiState) = if (selection.selectedMood.isBlank()) {
        "À écouter maintenant"
    } else {
        mixTitle(
            selection.source,
            selection.selectedMood,
        )
    }

    private fun selectionContext(selection: MusicAiUiState) =
        PlaybackContext(PlaybackContextType.Songs, selection.selectionId, selectionTitle(selection))
}
