package com.forgequest.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgequest.app.R

/**
 * Google Fit-style palette. Every color the UI uses comes from here so light and dark stay in sync.
 * "Track" colors are the faint ring/icon-circle backgrounds behind an accent.
 */
@Immutable
data class FitColors(
    val background: Color,
    val card: Color,
    val cardHigh: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val teal: Color,
    val tealTrack: Color,
    val onTeal: Color,
    val blue: Color,
    val blueTrack: Color,
    val gold: Color,
    val goldTrack: Color,
    val coral: Color,
    val coralTrack: Color,
    val purple: Color,
    val purpleTrack: Color,
    val muscleRest: Color,
    val bodyOutline: Color,
    val isDark: Boolean,
    /** Chart hues, validated for colour-blind separation and 3:1 contrast on the card surface in each mode. */
    val chartStrength: Color,
    val chartWeight: Color,
    val chartCalories: Color,
    val chartProtein: Color,
    val chartOther: Color,
    val chartGain: Color,
    val chartGainTrack: Color,
)

val DarkFitColors = FitColors(
    background = Color(0xFF131314),
    card = Color(0xFF1E1F20),
    cardHigh = Color(0xFF282A2C),
    divider = Color(0xFF333537),
    textPrimary = Color(0xFFE3E3E3),
    textSecondary = Color(0xFF9AA0A6),
    teal = Color(0xFF5CD6C0),
    tealTrack = Color(0xFF1C2E2B),
    onTeal = Color(0xFF00382E),
    blue = Color(0xFF8AB4F8),
    blueTrack = Color(0xFF1D2536),
    gold = Color(0xFFFDD663),
    goldTrack = Color(0xFF2D2618),
    coral = Color(0xFFF28B82),
    coralTrack = Color(0xFF3A2220),
    purple = Color(0xFFC58AF9),
    purpleTrack = Color(0xFF2A2136),
    muscleRest = Color(0xFF34373A),
    bodyOutline = Color(0xFF4A4D51),
    isDark = true,
    chartStrength = Color(0xFF21A693),
    chartWeight = Color(0xFF4C8FE6),
    chartCalories = Color(0xFFB07F00),
    chartProtein = Color(0xFFC85A72),
    chartOther = Color(0xFFA572E0),
    chartGain = Color(0xFF21A693),
    chartGainTrack = Color(0xFF1C2E2B),
)

/**
 * Light accents are deliberately deeper than the dark ones: they're used as text on white cards,
 * and each one measures at least 4.5:1 there and on the pale "track" tints (WCAG AA for body text).
 */
val LightFitColors = FitColors(
    background = Color(0xFFF6F8FC),
    card = Color(0xFFFFFFFF),
    cardHigh = Color(0xFFEDF1F7),
    divider = Color(0xFFE1E5EA),
    textPrimary = Color(0xFF1F1F1F),
    textSecondary = Color(0xFF5F6368),
    teal = Color(0xFF00705F),
    tealTrack = Color(0xFFD5F0EB),
    onTeal = Color(0xFFFFFFFF),
    blue = Color(0xFF0B57D0),
    blueTrack = Color(0xFFDCE8FC),
    gold = Color(0xFF8A6100),
    goldTrack = Color(0xFFFCEFC7),
    coral = Color(0xFFB8372F),
    coralTrack = Color(0xFFFCE0DD),
    purple = Color(0xFF7B2CBF),
    purpleTrack = Color(0xFFEFE1FC),
    muscleRest = Color(0xFFDCE1E8),
    bodyOutline = Color(0xFFB9C0C9),
    isDark = false,
    chartStrength = Color(0xFF008F78),
    chartWeight = Color(0xFF0B57D0),
    chartCalories = Color(0xFF8A6100),
    chartProtein = Color(0xFFC62A6E),
    chartOther = Color(0xFF6F2DBD),
    chartGain = Color(0xFF008F78),
    chartGainTrack = Color(0xFFD5F0EB),
)

private val LocalFitColors = staticCompositionLocalOf { DarkFitColors }

object Fit {
    val colors: FitColors
        @Composable @ReadOnlyComposable get() = LocalFitColors.current
}

/**
 * Tabular (equal-width) figures, for numbers that change in place: timers, steppers, ring centres, stats.
 * Without it a "1" is narrower than an "8" and the text shifts as the value ticks.
 */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * Manrope (SIL OFL) for display and title sizes: a geometric sans with open, rounded forms that reads as
 * athletic without shouting. Body and label text stay on the system sans for density and Arabic fallback.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Manrope = FontFamily(
    Font(R.font.manrope, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.manrope, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.manrope, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/**
 * IBM Plex Sans Arabic (SIL OFL), the Arabic face: a calm, compact sans with Arabic and Latin glyphs, so Western digits
 * and English words inside Arabic text match. Static 400, 500 and 600; Bold falls back to 600.
 */
val PlexArabic = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.Bold),
)

/** Manrope for numbers, but Plex Arabic in Arabic, where the text often carries an Arabic unit Manrope lacks. */
val NumberFont: FontFamily
    @Composable @ReadOnlyComposable get() =
        if (androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language == "ar") PlexArabic else Manrope

private val FitTypography = Typography(
    displaySmall = TextStyle(fontFamily = Manrope, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Manrope, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = Manrope, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Manrope, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/**
 * Arabic: IBM Plex Sans Arabic on every style (same sizes, weights and line heights as English), no tracking on the
 * display sizes, and every style picks its direction from the text itself, so English data (plan notes, food names) still reads left to right with its full stop at the end.
 */
private val ArabicTypography = FitTypography.run {
    fun TextStyle.content() = copy(fontFamily = PlexArabic, textDirection = androidx.compose.ui.text.style.TextDirection.Content)
    fun TextStyle.plain() = copy(letterSpacing = 0.sp).content()
    copy(
        displaySmall = displaySmall.plain(), headlineMedium = headlineMedium.plain(), headlineSmall = headlineSmall.plain(), titleLarge = titleLarge.plain(),
        titleMedium = titleMedium.content(), titleSmall = titleSmall.content(), bodyLarge = bodyLarge.content(), bodyMedium = bodyMedium.content(),
        bodySmall = bodySmall.content(), labelLarge = labelLarge.content(), labelMedium = labelMedium.content(), labelSmall = labelSmall.content(),
    )
}

/**
 * The full Material 3 colour scheme, built from [FitColors] so built-in components (switches, snackbars, sheets, chips,
 * the navigation bar) pick up our palette without per-call colours. Our hex values win wherever the Material spec
 * differs; roles we have no colour for take the spec's value. Surface containers step up in tone from the canvas:
 * lowest, low, container (our card), high (our raised card), highest. No dynamic colour: the palette is the brand.
 */
internal fun forgeDarkScheme(f: FitColors) = darkColorScheme(
    primary = f.teal, onPrimary = f.onTeal, primaryContainer = f.tealTrack, onPrimaryContainer = f.teal,
    inversePrimary = LightFitColors.teal,
    secondary = f.blue, onSecondary = Color(0xFF003062), secondaryContainer = f.blueTrack, onSecondaryContainer = f.blue,
    tertiary = f.gold, onTertiary = Color(0xFF3F2E00), tertiaryContainer = f.goldTrack, onTertiaryContainer = f.gold,
    error = f.coral, onError = Color(0xFF601410), errorContainer = f.coralTrack, onErrorContainer = f.coral,
    background = f.background, onBackground = f.textPrimary,
    surface = f.background, onSurface = f.textPrimary, surfaceVariant = f.cardHigh, onSurfaceVariant = f.textSecondary,
    inverseSurface = f.textPrimary, inverseOnSurface = f.card,
    outline = Color(0xFF8E918F), outlineVariant = f.divider, scrim = Color.Black,
    surfaceContainerLowest = Color(0xFF0E0E0F), surfaceContainerLow = Color(0xFF18191A), surfaceContainer = f.card,
    surfaceContainerHigh = f.cardHigh, surfaceContainerHighest = Color(0xFF333537),
)

internal fun forgeLightScheme(f: FitColors) = lightColorScheme(
    primary = f.teal, onPrimary = f.onTeal, primaryContainer = f.tealTrack, onPrimaryContainer = Color(0xFF00413A),
    inversePrimary = DarkFitColors.teal,
    secondary = f.blue, onSecondary = Color.White, secondaryContainer = f.blueTrack, onSecondaryContainer = Color(0xFF0B3C86),
    tertiary = f.gold, onTertiary = Color.White, tertiaryContainer = f.goldTrack, onTertiaryContainer = Color(0xFF261900),
    error = f.coral, onError = Color.White, errorContainer = f.coralTrack, onErrorContainer = Color(0xFF410E0B),
    background = f.background, onBackground = f.textPrimary,
    surface = f.background, onSurface = f.textPrimary, surfaceVariant = f.cardHigh, onSurfaceVariant = f.textSecondary,
    inverseSurface = Color(0xFF2E3133), inverseOnSurface = Color(0xFFF0F1F3),
    outline = Color(0xFF747775), outlineVariant = f.divider, scrim = Color.Black,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F4F9), surfaceContainer = f.card,
    surfaceContainerHigh = f.cardHigh, surfaceContainerHighest = Color(0xFFD8E0EC),
)

/** The M3 shape scale, set explicitly. Our own components keep their corners (cards 24, pills fully round). */
private val ForgeShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ForgeQuestTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val fit = if (dark) DarkFitColors else LightFitColors
    val scheme = if (dark) forgeDarkScheme(fit) else forgeLightScheme(fit)
    // Manrope has no Arabic glyphs, and the negative tracking on display sizes pulls joined Arabic letters apart and
    // makes Compose wrap the last letter of a title onto its own line. Arabic gets IBM Plex Sans Arabic, untracked.
    val arabic = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language == "ar"
    CompositionLocalProvider(LocalFitColors provides fit) {
        MaterialTheme(colorScheme = scheme, shapes = ForgeShapes, typography = if (arabic) ArabicTypography else FitTypography) {
            ProvideReducedMotion(content)
        }
    }
}
