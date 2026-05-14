package com.umain.fortress.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.umain.fortress.ui.components.preview.DarkModeProvider
import com.umain.fortress.ui.components.preview.PreviewSurface
import com.umain.fortress.ui.format.MoneyParts
import com.umain.fortress.ui.format.splitMinorUnits
import com.umain.fortress.ui.theme.FortressTheme
import com.umain.fortress.ui.theme.MoneyDisplay
import com.umain.fortress.ui.theme.MoneyDisplayTail
import com.umain.fortress.ui.theme.MoneyLarge
import com.umain.fortress.ui.theme.MoneyLargeTail
import com.umain.fortress.ui.theme.MoneyMedium
import com.umain.fortress.ui.theme.MoneyMediumTail
import com.umain.fortress.ui.theme.MoneySmall
import com.umain.fortress.ui.theme.MoneySmallTail

/**
 * Size pairs available to [MoneyText].
 *
 * Each entry binds a head [TextStyle] (used for the sign, optional currency symbol, and
 * major units) to a matching tail [TextStyle] (used for the decimal fraction and optional
 * ISO suffix). The pair is chosen so the baselines line up regardless of size.
 */
enum class MoneySize { Display, Large, Medium, Small }

private fun MoneySize.head(): TextStyle = when (this) {
    MoneySize.Display -> MoneyDisplay
    MoneySize.Large -> MoneyLarge
    MoneySize.Medium -> MoneyMedium
    MoneySize.Small -> MoneySmall
}

private fun MoneySize.tail(): TextStyle = when (this) {
    MoneySize.Display -> MoneyDisplayTail
    MoneySize.Large -> MoneyLargeTail
    MoneySize.Medium -> MoneyMediumTail
    MoneySize.Small -> MoneySmallTail
}

/**
 * Paired money display: e.g. `$31,180.24` where the major units are rendered in the head
 * style and the trailing decimals are rendered smaller and in the muted
 * [FortressTheme.colors.moneyTail] colour.
 *
 * Always use this composable rather than concatenating major and minor units by hand —
 * it owns locale-aware grouping, currency-symbol vs ISO-code formatting and the redact
 * (`hidden = true`) variant used by the dashboard eye toggle.
 *
 * @param minorUnits Amount in minor units (cents, öre, …) as delivered by the backend.
 * @param currencyCode ISO 4217 currency code (e.g. `"USD"`).
 * @param size Size pair from [MoneySize].
 * @param modifier Layout modifier applied to the rendered text node.
 * @param hidden When `true`, the digits are replaced with bullet glyphs (sign + symbol kept).
 * @param useSymbol Render `$` / `€` prefix when `true`, otherwise emit the ISO code as suffix.
 * @param colorOverride Optional override for the head colour. The tail always uses
 *                      [FortressTheme.colors.moneyTail].
 */
@Composable
fun MoneyText(
    minorUnits: Long,
    currencyCode: String,
    size: MoneySize = MoneySize.Medium,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
    useSymbol: Boolean = true,
    colorOverride: Color? = null,
) {
    val parts = splitMinorUnits(minorUnits, currencyCode)
    val headColor = colorOverride ?: LocalContentColor.current
    val tailColor = FortressTheme.colors.moneyTail
    val annotated: AnnotatedString = buildAnnotatedString {
        if (hidden) {
            withStyle(SpanStyle(color = headColor)) {
                append(parts.sign)
                if (useSymbol) append(parts.symbol)
                append("•".repeat(parts.head.length.coerceAtLeast(3)))
            }
            withStyle(SpanStyle(color = tailColor)) {
                if (parts.tail.isNotEmpty()) append("." + "•".repeat(parts.tail.length - 1))
                if (!useSymbol) append(" ${parts.currencyCode}")
            }
        } else {
            withStyle(SpanStyle(color = headColor)) {
                append(parts.sign)
                if (useSymbol) append(parts.symbol)
                append(parts.head)
            }
            withStyle(SpanStyle(color = tailColor, fontSize = size.tail().fontSize, fontWeight = size.tail().fontWeight)) {
                append(parts.tail)
                if (!useSymbol) append(" ${parts.currencyCode}")
            }
        }
    }
    Text(text = annotated, style = size.head(), modifier = modifier)
}

/**
 * Pre-split variant for hot paths (transaction lists) where the caller already computed
 * the [MoneyParts] and wants to avoid a second locale-aware [java.text.NumberFormat]
 * allocation on each recomposition.
 */
@Composable
fun MoneyText(
    parts: MoneyParts,
    size: MoneySize = MoneySize.Small,
    modifier: Modifier = Modifier,
    useSymbol: Boolean = false,
    colorOverride: Color? = null,
) {
    val headColor = colorOverride ?: LocalContentColor.current
    val tailColor = FortressTheme.colors.moneyTail
    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(color = headColor)) {
            append(parts.sign)
            if (useSymbol) append(parts.symbol)
            append(parts.head)
        }
        withStyle(SpanStyle(color = tailColor, fontSize = size.tail().fontSize, fontWeight = size.tail().fontWeight)) {
            append(parts.tail)
            if (!useSymbol) append(" ${parts.currencyCode}")
        }
    }
    Text(text = annotated, style = size.head(), modifier = modifier)
}

/**
 * Catalogue of all four size pairs, light and dark. Useful as a visual regression target
 * when tweaking the head/tail typography.
 */
@Preview(name = "MoneyText sizes", showBackground = true)
@Composable
private fun MoneyTextSizesPreview(
    @PreviewParameter(DarkModeProvider::class) darkTheme: Boolean,
) {
    PreviewSurface(darkTheme = darkTheme) {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            MoneyText(minorUnits = 3_118_024L, currencyCode = "USD", size = MoneySize.Display)
            MoneyText(minorUnits = 425_000L, currencyCode = "USD", size = MoneySize.Large)
            MoneyText(minorUnits = 1_200L, currencyCode = "USD", size = MoneySize.Medium)
            MoneyText(minorUnits = -4_500L, currencyCode = "USD", size = MoneySize.Small)
        }
    }
}

@Preview(name = "MoneyText hidden", showBackground = true)
@Composable
private fun MoneyTextHiddenPreview() {
    PreviewSurface {
        MoneyText(
            minorUnits = 3_118_024L,
            currencyCode = "USD",
            size = MoneySize.Display,
            hidden = true,
        )
    }
}

