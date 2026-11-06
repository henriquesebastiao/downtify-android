package com.henriquesebastiao.downtify.core.player

import com.henriquesebastiao.downtify.core.data.di.ApplicationScope
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiFailure
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto
import com.henriquesebastiao.downtify.core.network.session.Session
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class DjPhase { Classic, Preparing, Ready, Active, Returning }

/** Preparation survives navigation; every entry point shares the native player. */
@Singleton
class DjPlaybackCoordinator @Inject constructor(
    private val music: MusicAiRepository,
    private val player: PlayerController,
    private val library: LibraryRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    val capabilities = music.capabilities
    private val mutableMessage = MutableStateFlow("")
    val message = mutableMessage.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableArmed = MutableStateFlow(false)
    val armed = mutableArmed.asStateFlow()
    private val mutablePhase = MutableStateFlow(DjPhase.Classic)
    val phase = mutablePhase.asStateFlow()
    private var generation = 0L
    private var preparation: Job? = null
    private var pendingId: String? = null
    private var pendingRequest: String? = null
    private var originKey: String? = null
    private var originIndex = -1
    private var activeId: String? = null
    private var activeMode: String? = null
    private var activeSelection = -1L

    init {
        scope.launch {
            music.session.drop(1).collect {
                cancel(false)
                mutableMessage.value = ""
            }
        }
        scope.launch {
            player.selectionVersion.drop(1).collect { version ->
                if (mutableBusy.value || mutableArmed.value) {
                    cancel(true)
                } else if (activeId != null && version != activeSelection) {
                    val retired = activeId
                    val stopRemote = activeMode == "interactive"
                    val owner = music.session.value
                    activeId = null
                    mutablePhase.value = DjPhase.Classic
                    scope.launch {
                        if (stopRemote && owner == music.session.value &&
                            retired != null
                        ) {
                            try {
                                music.djStop(retired)
                            } catch (_: MusicAiFailure) { }
                        }
                    }
                }
            }
        }
        scope.launch {
            player.state.collect { state ->
                if (pendingId != null && state.dj?.id == pendingId) {
                    pendingId = null
                    pendingRequest = null
                    activeId = state.dj?.id
                    activeMode = state.dj?.mode
                    activeSelection =
                        player.selectionVersion.value
                    mutableArmed.value = false
                    mutablePhase.value = DjPhase.Active
                } else if (activeId != null && state.dj?.id == activeId) {
                    mutablePhase.value = DjPhase.Active
                } else if (activeId != null && mutablePhase.value == DjPhase.Active) {
                    // System controllers can change media without going through
                    // the app's selectionVersion. Retire that transport too.
                    val retired = activeId
                    val stopRemote = activeMode == "interactive"
                    val owner = music.session.value
                    activeId = null
                    mutablePhase.value = DjPhase.Classic
                    if (retired != null) {
                        player.exitDj(retired)
                        scope.launch {
                            if (stopRemote &&
                                owner == music.session.value
                            ) {
                                try {
                                    music.djStop(retired)
                                } catch (_: MusicAiFailure) { }
                            }
                        }
                    }
                } else if ((mutableBusy.value || mutableArmed.value) &&
                    leftOrigin(state.mediaKey, state.currentIndex)
                ) {
                    cancel(true)
                }
            }
        }
    }

    fun toggleQueue() {
        if (hasDjWork()) {
            stop()
            return
        }
        val snapshot = player.state.value
        val defer = snapshot.track != null
        val ids = (if (defer) emptyList() else listOfNotNull(snapshot.track?.id)) + snapshot.upNext.map { it.trackId }
        if (ids.size !in 2..40 || ids.any { library.library.value?.byId?.containsKey(it) != true }) {
            mutableMessage.value = "Choisissez de 2 à 40 titres locaux à suivre."
            return
        }
        prepare("Mode DJ · votre file", defer) { nonce -> music.djQueue(ids, nonce) }
    }

    fun startTheme(theme: String, style: String) {
        if (hasDjWork()) {
            stop()
            return
        }
        prepare("DJ continu · $theme", false) { nonce -> music.djContinuous(theme, style, nonce) }
    }

    fun startSeed(seed: String) {
        if (!music.capabilities.value.djSeedMix) {
            mutableMessage.value = "Ce serveur ne prend pas encore en charge les mixes DJ à partir d’un titre."
            return
        }
        if (mutableBusy.value || mutableArmed.value || player.state.value.dj != null) stop()
        val owner = music.session.value
        val mine = generation
        val selection = player.selectionVersion.value
        scope.launch {
            val classic = withTimeoutOrNull(2000) { player.state.first { it.dj == null } } ?: return@launch
            if (!matches(Preparation(owner, mine, selection, ""))) {
                return@launch
            }
            val defer = classic.track?.id == seed
            prepare("DJ · à partir de ce titre", defer, preserveQueue = false) { nonce ->
                music.djSeed(seed, nonce, skipSeed = defer)
            }
        }
    }

    private data class Preparation(val owner: Session?, val generation: Long, val selection: Long, val request: String)

    private fun leftOrigin(key: String?, index: Int) = key != originKey || index != originIndex

    private fun hasDjWork(): Boolean {
        val pending = mutableBusy.value || mutableArmed.value
        return activeId != null || player.state.value.dj != null || pending
    }

    private fun matches(token: Preparation) = token.generation == generation &&
        token.owner == music.session.value && token.selection == player.selectionVersion.value

    private fun prepare(
        title: String,
        defer: Boolean,
        preserveQueue: Boolean = true,
        create: suspend (String) -> MusicDjSessionDto,
    ) {
        val owner = music.session.value ?: return
        val token = Preparation(owner, ++generation, player.selectionVersion.value, UUID.randomUUID().toString())
        originKey = player.state.value.mediaKey
        originIndex = player.state.value.currentIndex
        pendingRequest = token.request
        mutableBusy.value = true
        mutablePhase.value = DjPhase.Preparing
        mutableMessage.value = "Préparation des transitions ; votre écoute continue."
        preparation = scope.launch { performPreparation(token, title, defer, preserveQueue, create) }
    }

    private suspend fun performPreparation(
        token: Preparation,
        title: String,
        defer: Boolean,
        preserveQueue: Boolean,
        create: suspend (String) -> MusicDjSessionDto,
    ) {
        var created: String? = null
        try {
            if (!music.capabilities.value.interactiveAvailable()) {
                mutableMessage.value = "Activez le DJ direct dans Paramètres → IA. " +
                    "Le moteur doit être disponible ou autorisé en essai bêta."
                return
            }
            val result = create(token.request)
            created = result.id
            if (!matches(token)) {
                if (token.owner == music.session.value) music.djStop(result.id)
                return
            }
            pendingId = result.id
            if (awaitReady(token, result.id, title, defer, preserveQueue)) return
            if (token.owner == music.session.value) music.djStop(result.id)
            mutableMessage.value = "Préparation trop longue. La lecture classique reste disponible."
        } catch (error: CancellationException) {
            if (created != null) cleanupPreparation(token, created)
            throw error
        } catch (_: MusicAiFailure) {
            if (token.generation == generation) {
                mutableMessage.value = "Le DJ est temporairement indisponible. " +
                    "Votre lecture classique reste disponible."
                cleanupPreparation(token, created)
            }
        } finally {
            if (token.generation == generation) {
                mutableBusy.value = false
                if (!mutableArmed.value && mutablePhase.value != DjPhase.Active) {
                    pendingId = null
                    pendingRequest = null
                    mutablePhase.value = DjPhase.Classic
                }
            }
        }
    }

    private suspend fun awaitReady(
        token: Preparation,
        id: String,
        title: String,
        defer: Boolean,
        preserveQueue: Boolean,
    ): Boolean {
        repeat(450) {
            delay(2000)
            if (!matches(token)) return true
            val current = music.djSession(id)
            if (current.state in setOf("failed", "stopped", "cancelled", "deleted")) throw MusicAiFailure()
            if (current.state in setOf("playing", "paused")) {
                activateReady(token, current, title, defer, preserveQueue)
                return true
            }
        }
        return false
    }

    private suspend fun activateReady(
        token: Preparation,
        current: MusicDjSessionDto,
        title: String,
        defer: Boolean,
        preserveQueue: Boolean,
    ) {
        if (defer) {
            if (current.state == "playing") music.djCommand(current.id, "pause")
            if (!matches(token)) return
            mutableArmed.value = true
            mutablePhase.value = DjPhase.Ready
            player.queueDj(
                current.id,
                title,
                (current.duration * 1000).toLong(),
                sessionPlan = current,
                preserveQueue = preserveQueue,
            )
            mutableMessage.value = "Mode DJ prêt · relais au prochain titre."
        } else {
            pendingId = null
            pendingRequest = null
            activeId = current.id
            activeMode = current.mode
            activeSelection = token.selection + 1
            mutableBusy.value = false
            mutablePhase.value = DjPhase.Ready
            player.playDj(
                current.id,
                title,
                (current.duration * 1000).toLong(),
                "interactive",
                expectedSelection = token.selection,
                sessionPlan = current,
            )
            mutableMessage.value = "DJ actif dans le lecteur."
        }
    }

    private fun cleanupPreparation(token: Preparation, created: String?) {
        if (token.owner != music.session.value) return
        scope.launch {
            try {
                if (created == null) music.djCancelRequest(token.request) else music.djStop(created)
            } catch (_: MusicAiFailure) { }
        }
    }

    private fun cancel(remote: Boolean) {
        val owner = music.session.value
        val id = pendingId
        val request = pendingRequest
        generation++
        preparation?.cancel()
        preparation = null
        pendingId = null
        pendingRequest = null
        activeId = null
        activeMode = null
        mutableBusy.value = false
        mutableArmed.value = false
        mutablePhase.value = DjPhase.Classic
        if (remote) mutableMessage.value = "Préparation DJ annulée. Votre sélection classique continue."
        if (id != null) player.exitDj(id)
        if (remote) {
            scope.launch {
                if (owner != music.session.value) return@launch
                try {
                    if (request !=
                        null
                    ) {
                        music.djCancelRequest(request)
                    }
                    if (id != null) music.djStop(id)
                } catch (_: MusicAiFailure) { }
            }
        }
    }

    fun stop() {
        val id = player.state.value.dj?.id ?: activeId
        val stopRemote = (player.state.value.dj?.mode ?: activeMode) == "interactive"
        val owner = music.session.value
        cancel(true)
        if (id != null) {
            mutablePhase.value = DjPhase.Returning
            player.exitDj(id)
            mutablePhase.value = DjPhase.Classic
            scope.launch {
                if (stopRemote &&
                    owner == music.session.value
                ) {
                    try {
                        music.djStop(id)
                    } catch (_: MusicAiFailure) { }
                }
            }
        }
        mutableMessage.value = "Mode DJ désactivé. Votre file reste disponible."
    }

    fun listen(session: MusicDjSessionDto, title: String) {
        val previous = activeId ?: player.state.value.dj?.id
        val stopPrevious = (activeMode ?: player.state.value.dj?.mode) == "interactive"
        if (previous == session.id) {
            player.resume()
            return
        }
        cancel(true)
        val owner = music.session.value
        if (previous != null &&
            stopPrevious
        ) {
            scope.launch {
                if (owner ==
                    music.session.value
                ) {
                    try {
                        music.djStop(previous)
                    } catch (_: MusicAiFailure) { }
                }
            }
        }
        val selection = player.selectionVersion.value
        activeId = session.id
        activeMode = session.mode
        activeSelection = selection + 1
        mutablePhase.value = DjPhase.Ready
        mutableMessage.value = "DJ actif dans le lecteur."
        player.playDj(
            session.id,
            title,
            (session.duration * 1000).toLong(),
            session.mode,
            expectedSelection = selection,
            sessionPlan = session,
        )
    }
}
