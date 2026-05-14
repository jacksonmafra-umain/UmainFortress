package com.umain.fortress.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.umain.fortress.ui.components.preview.DarkModeProvider
import com.umain.fortress.ui.components.preview.PreviewSurface
import com.umain.fortress.ui.theme.FortressPillShape

/**
 * Primary call-to-action used for actions like Transfer, Confirm and Send money.
 *
 * Pill-shaped, lavender-filled (`primaryContainer` / `onPrimaryContainer`), with a built-in
 * indeterminate spinner that replaces the label while [loading] is `true`.
 *
 * @param text Button label.
 * @param onClick Tap handler.
 * @param modifier Layout modifier applied to the underlying [Button].
 * @param enabled Whether the button accepts taps (also implicitly `false` while loading).
 * @param loading Whether to show a spinner in place of the label.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        enabled = enabled && !loading,
        shape = FortressPillShape,
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text(text = text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * High-contrast CTA — ink-black fill, white label, pill-shaped.
 *
 * Used by the onboarding "Next / Get started" flow where the lavender [PrimaryButton]
 * would clash with the lavender wash background.
 *
 * @param text Button label.
 * @param onClick Tap handler.
 * @param modifier Layout modifier applied to the underlying [Button].
 * @param enabled Whether the button accepts taps (also implicitly `false` while loading).
 * @param loading Whether to show a spinner in place of the label.
 */
@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        enabled = enabled && !loading,
        shape = FortressPillShape,
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onSecondary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Outlined secondary action. Pill-shaped, transparent fill, primary-coloured border + label.
 *
 * Used alongside [PrimaryButton] for the cancel / dismiss / "Edit" option in two-button
 * action groups.
 *
 * @param text Button label.
 * @param onClick Tap handler.
 * @param modifier Layout modifier applied to the underlying [OutlinedButton].
 * @param enabled Whether the button accepts taps.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        enabled = enabled,
        shape = FortressPillShape,
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Preview(name = "Button catalogue", showBackground = true)
@Composable
private fun ButtonCataloguePreview(
    @PreviewParameter(DarkModeProvider::class) darkTheme: Boolean,
) {
    PreviewSurface(darkTheme = darkTheme) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryButton(text = "Transfer", onClick = {}, modifier = Modifier.fillMaxWidth())
            PrimaryButton(text = "Verifying", onClick = {}, loading = true, modifier = Modifier.fillMaxWidth())
            PrimaryButton(text = "Disabled", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
            InkButton(text = "Get started", onClick = {}, modifier = Modifier.fillMaxWidth())
            SecondaryButton(text = "Edit", onClick = {}, modifier = Modifier.fillMaxWidth())
        }
    }
}
