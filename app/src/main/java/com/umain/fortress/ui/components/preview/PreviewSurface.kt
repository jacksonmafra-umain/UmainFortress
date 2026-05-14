package com.umain.fortress.ui.components.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.umain.fortress.ui.theme.FortressTheme

/**
 * Wraps preview content in [FortressTheme] with the page background colour and a small
 * inset so individual components render as they would in-app.
 *
 * Every `@Preview` in the design-system uses this surface so the previews stay terse and
 * the theme wiring is single-sourced.
 */
@Composable
fun PreviewSurface(
    darkTheme: Boolean = false,
    padding: PaddingValues = PaddingValues(20.dp),
    content: @Composable () -> Unit,
) {
    FortressTheme(darkTheme = darkTheme) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
        ) {
            content()
        }
    }
}

/**
 * Two-value parameter provider that flips a preview between light and dark mode without
 * duplicating the `@Preview` declaration.
 */
class DarkModeProvider : PreviewParameterProvider<Boolean> {
    override val values: Sequence<Boolean> = sequenceOf(false, true)
}
