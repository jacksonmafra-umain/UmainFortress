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
 * Extended design-system colour tokens that don't fit the Material 3
 * [androidx.compose.material3.ColorScheme] surface model.
 *
 * Exposed to composables via [FortressTheme.colors]. Add new tokens here when a colour
 * concept (gradient stop, money tail, semantic surface) has no clean home in M3.
 *
 * @property pageGradientTop Top stop of the splash / onboarding page wash.
 * @property pageGradientBottom Bottom stop of the splash / onboarding page wash.
 * @property cardSurface Off-white card surface in light mode, ink-elevated in dark mode.
 * @property cardInk Always-ink card variant (bottom nav, scan modal, money-tip card).
 * @property cardInkContent Foreground on top of [cardInk].
 * @property moneyTail Grey used by [com.umain.fortress.ui.components.MoneyText] for the tail.
 * @property successSurface Pastel green surface for success chips and credit rows.
 * @property successOn Foreground on top of [successSurface] and for credit amounts.
 * @property warningSurface Pastel amber surface for "Limited" verdict chips.
 * @property warningOn Foreground on top of [warningSurface] and for medium-risk badges.
 * @property dangerSurface Pastel coral surface for "Untrusted" verdict chips.
 * @property dangerOn Foreground on top of [dangerSurface] and for high-risk badges.
 * @property divider Hairline divider colour.
 * @property isLight `true` when the surrounding theme is the light variant.
 */
data class FortressColors(
    val pageGradientTop: Color,
    val pageGradientBottom: Color,
    val cardSurface: Color,
    val cardInk: Color,
    val cardInkContent: Color,
    val moneyTail: Color,
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

/** Composition local carrying the active [FortressColors] for the current theme. */
val LocalFortressColors = staticCompositionLocalOf<FortressColors> { LightExtended }

/**
 * Entry point for the extended design-system tokens. Use as
 * `FortressTheme.colors.moneyTail` from any composable below [FortressTheme].
 */
object FortressTheme {
    /** Active [FortressColors] for the surrounding theme. */
    val colors: FortressColors
        @Composable
        get() = LocalFortressColors.current
}

/**
 * Root composable applying the Fortress design system: Material 3 colour scheme, type
 * scale, shapes, extended colours, and a `WindowCompat` system-bar appearance hook.
 *
 * Wrap the entire app in this. Nested calls override the theme for their subtree, useful
 * for previews and the design-system catalogue.
 *
 * @param darkTheme Whether to use the dark colour scheme. Defaults to the system setting.
 * @param content Composable subtree that receives the theme.
 */
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
