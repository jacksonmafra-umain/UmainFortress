package com.umain.fortress.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.umain.fortress.network.dto.CardDto
import com.umain.fortress.ui.theme.Emerald500
import com.umain.fortress.ui.theme.Midnight700
import com.umain.fortress.ui.theme.Midnight800
import com.umain.fortress.ui.theme.Midnight900
import com.umain.fortress.ui.theme.MoneyMedium
import com.umain.fortress.ui.theme.MonoCaption
import com.umain.fortress.ui.theme.Violet500

/**
 * Visual representation of a virtual card. Brand drives the gradient; frozen state overlays a
 * desaturated mask with an ice icon. PAN reveals are passed in via [overridePan] — null means
 * show the server-masked value.
 */
@Composable
fun VirtualCardView(
    card: CardDto,
    modifier: Modifier = Modifier,
    overridePan: String? = null,
    overrideCvv: String? = null,
) {
    val brandColors = when (card.brand.lowercase()) {
        "mastercard" -> listOf(Midnight800, Emerald500.copy(alpha = 0.55f), Midnight900)
        "amex" -> listOf(Midnight800, Color(0xFF1F6FEB), Midnight900)
        else -> listOf(Midnight800, Violet500.copy(alpha = 0.6f), Midnight900)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.6f),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(brandColors)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(PaddingValues(horizontal = 22.dp, vertical = 20.dp)),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Fortress · ${card.variant.replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                    Text(
                        text = card.brand.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = overridePan ?: card.panMasked,
                        style = MoneyMedium,
                        color = Color.White,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = "CARDHOLDER",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                            Text(
                                text = card.holderName,
                                style = MonoCaption,
                                color = Color.White,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "EXPIRES",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                            Text(
                                text = "%02d/%02d".format(card.expMonth, card.expYear % 100),
                                style = MonoCaption,
                                color = Color.White,
                            )
                        }
                        overrideCvv?.let {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "CVV",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                                Text(
                                    text = it,
                                    style = MonoCaption,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
            if (card.frozen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.AcUnit,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = "Frozen",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                    }
                }
            }
        }
        // Suppress unused theme palette imports referenced via gradient builder above
        @Suppress("UNUSED_EXPRESSION") Midnight700
    }
}
