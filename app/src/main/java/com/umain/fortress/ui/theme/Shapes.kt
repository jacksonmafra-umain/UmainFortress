package com.umain.fortress.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// =====================================================================================
//  Shape scale — bumped softer to match the "Vault" reference. Most surfaces use the
//  large/extraLarge tier; pill-shaped CTAs reach for the FullyRounded constant.
// =====================================================================================
val FortressShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Pill / fully-rounded shape used by quick-action CTAs and the Send/Receive pills. */
val FortressPillShape: RoundedCornerShape = RoundedCornerShape(percent = 50)
