package com.henriquesebastiao.downtify.core.data.offline

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.henriquesebastiao.downtify.core.data.db.DowntifyDatabase
import com.henriquesebastiao.downtify.core.data.db.OfflineCollectionEntity
import com.henriquesebastiao.downtify.core.data.db.OfflineFileEntity
import com.henriquesebastiao.downtify.core.data.di.ApplicationScope
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.library.LibrarySnapshot
import com.henriquesebastiao.downtify.core.data.loudness.LoudnessProfiles
import com.henriquesebastiao.downtify.core.data.settings.SettingsRepository
import com.henriquesebastiao.downtify.core.model.OfflineCollection
import com.henriquesebastiao.downtify.core.model.OfflineFile
import com.henriquesebastiao.downtify.core.model.OfflinePlan
import com.henriquesebastiao.downtify.core.model.OfflinePlanner
import com.henriquesebastiao.downtify.core.model.OfflineProgress
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.Playlist
import com.henriquesebastiao.downtify.core.model.StreamQuality
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.core.network.di.StreamingClient
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** A kept collection, resolved against the library, with how much of it is on the phone. */
data class KeptCollection(
    val collection: OfflineCollection,
    /** Album title or playlist name; blank for Liked songs (the UI names it). */
    val title: String,
    val coverTrackId: String?,
    val tracks: List<Track>,
    val progress: OfflineProgress,
)

/** Everything offline, recomputed whenever the library, the kept collections, the files or the limit change. */
data class OfflineState(
    val collections: List<KeptCollection>,
    val files: Map<String, OfflineFile>,
    val plan: OfflinePlan,
    val limitBytes: Long,
    val wifiOnly: Boolean,
) {
    val usedBytes: Long get() = files.values.sumOf { it.bytes }

    fun isKept(type: PlaybackContextType, refId: String): Boolean {
        val key = OfflineCollection.keyOf(type, refId)
        return collections.any { it.collection.key == key }
    }

    fun kept(type: PlaybackContextType, refId: String): KeptCollection? {
        val key = OfflineCollection.keyOf(type, refId)
        return collections.firstOrNull { it.collection.key == key }
    }
}

/** The track downloading right now. */
data class DownloadProgress(val trackId: String, val bytes: Long, val total: Long)

/** What the background download job is doing. */
enum class DownloadActivity { Idle, Downloading, WaitingForNetwork }

/**
 * Offline copies: which collections the user keeps, what that means in files
 * (an [OfflinePlan]), and keeping the phone in step — deleting what's no
 * longer wanted at once, and handing downloads to [OfflineSyncWorker].
 */
@Singleton
class OfflineRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: DowntifyDatabase,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val storage: OfflineStorage,
    private val sessions: SessionStore,
    private val loudness: LoudnessProfiles,
    @StreamingClient client: OkHttpClient,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val downloader = TrackDownloader(client)
    private val dao = db.offline()

    /**
     * Null until every source was read once — the Room flows (not the repository's
     * `stateIn` defaults), so an empty first value never looks like "delete everything".
     */
    val state: StateFlow<OfflineState?> = combine(
        library.library.filterNotNull(),
        db.playlists().observeAll(),
        db.likes().observeIds(),
        dao.observeCollections(),
        combine(dao.observeFiles(), settings.settings) { files, s -> files to s },
    ) { snapshot, playlists, liked, collections, (files, s) ->
        buildState(
            snapshot = snapshot,
            playlists = playlists.map { it.toModel() },
            liked = liked,
            collections = collections.mapNotNull { it.toModel() },
            files = files.associate { it.trackId to it.toModel() },
            limitBytes = s.offlineLimitBytes,
            wifiOnly = s.downloadWifiOnly,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val mutableProgress = MutableStateFlow<DownloadProgress?>(null)
    val progress: StateFlow<DownloadProgress?> = mutableProgress.asStateFlow()

    // Lazy: this repository is built while the Application is injected, before WorkManager may start.
    val activity: Flow<DownloadActivity> by lazy { activityFlow() }

    private fun activityFlow(): Flow<DownloadActivity> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(WORK_NAME)
        .map { infos ->
            when {
                infos.any { it.state == WorkInfo.State.RUNNING } -> DownloadActivity.Downloading
                infos.any { it.state == WorkInfo.State.ENQUEUED } -> DownloadActivity.WaitingForNetwork
                else -> DownloadActivity.Idle
            }
        }
        .distinctUntilChanged()

    private var scheduledIds: Set<String> = emptySet()
    private var scheduledWifiOnly: Boolean? = null

    /** Starts keeping the phone in step. Called once, from the Application. */
    fun start() {
        scope.launch {
            dropMissingFiles()
            state.filterNotNull().collect { reconcile(it) }
        }
    }

    /** The offline copy of [trackId], when there's a complete one. Safe from any thread. */
    fun localFile(trackId: String): File? {
        val file = state.value?.files?.get(trackId) ?: return null
        return storage.file(file.fileName).takeIf { it.isFile }
    }

    suspend fun setKept(type: PlaybackContextType, refId: String, keep: Boolean) {
        require(type in OfflineCollection.KEEPABLE) { "Can't keep $type offline" }
        val key = OfflineCollection.keyOf(type, refId)
        if (keep) {
            dao.upsertCollection(OfflineCollectionEntity(key, type.name, refId, System.currentTimeMillis()))
        } else {
            dao.deleteCollection(key)
        }
    }

    /** Stops downloading and keeps nothing offline. */
    suspend fun removeAll() {
        dao.deleteAllCollections()
    }

    /** Frees space the storage card counts: partial downloads plus the complete copies. */
    fun freeBytes(): Long = storage.freeBytes()

    fun partialBytes(): Long = storage.partialBytes()

    /**
     * One step of [OfflineSyncWorker]: download the next missing track. [handled]
     * are the ids this run already dealt with — the plan may not show a finished
     * file yet (Room's flow re-emits a moment later).
     */
    internal suspend fun downloadNext(handled: Set<String>): Step {
        val plan = state.filterNotNull().first().plan
        val track = plan.toDownload.firstOrNull { it.id !in handled } ?: return Step.Done
        val session = sessions.current ?: return Step.Done
        if (storage.freeBytes() < track.size + FREE_SPACE_MARGIN) return Step.OutOfSpace
        val part = storage.partFile(track.id)
        val url = ServerUrls.stream(session.baseUrl, track.id, StreamQuality.Original)
        mutableProgress.value = DownloadProgress(track.id, part.length(), track.size)
        val result = try {
            downloader.download(url, part) { bytes ->
                mutableProgress.value =
                    DownloadProgress(track.id, bytes, track.size)
            }
        } finally {
            mutableProgress.value = null
        }
        return when (result) {
            is TrackDownloader.Result.Complete -> {
                commit(track, part, result.bytes)?.let { file ->
                    loudness.bindDownloaded(track.id, file, result.etag, result.bytes)
                }
                Step.Handled(track.id)
            }

            TrackDownloader.Result.Gone -> {
                part.delete()
                Step.Handled(track.id)
            }

            is TrackDownloader.Result.Failed -> {
                Log.w(TAG, "Download of ${track.id} failed (${result.status})", result.cause)
                Step.Retry
            }
        }
    }

    internal sealed interface Step {
        /** Downloaded, or gone from the server: don't try it again this run. */
        data class Handled(val trackId: String) : Step
        data object Done : Step
        data object Retry : Step
        data object OutOfSpace : Step
    }

    private suspend fun commit(track: Track, part: File, bytes: Long): File? {
        // Removed while it downloaded: don't keep it.
        if (track.id !in state.filterNotNull().first().plan.keep) {
            part.delete()
            return null
        }
        val name = storage.fileName(track)
        val file = storage.commit(part, name)
        if (file != null) {
            dao.upsertFile(OfflineFileEntity(track.id, name, bytes, System.currentTimeMillis()))
        }
        return file
    }

    private suspend fun reconcile(state: OfflineState) {
        val plan = state.plan
        if (plan.toDelete.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                plan.toDelete.forEach { id -> state.files[id]?.let { storage.delete(it.fileName) } }
            }
            dao.deleteFiles(plan.toDelete.toList())
            return // The files flow emits again without them.
        }
        withContext(Dispatchers.IO) {
            val keep = state.files.values.mapTo(mutableSetOf()) { it.fileName }
            plan.toDownload.mapTo(keep) { it.id + OfflineStorage.PART }
            storage.sweep(keep)
        }
        schedule(plan.toDownload.mapTo(mutableSetOf()) { it.id }, state.wifiOnly)
    }

    /**
     * Enqueues the download job when there's something new to fetch, or the
     * network rule changed. A running job picks up later additions by itself;
     * appending covers the moment it's about to finish.
     */
    private fun schedule(ids: Set<String>, wifiOnly: Boolean) {
        if (ids.isEmpty()) {
            scheduledIds = emptySet()
            return
        }
        val policy = when {
            wifiOnly != scheduledWifiOnly -> ExistingWorkPolicy.REPLACE
            !scheduledIds.containsAll(ids) -> ExistingWorkPolicy.APPEND_OR_REPLACE
            else -> return
        }
        scheduledIds = ids
        scheduledWifiOnly = wifiOnly
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }

    /** Rows whose file vanished (cleared storage, a crash mid-rename) would look downloaded forever. */
    private suspend fun dropMissingFiles() {
        val missing = dao.files().filterNot { withContext(Dispatchers.IO) { storage.file(it.fileName).isFile } }
        if (missing.isNotEmpty()) dao.deleteFiles(missing.map { it.trackId })
    }

    companion object {
        internal const val WORK_NAME = "offline-downloads"
        private const val TAG = "Offline"
        private const val BACKOFF_SECONDS = 30L

        /** Leave this much free on the phone. */
        private const val FREE_SPACE_MARGIN = 200_000_000L

        @Suppress("LongParameterList")
        internal fun buildState(
            snapshot: LibrarySnapshot,
            playlists: List<Playlist>,
            liked: List<String>,
            collections: List<OfflineCollection>,
            files: Map<String, OfflineFile>,
            limitBytes: Long,
            wifiOnly: Boolean,
        ): OfflineState {
            val resolved = collections.map { c -> c to resolve(c, snapshot, playlists, liked) }
            val plan = OfflinePlanner.plan(resolved.flatMap { it.second.second }, files, limitBytes)
            val kept = resolved.map { (c, r) ->
                val (title, tracks) = r
                KeptCollection(
                    collection = c,
                    title = title,
                    coverTrackId = tracks.firstOrNull { it.hasCover }?.id,
                    tracks = tracks,
                    progress = OfflineProgress.of(tracks, files, plan),
                )
            }
            return OfflineState(kept, files, plan, limitBytes, wifiOnly)
        }

        private fun resolve(
            c: OfflineCollection,
            snapshot: LibrarySnapshot,
            playlists: List<Playlist>,
            liked: List<String>,
        ): Pair<String, List<Track>> = when (c.type) {
            PlaybackContextType.Album -> snapshot.albumsById[c.refId]
                .let { (it?.title ?: "") to (it?.tracks ?: emptyList()) }

            PlaybackContextType.Playlist ->
                c.refId to
                    (playlists.firstOrNull { it.name == c.refId }?.let { snapshot.tracks(it.trackIds) } ?: emptyList())

            PlaybackContextType.Liked -> "" to snapshot.tracks(liked)

            else -> "" to emptyList()
        }
    }
}
