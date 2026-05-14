package com.umain.fortress.ui.screens.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.umain.fortress.ui.components.MoneySize
import com.umain.fortress.ui.components.MoneyText
import com.umain.fortress.ui.components.SectionHeader
import com.umain.fortress.ui.theme.FortressPillShape
import com.umain.fortress.ui.theme.FortressTheme
import com.umain.fortress.ui.theme.MoneyDisplay
import com.umain.fortress.ui.theme.MoneyDisplayTail

/**
 * Analytics tab. Top: pill toggle Spending / Expense. Middle: smooth line chart with a
 * highlighted point. Bottom: "How to manage your money well" promo card and a Monthly
 * Payment list.
 *
 * Reference: Vault analytics mock.
 */
@Composable
fun AnalyticsScreen() {
    var tab by remember { mutableStateOf(AnalyticsTab.Expense) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Analytics",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        item { PillToggle(selected = tab, onSelect = { tab = it }) }
        item { TotalSection(tab = tab) }
        item { SmoothLineChart(modifier = Modifier.fillMaxWidth().height(180.dp)) }
        item { MoneyTipCard() }
        item { SectionHeader(title = "Monthly Payment", actionLabel = "See All", onActionClick = {}) }
        item { MonthlyPaymentRow(label = "Food", amountMinor = 4500L, currency = "USD") }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        item { MonthlyPaymentRow(label = "Personal", amountMinor = 50000L, currency = "USD") }
    }
}

private enum class AnalyticsTab(val label: String) { Spending("Spending"), Expense("Expense") }

@Composable
private fun PillToggle(selected: AnalyticsTab, onSelect: (AnalyticsTab) -> Unit) {
    Surface(
        shape = FortressPillShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AnalyticsTab.entries.forEach { tab ->
                val isSelected = tab == selected
                Surface(
                    shape = FortressPillShape,
                    color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(tab) },
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalSection(tab: AnalyticsTab) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (tab == AnalyticsTab.Spending) "Total Spending" else "Total Expenses",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MoneyText(
            minorUnits = 318024L,
            currencyCode = "USD",
            size = MoneySize.Display,
            useSymbol = true,
            colorOverride = MaterialTheme.colorScheme.onBackground,
        )
        // Side-imports to silence unused-import warnings on the display/tail tokens used by
        // [MoneyText] under the hood — keeping them visible here documents the styles wired in.
        @Suppress("UNUSED_EXPRESSION") (MoneyDisplay to MoneyDisplayTail)
    }
}

/**
 * Two smooth bezier curves over a shared x-axis with a highlighted data point. Purely
 * decorative — backend wiring lands when the spending-by-day endpoint is exposed.
 */
@Composable
private fun SmoothLineChart(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val ink = FortressTheme.colors.cardInk
    val dotFill = MaterialTheme.colorScheme.surface
    val dotRing = MaterialTheme.colorScheme.primary
    val days = listOf("1 Dec", "2 Dec", "3 Dec", "4 Dec", "5 Dec", "6 Dec")
    Column(modifier = modifier) {
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .padding(horizontal = 8.dp)) {
            val w = size.width
            val h = size.height
            val pointsA = listOf(0f, 0.45f, 0.6f, 0.35f, 0.55f, 0.4f)
            val pointsB = listOf(0.7f, 0.5f, 0.65f, 0.4f, 0.75f, 0.5f)
            drawSmoothLine(pointsA, w, h, primary, Stroke(width = 6f, cap = StrokeCap.Round))
            drawSmoothLine(pointsB, w, h, ink, Stroke(width = 6f, cap = StrokeCap.Round))
            val highlightX = w * (3f / (pointsA.size - 1))
            val highlightY = h - (pointsA[3] * h)
            drawCircle(color = dotRing, radius = 18f, center = Offset(highlightX, highlightY))
            drawCircle(color = dotFill, radius = 10f, center = Offset(highlightX, highlightY))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            days.forEach {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSmoothLine(
    points: List<Float>,
    w: Float,
    h: Float,
    color: Color,
    stroke: Stroke,
) {
    if (points.isEmpty()) return
    val xs = points.mapIndexed { i, _ -> w * (i.toFloat() / (points.size - 1)) }
    val ys = points.map { h - it * h }
    val path = Path().apply {
        moveTo(xs[0], ys[0])
        for (i in 1 until points.size) {
            val midX = (xs[i - 1] + xs[i]) / 2f
            cubicTo(midX, ys[i - 1], midX, ys[i], xs[i], ys[i])
        }
    }
    drawPath(path = path, color = color, style = stroke)
}

@Composable
private fun MoneyTipCard() {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = FortressTheme.colors.cardInk,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "How to manage\nyour money well",
                style = MaterialTheme.typography.headlineSmall,
                color = FortressTheme.colors.cardInkContent,
            )
            Surface(
                shape = FortressPillShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = "Learn",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MonthlyPaymentRow(label: String, amountMinor: Long, currency: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(MaterialTheme.colorScheme.onBackground, androidx.compose.foundation.shape.CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
        MoneyText(
            minorUnits = amountMinor,
            currencyCode = currency,
            size = MoneySize.Medium,
            useSymbol = true,
            colorOverride = MaterialTheme.colorScheme.onBackground,
        )
    }
}
