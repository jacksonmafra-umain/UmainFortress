package com.umain.fortress.ui.screens.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umain.fortress.network.dto.TransferChallengeSummary
import com.umain.fortress.ui.components.FortressTextField
import com.umain.fortress.ui.components.PrimaryButton
import com.umain.fortress.ui.components.SecondaryButton
import com.umain.fortress.ui.format.formatMinorUnits
import com.umain.fortress.ui.theme.MoneyLarge
import com.umain.fortress.ui.theme.MoneySmall
import com.umain.fortress.ui.theme.MonoCaption
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TransferScreen(
    sourceAccountId: String,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: TransferViewModel = koinViewModel { parametersOf(sourceAccountId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as FragmentActivity
    val scroll = rememberScrollState()

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
                text = when (state.phase) {
                    TransferPhase.Success -> "Done"
                    TransferPhase.Reviewing, TransferPhase.Verifying -> "Review transfer"
                    else -> "Send money"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.phase) {
                TransferPhase.Editing, TransferPhase.Loading -> EditingForm(
                    state = state,
                    viewModel = viewModel,
                )
                TransferPhase.Reviewing, TransferPhase.Verifying -> ReviewingCard(
                    summary = state.challenge!!.summary,
                    submitting = state.phase == TransferPhase.Verifying,
                    onEdit = viewModel::backToEditing,
                    onConfirm = { viewModel.confirm(activity) },
                )
                TransferPhase.Success -> SuccessCard(
                    summary = state.challenge!!.summary,
                    transactionId = state.result!!.transactionId,
                    onDone = onDone,
                )
            }

            state.errorMessage?.let {
                if (state.phase != TransferPhase.Success) {
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

@Composable
private fun EditingForm(state: TransferUiState, viewModel: TransferViewModel) {
    val source = state.sourceAccount
    if (source != null) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("From", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(source.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${source.maskedNumber}  ·  ${formatMinorUnits(source.balanceMinorUnits, source.currency)}",
                    style = MonoCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    FortressTextField(
        value = state.recipientName,
        onValueChange = viewModel::onRecipientNameChange,
        label = "Recipient name",
        enabled = state.phase == TransferPhase.Editing,
    )
    FortressTextField(
        value = state.recipientIban,
        onValueChange = viewModel::onRecipientIbanChange,
        label = "Recipient IBAN",
        enabled = state.phase == TransferPhase.Editing,
    )
    FortressTextField(
        value = state.amountInput,
        onValueChange = viewModel::onAmountChange,
        label = "Amount (${state.currency})",
        keyboardType = KeyboardType.Decimal,
        enabled = state.phase == TransferPhase.Editing,
    )
    FortressTextField(
        value = state.memo,
        onValueChange = viewModel::onMemoChange,
        label = "Memo (optional)",
        enabled = state.phase == TransferPhase.Editing,
    )

    PrimaryButton(
        text = "Review",
        onClick = viewModel::review,
        loading = state.phase == TransferPhase.Loading,
        enabled = state.sourceAccount != null,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ReviewingCard(
    summary: TransferChallengeSummary,
    submitting: Boolean,
    onEdit: () -> Unit,
    onConfirm: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("You're about to send", style = MaterialTheme.typography.labelMedium)
            Text(
                text = formatMinorUnits(summary.amountMinorUnits, summary.currency),
                style = MoneyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ReviewRow("From", "${summary.sourceAccountDisplayName} (${summary.sourceMaskedNumber})")
            ReviewRow("To", summary.recipientName)
            ReviewRow("IBAN", summary.recipientIban)
            summary.memo?.let { ReviewRow("Memo", it) }
        }
    }

    PrimaryButton(
        text = if (submitting) "Verifying…" else "Confirm with biometric",
        onClick = onConfirm,
        loading = submitting,
        modifier = Modifier.fillMaxWidth(),
    )
    SecondaryButton(
        text = "Edit",
        onClick = onEdit,
        enabled = !submitting,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MoneySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun SuccessCard(
    summary: TransferChallengeSummary,
    transactionId: String,
    onDone: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = "Sent",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = formatMinorUnits(summary.amountMinorUnits, summary.currency),
                style = MoneyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "to ${summary.recipientName}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = transactionId,
                style = MonoCaption,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
    PrimaryButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
}
