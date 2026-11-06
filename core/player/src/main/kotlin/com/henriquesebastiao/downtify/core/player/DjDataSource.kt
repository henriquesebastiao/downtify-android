package com.henriquesebastiao.downtify.core.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/** DJ bytes must always reach the authenticated server, including range requests after a seek. */
@UnstableApi
internal class DjDataSource(
    private val regular: DataSource.Factory,
    private val direct: DataSource.Factory,
    private val liveResource: (Uri) -> Boolean = { false },
) : DataSource {
    private var active: DataSource? = null
    private val listeners = mutableListOf<TransferListener>()
    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        active?.addTransferListener(transferListener)
    }
    override fun open(dataSpec: DataSpec): Long {
        check(active == null)
        val factory = if (DjMediaItems.idOf(dataSpec.uri) != null || liveResource(dataSpec.uri)) direct else regular
        val source = factory.createDataSource()
        listeners.forEach(source::addTransferListener)
        active = source
        return source.open(dataSpec)
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int) = checkNotNull(active).read(buffer, offset, length)
    override fun getUri(): Uri? = active?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()
    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }
}
