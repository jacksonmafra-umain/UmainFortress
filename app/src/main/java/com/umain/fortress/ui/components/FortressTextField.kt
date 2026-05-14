package com.umain.fortress.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.umain.fortress.ui.components.preview.DarkModeProvider
import com.umain.fortress.ui.components.preview.PreviewSurface

/**
 * Single-line outlined text field, design-system styled.
 *
 * Surface-filled at rest (`surfaceContainer`) and surface-filled (`surface`) when focused;
 * the border switches from `outlineVariant` to `primary` on focus and the label colour
 * follows.
 *
 * @param value Current text value.
 * @param onValueChange Callback fired on every keystroke.
 * @param label Floating label.
 * @param modifier Layout modifier applied to the underlying [OutlinedTextField].
 * @param isPassword When `true`, applies password visual transformation and password keyboard.
 * @param keyboardType Keyboard type (ignored when [isPassword] is `true`).
 * @param enabled Whether the field accepts input.
 * @param supportingText Optional helper text shown below the field.
 * @param isError When `true`, renders the field in the error state.
 */
@Composable
fun FortressTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    supportingText: String? = null,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        isError = isError,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
        ),
        visualTransformation =
            if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        supportingText = supportingText?.let { { Text(it) } },
        shape = MaterialTheme.shapes.large,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledBorderColor = Color.Transparent,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@Preview(name = "FortressTextField", showBackground = true)
@Composable
private fun FortressTextFieldPreview(
    @PreviewParameter(DarkModeProvider::class) darkTheme: Boolean,
) {
    PreviewSurface(darkTheme = darkTheme) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FortressTextField(value = "", onValueChange = {}, label = "Recipient name")
            FortressTextField(value = "alice@fortress.dev", onValueChange = {}, label = "Email")
            FortressTextField(value = "passw0rd!", onValueChange = {}, label = "Password", isPassword = true)
            FortressTextField(value = "wrong", onValueChange = {}, label = "Amount", isError = true, supportingText = "Insufficient funds")
        }
    }
}
