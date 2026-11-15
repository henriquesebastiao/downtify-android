package com.henriquesebastiao.downtify.core.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.core.network.session.Session
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/** Immutable HLS chunks only: a fresh manifest remains the server's current playback window. */
@UnstableApi
internal class DjSegmentCache(
    private val cache: Cache,
    private val current: () -> Session?,
    private val enabled: () -> Boolean,
    private val upstream: DataSource.Factory,
) : DataSource.Factory {
    private class Binding(val owner: Session, val prefix: String = "dj-v1:${UUID.randomUUID()}:") {
        val identities = mutableMapOf<String, String>()
    }
    private var binding: Binding? = null
    private val cached = CacheDataSource.Factory().setCache(cache)
        .setUpstreamDataSourceFactory(upstream)
        .setCacheKeyFactory { checkNotNull(it.key) }
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    override fun createDataSource(): DataSource = Source()

    /** Removal is housekeeping; the read fence takes effect before this asynchronous cleanup. */
    fun purgeRetired(): Boolean {
        val prefix = currentBinding()?.prefix
        val retired = cache.keys.filter { prefix == null || !it.startsWith(prefix) }
        var complete = true
        retired.forEach {
            try {
                cache.removeResource(it)
            } catch (_: Cache.CacheException) {
                complete = false
            } catch (_: IllegalStateException) {
                complete = false
            }
        }
        return complete
    }

    @Synchronized
    private fun currentBinding(): Binding? {
        val owner = current()?.takeIf { enabled() }
        if (owner != binding?.owner) binding = owner?.let(::Binding)
        return binding
    }

    private fun capture(uri: Uri): Binding {
        val captured = currentBinding() ?: throw IOException("DJ playback is unavailable for this account")
        val decorated = uri.userInfo != null || uri.query != null || uri.fragment != null
        if (decorated || !ServerUrls.isDjHlsResource(captured.owner.baseUrl, uri.toString())) {
            throw IOException("Invalid DJ playback resource")
        }
        return captured
    }

    private fun checkCurrent(captured: Binding) {
        if (currentBinding() !== captured) throw IOException("DJ playback owner or availability changed")
    }

    @Synchronized
    internal fun observeManifest(uri: Uri, text: String) {
        val owner = capture(uri)
        owner.identities.clear()
        owner.identities.putAll(DjSegmentIdentity.parse(uri, text))
    }

    @Synchronized
    private fun identity(owner: Binding, uri: Uri): String? = owner.identities[uri.toString()]

    private fun key(captured: Binding, uri: Uri, identity: String): String = captured.prefix +
        MessageDigest.getInstance("SHA-256").digest("$uri:$identity".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private inner class Source : DataSource {
        private val listeners = mutableListOf<TransferListener>()
        private var active: DataSource? = null
        private var captured: Binding? = null
        private var manifest: ByteArrayOutputStream? = null
        private var manifestUri: Uri? = null
        private var resourceUri: Uri? = null
        private var renderIdentity: String? = null

        override fun addTransferListener(transferListener: TransferListener) {
            listeners += transferListener
            active?.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            check(active == null)
            val owner = capture(dataSpec.uri)
            val identity = identity(owner, dataSpec.uri)
            val immutable =
                identity != null && dataSpec.uri.lastPathSegment?.matches(Regex("segment-\\d{8}\\.ts")) == true
            val spec = dataSpec.buildUpon().setKey(
                if (immutable) key(owner, dataSpec.uri, checkNotNull(identity)) else null,
            )
                .setFlags(dataSpec.flags or DataSpec.FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN).build()
            val source = (if (immutable) cached else upstream).createDataSource()
            listeners.forEach(source::addTransferListener)
            captured = owner
            active = source
            resourceUri = dataSpec.uri
            renderIdentity = if (immutable) identity else null
            if (dataSpec.uri.lastPathSegment == "index.m3u8") {
                manifest = ByteArrayOutputStream()
                manifestUri = dataSpec.uri
            }
            try {
                val length = source.open(spec)
                checkCurrent(owner)
                return length
            } catch (error: IOException) {
                try {
                    close()
                } catch (closeError: IOException) {
                    error.addSuppressed(closeError)
                }
                throw error
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val owner = checkNotNull(captured)
            checkCurrent(owner)
            checkRender(owner)
            val count = checkNotNull(active).read(buffer, offset, length)
            checkCurrent(owner)
            checkRender(owner)
            val pending = manifest
            if (pending != null) {
                if (count == -1) {
                    observeManifest(checkNotNull(manifestUri), pending.toString("UTF-8"))
                } else if (count > 0 && pending.size() + count <= 262144) {
                    pending.write(buffer, offset, count)
                } else if (count > 0) {
                    manifest = null
                    observeManifest(checkNotNull(manifestUri), "")
                }
            }
            return count
        }

        private fun checkRender(owner: Binding) {
            val previous = renderIdentity ?: return
            val now = resourceUri?.let { identity(owner, it) } ?: return
            if (previous != now) throw IOException("DJ render changed")
        }

        override fun getUri(): Uri? = active?.uri
        override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()
        override fun close() {
            try {
                active?.close()
            } finally {
                active = null
                captured = null
                manifest = null
                manifestUri = null
                resourceUri = null
                renderIdentity = null
            }
        }
    }
}
