package com.umain.fortress.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.umain.fortress.ui.icons.FortressIcons
import com.umain.fortress.ui.theme.FortressTheme

/** Top-level destinations shown in [BottomTabBar]. Order is also visual order. */
enum class FortressTab(val icon: ImageVector, val label: String) {
    Home(FortressIcons.Home, "Home"),
    Cards(FortressIcons.Cards, "Cards"),
    Scan(FortressIcons.Scan, "Scan"),
    Analytics(FortressIcons.Analytics, "Analytics"),
    Profile(FortressIcons.Profile, "Profile"),
}

/**
 * Bottom tab bar — pill-shaped dark surface with 5 tabs. The currently-selected tab
 * (and the centred Scan tab when active) gets a lavender accent circle.
 *
 * Reference: Vault dashboard mock — ink-black pill with the centre tab raised in lavender.
 */
@Composable
fun BottomTabBar(
    selected: FortressTab,
    onTabSelected: (FortressTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        shape = MaterialTheme.shapes.extraLarge,
        color = FortressTheme.colors.cardInk,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FortressTab.entries.forEach { tab ->
                TabSlot(
                    tab = tab,
                    isSelected = tab == selected,
                    onClick = { onTabSelected(tab) },
                )
            }
        }
    }
}

@Composable
private fun TabSlot(tab: FortressTab, isSelected: Boolean, onClick: () -> Unit) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
    val tint = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        FortressTheme.colors.cardInkContent.copy(alpha = 0.72f)
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(bg, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}
