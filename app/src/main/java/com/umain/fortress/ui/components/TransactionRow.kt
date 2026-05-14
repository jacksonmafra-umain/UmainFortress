package com.umain.fortress.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.umain.fortress.network.dto.TransactionDto
import com.umain.fortress.ui.components.preview.DarkModeProvider
import com.umain.fortress.ui.components.preview.PreviewData
import com.umain.fortress.ui.components.preview.PreviewSurface
import com.umain.fortress.ui.icons.FortressIcons
import com.umain.fortress.ui.theme.FortressTheme

/**
 * Single transaction line item used by the Dashboard "Transactions" section.
 *
 * Renders a leading 40dp directional icon (Receive for credit, Send for debit), the
 * description and counterparty stacked in the middle, and the [MoneyText] amount on the
 * trailing edge — green for credit, on-surface ink for debit, with an optional risk badge
 * underneath when the transaction is rated medium or high.
 *
 * @param transaction Transaction DTO to render.
 * @param modifier Layout modifier applied to the row.
 */
@Composable
fun TransactionRow(
    transaction: TransactionDto,
    modifier: Modifier = Modifier,
) {
    val isCredit = transaction.amountMinorUnits >= 0
    val amountColor =
        if (isCredit) FortressTheme.colors.successOn else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isCredit) FortressIcons.Receive else FortressIcons.Send,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = transaction.counterparty,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(
                minorUnits = transaction.amountMinorUnits,
                currencyCode = transaction.currency,
                size = MoneySize.Small,
                useSymbol = true,
                colorOverride = amountColor,
            )
            RiskBadge(transaction.riskLevel)
        }
    }
}

@Composable
private fun RiskBadge(level: String) {
    val (label, color) = when (level.lowercase()) {
        "high" -> "High risk" to FortressTheme.colors.dangerOn
        "medium" -> "Med risk" to FortressTheme.colors.warningOn
        else -> return
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

@Preview(name = "TransactionRow catalogue", showBackground = true)
@Composable
private fun TransactionRowPreview(
    @PreviewParameter(DarkModeProvider::class) darkTheme: Boolean,
) {
    PreviewSurface(darkTheme = darkTheme) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TransactionRow(transaction = PreviewData.debitTransaction)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TransactionRow(transaction = PreviewData.creditTransaction)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TransactionRow(transaction = PreviewData.riskyTransaction)
        }
    }
}
