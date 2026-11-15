package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.musicai.DjLocalPlansRepository
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiRepository
import com.henriquesebastiao.downtify.core.model.DjLocalProblem
import com.henriquesebastiao.downtify.core.model.DjLocalRequest
import com.henriquesebastiao.downtify.core.model.DjLocalResult
import com.henriquesebastiao.downtify.core.model.DjLocalTrackStatus
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.network.session.Session
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** One service-owned subscription. Queue/source events trigger requests; playback ticks do not. */
@UnstableApi
internal class LocalDjProfiles(
    private val player: LocalDjPlayer,
    private val settings: LocalDjSettings,
    private val music: MusicAiRepository,
    private val library: LibraryRepository,
    private val repository: DjLocalPlansRepository,
    private val scope: CoroutineScope,
) {
    private val windows = MutableStateFlow(player.evidenceWindow())
    private var owner: Session? = null
    private var previous: LocalDjWindow? = null
    private var sources = emptyMap<String, Track>()
    private var transitionMs = 0L
    private var generation = 0L

    private data class Activation(val active: Boolean, val milliseconds: Long, val refresh: Long)
    private data class Request(
        val activation: Activation,
        val window: LocalDjWindow,
        val owner: Session?,
        val sources: List<Track?>,
        val enabled: Boolean,
        val protocol: Int,
    )

    fun start() {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                windows.value = this@LocalDjProfiles.player.evidenceWindow()
            }
        }
        player.addListener(listener)
        scope.launch {
            try {
                val activation = combine(
                    settings.device,
                    settings.mode,
                    settings.armed,
                    settings.transitionSeconds,
                    settings.preparationRevision,
                ) { device, mode, armed, seconds, refresh ->
                    Activation(device && mode == LocalDjMode.Dj && armed, seconds * 1000L, refresh)
                }
                combine(activation, windows, music.session, library.library, music.capabilities) {
                        active,
                        window,
                        session,
                        snapshot,
                        caps,
                    ->
                    Request(
                        active,
                        window,
                        session,
                        window.ids.map { snapshot?.byId?.get(it) },
                        caps.enabled("dj_interactive"),
                        caps.dj.localPlansVersion,
                    )
                }.distinctUntilChanged().collectLatest { request ->
                    process(request)
                }
            } finally {
                player.removeListener(listener)
            }
        }
    }

    private suspend fun process(request: Request) {
        val revision = ++generation
        if (!request.activation.active || request.owner == null || !request.enabled) {
            if (owner != null) player.clearEvidence()
            owner = null
            previous = null
            sources = emptyMap()
            settings.publishReadiness(LocalDjReadiness())
            return
        }
        val window = request.window
        val cohort = request.sources.filterNotNull().associateBy { it.id }
        val sameScope = owner == request.owner && previous?.generation == window.generation &&
            previous?.revision == window.revision && transitionMs == request.activation.milliseconds
        if (sameScope) {
            player.retainEvidence(window, cohort.filter { (id, value) -> sources[id] == value }.keys)
        } else {
            player.clearEvidence()
        }
        owner = request.owner
        previous = window
        sources = cohort
        transitionMs = request.activation.milliseconds
        val unavailable = localDjWindowProblem(window, request.protocol, request.sources.all { it != null })
        if (unavailable != null) {
            player.clearEvidence()
            settings.publishReadiness(LocalDjReadiness(problem = unavailable))
            return
        }
        settings.publishReadiness(LocalDjReadiness(preparing = true, ready = player.currentPlanReady(window)))
        val result = try {
            repository.load(DjLocalRequest(window.ids, transitionMs, revision))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DjLocalResult.Unavailable(DjLocalProblem.Network)
        }
        coroutineContext.ensureActive()
        if (!valid(request, revision)) {
            return
        }
        applyResult(request.window, result)
    }

    private fun applyResult(window: LocalDjWindow, result: DjLocalResult) {
        when (result) {
            is DjLocalResult.Unavailable -> settings.publishReadiness(
                LocalDjReadiness(ready = player.currentPlanReady(window), problem = result.problem),
            )

            is DjLocalResult.Available -> {
                val body = result.value
                val analyses = body.tracks.mapNotNull { row ->
                    row.analysis?.let { row.id to LocalDjAnalysis(it.bpm, it.downbeatsMs, it.confidence, it.revision) }
                }.toMap()
                val plans = body.plans.filter { it.ready }.map { plan ->
                    val first = body.tracks[plan.fromIndex]
                    val second = body.tracks[plan.toIndex]
                    LocalDjVerifiedPlan(
                        window.indices[plan.fromIndex],
                        window.indices[plan.toIndex],
                        first.id,
                        second.id,
                        checkNotNull(first.analysis).revision,
                        checkNotNull(second.analysis).revision,
                        LocalDjPlan(
                            plan.outgoingStartMs,
                            plan.incomingCueMs,
                            plan.durationMs,
                            plan.speed,
                            LocalDjFallback.None,
                        ),
                    )
                }
                if (player.updateEvidence(window, analyses, plans)) {
                    val ready = player.currentPlanReady(window)
                    val problem = when {
                        ready -> null

                        body.tracks.take(2).any {
                            it.status == DjLocalTrackStatus.Stale
                        } -> DjLocalProblem.StaleAnalysis

                        body.tracks.take(2).any {
                            it.status == DjLocalTrackStatus.Missing
                        } -> DjLocalProblem.MissingAnalysis

                        else -> DjLocalProblem.UnreliableAnalysis
                    }
                    settings.publishReadiness(
                        LocalDjReadiness(
                            ready = ready,
                            problem = problem,
                        ),
                    )
                }
            }
        }
    }

    private fun valid(request: Request, revision: Long) = generation == revision &&
        music.session.value == request.owner && player.evidenceWindow() == request.window &&
        settings.device.value && settings.armed.value && settings.mode.value == LocalDjMode.Dj &&
        settings.transitionSeconds.value * 1000L == request.activation.milliseconds &&
        settings.preparationRevision.value == request.activation.refresh &&
        music.capabilities.value.enabled("dj_interactive") &&
        music.capabilities.value.dj.localPlansVersion == request.protocol &&
        request.window.ids.map { library.library.value?.byId?.get(it) } == request.sources
}
