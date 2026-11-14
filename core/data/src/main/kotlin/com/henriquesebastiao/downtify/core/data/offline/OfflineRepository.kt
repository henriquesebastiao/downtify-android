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
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** A kept collection, resolved against the library, with how much of it is on the phone. */
data class KeptCollection(
    val collection: OfflineCollection,
    val title: String,
    val coverTrackId: String?,
    val tracks: List<Track>,
    val progress: OfflineProgress,
)

/** Everything offline, recomputed whenever the library, collections, files or limit change. */
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

data class DownloadProgress(val trackId: String, val bytes: Long, val total: Long)

enum class DownloadActivity { Idle, Downloading, WaitingForNetwork }

sealed interface OfflineLocationChange {
    data object Idle : OfflineLocationChange
    data object Moving : OfflineLocationChange
    data class Failed(val cause: Throwable) : OfflineLocationChange
}

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
    private val locationMutex = Mutex()

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
    private val mutableLocationChange = MutableStateFlow<OfflineLocationChange>(OfflineLocationChange.Idle)
    val locationChange: StateFlow<OfflineLocationChange> = mutableLocationChange.asStateFlow()

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

    @Suppress("TooGenericExceptionCaught")
    fun start() {
        scope.launch {
            val directory = settings.settings.first().offlineDirectoryUri
            val initialized = try {
                withContext(Dispatchers.IO) {
                    storage.moveTo(directory)
                    migrateLegacy()
                }
                true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableLocationChange.value = OfflineLocationChange.Failed(error)
                Log.w(TAG, "Could not initialize the selected offline folder", error)
                false
            }
            if (!initialized) return@launch
            dropInvalidFiles()
            state.filterNotNull().collect { reconcileSafely(it) }
        }
    }

    fun localAudio(trackId: String): OfflineAudio? {
        val file = state.value?.files?.get(trackId) ?: return null
        return storage.audio(file.fileName).takeIf { storage.usable(file.fileName, file.bytes) }
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

    suspend fun removeAll() {
        dao.deleteAllCollections()
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun setDirectory(treeUri: String?): Boolean = locationMutex.withLock {
        mutableLocationChange.value = OfflineLocationChange.Moving
        val previous = settings.settings.first().offlineDirectoryUri
        var grantTaken = false
        return@withLock try {
            withContext(Dispatchers.IO) {
                if (treeUri != null && treeUri != previous) {
                    context.contentResolver.takePersistableUriPermission(
                        android.net.Uri.parse(treeUri),
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    grantTaken = true
                }
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME).result.get()
                storage.moveTo(treeUri)
            }
            settings.setOfflineDirectory(treeUri)
            if (previous != null && previous != treeUri) releaseDirectoryPermission(previous)
            scheduledIds = emptySet()
            scheduledWifiOnly = null
            state.value?.let { schedule(it.plan.toDownload.mapTo(mutableSetOf()) { track -> track.id }, it.wifiOnly) }
            mutableLocationChange.value = OfflineLocationChange.Idle
            true
        } catch (error: CancellationException) {
            mutableLocationChange.value = OfflineLocationChange.Idle
            throw error
        } catch (error: Exception) {
            if (grantTaken && treeUri != null) releaseDirectoryPermission(treeUri)
            Log.w(TAG, "Could not move offline media", error)
            mutableLocationChange.value = OfflineLocationChange.Failed(error)
            false
        }
    }

    fun locationLabel(): String = storage.locationLabel()

    private fun releaseDirectoryPermission(treeUri: String) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                android.net.Uri.parse(treeUri),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    fun freeBytes(): Long = storage.freeBytes()
    fun partialBytes(): Long = storage.partialBytes()

    internal suspend fun downloadNext(handled: Set<String>): Step {
        val plan = state.filterNotNull().first().plan
        val track = plan.toDownload.firstOrNull { it.id !in handled } ?: return Step.Done
        val session = sessions.current ?: return Step.Done
        if (storage.freeBytes() < track.size + FREE_SPACE_MARGIN) return Step.OutOfSpace
        val part = storage.part(track.id)
        val url = ServerUrls.stream(session.baseUrl, track.id, StreamQuality.Original)
        mutableProgress.value = DownloadProgress(track.id, part.length(), track.size)
        val result = try {
            downloader.download(url, part) { bytes ->
                mutableProgress.value = DownloadProgress(track.id, bytes, track.size)
            }
        } finally {
            mutableProgress.value = null
        }
        return when (result) {
            is TrackDownloader.Result.Complete -> {
                commit(track, part, result.bytes)?.let { audio ->
                    loudness.bindDownloaded(track.id, audio, result.etag, result.bytes)
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
        data class Handled(val trackId: String) : Step
        data object Done : Step
        data object Retry : Step
        data object OutOfSpace : Step
    }

    private suspend fun commit(track: Track, part: OfflineTarget, bytes: Long): OfflineAudio? {
        if (track.id !in state.filterNotNull().first().plan.keep) {
            part.delete()
            return null
        }
        val audio = storage.commit(part, track)
        if (audio != null) {
            dao.upsertFile(OfflineFileEntity(track.id, audio.path, bytes, System.currentTimeMillis()))
        }
        return audio
    }

    private suspend fun migrateLegacy() {
        val tracks = library.library.filterNotNull().first().byId
        dao.files().forEach { entity ->
            val legacy = storage.legacyFile(entity.fileName)
            val track = tracks[entity.trackId]
            if (legacy.isFile && track != null) {
                storage.importLegacy(legacy, track)?.let { audio ->
                    dao.upsertFile(entity.copy(fileName = audio.path))
                }
            }
        }
    }

    internal suspend fun reconcileSafely(state: OfflineState) {
        try {
            reconcile(state)
        } catch (error: java.io.IOException) {
            Log.w(TAG, "Offline storage is unavailable", error)
            mutableLocationChange.value = OfflineLocationChange.Failed(error)
        } catch (error: SecurityException) {
            Log.w(TAG, "Offline storage permission was revoked", error)
            mutableLocationChange.value = OfflineLocationChange.Failed(error)
        }
    }

    private suspend fun reconcile(state: OfflineState) {
        val plan = state.plan
        if (plan.toDelete.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                plan.toDelete.forEach { id -> state.files[id]?.let { storage.delete(it.fileName) } }
            }
            dao.deleteFiles(plan.toDelete.toList())
            syncPlaylists(state)
            return
        }
        withContext(Dispatchers.IO) {
            val keep = state.files.values.mapTo(mutableSetOf()) { it.fileName }
            plan.toDownload.mapTo(keep) { OfflinePaths.part(it.id) }
            keep += storage.playlistPaths()
            storage.sweep(keep)
        }
        syncPlaylists(state)
        schedule(plan.toDownload.mapTo(mutableSetOf()) { it.id }, state.wifiOnly)
    }

    private suspend fun syncPlaylists(state: OfflineState) = withContext(Dispatchers.IO) {
        val playlists = state.collections.mapNotNull { kept ->
            val title = when (kept.collection.type) {
                PlaybackContextType.Playlist -> kept.title
                PlaybackContextType.Liked -> LIKED_PLAYLIST_TITLE
                else -> null
            } ?: return@mapNotNull null
            val tracks = kept.tracks.mapNotNull { track ->
                val file = state.files[track.id]
                if (track.id in state.plan.keep && file != null && storage.usable(file.fileName, file.bytes)) {
                    track to file.fileName
                } else {
                    null
                }
            }
            OfflinePlaylist(title, kept.collection.key, tracks)
        }
        storage.syncPlaylists(playlists)
    }

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

    private suspend fun dropInvalidFiles() {
        val missing = withContext(Dispatchers.IO) {
            dao.files().filterNot { storage.usable(it.fileName, it.bytes) }
        }
        if (missing.isNotEmpty()) dao.deleteFiles(missing.map { it.trackId })
    }

    companion object {
        internal const val WORK_NAME = "offline-downloads"
        private const val TAG = "Offline"
        private const val BACKOFF_SECONDS = 30L
        private const val FREE_SPACE_MARGIN = 200_000_000L
        private const val LIKED_PLAYLIST_TITLE = "Liked Songs"

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
