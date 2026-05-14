package com.umain.fortress.ui.screens.securitycenter

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umain.fortress.network.dto.ActiveSessionDto
import com.umain.fortress.network.dto.TrustedDeviceDto
import com.umain.fortress.security.IntegrityVerdict
import com.umain.fortress.ui.components.SecondaryButton
import com.umain.fortress.ui.icons.FortressIcons
import com.umain.fortress.ui.theme.FortressTheme
import com.umain.fortress.ui.theme.MonoCaption
import org.koin.androidx.compose.koinViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun SecurityCenterScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SecurityCenterViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
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
                text = "Security center",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        if (state.loading && state.devices.isEmpty() && state.sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item { RiskCard(state.verdict) }

                item { Text("Trusted devices", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground) }
                items(state.devices, key = { it.id }) { device ->
                    DeviceRow(
                        device = device,
                        isCurrent = device.deviceId == state.currentDeviceId,
                        revoking = device.id in state.revokingIds,
                        onRevoke = { viewModel.revokeDevice(device) },
                    )
                }

                item { Text("Active sessions", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground) }
                items(state.sessions, key = { it.id }) { session ->
                    SessionRow(session = session, isCurrent = session.deviceId == state.currentDeviceId)
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

                item {
                    SecondaryButton(
                        text = if (state.signingOutAll) "Signing out…" else "Sign out on all devices",
                        onClick = { viewModel.signOutEverywhere(onSignedOut) },
                        enabled = !state.signingOutAll && state.sessions.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun RiskCard(verdict: IntegrityVerdict) {
    val theme = FortressTheme.colors
    val (bg, fg, label, body) = when (verdict) {
        IntegrityVerdict.Trusted -> Quadruple(theme.successSurface, theme.successOn, "Low", "Device looks healthy. All step-up signals pass.")
        is IntegrityVerdict.Limited -> Quadruple(theme.warningSurface, theme.warningOn, "Medium", verdict.reasons.firstOrNull() ?: "Some signals are weakening — sensitive ops will step up.")
        is IntegrityVerdict.Untrusted -> Quadruple(theme.dangerSurface, theme.dangerOn, "High", verdict.reasons.firstOrNull() ?: "Risk signals firing — sensitive ops are blocked.")
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = bg,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(fg.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = FortressIcons.ShieldGood,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Risk score: $label", style = MaterialTheme.typography.titleMedium, color = fg)
                Text(body, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.85f))
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D) {
    operator fun component1() = a
    operator fun component2() = b
    operator fun component3() = c
    operator fun component4() = d
}

@Composable
private fun DeviceRow(
    device: TrustedDeviceDto,
    isCurrent: Boolean,
    revoking: Boolean,
    onRevoke: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = FortressIcons.Fingerprint,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.deviceId.take(13) + "…",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (isCurrent) "This device · enrolled ${formatDate(device.createdAtEpochMs)}"
                    else "Last seen ${formatDate(device.updatedAtEpochMs)}",
                    style = MonoCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isCurrent) {
                CurrentChip()
            } else {
                TextButton(onClick = onRevoke, enabled = !revoking) {
                    Text(
                        text = if (revoking) "…" else "Revoke",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentChip() {
    Surface(
        color = FortressTheme.colors.successSurface,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = "Current",
            style = MaterialTheme.typography.labelSmall,
            color = FortressTheme.colors.successOn,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SessionRow(session: ActiveSessionDto, isCurrent: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = FortressIcons.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.deviceId.take(13) + "…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Issued ${formatDate(session.issuedAtEpochMs)} · expires ${formatDate(session.expiresAtEpochMs)}",
                    style = MonoCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isCurrent) CurrentChip()
        }
    }
}

private fun formatDate(epochMs: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMs))

// Unused-suppress: Color used implicitly through alpha helpers above.
@Suppress("UNUSED_EXPRESSION")
private val SuppressUnused: Color = Color.Unspecified
