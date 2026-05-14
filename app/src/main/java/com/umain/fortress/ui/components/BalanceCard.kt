package com.umain.fortress.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.umain.fortress.network.dto.AccountDto
import com.umain.fortress.ui.format.formatMinorUnits
import com.umain.fortress.ui.theme.Midnight700
import com.umain.fortress.ui.theme.Midnight800
import com.umain.fortress.ui.theme.MoneyLarge
import com.umain.fortress.ui.theme.MonoCaption
import com.umain.fortress.ui.theme.Violet500

@Composable
fun BalanceCard(
    account: AccountDto,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(Midnight800, Midnight700, Violet500.copy(alpha = 0.55f)),
                    ),
                )
                .padding(PaddingValues(horizontal = 24.dp, vertical = 28.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = account.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
            )
            Text(
                text = formatMinorUnits(account.balanceMinorUnits, account.currency),
                style = MoneyLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = account.maskedNumber,
                style = MonoCaption,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
            )
        }
    }
}
