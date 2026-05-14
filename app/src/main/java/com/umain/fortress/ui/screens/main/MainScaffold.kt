package com.umain.fortress.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.umain.fortress.ui.components.BottomTabBar
import com.umain.fortress.ui.components.FortressTab
import com.umain.fortress.ui.screens.analytics.AnalyticsScreen
import com.umain.fortress.ui.screens.cards.CardsScreen
import com.umain.fortress.ui.screens.dashboard.DashboardScreen
import com.umain.fortress.ui.screens.profile.ProfileScreen
import com.umain.fortress.ui.screens.scan.ScanQrScreen

/**
 * Post-auth scaffold. Hosts the 5-tab bottom navigation (Home, Cards, Scan, Analytics,
 * Profile) and renders the active tab's content. Deep navigations (account detail,
 * full-fat Transfer flow, card reveal) are routed through the outer [com.umain.fortress.
 * ui.navigation.FortressNavGraph] via the supplied callbacks.
 */
@Composable
fun MainScaffold(
    onSignOut: () -> Unit,
    onAccountsClick: () -> Unit,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onSecurityCenter: () -> Unit,
    onDevMode: () -> Unit = {},
) {
    var selectedTab by remember { mutableStateOf(FortressTab.Home) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            BottomTabBar(selected = selectedTab, onTabSelected = { selectedTab = it })
        },
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(bottom = inner.calculateBottomPadding()),
        ) {
            when (selectedTab) {
                FortressTab.Home -> DashboardScreen(
                    onSignOut = onSignOut,
                    onAccountsClick = onAccountsClick,
                    onCardsClick = { selectedTab = FortressTab.Cards },
                    onSendClick = onSendClick,
                    onReceiveClick = onReceiveClick,
                    onTransactionsAllClick = onAccountsClick,
                )
                FortressTab.Cards -> CardsScreen(onBack = { selectedTab = FortressTab.Home })
                FortressTab.Scan -> ScanQrScreen(
                    onBack = { selectedTab = FortressTab.Home },
                    onScanned = { _ -> selectedTab = FortressTab.Home },
                )
                FortressTab.Analytics -> AnalyticsScreen()
                FortressTab.Profile -> ProfileScreen(
                    onSignOut = onSignOut,
                    onSecurityCenter = onSecurityCenter,
                    onDevMode = onDevMode,
                )
            }
        }
    }
}
