package com.umain.fortress.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.umain.fortress.ui.components.preview.DarkModeProvider
import com.umain.fortress.ui.components.preview.PreviewSurface
import com.umain.fortress.ui.icons.FortressIcons

/**
 * Design-system switch.
 *
 * The Material 3 [Switch] defaults blend into the lavender [MaterialTheme.colorScheme.primary]
 * surfaces of the Fortress palette — the thumb and track end up close enough in chroma that
 * the affordance disappears. This wrapper pins explicit colours so checked and unchecked
 * states stay legibly distinct on every surface and adds a leading check glyph on the thumb
 * for an extra visual cue.
 *
 * @param checked Current toggle state.
 * @param onCheckedChange Callback invoked with the new state when the user toggles.
 * @param modifier Layout modifier applied to the underlying [Switch].
 * @param enabled When `false`, the switch is rendered at reduced emphasis and ignores taps.
 */
@Composable
fun FortressSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        thumbContent = if (checked) {
            {
                Icon(
                    imageVector = FortressIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        } else null,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = MaterialTheme.colorScheme.primary,
            checkedIconColor = MaterialTheme.colorScheme.onPrimary,
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedTrackColor = MaterialTheme.colorScheme.surface,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            disabledCheckedThumbColor = MaterialTheme.colorScheme.surface,
            disabledCheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            disabledUncheckedTrackColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Preview(name = "FortressSwitch catalogue", showBackground = true)
@Composable
private fun FortressSwitchPreview(
    @PreviewParameter(DarkModeProvider::class) darkTheme: Boolean,
) {
    var on by remember { mutableStateOf(true) }
    var off by remember { mutableStateOf(false) }
    PreviewSurface(darkTheme = darkTheme) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            SwitchRow("Checked", on) { on = it }
            SwitchRow("Unchecked", off) { off = it }
            SwitchRow("Disabled checked", true, enabled = false, onChange = {})
            SwitchRow("Disabled unchecked", false, enabled = false, onChange = {})
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    value: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        FortressSwitch(checked = value, onCheckedChange = onChange, enabled = enabled)
    }
}
