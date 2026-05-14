/**
 * Fortress design system — colour tokens (the "Vault" palette).
 *
 * Lavender-led light theme paired with a true-ink dark theme. Every screen reaches into
 * this file and only this file for colour; Material 3 [androidx.compose.material3.ColorScheme]
 * wiring lives in [com.umain.fortress.ui.theme.Theme].
 *
 * Naming convention: hue name + tone weight (50 = lightest, 950 = darkest), matching the
 * Material 3 / Tailwind expectation. The trailing `@Deprecated` block keeps the old
 * Midnight / Emerald / Violet aliases compiling so the migration is incremental.
 */
package com.umain.fortress.ui.theme

import androidx.compose.ui.graphics.Color

/** Lightest lavender — primary container in light mode. */
val Lavender50 = Color(0xFFF5F0FF)
/** Section-chip and onboarding hero-circle fill. */
val Lavender100 = Color(0xFFE9DEFF)
/** Empty-state and Add-Card pastel wash. */
val Lavender200 = Color(0xFFD7C4FF)
/** Primary accent in dark mode. */
val Lavender300 = Color(0xFFBEA1FF)
/** Disabled-but-tonal lavender. */
val Lavender400 = Color(0xFFA988F2)
/** Light-theme `primary`: CTA fill, dot indicator, focus ring. */
val Lavender500 = Color(0xFF8E6BE6)
/** Pressed / hover state for primary. */
val Lavender600 = Color(0xFF724FD0)
/** `onPrimaryContainer`; deep card gradients. */
val Lavender700 = Color(0xFF5635AE)

/** Scrim and scan-mask shadow base. */
val Ink950 = Color(0xFF06070B)
/** Dark theme `background`. */
val InkSurfaceDark = Color(0xFF0E1018)
/** Dark theme `surface`; ink-card variants in light theme. */
val InkSurfaceElevated = Color(0xFF161A26)
/** Dark theme `surfaceVariant`; dividers on ink surfaces. */
val InkSurfaceHigh = Color(0xFF1F2435)
/** Subtle text on ink surfaces. */
val Ink500 = Color(0xFF323A55)

/** Light theme `surface`: cards, sheets, modals. */
val Cloud0 = Color(0xFFFFFFFF)
/** Light theme `background` — subtle violet-tinted off-white. */
val Cloud50 = Color(0xFFFAF7FF)
/** `surfaceContainer`: text-field fill, keypad keys. */
val Cloud100 = Color(0xFFF1ECFA)
/** `surfaceContainerHigh`: divider strokes, raised swatches. */
val Cloud200 = Color(0xFFE3DBF1)
/** `outline`; inactive dot-indicator. */
val Cloud300 = Color(0xFFCFC3E2)

/** Dark-mode `onSurfaceVariant`. */
val MistText300 = Color(0xFFA9A3BD)
/** Light-mode `onSurfaceVariant`; money-tail grey. */
val MistText500 = Color(0xFF6E6883)
/** High-emphasis secondary text on light surfaces. */
val MistText700 = Color(0xFF3D3852)

/** Credit / positive / success. */
val Sage500 = Color(0xFF2EB37A)
/** Success-chip surface. */
val Sage100 = Color(0xFFD6F2E5)
/** Error / debit warning. */
val Coral500 = Color(0xFFE5484D)
/** Error-chip surface. */
val Coral100 = Color(0xFFFDE2E2)
/** Medium-risk status. */
val AmberStatus500 = Color(0xFFE9A23B)
/** Warning-chip surface. */
val AmberStatus100 = Color(0xFFFFE9B8)

/** Grey used for the trailing ".24" portion of [com.umain.fortress.ui.components.MoneyText]. */
val MoneyTail = Color(0xFF6E6883)

@Deprecated("Use Lavender700", ReplaceWith("Lavender700"))
val Midnight900 = Lavender700
@Deprecated("Use Lavender600", ReplaceWith("Lavender600"))
val Midnight800 = Lavender600
@Deprecated("Use Lavender500", ReplaceWith("Lavender500"))
val Midnight700 = Lavender500
@Deprecated("Use Lavender100", ReplaceWith("Lavender100"))
val Midnight200 = Lavender100
@Deprecated("Use Lavender50", ReplaceWith("Lavender50"))
val Midnight100 = Lavender50

@Deprecated("Use Sage500", ReplaceWith("Sage500"))
val Emerald600 = Sage500
@Deprecated("Use Sage500", ReplaceWith("Sage500"))
val Emerald500 = Sage500
@Deprecated("Use Sage500", ReplaceWith("Sage500"))
val Emerald400 = Sage500
@Deprecated("Use Sage100", ReplaceWith("Sage100"))
val Emerald100 = Sage100

@Deprecated("Use Lavender500", ReplaceWith("Lavender500"))
val Violet600 = Lavender600
@Deprecated("Use Lavender500", ReplaceWith("Lavender500"))
val Violet500 = Lavender500
@Deprecated("Use Lavender300", ReplaceWith("Lavender300"))
val Violet400 = Lavender300
@Deprecated("Use Lavender100", ReplaceWith("Lavender100"))
val Violet100 = Lavender100

@Deprecated("Use Coral500", ReplaceWith("Coral500"))
val Vermilion600 = Coral500
@Deprecated("Use Coral500", ReplaceWith("Coral500"))
val Vermilion500 = Coral500
@Deprecated("Use Coral500", ReplaceWith("Coral500"))
val Vermilion400 = Coral500

@Deprecated("Use AmberStatus500", ReplaceWith("AmberStatus500"))
val Amber500 = AmberStatus500
@Deprecated("Use AmberStatus500", ReplaceWith("AmberStatus500"))
val Amber400 = AmberStatus500

@Deprecated("Use MistText500", ReplaceWith("MistText500"))
val Slate500 = MistText500
@Deprecated("Use MistText700", ReplaceWith("MistText700"))
val Slate700 = MistText700

@Deprecated("Use InkSurfaceDark", ReplaceWith("InkSurfaceDark"))
val Ink900 = InkSurfaceDark
@Deprecated("Use InkSurfaceElevated", ReplaceWith("InkSurfaceElevated"))
val Ink800 = InkSurfaceElevated
@Deprecated("Use InkSurfaceHigh", ReplaceWith("InkSurfaceHigh"))
val Ink700 = InkSurfaceHigh
@Deprecated("Use MistText300", ReplaceWith("MistText300"))
val Mist300 = MistText300
@Deprecated("Use Cloud0", ReplaceWith("Cloud0"))
val Mist100 = Cloud0
