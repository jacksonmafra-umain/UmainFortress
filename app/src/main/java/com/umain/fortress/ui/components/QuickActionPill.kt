package com.umain.fortress.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.umain.fortress.ui.components.preview.DarkModeProvider
import com.umain.fortress.ui.components.preview.PreviewSurface
import com.umain.fortress.ui.icons.FortressIcons
import com.umain.fortress.ui.theme.FortressPillShape

/**
 * Pill-shaped quick action used under the dashboard balance for Send / Receive.
 *
 * Anatomy: pill outline, leading 36dp lavender circle housing the icon, sans-serif label.
 *
 * @param icon Leading icon (typically [FortressIcons.Send] / [FortressIcons.Receive]).
 * @param label Pill label.
 * @param onClick Tap handler.
 * @param modifier Layout modifier applied to the pill row.
 */
@Composable
fun QuickActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, FortressPillShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, FortressPillShape)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Square 52dp icon button — used as the "more / grid" affordance to the right of the
 * Send / Receive pills.
 *
 * @param icon Glyph to render.
 * @param onClick Tap handler.
 * @param contentDescription Accessibility label.
 * @param modifier Layout modifier applied to the square surface.
 */
@Composable
fun QuickActionSquare(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Vertical action item: 48dp lavender circle stacked above a small label. Used in dense
 * action grids on screens like Cards.
 *
 * @param icon Glyph to render in the circle.
 * @param label Caption shown beneath the circle.
 * @param onClick Tap handler.
 * @param modifier Layout modifier applied to the tile column.
 */
@Composable
fun QuickActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(name = "QuickAction catalogue", showBackground = true)
@Composable
private fun QuickActionPreview(
    @PreviewParameter(DarkModeProvider::class) darkTheme: Boolean,
) {
    PreviewSurface(darkTheme = darkTheme) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionPill(icon = FortressIcons.Send, label = "Send", onClick = {}, modifier = Modifier.weight(1f))
                QuickActionPill(icon = FortressIcons.Receive, label = "Receive", onClick = {}, modifier = Modifier.weight(1f))
                QuickActionSquare(icon = FortressIcons.Grid, onClick = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                QuickActionTile(icon = FortressIcons.Card, label = "Cards", onClick = {})
                QuickActionTile(icon = FortressIcons.Savings, label = "Savings", onClick = {})
                QuickActionTile(icon = FortressIcons.Insights, label = "Insights", onClick = {})
            }
        }
    }
}
