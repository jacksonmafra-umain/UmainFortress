package com.umain.fortress.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import com.umain.fortress.security.IntegrityVerdict
import com.umain.fortress.ui.icons.FortressIcons
import com.umain.fortress.ui.theme.FortressTheme

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
