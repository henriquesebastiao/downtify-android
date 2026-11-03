package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.okhttp.OkHttpDataSource
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/** Cancellation belongs to one plan, including HTTP calls still waiting for response headers. */
@UnstableApi
internal class ReadAheadRequest(client: OkHttpClient) {
    private val cancelled = AtomicBoolean()
    private val call = AtomicReference<Call?>()
    private val writer = AtomicReference<CacheWriter?>()
    val http = OkHttpDataSource.Factory(object : Call.Factory {
        override fun newCall(request: Request): Call = client.newCall(request).also {
            call.set(it)
            if (cancelled.get()) it.cancel()
        }
    }).setUserAgent("Downtify-Android")

    fun cache(source: CacheDataSource, spec: DataSpec, listener: CacheWriter.ProgressListener) {
        val pending = CacheWriter(source, spec, null, listener)
        writer.set(pending)
        if (cancelled.get()) pending.cancel()
        try {
            pending.cache()
        } finally {
            writer.compareAndSet(pending, null)
            call.getAndSet(null)?.cancel()
        }
    }

    fun cancel() {
        cancelled.set(true)
        writer.get()?.cancel()
        call.get()?.cancel()
    }
}
