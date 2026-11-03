package com.henriquesebastiao.downtify.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.henriquesebastiao.downtify.core.model.AppFont

/**
 * Downtify's Material 3 Expressive theme, with its spring motion. The brand scheme is the default;
 * [dynamicColor] (Material You wallpaper colors, Android 12+) is opt-in from Settings, and so is
 * [amoled] (true black surfaces in the dark theme), the [font] and whether the navigation bar
 * floats ([floatingNavBar], read through [LocalFloatingNavBar]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DowntifyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    amoled: Boolean = false,
    font: AppFont = AppFont.Default,
    floatingNavBar: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DowntifyDarkColors

        else -> DowntifyLightColors
    }.let { if (darkTheme && amoled) it.amoled() else it }
    val typography = remember(font) { downtifyTypography(font) }
    CompositionLocalProvider(LocalFloatingNavBar provides floatingNavBar) {
        MaterialExpressiveTheme(
            colorScheme = colors,
            motionScheme = MotionScheme.expressive(),
            shapes = DowntifyShapes,
            typography = typography,
            content = content,
        )
    }
}

/** Whether the navigation bar floats as a pill over the content (the default) or spans the screen's width. */
val LocalFloatingNavBar = staticCompositionLocalOf { true }

/**
 * [this] with true black behind everything, for OLED screens: black pixels are off. The raised
 * surfaces (cards, list items, sheets) stay a little lighter than the background so they still read
 * as raised, only darker than they'd be on the usual dark gray.
 */
fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = surfaceContainerLow.towardsBlack(AMOLED_LOW),
    surfaceContainer = surfaceContainer.towardsBlack(AMOLED_CONTAINER),
    surfaceContainerHigh = surfaceContainerHigh.towardsBlack(AMOLED_HIGH),
    surfaceContainerHighest = surfaceContainerHighest.towardsBlack(AMOLED_HIGHEST),
)

private fun Color.towardsBlack(fraction: Float) = lerp(this, Color.Black, fraction)

// How far each raised surface moves towards black.
private const val AMOLED_LOW = 0.35f
private const val AMOLED_CONTAINER = 0.25f
private const val AMOLED_HIGH = 0.18f
private const val AMOLED_HIGHEST = 0.1f
