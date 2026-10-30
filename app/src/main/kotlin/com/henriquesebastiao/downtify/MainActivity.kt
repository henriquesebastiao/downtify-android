package com.henriquesebastiao.downtify

import android.app.SearchManager
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.henriquesebastiao.downtify.core.data.settings.ThemeMode
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.AppFont
import com.henriquesebastiao.downtify.core.player.PlayerController
import com.henriquesebastiao.downtify.ui.DowntifyApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var player: PlayerController

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !viewModel.isReady }
        handleIntent(intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    viewModel.appState.collectLatest { state ->
                        if (state !is AppState.Paired) return@collectLatest
                        while (isActive) {
                            try {
                                player.connect()
                                awaitCancellation()
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                // A transient bind failure must not leave the mini player
                                // absent until another Activity restart. No URLs/tokens logged.
                                Log.w("PlaybackConnection", "Controller connection failed; retrying while visible")
                                delay(1_000)
                            }
                        }
                    }
                } finally {
                    player.release()
                }
            }
        }

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings?.theme) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                else -> isSystemInDarkTheme()
            }
            DisposableEffect(darkTheme) {
                val bars = if (darkTheme) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose {}
            }
            val amoled = darkTheme && settings?.amoled == true
            // The window shows through during transitions and when the keyboard resizes the screen.
            DisposableEffect(amoled) {
                val original = window.decorView.background
                if (amoled) window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
                onDispose { if (amoled) window.setBackgroundDrawable(original) }
            }
            DowntifyTheme(
                darkTheme = darkTheme,
                dynamicColor = settings?.dynamicColor == true,
                amoled = settings?.amoled == true,
                font = settings?.font ?: AppFont.Default,
                floatingNavBar = settings?.floatingNavBar ?: true,
            ) {
                DowntifyApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            val query = intent.getStringExtra(SearchManager.QUERY)?.trim()?.takeIf { it.isNotEmpty() } ?: return
            lifecycleScope.launch {
                try {
                    player.playLibrarySearch(query)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    Log.w("PlaybackConnection", "Could not start library search playback")
                }
            }
            return
        }
        val data = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW && data.scheme == "downtify") viewModel.onPairingLink(data.toString())
    }
}
