package com.umain.fortress.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val FortressLightColors = lightColorScheme(
    primary = Midnight800,
    onPrimary = Cloud50,
    primaryContainer = Midnight100,
    onPrimaryContainer = Midnight800,
    secondary = Emerald500,
    onSecondary = Cloud50,
    secondaryContainer = Emerald100,
    onSecondaryContainer = Midnight900,
    tertiary = Violet500,
    onTertiary = Cloud50,
    tertiaryContainer = Violet100,
    onTertiaryContainer = Midnight900,
    error = Vermilion500,
    onError = Cloud50,
    errorContainer = Color(0xFFFDE2E2),
    onErrorContainer = Vermilion600,
    background = Cloud50,
    onBackground = Midnight900,
    surface = Color.White,
    onSurface = Midnight900,
    surfaceVariant = Cloud100,
    onSurfaceVariant = Slate500,
    surfaceContainer = Cloud100,
    surfaceContainerHigh = Cloud200,
    outline = Cloud200,
    outlineVariant = Cloud100,
)

private val FortressDarkColors = darkColorScheme(
    primary = Mist100,
    onPrimary = Midnight900,
    primaryContainer = Midnight700,
    onPrimaryContainer = Mist100,
    secondary = Emerald400,
    onSecondary = Midnight900,
    secondaryContainer = Color(0xFF0E5A3E),
    onSecondaryContainer = Emerald100,
    tertiary = Violet400,
    onTertiary = Midnight900,
    tertiaryContainer = Color(0xFF3A2A8C),
    onTertiaryContainer = Violet100,
    error = Vermilion400,
    onError = Midnight900,
    errorContainer = Color(0xFF5A1A1F),
    onErrorContainer = Color(0xFFFDE2E2),
    background = Ink900,
    onBackground = Mist100,
    surface = Ink800,
    onSurface = Mist100,
    surfaceVariant = Ink700,
    onSurfaceVariant = Mist300,
    surfaceContainer = Ink800,
    surfaceContainerHigh = Ink700,
    outline = Ink700,
    outlineVariant = Ink800,
)

@Composable
fun FortressTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) FortressDarkColors else FortressLightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FortressTypography,
        shapes = FortressShapes,
        content = content,
    )
}
