package com.umain.fortress.ui.format

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Backend amounts come over the wire as minor units (cents, öre, etc.) keyed by ISO-4217 code.
 * Display formatting respects locale grouping but uses the currency's own code, not the user's
 * regional symbol — fintech UIs typically lean on the explicit code for unambiguity ("€" vs "EUR").
 */
fun formatMinorUnits(minorUnits: Long, currencyCode: String, locale: Locale = Locale.getDefault()): String {
    val parts = splitMinorUnits(minorUnits, currencyCode, locale)
    return parts.signed
}

/**
 * Split a money amount into its head ("$31,180") and tail (".24") + currency code so the
 * MoneyText composable can render them in paired typography styles. Sign is included on the
 * head.
 */
data class MoneyParts(
    val sign: String,           // "" or "-"
    val head: String,           // localised major units, no sign — e.g. "31,180"
    val tail: String,           // ".24" (includes the dot), or "" when fractionDigits == 0
    val currencyCode: String,   // "USD", "EUR", ...
    val symbol: String,         // "$", "€", ... — falls back to currencyCode when unknown
) {
    /** Full string form, sign + head + tail + " " + currencyCode. */
    val signed: String
        get() = if (tail.isEmpty()) "$sign$head $currencyCode" else "$sign$head$tail $currencyCode"

    /** Sign + symbol + head + tail (no ISO suffix) — e.g. "-$31,180.24". */
    val symbolic: String
        get() = "$sign$symbol$head$tail"
}

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
