package com.umain.fortress.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppMaybe
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
import com.umain.fortress.ui.theme.Amber500
import com.umain.fortress.ui.theme.Emerald500
import com.umain.fortress.ui.theme.Emerald100
import com.umain.fortress.ui.theme.Vermilion500
import com.umain.fortress.ui.theme.Vermilion400

@Composable
fun SecurityChip(
    verdict: IntegrityVerdict,
    modifier: Modifier = Modifier,
) {
    val (icon, fg, bg) = when (verdict) {
        IntegrityVerdict.Trusted -> Triple(Icons.Default.GppGood, Emerald500, Emerald100)
        is IntegrityVerdict.Limited -> Triple(Icons.Default.GppMaybe, Color(0xFF8A5300), Color(0xFFFFE9B8))
        is IntegrityVerdict.Untrusted -> Triple(Icons.Default.GppBad, Vermilion500, Color(0xFFFDE2E2))
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

@Suppress("unused")
private val UnusedSuppress = Amber500 to Vermilion400
