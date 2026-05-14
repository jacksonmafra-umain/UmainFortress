package com.umain.fortress.ui.screens.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umain.fortress.network.dto.CardDto
import com.umain.fortress.network.dto.CardRevealResponse
import com.umain.fortress.ui.components.PrimaryButton
import com.umain.fortress.ui.components.SecondaryButton
import com.umain.fortress.ui.components.VirtualCardView
import com.umain.fortress.ui.icons.FortressIcons
import org.koin.androidx.compose.koinViewModel

/**
 * Cards tab destination — lists every card on file with reveal / freeze affordances.
 *
 * Surfaces four distinct states so the page is never blank: loading spinner, populated
 * list, no-cards empty state with an "Add card" CTA, and an error state with retry.
 *
 * @param onBack Pops the screen back to its parent (Home tab) on the back chevron.
 * @param onAddCard Opens the Add Card flow from the empty state and the trailing
 *                  Add Card row at the bottom of the populated list.
 * @param viewModel Cards data source — injected via Koin in production, swappable in tests.
 */
@Composable
fun CardsScreen(
    onBack: () -> Unit,
    onAddCard: () -> Unit = {},
    viewModel: CardsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as FragmentActivity

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = FortressIcons.Back,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = "Your cards",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        when {
            state.loading && state.cards.isEmpty() -> LoadingState()

            state.cards.isEmpty() && state.errorMessage != null -> ErrorState(
                message = state.errorMessage!!,
                onRetry = viewModel::load,
            )

            state.cards.isEmpty() -> EmptyState(onAddCard = onAddCard)

            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(state.cards, key = { it.id }) { card ->
                    CardItem(
                        card = card,
                        revealed = state.revealed[card.id],
                        toggling = card.id in state.togglingIds,
                        revealing = card.id in state.revealingIds,
                        onToggleFreeze = { viewModel.toggleFreeze(card) },
                        onReveal = { viewModel.reveal(activity, card) },
                        onHide = { viewModel.hide(card.id) },
                    )
                }
                state.errorMessage?.let { msg ->
                    item {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                item {
                    PrimaryButton(
                        text = "Add another card",
                        onClick = onAddCard,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyState(onAddCard: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = FortressIcons.Card,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            )
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(24.dp))
        Text(
            text = "No cards yet",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = "When a card is linked to your Fortress account it'll show up here. Add your first one to get started.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(20.dp))
        PrimaryButton(
            text = "Add card",
            onClick = onAddCard,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = FortressIcons.ShieldBad,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(44.dp),
            )
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(24.dp))
        Text(
            text = "Couldn't load your cards",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(20.dp))
        PrimaryButton(
            text = "Try again",
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CardItem(
    card: CardDto,
    revealed: CardRevealResponse?,
    toggling: Boolean,
    revealing: Boolean,
    onToggleFreeze: () -> Unit,
    onReveal: () -> Unit,
    onHide: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VirtualCardView(
            card = card,
            overridePan = revealed?.panFull,
            overrideCvv = revealed?.cvvFull,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SecondaryButton(
                text = when {
                    toggling -> "…"
                    card.frozen -> "Unfreeze"
                    else -> "Freeze"
                },
                onClick = onToggleFreeze,
                enabled = !toggling,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = when {
                    revealing -> "Verifying…"
                    revealed != null -> "Hide"
                    else -> "Reveal"
                },
                onClick = if (revealed != null) onHide else onReveal,
                loading = revealing,
                enabled = !card.frozen || revealed != null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
