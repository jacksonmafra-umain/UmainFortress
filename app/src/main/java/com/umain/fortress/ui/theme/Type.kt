package com.umain.fortress.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Sans reference face. Inter is the target; the platform sans-serif fallback keeps builds
 * clean until the font asset lands under `res/font`.
 */
private val Sans = FontFamily.SansSerif

/**
 * Mono reference face used for non-money identifiers (account numbers, IBANs, transaction
 * IDs). JetBrains Mono is the target; the platform monospace fallback applies until then.
 */
private val Mono = FontFamily.Monospace

/**
 * Fortress Material 3 type scale, wired against Inter. Role-to-usage mapping is documented
 * in [docs/design-system.md](../../../../../../../docs/design-system.md).
 */
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

/** Money head — Display size (44sp). Pairs with [MoneyDisplayTail]. */
val MoneyDisplay = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.Bold,
    fontSize = 44.sp, lineHeight = 52.sp, letterSpacing = (-1.5).sp,
)

/** Money tail — Display size. */
val MoneyDisplayTail = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.5).sp,
)

/** Money head — Large size (28sp). Pairs with [MoneyLargeTail]. */
val MoneyLarge = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.Bold,
    fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp,
)

/** Money tail — Large size. */
val MoneyLargeTail = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp, lineHeight = 20.sp,
)

/** Money head — Medium size (18sp). Pairs with [MoneyMediumTail]. */
val MoneyMedium = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.25).sp,
)

/** Money tail — Medium size. */
val MoneyMediumTail = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.Medium,
    fontSize = 14.sp, lineHeight = 20.sp,
)

/** Money head — Small size (14sp). Pairs with [MoneySmallTail]. */
val MoneySmall = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp, lineHeight = 20.sp,
)

/** Money tail — Small size. */
val MoneySmallTail = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight.Medium,
    fontSize = 11.sp, lineHeight = 14.sp,
)

/** Mono caption used for masked account numbers, IBANs, transaction IDs. Not for money. */
val MonoCaption = TextStyle(
    fontFamily = Mono, fontWeight = FontWeight.Normal,
    fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
)
