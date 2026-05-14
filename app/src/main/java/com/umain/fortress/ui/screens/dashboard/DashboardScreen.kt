package com.umain.fortress.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umain.fortress.ui.components.BalanceCard
import com.umain.fortress.ui.components.CardCarousel
import com.umain.fortress.ui.components.QuickActionPill
import com.umain.fortress.ui.components.QuickActionSquare
import com.umain.fortress.ui.components.SectionHeader
import com.umain.fortress.ui.components.SecurityChip
import com.umain.fortress.ui.components.TransactionRow
import com.umain.fortress.ui.icons.FortressIcons
import com.umain.fortress.ui.screens.cards.CardsViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * Home tab content. Lays out, top-down:
 *   - Avatar + "Hi, Jack" greeting, with notifications bell
 *   - Total Balance + MoneyText + eye toggle (via [BalanceCard])
 *   - "My cards" carousel
 *   - Send / Receive / grid quick actions
 *   - Transactions list
 *
 * Reference: "Vault" dashboard mock.
 */
@Composable
fun DashboardScreen(
    onSignOut: () -> Unit,
    onAccountsClick: () -> Unit,
    onCardsClick: () -> Unit,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onTransactionsAllClick: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
    cardsViewModel: CardsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cardsState by cardsViewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()) {

        // --- Header: avatar + greeting + security chip + notifications ---------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(initial = state.snapshot?.primaryAccount?.displayName?.firstOrNull()?.toString() ?: "J")
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Hi, ${state.snapshot?.primaryAccount?.displayName?.substringBefore(' ') ?: "there"}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Welcome",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SecurityChip(verdict = state.integrity)
            IconButton(onClick = onSignOut) {
                Icon(
                    imageVector = FortressIcons.Notifications,
                    contentDescription = "Notifications",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when {
            state.loading && state.snapshot == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.snapshot != null -> {
                val snapshot = state.snapshot!!
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item { BalanceCard(account = snapshot.primaryAccount) }
                    item {
                        SectionHeader(
                            title = "My cards",
                            actionLabel = "See All",
                            onActionClick = onCardsClick,
                        )
                    }
                    item {
                        if (cardsState.cards.isNotEmpty()) {
                            // Bleed slightly outside the parent padding so the strip can scroll
                            // edge-to-edge while the rest of the page stays inset.
                            CardCarousel(
                                cards = cardsState.cards,
                                onCardClick = { onCardsClick() },
                                onAddCardClick = { onCardsClick() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = (-20).dp),
                            )
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            QuickActionPill(
                                icon = FortressIcons.Send,
                                label = "Send",
                                onClick = onSendClick,
                                modifier = Modifier.weight(1f),
                            )
                            QuickActionPill(
                                icon = FortressIcons.Receive,
                                label = "Receive",
                                onClick = onReceiveClick,
                                modifier = Modifier.weight(1f),
                            )
                            QuickActionSquare(
                                icon = FortressIcons.Grid,
                                contentDescription = "All accounts",
                                onClick = onAccountsClick,
                            )
                        }
                    }
                    item {
                        SectionHeader(
                            title = "Transactions",
                            actionLabel = "See All",
                            onActionClick = onTransactionsAllClick,
                        )
                    }
                    items(snapshot.recentTransactions, key = { it.id }) { tx ->
                        TransactionRow(transaction = tx)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            state.errorMessage != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Couldn't load your dashboard",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = state.errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Avatar(initial: String) {
    Surface(
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Text(
                text = initial.uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
