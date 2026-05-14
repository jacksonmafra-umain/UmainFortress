package com.umain.fortress.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.umain.fortress.ui.screens.auth.LoginScreen
import com.umain.fortress.ui.screens.biometric.BiometricUnlockScreen
import com.umain.fortress.ui.screens.dashboard.DashboardScreen
import com.umain.fortress.ui.screens.splash.SplashScreen

@Composable
fun FortressNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onGoLogin = { navController.popAndGo(Routes.LOGIN) },
                onGoBiometric = { navController.popAndGo(Routes.BIOMETRIC_UNLOCK) },
                onBlocked = { navController.popAndGo(Routes.BLOCKED) },
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { navController.popAndGo(Routes.DASHBOARD) },
            )
        }
        composable(Routes.BIOMETRIC_UNLOCK) {
            BiometricUnlockScreen(
                onUnlocked = { navController.popAndGo(Routes.DASHBOARD) },
                onSignedOut = { navController.popAndGo(Routes.LOGIN) },
            )
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onSignOut = { navController.popAndGo(Routes.LOGIN) },
            )
        }
        composable(Routes.BLOCKED) { BlockedScreen() }
    }
}

private fun NavHostController.popAndGo(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
private fun BlockedScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Device blocked",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = "This device failed integrity checks. Fortress cannot run here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
