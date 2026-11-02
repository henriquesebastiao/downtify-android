package com.henriquesebastiao.downtify.core.player

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.henriquesebastiao.downtify.core.data.library.LibraryRepository
import com.henriquesebastiao.downtify.core.data.offline.OfflineRepository
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** The car browses the existing local feed, not the server search/download API. */
@OptIn(FlowPreview::class)
@UnstableApi
internal open class CarBrowser(
    private val context: Context,
    private val library: LibraryRepository,
    private val offline: OfflineRepository,
    private val sessions: SessionStore,
    private val artwork: SessionArtworkCache,
    private val scope: CoroutineScope,
) : MediaLibrarySession.Callback {
    private val indexed = MutableStateFlow<Pair<String?, CarLibrary?>>(null to null)
    private val warmups = mutableMapOf<String, Job>()
    private val subscriptions = mutableMapOf<MediaSession.ControllerInfo, MutableSet<String>>()
    private var initialOwner: String? = null
    private val compactRoots = mutableSetOf<MediaSession.ControllerInfo>()

    fun observe(session: MediaLibrarySession) {
        scope.launch {
            withContext(Dispatchers.IO) { sessions.ensureLoaded() }
            initialOwner = sessions.current?.artworkOwner
            combine(library.library, library.playlists, library.likedIds, offline.state, sessions.session) {
                    snapshot,
                    playlists,
                    likes,
                    files,
                    association,
                ->
                val next = snapshot?.let {
                    withContext(Dispatchers.Default) { CarLibrary(it, playlists, likes, files?.files?.keys.orEmpty()) }
                }
                association?.artworkOwner to next
            }.debounce(250).collect { (association, next) ->
                val (previousOwner, previous) = indexed.value
                warmups.values.forEach(Job::cancel)
                warmups.clear()
                indexed.value = association to next
                subscriptions.forEach { (browser, parents) ->
                    parents.forEach { parent ->
                        if (previousOwner != association || previous == null ||
                            previous.children(parent) != next?.children(parent)
                        ) {
                            session.notifyChildrenChanged(browser, parent, childCount(parent), null)
                        }
                    }
                }
            }
        }
    }

    private fun current(): CarLibrary? = indexed.value.let { (owner, index) ->
        index.takeIf { owner != null && owner == initialOwner && owner == sessions.current?.artworkOwner }
    }

    private fun childCount(parent: String): Int = if (parent == CarLibrary.ROOT || parent == COLLECTIONS) {
        4
    } else if (parent == LIBRARY) {
        3
    } else {
        current()?.children(parent)?.size ?: 0
    }

    private fun root(): MediaItem = folder(CarLibrary.ROOT, context.getString(R.string.car_library))

    private fun roots(): List<MediaItem> = listOf(
        folder(CarLibrary.PLAYLISTS, context.getString(R.string.car_playlists)),
        folder(CarLibrary.FAVORITES, context.getString(R.string.car_favorites)),
        folder(LIBRARY, context.getString(R.string.car_library)),
        folder(CarLibrary.OFFLINE, context.getString(R.string.car_offline)),
    )

    private fun libraryRoots(): List<MediaItem> = listOf(
        folder(CarLibrary.ARTISTS, context.getString(R.string.car_artists)),
        folder(CarLibrary.ALBUMS, context.getString(R.string.car_albums)),
        folder(CarLibrary.TRACKS, context.getString(R.string.car_tracks)),
    )

    private fun folder(id: String, title: String): MediaItem = MediaItem.Builder().setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build(),
        ).build()

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        // Legacy onGetRoot must never wait for Keystore, persistence or networking.
        val limit = params?.extras?.getInt(MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT, 4) ?: 4
        if (limit in 1..3) compactRoots.add(browser) else compactRoots.remove(browser)
        val extras = Bundle(params?.extras ?: Bundle.EMPTY).apply {
            putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
        }
        return Futures.immediateFuture(
            LibraryResult.ofItem(root(), LibraryParams.Builder().setExtras(extras).build()),
        )
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
        withContext(Dispatchers.IO) { sessions.ensureLoaded() }
        if (sessions.current == null) return@future LibraryResult.ofError(authenticationError())
        if (page < 0 || pageSize <= 0) return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        if (parentId ==
            CarLibrary.ROOT
        ) {
            val items = if (browser in
                compactRoots
            ) {
                listOf(folder(COLLECTIONS, context.getString(R.string.car_library)))
            } else {
                roots()
            }
            return@future LibraryResult.ofItemList(CarLibrary.page(items, page, pageSize), params)
        }
        if (parentId == LIBRARY) {
            return@future LibraryResult.ofItemList(CarLibrary.page(libraryRoots(), page, pageSize), params)
        }
        if (parentId == COLLECTIONS) {
            return@future LibraryResult.ofItemList(CarLibrary.page(roots(), page, pageSize), params)
        }
        val catalog = awaitIndex() ?: return@future LibraryResult.ofError(loadingError())
        val entries = catalog.children(parentId) ?: return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        val selected = CarLibrary.page(entries, page, pageSize)
        val expectedOwner = sessions.current?.artworkOwner
        val items = withContext(Dispatchers.Default) { selected.map { item(it) } }
        if (sessions.current?.artworkOwner != expectedOwner) return@future LibraryResult.ofError(authenticationError())
        CarDiagnostics.response(items.size)
        warm(session, parentId, selected)
        LibraryResult.ofItemList(items, params)
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
        withContext(Dispatchers.IO) { sessions.ensureLoaded() }
        if (sessions.current == null) return@future LibraryResult.ofError(authenticationError())
        if (mediaId == CarLibrary.ROOT) return@future LibraryResult.ofItem(root(), null)
        if (mediaId ==
            LIBRARY
        ) {
            return@future LibraryResult.ofItem(folder(LIBRARY, context.getString(R.string.car_library)), null)
        }
        libraryRoots().firstOrNull { it.mediaId == mediaId }?.let { return@future LibraryResult.ofItem(it, null) }
        if (mediaId == COLLECTIONS) {
            return@future LibraryResult.ofItem(folder(COLLECTIONS, context.getString(R.string.car_library)), null)
        }
        roots().firstOrNull { it.mediaId == mediaId }?.let { return@future LibraryResult.ofItem(it, null) }
        val entry = awaitIndex()?.item(mediaId) ?: return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        val expectedOwner = sessions.current?.artworkOwner
        val result = withContext(Dispatchers.Default) { item(entry) }
        if (sessions.current?.artworkOwner != expectedOwner) {
            LibraryResult.ofError(authenticationError())
        } else {
            LibraryResult.ofItem(result, null)
        }
    }

    override fun onSubscribe(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = scope.future {
        withContext(Dispatchers.IO) { sessions.ensureLoaded() }
        if (sessions.current == null) return@future LibraryResult.ofError(authenticationError())
        if (parentId !in (CarLibrary.ROOTS + listOf(CarLibrary.ROOT, COLLECTIONS, LIBRARY)) &&
            current()?.children(parentId) == null
        ) {
            return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }
        subscriptions.getOrPut(browser) { mutableSetOf() }.add(parentId)
        // Media3 sends the initial children notification after accepting the subscription.
        LibraryResult.ofVoid(params)
    }

    override fun onUnsubscribe(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
    ): ListenableFuture<LibraryResult<Void>> {
        subscriptions[browser]?.remove(parentId)
        if (subscriptions[browser]?.isEmpty() == true) subscriptions.remove(browser)
        if (subscriptions.values.none { parentId in it }) warmups.remove(parentId)?.cancel()
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
        subscriptions.remove(controller)?.forEach { parent ->
            if (subscriptions.values.none { parent in it }) warmups.remove(parent)?.cancel()
        }
        compactRoots.remove(controller)
        super.onDisconnected(session, controller)
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = scope.future {
        withContext(Dispatchers.IO) { sessions.ensureLoaded() }
        if (sessions.current == null) return@future LibraryResult.ofError(authenticationError())
        val catalog = awaitIndex() ?: return@future LibraryResult.ofError(loadingError())
        val count = withContext(Dispatchers.Default) { catalog.search(query).size }
        if (count > CarLibrary.MAX_CHILDREN) return@future LibraryResult.ofError(searchError())
        session.notifySearchResultChanged(browser, query, count, params)
        LibraryResult.ofVoid(params)
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
        withContext(Dispatchers.IO) { sessions.ensureLoaded() }
        if (sessions.current == null) return@future LibraryResult.ofError(authenticationError())
        if (page < 0 || pageSize <= 0) return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        val catalog = awaitIndex() ?: return@future LibraryResult.ofError(loadingError())
        val expectedOwner = sessions.current?.artworkOwner
        val results = withContext(Dispatchers.Default) { catalog.search(query) }
        if (results.size > CarLibrary.MAX_CHILDREN) return@future LibraryResult.ofError(searchError())
        val items = withContext(Dispatchers.Default) { CarLibrary.page(results, page, pageSize).map { item(it) } }
        if (sessions.current?.artworkOwner != expectedOwner) return@future LibraryResult.ofError(authenticationError())
        LibraryResult.ofItemList(items, params)
    }

    /** External clients send opaque IDs; their supplied URI and metadata are never trusted. */
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
        withContext(Dispatchers.IO) { sessions.ensureLoaded() }
        val catalog = awaitIndex() ?: throw IllegalArgumentException(context.getString(R.string.car_loading))
        val expectedOwner = sessions.current?.artworkOwner
        if (mediaItems.size > 1) {
            val selectedIndex = if (startIndex == C.INDEX_UNSET) 0 else startIndex
            require(selectedIndex in mediaItems.indices)
            return@future MediaSession.MediaItemsWithStartPosition(
                carAddItems(mediaItems),
                selectedIndex,
                startPositionMs,
            )
        }
        val selected = mediaItems.getOrNull(if (startIndex == C.INDEX_UNSET) 0 else startIndex)
            ?: throw IllegalArgumentException(context.getString(R.string.car_unavailable))
        val playback = if (selected.mediaId.isNotEmpty()) {
            catalog.playback(selected.mediaId)
        } else {
            val query = selected.requestMetadata.searchQuery.orEmpty()
            val results = withContext(Dispatchers.Default) { catalog.search(query).filter { it.track != null } }
            if (results.size == 1) catalog.playback(results.single().id) else null
        } ?: throw IllegalArgumentException(context.getString(R.string.car_choose_result))
        require(sessions.current?.artworkOwner == expectedOwner) { context.getString(R.string.car_pair) }
        val base = sessions.current?.baseUrl ?: throw IllegalArgumentException(context.getString(R.string.car_pair))
        MediaSession.MediaItemsWithStartPosition(
            playback.first.map { MediaItems.from(it, base) },
            playback.second,
            startPositionMs.takeIf { it >= 0 } ?: C.TIME_UNSET,
        )
    }

    protected fun carAddItems(mediaItems: List<MediaItem>): List<MediaItem> {
        val catalog = requireNotNull(current()) { context.getString(R.string.car_loading) }
        val base = requireNotNull(sessions.current?.baseUrl) { context.getString(R.string.car_pair) }
        return mediaItems.map {
            val track =
                catalog.track(it.mediaId) ?: throw IllegalArgumentException(context.getString(R.string.car_unavailable))
            MediaItems.from(track, base)
        }
    }

    private suspend fun awaitIndex(): CarLibrary? {
        current()?.let { return it }
        if (sessions.current == null) return null
        val expected = sessions.current?.artworkOwner
        return withTimeoutOrNull(3000) {
            indexed.first { it.first == expected && it.second != null }
            current()
        }
    }

    private suspend fun item(entry: CarLibrary.Entry): MediaItem {
        val base = sessions.current?.baseUrl
        val cover = entry.coverTrackId?.takeIf { base != null }?.let {
            artwork.cached(it, Uri.parse(ServerUrls.cover(checkNotNull(base), it, ServerUrls.COVER_LARGE)))
        }
        return (
            entry.track?.let { MediaItems.from(it, base).buildUpon().setMediaId(entry.id).build() }
                ?: folder(entry.id, entry.title)
            ).let { media ->
            media.buildUpon().setMediaMetadata(
                media.mediaMetadata.buildUpon().setSubtitle(entry.subtitle)
                    .setArtworkUri(cover).build(),
            ).build()
        }
    }

    private fun warm(session: MediaLibrarySession, parent: String, entries: List<CarLibrary.Entry>) {
        if (warmups[parent]?.isActive == true) return
        val paired = sessions.current ?: return
        warmups[parent] = scope.launch {
            var changed = false
            entries.mapNotNull { it.coverTrackId }.distinct().take(12).forEach { id ->
                val source = Uri.parse(ServerUrls.cover(paired.baseUrl, id, ServerUrls.COVER_LARGE))
                if (artwork.cached(id, source) == null) changed = artwork.prepare(id, source) != null || changed
            }
            if (changed && sessions.current?.artworkOwner == paired.artworkOwner) {
                session.notifyChildrenChanged(parent, childCount(parent), null)
            }
        }
    }

    private fun authenticationError(): SessionError =
        SessionError(SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED, context.getString(R.string.car_pair))
    private fun searchError(): SessionError =
        SessionError(SessionError.ERROR_BAD_VALUE, context.getString(R.string.car_refine_search))
    private fun loadingError(): SessionError =
        SessionError(SessionError.ERROR_IO, context.getString(R.string.car_loading))

    private companion object {
        const val LIBRARY = "auto:library"
        const val COLLECTIONS = "auto:collections"
    }
}
