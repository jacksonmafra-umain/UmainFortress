package com.umain.fortress.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Fortress icon registry.
 *
 * The reference set is the "Free UI Icons — Open Source Vector Icon Set" (Streamline / Lucide
 * lineage): thin 1.5px stroke, 24×24 viewport, rounded caps and joins, optical sizing.
 * We mirror the visual language using the **outlined** Material Symbols family — close enough
 * in stroke weight + geometry that the screens read as "Vault"-style without shipping a second
 * SVG bundle. To swap in the exact Streamline / Lucide glyphs later, replace each property
 * here with an [ImageVector] built from the SVG path data (see docs/09-design-system.md).
 *
 * Every screen and component **must** route through this object — never reach for
 * `Icons.Default.*` directly. That keeps the icon swap a single PR.
 */
object FortressIcons {
    // --- Navigation / app chrome -----------------------------------------------------
    val Back: ImageVector get() = Icons.AutoMirrored.Outlined.ArrowBack
    val Logout: ImageVector get() = Icons.AutoMirrored.Outlined.Logout
    val Notifications: ImageVector get() = Icons.Outlined.NotificationsNone
    val Settings: ImageVector get() = Icons.Outlined.Settings
    val More: ImageVector get() = Icons.Outlined.MoreHoriz

    // --- Bottom navigation -----------------------------------------------------------
    val Home: ImageVector get() = Icons.Outlined.Home
    val Cards: ImageVector get() = Icons.Outlined.Wallet
    val Scan: ImageVector get() = Icons.Outlined.QrCodeScanner
    val Analytics: ImageVector get() = Icons.Outlined.PieChart
    val Profile: ImageVector get() = Icons.Outlined.AccountCircle

    // --- Quick actions ---------------------------------------------------------------
    /** Send (outbound). Arrow-up-right semantic. */
    val Send: ImageVector get() = Icons.Outlined.TrendingUp
    /** Receive (inbound). Arrow-down-left semantic. */
    val Receive: ImageVector get() = Icons.Outlined.SwapHoriz
    val Grid: ImageVector get() = Icons.Outlined.GridView

    // --- Money / accounts ------------------------------------------------------------
    val Savings: ImageVector get() = Icons.Outlined.Savings
    val Investment: ImageVector get() = Icons.Outlined.ShowChart
    val Card: ImageVector get() = Icons.Outlined.CreditCard
    val Insights: ImageVector get() = Icons.Outlined.Insights
    val Document: ImageVector get() = Icons.Outlined.Description

    // --- Security --------------------------------------------------------------------
    val ShieldGood: ImageVector get() = Icons.Outlined.GppGood
    val ShieldMaybe: ImageVector get() = Icons.Outlined.GppMaybe
    val ShieldBad: ImageVector get() = Icons.Outlined.GppBad
    val ShieldVerified: ImageVector get() = Icons.Outlined.VerifiedUser
    val Fingerprint: ImageVector get() = Icons.Outlined.Fingerprint
    val Lock: ImageVector get() = Icons.Outlined.Lock
    val Bolt: ImageVector get() = Icons.Outlined.Bolt

    // --- Visibility / chrome ---------------------------------------------------------
    val Eye: ImageVector get() = Icons.Outlined.Visibility
    val EyeOff: ImageVector get() = Icons.Outlined.VisibilityOff
    val Check: ImageVector get() = Icons.Outlined.CheckCircle
}
