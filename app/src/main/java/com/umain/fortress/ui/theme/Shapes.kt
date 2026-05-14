package com.umain.fortress.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Fortress shape scale, bumped softer than the Material 3 default.
 *
 * Most surfaces use `large` (24dp); the balance card, card swatches, bottom nav, scan
 * frame and money-tip card use `extraLarge` (32dp). The pill shape below is used for
 * primary CTAs and the Send / Receive quick actions.
 */
val FortressShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Fully rounded pill shape for primary CTAs and quick-action pills. */
val FortressPillShape: RoundedCornerShape = RoundedCornerShape(percent = 50)
