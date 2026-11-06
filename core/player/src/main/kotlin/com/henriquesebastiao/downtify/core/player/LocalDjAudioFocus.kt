package com.henriquesebastiao.downtify.core.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/** One audio focus/noisy owner for the session, independent of the dominant decoder. */
@UnstableApi
class LocalDjAudioFocus(private val context: Context, private val player: LocalDjPlayer) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private var registered = false
    private var held = false
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
        )
        .setAcceptsDelayedFocusGain(false)
        .setOnAudioFocusChangeListener(::onFocusChange, Handler(player.applicationLooper))
        .build()
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) player.pause()
        }
    }
    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) abandon()
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(noisy, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(noisy, filter)
        }
        registered = true
        player.setPlayGate(::requestFocus)
        player.addListener(listener)
        if (player.playWhenReady && !requestFocus()) player.pause()
    }

    fun release() {
        player.setPlayGate { false }
        player.removeListener(listener)
        if (registered) context.unregisterReceiver(noisy)
        registered = false
        abandon()
    }

    private fun requestFocus(): Boolean {
        if (held) return true
        held = manager?.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (held) player.setFocusGain(1f)
        return held
    }

    internal fun onFocusChange(change: Int) {
        if (!registered) return
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> player.setFocusGain(1f)

            // Never resume after a loss.
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player.setFocusGain(0.2f)

            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                player.pause()
                abandon()
            }
        }
    }

    private fun abandon() {
        if (held) manager?.abandonAudioFocusRequest(request)
        held = false
        player.setFocusGain(1f)
    }
}
