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
 * Sizes available for [MoneyText]. The head + tail are chosen as a paired set so they line
 * up on the baseline regardless of which size is requested.
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
 * Paired money display: e.g. **"$31,180".24** where the major units use the head style and
 * the trailing decimals are rendered smaller and in the muted [FortressTheme.colors.moneyTail]
 * colour, matching the "Vault" screenshots.
 *
 * @param hidden When true, the digits are replaced with hide-glyphs and the symbol is kept,
 *               for the "eye toggle" gesture on the dashboard balance.
 * @param useSymbol Render "$" / "€" prefix (true) vs "USD" / "EUR" suffix (false).
 * @param colorOverride Optional override for the head colour. Tail colour always comes from
 *                      the design-system [FortressTheme.colors.moneyTail] token.
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
 * Variant for inline transaction-row use that takes a pre-split [MoneyParts] when the caller
 * already computed it (avoids a second locale-aware NumberFormat allocation per row).
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
