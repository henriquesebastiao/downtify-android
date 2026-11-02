package com.henriquesebastiao.downtify

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.media.browse.MediaBrowser
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import com.henriquesebastiao.downtify.core.player.PlaybackService

/** Development-only client: exercises the framework browser across a real process boundary. */
@androidx.media3.common.util.UnstableApi
class LegacyCarProbeService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var browser: MediaBrowser? = null
    private val messenger = Messenger(
        Handler(Looper.getMainLooper()) { request ->
            probe(request.replyTo)
            true
        },
    )

    override fun onBind(intent: Intent): IBinder = messenger.binder

    private fun probe(reply: Messenger) {
        browser?.disconnect()
        var finished = false
        fun finish(connected: Boolean) {
            if (finished) return
            finished = true
            browser?.disconnect()
            browser = null
            handler.removeCallbacksAndMessages(null)
            reply.send(
                Message.obtain().apply {
                    data = Bundle().apply {
                        putBoolean("connected", connected)
                        putInt("pid", Process.myPid())
                    }
                },
            )
        }
        browser = MediaBrowser(
            this,
            ComponentName(this, PlaybackService::class.java),
            object : MediaBrowser.ConnectionCallback() {
                override fun onConnected() = finish(browser?.root == "auto:root")
                override fun onConnectionFailed() = finish(false)
                override fun onConnectionSuspended() = finish(false)
            },
            null,
        )
        handler.postDelayed({ finish(false) }, 5_000)
        browser?.connect()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        browser?.disconnect()
        super.onDestroy()
    }
}
