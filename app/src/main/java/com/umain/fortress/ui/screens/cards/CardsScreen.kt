package com.umain.fortress.ui.screens.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import org.koin.androidx.compose.koinViewModel

@Composable
fun CardsScreen(
    onBack: () -> Unit,
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
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
            state.loading && state.cards.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.cards.isNotEmpty() -> {
                LazyColumn(
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
                    state.errorMessage?.let {
                        item {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
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
