package com.henriquesebastiao.downtify.core.player

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.henriquesebastiao.downtify.core.data.di.ApplicationScope
import com.henriquesebastiao.downtify.core.data.settings.SettingsRepository
import com.henriquesebastiao.downtify.core.model.SavedSleepTimer
import com.henriquesebastiao.downtify.core.model.SleepTimerMode
import com.henriquesebastiao.downtify.core.model.SleepTimerPhase
import com.henriquesebastiao.downtify.core.model.SleepTimerState
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The sleep timer that [PlaybackService] runs, so it keeps counting with the app in the background
 * or closed: the UI only starts, extends and cancels it ([PlayerController] passes those on), and
 * reads [state] back through [PlayerState.sleepTimer].
 *
 * The countdown follows what the player is really doing rather than a delay that pauses playback
 * on its own: one reading of the player's position, length and playing state every [TICK_MS]
 * ([SleepTimerState.advance] counts nothing while paused), so a pause, a seek or a skip all move
 * the timer the way they move the player. The last [SleepTimerState.FADE_MS] of it turns the
 * volume down to nothing ([SleepTimerState.volume]) before playback pauses — including the end of
 * the song a [SleepTimerMode.EndOfTrack] timer waits for, whose last five seconds are the fade, so
 * the item after it is never heard.
 *
 * The timer is kept in the settings ([SettingsRepository]) as it runs, so a service Android stopped
 * comes back with it rather than silently losing it.
 */
@UnstableApi
@Singleton
class SleepTimerController @Inject constructor(
    private val settings: SettingsRepository,
    /** Only the settings are written from here: the player is read on the service's thread. */
    @ApplicationScope private val keeper: CoroutineScope,
) : Player.Listener {
    private val mutableState = MutableStateFlow(SleepTimerState.Inactive)

    /** The timer as it stands: [SleepTimerState.Inactive] when there is none. */
    val state = mutableState.asStateFlow()

    private var player: Player? = null

    /** The service's own scope: the player must be read on the thread it was made on. */
    private var scope: CoroutineScope? = null
    private var ticker: Job? = null
    private var kept: SavedSleepTimer? = null
    private var restoring = false

    /** The UI has set, extended or cleared a timer: what was kept before that is older news. */
    private var touched = false

    /** The clock the countdown reads; elapsed real time, which keeps running through a doze. */
    internal var clock: () -> Long = SystemClock::elapsedRealtime

    /** Binds to the service's player (called on service create) and picks up a kept timer. */
    fun attach(player: Player, scope: CoroutineScope) {
        if (this.player === player) return
        stop()
        this.player = player
        this.scope = scope
        player.addListener(this)
        if (mutableState.value.active) {
            // A timer that survived this process's service came back with it: it carries on.
            follow()
            update()
        } else {
            restore()
        }
    }

    /** Lets go of the player (the service is going away); a running timer stays, and is kept. */
    fun stop() {
        player?.removeListener(this)
        player?.volume = FULL_VOLUME
        ticker?.cancel()
        ticker = null
        player = null
        scope = null
        persist(force = true)
    }

    /** Arms the timer (replacing one already running) for [mode]. */
    fun start(mode: SleepTimerMode) {
        touched = true
        val now = clock()
        val position = player?.currentPosition ?: 0L
        val duration = durationOf()
        mutableState.value = when (mode) {
            is SleepTimerMode.AfterMinutes -> SleepTimerState.afterMinutes(mode.minutes, now)
            SleepTimerMode.EndOfTrack -> SleepTimerState.endOfTrack(position, duration, now)
            SleepTimerMode.EndOfEpisode -> SleepTimerState.endOfEpisode(position, duration, now)
        }
        applyVolume()
        follow()
        persist(force = true)
    }

    /** Drops the timer and gives the volume back. */
    fun cancel() {
        touched = true
        if (!mutableState.value.active) return
        mutableState.value = SleepTimerState.Inactive
        ticker?.cancel()
        ticker = null
        applyVolume()
        persist(force = true)
    }

    /** Adds [minutes] to a running timer. */
    fun extend(minutes: Int) {
        touched = true
        if (!mutableState.value.active) return
        mutableState.value = mutableState.value.extend(minutes)
        applyVolume()
        persist(force = true)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (!mutableState.value.active) return
        if (events.containsAny(
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_TIMELINE_CHANGED,
            )
        ) {
            update()
        }
    }

    /**
     * The item a timer waited for has ended. The volume is already at nothing (the last
     * [SleepTimerState.FADE_MS] of that item), so pausing here — the transition has moved the
     * player on, and this is the first moment the end can be known — is heard as the end of it.
     */
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val timer = mutableState.value
        val ending = timer.mode == SleepTimerMode.EndOfTrack || timer.mode == SleepTimerMode.EndOfEpisode
        val ended = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
        if (timer.active && ending && ended) finish() else update()
    }

    /** One reading of the player, the volume for where the timer now is, and its end. */
    private fun update() {
        val timer = mutableState.value
        val player = player ?: return
        if (!timer.active) return
        val next = timer.advance(clock(), player.isPlaying, player.currentPosition, durationOf())
        mutableState.value = next
        if (next.expired) {
            finish()
        } else {
            applyVolume(next)
        }
    }

    /** Out of time: playback pauses where it is, the volume comes back, the timer goes. */
    private fun finish() {
        // The state goes first: the pause below is a player event, and no reading must see a timer.
        mutableState.value = SleepTimerState.Inactive
        ticker?.cancel()
        ticker = null
        player?.pause()
        applyVolume()
        persist(force = true)
    }

    /**
     * One reading a tick while a timer runs — paused playback too, where the reading counts
     * nothing but keeps the countdown honest across the pause. The timer is kept every
     * [KEEP_EVERY_MS] so a service restart loses seconds rather than the whole countdown.
     */
    private fun follow() {
        val service = scope ?: return
        if (!mutableState.value.active) {
            ticker?.cancel()
            ticker = null
            return
        }
        if (ticker?.isActive == true) return
        ticker = service.launch {
            var sinceKept = 0L
            while (isActive && mutableState.value.active) {
                try {
                    update()
                } catch (_: IllegalStateException) {
                    // The player is going away; the next one to attach picks the timer up again.
                    break
                }
                sinceKept += TICK_MS
                if (sinceKept >= KEEP_EVERY_MS) {
                    sinceKept = 0L
                    persist(force = true)
                }
                delay(TICK_MS)
            }
        }
    }

    private fun applyVolume(timer: SleepTimerState = mutableState.value) {
        val player = player ?: return
        val level = if (timer.phase == SleepTimerPhase.Fading) timer.volume else FULL_VOLUME
        if (player.volume != level) player.volume = level
    }

    private fun durationOf(): Long {
        val player = player ?: return 0L
        val duration = player.duration
        if (duration != C.TIME_UNSET && duration > 0) return duration
        return player.currentMediaItem?.mediaMetadata?.durationMs?.takeIf { it > 0 } ?: 0L
    }

    /** A timer kept from before this service: it carries on from where it was, not from nothing. */
    private fun restore() {
        val service = scope ?: return
        if (restoring || touched) return
        restoring = true
        service.launch {
            val saved = try {
                withContext(Dispatchers.IO) { settings.settings.first().sleepTimer }
            } catch (error: IOException) {
                Log.w(TAG, "Could not read the sleep timer back", error)
                null
            } ?: return@launch
            // One the UI set or cleared meanwhile is the newer truth.
            if (touched || mutableState.value.active) return@launch
            mutableState.value = SleepTimerState.restored(saved, clock())
            kept = saved
            follow()
            update()
        }
    }

    /** Keeps the timer where a restart can find it. [force] writes even a countdown that only ticked. */
    private fun persist(force: Boolean = false) {
        val saved = mutableState.value.saved()
        if (!force && saved == kept) return
        kept = saved
        keeper.launch {
            try {
                withContext(Dispatchers.IO) { settings.setSleepTimer(saved) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                Log.w(TAG, "Could not keep the sleep timer", error)
            }
        }
    }

    private companion object {
        const val TAG = "SleepTimerController"
        const val FULL_VOLUME = 1f

        // Four readings a second: twenty steps of the fade, and a pause or a seek seen at once.
        const val TICK_MS = 250L
        const val KEEP_EVERY_MS = 10_000L
    }
}
