package com.umain.fortress.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.umain.fortress.ui.components.preview.DarkModeProvider
import com.umain.fortress.ui.components.preview.PreviewSurface

/**
 * Section title row used at the head of dashboard sections (Transactions, My cards,
 * Monthly Payment, …). Renders the title on the leading edge and an optional small
 * tappable action label (e.g. `See All`) on the trailing edge.
 *
 * @param title Section title.
 * @param modifier Layout modifier applied to the outer row.
 * @param actionLabel Optional trailing label; ignored when `null` or [onActionClick] is `null`.
 * @param onActionClick Tap handler for the trailing label.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (actionLabel != null && onActionClick != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onActionClick),
            )
        }
    }
}

@Preview(name = "SectionHeader", showBackground = true)
@Composable
private fun SectionHeaderPreview(
    @PreviewParameter(DarkModeProvider::class) darkTheme: Boolean,
) {
    PreviewSurface(darkTheme = darkTheme) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionHeader(title = "Transactions", actionLabel = "See All", onActionClick = {})
            SectionHeader(title = "Monthly Payment")
        }
    }
}
