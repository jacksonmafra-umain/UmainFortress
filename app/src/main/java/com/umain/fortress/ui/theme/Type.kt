package com.umain.fortress.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// =====================================================================================
//  Fortress design system — typography
//  -------------------------------------------------------------------------------------
//  Reference faces: Inter (sans, UI body + headlines) and JetBrains Mono (money / IDs).
//  Until proper font assets land under res/font, we fall back to the platform sans/mono.
//
//  Money typography is split into a *paired* style: a large-weight head ("$31,180") and
//  a muted small tail (".24") — see MoneyText composable for usage.
// =====================================================================================
private val Sans = FontFamily.SansSerif
private val Mono = FontFamily.Monospace

val FortressTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 52.sp, lineHeight = 60.sp, letterSpacing = (-1.0).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 40.sp, lineHeight = 48.sp, letterSpacing = (-0.5).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.25).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.3.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp,
    ),
)

// --- Money typography (paired head + muted tail) -------------------------------------
// Use via the [com.umain.fortress.ui.components.MoneyText] composable, which renders
// the major + currency in the head style and the trailing ".24" in the tail style.

val MoneyDisplay = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.Bold,
    fontSize = 44.sp, lineHeight = 52.sp, letterSpacing = (-1.5).sp,
)
val MoneyDisplayTail = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.5).sp,
)

val MoneyLarge = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.Bold,
    fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp,
)
val MoneyLargeTail = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp, lineHeight = 20.sp,
)

val MoneyMedium = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.25).sp,
)
val MoneyMediumTail = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.Medium,
    fontSize = 14.sp, lineHeight = 20.sp,
)

val MoneySmall = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp, lineHeight = 20.sp,
)
val MoneySmallTail = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.Medium,
    fontSize = 11.sp, lineHeight = 14.sp,
)

/** Mono for account numbers / IBANs / transaction IDs. Not for money. */
val MonoCaption = TextStyle(
    fontFamily = Mono, fontWeight = FontWeight.Normal,
    fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
)
