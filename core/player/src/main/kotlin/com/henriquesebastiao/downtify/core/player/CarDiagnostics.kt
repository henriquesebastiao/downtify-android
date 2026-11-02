package com.henriquesebastiao.downtify.core.player

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

/** Opt-in logcat counters only. Never logs URLs, media IDs, titles or credentials. */
internal object CarDiagnostics {
    private const val TAG = "DowntifyCar"
    private val created = AtomicInteger()
    private val destroyed = AtomicInteger()

    fun service(starting: Boolean) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return
        if (starting) created.incrementAndGet() else destroyed.incrementAndGet()
        val runtime = Runtime.getRuntime()
        Log.d(
            TAG,
            "created=${created.get()} destroyed=${destroyed.get()} " +
                "heapBytes=${runtime.totalMemory() - runtime.freeMemory()} threads=${Thread.activeCount()}",
        )
    }

    fun response(count: Int) {
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "browseItems=$count")
    }
}
