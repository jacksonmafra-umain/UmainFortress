package com.umain.fortress.ui.screens.addcard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umain.fortress.ui.components.FortressTextField
import com.umain.fortress.ui.components.PrimaryButton
import com.umain.fortress.ui.components.SecondaryButton
import com.umain.fortress.ui.icons.FortressIcons
import com.umain.fortress.ui.theme.FortressPillShape
import com.umain.fortress.ui.theme.FortressTheme
import org.koin.androidx.compose.koinViewModel

/**
 * Add Card screen.
 *
 * Cardholder name, brand picker, variant picker, last four digits, expiry month/year.
 * Posts to `/me/cards`; on success the parent navigation pops back to the Cards screen
 * which then re-loads the list.
 *
 * @param onBack Pops the screen.
 * @param onCreated Invoked after the backend returns 201; the typical wiring also pops
 *                  navigation back to the cards list.
 * @param viewModel Form view-model — Koin-injected in production.
 */
@Composable
fun AddCardScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: AddCardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.created) {
        if (state.created != null) onCreated()
    }

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
                text = "Add card",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { HeroCard() }
            item { SectionLabel("Brand") }
            item { BrandPicker(selected = state.brand, onSelect = viewModel::onBrand) }
            item { SectionLabel("Variant") }
            item { VariantPicker(selected = state.variant, onSelect = viewModel::onVariant) }

            item {
                FortressTextField(
                    value = state.holderName,
                    onValueChange = viewModel::onHolderName,
                    label = "Cardholder name",
                    enabled = !state.submitting,
                )
            }
            item {
                FortressTextField(
                    value = state.last4,
                    onValueChange = viewModel::onLast4,
                    label = "Last 4 digits",
                    keyboardType = KeyboardType.Number,
                    enabled = !state.submitting,
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FortressTextField(
                        value = state.expMonth,
                        onValueChange = viewModel::onExpMonth,
                        label = "MM",
                        keyboardType = KeyboardType.Number,
                        enabled = !state.submitting,
                        modifier = Modifier.weight(1f),
                    )
                    FortressTextField(
                        value = state.expYear,
                        onValueChange = viewModel::onExpYear,
                        label = "YYYY",
                        keyboardType = KeyboardType.Number,
                        enabled = !state.submitting,
                        modifier = Modifier.weight(1.5f),
                    )
                }
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
                    text = if (state.submitting) "Linking…" else "Link card",
                    onClick = { viewModel.submit(onCreated) },
                    enabled = state.isValid,
                    loading = state.submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SecondaryButton(
                    text = "Cancel",
                    onClick = onBack,
                    enabled = !state.submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun HeroCard() {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = FortressTheme.colors.cardInk,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = FortressIcons.Card,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Link a new card",
                    style = MaterialTheme.typography.titleLarge,
                    color = FortressTheme.colors.cardInkContent,
                )
                Text(
                    text = "We never store the full PAN locally. Only the last four digits live on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FortressTheme.colors.cardInkContent.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BrandPicker(selected: CardBrand, onSelect: (CardBrand) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CardBrand.entries.forEach { brand ->
            ChoiceChip(
                label = brand.label,
                selected = brand == selected,
                onClick = { onSelect(brand) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun VariantPicker(selected: CardVariant, onSelect: (CardVariant) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CardVariant.entries.forEach { variant ->
            ChoiceChip(
                label = variant.label,
                selected = variant == selected,
                onClick = { onSelect(variant) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = FortressPillShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
