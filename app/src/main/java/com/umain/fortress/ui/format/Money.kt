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
    val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
    val fractionDigits = currency?.defaultFractionDigits ?: 2
    val scale = pow10(fractionDigits)
    val majorAbs = kotlin.math.abs(minorUnits) / scale
    val minorAbs = kotlin.math.abs(minorUnits) % scale
    val sign = if (minorUnits < 0) "-" else ""

    val formatter = NumberFormat.getIntegerInstance(locale)
    val majorStr = formatter.format(majorAbs)
    val minorStr = minorAbs.toString().padStart(fractionDigits, '0')

    return "$sign$majorStr.$minorStr $currencyCode"
}

private fun pow10(exp: Int): Long {
    var result = 1L
    repeat(exp) { result *= 10 }
    return result
}
