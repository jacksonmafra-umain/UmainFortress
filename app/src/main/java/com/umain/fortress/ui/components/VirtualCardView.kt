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
import com.umain.fortress.ui.theme.Ink950
import com.umain.fortress.ui.theme.InkSurfaceDark
import com.umain.fortress.ui.theme.InkSurfaceElevated
import com.umain.fortress.ui.theme.Lavender500
import com.umain.fortress.ui.theme.Lavender700
import com.umain.fortress.ui.theme.MonoCaption
import com.umain.fortress.ui.theme.Sage500
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.umain.fortress.ui.components.preview.DarkModeProvider
import com.umain.fortress.ui.components.preview.PreviewData
import com.umain.fortress.ui.components.preview.PreviewSurface

/**
 * Full-bleed virtual card view used by the Cards screen and the card-reveal flow.
 *
 * Brand drives the gradient (Mastercard → ink + sage; Amex → ink + cobalt; default → ink +
 * lavender). When the card is frozen, the surface is overlaid with a 45% black mask and a
 * leading ice icon. PAN and CVV reveals are passed in via [overridePan] / [overrideCvv];
 * `null` means show the server-masked value.
 *
 * @param card Card DTO to render.
 * @param modifier Layout modifier applied to the underlying [Surface]. The view picks its
 *                 own 1.6 aspect ratio off the available width.
 * @param overridePan Full PAN string to render in place of the masked value; usually
 *                    available only inside a successful card-reveal flow.
 * @param overrideCvv Full CVV to render alongside the expiry; null hides the column.
 */
@Composable
fun VirtualCardView(
    card: CardDto,
    modifier: Modifier = Modifier,
    overridePan: String? = null,
    overrideCvv: String? = null,
) {
    val brandColors = when (card.brand.lowercase()) {
        "mastercard" -> listOf(InkSurfaceElevated, Sage500.copy(alpha = 0.55f), Ink950)
        "amex" -> listOf(InkSurfaceElevated, Color(0xFF1F6FEB), Ink950)
        else -> listOf(Lavender700, Lavender500.copy(alpha = 0.8f), InkSurfaceDark)
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
                        style = MaterialTheme.typography.titleLarge,
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
    }
}

@Preview(name = "VirtualCardView default", showBackground = true)
@Composable
private fun VirtualCardViewDefaultPreview(
    @PreviewParameter(DarkModeProvider::class) darkTheme: Boolean,
) {
    PreviewSurface(darkTheme = darkTheme) {
        VirtualCardView(card = PreviewData.visaCard)
    }
}

@Preview(name = "VirtualCardView frozen", showBackground = true)
@Composable
private fun VirtualCardViewFrozenPreview() {
    PreviewSurface {
        VirtualCardView(card = PreviewData.masterCard.copy(frozen = true))
    }
}

@Preview(name = "VirtualCardView revealed", showBackground = true)
@Composable
private fun VirtualCardViewRevealedPreview() {
    PreviewSurface {
        VirtualCardView(
            card = PreviewData.visaCard,
            overridePan = "4929 8800 1234 4455",
            overrideCvv = "123",
        )
    }
}
