package com.umain.fortress.ui.format

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Format a backend minor-unit amount for display.
 *
 * Backend amounts arrive as minor units (cents, öre, …) keyed by ISO 4217 currency code.
 * The returned string uses locale-aware grouping but emits the explicit currency code
 * suffix rather than the locale symbol, which is unambiguous for fintech displays.
 *
 * @param minorUnits Amount in minor units; may be negative for debits.
 * @param currencyCode ISO 4217 currency code (e.g. `"USD"`).
 * @param locale Locale used to choose the grouping separator. Defaults to the system locale.
 */
fun formatMinorUnits(minorUnits: Long, currencyCode: String, locale: Locale = Locale.getDefault()): String {
    val parts = splitMinorUnits(minorUnits, currencyCode, locale)
    return parts.signed
}

/**
 * Decomposition of a money amount into the parts the design system renders separately.
 *
 * Produced by [splitMinorUnits] and consumed primarily by
 * [com.umain.fortress.ui.components.MoneyText], which renders the head and tail in paired
 * typography styles.
 *
 * @property sign Either an empty string or `"-"`.
 * @property head Major units with locale-aware grouping, no sign. E.g. `"31,180"`.
 * @property tail Decimal portion including the leading dot. E.g. `".24"`. Empty when the
 *               currency has zero fraction digits.
 * @property currencyCode ISO 4217 code passed in.
 * @property symbol Currency symbol from [java.util.Currency], or [currencyCode] when unknown.
 */
data class MoneyParts(
    val sign: String,
    val head: String,
    val tail: String,
    val currencyCode: String,
    val symbol: String,
) {
    /** Sign + head + tail + space + ISO code (e.g. `"-31,180.24 USD"`). */
    val signed: String
        get() = if (tail.isEmpty()) "$sign$head $currencyCode" else "$sign$head$tail $currencyCode"

    /** Sign + symbol + head + tail (e.g. `"-$31,180.24"`). */
    val symbolic: String
        get() = "$sign$symbol$head$tail"
}

/**
 * Split a money amount into a [MoneyParts] structure.
 *
 * Looks up the currency's [java.util.Currency.getDefaultFractionDigits] to size the tail
 * correctly (USD/EUR → 2, JPY → 0). Falls back to 2 fraction digits when the code is
 * unknown.
 *
 * @param minorUnits Amount in minor units; may be negative.
 * @param currencyCode ISO 4217 currency code.
 * @param locale Locale used to choose the grouping separator. Defaults to the system locale.
 */
fun splitMinorUnits(
    minorUnits: Long,
    currencyCode: String,
    locale: Locale = Locale.getDefault(),
): MoneyParts {
    val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
    val fractionDigits = currency?.defaultFractionDigits?.coerceAtLeast(0) ?: 2
    val scale = pow10(fractionDigits)
    val majorAbs = kotlin.math.abs(minorUnits) / scale
    val minorAbs = kotlin.math.abs(minorUnits) % scale

    val formatter = NumberFormat.getIntegerInstance(locale)
    val head = formatter.format(majorAbs)
    val tail = if (fractionDigits == 0) "" else "." + minorAbs.toString().padStart(fractionDigits, '0')

    return MoneyParts(
        sign = if (minorUnits < 0) "-" else "",
        head = head,
        tail = tail,
        currencyCode = currencyCode,
        symbol = currency?.symbol ?: currencyCode,
    )
}

private fun pow10(exp: Int): Long {
    var result = 1L
    repeat(exp) { result *= 10 }
    return result
}
