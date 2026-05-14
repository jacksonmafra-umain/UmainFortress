package com.umain.fortress.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.umain.fortress.network.dto.CardDto
import com.umain.fortress.ui.components.preview.DarkModeProvider
import com.umain.fortress.ui.components.preview.PreviewData
import com.umain.fortress.ui.components.preview.PreviewSurface
import com.umain.fortress.ui.icons.FortressIcons
import com.umain.fortress.ui.theme.FortressTheme
import com.umain.fortress.ui.theme.Lavender200
import com.umain.fortress.ui.theme.Lavender500

/**
 * Horizontal carousel of mini card swatches used on the Dashboard's "My cards" strip.
 *
 * Each [CardDto] is rendered as a 168×104dp gradient swatch carrying the brand stamp,
 * masked PAN, and holder name. A trailing 64×104dp pastel slot acts as the "Add Card"
 * affordance.
 *
 * @param cards Cards to show, in display order.
 * @param onCardClick Callback fired when the user taps a card swatch.
 * @param onAddCardClick Callback fired when the user taps the trailing "+" slot.
 * @param modifier Layout modifier applied to the underlying [LazyRow].
 */
@Composable
fun CardCarousel(
    cards: List<CardDto>,
    onCardClick: (CardDto) -> Unit,
    onAddCardClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(cards.size) { i ->
            val card = cards[i]
            MiniCardSwatch(card = card, onClick = { onCardClick(card) })
        }
        items(1) { _ ->
            AddCardSwatch(onClick = onAddCardClick)
        }
    }
}

@Composable
private fun MiniCardSwatch(card: CardDto, onClick: () -> Unit) {
    val gradient = when (card.brand.lowercase()) {
        "mastercard" -> listOf(FortressTheme.colors.cardInk, Color(0xFF2C2742))
        "amex" -> listOf(Color(0xFF1F2A4A), Color(0xFF101626))
        else -> listOf(Lavender500, Color(0xFFB39AFF))
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        modifier = Modifier
            .size(width = 168.dp, height = 104.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(gradient), MaterialTheme.shapes.large)
                .padding(14.dp),
        ) {
            Text(
                text = card.brand.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.TopEnd),
            )
            Column(
                modifier = Modifier.align(Alignment.BottomStart),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "•••• ${card.panMasked.takeLast(4)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
                Text(
                    text = card.holderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun AddCardSwatch(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 104.dp)
            .background(Lavender200.copy(alpha = 0.35f), MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = FortressIcons.Grid,
                contentDescription = "Add card",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Preview(name = "CardCarousel", showBackground = true)
@Composable
private fun CardCarouselPreview(
    @PreviewParameter(DarkModeProvider::class) darkTheme: Boolean,
) {
    PreviewSurface(darkTheme = darkTheme) {
        CardCarousel(
            cards = listOf(PreviewData.visaCard, PreviewData.masterCard),
            onCardClick = {},
            onAddCardClick = {},
        )
    }
}
