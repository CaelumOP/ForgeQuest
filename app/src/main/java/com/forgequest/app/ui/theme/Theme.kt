package com.forgequest.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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
)

val LightFitColors = FitColors(
    background = Color(0xFFF6F8FC),
    card = Color(0xFFFFFFFF),
    cardHigh = Color(0xFFEDF1F7),
    divider = Color(0xFFE1E5EA),
    textPrimary = Color(0xFF1F1F1F),
    textSecondary = Color(0xFF5F6368),
    teal = Color(0xFF00897B),
    tealTrack = Color(0xFFD5F0EB),
    onTeal = Color(0xFFFFFFFF),
    blue = Color(0xFF1A73E8),
    blueTrack = Color(0xFFDCE8FC),
    gold = Color(0xFFE8A500),
    goldTrack = Color(0xFFFCEFC7),
    coral = Color(0xFFE5675C),
    coralTrack = Color(0xFFFCE0DD),
    purple = Color(0xFF9334E6),
    purpleTrack = Color(0xFFEFE1FC),
    muscleRest = Color(0xFFDCE1E8),
    bodyOutline = Color(0xFFB9C0C9),
    isDark = false,
)

private val LocalFitColors = staticCompositionLocalOf { DarkFitColors }

object Fit {
    val colors: FitColors
        @Composable @ReadOnlyComposable get() = LocalFitColors.current
}

private val FitTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Normal),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Normal),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun ForgeQuestTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val fit = if (dark) DarkFitColors else LightFitColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = fit.teal, onPrimary = fit.onTeal, primaryContainer = fit.tealTrack, onPrimaryContainer = fit.teal,
            secondary = fit.blue, secondaryContainer = fit.blueTrack, onSecondaryContainer = fit.blue,
            background = fit.background, onBackground = fit.textPrimary,
            surface = fit.background, onSurface = fit.textPrimary, onSurfaceVariant = fit.textSecondary,
            surfaceContainerLowest = fit.background, surfaceContainerLow = fit.card, surfaceContainer = fit.card,
            surfaceContainerHigh = fit.card, surfaceContainerHighest = fit.cardHigh,
            outline = fit.divider, outlineVariant = fit.divider, error = fit.coral,
        )
    } else {
        lightColorScheme(
            primary = fit.teal, onPrimary = fit.onTeal, primaryContainer = fit.tealTrack, onPrimaryContainer = Color(0xFF00413A),
            secondary = fit.blue, secondaryContainer = fit.blueTrack, onSecondaryContainer = Color(0xFF0B3C86),
            background = fit.background, onBackground = fit.textPrimary,
            surface = fit.background, onSurface = fit.textPrimary, onSurfaceVariant = fit.textSecondary,
            surfaceContainerLowest = fit.card, surfaceContainerLow = fit.card, surfaceContainer = fit.card,
            surfaceContainerHigh = fit.card, surfaceContainerHighest = fit.cardHigh,
            outline = fit.divider, outlineVariant = fit.divider, error = fit.coral,
        )
    }
    CompositionLocalProvider(LocalFitColors provides fit) {
        MaterialTheme(colorScheme = scheme, typography = FitTypography, content = content)
    }
}
