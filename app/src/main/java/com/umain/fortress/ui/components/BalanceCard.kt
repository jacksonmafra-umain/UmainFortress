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
import androidx.compose.ui.unit.dp
import com.umain.fortress.network.dto.AccountDto
import com.umain.fortress.ui.icons.FortressIcons

/**
 * Flat balance card matching the "Vault" dashboard reference. Sits directly on the page
 * background (no surface fill), uses the paired [MoneyText] display style, and ships a
 * built-in eye toggle to redact the digits.
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
