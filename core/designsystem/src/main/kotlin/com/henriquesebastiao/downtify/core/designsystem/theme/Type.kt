package com.henriquesebastiao.downtify.core.designsystem.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.henriquesebastiao.downtify.core.designsystem.R
import com.henriquesebastiao.downtify.core.model.AppFont

// Three choices in Settings › Appearance:
//  - Google Sans Flex (the default): its rounded cut for everything.
//  - Downtify classic: Sora for display, headline and titleLarge; DM Sans for everything else.
//  - System: the phone's own font (FontFamily.Default).
// Google downloadable fonts are fetched by Play services and shared between apps. Without
// Play services (de-Googled phones) Compose falls back to the system font.

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val weights = listOf(
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
    FontWeight.ExtraBold,
)

private fun family(name: String) =
    FontFamily(weights.map { Font(googleFont = GoogleFont(name), fontProvider = provider, weight = it) })

val Sora = family("Sora")
val DmSans = family("DM Sans")

/** Google Sans Flex's roundness axis, 0 (square ends) to 100 (fully rounded). */
private const val ROUNDNESS_AXIS = "ROND"

// The rounded cut of Google Sans Flex, the one on Pixel phones: the variable font's ROND axis at 100.
// The width is set too, at its normal 100: the hero's wide cut must never stand in for it.
val GoogleSansFlex = FontFamily(
    weights.map { weight ->
        Font(
            googleFont = GoogleFont("Google Sans Flex"),
            fontProvider = provider,
            weight = weight,
            variationSettings = FontVariation.Settings(
                weight,
                FontStyle.Normal,
                FontVariation.width(100f),
                FontVariation.Setting(ROUNDNESS_AXIS, 100f),
            ),
        )
    },
)

/**
 * Google Sans Flex at its widest (wdth 151), rounded like the rest (ROND 100): the hero title only.
 * Its request to the font provider must differ from the UI's SemiBold one: the provider tells
 * fonts apart by name, weight and style only, so two SemiBold requests got the same font and a
 * mini player title came out wide. Asking for the exact font (no best effort) makes it its own.
 */
private val GoogleSansFlexWide = FontFamily(
    Font(
        googleFont = GoogleFont("Google Sans Flex", bestEffort = false),
        fontProvider = provider,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(FontWeight.SemiBold.weight),
            FontVariation.width(151f),
            FontVariation.Setting(ROUNDNESS_AXIS, 100f),
        ),
    ),
)

private val base = Typography()

/** The family of headlines and of everything else. */
private data class Faces(val heading: FontFamily, val body: FontFamily)

private fun facesOf(font: AppFont) = when (font) {
    AppFont.GoogleSansFlex -> Faces(GoogleSansFlex, GoogleSansFlex)
    AppFont.Downtify -> Faces(Sora, DmSans)
    AppFont.System -> Faces(FontFamily.Default, FontFamily.Default)
}

/**
 * The v3 type scale in [font]: bold 36 sp page titles (displayMedium), semibold headlines, 18 sp
 * titleMedium, 16/14 sp labels. Sizes, weights and line heights are the same for all three fonts.
 * Each emphasized style is its role one weight step up.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun downtifyTypography(font: AppFont): Typography {
    val faces = facesOf(font)

    fun TextStyle.role(heading: Boolean, weight: Int, size: Int, line: Int, tracking: Double = 0.0) = copy(
        fontFamily = if (heading) faces.heading else faces.body,
        fontWeight = FontWeight(weight),
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
    )

    fun TextStyle.emphasized(weight: Int) = copy(fontWeight = FontWeight(weight))

    val displayLarge = base.displayLarge.role(heading = true, weight = 700, size = 48, line = 56)
    val displayMedium = base.displayMedium.role(heading = true, weight = 700, size = 36, line = 44)
    val displaySmall = base.displaySmall.role(heading = true, weight = 400, size = 30, line = 38)
    val headlineLarge = base.headlineLarge.role(heading = true, weight = 600, size = 32, line = 40)
    val headlineMedium = base.headlineMedium.role(heading = true, weight = 600, size = 28, line = 36)
    val headlineSmall = base.headlineSmall.role(heading = true, weight = 600, size = 24, line = 32)
    val titleLarge = base.titleLarge.role(heading = true, weight = 400, size = 22, line = 28)
    val titleMedium = base.titleMedium.role(heading = false, weight = 500, size = 18, line = 24, tracking = 0.15)
    val titleSmall = base.titleSmall.role(heading = false, weight = 500, size = 14, line = 20, tracking = 0.1)
    val bodyLarge = base.bodyLarge.role(heading = false, weight = 400, size = 16, line = 24, tracking = 0.5)
    val bodyMedium = base.bodyMedium.role(heading = false, weight = 400, size = 14, line = 20, tracking = 0.25)
    val bodySmall = base.bodySmall.role(heading = false, weight = 400, size = 12, line = 16, tracking = 0.4)
    val labelLarge = base.labelLarge.role(heading = false, weight = 500, size = 16, line = 20, tracking = 0.1)
    val labelMedium = base.labelMedium.role(heading = false, weight = 500, size = 14, line = 16, tracking = 0.5)
    val labelSmall = base.labelSmall.role(heading = false, weight = 500, size = 11, line = 16, tracking = 0.5)

    return Typography(
        displayLarge = displayLarge,
        displayMedium = displayMedium,
        displaySmall = displaySmall,
        headlineLarge = headlineLarge,
        headlineMedium = headlineMedium,
        headlineSmall = headlineSmall,
        titleLarge = titleLarge,
        titleMedium = titleMedium,
        titleSmall = titleSmall,
        bodyLarge = bodyLarge,
        bodyMedium = bodyMedium,
        bodySmall = bodySmall,
        labelLarge = labelLarge,
        labelMedium = labelMedium,
        labelSmall = labelSmall,
        displayLargeEmphasized = displayLarge.emphasized(800),
        displayMediumEmphasized = displayMedium.emphasized(800),
        displaySmallEmphasized = displaySmall.emphasized(500),
        headlineLargeEmphasized = headlineLarge.emphasized(700),
        headlineMediumEmphasized = headlineMedium.emphasized(700),
        headlineSmallEmphasized = headlineSmall.emphasized(700),
        titleLargeEmphasized = titleLarge.emphasized(500),
        titleMediumEmphasized = titleMedium.emphasized(700),
        titleSmallEmphasized = titleSmall.emphasized(700),
        bodyLargeEmphasized = bodyLarge.emphasized(500),
        bodyMediumEmphasized = bodyMedium.emphasized(500),
        bodySmallEmphasized = bodySmall.emphasized(500),
        labelLargeEmphasized = labelLarge.emphasized(700),
        labelMediumEmphasized = labelMedium.emphasized(700),
        labelSmallEmphasized = labelSmall.emphasized(700),
    )
}

/** The default font's type scale: what previews and tests use. */
val DowntifyTypography = downtifyTypography(AppFont.Default)

/**
 * The styles Material 3's scale doesn't have. The hero's face is fixed: it doesn't follow the font
 * setting. The data styles do: they're the app's own face.
 */
object DowntifyType {
    /** The wide, rounded hero title (home, welcome): Google Sans Flex at wdth 151, whatever font the app uses. */
    val hero = base.displayLarge.copy(
        fontFamily = GoogleSansFlexWide,
        fontWeight = FontWeight.SemiBold,
        fontSize = 52.sp,
        // In em, so the line follows the size when a long word makes the title step down.
        lineHeight = 0.95.em,
        letterSpacing = (-0.02).em,
    )

    /** [hero] for titles with a long word (more than ~8 letters). */
    val heroSmall = hero.copy(fontSize = 44.sp)

    // Technical values (durations, bitrates, sample rates, sizes, formats): the app's own face, with
    // tabular figures so a value that changes doesn't shift.
    val dataLarge: TextStyle
        @Composable @ReadOnlyComposable
        get() = data(FontWeight.Medium, size = 22, line = 28)
    val dataMedium: TextStyle
        @Composable @ReadOnlyComposable
        get() = data(FontWeight.Medium, size = 14, line = 20)
    val dataSmall: TextStyle
        @Composable @ReadOnlyComposable
        get() = data(FontWeight.Normal, size = 12, line = 16)

    /** A technical value being typed (a server address): the text field's size, in the data style. */
    val dataInput: TextStyle
        @Composable @ReadOnlyComposable
        get() = data(FontWeight.Normal, size = 16, line = 24)

    @Composable
    @ReadOnlyComposable
    private fun data(weight: FontWeight, size: Int, line: Int) = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )
}

/**
 * [text], a translated sentence with values in it ("2.4 GB used", "212 songs · 5.6 GB free"),
 * with each of [values] in tabular figures where it appears. The sentence keeps its language's
 * word order and its face; only the values' figures change.
 */
fun withDataValues(text: String, vararg values: String): AnnotatedString = withDataValues(text, values.asList())

/** [withDataValues] for values known only at run time (a row's, a dialog option's). */
fun withDataValues(text: String, values: List<String>): AnnotatedString = buildAnnotatedString {
    append(text)
    values.filter { it.isNotEmpty() }.forEach { value ->
        var from = text.indexOf(value)
        while (from >= 0) {
            addStyle(DataSpan, from, from + value.length)
            from = text.indexOf(value, from + value.length)
        }
    }
}

private const val TABULAR_FIGURES = "tnum"

private val DataSpan = SpanStyle(fontFeatureSettings = TABULAR_FIGURES)
