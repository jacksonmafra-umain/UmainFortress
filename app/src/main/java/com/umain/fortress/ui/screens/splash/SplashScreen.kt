package com.umain.fortress.ui.screens.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umain.fortress.ui.theme.Midnight700
import com.umain.fortress.ui.theme.Midnight800
import com.umain.fortress.ui.theme.Midnight900
import com.umain.fortress.ui.theme.Violet500
import org.koin.androidx.compose.koinViewModel

@Composable
fun SplashScreen(
    onGoLogin: () -> Unit,
    onGoBiometric: () -> Unit,
    onBlocked: (List<String>) -> Unit,
    viewModel: SplashViewModel = koinViewModel(),
) {
    val decision by viewModel.decision.collectAsStateWithLifecycle()

    LaunchedEffect(decision) {
        when (val d = decision) {
            SplashDecision.Loading -> Unit
            SplashDecision.GoLogin -> onGoLogin()
            SplashDecision.GoBiometric -> onGoBiometric()
            is SplashDecision.Blocked -> onBlocked(d.reasons)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Midnight900, Midnight800, Violet500.copy(alpha = 0.6f))),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = "Fortress",
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = "verifying device integrity…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
            )
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp),
            )
        }
        // Suppress unused-import warnings for theme palette in this composable.
        @Suppress("UNUSED_EXPRESSION") Midnight700
    }
}
