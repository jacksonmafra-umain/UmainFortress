package com.umain.fortress.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.umain.fortress.network.dto.AccountDto
import com.umain.fortress.ui.components.preview.DarkModeProvider
import com.umain.fortress.ui.components.preview.PreviewData
import com.umain.fortress.ui.components.preview.PreviewSurface
import com.umain.fortress.ui.icons.FortressIcons

/**
 * Total-balance display used at the top of the Dashboard.
 *
 * Sits directly on the page background — no surface fill, no elevation — and lets colour
 * + typography carry the hierarchy. Owns its own redact state: tapping the eye icon flips
 * the digits to bullet glyphs while preserving the sign and currency symbol.
 *
 * @param account Account whose balance, display name and masked number should be shown.
 * @param modifier Layout modifier applied to the outer column.
 */
@Composable
fun BalanceCard(
    account: AccountDto,
    modifier: Modifier = Modifier,
) {
    var hidden by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Total Balance",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MoneyText(
                minorUnits = account.balanceMinorUnits,
                currencyCode = account.currency,
                size = MoneySize.Display,
                hidden = hidden,
                useSymbol = true,
                colorOverride = MaterialTheme.colorScheme.onBackground,
            )
            Icon(
                imageVector = if (hidden) FortressIcons.EyeOff else FortressIcons.Eye,
                contentDescription = if (hidden) "Show balance" else "Hide balance",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .clickable { hidden = !hidden },
            )
        }
        Text(
            text = "${account.displayName} · ${account.maskedNumber}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(name = "BalanceCard", showBackground = true)
@Composable
private fun BalanceCardPreview(
    @PreviewParameter(DarkModeProvider::class) darkTheme: Boolean,
) {
    PreviewSurface(darkTheme = darkTheme) {
        BalanceCard(account = PreviewData.primaryAccount)
    }
}
