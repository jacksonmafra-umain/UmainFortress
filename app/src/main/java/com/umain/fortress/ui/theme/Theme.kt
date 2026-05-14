package com.umain.fortress.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// =====================================================================================
//  Fortress design system — Material 3 scheme wiring
//  -------------------------------------------------------------------------------------
//  Light theme matches the "Vault" screenshots: soft violet wash background, off-white
//  cards, lavender accent, ink-black bottom nav. Dark theme adapts the same palette to
//  near-black surfaces so system dark mode is supported without a brand reskin.
//
//  We also expose a small bag of *extended* tokens (FortressColors / LocalFortressColors)
//  for design-system needs that don't map cleanly onto the M3 ColorScheme — gradient
//  stops, the dedicated "money tail" grey, success/danger surface fills, etc.
// =====================================================================================

private val FortressLightColors = lightColorScheme(
    primary = Lavender500,
    onPrimary = Cloud0,
    primaryContainer = Lavender100,
    onPrimaryContainer = Lavender700,
    secondary = Ink900,
    onSecondary = Cloud0,
    secondaryContainer = Ink800,
    onSecondaryContainer = Cloud0,
    tertiary = Lavender700,
    onTertiary = Cloud0,
    tertiaryContainer = Lavender50,
    onTertiaryContainer = Lavender700,
    error = Coral500,
    onError = Cloud0,
    errorContainer = Coral100,
    onErrorContainer = Color(0xFF8A1F22),
    background = Cloud50,
    onBackground = Ink900,
    surface = Cloud0,
    onSurface = Ink900,
    surfaceVariant = Cloud100,
    onSurfaceVariant = MistText500,
    surfaceContainer = Cloud100,
    surfaceContainerHigh = Cloud200,
    surfaceContainerHighest = Cloud300,
    outline = Cloud300,
    outlineVariant = Cloud200,
)

private val FortressDarkColors = darkColorScheme(
    primary = Lavender300,
    onPrimary = Ink950,
    primaryContainer = Lavender700,
    onPrimaryContainer = Lavender100,
    secondary = Cloud0,
    onSecondary = Ink950,
    secondaryContainer = InkSurfaceElevated,
    onSecondaryContainer = Cloud0,
    tertiary = Lavender400,
    onTertiary = Ink950,
    tertiaryContainer = Color(0xFF3A2A8C),
    onTertiaryContainer = Lavender100,
    error = Coral500,
    onError = Cloud0,
    errorContainer = Color(0xFF5A1A1F),
    onErrorContainer = Coral100,
    background = InkSurfaceDark,
    onBackground = Cloud0,
    surface = InkSurfaceElevated,
    onSurface = Cloud0,
    surfaceVariant = InkSurfaceHigh,
    onSurfaceVariant = MistText300,
    surfaceContainer = InkSurfaceElevated,
    surfaceContainerHigh = InkSurfaceHigh,
    surfaceContainerHighest = Ink500,
    outline = InkSurfaceHigh,
    outlineVariant = InkSurfaceElevated,
)

/**
 * Extended tokens that don't fit into Material 3's [androidx.compose.material3.ColorScheme]
 * but are still part of the design system contract.
 */
data class FortressColors(
    val pageGradientTop: Color,
    val pageGradientBottom: Color,
    val cardSurface: Color,           // off-white in light, deep ink in dark
    val cardInk: Color,                // always-ink card variant (Scan, Bottom nav)
    val cardInkContent: Color,         // text/icons on the always-ink card
    val moneyTail: Color,              // grey for the .24 tail in money display
    val successSurface: Color,
    val successOn: Color,
    val warningSurface: Color,
    val warningOn: Color,
    val dangerSurface: Color,
    val dangerOn: Color,
    val divider: Color,
    val isLight: Boolean,
)

private val LightExtended = FortressColors(
    pageGradientTop = Cloud50,
    pageGradientBottom = Lavender100,
    cardSurface = Cloud0,
    cardInk = Ink900,
    cardInkContent = Cloud0,
    moneyTail = MoneyTail,
    successSurface = Sage100,
    successOn = Sage500,
    warningSurface = AmberStatus100,
    warningOn = Color(0xFF8A5300),
    dangerSurface = Coral100,
    dangerOn = Coral500,
    divider = Cloud200,
    isLight = true,
)

private val DarkExtended = FortressColors(
    pageGradientTop = InkSurfaceDark,
    pageGradientBottom = Ink950,
    cardSurface = InkSurfaceElevated,
    cardInk = Ink950,
    cardInkContent = Cloud0,
    moneyTail = MistText300,
    successSurface = Color(0xFF0E5A3E),
    successOn = Sage500,
    warningSurface = Color(0xFF6A4A0F),
    warningOn = AmberStatus500,
    dangerSurface = Color(0xFF5A1A1F),
    dangerOn = Coral500,
    divider = InkSurfaceHigh,
    isLight = false,
)

val LocalFortressColors = staticCompositionLocalOf<FortressColors> { LightExtended }

object FortressTheme {
    val colors: FortressColors
        @Composable
        get() = LocalFortressColors.current
}

@Composable
fun FortressTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) FortressDarkColors else FortressLightColors
    val extended = if (darkTheme) DarkExtended else LightExtended

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalFortressColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FortressTypography,
            shapes = FortressShapes,
            content = content,
        )
    }
}
