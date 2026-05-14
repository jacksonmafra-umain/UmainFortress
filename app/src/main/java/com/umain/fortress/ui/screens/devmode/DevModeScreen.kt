package com.umain.fortress.ui.screens.devmode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.umain.fortress.BuildConfig
import com.umain.fortress.ui.components.FortressSwitch
import com.umain.fortress.ui.components.SecondaryButton
import com.umain.fortress.ui.icons.FortressIcons
import com.umain.fortress.ui.theme.FortressTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun DevModeScreen(
    onBack: () -> Unit,
    viewModel: DevModeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
                text = "Dev Mode",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        if (!BuildConfig.ALLOW_DEV_MODE) {
            ProductionGuard()
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Banner() }
            item {
                ToggleRow(
                    title = "Simulate root / Magisk",
                    body = "Marks the device as compromised. IntegrityCheck returns Untrusted; SecurityChip turns red; sensitive ops should refuse.",
                    checked = state.simulateRoot,
                    onCheckedChange = viewModel::setSimulateRoot,
                )
            }
            item {
                ToggleRow(
                    title = "Simulate MITM proxy",
                    body = "Pretends a hostile proxy is on the wire. Verdict goes Limited; pin failures are surfaced in telemetry.",
                    checked = state.simulateMitm,
                    onCheckedChange = viewModel::setSimulateMitm,
                )
            }
            item {
                ToggleRow(
                    title = "Simulate replayed challenge",
                    body = "Pretends the last step-up nonce was reused. Server rejects the verify call with CHALLENGE_REJECTED.",
                    checked = state.simulateReplay,
                    onCheckedChange = viewModel::setSimulateReplay,
                )
            }
            item {
                ToggleRow(
                    title = "Simulate Play Integrity fail",
                    body = "Forces the integrity verdict to Untrusted with a Play Integrity reason. Hard-blocks step-up flows.",
                    checked = state.simulateIntegrityFail,
                    onCheckedChange = viewModel::setSimulateIntegrityFail,
                )
            }
            item {
                SecondaryButton(
                    text = "Reset all toggles",
                    onClick = viewModel::reset,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Banner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = FortressTheme.colors.warningSurface,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Dev Mode",
                style = MaterialTheme.typography.titleMedium,
                color = FortressTheme.colors.warningOn,
            )
            Text(
                text = "Toggles simulate attack scenarios so each defence is visible in the running app. Release builds (BuildConfig.ALLOW_DEV_MODE=false) ignore everything below.",
                style = MaterialTheme.typography.bodySmall,
                color = FortressTheme.colors.warningOn,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FortressSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun ProductionGuard() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Dev Mode is disabled in release builds.",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "BuildConfig.ALLOW_DEV_MODE is false. The IntegrityCheck stub ignores the toggles and always returns Trusted.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
