package com.umain.fortress.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.umain.fortress.network.dto.AccountDto
import com.umain.fortress.ui.components.preview.DarkModeProvider
import com.umain.fortress.ui.components.preview.PreviewData
import com.umain.fortress.ui.components.preview.PreviewSurface
import com.umain.fortress.ui.icons.FortressIcons
import com.umain.fortress.ui.theme.MonoCaption

/**
 * Tappable row representing a single account in the Accounts list. Pairs an account-type
 * icon (savings / investment / generic card) with the display name + masked number and a
 * trailing [MoneyText] balance.
 *
 * @param account Account DTO to render.
 * @param onClick Tap handler — receives the same [account] back.
 * @param modifier Layout modifier applied to the row [Surface].
 */
@Composable
fun AccountListRow(
    account: AccountDto,
    onClick: (AccountDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(account) },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AccountIcon(account.type)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = account.maskedNumber,
                    style = MonoCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MoneyText(
                minorUnits = account.balanceMinorUnits,
                currencyCode = account.currency,
                size = MoneySize.Medium,
                useSymbol = true,
                colorOverride = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AccountIcon(type: String) {
    val icon: ImageVector = when (type.lowercase()) {
        "savings" -> FortressIcons.Savings
        "investment" -> FortressIcons.Investment
        else -> FortressIcons.Cards
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Preview(name = "AccountListRow catalogue", showBackground = true)
@Composable
private fun AccountListRowPreview(
    @PreviewParameter(DarkModeProvider::class) darkTheme: Boolean,
) {
    PreviewSurface(darkTheme = darkTheme) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountListRow(account = PreviewData.primaryAccount, onClick = {})
            AccountListRow(account = PreviewData.savingsAccount, onClick = {})
        }
    }
}
