package com.umain.fortress.ui.theme

import androidx.compose.ui.graphics.Color

// =====================================================================================
//  Fortress design system — colour tokens
//  -------------------------------------------------------------------------------------
//  Re-cut for the "Vault" palette: a lavender-led light theme paired with a true-ink
//  dark theme. Every screen reaches into this file and only this file for colour.
//
//  Naming convention: HueName + tone weight (50 = lightest, 950 = darkest), matching
//  Material 3 / Tailwind expectations. Semantic aliases live below the raw tokens.
//  Old Midnight/Emerald/Violet names are kept as @Deprecated aliases so legacy call
//  sites keep compiling while they migrate.
// =====================================================================================

// --- Lavender (primary accent — identity, focus, selected states) --------------------
val Lavender50 = Color(0xFFF5F0FF)
val Lavender100 = Color(0xFFE9DEFF)
val Lavender200 = Color(0xFFD7C4FF)
val Lavender300 = Color(0xFFBEA1FF)
val Lavender400 = Color(0xFFA988F2)
val Lavender500 = Color(0xFF8E6BE6)      // brand primary in light theme
val Lavender600 = Color(0xFF724FD0)
val Lavender700 = Color(0xFF5635AE)

// --- Ink (deep surfaces — bottom nav, scan modal, card backgrounds, dark theme) ------
val Ink950 = Color(0xFF06070B)
val InkSurfaceDark = Color(0xFF0E1018)   // page background in dark theme
val InkSurfaceElevated = Color(0xFF161A26)
val InkSurfaceHigh = Color(0xFF1F2435)
val Ink500 = Color(0xFF323A55)

// --- Cloud (off-white surfaces — page background, card surface, dividers) ------------
val Cloud0 = Color(0xFFFFFFFF)
val Cloud50 = Color(0xFFFAF7FF)          // page background in light theme (subtle violet wash)
val Cloud100 = Color(0xFFF1ECFA)
val Cloud200 = Color(0xFFE3DBF1)
val Cloud300 = Color(0xFFCFC3E2)

// --- Mist (low-emphasis text + subtle outlines) --------------------------------------
val MistText300 = Color(0xFFA9A3BD)
val MistText500 = Color(0xFF6E6883)
val MistText700 = Color(0xFF3D3852)

// --- Semantic accents ----------------------------------------------------------------
val Sage500 = Color(0xFF2EB37A)          // positive / credit / success
val Sage100 = Color(0xFFD6F2E5)
val Coral500 = Color(0xFFE5484D)         // error / debit warning
val Coral100 = Color(0xFFFDE2E2)
val AmberStatus500 = Color(0xFFE9A23B)   // medium risk
val AmberStatus100 = Color(0xFFFFE9B8)

// --- Money / typography accents ------------------------------------------------------
val MoneyTail = Color(0xFF6E6883)        // grey-ish trailing decimals "31,180.[24]"

// =====================================================================================
//  Legacy aliases — kept so the existing call sites compile during migration.
//  New code should reference the canonical tokens above instead.
// =====================================================================================
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
