package com.henriquesebastiao.downtify.core.data.musicai

import com.henriquesebastiao.downtify.core.data.di.ApplicationScope
import com.henriquesebastiao.downtify.core.model.DjLocalPlanCache
import com.henriquesebastiao.downtify.core.model.DjLocalProblem
import com.henriquesebastiao.downtify.core.model.DjLocalRequest
import com.henriquesebastiao.downtify.core.model.DjLocalResult
import com.henriquesebastiao.downtify.core.network.session.Session
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One bounded in-memory window; no source files, periodic poller or durable listening profile. */
@Singleton
class DjLocalPlansRepository @Inject constructor(
    private val music: MusicAiRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val cache = DjLocalPlanCache<Session>({ System.nanoTime() / 1_000_000 })
    private var cacheOwner: Session? = null

    init {
        scope.launch {
            music.session.collect { owner ->
                mutex.withLock {
                    if (owner == music.session.value && owner != cacheOwner) {
                        cache.clear()
                        cacheOwner = owner
                    }
                }
            }
        }
    }

    suspend fun load(request: DjLocalRequest): DjLocalResult = withContext(Dispatchers.Default) {
        if (!request.valid()) return@withContext DjLocalResult.Unavailable(DjLocalProblem.InvalidResponse)
        val owner = music.session.value ?: return@withContext DjLocalResult.Unavailable(DjLocalProblem.Unsupported)
        if (!music.capabilities.value.enabled("dj_interactive")) {
            return@withContext DjLocalResult.Unavailable(DjLocalProblem.Disabled)
        }
        if (music.capabilities.value.dj.localPlansVersion != 1) {
            return@withContext DjLocalResult.Unavailable(DjLocalProblem.Unsupported)
        }
        val ticket = mutex.withLock {
            if (music.session.value != owner) throw CancellationException("Local DJ request retired")
            if (cacheOwner != owner) {
                cache.clear()
                cacheOwner = owner
            }
            cache.begin(owner, request)
        }
        mutex.withLock { cache.cached(ticket) }?.let { return@withContext it }
        val result = try {
            DjLocalResult.Available(localPlansBody(music.localPlans(request.ids, request.transitionMs, owner), request))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IllegalArgumentException) {
            DjLocalResult.Unavailable(DjLocalProblem.InvalidResponse)
        } catch (error: MusicAiFailure) {
            DjLocalResult.Unavailable(
                when (error.kind) {
                    MusicAiFailureKind.Unsupported -> DjLocalProblem.Unsupported
                    MusicAiFailureKind.Disabled -> DjLocalProblem.Disabled
                    MusicAiFailureKind.InvalidResponse -> DjLocalProblem.InvalidResponse
                    else -> DjLocalProblem.Network
                },
            )
        }
        coroutineContext.ensureActive()
        if (music.session.value != owner || !mutex.withLock { cache.complete(ticket, result) }) {
            throw CancellationException("Local DJ request retired")
        }
        result
    }
}
