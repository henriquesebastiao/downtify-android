package com.henriquesebastiao.downtify.core.designsystem.component

import android.content.Context
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Applies the scheme of [coverUrl]'s cover to [content]: Now Playing, the mini player, an album's
 * header and the lyrics. Without a cover (or until it's read) [content] keeps the app's scheme. The
 * change is animated on the theme's slow effects spring.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CoverTheme(coverUrl: String?, content: @Composable () -> Unit) {
    val app = MaterialTheme.colorScheme
    val dark = app.surface.luminance() < DARK_LUMINANCE
    val seed = rememberCoverSeed(coverUrl)
    val cover = remember(seed, dark) { seed?.let { coverColorScheme(it, dark) } }
    val scheme = animateColorScheme(cover ?: app, MaterialTheme.motionScheme.slowEffectsSpec())
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MaterialTheme.motionScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
        content = content,
    )
}

/**
 * The color that seeds [coverUrl]'s scheme: the Palette API's vibrant swatch, or the dominant one
 * when the cover has none. Null without a cover or when it can't be read. While a new cover is
 * read, the previous seed stays, so the scheme goes straight from one cover to the next. A cover
 * read lately has its seed from the first frame: a surface that appears in its colors (Now Playing
 * growing out of the mini player) doesn't flash the app's on the way.
 */
@Composable
fun rememberCoverSeed(coverUrl: String?): Int? {
    val context = LocalContext.current
    var seed by remember { mutableStateOf(coverUrl?.let(seeds::get)) }
    LaunchedEffect(coverUrl) {
        seed = coverUrl?.let { url -> seeds.get(url) ?: readSeed(context, url)?.also { seeds.put(url, it) } }
    }
    return seed
}

private suspend fun readSeed(context: Context, url: String): Int? {
    val request = ImageRequest.Builder(context).data(url).size(PALETTE_SIZE).allowHardware(false).build()
    val bitmap = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
        ?: return null
    return withContext(Dispatchers.Default) {
        val palette = Palette.from(bitmap).generate()
        (palette.vibrantSwatch ?: palette.dominantSwatch)?.rgb
    }
}

// Seeds of the covers seen lately: going back to an album doesn't read its cover again.
private val seeds = LruCache<String, Int>(SEED_CACHE_SIZE)

/** Material's tonal-spot scheme for [seed] (ARGB), as Android builds one from the wallpaper. */
fun coverColorScheme(seed: Int, dark: Boolean): ColorScheme {
    val scheme = SchemeTonalSpot(Hct.fromInt(seed), dark, 0.0)
    val roles = MaterialDynamicColors()
    fun DynamicColor.color() = Color(getArgb(scheme))
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = roles.primary().color(),
        onPrimary = roles.onPrimary().color(),
        primaryContainer = roles.primaryContainer().color(),
        onPrimaryContainer = roles.onPrimaryContainer().color(),
        inversePrimary = roles.inversePrimary().color(),
        secondary = roles.secondary().color(),
        onSecondary = roles.onSecondary().color(),
        secondaryContainer = roles.secondaryContainer().color(),
        onSecondaryContainer = roles.onSecondaryContainer().color(),
        tertiary = roles.tertiary().color(),
        onTertiary = roles.onTertiary().color(),
        tertiaryContainer = roles.tertiaryContainer().color(),
        onTertiaryContainer = roles.onTertiaryContainer().color(),
        background = roles.background().color(),
        onBackground = roles.onBackground().color(),
        surface = roles.surface().color(),
        onSurface = roles.onSurface().color(),
        surfaceVariant = roles.surfaceVariant().color(),
        onSurfaceVariant = roles.onSurfaceVariant().color(),
        surfaceTint = roles.surfaceTint().color(),
        inverseSurface = roles.inverseSurface().color(),
        inverseOnSurface = roles.inverseOnSurface().color(),
        error = roles.error().color(),
        onError = roles.onError().color(),
        errorContainer = roles.errorContainer().color(),
        onErrorContainer = roles.onErrorContainer().color(),
        outline = roles.outline().color(),
        outlineVariant = roles.outlineVariant().color(),
        scrim = roles.scrim().color(),
        surfaceBright = roles.surfaceBright().color(),
        surfaceDim = roles.surfaceDim().color(),
        surfaceContainer = roles.surfaceContainer().color(),
        surfaceContainerHigh = roles.surfaceContainerHigh().color(),
        surfaceContainerHighest = roles.surfaceContainerHighest().color(),
        surfaceContainerLow = roles.surfaceContainerLow().color(),
        surfaceContainerLowest = roles.surfaceContainerLowest().color(),
        primaryFixed = roles.primaryFixed().color(),
        primaryFixedDim = roles.primaryFixedDim().color(),
        onPrimaryFixed = roles.onPrimaryFixed().color(),
        onPrimaryFixedVariant = roles.onPrimaryFixedVariant().color(),
        secondaryFixed = roles.secondaryFixed().color(),
        secondaryFixedDim = roles.secondaryFixedDim().color(),
        onSecondaryFixed = roles.onSecondaryFixed().color(),
        onSecondaryFixedVariant = roles.onSecondaryFixedVariant().color(),
        tertiaryFixed = roles.tertiaryFixed().color(),
        tertiaryFixedDim = roles.tertiaryFixedDim().color(),
        onTertiaryFixed = roles.onTertiaryFixed().color(),
        onTertiaryFixedVariant = roles.onTertiaryFixedVariant().color(),
    )
}

/** [target], reached from the scheme shown before it on [spec]: every role moves together. */
@Composable
private fun animateColorScheme(target: ColorScheme, spec: AnimationSpec<Float>): ColorScheme {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        // Start from what is on screen, even when the last change hadn't finished.
        from = lerp(from, to, progress.value)
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, spec)
    }
    return if (progress.value == 1f) to else lerp(from, to, progress.value)
}

/** The scheme [fraction] of the way from [start] to [stop], role by role. */
fun lerp(start: ColorScheme, stop: ColorScheme, fraction: Float): ColorScheme {
    fun mix(role: ColorScheme.() -> Color) = lerp(start.role(), stop.role(), fraction)
    return start.copy(
        primary = mix { primary },
        onPrimary = mix { onPrimary },
        primaryContainer = mix { primaryContainer },
        onPrimaryContainer = mix { onPrimaryContainer },
        inversePrimary = mix { inversePrimary },
        secondary = mix { secondary },
        onSecondary = mix { onSecondary },
        secondaryContainer = mix { secondaryContainer },
        onSecondaryContainer = mix { onSecondaryContainer },
        tertiary = mix { tertiary },
        onTertiary = mix { onTertiary },
        tertiaryContainer = mix { tertiaryContainer },
        onTertiaryContainer = mix { onTertiaryContainer },
        background = mix { background },
        onBackground = mix { onBackground },
        surface = mix { surface },
        onSurface = mix { onSurface },
        surfaceVariant = mix { surfaceVariant },
        onSurfaceVariant = mix { onSurfaceVariant },
        surfaceTint = mix { surfaceTint },
        inverseSurface = mix { inverseSurface },
        inverseOnSurface = mix { inverseOnSurface },
        error = mix { error },
        onError = mix { onError },
        errorContainer = mix { errorContainer },
        onErrorContainer = mix { onErrorContainer },
        outline = mix { outline },
        outlineVariant = mix { outlineVariant },
        scrim = mix { scrim },
        surfaceBright = mix { surfaceBright },
        surfaceDim = mix { surfaceDim },
        surfaceContainer = mix { surfaceContainer },
        surfaceContainerHigh = mix { surfaceContainerHigh },
        surfaceContainerHighest = mix { surfaceContainerHighest },
        surfaceContainerLow = mix { surfaceContainerLow },
        surfaceContainerLowest = mix { surfaceContainerLowest },
        primaryFixed = mix { primaryFixed },
        primaryFixedDim = mix { primaryFixedDim },
        onPrimaryFixed = mix { onPrimaryFixed },
        onPrimaryFixedVariant = mix { onPrimaryFixedVariant },
        secondaryFixed = mix { secondaryFixed },
        secondaryFixedDim = mix { secondaryFixedDim },
        onSecondaryFixed = mix { onSecondaryFixed },
        onSecondaryFixedVariant = mix { onSecondaryFixedVariant },
        tertiaryFixed = mix { tertiaryFixed },
        tertiaryFixedDim = mix { tertiaryFixedDim },
        onTertiaryFixed = mix { onTertiaryFixed },
        onTertiaryFixedVariant = mix { onTertiaryFixedVariant },
    )
}

private const val DARK_LUMINANCE = 0.5f
private const val PALETTE_SIZE = 128
private const val SEED_CACHE_SIZE = 64
