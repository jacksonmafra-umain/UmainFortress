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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.umain.fortress.ui.screens.accountdetail.AccountDetailScreen
import com.umain.fortress.ui.screens.accounts.AccountsScreen
import com.umain.fortress.ui.screens.auth.LoginScreen
import com.umain.fortress.ui.screens.biometric.BiometricUnlockScreen
import com.umain.fortress.ui.screens.cards.CardsScreen
import com.umain.fortress.ui.screens.devmode.DevModeScreen
import com.umain.fortress.ui.screens.main.MainScaffold
import com.umain.fortress.ui.screens.onboarding.OnboardingScreen
import com.umain.fortress.ui.screens.securitycenter.SecurityCenterScreen
import com.umain.fortress.ui.screens.splash.SplashScreen
import com.umain.fortress.ui.screens.transfer.TransferScreen
import com.umain.fortress.ui.screens.transferquick.TransferKeypadScreen

@Composable
fun FortressNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onGoOnboarding = { navController.popAndGo(Routes.ONBOARDING) },
                onGoLogin = { navController.popAndGo(Routes.LOGIN) },
                onGoBiometric = { navController.popAndGo(Routes.BIOMETRIC_UNLOCK) },
                onBlocked = { navController.popAndGo(Routes.BLOCKED) },
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = { navController.popAndGo(Routes.LOGIN) },
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { navController.popAndGo(Routes.MAIN) },
            )
        }
        composable(Routes.BIOMETRIC_UNLOCK) {
            BiometricUnlockScreen(
                onUnlocked = { navController.popAndGo(Routes.MAIN) },
                onSignedOut = { navController.popAndGo(Routes.LOGIN) },
            )
        }

        // --- Post-auth: tabbed shell ------------------------------------------------
        composable(Routes.MAIN) {
            MainScaffold(
                onSignOut = { navController.popAndGo(Routes.LOGIN) },
                onAccountsClick = { navController.navigate(Routes.ACCOUNTS) },
                onSendClick = { navController.navigate(Routes.TRANSFER_QUICK) },
                onReceiveClick = { navController.navigate(Routes.TRANSFER_QUICK) },
                onSecurityCenter = { navController.navigate(Routes.SECURITY_CENTER) },
                onDevMode = { navController.navigate(Routes.DEV_MODE) },
            )
        }

        // --- Deep navigations -------------------------------------------------------
        composable(Routes.ACCOUNTS) {
            AccountsScreen(
                onAccountClick = { account ->
                    navController.navigate("${Routes.ACCOUNT_DETAIL}/${account.id}")
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "${Routes.ACCOUNT_DETAIL}/{accountId}",
            arguments = listOf(navArgument("accountId") { type = NavType.StringType }),
        ) { entry ->
            val accountId = entry.arguments?.getString("accountId") ?: return@composable
            AccountDetailScreen(
                accountId = accountId,
                onBack = { navController.popBackStack() },
                onTransferClick = { sourceId ->
                    navController.navigate("${Routes.TRANSFER}/$sourceId")
                },
            )
        }
        composable(
            route = "${Routes.TRANSFER}/{accountId}",
            arguments = listOf(navArgument("accountId") { type = NavType.StringType }),
        ) { entry ->
            val accountId = entry.arguments?.getString("accountId") ?: return@composable
            TransferScreen(
                sourceAccountId = accountId,
                onDone = { navController.popBackStack(Routes.MAIN, inclusive = false) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.TRANSFER_QUICK) {
            TransferKeypadScreen(
                onContinue = { _ ->
                    // Quick-keypad amounts flow into the account-bound transfer review screen
                    // when an account is selected upstream. For now, bounce back to Main.
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.CARDS) {
            CardsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SECURITY_CENTER) {
            SecurityCenterScreen(
                onBack = { navController.popBackStack() },
                onSignedOut = { navController.popAndGo(Routes.LOGIN) },
            )
        }
        composable(Routes.DEV_MODE) {
            DevModeScreen(onBack = { navController.popBackStack() })
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
