package com.henriquesebastiao.downtify.core.data.playback

import android.database.sqlite.SQLiteException
import android.util.Log
import com.henriquesebastiao.downtify.core.data.db.DowntifyDatabase
import com.henriquesebastiao.downtify.core.data.di.ApplicationScope
import com.henriquesebastiao.downtify.core.model.PlaybackSnapshot
import com.henriquesebastiao.downtify.core.network.session.Session
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Owns durable playback state. Service shutdown does not cancel an already queued write. */
@Singleton
class PlaybackRepository @Inject constructor(
    db: DowntifyDatabase,
    private val sessions: SessionStore,
    @ApplicationScope scope: CoroutineScope,
) {
    private val dao = db.playback()
    private val lock = Mutex()
    private val writes = Channel<Pair<String, PlaybackSnapshot?>>(Channel.CONFLATED)
    private var previous: Pair<String, PlaybackSnapshot>? = null

    init {
        scope.launch(Dispatchers.IO) {
            sessions.ensureLoaded()
            sessions.session.map { it?.playbackOwner }.distinctUntilChanged().collect { owner ->
                lock.withLock {
                    if (owner == null || dao.readOwner()?.let { it != owner } == true) {
                        dao.clear()
                        previous = null
                    }
                }
            }
        }
        scope.launch(Dispatchers.IO) {
            for ((owner, snapshot) in writes) {
                try {
                    lock.withLock {
                        if (sessions.current?.playbackOwner != owner) return@withLock
                        if (snapshot == null) {
                            dao.clear()
                            previous = null
                        } else {
                            val old = previous?.takeIf { it.first == owner }?.second
                            if (old != null && sameContents(old, snapshot)) {
                                dao.checkpoint(owner, snapshot.index, snapshot.positionMs)
                            } else {
                                dao.write(PlaybackCodec.encode(owner, snapshot))
                            }
                            previous = owner to snapshot
                        }
                    }
                } catch (error: SQLiteException) {
                    Log.w("PlaybackRepository", "Could not save playback state", error)
                }
            }
        }
    }

    suspend fun load(owner: String): PlaybackSnapshot? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (sessions.current?.playbackOwner != owner) return@withLock null
            dao.read()?.takeIf { it.owner == owner }?.let(PlaybackCodec::decode)
        }
    }

    /** The latest checkpoint carries the latest queue too: conflation cannot drop a queue edit. */
    fun save(owner: String, snapshot: PlaybackSnapshot?) {
        writes.trySend(owner to snapshot)
    }

    private fun sameContents(old: PlaybackSnapshot, new: PlaybackSnapshot): Boolean =
        old.items === new.items && old.shuffleOrder === new.shuffleOrder && old.shuffle == new.shuffle &&
            old.repeatMode == new.repeatMode && old.speed == new.speed && old.context == new.context
}

/** A renamed account or a changed LAN/VPN address must not lose the same device's session. */
val Session.playbackOwner: String get() = "${serverId.ifBlank { baseUrl }}\u001f$deviceId"
