package com.umain.fortress.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.umain.fortress.security.IntegrityVerdict
import com.umain.fortress.ui.components.preview.DarkModeProvider
import com.umain.fortress.ui.components.preview.PreviewSurface
import com.umain.fortress.ui.icons.FortressIcons
import com.umain.fortress.ui.theme.FortressTheme

/**
 * Pill-shaped status chip that surfaces the current [IntegrityVerdict] in the dashboard
 * header. Maps each verdict to a paired (icon, foreground, background) triple from the
 * extended [FortressTheme.colors] semantic surfaces.
 *
 * @param verdict The verdict to render — Trusted / Limited / Untrusted.
 * @param modifier Layout modifier applied to the chip surface.
 */
@Composable
fun SecurityChip(
    verdict: IntegrityVerdict,
    modifier: Modifier = Modifier,
) {
    val theme = FortressTheme.colors
    val (icon, fg, bg) = when (verdict) {
        IntegrityVerdict.Trusted -> Triple(FortressIcons.ShieldGood, theme.successOn, theme.successSurface)
        is IntegrityVerdict.Limited -> Triple(FortressIcons.ShieldMaybe, theme.warningOn, theme.warningSurface)
        is IntegrityVerdict.Untrusted -> Triple(FortressIcons.ShieldBad, theme.dangerOn, theme.dangerSurface)
    }
    SecurityChipScaffold(icon = icon, label = verdict.label, fg = fg, bg = bg, modifier = modifier)
}

@Composable
private fun SecurityChipScaffold(
    icon: ImageVector,
    label: String,
    fg: Color,
    bg: Color,
    modifier: Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .background(color = bg, shape = CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
        )
    }
}

@Preview(name = "SecurityChip states", showBackground = true)
@Composable
private fun SecurityChipPreview(
    @PreviewParameter(DarkModeProvider::class) darkTheme: Boolean,
) {
    PreviewSurface(darkTheme = darkTheme) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SecurityChip(verdict = IntegrityVerdict.Trusted)
            SecurityChip(verdict = IntegrityVerdict.Limited(listOf("Emulator detected")))
            SecurityChip(verdict = IntegrityVerdict.Untrusted(listOf("Root detected")))
        }
    }
}
